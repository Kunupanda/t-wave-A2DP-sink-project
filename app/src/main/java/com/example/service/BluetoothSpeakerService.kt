package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.A2dpSinkAudioRouter
import com.example.audio.PcmAnalyzer
import com.example.audio.PcmAudioTrackConfigurator
import com.example.audio.PcmSyncDelayLine
import com.example.bluetooth.RootA2dpController
import com.example.data.SpeakerPreferences
import com.example.model.AudioQualityProfile
import com.example.model.AudioTelemetry
import com.example.model.ConnectedDeviceInfo
import com.example.model.ConnectionStatus
import com.example.model.SpeakerMode
import java.io.IOException
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BluetoothSpeakerService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var receiverJob: Job? = null
  private lateinit var preferences: SpeakerPreferences
  private val rootA2dpController = RootA2dpController()

  @Volatile private var activeServerSocket: BluetoothServerSocket? = null
  @Volatile private var activeClientSocket: BluetoothSocket? = null
  @Volatile private var activeAudioTrack: AudioTrack? = null
  @Volatile private var reconfigureAudioRequested: Boolean = false
  @Volatile private var userStopRequested: Boolean = false
  @Volatile private var a2dpSinkProxy: BluetoothProfile? = null
  @Volatile private var latestA2dpBroadcastDevice: ConnectedDeviceInfo? = null
  @Volatile private var a2dpBroadcastConnected: Boolean? = null
  @Volatile private var wakeA2dpRequested: Boolean = false
  @Volatile private var mediaBrowserWakeSession: A2dpSinkAudioRouter.MediaBrowserWakeSession? = null
  @Volatile private var halCaptureBridge: A2dpSinkAudioRouter.HalCaptureBridge? = null

  private var bleAdvertiser: BluetoothLeAdvertiser? = null
  private var bleAdvertiseCallback: AdvertiseCallback? = null
  private var audioFocusRequest: AudioFocusRequest? = null
  private var a2dpReceiverRegistered: Boolean = false
  private var screenStateReceiverRegistered: Boolean = false
  private var cpuWakeLock: PowerManager.WakeLock? = null
  private var mediaSession: MediaSession? = null

  private val screenStateReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
          Intent.ACTION_SCREEN_OFF,
          Intent.ACTION_USER_PRESENT -> {
            syncLockScreenWakeLock()
            refreshForegroundNotification()
          }
        }
      }
    }

  private val a2dpSinkStateReceiver =
    object : BroadcastReceiver() {
      @SuppressLint("MissingPermission")
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != RootA2dpController.ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED) return
        val newState =
          intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
        val device =
          IntentCompat.getParcelableExtra(
            intent,
            BluetoothDevice.EXTRA_DEVICE,
            BluetoothDevice::class.java,
          )
        when (newState) {
          BluetoothProfile.STATE_CONNECTED -> {
            val deviceName =
              if (hasBluetoothConnectPermission()) {
                try {
                  device?.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio Source"
                } catch (_: SecurityException) {
                  "Bluetooth Audio Source"
                }
              } else {
                "Bluetooth Audio Source"
              }
            val info =
              ConnectedDeviceInfo(
                name = deviceName,
                address = device?.address ?: "Unknown",
              )
            latestA2dpBroadcastDevice = info
            a2dpBroadcastConnected = true
          }
          BluetoothProfile.STATE_DISCONNECTED -> {
            a2dpBroadcastConnected = false
          }
        }
      }
    }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    preferences = SpeakerPreferences(applicationContext)
    createNotificationChannel()
    initMediaSession()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP_SPEAKER -> {
        userStopRequested = true
        stopSpeakerPipeline(errorMessage = null)
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_UPDATE_GAIN -> {
        applyVolumeToActiveTrack()
        return START_STICKY
      }
      ACTION_TOGGLE_MUTE -> {
        SpeakerSessionManager.toggleMute()
        applyVolumeToActiveTrack()
        refreshForegroundNotification()
        return START_STICKY
      }
      ACTION_LOCK_SCREEN_RECONFIGURE -> {
        syncLockScreenWakeLock()
        refreshForegroundNotification()
        return START_STICKY
      }
      ACTION_RECONFIGURE_AUDIO -> {
        reconfigureAudioRequested = true
        refreshForegroundNotification()
        return START_STICKY
      }
      ACTION_RESTART_LISTENER -> {
        if (SpeakerSessionManager.state.value.status == ConnectionStatus.LISTENING &&
          SpeakerSessionManager.state.value.speakerMode == SpeakerMode.RFCOMM_SPP
        ) {
          closeServerSocketQuietly()
        }
        return START_STICKY
      }
      ACTION_CONNECT_A2DP_DEVICE -> {
        val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        if (!address.isNullOrBlank()) {
          connectToA2dpSource(address)
        }
        return START_STICKY
      }
      ACTION_WAKE_A2DP_AUDIO -> {
        wakeA2dpAudioRoute()
        return START_STICKY
      }
      ACTION_START_SPEAKER,
      null -> {
        userStopRequested = false
        syncLockScreenWakeLock()
        registerScreenStateReceiver()
        val state = SpeakerSessionManager.state.value
        val (title, body) =
          if (state.speakerMode == SpeakerMode.A2DP_SINK) {
            getString(R.string.notification_title_a2dp_listening) to
              getString(R.string.notification_text_a2dp_listening, "A2DP Sink 0x110B")
          } else {
            getString(R.string.notification_title_listening) to
              getString(R.string.notification_text_listening, state.qualityProfile.shortBadge)
          }
        promoteToForeground(title = title, body = body)
        startSpeakerPipeline()
        return START_STICKY
      }
      else -> return START_STICKY
    }
  }

  override fun onDestroy() {
    userStopRequested = true
    stopSpeakerPipeline(errorMessage = null)
    releaseMediaSession()
    serviceScope.cancel()
    super.onDestroy()
  }

  private fun refreshForegroundNotification() {
    val state = SpeakerSessionManager.state.value
    when (state.status) {
      ConnectionStatus.LISTENING ->
        if (state.speakerMode == SpeakerMode.A2DP_SINK) {
          updateNotification(
            title = getString(R.string.notification_title_a2dp_listening),
            body = getString(R.string.notification_text_a2dp_listening, "A2DP Sink 0x110B"),
          )
        } else {
          updateNotification(
            title = getString(R.string.notification_title_listening),
            body = getString(R.string.notification_text_listening, state.qualityProfile.shortBadge),
          )
        }
      ConnectionStatus.RECONNECTING ->
        updateNotification(
          title = getString(R.string.notification_title_reconnecting),
          body =
            getString(
              R.string.notification_text_reconnecting,
              state.lastConnectedDevice?.name ?: "Bluetooth Audio Source",
              state.reconnectAttempt,
              state.maxReconnectAttempts,
            ),
        )
      ConnectionStatus.CONNECTED ->
        updateNotification(
          title = getString(R.string.notification_title_connected),
          body =
            getString(
              R.string.notification_text_connected,
              if (state.speakerMode == SpeakerMode.A2DP_SINK) "A2DP Sink"
              else state.qualityProfile.shortBadge,
              state.connectedDevice?.name ?: "Bluetooth Audio Source",
            ),
        )
      ConnectionStatus.IDLE -> Unit
    }
  }

  private fun promoteToForeground(title: String, body: String) {
    val notification = buildNotification(title, body)
    val foregroundType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
      } else {
        0
      }
    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
  }

  private fun updateNotification(title: String, body: String) {
    updateMediaSessionState(title = title, subtitle = body)
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(NOTIFICATION_ID, buildNotification(title, body))
  }

  private fun buildNotification(title: String, body: String): Notification {
    val state = SpeakerSessionManager.state.value
    val launchIntent =
      Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
    val contentPendingIntent =
      PendingIntent.getActivity(
        this,
        0,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val stopIntent =
      Intent(this, BluetoothSpeakerService::class.java).apply { action = ACTION_STOP_SPEAKER }
    val stopPendingIntent =
      PendingIntent.getService(
        this,
        1,
        stopIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val muteIntent =
      Intent(this, BluetoothSpeakerService::class.java).apply { action = ACTION_TOGGLE_MUTE }
    val mutePendingIntent =
      PendingIntent.getService(
        this,
        2,
        muteIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    val muteLabel =
      if (state.isMuted) getString(R.string.notification_unmute)
      else getString(R.string.notification_mute)

    val activeSession = mediaSession
    if (state.lockScreenPlaybackEnabled && activeSession != null) {
      val platformBuilder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
          @Suppress("DEPRECATION")
          Notification.Builder(this).setPriority(Notification.PRIORITY_LOW)
        }
      val muteAction =
        Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat_speaker),
            muteLabel,
            mutePendingIntent,
          )
          .build()
      val stopAction =
        Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat_speaker),
            getString(R.string.action_stop_service),
            stopPendingIntent,
          )
          .build()
      return platformBuilder
        .setSmallIcon(R.drawable.ic_stat_speaker)
        .setContentTitle(title)
        .setContentText(body)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(Notification.CATEGORY_TRANSPORT)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setContentIntent(contentPendingIntent)
        .addAction(muteAction)
        .addAction(stopAction)
        .setStyle(
          Notification.MediaStyle()
            .setMediaSession(activeSession.sessionToken)
            .setShowActionsInCompactView(0, 1)
        )
        .build()
    }

    return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_speaker)
      .setContentTitle(title)
      .setContentText(body)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setContentIntent(contentPendingIntent)
      .addAction(0, muteLabel, mutePendingIntent)
      .addAction(0, getString(R.string.action_stop_service), stopPendingIntent)
      .build()
  }

  private fun initMediaSession() {
    if (mediaSession != null) return
    mediaSession =
      try {
        MediaSession(this, "t_wave_speaker_session").apply {
          setCallback(
            object : MediaSession.Callback() {
              override fun onPlay() {
                SpeakerSessionManager.setMuted(false)
                applyVolumeToActiveTrack()
                if (SpeakerSessionManager.state.value.speakerMode == SpeakerMode.A2DP_SINK) {
                  wakeA2dpAudioRoute()
                }
                refreshForegroundNotification()
              }

              override fun onPause() {
                SpeakerSessionManager.setMuted(true)
                applyVolumeToActiveTrack()
                refreshForegroundNotification()
              }

              override fun onStop() {
                userStopRequested = true
                stopSpeakerPipeline(errorMessage = null)
                stopSelf()
              }
            }
          )
          isActive = true
        }
      } catch (_: Exception) {
        null
      }
  }

  private fun updateMediaSessionState(title: String, subtitle: String) {
    val session = mediaSession ?: return
    val state = SpeakerSessionManager.state.value
    val pbState =
      when {
        state.status == ConnectionStatus.CONNECTED && state.isMuted -> PlaybackState.STATE_PAUSED
        state.status == ConnectionStatus.CONNECTED -> PlaybackState.STATE_PLAYING
        state.status == ConnectionStatus.LISTENING || state.status == ConnectionStatus.RECONNECTING ->
          PlaybackState.STATE_CONNECTING
        else -> PlaybackState.STATE_STOPPED
      }
    try {
      session.setPlaybackState(
        PlaybackState.Builder()
          .setActions(
            PlaybackState.ACTION_PLAY or
              PlaybackState.ACTION_PAUSE or
              PlaybackState.ACTION_PLAY_PAUSE or
              PlaybackState.ACTION_STOP
          )
          .setState(pbState, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
          .build()
      )
      session.setMetadata(
        MediaMetadata.Builder()
          .putString(MediaMetadata.METADATA_KEY_TITLE, title)
          .putString(
            MediaMetadata.METADATA_KEY_ARTIST,
            state.connectedDevice?.name ?: "t_wave Bluetooth Speaker",
          )
          .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
          .build()
      )
      session.isActive = state.status != ConnectionStatus.IDLE
    } catch (_: Exception) {
    }
  }

  private fun releaseMediaSession() {
    val session = mediaSession ?: return
    mediaSession = null
    try {
      session.isActive = false
      session.release()
    } catch (_: Exception) {
    }
  }

  private fun syncLockScreenWakeLock() {
    val state = SpeakerSessionManager.state.value
    if (state.lockScreenPlaybackEnabled && !userStopRequested) {
      acquirePlaybackWakeLock()
    } else {
      releasePlaybackWakeLock()
    }
  }

  @SuppressLint("WakelockTimeout")
  private fun acquirePlaybackWakeLock() {
    try {
      val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
      val lock =
        cpuWakeLock
          ?: powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "t_wave::SpeakerPlaybackWakeLock")
            .apply {
              setReferenceCounted(false)
              cpuWakeLock = this
            }
      if (!lock.isHeld) {
        lock.acquire(12 * 60 * 60 * 1000L)
      }
    } catch (_: Exception) {
    }
  }

  private fun releasePlaybackWakeLock() {
    val lock = cpuWakeLock ?: return
    try {
      if (lock.isHeld) {
        lock.release()
      }
    } catch (_: Exception) {
    }
  }

  private fun registerScreenStateReceiver() {
    if (screenStateReceiverRegistered) return
    val filter =
      IntentFilter().apply {
        addAction(Intent.ACTION_SCREEN_OFF)
        addAction(Intent.ACTION_USER_PRESENT)
      }
    try {
      ContextCompat.registerReceiver(
        this,
        screenStateReceiver,
        filter,
        ContextCompat.RECEIVER_NOT_EXPORTED,
      )
      screenStateReceiverRegistered = true
    } catch (_: Exception) {
    }
  }

  private fun unregisterScreenStateReceiver() {
    if (!screenStateReceiverRegistered) return
    screenStateReceiverRegistered = false
    try {
      unregisterReceiver(screenStateReceiver)
    } catch (_: IllegalArgumentException) {
    }
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
          )
          .apply { description = getString(R.string.notification_channel_desc) }
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  private fun startSpeakerPipeline() {
    if (receiverJob?.isActive == true) return

    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bluetoothManager?.adapter
    if (adapter == null) {
      stopSpeakerPipeline("Bluetooth hardware is not available on this device.")
      stopSelf()
      return
    }
    if (!hasBluetoothConnectPermission()) {
      stopSpeakerPipeline("BLUETOOTH_CONNECT permission is required to host Speaker Mode.")
      stopSelf()
      return
    }

    receiverJob =
      serviceScope.launch {
        val saved = preferences.settingsFlow.first()
        SpeakerSessionManager.setAutoReconnectEnabled(saved.autoReconnectEnabled)
        SpeakerSessionManager.setSpeakerMode(saved.speakerMode)
        SpeakerSessionManager.setQualityProfile(saved.qualityProfile)
        SpeakerSessionManager.setBufferProfile(saved.bufferProfile)
        SpeakerSessionManager.setLowLatencyMode(saved.lowLatencyMode)
        SpeakerSessionManager.setUltraLowLatencyMode(saved.ultraLowLatencyMode)
        SpeakerSessionManager.setSyncOffsetMs(saved.syncOffsetMs)
        SpeakerSessionManager.setLockScreenPlaybackEnabled(saved.lockScreenPlaybackEnabled)
        SpeakerSessionManager.setLastConnectedDevice(saved.lastDevice)
        syncLockScreenWakeLock()
        refreshForegroundNotification()

        if (saved.speakerMode == SpeakerMode.A2DP_SINK) {
          runA2dpSinkMonitorLoop(adapter)
        } else {
          if (!adapter.isEnabled) {
            stopSpeakerPipeline("Bluetooth is turned off. Enable Bluetooth to start Speaker Mode.")
            stopSelf()
            return@launch
          }
          runRfcommAcceptLoop(adapter)
        }
      }
  }

  @SuppressLint("MissingPermission")
  private suspend fun runA2dpSinkMonitorLoop(adapter: BluetoothAdapter) {
    // Enforce root privilege requirement on the service side as well
    val rootCheck = rootA2dpController.requestRootAndInspect()
    SpeakerSessionManager.setRootStatus(rootCheck)
    if (!rootCheck.isRootGranted) {
      stopSpeakerPipeline(
        "Root privilege (su) is required to enable A2DP Sink Bluetooth Speaker Mode."
      )
      stopSelf()
      return
    }

    if (!rootCheck.isA2dpSinkEnabled) {
      val enableResult =
        rootA2dpController.enableA2dpSink(
          packageName = packageName,
          cachedOriginalCod = SpeakerSessionManager.state.value.rootStatus.originalClassOfDevice,
        )
      enableResult.onSuccess { updated ->
        SpeakerSessionManager.setRootStatus(updated)
        updated.originalClassOfDevice?.let { preferences.saveOriginalClassOfDevice(it) }
      }.onFailure { err ->
        stopSpeakerPipeline(
          err.localizedMessage ?: "Failed to unlock A2DP Sink profile via root."
        )
        stopSelf()
        return
      }
    }

    // Wait briefly if Bluetooth is still coming back up after a cold com.android.bluetooth restart
    var waitTries = 0
    while (!adapter.isEnabled && waitTries < 12 && serviceScope.isActive && !userStopRequested) {
      delay(400L)
      waitTries++
    }
    if (!adapter.isEnabled) {
      stopSpeakerPipeline("Bluetooth did not re-enable after A2DP Sink stack restart.")
      stopSelf()
      return
    }

    // Crucial: abandon any focus previously held by t_wave so com.android.bluetooth's
    // A2dpSinkStreamHandler does not receive AUDIOFOCUS_LOSS and mute its output AudioTrack.
    abandonAudioFocus()

    registerA2dpSinkReceiver()
    bindA2dpSinkProxy(adapter)

    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val wakeSession = A2dpSinkAudioRouter.MediaBrowserWakeSession(applicationContext)
    mediaBrowserWakeSession = wakeSession
    val bridge = A2dpSinkAudioRouter.HalCaptureBridge(applicationContext, serviceScope)
    halCaptureBridge = bridge

    val initialState = SpeakerSessionManager.state.value
    A2dpSinkAudioRouter.ensureSpeakerStreamAudible(
      audioManager = audioManager,
      appGain = initialState.gain,
      isAppMuted = initialState.isMuted,
      bumpZeroVolume = true,
    )

    SpeakerSessionManager.setListening()
    refreshForegroundNotification()

    var phase = 0f
    var lastConnected: ConnectedDeviceInfo? = null
    var connectedSinceMs = 0L
    var attemptedRecordGrant = false
    var tickCount = 0

    try {
      while (serviceScope.isActive && !userStopRequested) {
        if (!hasBluetoothConnectPermission()) {
          stopSpeakerPipeline("BLUETOOTH_CONNECT permission revoked.")
          stopSelf()
          return
        }

        val currentDevice = resolveCurrentA2dpDevice(pollDumpsys = tickCount % 4 == 0)
        tickCount++

        if (currentDevice != null) {
          if (lastConnected == null || lastConnected.address != currentDevice.address) {
            lastConnected = currentDevice
            connectedSinceMs = System.currentTimeMillis()
            SpeakerSessionManager.setConnected(currentDevice)
            preferences.saveLastConnectedDevice(currentDevice)
            refreshForegroundNotification()

            val curState = SpeakerSessionManager.state.value
            A2dpSinkAudioRouter.ensureSpeakerStreamAudible(
              audioManager = audioManager,
              appGain = curState.gain,
              isAppMuted = curState.isMuted,
              bumpZeroVolume = true,
            )
            wakeSession.connectAndPlay()
          }

          val btDevice =
            try {
              adapter.getRemoteDevice(currentDevice.address)
            } catch (_: IllegalArgumentException) {
              null
            }
          val proxyAudio = A2dpSinkAudioRouter.inspectProxyAudio(a2dpSinkProxy, btDevice)
          val musicActive = audioManager?.isMusicActive == true

          if (reconfigureAudioRequested && bridge.isRunning) {
            reconfigureAudioRequested = false
            bridge.stop()
          }

          if (
            !bridge.isRunning &&
              !musicActive &&
              (wakeA2dpRequested || System.currentTimeMillis() - connectedSinceMs >= 1_200L)
          ) {
            wakeA2dpRequested = false
            val a2dpInput = A2dpSinkAudioRouter.findA2dpInputDevice(audioManager)
            if (a2dpInput != null) {
              if (!A2dpSinkAudioRouter.hasRecordAudioPermission(this) && !attemptedRecordGrant) {
                attemptedRecordGrant = true
                rootA2dpController.grantRecordAudioViaRoot(packageName)
              }
              val latestAudioState = SpeakerSessionManager.state.value
              bridge.startIfNeeded(
                inputDevice = a2dpInput,
                sampleRateHz = proxyAudio.sampleRateHz,
                lowLatency = latestAudioState.lowLatencyMode,
                ultraLowLatency = latestAudioState.ultraLowLatencyMode,
              )
            }
          }

          if (!bridge.isRunning) {
            val a2dpStreaming = proxyAudio.isPlaying || musicActive
            phase += if (a2dpStreaming) 0.45f else 0.12f
            val bands =
              List(AudioTelemetry.BAND_COUNT) { idx ->
                if (a2dpStreaming) {
                  (0.28f + 0.52f * abs(sin(phase + idx * 0.31f))).coerceIn(0f, 1f)
                } else {
                  0.08f
                }
              }
            val level = if (a2dpStreaming) 0.55f + 0.25f * abs(sin(phase)) else 0.06f
            val rateKhz =
              if (proxyAudio.sampleRateHz % 1000 == 0) "${proxyAudio.sampleRateHz / 1000} kHz"
              else "${proxyAudio.sampleRateHz / 1000f} kHz"
            val outputDesc =
              if (a2dpStreaming) "A2DP Sink • Playing ($rateKhz)"
              else "A2DP Sink • Connected (Waiting Audio)"
            val curLatencyState = SpeakerSessionManager.state.value

            SpeakerSessionManager.updateTelemetry(
              AudioTelemetry(
                leftLevel = level,
                rightLevel = level * 0.94f,
                spectrumBands = bands,
                bufferFillRatio = if (a2dpStreaming) 0.42f else 0.08f,
                throughputKbps = if (a2dpStreaming) 328f else 0f,
                estimatedLatencyMs =
                  PcmSyncDelayLine.computeEffectiveLatencyMs(
                    baseLatencyMs =
                      when {
                        curLatencyState.ultraLowLatencyMode -> 2
                        curLatencyState.lowLatencyMode -> 28
                        else -> 85
                      },
                    syncOffsetMs = curLatencyState.syncOffsetMs,
                  ),
                sampleRateHz = proxyAudio.sampleRateHz,
                bitDepth = proxyAudio.bitDepth,
                channels = proxyAudio.channels,
                activeOutputDescription = outputDesc,
              )
            )
          }
        } else {
          bridge.stop()
          connectedSinceMs = 0L
          val droppedDevice = lastConnected
          lastConnected = null

          if (droppedDevice != null &&
            !userStopRequested &&
            adapter.isEnabled &&
            SpeakerSessionManager.state.value.autoReconnectEnabled
          ) {
            val reconnected = attemptA2dpSinkAutoReconnect(adapter, droppedDevice)
            if (reconnected != null) {
              lastConnected = reconnected
              connectedSinceMs = System.currentTimeMillis()
              SpeakerSessionManager.setConnected(reconnected)
              preferences.saveLastConnectedDevice(reconnected)
              refreshForegroundNotification()
              wakeSession.connectAndPlay()
              continue
            }
          }

          if (SpeakerSessionManager.state.value.status != ConnectionStatus.LISTENING) {
            SpeakerSessionManager.setListening()
            refreshForegroundNotification()
          }
        }

        delay(450L)
      }
    } finally {
      bridge.stop()
      halCaptureBridge = null
      wakeSession.close()
      mediaBrowserWakeSession = null
      unregisterA2dpSinkReceiver()
      closeA2dpSinkProxy(adapter)
    }
  }

  private fun wakeA2dpAudioRoute() {
    wakeA2dpRequested = true
    abandonAudioFocus()
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val state = SpeakerSessionManager.state.value
    A2dpSinkAudioRouter.ensureSpeakerStreamAudible(
      audioManager = audioManager,
      appGain = state.gain,
      isAppMuted = state.isMuted,
      bumpZeroVolume = true,
    )
    mediaBrowserWakeSession?.connectAndPlay()
    serviceScope.launch {
      rootA2dpController.wakeA2dpAudioViaRoot(packageName)
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun resolveCurrentA2dpDevice(pollDumpsys: Boolean): ConnectedDeviceInfo? {
    if (a2dpBroadcastConnected == true && latestA2dpBroadcastDevice != null) {
      return latestA2dpBroadcastDevice
    }

    val proxy = a2dpSinkProxy
    if (proxy != null) {
      try {
        val connected = proxy.connectedDevices
        val first = connected.firstOrNull()
        if (first != null) {
          val name = first.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio Source"
          return ConnectedDeviceInfo(name, first.address)
        }
      } catch (_: Exception) {
      }
    }

    if (pollDumpsys && a2dpBroadcastConnected != false) {
      return rootA2dpController.queryConnectedA2dpDevice()
    }
    return null
  }

  @SuppressLint("MissingPermission")
  private suspend fun attemptA2dpSinkAutoReconnect(
    adapter: BluetoothAdapter,
    targetDevice: ConnectedDeviceInfo,
  ): ConnectedDeviceInfo? {
    for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
      if (!serviceScope.isActive ||
        userStopRequested ||
        !adapter.isEnabled ||
        !SpeakerSessionManager.state.value.autoReconnectEnabled
      ) {
        return null
      }

      SpeakerSessionManager.setReconnecting(targetDevice, attempt, MAX_RECONNECT_ATTEMPTS)
      refreshForegroundNotification()

      val remoteDevice =
        try {
          adapter.getRemoteDevice(targetDevice.address)
        } catch (_: IllegalArgumentException) {
          return null
        }

      val proxy = a2dpSinkProxy
      if (proxy != null) {
        try {
          val connectMethod =
            proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
          connectMethod.invoke(proxy, remoteDevice)
        } catch (_: Exception) {
        }
      }

      delay(RECONNECT_RETRY_DELAY_MS)

      val resolved = resolveCurrentA2dpDevice(pollDumpsys = true)
      if (resolved != null) {
        return resolved
      }
    }

    SpeakerSessionManager.setError(
      "Could not re-establish A2DP link to ${targetDevice.name}. Ready for incoming speaker connections."
    )
    return null
  }

  @SuppressLint("MissingPermission")
  private fun bindA2dpSinkProxy(adapter: BluetoothAdapter) {
    try {
      adapter.getProfileProxy(
        applicationContext,
        object : BluetoothProfile.ServiceListener {
          override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == RootA2dpController.A2DP_SINK_PROFILE_ID && proxy != null) {
              a2dpSinkProxy = proxy
              // Allow incoming A2DP Sink connections from all already-paired devices
              if (hasBluetoothConnectPermission()) {
                try {
                  adapter.bondedDevices?.forEach { device ->
                    allowDevicePolicyOnProxy(proxy, device)
                  }
                } catch (_: Exception) {
                }
              }
            }
          }

          override fun onServiceDisconnected(profile: Int) {
            if (profile == RootA2dpController.A2DP_SINK_PROFILE_ID) {
              a2dpSinkProxy = null
            }
          }
        },
        RootA2dpController.A2DP_SINK_PROFILE_ID,
      )
    } catch (_: Exception) {
    }
  }

  @SuppressLint("MissingPermission")
  private fun connectToA2dpSource(address: String) {
    val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bluetoothManager?.adapter ?: return
    if (!hasBluetoothConnectPermission() || !adapter.isEnabled) return

    serviceScope.launch {
      SpeakerSessionManager.setConnectingDeviceAddress(address)
      try {
        rootA2dpController.setA2dpSinkPolicyViaRoot(packageName)

        if (a2dpSinkProxy == null) {
          bindA2dpSinkProxy(adapter)
          var waitTicks = 0
          while (a2dpSinkProxy == null && waitTicks < 10) {
            delay(250L)
            waitTicks++
          }
        }

        val proxy = a2dpSinkProxy
        if (proxy == null) {
          SpeakerSessionManager.setError(
            "A2DP Sink profile proxy unavailable (A2dpSinkService inactive or BLUETOOTH_PRIVILEGED restricted). Unpair & re-pair from the sender device."
          )
          return@launch
        }

        val device =
          try {
            adapter.getRemoteDevice(address)
          } catch (_: IllegalArgumentException) {
            SpeakerSessionManager.setError("Invalid Bluetooth device address: $address")
            return@launch
          }

        disconnectConflictingA2dpSource(adapter, device)
        allowDevicePolicyOnProxy(proxy, device)

        val invoked = invokeConnectOnProxy(proxy, device)
        if (!invoked) {
          SpeakerSessionManager.setError(
            "System blocked app-initiated A2DP Sink connect. Unpair & re-pair \"${adapter.name ?: "this phone"}\" on your sender device and connect from there."
          )
          return@launch
        }

        // Wait briefly for connection state transition
        for (i in 0 until 10) {
          delay(500L)
          val connected = resolveCurrentA2dpDevice(pollDumpsys = i % 2 == 1)
          if (connected != null && connected.address.equals(address, ignoreCase = true)) {
            SpeakerSessionManager.setConnected(connected)
            preferences.saveLastConnectedDevice(connected)
            refreshForegroundNotification()
            return@launch
          }
        }
      } finally {
        SpeakerSessionManager.setConnectingDeviceAddress(null)
      }
    }
  }

  private fun allowDevicePolicyOnProxy(proxy: BluetoothProfile, device: BluetoothDevice) {
    try {
      val setPolicy =
        proxy.javaClass.getMethod(
          "setConnectionPolicy",
          BluetoothDevice::class.java,
          Int::class.javaPrimitiveType,
        )
      setPolicy.invoke(proxy, device, RootA2dpController.CONNECTION_POLICY_ALLOWED)
      return
    } catch (_: Exception) {
    }
    try {
      val setPriority =
        proxy.javaClass.getMethod(
          "setPriority",
          BluetoothDevice::class.java,
          Int::class.javaPrimitiveType,
        )
      setPriority.invoke(proxy, device, RootA2dpController.CONNECTION_POLICY_ALLOWED)
    } catch (_: Exception) {
    }
  }

  private fun invokeConnectOnProxy(proxy: BluetoothProfile, device: BluetoothDevice): Boolean =
    try {
      val connectMethod = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
      (connectMethod.invoke(proxy, device) as? Boolean) ?: true
    } catch (_: Exception) {
      false
    }

  @SuppressLint("MissingPermission")
  private fun disconnectConflictingA2dpSource(
    adapter: BluetoothAdapter,
    device: BluetoothDevice,
  ) {
    try {
      adapter.getProfileProxy(
        applicationContext,
        object : BluetoothProfile.ServiceListener {
          override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == RootA2dpController.A2DP_SOURCE_PROFILE_ID && proxy != null) {
              try {
                val disconnect =
                  proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                disconnect.invoke(proxy, device)
              } catch (_: Exception) {
              } finally {
                adapter.closeProfileProxy(RootA2dpController.A2DP_SOURCE_PROFILE_ID, proxy)
              }
            }
          }

          override fun onServiceDisconnected(profile: Int) {}
        },
        RootA2dpController.A2DP_SOURCE_PROFILE_ID,
      )
    } catch (_: Exception) {
    }
  }

  private fun closeA2dpSinkProxy(adapter: BluetoothAdapter) {
    val proxy = a2dpSinkProxy ?: return
    a2dpSinkProxy = null
    try {
      adapter.closeProfileProxy(RootA2dpController.A2DP_SINK_PROFILE_ID, proxy)
    } catch (_: Exception) {
    }
  }

  private fun registerA2dpSinkReceiver() {
    if (a2dpReceiverRegistered) return
    val filter = IntentFilter(RootA2dpController.ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED)
    ContextCompat.registerReceiver(
      this,
      a2dpSinkStateReceiver,
      filter,
      ContextCompat.RECEIVER_EXPORTED,
    )
    a2dpReceiverRegistered = true
  }

  private fun unregisterA2dpSinkReceiver() {
    if (!a2dpReceiverRegistered) return
    a2dpReceiverRegistered = false
    try {
      unregisterReceiver(a2dpSinkStateReceiver)
    } catch (_: IllegalArgumentException) {
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun runRfcommAcceptLoop(adapter: BluetoothAdapter) {
    while (serviceScope.isActive && !userStopRequested) {
      var serverSocket: BluetoothServerSocket? = null
      try {
        if (!hasBluetoothConnectPermission()) {
          stopSpeakerPipeline("BLUETOOTH_CONNECT permission revoked.")
          stopSelf()
          return
        }

        val requirePairing = SpeakerSessionManager.state.value.requirePairing
        serverSocket =
          if (requirePairing) {
            adapter.listenUsingRfcommWithServiceRecord(SERVICE_RECORD_NAME, SPP_UUID)
          } else {
            adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_RECORD_NAME, SPP_UUID)
          }
        activeServerSocket = serverSocket

        SpeakerSessionManager.setListening()
        refreshForegroundNotification()
        startBleDiscoveryBeacon(adapter)

        val clientSocket = serverSocket.accept()
        activeClientSocket = clientSocket

        stopBleDiscoveryBeacon()
        closeServerSocketQuietly()

        val connectedInfo = extractDeviceInfo(clientSocket)
        handleConnectedSession(adapter, clientSocket, connectedInfo)
      } catch (e: SecurityException) {
        stopSpeakerPipeline("Bluetooth permission denied: ${e.localizedMessage ?: "Security error"}")
        stopSelf()
        return
      } catch (e: IOException) {
        if (
          !serviceScope.isActive ||
            userStopRequested ||
            SpeakerSessionManager.state.value.status == ConnectionStatus.IDLE
        ) {
          return
        }
        if (!adapter.isEnabled) {
          stopSpeakerPipeline("Bluetooth radio was disabled.")
          stopSelf()
          return
        }
        if (activeServerSocket != null) {
          SpeakerSessionManager.setError(
            "RFCOMM connection interrupted: ${e.localizedMessage ?: "I/O error"}"
          )
        }
      } finally {
        stopBleDiscoveryBeacon()
        closeClientSocketQuietly()
        closeServerSocketQuietly()
      }
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun handleConnectedSession(
    adapter: BluetoothAdapter,
    initialSocket: BluetoothSocket,
    initialDevice: ConnectedDeviceInfo,
  ) {
    var currentSocket: BluetoothSocket? = initialSocket
    var currentDevice = initialDevice

    while (currentSocket != null && serviceScope.isActive && !userStopRequested) {
      SpeakerSessionManager.setConnected(currentDevice)
      preferences.saveLastConnectedDevice(currentDevice)
      refreshForegroundNotification()

      val droppedUnexpectedly = streamAudioFromSocket(currentSocket)
      closeClientSocketQuietly()
      currentSocket = null

      if (
        !droppedUnexpectedly ||
          userStopRequested ||
          !serviceScope.isActive ||
          !adapter.isEnabled ||
          !SpeakerSessionManager.state.value.autoReconnectEnabled ||
          !hasBluetoothConnectPermission()
      ) {
        break
      }

      currentSocket = attemptAutoReconnect(adapter, currentDevice)
    }
  }

  @SuppressLint("MissingPermission")
  private suspend fun attemptAutoReconnect(
    adapter: BluetoothAdapter,
    targetDevice: ConnectedDeviceInfo,
  ): BluetoothSocket? {
    for (attempt in 1..MAX_RECONNECT_ATTEMPTS) {
      if (
        !serviceScope.isActive ||
          userStopRequested ||
          !adapter.isEnabled ||
          !SpeakerSessionManager.state.value.autoReconnectEnabled ||
          !hasBluetoothConnectPermission()
      ) {
        return null
      }

      SpeakerSessionManager.setReconnecting(targetDevice, attempt, MAX_RECONNECT_ATTEMPTS)
      refreshForegroundNotification()

      val remoteDevice =
        try {
          adapter.getRemoteDevice(targetDevice.address)
        } catch (_: IllegalArgumentException) {
          return null
        }

      val requirePairing = SpeakerSessionManager.state.value.requirePairing
      val outgoingSocket =
        try {
          if (requirePairing) {
            remoteDevice.createRfcommSocketToServiceRecord(SPP_UUID)
          } else {
            remoteDevice.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
          }
        } catch (_: IOException) {
          null
        } catch (_: SecurityException) {
          return null
        }

      if (outgoingSocket != null) {
        try {
          activeClientSocket = outgoingSocket
          outgoingSocket.connect()
          return outgoingSocket
        } catch (_: IOException) {
          activeClientSocket = null
          try {
            outgoingSocket.close()
          } catch (_: IOException) {
          }
        } catch (_: SecurityException) {
          activeClientSocket = null
          try {
            outgoingSocket.close()
          } catch (_: IOException) {
          }
          return null
        }
      }

      if (attempt < MAX_RECONNECT_ATTEMPTS) {
        delay(RECONNECT_RETRY_DELAY_MS)
      }
    }

    SpeakerSessionManager.setError(
      "Could not re-establish link to ${targetDevice.name}. Listening for new connections."
    )
    return null
  }

  @SuppressLint("MissingPermission")
  private fun extractDeviceInfo(socket: BluetoothSocket): ConnectedDeviceInfo {
    val deviceName =
      try {
        if (hasBluetoothConnectPermission()) {
          socket.remoteDevice?.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio Source"
        } else {
          "Bluetooth Audio Source"
        }
      } catch (_: SecurityException) {
        "Bluetooth Audio Source"
      }
    val deviceAddress = socket.remoteDevice?.address ?: "Unknown"
    return ConnectedDeviceInfo(deviceName, deviceAddress)
  }

  @SuppressLint("MissingPermission")
  private fun startBleDiscoveryBeacon(adapter: BluetoothAdapter) {
    if (!hasBluetoothAdvertisePermission()) {
      SpeakerSessionManager.setBleAdvertisingActive(false)
      return
    }
    val advertiser =
      try {
        adapter.bluetoothLeAdvertiser
      } catch (_: Exception) {
        null
      } ?: run {
        SpeakerSessionManager.setBleAdvertisingActive(false)
        return
      }

    if (bleAdvertiseCallback != null) return

    val settings =
      AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
        .setConnectable(true)
        .build()

    val advertiseData =
      AdvertiseData.Builder()
        .setIncludeDeviceName(false)
        .addServiceUuid(ParcelUuid(SPP_UUID))
        .build()

    val scanResponse = AdvertiseData.Builder().setIncludeDeviceName(true).build()

    val callback =
      object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
          SpeakerSessionManager.setBleAdvertisingActive(true)
        }

        override fun onStartFailure(errorCode: Int) {
          SpeakerSessionManager.setBleAdvertisingActive(false)
        }
      }

    try {
      bleAdvertiser = advertiser
      bleAdvertiseCallback = callback
      advertiser.startAdvertising(settings, advertiseData, scanResponse, callback)
    } catch (_: SecurityException) {
      bleAdvertiseCallback = null
      SpeakerSessionManager.setBleAdvertisingActive(false)
    } catch (_: IllegalArgumentException) {
      bleAdvertiseCallback = null
      SpeakerSessionManager.setBleAdvertisingActive(false)
    }
  }

  @SuppressLint("MissingPermission")
  private fun stopBleDiscoveryBeacon() {
    val callback = bleAdvertiseCallback ?: return
    val advertiser = bleAdvertiser
    bleAdvertiseCallback = null
    bleAdvertiser = null
    SpeakerSessionManager.setBleAdvertisingActive(false)
    if (advertiser != null && hasBluetoothAdvertisePermission()) {
      try {
        advertiser.stopAdvertising(callback)
      } catch (_: Exception) {
      }
    }
  }

  /**
   * Reads PCM bytes from [socket] into a frame-aligned high-res [AudioTrack].
   * Returns `true` if the stream terminated due to an unexpected link drop / I/O failure.
   */
  private fun streamAudioFromSocket(socket: BluetoothSocket): Boolean {
    requestAudioFocus()

    var currentProfile = SpeakerSessionManager.state.value.qualityProfile
    var currentBufferProfile = SpeakerSessionManager.state.value.bufferProfile
    var currentLowLatency = SpeakerSessionManager.state.value.lowLatencyMode
    var currentUltraLowLatency = SpeakerSessionManager.state.value.ultraLowLatencyMode
    reconfigureAudioRequested = false

    val previousThreadPriority =
      try {
        Process.getThreadPriority(Process.myTid())
      } catch (_: Exception) {
        Process.THREAD_PRIORITY_DEFAULT
      }
    if (currentLowLatency || currentUltraLowLatency) {
      try {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
      } catch (_: Exception) {
      }
    }

    var trackConfig =
      PcmAudioTrackConfigurator.buildConfiguredTrack(
        context = applicationContext,
        profile = currentProfile,
        lowLatency = currentLowLatency,
        bufferProfile = currentBufferProfile,
        ultraLowLatency = currentUltraLowLatency,
      )
    activeAudioTrack = trackConfig.track

    val readBuffer = ByteArray(STANDARD_READ_CHUNK_BYTES)
    val alignedBuffer = ByteArray(STANDARD_READ_CHUNK_BYTES + PcmAnalyzer.MAX_FRAME_SIZE_BYTES)
    val syncedBuffer = ByteArray(STANDARD_READ_CHUNK_BYTES * 4 + PcmAnalyzer.MAX_FRAME_SIZE_BYTES)
    val floatBuffer = FloatArray((syncedBuffer.size / 2) + 8)
    val carryBuffer = ByteArray(PcmAnalyzer.MAX_FRAME_SIZE_BYTES)
    val delayLine = PcmSyncDelayLine()
    var carryCount = 0

    var totalBytesReceived = 0L
    var totalFramesWritten = 0L
    var windowStartNanos = System.nanoTime()
    var windowBytes = 0L
    var currentThroughputKbps = 0f
    var lastTelemetryPublishMs = 0L
    var droppedUnexpectedly = false

    try {
      trackConfig.track.setVolume(SpeakerSessionManager.effectiveVolume())
      trackConfig.track.play()
      SpeakerSessionManager.updateTelemetry(
        AudioTelemetry(
          bufferCapacityBytes = trackConfig.bufferSizing.bufferSizeBytes,
          estimatedLatencyMs =
            PcmSyncDelayLine.computeEffectiveLatencyMs(
              trackConfig.bufferSizing.bufferDurationMs,
              SpeakerSessionManager.state.value.syncOffsetMs,
            ),
          sampleRateHz = trackConfig.actualSampleRateHz,
          bitDepth = trackConfig.trackBitDepth,
          channels = currentProfile.channels,
          activeOutputDescription = trackConfig.outputDescription,
        )
      )

      val inputStream = socket.inputStream

      while (serviceScope.isActive && !userStopRequested && socket.isConnected) {
        if (reconfigureAudioRequested) {
          reconfigureAudioRequested = false
          val latestState = SpeakerSessionManager.state.value
          val targetProfile = latestState.qualityProfile
          val targetBufferProfile = latestState.bufferProfile
          val targetLowLatency = latestState.lowLatencyMode
          val targetUltraLowLatency = latestState.ultraLowLatencyMode
          if (
            targetProfile != currentProfile ||
              targetBufferProfile != currentBufferProfile ||
              targetLowLatency != currentLowLatency ||
              targetUltraLowLatency != currentUltraLowLatency
          ) {
            releaseAudioTrackQuietly()
            currentProfile = targetProfile
            currentBufferProfile = targetBufferProfile
            currentLowLatency = targetLowLatency
            currentUltraLowLatency = targetUltraLowLatency
            carryCount = 0
            totalFramesWritten = 0L
            delayLine.reset()
            try {
              Process.setThreadPriority(
                if (currentLowLatency || currentUltraLowLatency)
                  Process.THREAD_PRIORITY_URGENT_AUDIO
                else previousThreadPriority
              )
            } catch (_: Exception) {
            }
            trackConfig =
              PcmAudioTrackConfigurator.buildConfiguredTrack(
                context = applicationContext,
                profile = currentProfile,
                lowLatency = currentLowLatency,
                bufferProfile = currentBufferProfile,
                ultraLowLatency = currentUltraLowLatency,
              )
            activeAudioTrack = trackConfig.track
            trackConfig.track.setVolume(SpeakerSessionManager.effectiveVolume())
            trackConfig.track.play()
          }
        }

        val chunkSize =
          when {
            currentUltraLowLatency -> ULTRA_LOW_LATENCY_READ_CHUNK_BYTES
            currentLowLatency -> LOW_LATENCY_READ_CHUNK_BYTES
            else -> STANDARD_READ_CHUNK_BYTES
          }
        val bytesRead = inputStream.read(readBuffer, 0, chunkSize)
        if (bytesRead <= 0) {
          if (!userStopRequested) droppedUnexpectedly = true
          break
        }

        totalBytesReceived += bytesRead
        windowBytes += bytesRead

        val inputFrameSize = currentProfile.frameSizeBytes
        val (alignedLength, nextCarryCount) =
          PcmAnalyzer.alignToFrameBoundary(
            carryBuffer = carryBuffer,
            carryCount = carryCount,
            incoming = readBuffer,
            incomingLength = bytesRead,
            outputBuffer = alignedBuffer,
            frameSizeBytes = inputFrameSize,
          )
        carryCount = nextCarryCount

        val liveSyncOffsetMs = SpeakerSessionManager.state.value.syncOffsetMs
        val syncedLength =
          if (alignedLength > 0) {
            delayLine.processChunk(
              input = alignedBuffer,
              length = alignedLength,
              output = syncedBuffer,
              sampleRateHz = currentProfile.sampleRateHz,
              frameSizeBytes = inputFrameSize,
              syncOffsetMs = liveSyncOffsetMs,
            )
          } else {
            0
          }

        if (syncedLength > 0) {
          val track = trackConfig.track
          track.setVolume(SpeakerSessionManager.effectiveVolume())
          val framesInChunk = syncedLength / inputFrameSize

          val writeStatus =
            if (trackConfig.usesFloatConversion) {
              val floatSamples =
                PcmAnalyzer.convertLePcmToFloat(
                  pcmBytes = syncedBuffer,
                  length = syncedLength,
                  bitDepth = currentProfile.bitDepth,
                  floatOutput = floatBuffer,
                )
              track.write(floatBuffer, 0, floatSamples, AudioTrack.WRITE_BLOCKING)
            } else {
              track.write(syncedBuffer, 0, syncedLength, AudioTrack.WRITE_BLOCKING)
            }

          if (writeStatus < 0) {
            break
          }
          totalFramesWritten += framesInChunk
        }

        val nowMs = System.currentTimeMillis()
        val nowNanos = System.nanoTime()
        val elapsedNanos = nowNanos - windowStartNanos
        if (elapsedNanos >= 500_000_000L) {
          val seconds = elapsedNanos / 1_000_000_000f
          currentThroughputKbps = (windowBytes * 8f) / 1000f / seconds
          windowBytes = 0L
          windowStartNanos = nowNanos
        }

        val publishIntervalMs =
          when {
            currentUltraLowLatency -> 24L
            currentLowLatency -> 30L
            else -> 45L
          }
        if (nowMs - lastTelemetryPublishMs >= publishIntervalMs && alignedLength > 0) {
          lastTelemetryPublishMs = nowMs
          val analysisBuf = if (syncedLength > 0) syncedBuffer else alignedBuffer
          val analysisLen = if (syncedLength > 0) syncedLength else alignedLength
          val analysis =
            PcmAnalyzer.analyzeStereoPcm(
              pcmBytes = analysisBuf,
              length = analysisLen,
              bitDepth = currentProfile.bitDepth,
            )

          val trackFrameBytes = trackConfig.bufferSizing.trackBytesPerFrame
          val trackBufBytes = trackConfig.bufferSizing.bufferSizeBytes
          val bufferCapacityFrames = trackConfig.bufferSizing.bufferFrames
          val playedFrames = trackConfig.track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
          val queuedFrames =
            (totalFramesWritten - playedFrames).coerceIn(0L, bufferCapacityFrames.toLong())
          val incomingAvailable =
            try {
              inputStream.available()
            } catch (_: IOException) {
              0
            }

          val bufferedTrackBytes = (queuedFrames * trackFrameBytes).toInt()
          val totalBufferedBytes = (bufferedTrackBytes + incomingAvailable).coerceIn(0, trackBufBytes)
          val fillRatio = (totalBufferedBytes.toFloat() / trackBufBytes.toFloat()).coerceIn(0f, 1f)
          val latencyFloor =
            when {
              currentUltraLowLatency -> 2
              trackConfig.lowLatencyApplied -> 4
              else -> 12
            }
          val baseLatencyMs =
            ((queuedFrames.toFloat() / trackConfig.actualSampleRateHz.toFloat()) * 1000f)
              .toInt()
              .coerceAtLeast(latencyFloor)
          val latencyMs = PcmSyncDelayLine.computeEffectiveLatencyMs(baseLatencyMs, liveSyncOffsetMs)

          SpeakerSessionManager.updateTelemetry(
            AudioTelemetry(
              leftLevel = analysis.leftLevel,
              rightLevel = analysis.rightLevel,
              spectrumBands = analysis.bands,
              bufferFillRatio = fillRatio,
              throughputKbps = currentThroughputKbps,
              totalBytesReceived = totalBytesReceived,
              bufferedBytes = totalBufferedBytes,
              bufferCapacityBytes = trackBufBytes,
              estimatedLatencyMs = latencyMs,
              sampleRateHz = trackConfig.actualSampleRateHz,
              bitDepth = trackConfig.trackBitDepth,
              channels = currentProfile.channels,
              activeOutputDescription = trackConfig.outputDescription,
            )
          )
        }
      }
    } catch (_: IOException) {
      if (!userStopRequested && serviceScope.isActive) {
        droppedUnexpectedly = true
      }
    } finally {
      try {
        Process.setThreadPriority(previousThreadPriority)
      } catch (_: Exception) {
      }
      releaseAudioTrackQuietly()
      abandonAudioFocus()
    }
    return droppedUnexpectedly
  }

  private fun applyVolumeToActiveTrack() {
    val state = SpeakerSessionManager.state.value
    if (state.speakerMode == SpeakerMode.A2DP_SINK) {
      val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      A2dpSinkAudioRouter.applyA2dpStreamVolume(audioManager, state.gain, state.isMuted)
      halCaptureBridge?.updateVolume()
    }
    val track = activeAudioTrack ?: return
    try {
      if (track.state == AudioTrack.STATE_INITIALIZED) {
        track.setVolume(SpeakerSessionManager.effectiveVolume())
      }
    } catch (_: IllegalStateException) {
    }
  }

  private fun stopSpeakerPipeline(errorMessage: String?) {
    receiverJob?.cancel()
    receiverJob = null
    halCaptureBridge?.stop()
    halCaptureBridge = null
    mediaBrowserWakeSession?.close()
    mediaBrowserWakeSession = null
    unregisterA2dpSinkReceiver()
    unregisterScreenStateReceiver()
    stopBleDiscoveryBeacon()
    closeClientSocketQuietly()
    closeServerSocketQuietly()
    releaseAudioTrackQuietly()
    abandonAudioFocus()
    releasePlaybackWakeLock()
    SpeakerSessionManager.setIdle(errorMessage)
    updateMediaSessionState(title = "t_wave", subtitle = errorMessage ?: "Idle")
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
  }

  private fun closeServerSocketQuietly() {
    val socket = activeServerSocket ?: return
    activeServerSocket = null
    try {
      socket.close()
    } catch (_: IOException) {
    }
  }

  private fun closeClientSocketQuietly() {
    try {
      activeClientSocket?.close()
    } catch (_: IOException) {
    } finally {
      activeClientSocket = null
    }
  }

  private fun releaseAudioTrackQuietly() {
    val track = activeAudioTrack ?: return
    activeAudioTrack = null
    try {
      if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
        track.pause()
        track.flush()
      }
      track.release()
    } catch (_: IllegalStateException) {
    }
  }

  private fun requestAudioFocus() {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val request =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build()
          )
          .build()
      audioFocusRequest = request
      audioManager.requestAudioFocus(request)
    } else {
      @Suppress("DEPRECATION")
      audioManager.requestAudioFocus(
        null,
        AudioManager.STREAM_MUSIC,
        AudioManager.AUDIOFOCUS_GAIN,
      )
    }
  }

  private fun abandonAudioFocus() {
    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
      audioFocusRequest = null
    } else {
      @Suppress("DEPRECATION")
      audioManager.abandonAudioFocus(null)
    }
  }

  private fun hasBluetoothConnectPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

  private fun hasBluetoothAdvertisePermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      true
    }

  companion object {
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val SERVICE_RECORD_NAME = "t_wave Speaker"
    private const val NOTIFICATION_CHANNEL_ID = "wavecast_speaker_channel"
    private const val NOTIFICATION_ID = 1001
    private const val STANDARD_READ_CHUNK_BYTES = 4096
    private const val LOW_LATENCY_READ_CHUNK_BYTES = 1024
    private const val ULTRA_LOW_LATENCY_READ_CHUNK_BYTES = 512
    private const val DEFAULT_AUDIO_BUFFER_BYTES = 16384
    const val MAX_RECONNECT_ATTEMPTS = 5
    private const val RECONNECT_RETRY_DELAY_MS = 2500L

    const val ACTION_START_SPEAKER = "com.example.action.START_SPEAKER"
    const val ACTION_STOP_SPEAKER = "com.example.action.STOP_SPEAKER"
    const val ACTION_UPDATE_GAIN = "com.example.action.UPDATE_GAIN"
    const val ACTION_TOGGLE_MUTE = "com.example.action.TOGGLE_MUTE"
    const val ACTION_LOCK_SCREEN_RECONFIGURE = "com.example.action.LOCK_SCREEN_RECONFIGURE"
    const val ACTION_RECONFIGURE_AUDIO = "com.example.action.RECONFIGURE_AUDIO"
    const val ACTION_RESTART_LISTENER = "com.example.action.RESTART_LISTENER"
    const val ACTION_CONNECT_A2DP_DEVICE = "com.example.action.CONNECT_A2DP_DEVICE"
    const val ACTION_WAKE_A2DP_AUDIO = "com.example.action.WAKE_A2DP_AUDIO"
    const val EXTRA_DEVICE_ADDRESS = "com.example.extra.DEVICE_ADDRESS"
  }
}
