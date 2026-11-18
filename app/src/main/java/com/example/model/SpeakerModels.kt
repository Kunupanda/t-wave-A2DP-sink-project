package com.example.model

enum class ConnectionStatus {
  IDLE,
  LISTENING,
  RECONNECTING,
  CONNECTED,
}

enum class SpeakerMode(val requiresRoot: Boolean) {
  A2DP_SINK(requiresRoot = true),
  RFCOMM_SPP(requiresRoot = false),
}

enum class RootPrivilegeStatus {
  UNKNOWN,
  CHECKING,
  GRANTED,
  DENIED,
  UNAVAILABLE,
}

data class RootA2dpStatus(
  val privilegeStatus: RootPrivilegeStatus = RootPrivilegeStatus.UNKNOWN,
  val isA2dpSinkEnabled: Boolean = false,
  val isA2dpSinkServiceRunning: Boolean = false,
  val isAvrcpControllerEnabled: Boolean = false,
  val isOffloadDisabled: Boolean = false,
  val isAudioSinkSdpExposed: Boolean = false,
  val isAvrcpSdpExposed: Boolean = false,
  val isBtConfigPatched: Boolean = false,
  val exposedSdpUuids: List<String> = emptyList(),
  val classOfDevice: String = "",
  val originalClassOfDevice: String? = null,
  val lastCommandLog: String = "",
) {
  val isRootGranted: Boolean
    get() = privilegeStatus == RootPrivilegeStatus.GRANTED
}

data class PairedDeviceItem(
  val name: String,
  val address: String,
  val isConnected: Boolean = false,
)

enum class AudioQualityProfile(
  val displayLabel: String,
  val shortBadge: String,
  val sampleRateHz: Int,
  val bitDepth: Int,
  val channels: Int = 2,
  val isHiRes: Boolean,
) {
  STANDARD_44_16("44.1 kHz • 16-bit PCM", "CD 16/44.1", 44_100, 16, 2, false),
  STUDIO_48_24("48 kHz • 24-bit PCM", "HD 24/48", 48_000, 24, 2, true),
  HIRES_96_24("96 kHz • 24-bit PCM", "HI-RES 24/96", 96_000, 24, 2, true),
  MASTER_192_32("192 kHz • 32-bit PCM", "MASTER 32/192", 192_000, 32, 2, true);

  val frameSizeBytes: Int
    get() = channels * (bitDepth / 8)
}

enum class AudioBufferProfile(
  val shortLabel: String,
  val multiplier: Int,
  val minFloorBytes: Int,
) {
  LOW_LATENCY("2× Ultra-Low", 2, 4_096),
  BALANCED("4× Balanced", 4, 16_384),
  HIRES_DEEP("8× Studio Hi-Res", 8, 32_768),
}

data class ConnectedDeviceInfo(
  val name: String,
  val address: String,
)

data class AudioTelemetry(
  val leftLevel: Float = 0f,
  val rightLevel: Float = 0f,
  val spectrumBands: List<Float> = List(BAND_COUNT) { 0f },
  val bufferFillRatio: Float = 0f,
  val throughputKbps: Float = 0f,
  val totalBytesReceived: Long = 0L,
  val bufferedBytes: Int = 0,
  val bufferCapacityBytes: Int = 0,
  val estimatedLatencyMs: Int = 0,
  val sampleRateHz: Int = 44_100,
  val bitDepth: Int = 16,
  val channels: Int = 2,
  val activeOutputDescription: String = "44.1 kHz / 16-bit",
) {
  companion object {
    const val BAND_COUNT = 28
  }
}

data class PermissionStatus(
  val bluetoothConnectGranted: Boolean = false,
  val bluetoothAdvertiseGranted: Boolean = false,
  val postNotificationsGranted: Boolean = false,
) {
  val allGranted: Boolean
    get() = bluetoothConnectGranted && bluetoothAdvertiseGranted && postNotificationsGranted

  val canStartServer: Boolean
    get() = bluetoothConnectGranted
}

data class BluetoothHardwareStatus(
  val isSupported: Boolean = true,
  val isEnabled: Boolean = false,
  val isDiscoverable: Boolean = false,
  val discoverableRemainingSeconds: Int = 0,
  val adapterName: String = "Android Speaker",
)

enum class ScopeAnimationMode(val label: String) {
  DUAL_SPECTRUM("Dual Bars"),
  SPECTRUM_QUADS_NARROW("Narrow Quads"),
  NEON_WAVE("Neon Wave"),
  STEREO_WAVE("Stereo Wave"),
  RADIAL_PULSE("Radial Pulse"),
  AURORA_RIBBONS("Aurora"),
  FLOOR_EQ("Floor VU"),
  HARMONIC_DOTS("Matrix Dots"),
}

data class SpeakerServiceState(
  val status: ConnectionStatus = ConnectionStatus.IDLE,
  val speakerMode: SpeakerMode = SpeakerMode.A2DP_SINK,
  val rootStatus: RootA2dpStatus = RootA2dpStatus(),
  val isTogglingA2dpRoot: Boolean = false,
  val pairedDevices: List<PairedDeviceItem> = emptyList(),
  val connectingDeviceAddress: String? = null,
  val connectedDevice: ConnectedDeviceInfo? = null,
  val lastConnectedDevice: ConnectedDeviceInfo? = null,
  val autoReconnectEnabled: Boolean = true,
  val reconnectAttempt: Int = 0,
  val maxReconnectAttempts: Int = 5,
  val telemetry: AudioTelemetry = AudioTelemetry(),
  val qualityProfile: AudioQualityProfile = AudioQualityProfile.STANDARD_44_16,
  val bufferProfile: AudioBufferProfile = AudioBufferProfile.BALANCED,
  val scopeAnimationMode: ScopeAnimationMode = ScopeAnimationMode.DUAL_SPECTRUM,
  val lowLatencyMode: Boolean = false,
  val ultraLowLatencyMode: Boolean = false,
  val syncOffsetMs: Int = 0,
  val lockScreenPlaybackEnabled: Boolean = true,
  val requirePairing: Boolean = false,
  val bleAdvertisingActive: Boolean = false,
  val hardwareMaxSampleRateHz: Int = 48_000,
  val hardwareSupportsHiRes: Boolean = false,
  val gain: Float = 1f,
  val isMuted: Boolean = false,
  val errorMessage: String? = null,
) {
  companion object {
    const val MIN_SYNC_OFFSET_MS = -250
    const val MAX_SYNC_OFFSET_MS = 500
    const val SYNC_OFFSET_STEP_MS = 5
  }
}
