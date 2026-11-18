package com.example.service

import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.bluetooth.BluetoothSdpProfileConfigurator
import com.example.data.SpeakerPreferences
import com.example.model.RootPrivilegeStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Dedicated Android service component that uses acquired root privileges (su) to modify
 * Bluetooth SDP records (`0x110B` AudioSink / `0x110E` AVRCP Controller), local Class of Device
 * (`0x240414` Loudspeaker), `bt_config.conf`, and Android Bluetooth profile flags so remote devices
 * recognize and connect to this phone as a standard A2DP speaker sink.
 */
class RootBluetoothSdpService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private lateinit var preferences: SpeakerPreferences
  private lateinit var configurator: BluetoothSdpProfileConfigurator

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    preferences = SpeakerPreferences(applicationContext)
    configurator = configuratorFactory()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val action = intent?.action ?: ACTION_INSPECT_SDP_RECORDS
    when (action) {
      ACTION_APPLY_A2DP_SINK_SDP_PROFILE -> {
        val cachedCod = intent?.getStringExtra(EXTRA_CACHED_ORIGINAL_COD)
        val startSpeakerAfter = intent?.getBooleanExtra(EXTRA_START_SPEAKER_AFTER, false) == true
        handleApplyA2dpSinkSdp(cachedCod, startSpeakerAfter, startId)
      }
      ACTION_RESTORE_STANDARD_SDP_PROFILE -> {
        val cachedCod = intent?.getStringExtra(EXTRA_CACHED_ORIGINAL_COD)
        handleRestoreStandardSdp(cachedCod, startId)
      }
      ACTION_INSPECT_SDP_RECORDS -> {
        val requireRoot = intent?.getBooleanExtra(EXTRA_REQUIRE_ROOT, true) != false
        handleInspectSdpRecords(requireRoot, startId)
      }
      else -> stopSelf(startId)
    }
    return START_NOT_STICKY
  }

  private fun handleApplyA2dpSinkSdp(
    cachedCodArg: String?,
    startSpeakerAfter: Boolean,
    startId: Int,
  ) {
    SpeakerSessionManager.setTogglingA2dpRoot(true)
    SpeakerSessionManager.setRootChecking()

    serviceScope.launch {
      try {
        val savedCod = cachedCodArg ?: preferences.settingsFlow.first().originalClassOfDevice
        val adapter =
          (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val result =
          configurator.applyAudioSinkSdpAndProfile(
            packageName = packageName,
            cachedOriginalCod = savedCod,
            adapter = adapter,
          )
        result
          .onSuccess { updatedRoot ->
            updatedRoot.originalClassOfDevice?.let { cod ->
              preferences.saveOriginalClassOfDevice(cod)
            }
            SpeakerSessionManager.setRootStatus(updatedRoot)
            SpeakerSessionManager.setTogglingA2dpRoot(false)
            SpeakerSessionManager.clearError()
            sendBroadcast(Intent(ACTION_SDP_PROFILE_STATE_UPDATED).setPackage(packageName))
            if (startSpeakerAfter) {
              val speakerIntent =
                Intent(applicationContext, BluetoothSpeakerService::class.java).apply {
                  action = BluetoothSpeakerService.ACTION_START_SPEAKER
                }
              ContextCompat.startForegroundService(applicationContext, speakerIntent)
            }
          }
          .onFailure { error ->
            val inspected = configurator.inspectCurrentState(adapter = adapter, requireRoot = false)
            val status =
              if (inspected.privilegeStatus == RootPrivilegeStatus.UNAVAILABLE) {
                RootPrivilegeStatus.UNAVAILABLE
              } else {
                RootPrivilegeStatus.DENIED
              }
            SpeakerSessionManager.setRootStatus(
              inspected.copy(
                privilegeStatus = status,
                lastCommandLog = error.localizedMessage ?: "Root SDP configuration failed",
              )
            )
            SpeakerSessionManager.setTogglingA2dpRoot(false)
            SpeakerSessionManager.setError(
              error.localizedMessage
                ?: "Root privilege (su) is required to modify Bluetooth SDP records and A2DP Sink settings."
            )
          }
      } finally {
        stopSelf(startId)
      }
    }
  }

  private fun handleRestoreStandardSdp(cachedCodArg: String?, startId: Int) {
    SpeakerSessionManager.setTogglingA2dpRoot(true)
    serviceScope.launch {
      try {
        val savedCod =
          cachedCodArg
            ?: SpeakerSessionManager.state.value.rootStatus.originalClassOfDevice
            ?: preferences.settingsFlow.first().originalClassOfDevice
        val adapter =
          (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val result =
          configurator.restoreStandardPhoneProfile(
            originalCod = savedCod,
            adapter = adapter,
          )
        result.onSuccess { restored ->
          SpeakerSessionManager.setRootStatus(restored)
          sendBroadcast(Intent(ACTION_SDP_PROFILE_STATE_UPDATED).setPackage(packageName))
        }
      } finally {
        SpeakerSessionManager.setTogglingA2dpRoot(false)
        stopSelf(startId)
      }
    }
  }

  private fun handleInspectSdpRecords(requireRoot: Boolean, startId: Int) {
    if (requireRoot) {
      SpeakerSessionManager.setRootChecking()
    }
    serviceScope.launch {
      try {
        val adapter =
          (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        val inspected = configurator.inspectCurrentState(adapter = adapter, requireRoot = requireRoot)
        val savedCod = preferences.settingsFlow.first().originalClassOfDevice
        SpeakerSessionManager.setRootStatus(
          inspected.copy(originalClassOfDevice = savedCod ?: inspected.originalClassOfDevice)
        )
        if (requireRoot && !inspected.isRootGranted) {
          val message =
            if (inspected.privilegeStatus == RootPrivilegeStatus.UNAVAILABLE) {
              "Root (su) binary not found. Root privilege is required to modify Bluetooth SDP records and A2DP Sink profile settings."
            } else {
              "Root permission denied. Grant superuser access in your root manager to configure Bluetooth SDP records."
            }
          SpeakerSessionManager.setError(message)
        } else if (requireRoot) {
          SpeakerSessionManager.clearError()
        }
      } finally {
        stopSelf(startId)
      }
    }
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  companion object {
    const val ACTION_APPLY_A2DP_SINK_SDP_PROFILE =
      "com.example.action.APPLY_A2DP_SINK_SDP_PROFILE"
    const val ACTION_RESTORE_STANDARD_SDP_PROFILE =
      "com.example.action.RESTORE_STANDARD_SDP_PROFILE"
    const val ACTION_INSPECT_SDP_RECORDS =
      "com.example.action.INSPECT_SDP_RECORDS"
    const val ACTION_SDP_PROFILE_STATE_UPDATED =
      "com.example.action.SDP_PROFILE_STATE_UPDATED"

    const val EXTRA_CACHED_ORIGINAL_COD = "extra_cached_original_cod"
    const val EXTRA_START_SPEAKER_AFTER = "extra_start_speaker_after"
    const val EXTRA_REQUIRE_ROOT = "extra_require_root"

    @Volatile
    internal var configuratorFactory: () -> BluetoothSdpProfileConfigurator = {
      BluetoothSdpProfileConfigurator()
    }
  }
}
