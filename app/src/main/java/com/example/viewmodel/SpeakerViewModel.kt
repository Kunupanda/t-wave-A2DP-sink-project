package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.RootA2dpController
import com.example.bluetooth.ShellDiagnosticEntry
import com.example.bluetooth.ShellDiagnosticLogHandler
import com.example.data.SpeakerPreferences
import com.example.model.AudioBufferProfile
import com.example.model.AudioQualityProfile
import com.example.model.BluetoothHardwareStatus
import com.example.model.ConnectionStatus
import com.example.model.PairedDeviceItem
import com.example.model.PermissionStatus
import com.example.model.RootPrivilegeStatus
import com.example.model.ScopeAnimationMode
import com.example.model.SpeakerMode
import com.example.model.SpeakerServiceState
import com.example.permissions.PermissionHelper
import com.example.service.BluetoothSpeakerService
import com.example.service.RootBluetoothSdpService
import com.example.service.SpeakerSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpeakerViewModel(
  application: Application,
  private val rootA2dpController: RootA2dpController = RootA2dpController(),
) : AndroidViewModel(application) {

  constructor(application: Application) : this(application, RootA2dpController())

  private val preferences = SpeakerPreferences(application)
  val serviceState: StateFlow<SpeakerServiceState> = SpeakerSessionManager.state
  val shellDiagnostics: StateFlow<List<ShellDiagnosticEntry>> = ShellDiagnosticLogHandler.shared.entries

  fun clearShellDiagnostics() {
    ShellDiagnosticLogHandler.shared.clear()
  }

  private val _permissionStatus = MutableStateFlow(PermissionHelper.checkStatus(application))
  val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

  private val _bluetoothStatus = MutableStateFlow(readBluetoothHardwareStatus())
  val bluetoothStatus: StateFlow<BluetoothHardwareStatus> = _bluetoothStatus.asStateFlow()

  private var discoverableTimerJob: Job? = null
  private var savedOriginalCod: String? = null

  private val bluetoothStateReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
          BluetoothAdapter.ACTION_STATE_CHANGED -> {
            _bluetoothStatus.value = readBluetoothHardwareStatus()
            refreshPairedDevices()
          }
          BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
            refreshPairedDevices()
          }
          BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> {
            val scanMode =
              intent.getIntExtra(
                BluetoothAdapter.EXTRA_SCAN_MODE,
                BluetoothAdapter.SCAN_MODE_NONE,
              )
            if (scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
              discoverableTimerJob?.cancel()
              discoverableTimerJob = null
            }
            _bluetoothStatus.value = readBluetoothHardwareStatus()
          }
          RootBluetoothSdpService.ACTION_SDP_PROFILE_STATE_UPDATED -> {
            refreshEnvironmentState()
          }
        }
      }
    }

  init {
    inspectHardwareAudioCapabilities()
    refreshPairedDevices()
    viewModelScope.launch {
      val probed = withContext(Dispatchers.IO) { rootA2dpController.probeSystemProperties() }
      SpeakerSessionManager.setRootStatus(
        probed.copy(originalClassOfDevice = savedOriginalCod ?: probed.originalClassOfDevice)
      )
    }
    viewModelScope.launch {
      preferences.settingsFlow.collect { saved ->
        savedOriginalCod = saved.originalClassOfDevice
        SpeakerSessionManager.setAutoReconnectEnabled(saved.autoReconnectEnabled)
        SpeakerSessionManager.setSpeakerMode(saved.speakerMode)
        SpeakerSessionManager.setQualityProfile(saved.qualityProfile)
        SpeakerSessionManager.setBufferProfile(saved.bufferProfile)
        SpeakerSessionManager.setScopeAnimationMode(saved.scopeAnimationMode)
        SpeakerSessionManager.setLowLatencyMode(saved.lowLatencyMode)
        SpeakerSessionManager.setUltraLowLatencyMode(saved.ultraLowLatencyMode)
        SpeakerSessionManager.setSyncOffsetMs(saved.syncOffsetMs)
        SpeakerSessionManager.setLockScreenPlaybackEnabled(saved.lockScreenPlaybackEnabled)
        SpeakerSessionManager.setLastConnectedDevice(saved.lastDevice)
      }
    }
    val filter =
      IntentFilter().apply {
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        addAction(RootBluetoothSdpService.ACTION_SDP_PROFILE_STATE_UPDATED)
      }
    ContextCompat.registerReceiver(
      application,
      bluetoothStateReceiver,
      filter,
      ContextCompat.RECEIVER_EXPORTED,
    )
  }

  override fun onCleared() {
    discoverableTimerJob?.cancel()
    try {
      getApplication<Application>().unregisterReceiver(bluetoothStateReceiver)
    } catch (_: IllegalArgumentException) {
    }
    super.onCleared()
  }

  fun refreshEnvironmentState() {
    val app = getApplication<Application>()
    _permissionStatus.value = PermissionHelper.checkStatus(app)
    _bluetoothStatus.value = readBluetoothHardwareStatus()
    inspectHardwareAudioCapabilities()
    refreshPairedDevices()
  }

  fun connectPairedDevice(address: String) {
    val app = getApplication<Application>()
    if (serviceState.value.status == ConnectionStatus.IDLE) {
      SpeakerSessionManager.setError(
        "Start A2DP Sink Speaker Mode first before connecting a paired device."
      )
      return
    }
    SpeakerSessionManager.clearError()
    val intent =
      Intent(app, BluetoothSpeakerService::class.java).apply {
        action = BluetoothSpeakerService.ACTION_CONNECT_A2DP_DEVICE
        putExtra(BluetoothSpeakerService.EXTRA_DEVICE_ADDRESS, address)
      }
    app.startService(intent)
  }

  fun wakeA2dpAudio() {
    val app = getApplication<Application>()
    if (serviceState.value.speakerMode != SpeakerMode.A2DP_SINK) return
    val intent =
      Intent(app, BluetoothSpeakerService::class.java).apply {
        action = BluetoothSpeakerService.ACTION_WAKE_A2DP_AUDIO
      }
    app.startService(intent)
  }

  @SuppressLint("MissingPermission")
  private fun refreshPairedDevices() {
    val app = getApplication<Application>()
    val permissions = PermissionHelper.checkStatus(app)
    if (!permissions.bluetoothConnectGranted) {
      SpeakerSessionManager.setPairedDevices(emptyList())
      return
    }
    val manager = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter
    if (adapter == null || !adapter.isEnabled) {
      SpeakerSessionManager.setPairedDevices(emptyList())
      return
    }
    val activeAddress = serviceState.value.connectedDevice?.address
    val devices =
      try {
        adapter.bondedDevices
          ?.map { btDevice ->
            PairedDeviceItem(
              name = btDevice.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Device",
              address = btDevice.address,
              isConnected = btDevice.address.equals(activeAddress, ignoreCase = true),
            )
          }
          ?.sortedBy { it.name.lowercase() }
          ?: emptyList()
      } catch (_: SecurityException) {
        emptyList()
      }
    SpeakerSessionManager.setPairedDevices(devices)
  }

  fun requestRootAccess() {
    if (serviceState.value.rootStatus.privilegeStatus == RootPrivilegeStatus.CHECKING) return
    val app = getApplication<Application>()
    SpeakerSessionManager.setRootChecking()
    val intent =
      Intent(app, RootBluetoothSdpService::class.java).apply {
        action = RootBluetoothSdpService.ACTION_INSPECT_SDP_RECORDS
        putExtra(RootBluetoothSdpService.EXTRA_REQUIRE_ROOT, true)
      }
    app.startService(intent)
  }

  fun applySdpAndProfileSettings() {
    if (serviceState.value.isTogglingA2dpRoot) return
    val app = getApplication<Application>()
    SpeakerSessionManager.clearError()
    val intent =
      Intent(app, RootBluetoothSdpService::class.java).apply {
        action = RootBluetoothSdpService.ACTION_APPLY_A2DP_SINK_SDP_PROFILE
        putExtra(RootBluetoothSdpService.EXTRA_CACHED_ORIGINAL_COD, savedOriginalCod)
        putExtra(RootBluetoothSdpService.EXTRA_START_SPEAKER_AFTER, false)
      }
    app.startService(intent)
  }

  fun setSpeakerMode(mode: SpeakerMode) {
    if (serviceState.value.speakerMode == mode) return
    if (serviceState.value.status != ConnectionStatus.IDLE) {
      stopSpeakerMode()
    }
    SpeakerSessionManager.setSpeakerMode(mode)
    SpeakerSessionManager.clearError()
    viewModelScope.launch { preferences.setSpeakerMode(mode) }
  }

  fun buildDiscoverableIntent(durationSeconds: Int = DEFAULT_DISCOVERABLE_DURATION_SECONDS): Intent =
    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
      putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSeconds.coerceIn(1, 3600))
    }

  fun onDiscoverableResult(resultCode: Int) {
    refreshEnvironmentState()
    if (resultCode > 0) {
      startDiscoverableCountdown(resultCode)
      SpeakerSessionManager.clearError()
    } else if (resultCode == Activity.RESULT_CANCELED && !_bluetoothStatus.value.isDiscoverable) {
      discoverableTimerJob?.cancel()
      _bluetoothStatus.update { it.copy(discoverableRemainingSeconds = 0) }
      SpeakerSessionManager.setError(
        "Bluetooth discoverability was declined. Tap Make Visible so nearby devices can find this phone."
      )
    }
  }

  private fun startDiscoverableCountdown(durationSeconds: Int) {
    discoverableTimerJob?.cancel()
    _bluetoothStatus.update {
      it.copy(isDiscoverable = true, discoverableRemainingSeconds = durationSeconds)
    }
    discoverableTimerJob =
      viewModelScope.launch {
        var remaining = durationSeconds
        while (remaining > 0) {
          delay(1000L)
          remaining--
          _bluetoothStatus.update { it.copy(discoverableRemainingSeconds = remaining) }
        }
        _bluetoothStatus.value = readBluetoothHardwareStatus()
      }
  }

  fun startSpeakerMode() {
    val app = getApplication<Application>()
    refreshEnvironmentState()
    if (!_permissionStatus.value.canStartServer) {
      SpeakerSessionManager.setError(
        "Grant BLUETOOTH_CONNECT permission to start Bluetooth Speaker Mode."
      )
      return
    }
    if (!_bluetoothStatus.value.isSupported) {
      SpeakerSessionManager.setError("Bluetooth hardware is unavailable on this device.")
      return
    }
    if (!_bluetoothStatus.value.isEnabled) {
      SpeakerSessionManager.setError("Turn on Bluetooth before starting Speaker Mode.")
      return
    }

    SpeakerSessionManager.clearError()
    val currentMode = serviceState.value.speakerMode
    if (currentMode == SpeakerMode.A2DP_SINK) {
      startA2dpSinkWithRoot(app)
    } else {
      launchForegroundSpeakerService(app)
    }
  }

  private fun startA2dpSinkWithRoot(app: Application) {
    if (serviceState.value.isTogglingA2dpRoot) return
    val intent =
      Intent(app, RootBluetoothSdpService::class.java).apply {
        action = RootBluetoothSdpService.ACTION_APPLY_A2DP_SINK_SDP_PROFILE
        putExtra(RootBluetoothSdpService.EXTRA_CACHED_ORIGINAL_COD, savedOriginalCod)
        putExtra(RootBluetoothSdpService.EXTRA_START_SPEAKER_AFTER, true)
      }
    app.startService(intent)
  }

  private fun launchForegroundSpeakerService(app: Application) {
    val intent =
      Intent(app, BluetoothSpeakerService::class.java).apply {
        action = BluetoothSpeakerService.ACTION_START_SPEAKER
      }
    ContextCompat.startForegroundService(app, intent)
  }

  fun stopSpeakerMode() {
    val app = getApplication<Application>()
    val stopIntent =
      Intent(app, BluetoothSpeakerService::class.java).apply {
        action = BluetoothSpeakerService.ACTION_STOP_SPEAKER
      }
    app.startService(stopIntent)

    val state = serviceState.value
    if (state.speakerMode == SpeakerMode.A2DP_SINK &&
      state.rootStatus.isRootGranted &&
      state.rootStatus.isA2dpSinkEnabled
    ) {
      val originalCod = savedOriginalCod ?: state.rootStatus.originalClassOfDevice
      val restoreIntent =
        Intent(app, RootBluetoothSdpService::class.java).apply {
          action = RootBluetoothSdpService.ACTION_RESTORE_STANDARD_SDP_PROFILE
          putExtra(RootBluetoothSdpService.EXTRA_CACHED_ORIGINAL_COD, originalCod)
        }
      try {
        app.startService(restoreIntent)
      } catch (_: IllegalStateException) {
        viewModelScope.launch {
          val result = withContext(Dispatchers.IO) { rootA2dpController.disableA2dpSink(originalCod) }
          result.onSuccess { restored -> SpeakerSessionManager.setRootStatus(restored) }
          refreshEnvironmentState()
        }
      }
    }
  }

  fun toggleSpeakerMode() {
    if (serviceState.value.status == ConnectionStatus.IDLE) {
      startSpeakerMode()
    } else {
      stopSpeakerMode()
    }
  }

  fun setLowLatencyMode(enabled: Boolean) {
    SpeakerSessionManager.setLowLatencyMode(enabled)
    viewModelScope.launch {
      preferences.setLowLatencyMode(enabled)
      if (!enabled) {
        preferences.setUltraLowLatencyMode(false)
      }
    }
    notifyAudioReconfigureIfActive()
  }

  fun setUltraLowLatencyMode(enabled: Boolean) {
    SpeakerSessionManager.setUltraLowLatencyMode(enabled)
    viewModelScope.launch {
      preferences.setUltraLowLatencyMode(enabled)
      if (enabled) {
        preferences.setLowLatencyMode(true)
        preferences.setBufferProfile(AudioBufferProfile.LOW_LATENCY)
      }
    }
    notifyAudioReconfigureIfActive()
  }

  fun setSyncOffsetMs(offsetMs: Int) {
    val snapped = com.example.audio.PcmSyncDelayLine.clampAndSnapOffset(offsetMs)
    SpeakerSessionManager.setSyncOffsetMs(snapped)
    viewModelScope.launch { preferences.setSyncOffsetMs(snapped) }
  }

  fun setLockScreenPlaybackEnabled(enabled: Boolean) {
    SpeakerSessionManager.setLockScreenPlaybackEnabled(enabled)
    viewModelScope.launch { preferences.setLockScreenPlaybackEnabled(enabled) }
    if (serviceState.value.status != ConnectionStatus.IDLE) {
      val app = getApplication<Application>()
      val intent =
        Intent(app, BluetoothSpeakerService::class.java).apply {
          action = BluetoothSpeakerService.ACTION_LOCK_SCREEN_RECONFIGURE
        }
      app.startService(intent)
    }
  }

  fun setQualityProfile(profile: AudioQualityProfile) {
    SpeakerSessionManager.setQualityProfile(profile)
    viewModelScope.launch { preferences.setQualityProfile(profile) }
    notifyAudioReconfigureIfActive()
  }

  fun setBufferProfile(bufferProfile: AudioBufferProfile) {
    SpeakerSessionManager.setBufferProfile(bufferProfile)
    viewModelScope.launch { preferences.setBufferProfile(bufferProfile) }
    notifyAudioReconfigureIfActive()
  }

  fun setScopeAnimationMode(mode: ScopeAnimationMode) {
    SpeakerSessionManager.setScopeAnimationMode(mode)
    viewModelScope.launch { preferences.setScopeAnimationMode(mode) }
  }

  fun setRequirePairing(enabled: Boolean) {
    SpeakerSessionManager.setRequirePairing(enabled)
    if (serviceState.value.status == ConnectionStatus.LISTENING) {
      val app = getApplication<Application>()
      val intent =
        Intent(app, BluetoothSpeakerService::class.java).apply {
          action = BluetoothSpeakerService.ACTION_RESTART_LISTENER
        }
      app.startService(intent)
    }
  }

  fun setAutoReconnectEnabled(enabled: Boolean) {
    SpeakerSessionManager.setAutoReconnectEnabled(enabled)
    viewModelScope.launch { preferences.setAutoReconnectEnabled(enabled) }
  }

  fun updateGain(gain: Float) {
    SpeakerSessionManager.setGain(gain)
    notifyGainChangedIfActive()
  }

  fun toggleMute() {
    SpeakerSessionManager.toggleMute()
    notifyGainChangedIfActive()
  }

  fun dismissError() {
    SpeakerSessionManager.clearError()
  }

  private fun notifyGainChangedIfActive() {
    if (serviceState.value.status != ConnectionStatus.IDLE) {
      val app = getApplication<Application>()
      val intent =
        Intent(app, BluetoothSpeakerService::class.java).apply {
          action = BluetoothSpeakerService.ACTION_UPDATE_GAIN
        }
      app.startService(intent)
    }
  }

  private fun notifyAudioReconfigureIfActive() {
    if (serviceState.value.status != ConnectionStatus.IDLE) {
      val app = getApplication<Application>()
      val intent =
        Intent(app, BluetoothSpeakerService::class.java).apply {
          action = BluetoothSpeakerService.ACTION_RECONFIGURE_AUDIO
        }
      app.startService(intent)
    }
  }

  private fun inspectHardwareAudioCapabilities() {
    val app = getApplication<Application>()
    val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    var maxRate = 48_000
    var supportsHiRes = false

    outputs.forEach { device ->
      device.sampleRates.maxOrNull()?.let { rate -> if (rate > maxRate) maxRate = rate }
      if (device.sampleRates.any { it >= 96_000 }) {
        supportsHiRes = true
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (
          device.encodings.any {
            it == AudioFormat.ENCODING_PCM_24BIT_PACKED ||
              it == AudioFormat.ENCODING_PCM_32BIT ||
              it == AudioFormat.ENCODING_PCM_FLOAT
          }
        ) {
          supportsHiRes = true
        }
      } else if (device.encodings.any { it == AudioFormat.ENCODING_PCM_FLOAT }) {
        supportsHiRes = true
      }
    }

    SpeakerSessionManager.updateHardwareAudioCapabilities(
      maxSampleRateHz = maxRate,
      supportsHiRes = supportsHiRes,
    )
  }

  @SuppressLint("MissingPermission")
  private fun readBluetoothHardwareStatus(): BluetoothHardwareStatus {
    val app = getApplication<Application>()
    val manager = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter ?: return BluetoothHardwareStatus(isSupported = false)

    val permissions = PermissionHelper.checkStatus(app)
    val adapterName =
      if (permissions.bluetoothConnectGranted) {
        try {
          adapter.name?.takeIf { it.isNotBlank() } ?: "Android Speaker"
        } catch (_: SecurityException) {
          "Android Speaker"
        }
      } else {
        "Android Speaker"
      }

    val isDiscoverable =
      if (permissions.bluetoothConnectGranted) {
        try {
          adapter.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
        } catch (_: SecurityException) {
          false
        }
      } else {
        false
      }

    val currentRemaining =
      if (isDiscoverable) _bluetoothStatus.value.discoverableRemainingSeconds else 0

    return BluetoothHardwareStatus(
      isSupported = true,
      isEnabled = adapter.isEnabled,
      isDiscoverable = isDiscoverable,
      discoverableRemainingSeconds = currentRemaining,
      adapterName = adapterName,
    )
  }

  companion object {
    const val DEFAULT_DISCOVERABLE_DURATION_SECONDS = 300
  }
}
