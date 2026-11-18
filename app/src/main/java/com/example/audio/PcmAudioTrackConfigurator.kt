package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.example.model.AudioBufferProfile
import com.example.model.AudioQualityProfile

/**
 * Configures and validates Android [AudioTrack] instances for incoming PCM streams across
 * standard (44.1 kHz / 16-bit) and high-resolution (48–192 kHz, 24/32-bit / 32f) playback paths.
 */
object PcmAudioTrackConfigurator {

  const val DEFAULT_OUTPUT_FRAMES_PER_BUFFER = 256
  const val MIN_VALID_SAMPLE_RATE_HZ = 8_000
  const val MAX_HIRES_SAMPLE_RATE_HZ = 192_000

  data class BufferSizingResult(
    val minHardwareBufferBytes: Int,
    val bufferSizeBytes: Int,
    val bufferFrames: Int,
    val bufferDurationMs: Int,
    val trackBytesPerFrame: Int,
  )

  data class EncodingResolution(
    val encoding: Int,
    val usesFloatConversion: Boolean,
    val trackBitDepth: Int,
  )

  data class ConfiguredPcmAudioTrack(
    val track: AudioTrack,
    val requestedProfile: AudioQualityProfile,
    val actualSampleRateHz: Int,
    val actualEncoding: Int,
    val trackBitDepth: Int,
    val usesFloatConversion: Boolean,
    val lowLatencyApplied: Boolean,
    val bufferSizing: BufferSizingResult,
    val isHiResPath: Boolean,
    val outputDescription: String,
  ) {
    val isReady: Boolean
      get() = track.state == AudioTrack.STATE_INITIALIZED
  }

  fun bytesPerSampleForEncoding(encoding: Int): Int =
    when (encoding) {
      AudioFormat.ENCODING_PCM_16BIT -> 2
      AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
      AudioFormat.ENCODING_PCM_32BIT,
      AudioFormat.ENCODING_PCM_FLOAT -> 4
      else -> 2
    }

  fun resolveChannelMask(channels: Int): Int =
    if (channels <= 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

  fun resolveEncoding(
    sampleRateHz: Int,
    bitDepth: Int,
    channels: Int = 2,
  ): EncodingResolution {
    val clampedRate = sampleRateHz.coerceIn(MIN_VALID_SAMPLE_RATE_HZ, MAX_HIRES_SAMPLE_RATE_HZ)
    val channelMask = resolveChannelMask(channels)

    if (bitDepth == 16) {
      return EncodingResolution(
        encoding = AudioFormat.ENCODING_PCM_16BIT,
        usesFloatConversion = false,
        trackBitDepth = 16,
      )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val nativeEncoding =
        when (bitDepth) {
          24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
          32 -> AudioFormat.ENCODING_PCM_32BIT
          else -> AudioFormat.ENCODING_PCM_FLOAT
        }
      val minBuf = AudioTrack.getMinBufferSize(clampedRate, channelMask, nativeEncoding)
      if (minBuf > 0) {
        return EncodingResolution(
          encoding = nativeEncoding,
          usesFloatConversion = false,
          trackBitDepth = bitDepth,
        )
      }
    }

    return EncodingResolution(
      encoding = AudioFormat.ENCODING_PCM_FLOAT,
      usesFloatConversion = true,
      trackBitDepth = 32,
    )
  }

  /**
   * Computes a frame-aligned, HAL-burst-aligned PCM [AudioTrack] buffer size that always meets or
   * exceeds both [minHardwareBufferBytes] and Android frame alignment requirements (critical for
   * 24-bit packed stereo 6-byte frames and high-res 96/192 kHz bursts).
   */
  fun computeBufferSizing(
    sampleRateHz: Int,
    channels: Int,
    trackEncoding: Int,
    minHardwareBufferBytes: Int,
    framesPerBuffer: Int = DEFAULT_OUTPUT_FRAMES_PER_BUFFER,
    lowLatency: Boolean = false,
    bufferProfile: AudioBufferProfile = AudioBufferProfile.BALANCED,
    explicitRequestedBufferBytes: Int? = null,
    ultraLowLatency: Boolean = false,
  ): BufferSizingResult {
    val safeRate = sampleRateHz.coerceIn(MIN_VALID_SAMPLE_RATE_HZ, MAX_HIRES_SAMPLE_RATE_HZ)
    val safeChannels = channels.coerceIn(1, 2)
    val trackBytesPerFrame = safeChannels * bytesPerSampleForEncoding(trackEncoding)
    val halBurstBytes = framesPerBuffer.coerceAtLeast(64) * trackBytesPerFrame
    val safeMinHardware =
      if (minHardwareBufferBytes > 0) minHardwareBufferBytes else halBurstBytes * 2

    val effectiveLowLatency = lowLatency || ultraLowLatency
    val effectiveMultiplier =
      when {
        ultraLowLatency -> 1
        effectiveLowLatency && bufferProfile != AudioBufferProfile.HIRES_DEEP ->
          minOf(bufferProfile.multiplier, AudioBufferProfile.LOW_LATENCY.multiplier)
        else -> bufferProfile.multiplier
      }
    val floorBytes =
      when {
        ultraLowLatency -> halBurstBytes
        effectiveLowLatency && bufferProfile != AudioBufferProfile.HIRES_DEEP ->
          AudioBufferProfile.LOW_LATENCY.minFloorBytes
        else -> bufferProfile.minFloorBytes
      }

    // Scale burst count gently when running >= 88.2 kHz so high-res DAC queues don't underrun.
    val hiResRateScale = if (safeRate >= 88_200) 2 else 1
    val targetRawBytes =
      explicitRequestedBufferBytes?.let { maxOf(it, safeMinHardware) }
        ?: if (ultraLowLatency) {
          maxOf(safeMinHardware, halBurstBytes * hiResRateScale)
        } else {
          maxOf(
            safeMinHardware * effectiveMultiplier,
            halBurstBytes * effectiveMultiplier * 2 * hiResRateScale,
            floorBytes * hiResRateScale,
          )
        }

    // Always round UP to an exact whole multiple of trackBytesPerFrame.
    val alignedBytes =
      ((targetRawBytes + trackBytesPerFrame - 1) / trackBytesPerFrame) * trackBytesPerFrame
    val finalBytes =
      if (minHardwareBufferBytes > 0 && alignedBytes < minHardwareBufferBytes) {
        ((minHardwareBufferBytes + trackBytesPerFrame - 1) / trackBytesPerFrame) *
          trackBytesPerFrame
      } else {
        alignedBytes
      }

    val bufferFrames = (finalBytes / trackBytesPerFrame).coerceAtLeast(1)
    val durationMs = ((bufferFrames * 1000L) / safeRate).toInt().coerceAtLeast(1)

    return BufferSizingResult(
      minHardwareBufferBytes = safeMinHardware,
      bufferSizeBytes = finalBytes,
      bufferFrames = bufferFrames,
      bufferDurationMs = durationMs,
      trackBytesPerFrame = trackBytesPerFrame,
    )
  }

  fun previewBufferSizing(
    context: Context?,
    profile: AudioQualityProfile,
    lowLatency: Boolean,
    bufferProfile: AudioBufferProfile,
    overrideSampleRateHz: Int? = null,
    ultraLowLatency: Boolean = false,
  ): BufferSizingResult {
    val sampleRate =
      (overrideSampleRateHz ?: profile.sampleRateHz).coerceIn(
        MIN_VALID_SAMPLE_RATE_HZ,
        MAX_HIRES_SAMPLE_RATE_HZ,
      )
    val framesPerBuffer = readHalFramesPerBuffer(context)
    val resolution = resolveEncoding(sampleRate, profile.bitDepth, profile.channels)
    val minBuf =
      AudioTrack.getMinBufferSize(
        sampleRate,
        resolveChannelMask(profile.channels),
        resolution.encoding,
      )
    return computeBufferSizing(
      sampleRateHz = sampleRate,
      channels = profile.channels,
      trackEncoding = resolution.encoding,
      minHardwareBufferBytes = minBuf,
      framesPerBuffer = framesPerBuffer,
      lowLatency = lowLatency,
      bufferProfile = bufferProfile,
      ultraLowLatency = ultraLowLatency,
    )
  }

  fun buildConfiguredTrack(
    context: Context,
    profile: AudioQualityProfile,
    lowLatency: Boolean,
    bufferProfile: AudioBufferProfile = AudioBufferProfile.BALANCED,
    overrideSampleRateHz: Int? = null,
    explicitRequestedBufferBytes: Int? = null,
    ultraLowLatency: Boolean = false,
  ): ConfiguredPcmAudioTrack {
    val targetSampleRate =
      (overrideSampleRateHz ?: profile.sampleRateHz).coerceIn(
        MIN_VALID_SAMPLE_RATE_HZ,
        MAX_HIRES_SAMPLE_RATE_HZ,
      )
    val framesPerBuffer = readHalFramesPerBuffer(context)
    val primaryEncoding = resolveEncoding(targetSampleRate, profile.bitDepth, profile.channels)
    val effectiveLowLatency = lowLatency || ultraLowLatency

    // Attempt 1: Exact requested sampling rate + resolved encoding + requested latency mode.
    tryCreateTrack(
      sampleRateHz = targetSampleRate,
      channels = profile.channels,
      encodingResolution = primaryEncoding,
      framesPerBuffer = framesPerBuffer,
      lowLatency = effectiveLowLatency,
      bufferProfile = bufferProfile,
      explicitRequestedBufferBytes = explicitRequestedBufferBytes,
      ultraLowLatency = ultraLowLatency,
    )?.let { (track, sizing) ->
      return ConfiguredPcmAudioTrack(
        track = track,
        requestedProfile = profile,
        actualSampleRateHz = targetSampleRate,
        actualEncoding = primaryEncoding.encoding,
        trackBitDepth = primaryEncoding.trackBitDepth,
        usesFloatConversion = primaryEncoding.usesFloatConversion,
        lowLatencyApplied = effectiveLowLatency,
        bufferSizing = sizing,
        isHiResPath = targetSampleRate >= 48_000 && profile.bitDepth >= 24,
        outputDescription =
          formatOutputDescription(
            profileLabel = profile.shortBadge,
            usesFloatConversion = primaryEncoding.usesFloatConversion,
            lowLatency = effectiveLowLatency,
            ultraLowLatency = ultraLowLatency,
            sizing = sizing,
          ),
      )
    }

    // Attempt 2: Preserve high-res sample rate with Float32 PCM & standard mixer if FastMixer
    // rejected >= 96 kHz or native 24/32-bit packed failed HAL validation.
    val floatHiResEncoding =
      EncodingResolution(
        encoding = AudioFormat.ENCODING_PCM_FLOAT,
        usesFloatConversion = true,
        trackBitDepth = 32,
      )
    if (effectiveLowLatency || primaryEncoding.encoding != AudioFormat.ENCODING_PCM_FLOAT) {
      tryCreateTrack(
        sampleRateHz = targetSampleRate,
        channels = profile.channels,
        encodingResolution = floatHiResEncoding,
        framesPerBuffer = framesPerBuffer,
        lowLatency = false,
        bufferProfile = bufferProfile,
        explicitRequestedBufferBytes = explicitRequestedBufferBytes,
        ultraLowLatency = ultraLowLatency,
      )?.let { (track, sizing) ->
        return ConfiguredPcmAudioTrack(
          track = track,
          requestedProfile = profile,
          actualSampleRateHz = targetSampleRate,
          actualEncoding = floatHiResEncoding.encoding,
          trackBitDepth = 32,
          usesFloatConversion = true,
          lowLatencyApplied = false,
          bufferSizing = sizing,
          isHiResPath = targetSampleRate >= 48_000,
          outputDescription =
            formatOutputDescription(
              profileLabel = profile.shortBadge,
              usesFloatConversion = true,
              lowLatency = false,
              ultraLowLatency = false,
              sizing = sizing,
            ),
        )
      }
    }

    // Attempt 3: Guaranteed 48 kHz Float32 fallback compatible with all Android audio HALs.
    val safeRate = targetSampleRate.coerceAtMost(48_000)
    val fallbackSizing =
      computeBufferSizing(
        sampleRateHz = safeRate,
        channels = profile.channels,
        trackEncoding = AudioFormat.ENCODING_PCM_FLOAT,
        minHardwareBufferBytes =
          AudioTrack.getMinBufferSize(
            safeRate,
            resolveChannelMask(profile.channels),
            AudioFormat.ENCODING_PCM_FLOAT,
          ),
        framesPerBuffer = framesPerBuffer,
        lowLatency = false,
        bufferProfile = bufferProfile,
        explicitRequestedBufferBytes = explicitRequestedBufferBytes,
      )
    val fallbackTrack =
      buildPlatformTrack(
        sampleRateHz = safeRate,
        channels = profile.channels,
        encoding = AudioFormat.ENCODING_PCM_FLOAT,
        bufferSizeBytes = fallbackSizing.bufferSizeBytes,
        lowLatency = false,
      )

    return ConfiguredPcmAudioTrack(
      track = fallbackTrack,
      requestedProfile = profile,
      actualSampleRateHz = safeRate,
      actualEncoding = AudioFormat.ENCODING_PCM_FLOAT,
      trackBitDepth = 32,
      usesFloatConversion = true,
      lowLatencyApplied = false,
      bufferSizing = fallbackSizing,
      isHiResPath = safeRate >= 48_000,
      outputDescription = "${safeRate / 1000} kHz Float Fallback (${fallbackSizing.bufferDurationMs} ms)",
    )
  }

  private fun readHalFramesPerBuffer(context: Context?): Int {
    val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    return audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull()
      ?: DEFAULT_OUTPUT_FRAMES_PER_BUFFER
  }

  private fun tryCreateTrack(
    sampleRateHz: Int,
    channels: Int,
    encodingResolution: EncodingResolution,
    framesPerBuffer: Int,
    lowLatency: Boolean,
    bufferProfile: AudioBufferProfile,
    explicitRequestedBufferBytes: Int?,
    ultraLowLatency: Boolean = false,
  ): Pair<AudioTrack, BufferSizingResult>? {
    val channelMask = resolveChannelMask(channels)
    val minBuf = AudioTrack.getMinBufferSize(sampleRateHz, channelMask, encodingResolution.encoding)
    val sizing =
      computeBufferSizing(
        sampleRateHz = sampleRateHz,
        channels = channels,
        trackEncoding = encodingResolution.encoding,
        minHardwareBufferBytes = minBuf,
        framesPerBuffer = framesPerBuffer,
        lowLatency = lowLatency,
        bufferProfile = bufferProfile,
        explicitRequestedBufferBytes = explicitRequestedBufferBytes,
        ultraLowLatency = ultraLowLatency,
      )
    return try {
      val track =
        buildPlatformTrack(
          sampleRateHz = sampleRateHz,
          channels = channels,
          encoding = encodingResolution.encoding,
          bufferSizeBytes = sizing.bufferSizeBytes,
          lowLatency = lowLatency,
        )
      if (track.state == AudioTrack.STATE_INITIALIZED) {
        if (ultraLowLatency && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          try {
            val capacityFrames = track.bufferCapacityInFrames
            if (capacityFrames > 0) {
              track.bufferSizeInFrames = sizing.bufferFrames.coerceIn(1, capacityFrames)
            }
          } catch (_: Exception) {
          }
        }
        track to sizing
      } else {
        track.release()
        null
      }
    } catch (_: UnsupportedOperationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  private fun buildPlatformTrack(
    sampleRateHz: Int,
    channels: Int,
    encoding: Int,
    bufferSizeBytes: Int,
    lowLatency: Boolean,
  ): AudioTrack {
    val builder =
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(sampleRateHz)
            .setChannelMask(resolveChannelMask(channels))
            .build()
        )
        .setBufferSizeInBytes(bufferSizeBytes)
        .setTransferMode(AudioTrack.MODE_STREAM)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      builder.setPerformanceMode(
        if (lowLatency) AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
        else AudioTrack.PERFORMANCE_MODE_NONE
      )
    }

    return builder.build()
  }

  private fun formatOutputDescription(
    profileLabel: String,
    usesFloatConversion: Boolean,
    lowLatency: Boolean,
    ultraLowLatency: Boolean,
    sizing: BufferSizingResult,
  ): String =
    buildString {
      append(profileLabel)
      if (usesFloatConversion) append(" → 32f")
      when {
        ultraLowLatency -> append(" • ULTRA-LOW")
        lowLatency -> append(" • FastMixer")
      }
      val kb = (sizing.bufferSizeBytes / 1024f).coerceAtLeast(1f)
      append(" • ${kb.toInt()} KB (${sizing.bufferDurationMs}ms)")
    }
}
