package com.example.service

import com.example.audio.PcmAudioTrackConfigurator
import com.example.audio.PcmSyncDelayLine
import com.example.model.AudioBufferProfile
import com.example.model.AudioQualityProfile
import com.example.model.AudioTelemetry
import com.example.model.ConnectedDeviceInfo
import com.example.model.ConnectionStatus
import com.example.model.RootA2dpStatus
import com.example.model.RootPrivilegeStatus
import com.example.model.ScopeAnimationMode
import com.example.model.SpeakerMode
import com.example.model.SpeakerServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared state holder synchronizing Root A2DP Sink privilege status, live Bluetooth
 * audio telemetry, Auto-Reconnect status, Low Latency Mode, and High-Res Audio profiles.
 */
object SpeakerSessionManager {
  private val _state = MutableStateFlow(SpeakerServiceState())
  val state: StateFlow<SpeakerServiceState> = _state.asStateFlow()

  fun setListening() {
    val current = _state.value
    _state.update {
      it.copy(
        status = ConnectionStatus.LISTENING,
        connectedDevice = null,
        reconnectAttempt = 0,
        telemetry =
          AudioTelemetry(
            sampleRateHz = current.qualityProfile.sampleRateHz,
            bitDepth = current.qualityProfile.bitDepth,
            channels = current.qualityProfile.channels,
            activeOutputDescription =
              if (current.speakerMode == SpeakerMode.A2DP_SINK) "A2DP Sink (0x110B)"
              else current.qualityProfile.shortBadge,
          ),
        errorMessage = null,
      )
    }
  }

  fun setReconnecting(device: ConnectedDeviceInfo, attempt: Int, maxAttempts: Int) {
    val current = _state.value
    _state.update {
      it.copy(
        status = ConnectionStatus.RECONNECTING,
        connectedDevice = null,
        lastConnectedDevice = device,
        reconnectAttempt = attempt,
        maxReconnectAttempts = maxAttempts,
        telemetry =
          AudioTelemetry(
            sampleRateHz = current.qualityProfile.sampleRateHz,
            bitDepth = current.qualityProfile.bitDepth,
            channels = current.qualityProfile.channels,
            activeOutputDescription =
              if (current.speakerMode == SpeakerMode.A2DP_SINK) "A2DP Sink (0x110B)"
              else current.qualityProfile.shortBadge,
          ),
      )
    }
  }

  fun setConnected(device: ConnectedDeviceInfo) {
    _state.update {
      it.copy(
        status = ConnectionStatus.CONNECTED,
        connectedDevice = device,
        lastConnectedDevice = device,
        reconnectAttempt = 0,
        bleAdvertisingActive = false,
        errorMessage = null,
      )
    }
  }

  fun updateTelemetry(telemetry: AudioTelemetry) {
    _state.update { it.copy(telemetry = telemetry) }
  }

  fun setIdle(errorMessage: String? = null) {
    val current = _state.value
    _state.update {
      it.copy(
        status = ConnectionStatus.IDLE,
        connectedDevice = null,
        reconnectAttempt = 0,
        bleAdvertisingActive = false,
        isTogglingA2dpRoot = false,
        telemetry =
          AudioTelemetry(
            sampleRateHz = current.qualityProfile.sampleRateHz,
            bitDepth = current.qualityProfile.bitDepth,
            channels = current.qualityProfile.channels,
            activeOutputDescription =
              if (current.speakerMode == SpeakerMode.A2DP_SINK) "A2DP Sink (0x110B)"
              else current.qualityProfile.shortBadge,
          ),
        errorMessage = errorMessage,
      )
    }
  }

  fun setSpeakerMode(mode: SpeakerMode) {
    _state.update {
      it.copy(
        speakerMode = mode,
        telemetry =
          it.telemetry.copy(
            activeOutputDescription =
              if (mode == SpeakerMode.A2DP_SINK) "A2DP Sink (0x110B)"
              else it.qualityProfile.shortBadge
          ),
      )
    }
  }

  fun setRootStatus(rootStatus: RootA2dpStatus) {
    _state.update { it.copy(rootStatus = rootStatus) }
  }

  fun setRootChecking() {
    _state.update {
      it.copy(rootStatus = it.rootStatus.copy(privilegeStatus = RootPrivilegeStatus.CHECKING))
    }
  }

  fun setTogglingA2dpRoot(inProgress: Boolean) {
    _state.update { it.copy(isTogglingA2dpRoot = inProgress) }
  }

  fun setPairedDevices(devices: List<com.example.model.PairedDeviceItem>) {
    _state.update { it.copy(pairedDevices = devices) }
  }

  fun setConnectingDeviceAddress(address: String?) {
    _state.update { it.copy(connectingDeviceAddress = address) }
  }

  fun setAutoReconnectEnabled(enabled: Boolean) {
    _state.update { it.copy(autoReconnectEnabled = enabled) }
  }

  fun setLastConnectedDevice(device: ConnectedDeviceInfo?) {
    if (device == null) return
    _state.update { it.copy(lastConnectedDevice = device) }
  }

  fun setQualityProfile(profile: AudioQualityProfile) {
    _state.update { current ->
      val sizing =
        PcmAudioTrackConfigurator.computeBufferSizing(
          sampleRateHz = profile.sampleRateHz,
          channels = profile.channels,
          trackEncoding =
            PcmAudioTrackConfigurator.resolveEncoding(
                profile.sampleRateHz,
                profile.bitDepth,
                profile.channels,
              )
              .encoding,
          minHardwareBufferBytes = 0,
          lowLatency = current.lowLatencyMode,
          bufferProfile = current.bufferProfile,
          ultraLowLatency = current.ultraLowLatencyMode,
        )
      current.copy(
        qualityProfile = profile,
        telemetry =
          current.telemetry.copy(
            sampleRateHz = profile.sampleRateHz,
            bitDepth = profile.bitDepth,
            channels = profile.channels,
            bufferCapacityBytes = sizing.bufferSizeBytes,
            estimatedLatencyMs =
              PcmSyncDelayLine.computeEffectiveLatencyMs(
                sizing.bufferDurationMs,
                current.syncOffsetMs,
              ),
            activeOutputDescription =
              if (current.speakerMode == SpeakerMode.A2DP_SINK) "A2DP Sink (0x110B)"
              else "${profile.shortBadge} • ${sizing.bufferSizeBytes / 1024} KB",
          ),
      )
    }
  }

  fun setBufferProfile(bufferProfile: AudioBufferProfile) {
    _state.update { current ->
      val profile = current.qualityProfile
      val sizing =
        PcmAudioTrackConfigurator.computeBufferSizing(
          sampleRateHz = profile.sampleRateHz,
          channels = profile.channels,
          trackEncoding =
            PcmAudioTrackConfigurator.resolveEncoding(
                profile.sampleRateHz,
                profile.bitDepth,
                profile.channels,
              )
              .encoding,
          minHardwareBufferBytes = 0,
          lowLatency = current.lowLatencyMode,
          bufferProfile = bufferProfile,
          ultraLowLatency = current.ultraLowLatencyMode,
        )
      current.copy(
        bufferProfile = bufferProfile,
        telemetry =
          current.telemetry.copy(
            bufferCapacityBytes = sizing.bufferSizeBytes,
            estimatedLatencyMs =
              PcmSyncDelayLine.computeEffectiveLatencyMs(
                sizing.bufferDurationMs,
                current.syncOffsetMs,
              ),
            activeOutputDescription =
              if (current.speakerMode == SpeakerMode.A2DP_SINK) "A2DP Sink (0x110B)"
              else "${profile.shortBadge} • ${sizing.bufferSizeBytes / 1024} KB",
          ),
      )
    }
  }

  fun setLowLatencyMode(enabled: Boolean) {
    _state.update { current ->
      val nextUltra = if (!enabled) false else current.ultraLowLatencyMode
      val profile = current.qualityProfile
      val sizing =
        PcmAudioTrackConfigurator.computeBufferSizing(
          sampleRateHz = profile.sampleRateHz,
          channels = profile.channels,
          trackEncoding =
            PcmAudioTrackConfigurator.resolveEncoding(
                profile.sampleRateHz,
                profile.bitDepth,
                profile.channels,
              )
              .encoding,
          minHardwareBufferBytes = 0,
          lowLatency = enabled,
          bufferProfile = current.bufferProfile,
          ultraLowLatency = nextUltra,
        )
      current.copy(
        lowLatencyMode = enabled,
        ultraLowLatencyMode = nextUltra,
        telemetry =
          current.telemetry.copy(
            bufferCapacityBytes = sizing.bufferSizeBytes,
            estimatedLatencyMs =
              PcmSyncDelayLine.computeEffectiveLatencyMs(
                sizing.bufferDurationMs,
                current.syncOffsetMs,
              ),
          ),
      )
    }
  }

  fun setUltraLowLatencyMode(enabled: Boolean) {
    _state.update { current ->
      val nextLowLatency = if (enabled) true else current.lowLatencyMode
      val nextBufferProfile = if (enabled) AudioBufferProfile.LOW_LATENCY else current.bufferProfile
      val profile = current.qualityProfile
      val sizing =
        PcmAudioTrackConfigurator.computeBufferSizing(
          sampleRateHz = profile.sampleRateHz,
          channels = profile.channels,
          trackEncoding =
            PcmAudioTrackConfigurator.resolveEncoding(
                profile.sampleRateHz,
                profile.bitDepth,
                profile.channels,
              )
              .encoding,
          minHardwareBufferBytes = 0,
          lowLatency = nextLowLatency,
          bufferProfile = nextBufferProfile,
          ultraLowLatency = enabled,
        )
      current.copy(
        lowLatencyMode = nextLowLatency,
        ultraLowLatencyMode = enabled,
        bufferProfile = nextBufferProfile,
        telemetry =
          current.telemetry.copy(
            bufferCapacityBytes = sizing.bufferSizeBytes,
            estimatedLatencyMs =
              PcmSyncDelayLine.computeEffectiveLatencyMs(
                sizing.bufferDurationMs,
                current.syncOffsetMs,
              ),
          ),
      )
    }
  }

  fun setSyncOffsetMs(offsetMs: Int) {
    val snapped = PcmSyncDelayLine.clampAndSnapOffset(offsetMs)
    _state.update { current ->
      val profile = current.qualityProfile
      val sizing =
        PcmAudioTrackConfigurator.computeBufferSizing(
          sampleRateHz = profile.sampleRateHz,
          channels = profile.channels,
          trackEncoding =
            PcmAudioTrackConfigurator.resolveEncoding(
                profile.sampleRateHz,
                profile.bitDepth,
                profile.channels,
              )
              .encoding,
          minHardwareBufferBytes = 0,
          lowLatency = current.lowLatencyMode,
          bufferProfile = current.bufferProfile,
          ultraLowLatency = current.ultraLowLatencyMode,
        )
      current.copy(
        syncOffsetMs = snapped,
        telemetry =
          current.telemetry.copy(
            estimatedLatencyMs =
              PcmSyncDelayLine.computeEffectiveLatencyMs(sizing.bufferDurationMs, snapped)
          ),
      )
    }
  }

  fun setLockScreenPlaybackEnabled(enabled: Boolean) {
    _state.update { it.copy(lockScreenPlaybackEnabled = enabled) }
  }

  fun setScopeAnimationMode(mode: ScopeAnimationMode) {
    _state.update { it.copy(scopeAnimationMode = mode) }
  }

  fun setMuted(muted: Boolean) {
    _state.update { it.copy(isMuted = muted) }
  }

  fun setRequirePairing(enabled: Boolean) {
    _state.update { it.copy(requirePairing = enabled) }
  }

  fun setBleAdvertisingActive(active: Boolean) {
    _state.update { it.copy(bleAdvertisingActive = active) }
  }

  fun updateHardwareAudioCapabilities(maxSampleRateHz: Int, supportsHiRes: Boolean) {
    _state.update {
      it.copy(
        hardwareMaxSampleRateHz = maxSampleRateHz,
        hardwareSupportsHiRes = supportsHiRes,
      )
    }
  }

  fun setError(message: String) {
    _state.update { it.copy(errorMessage = message) }
  }

  fun clearError() {
    _state.update { it.copy(errorMessage = null) }
  }

  fun setGain(gain: Float) {
    val clamped = gain.coerceIn(0f, 1f)
    _state.update { it.copy(gain = clamped) }
  }

  fun toggleMute() {
    _state.update { it.copy(isMuted = !it.isMuted) }
  }

  fun effectiveVolume(): Float {
    val current = _state.value
    return if (current.isMuted) 0f else current.gain
  }
}
