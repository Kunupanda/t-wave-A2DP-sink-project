package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.bluetooth.ShellDiagnosticEntry
import com.example.model.AudioBufferProfile
import com.example.model.AudioQualityProfile
import com.example.model.BluetoothHardwareStatus
import com.example.model.PermissionStatus
import com.example.model.ScopeAnimationMode
import com.example.model.SpeakerMode
import com.example.model.SpeakerServiceState
import com.example.ui.components.AboutDeveloperCard
import com.example.ui.components.AudioEngineControlCard
import com.example.ui.components.AudioVisualizerPanel
import com.example.ui.components.BluetoothHardwareBanner
import com.example.ui.components.ErrorBanner
import com.example.ui.components.RootDiagnosticsDialog
import com.example.ui.components.SppGuideDialog
import com.example.ui.components.SystemAndProtocolFooterCard
import com.example.ui.components.TopConsoleHeader
import com.example.ui.components.TransportControlDeck

@Composable
fun SpeakerScreen(
  serviceState: SpeakerServiceState,
  permissionStatus: PermissionStatus,
  bluetoothStatus: BluetoothHardwareStatus,
  shellDiagnostics: List<ShellDiagnosticEntry> = emptyList(),
  onClearDiagnostics: () -> Unit = {},
  onSpeakerModeSelected: (SpeakerMode) -> Unit,
  onRequestRoot: () -> Unit,
  onApplySdpProfile: () -> Unit,
  onConnectPairedDevice: (String) -> Unit,
  onWakeA2dpAudio: () -> Unit = {},
  onToggleSpeakerMode: () -> Unit,
  onGrantPermissions: () -> Unit,
  onOpenSettings: () -> Unit,
  onEnableBluetooth: () -> Unit,
  onRequestDiscoverable: () -> Unit,
  onLowLatencyChange: (Boolean) -> Unit,
  onUltraLowLatencyChange: (Boolean) -> Unit = {},
  onSyncOffsetChange: (Int) -> Unit = {},
  onQualityProfileSelected: (AudioQualityProfile) -> Unit,
  onBufferProfileSelected: (AudioBufferProfile) -> Unit = {},
  onScopeAnimationModeSelected: (ScopeAnimationMode) -> Unit = {},
  onRequirePairingChange: (Boolean) -> Unit,
  onAutoReconnectChange: (Boolean) -> Unit,
  onLockScreenPlaybackChange: (Boolean) -> Unit = {},
  onGainChange: (Float) -> Unit,
  onToggleMute: () -> Unit,
  onDismissError: () -> Unit,
) {
  var showGuideDialog by remember { mutableStateOf(false) }
  var showDiagnosticsDialog by remember { mutableStateOf(false) }

  if (showGuideDialog) {
    SppGuideDialog(
      qualityProfile = serviceState.qualityProfile,
      lowLatencyMode = serviceState.lowLatencyMode,
      adapterName = bluetoothStatus.adapterName,
      onDismiss = { showGuideDialog = false },
    )
  }

  if (showDiagnosticsDialog) {
    RootDiagnosticsDialog(
      entries = shellDiagnostics,
      onClear = onClearDiagnostics,
      onDismiss = { showDiagnosticsDialog = false },
    )
  }

  val pipelineDescription =
    buildString {
      append(
        serviceState.telemetry.activeOutputDescription.ifBlank {
          if (serviceState.speakerMode == SpeakerMode.A2DP_SINK) "A2DP Sink (0x110B)"
          else serviceState.qualityProfile.shortBadge
        }
      )
      if (serviceState.ultraLowLatencyMode && !contains("ULTRA-LOW")) {
        append(" • ULTRA-LOW")
      } else if (serviceState.lowLatencyMode && !contains("FastMixer")) {
        append(" • FastMixer")
      }
      if (serviceState.syncOffsetMs != 0) {
        append(" • Sync ${com.example.audio.PcmSyncDelayLine.formatOffsetLabel(serviceState.syncOffsetMs)}")
      }
    }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets.safeDrawing,
  ) { innerPadding ->
    val bgBrush =
      Brush.verticalGradient(
        colors =
          listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background,
          )
      )

    BoxWithConstraints(
      modifier = Modifier.fillMaxSize().background(bgBrush).padding(innerPadding)
    ) {
      val isWideLayout = maxWidth >= 720.dp
      val scrollState = rememberScrollState()

      Column(
        modifier =
          Modifier.fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().widthIn(max = 1080.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          TopConsoleHeader(
            status = serviceState.status,
            bleAdvertisingActive = serviceState.bleAdvertisingActive,
            onShowGuide = { showGuideDialog = true },
          )

          serviceState.errorMessage?.let { errorMsg ->
            ErrorBanner(message = errorMsg, onDismiss = onDismissError)
          }

          BluetoothHardwareBanner(
            bluetoothStatus = bluetoothStatus,
            onEnableBluetooth = onEnableBluetooth,
          )

          if (isWideLayout) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(16.dp),
              verticalAlignment = Alignment.Top,
            ) {
              Column(
                modifier = Modifier.weight(0.58f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                AudioVisualizerPanel(
                  status = serviceState.status,
                  telemetry = serviceState.telemetry,
                  qualityProfile = serviceState.qualityProfile,
                  lowLatencyMode = serviceState.lowLatencyMode,
                  isMuted = serviceState.isMuted,
                  ultraLowLatencyMode = serviceState.ultraLowLatencyMode,
                  syncOffsetMs = serviceState.syncOffsetMs,
                  scopeAnimationMode = serviceState.scopeAnimationMode,
                  onScopeAnimationModeSelected = onScopeAnimationModeSelected,
                )
                AudioEngineControlCard(
                  lowLatencyMode = serviceState.lowLatencyMode,
                  ultraLowLatencyMode = serviceState.ultraLowLatencyMode,
                  syncOffsetMs = serviceState.syncOffsetMs,
                  qualityProfile = serviceState.qualityProfile,
                  bufferProfile = serviceState.bufferProfile,
                  hardwareMaxSampleRateHz = serviceState.hardwareMaxSampleRateHz,
                  hardwareSupportsHiRes = serviceState.hardwareSupportsHiRes,
                  onLowLatencyChange = onLowLatencyChange,
                  onUltraLowLatencyChange = onUltraLowLatencyChange,
                  onSyncOffsetChange = onSyncOffsetChange,
                  onQualityProfileSelected = onQualityProfileSelected,
                  onBufferProfileSelected = onBufferProfileSelected,
                )
              }

              Column(
                modifier = Modifier.weight(0.42f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                TransportControlDeck(
                  status = serviceState.status,
                  speakerMode = serviceState.speakerMode,
                  rootStatus = serviceState.rootStatus,
                  isTogglingA2dpRoot = serviceState.isTogglingA2dpRoot,
                  pairedDevices = serviceState.pairedDevices,
                  connectingDeviceAddress = serviceState.connectingDeviceAddress,
                  connectedDevice = serviceState.connectedDevice,
                  lastConnectedDevice = serviceState.lastConnectedDevice,
                  reconnectAttempt = serviceState.reconnectAttempt,
                  maxReconnectAttempts = serviceState.maxReconnectAttempts,
                  bluetoothStatus = bluetoothStatus,
                  gain = serviceState.gain,
                  isMuted = serviceState.isMuted,
                  onSpeakerModeSelected = onSpeakerModeSelected,
                  onRequestRoot = onRequestRoot,
                  onApplySdpProfile = onApplySdpProfile,
                  onShowDiagnostics = { showDiagnosticsDialog = true },
                  shellDiagnosticsCount = shellDiagnostics.size,
                  onConnectPairedDevice = onConnectPairedDevice,
                  onWakeA2dpAudio = onWakeA2dpAudio,
                  onToggleSpeakerMode = onToggleSpeakerMode,
                  onRequestDiscoverable = onRequestDiscoverable,
                  onGainChange = onGainChange,
                  onToggleMute = onToggleMute,
                )
                SystemAndProtocolFooterCard(
                  permissionStatus = permissionStatus,
                  speakerMode = serviceState.speakerMode,
                  localAdapterName = bluetoothStatus.adapterName,
                  connectedDevice = serviceState.connectedDevice,
                  lastConnectedDevice = serviceState.lastConnectedDevice,
                  audioPipelineDescription = pipelineDescription,
                  requirePairing = serviceState.requirePairing,
                  autoReconnectEnabled = serviceState.autoReconnectEnabled,
                  lockScreenPlaybackEnabled = serviceState.lockScreenPlaybackEnabled,
                  onRequirePairingChange = onRequirePairingChange,
                  onAutoReconnectChange = onAutoReconnectChange,
                  onLockScreenPlaybackChange = onLockScreenPlaybackChange,
                  onGrantPermissions = onGrantPermissions,
                  onOpenSettings = onOpenSettings,
                )
                AboutDeveloperCard()
              }
            }
          } else {
            TransportControlDeck(
              status = serviceState.status,
              speakerMode = serviceState.speakerMode,
              rootStatus = serviceState.rootStatus,
              isTogglingA2dpRoot = serviceState.isTogglingA2dpRoot,
              pairedDevices = serviceState.pairedDevices,
              connectingDeviceAddress = serviceState.connectingDeviceAddress,
              connectedDevice = serviceState.connectedDevice,
              lastConnectedDevice = serviceState.lastConnectedDevice,
              reconnectAttempt = serviceState.reconnectAttempt,
              maxReconnectAttempts = serviceState.maxReconnectAttempts,
              bluetoothStatus = bluetoothStatus,
              gain = serviceState.gain,
              isMuted = serviceState.isMuted,
              onSpeakerModeSelected = onSpeakerModeSelected,
              onRequestRoot = onRequestRoot,
              onApplySdpProfile = onApplySdpProfile,
              onShowDiagnostics = { showDiagnosticsDialog = true },
              shellDiagnosticsCount = shellDiagnostics.size,
              onConnectPairedDevice = onConnectPairedDevice,
              onWakeA2dpAudio = onWakeA2dpAudio,
              onToggleSpeakerMode = onToggleSpeakerMode,
              onRequestDiscoverable = onRequestDiscoverable,
              onGainChange = onGainChange,
              onToggleMute = onToggleMute,
            )
            AudioVisualizerPanel(
              status = serviceState.status,
              telemetry = serviceState.telemetry,
              qualityProfile = serviceState.qualityProfile,
              lowLatencyMode = serviceState.lowLatencyMode,
              isMuted = serviceState.isMuted,
              ultraLowLatencyMode = serviceState.ultraLowLatencyMode,
              syncOffsetMs = serviceState.syncOffsetMs,
              scopeAnimationMode = serviceState.scopeAnimationMode,
              onScopeAnimationModeSelected = onScopeAnimationModeSelected,
            )
            AudioEngineControlCard(
              lowLatencyMode = serviceState.lowLatencyMode,
              ultraLowLatencyMode = serviceState.ultraLowLatencyMode,
              syncOffsetMs = serviceState.syncOffsetMs,
              qualityProfile = serviceState.qualityProfile,
              bufferProfile = serviceState.bufferProfile,
              hardwareMaxSampleRateHz = serviceState.hardwareMaxSampleRateHz,
              hardwareSupportsHiRes = serviceState.hardwareSupportsHiRes,
              onLowLatencyChange = onLowLatencyChange,
              onUltraLowLatencyChange = onUltraLowLatencyChange,
              onSyncOffsetChange = onSyncOffsetChange,
              onQualityProfileSelected = onQualityProfileSelected,
              onBufferProfileSelected = onBufferProfileSelected,
            )
            SystemAndProtocolFooterCard(
              permissionStatus = permissionStatus,
              speakerMode = serviceState.speakerMode,
              localAdapterName = bluetoothStatus.adapterName,
              connectedDevice = serviceState.connectedDevice,
              lastConnectedDevice = serviceState.lastConnectedDevice,
              audioPipelineDescription = pipelineDescription,
              requirePairing = serviceState.requirePairing,
              autoReconnectEnabled = serviceState.autoReconnectEnabled,
              lockScreenPlaybackEnabled = serviceState.lockScreenPlaybackEnabled,
              onRequirePairingChange = onRequirePairingChange,
              onAutoReconnectChange = onAutoReconnectChange,
              onLockScreenPlaybackChange = onLockScreenPlaybackChange,
              onGrantPermissions = onGrantPermissions,
              onOpenSettings = onOpenSettings,
            )
            AboutDeveloperCard()
          }
        }
      }
    }
  }
}
