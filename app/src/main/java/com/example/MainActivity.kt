package com.example

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ConnectionStatus
import com.example.permissions.PermissionHelper
import com.example.service.SpeakerSessionManager
import com.example.ui.SpeakerScreen
import com.example.ui.theme.WaveCastTheme
import com.example.viewmodel.SpeakerViewModel

class MainActivity : ComponentActivity() {

  private val viewModel: SpeakerViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      WaveCastTheme {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
        val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()
        val bluetoothStatus by viewModel.bluetoothStatus.collectAsStateWithLifecycle()
        val shellDiagnostics by viewModel.shellDiagnostics.collectAsStateWithLifecycle()

        var pendingStartAfterPermission by remember { mutableStateOf(false) }
        var pendingStartAfterBluetoothEnable by remember { mutableStateOf(false) }
        var pendingDiscoverableAfterPermission by remember { mutableStateOf(false) }
        var pendingDiscoverableOnListening by remember { mutableStateOf(false) }

        val discoverableLauncher =
          rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
          ) { result ->
            viewModel.onDiscoverableResult(result.resultCode)
          }

        fun launchDiscoverablePrompt() {
          val discoverableIntent = viewModel.buildDiscoverableIntent()
          try {
            discoverableLauncher.launch(discoverableIntent)
          } catch (_: SecurityException) {
            SpeakerSessionManager.setError(
              "Grant BLUETOOTH_ADVERTISE permission to make this device discoverable."
            )
          } catch (_: ActivityNotFoundException) {
            SpeakerSessionManager.setError(
              "Bluetooth discoverability dialog is not supported on this device."
            )
          }
        }

        // Keep speaker console accessible on lock screen when active playback on lock screen is enabled
        LaunchedEffect(serviceState.lockScreenPlaybackEnabled, serviceState.status) {
          val showLocked =
            serviceState.lockScreenPlaybackEnabled && serviceState.status != ConnectionStatus.IDLE
          applyLockScreenVisibility(showLocked)
        }

        // Only prompt discoverable once speaker mode actually enters LISTENING (after any root BT restart)
        LaunchedEffect(serviceState.status, pendingDiscoverableOnListening) {
          if (pendingDiscoverableOnListening && serviceState.status == ConnectionStatus.LISTENING) {
            pendingDiscoverableOnListening = false
            if (!bluetoothStatus.isDiscoverable && permissionStatus.bluetoothAdvertiseGranted) {
              launchDiscoverablePrompt()
            }
          }
        }

        val enableBluetoothLauncher =
          rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
          ) {
            viewModel.refreshEnvironmentState()
            if (pendingStartAfterBluetoothEnable) {
              pendingStartAfterBluetoothEnable = false
              val latestBt = viewModel.bluetoothStatus.value
              if (latestBt.isEnabled) {
                pendingDiscoverableOnListening = true
                viewModel.startSpeakerMode()
              }
            }
          }

        val permissionsLauncher =
          rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
          ) { _ ->
            viewModel.refreshEnvironmentState()
            val latestPerm = PermissionHelper.checkStatus(context)
            val latestBt = viewModel.bluetoothStatus.value

            if (pendingDiscoverableAfterPermission) {
              pendingDiscoverableAfterPermission = false
              if (latestPerm.bluetoothAdvertiseGranted) {
                launchDiscoverablePrompt()
              }
            } else if (pendingStartAfterPermission) {
              pendingStartAfterPermission = false
              if (latestPerm.canStartServer) {
                if (!latestBt.isEnabled && latestBt.isSupported) {
                  pendingStartAfterBluetoothEnable = true
                  enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } else {
                  pendingDiscoverableOnListening = true
                  viewModel.startSpeakerMode()
                }
              }
            }
          }

        DisposableEffect(lifecycleOwner) {
          val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
              viewModel.refreshEnvironmentState()
            }
          }
          lifecycleOwner.lifecycle.addObserver(observer)
          onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        SpeakerScreen(
          serviceState = serviceState,
          permissionStatus = permissionStatus,
          bluetoothStatus = bluetoothStatus,
          shellDiagnostics = shellDiagnostics,
          onClearDiagnostics = viewModel::clearShellDiagnostics,
          onSpeakerModeSelected = viewModel::setSpeakerMode,
          onRequestRoot = viewModel::requestRootAccess,
          onApplySdpProfile = viewModel::applySdpAndProfileSettings,
          onConnectPairedDevice = viewModel::connectPairedDevice,
          onWakeA2dpAudio = viewModel::wakeA2dpAudio,
          onToggleSpeakerMode = {
            if (serviceState.status == ConnectionStatus.IDLE) {
              val missing = PermissionHelper.missingRuntimePermissions(context)
              val missingBtPerm =
                !permissionStatus.bluetoothConnectGranted ||
                  !permissionStatus.bluetoothAdvertiseGranted
              if (missing.isNotEmpty() && missingBtPerm) {
                pendingStartAfterPermission = true
                permissionsLauncher.launch(missing.toTypedArray())
              } else if (!bluetoothStatus.isEnabled && bluetoothStatus.isSupported) {
                pendingStartAfterBluetoothEnable = true
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
              } else {
                pendingDiscoverableOnListening = true
                viewModel.startSpeakerMode()
              }
            } else {
              pendingDiscoverableOnListening = false
              viewModel.stopSpeakerMode()
            }
          },
          onGrantPermissions = {
            val missing = PermissionHelper.missingRuntimePermissions(context)
            if (missing.isNotEmpty()) {
              pendingStartAfterPermission = false
              pendingDiscoverableAfterPermission = false
              permissionsLauncher.launch(missing.toTypedArray())
            }
          },
          onOpenSettings = {
            val settingsIntent =
              Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
              }
            context.startActivity(settingsIntent)
          },
          onEnableBluetooth = {
            if (permissionStatus.bluetoothConnectGranted) {
              enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
              val missing = PermissionHelper.missingRuntimePermissions(context)
              if (missing.isNotEmpty()) {
                permissionsLauncher.launch(missing.toTypedArray())
              }
            }
          },
          onRequestDiscoverable = {
            val missing = PermissionHelper.missingRuntimePermissions(context)
            if (!permissionStatus.bluetoothAdvertiseGranted && missing.isNotEmpty()) {
              pendingDiscoverableAfterPermission = true
              permissionsLauncher.launch(missing.toTypedArray())
            } else {
              launchDiscoverablePrompt()
            }
          },
          onLowLatencyChange = viewModel::setLowLatencyMode,
          onUltraLowLatencyChange = viewModel::setUltraLowLatencyMode,
          onSyncOffsetChange = viewModel::setSyncOffsetMs,
          onQualityProfileSelected = viewModel::setQualityProfile,
          onBufferProfileSelected = viewModel::setBufferProfile,
          onScopeAnimationModeSelected = viewModel::setScopeAnimationMode,
          onRequirePairingChange = viewModel::setRequirePairing,
          onAutoReconnectChange = viewModel::setAutoReconnectEnabled,
          onLockScreenPlaybackChange = viewModel::setLockScreenPlaybackEnabled,
          onGainChange = viewModel::updateGain,
          onToggleMute = viewModel::toggleMute,
          onDismissError = viewModel::dismissError,
        )
      }
    }
  }

  private fun applyLockScreenVisibility(showOverLockScreen: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(showOverLockScreen)
    } else {
      @Suppress("DEPRECATION")
      if (showOverLockScreen) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
      } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
      }
    }
  }
}
