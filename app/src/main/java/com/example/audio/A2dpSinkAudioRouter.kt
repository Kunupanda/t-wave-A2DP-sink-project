package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.content.ContextCompat
import com.example.model.AudioTelemetry
import com.example.service.SpeakerSessionManager
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class A2dpProxyAudioState(
  val isPlaying: Boolean = false,
  val sampleRateHz: Int = 44_100,
  val bitDepth: Int = 16,
  val channels: Int = 2,
)

/**
 * Handles A2DP Sink audio routing, MediaBrowser focus wakeup, `STREAM_MUSIC` volume/mute sync,
 * and optional HAL `TYPE_BLUETOOTH_A2DP` capture-to-speaker bridging when Android does not play
 * decoded A2DP PCM directly inside `com.android.bluetooth`.
 */
object A2dpSinkAudioRouter {

  private const val ACTION_MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService"
  private val KNOWN_BT_MEDIA_BROWSER_COMPONENTS =
    listOf(
      ComponentName(
        "com.android.bluetooth",
        "com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService",
      ),
      ComponentName(
        "com.google.android.bluetooth",
        "com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService",
      ),
      ComponentName(
        "com.android.btservices",
        "com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService",
      ),
    )

  fun hasRecordAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  fun findA2dpInputDevice(audioManager: AudioManager?): AudioDeviceInfo? {
    if (audioManager == null) return null
    return try {
      audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
      }
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Ensures `STREAM_MUSIC` is unmuted, audio mode is not stuck in an inactive call state, and
   * A2DP suspension HAL parameters are cleared so decoded A2DP Sink audio can reach the speaker.
   */
  fun ensureSpeakerStreamAudible(
    audioManager: AudioManager?,
    appGain: Float,
    isAppMuted: Boolean,
    bumpZeroVolume: Boolean,
  ) {
    if (audioManager == null) return
    try {
      val mode = audioManager.mode
      if (
        mode != AudioManager.MODE_NORMAL &&
          mode != AudioManager.MODE_IN_CALL &&
          mode != AudioManager.MODE_RINGTONE &&
          mode != AudioManager.MODE_IN_COMMUNICATION
      ) {
        audioManager.mode = AudioManager.MODE_NORMAL
      }
    } catch (_: Exception) {
    }

    try {
      audioManager.setParameters("A2dpSuspended=false")
      audioManager.setParameters("A2dpSinkSuspended=false")
    } catch (_: Exception) {
    }

    try {
      if (!isAppMuted) {
        if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
          audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_UNMUTE,
            0,
          )
        }
        if (bumpZeroVolume && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
          val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
          if (maxVol > 0) {
            val target = (maxVol * appGain.coerceAtLeast(0.6f)).roundToInt().coerceIn(1, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
          }
        }
      }
    } catch (_: SecurityException) {
    } catch (_: Exception) {
    }
  }

  fun applyA2dpStreamVolume(audioManager: AudioManager?, gain: Float, isMuted: Boolean) {
    if (audioManager == null) return
    try {
      if (isMuted) {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
      } else {
        if (audioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
          audioManager.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_UNMUTE,
            0,
          )
        }
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (maxVol > 0) {
          val target = (maxVol * gain.coerceIn(0f, 1f)).roundToInt().coerceIn(0, maxVol)
          audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
      }
    } catch (_: SecurityException) {
    } catch (_: Exception) {
    }
  }

  fun inspectProxyAudio(
    proxy: BluetoothProfile?,
    device: BluetoothDevice?,
  ): A2dpProxyAudioState {
    if (proxy == null || device == null) return A2dpProxyAudioState()

    val isPlaying =
      try {
        val m = proxy.javaClass.getMethod("isA2dpPlaying", BluetoothDevice::class.java)
        (m.invoke(proxy, device) as? Boolean) ?: false
      } catch (_: Exception) {
        false
      }

    var sampleRate = 44_100
    var bitDepth = 16
    var channels = 2

    try {
      val cfgMethod = proxy.javaClass.getMethod("getAudioConfig", BluetoothDevice::class.java)
      val cfg = cfgMethod.invoke(proxy, device)
      if (cfg != null) {
        val rawRate =
          (cfg.javaClass.getMethod("getSampleRate").invoke(cfg) as? Int) ?: 44_100
        if (rawRate in 8_000..192_000) {
          sampleRate = rawRate
        }
        val rawBits =
          try {
            (cfg.javaClass.getMethod("getBitsPerSample").invoke(cfg) as? Int) ?: 16
          } catch (_: Exception) {
            16
          }
        if (rawBits == 16 || rawBits == 24 || rawBits == 32) {
          bitDepth = rawBits
        }
        val rawChan =
          try {
            (cfg.javaClass.getMethod("getChannelConfig").invoke(cfg) as? Int)
              ?: AudioFormat.CHANNEL_IN_STEREO
          } catch (_: Exception) {
            AudioFormat.CHANNEL_IN_STEREO
          }
        if (
          rawChan == AudioFormat.CHANNEL_IN_MONO || rawChan == AudioFormat.CHANNEL_OUT_MONO
        ) {
          channels = 1
        }
      }
    } catch (_: Exception) {
    }

    return A2dpProxyAudioState(
      isPlaying = isPlaying,
      sampleRateHz = sampleRate,
      bitDepth = bitDepth,
      channels = channels,
    )
  }

  fun discoverBluetoothMediaBrowserComponents(packageManager: PackageManager): List<ComponentName> {
    val discovered = mutableListOf<ComponentName>()
    try {
      val intent = Intent(ACTION_MEDIA_BROWSER_SERVICE)
      val resolveInfos =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          packageManager.queryIntentServices(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
          )
        } else {
          @Suppress("DEPRECATION")
          packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }
      resolveInfos
        .mapNotNull { it.serviceInfo }
        .filter { info ->
          val pkg = info.packageName.lowercase()
          val cls = info.name.lowercase()
          pkg.contains("bluetooth") ||
            pkg.contains("btservices") ||
            cls.contains("bluetoothmediabrowser") ||
            cls.contains("avrcpcontroller")
        }
        .forEach { info -> discovered += ComponentName(info.packageName, info.name) }
    } catch (_: Exception) {
    }
    return (discovered + KNOWN_BT_MEDIA_BROWSER_COMPONENTS).distinct()
  }

  /**
   * Connects to `com.android.bluetooth`'s `BluetoothMediaBrowserService` on the main looper and
   * invokes `MediaController.transportControls.play()`, which unlocks `A2dpSinkStreamHandler`'s
   * audio focus gate on stock/AOSP Android builds where `automatically_request_audio_focus` is off.
   */
  class MediaBrowserWakeSession(
    private val context: Context,
  ) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    @Volatile private var activeBrowser: MediaBrowser? = null
    @Volatile private var activeController: MediaController? = null

    fun connectAndPlay() {
      if (closed.get()) return
      mainHandler.post {
        if (closed.get()) return@post
        val currentController = activeController
        val currentBrowser = activeBrowser
        if (currentBrowser != null && currentBrowser.isConnected && currentController != null) {
          issuePlay(currentController)
          return@post
        }
        val candidates = discoverBluetoothMediaBrowserComponents(context.packageManager)
        tryConnectCandidate(candidates, index = 0)
      }
    }

    private fun tryConnectCandidate(candidates: List<ComponentName>, index: Int) {
      if (closed.get() || index >= candidates.size) return
      val component = candidates[index]
      disconnectCurrentBrowserOnMain()

      var browserRef: MediaBrowser? = null
      val callback =
        object : MediaBrowser.ConnectionCallback() {
          override fun onConnected() {
            val browser = browserRef ?: return
            if (closed.get() || !browser.isConnected) {
              try {
                browser.disconnect()
              } catch (_: Exception) {
              }
              return
            }
            activeBrowser = browser
            try {
              val token = browser.sessionToken
              val controller = MediaController(context, token)
              activeController = controller
              issuePlay(controller)
            } catch (_: Exception) {
            }
          }

          override fun onConnectionFailed() {
            if (browserRef == activeBrowser) {
              activeBrowser = null
              activeController = null
            }
            tryConnectCandidate(candidates, index + 1)
          }

          override fun onConnectionSuspended() {
            activeController = null
          }
        }

      try {
        val browser = MediaBrowser(context.applicationContext, component, callback, null)
        browserRef = browser
        activeBrowser = browser
        browser.connect()
      } catch (_: Exception) {
        tryConnectCandidate(candidates, index + 1)
      }
    }

    private fun issuePlay(controller: MediaController) {
      try {
        controller.transportControls.prepare()
      } catch (_: Exception) {
      }
      try {
        controller.transportControls.play()
      } catch (_: Exception) {
      }
    }

    private fun disconnectCurrentBrowserOnMain() {
      activeController = null
      val browser = activeBrowser ?: return
      activeBrowser = null
      try {
        browser.disconnect()
      } catch (_: Exception) {
      }
    }

    fun close() {
      if (!closed.compareAndSet(false, true)) return
      if (Looper.myLooper() == Looper.getMainLooper()) {
        disconnectCurrentBrowserOnMain()
      } else {
        mainHandler.post { disconnectCurrentBrowserOnMain() }
      }
    }
  }

  /**
   * Captures decoded PCM from an `AudioDeviceInfo.TYPE_BLUETOOTH_A2DP` HAL input device and plays
   * it to Android's media output stream (`AudioTrack` on `USAGE_MEDIA`) without acquiring
   * `AudioFocus`, avoiding `AUDIOFOCUS_LOSS` in `com.android.bluetooth`.
   */
  class HalCaptureBridge(
    private val context: Context,
    private val scope: CoroutineScope,
  ) {
    private var bridgeJob: Job? = null
    @Volatile private var bridgeTrack: AudioTrack? = null
    @Volatile var isRunning: Boolean = false
      private set

    fun startIfNeeded(
      inputDevice: AudioDeviceInfo,
      sampleRateHz: Int,
      lowLatency: Boolean,
      ultraLowLatency: Boolean = false,
    ): Boolean {
      if (bridgeJob?.isActive == true) return true
      if (!hasRecordAudioPermission(context)) return false

      bridgeJob =
        scope.launch(Dispatchers.IO) {
          runBridgeLoop(inputDevice, sampleRateHz, lowLatency, ultraLowLatency)
        }
      return true
    }

    fun updateVolume() {
      val track = bridgeTrack ?: return
      try {
        if (track.state == AudioTrack.STATE_INITIALIZED) {
          track.setVolume(SpeakerSessionManager.effectiveVolume())
        }
      } catch (_: IllegalStateException) {
      }
    }

    fun stop() {
      isRunning = false
      bridgeJob?.cancel()
      bridgeJob = null
      val track = bridgeTrack
      bridgeTrack = null
      if (track != null) {
        try {
          if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
            track.flush()
          }
          track.release()
        } catch (_: IllegalStateException) {
        }
      }
    }

    @SuppressLint("MissingPermission")
    private fun CoroutineScope.runBridgeLoop(
      inputDevice: AudioDeviceInfo,
      requestedRateHz: Int,
      lowLatency: Boolean,
      ultraLowLatency: Boolean,
    ) {
      val preferredRate =
        inputDevice.sampleRates.firstOrNull { it == requestedRateHz }
          ?: inputDevice.sampleRates.maxOrNull()?.takeIf { it in 8_000..192_000 }
          ?: requestedRateHz
      val minRecBuf =
        AudioRecord.getMinBufferSize(
          preferredRate,
          AudioFormat.CHANNEL_IN_STEREO,
          AudioFormat.ENCODING_PCM_16BIT,
        )
      if (minRecBuf <= 0) return

      val currentState = SpeakerSessionManager.state.value
      val effectiveUltraLow = ultraLowLatency || currentState.ultraLowLatencyMode
      val effectiveLowLatency = lowLatency || currentState.lowLatencyMode || effectiveUltraLow
      val recBufBytes =
        if (effectiveUltraLow) maxOf(minRecBuf, 2048) else maxOf(minRecBuf * 2, 8192)

      val previousThreadPriority =
        try {
          Process.getThreadPriority(Process.myTid())
        } catch (_: Exception) {
          Process.THREAD_PRIORITY_DEFAULT
        }
      try {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
      } catch (_: Exception) {
      }

      var recorder: AudioRecord? = null
      var track: AudioTrack? = null
      try {
        recorder =
          AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
            .setAudioFormat(
              AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(preferredRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            )
            .setBufferSizeInBytes(recBufBytes)
            .build()

        recorder.setPreferredDevice(inputDevice)
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
          return
        }

        val configuredTrack =
          PcmAudioTrackConfigurator.buildConfiguredTrack(
            context = context,
            profile = currentState.qualityProfile,
            lowLatency = effectiveLowLatency,
            bufferProfile = currentState.bufferProfile,
            overrideSampleRateHz = preferredRate,
            ultraLowLatency = effectiveUltraLow,
          )
        track = configuredTrack.track
        val trackBufBytes = configuredTrack.bufferSizing.bufferSizeBytes
        bridgeTrack = track
        track.setVolume(SpeakerSessionManager.effectiveVolume())

        recorder.startRecording()
        track.play()
        isRunning = true

        val chunkBytes =
          when {
            effectiveUltraLow -> 512
            effectiveLowLatency -> 1024
            else -> 4096
          }
        val pcmBuffer = ByteArray(chunkBytes)
        val syncedBuffer = ByteArray(chunkBytes * 4 + 16)
        val delayLine = PcmSyncDelayLine()
        var totalBytes = 0L
        var lastPublishMs = 0L
        val publishIntervalMs = if (effectiveUltraLow) 24L else 45L

        while (isActive) {
          val read = recorder.read(pcmBuffer, 0, pcmBuffer.size)
          if (read < 0) break
          if (read == 0) continue

          val aligned = read - (read % 4)
          if (aligned <= 0) continue

          val liveSyncOffsetMs = SpeakerSessionManager.state.value.syncOffsetMs
          val syncedBytes =
            delayLine.processChunk(
              input = pcmBuffer,
              length = aligned,
              output = syncedBuffer,
              sampleRateHz = preferredRate,
              frameSizeBytes = 4,
              syncOffsetMs = liveSyncOffsetMs,
            )

          totalBytes += aligned
          track.setVolume(SpeakerSessionManager.effectiveVolume())
          if (syncedBytes > 0) {
            val written = track.write(syncedBuffer, 0, syncedBytes, AudioTrack.WRITE_BLOCKING)
            if (written < 0) break
          }

          val nowMs = System.currentTimeMillis()
          if (nowMs - lastPublishMs >= publishIntervalMs) {
            lastPublishMs = nowMs
            val analysisBuf = if (syncedBytes > 0) syncedBuffer else pcmBuffer
            val analysisLen = if (syncedBytes > 0) syncedBytes else aligned
            val analysis = PcmAnalyzer.analyzeStereo16BitLe(analysisBuf, analysisLen)
            val baseLatencyMs =
              when {
                effectiveUltraLow -> 2
                effectiveLowLatency -> 24
                else -> 65
              }
            SpeakerSessionManager.updateTelemetry(
              AudioTelemetry(
                leftLevel = analysis.leftLevel,
                rightLevel = analysis.rightLevel,
                spectrumBands = analysis.bands,
                bufferFillRatio = if (effectiveUltraLow) 0.18f else 0.35f,
                throughputKbps = (preferredRate * 32f) / 1000f,
                totalBytesReceived = totalBytes,
                bufferedBytes = aligned + delayLine.currentBufferedBytes,
                bufferCapacityBytes = trackBufBytes,
                estimatedLatencyMs =
                  PcmSyncDelayLine.computeEffectiveLatencyMs(baseLatencyMs, liveSyncOffsetMs),
                sampleRateHz = preferredRate,
                bitDepth = 16,
                channels = 2,
                activeOutputDescription =
                  if (effectiveUltraLow) "A2DP Sink • Ultra-Low HAL Bridge"
                  else "A2DP Sink • HAL Capture Bridge",
              )
            )
          }
        }
      } catch (_: Exception) {
      } finally {
        isRunning = false
        bridgeTrack = null
        try {
          Process.setThreadPriority(previousThreadPriority)
        } catch (_: Exception) {
        }
        try {
          recorder?.stop()
        } catch (_: IllegalStateException) {
        }
        try {
          recorder?.release()
        } catch (_: Exception) {
        }
        try {
          if (track?.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
            track.flush()
          }
          track?.release()
        } catch (_: IllegalStateException) {
        }
      }
    }
  }
}
