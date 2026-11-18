package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PcmAudioTrackConfigurator
import com.example.audio.PcmSyncDelayLine
import com.example.model.AudioBufferProfile
import com.example.model.AudioQualityProfile
import com.example.model.BluetoothHardwareStatus
import com.example.model.ConnectedDeviceInfo
import com.example.model.ConnectionStatus
import com.example.model.PairedDeviceItem
import com.example.model.PermissionStatus
import com.example.model.RootA2dpStatus
import com.example.model.RootPrivilegeStatus
import com.example.model.SpeakerMode
import com.example.model.SpeakerServiceState
import com.example.service.BluetoothSpeakerService
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAcousticColors
import com.example.ui.theme.MonoLabelStyle
import kotlin.math.roundToInt

@Composable
fun TopConsoleHeader(
  status: ConnectionStatus,
  bleAdvertisingActive: Boolean,
  onShowGuide: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val acousticColors = LocalAcousticColors.current
  val beaconColor by
    animateColorAsState(
      targetValue =
        when (status) {
          ConnectionStatus.IDLE -> acousticColors.idleBeacon
          ConnectionStatus.LISTENING -> acousticColors.listeningBeacon
          ConnectionStatus.RECONNECTING -> acousticColors.vuPeakAmber
          ConnectionStatus.CONNECTED -> acousticColors.connectedBeacon
        },
      animationSpec = tween(300),
      label = "header_beacon_color",
    )

  val infiniteTransition = rememberInfiniteTransition(label = "header_pulse")
  val pulseScale by
    infiniteTransition.animateFloat(
      initialValue = 0.65f,
      targetValue = 1.35f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(1400, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "header_pulse_scale",
    )

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          text = stringResource(R.string.app_name),
          style =
            MaterialTheme.typography.headlineLarge.copy(
              fontFamily = JetBrainsMonoFontFamily,
              fontWeight = FontWeight.Bold,
            ),
          color = MaterialTheme.colorScheme.onBackground,
        )
        Surface(
          modifier = Modifier.testTag("connection_status_badge"),
          shape = RoundedCornerShape(50),
          color = beaconColor.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, beaconColor.copy(alpha = 0.45f)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(modifier = Modifier.size(12.dp), contentAlignment = Alignment.Center) {
              Canvas(modifier = Modifier.size(12.dp)) {
                val baseRadius = size.minDimension * 0.25f
                if (status != ConnectionStatus.IDLE) {
                  drawCircle(
                    color = beaconColor.copy(alpha = 0.3f),
                    radius = baseRadius * pulseScale * 1.45f,
                  )
                }
                drawCircle(color = beaconColor, radius = baseRadius)
              }
            }
            Text(
              text =
                when (status) {
                  ConnectionStatus.IDLE -> stringResource(R.string.status_idle)
                  ConnectionStatus.LISTENING -> stringResource(R.string.status_listening)
                  ConnectionStatus.RECONNECTING -> stringResource(R.string.status_reconnecting)
                  ConnectionStatus.CONNECTED -> stringResource(R.string.status_connected)
                },
              style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
              color = beaconColor,
            )
          }
        }
        if (bleAdvertisingActive && status == ConnectionStatus.LISTENING) {
          Surface(
            shape = RoundedCornerShape(50),
            color = acousticColors.vuSpectrumCyan.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, acousticColors.vuSpectrumCyan.copy(alpha = 0.35f)),
          ) {
            Text(
              text = stringResource(R.string.ble_beacon_active),
              style = MaterialTheme.typography.labelSmall,
              color = acousticColors.vuSpectrumCyan,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
      }
      Box(
        modifier =
          Modifier.width(44.dp)
            .height(3.dp)
            .clip(CircleShape)
            .background(
              Brush.horizontalGradient(
                listOf(acousticColors.vuSpectrumCyan, acousticColors.vuPeakAmber)
              )
            )
      )
    }

    FilledTonalIconButton(
      onClick = onShowGuide,
      modifier = Modifier.minimumInteractiveComponentSize().testTag("protocol_guide_button"),
      colors =
        IconButtonDefaults.filledTonalIconButtonColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer,
          contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
      Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = stringResource(R.string.action_protocol_guide),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransportControlDeck(
  status: ConnectionStatus,
  speakerMode: SpeakerMode,
  rootStatus: RootA2dpStatus,
  isTogglingA2dpRoot: Boolean,
  pairedDevices: List<PairedDeviceItem>,
  connectingDeviceAddress: String?,
  connectedDevice: ConnectedDeviceInfo?,
  lastConnectedDevice: ConnectedDeviceInfo?,
  reconnectAttempt: Int,
  maxReconnectAttempts: Int,
  bluetoothStatus: BluetoothHardwareStatus,
  gain: Float,
  isMuted: Boolean,
  onSpeakerModeSelected: (SpeakerMode) -> Unit,
  onRequestRoot: () -> Unit,
  onApplySdpProfile: () -> Unit,
  onShowDiagnostics: () -> Unit = {},
  shellDiagnosticsCount: Int = 0,
  onConnectPairedDevice: (String) -> Unit,
  onWakeA2dpAudio: () -> Unit = {},
  onToggleSpeakerMode: () -> Unit,
  onRequestDiscoverable: () -> Unit,
  onGainChange: (Float) -> Unit,
  onToggleMute: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val acousticColors = LocalAcousticColors.current
  val isActive = status != ConnectionStatus.IDLE
  val accentColor =
    when (status) {
      ConnectionStatus.IDLE -> acousticColors.surfaceBorder
      ConnectionStatus.LISTENING -> acousticColors.listeningBeacon.copy(alpha = 0.55f)
      ConnectionStatus.RECONNECTING -> acousticColors.vuPeakAmber.copy(alpha = 0.65f)
      ConnectionStatus.CONNECTED -> acousticColors.connectedBeacon.copy(alpha = 0.6f)
    }

  Card(
    modifier = modifier.fillMaxWidth().testTag("transport_deck"),
    shape = RoundedCornerShape(24.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, accentColor),
  ) {
    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Speaker Mode Protocol Selector (Root A2DP Sink vs Non-Root RFCOMM SPP)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.speaker_mode_label).uppercase(),
          style = MonoLabelStyle,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          SpeakerMode.entries.forEach { mode ->
            val selected = mode == speakerMode
            FilterChip(
              selected = selected,
              onClick = { onSpeakerModeSelected(mode) },
              leadingIcon = {
                Icon(
                  imageVector =
                    if (mode.requiresRoot) Icons.Rounded.AdminPanelSettings
                    else Icons.Rounded.Bluetooth,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
              },
              label = {
                Text(
                  text =
                    if (mode == SpeakerMode.A2DP_SINK) stringResource(R.string.mode_a2dp_sink)
                    else stringResource(R.string.mode_rfcomm_spp),
                  style = MonoLabelStyle,
                )
              },
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("mode_chip_${mode.name}"),
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor =
                    if (mode.requiresRoot) acousticColors.vuPeakAmber.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor =
                    if (mode.requiresRoot) acousticColors.vuPeakAmber
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
          }
        }
      }

      // Root Privilege Gate & A2DP Diagnostics Card
      if (speakerMode == SpeakerMode.A2DP_SINK) {
        val rootColor =
          when (rootStatus.privilegeStatus) {
            RootPrivilegeStatus.GRANTED ->
              if (rootStatus.isA2dpSinkEnabled && !rootStatus.isA2dpSinkServiceRunning && isActive) {
                acousticColors.vuPeakAmber
              } else {
                acousticColors.connectedBeacon
              }
            RootPrivilegeStatus.CHECKING -> acousticColors.listeningBeacon
            RootPrivilegeStatus.DENIED,
            RootPrivilegeStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
            RootPrivilegeStatus.UNKNOWN -> acousticColors.vuPeakAmber
          }
        Surface(
          modifier = Modifier.fillMaxWidth().testTag("root_status_card"),
          shape = RoundedCornerShape(14.dp),
          color = rootColor.copy(alpha = 0.10f),
          border = BorderStroke(1.dp, rootColor.copy(alpha = 0.4f)),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector =
                  if (rootStatus.isRootGranted) Icons.Rounded.VerifiedUser
                  else Icons.Rounded.GppMaybe,
                contentDescription = null,
                tint = rootColor,
              )
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
              ) {
                Text(
                  text =
                    if (rootStatus.isRootGranted) stringResource(R.string.root_card_title_granted)
                    else stringResource(R.string.root_card_title_required),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    when (rootStatus.privilegeStatus) {
                      RootPrivilegeStatus.GRANTED ->
                        when {
                          rootStatus.isA2dpSinkServiceRunning ->
                            stringResource(R.string.root_card_desc_granted_active)
                          rootStatus.isA2dpSinkEnabled ->
                            stringResource(R.string.root_card_desc_granted_inactive)
                          else -> stringResource(R.string.root_card_desc_granted_ready)
                        }
                      RootPrivilegeStatus.CHECKING -> "Verifying superuser (su) permission…"
                      RootPrivilegeStatus.DENIED -> stringResource(R.string.root_card_desc_denied)
                      RootPrivilegeStatus.UNAVAILABLE ->
                        stringResource(R.string.root_card_desc_unavailable)
                      RootPrivilegeStatus.UNKNOWN -> stringResource(R.string.root_card_desc_unknown)
                    },
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              FilledTonalButton(
                onClick = onRequestRoot,
                enabled = rootStatus.privilegeStatus != RootPrivilegeStatus.CHECKING,
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("request_root_button"),
                shape = RoundedCornerShape(12.dp),
              ) {
                Text(
                  text =
                    if (rootStatus.isRootGranted) stringResource(R.string.action_recheck_root)
                    else stringResource(R.string.action_request_root),
                  maxLines = 1,
                )
              }
            }

            if (rootStatus.isRootGranted) {
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                DiagnosticChip(
                  label =
                    if (rootStatus.isA2dpSinkServiceRunning) "A2dpSinkService: ON"
                    else "A2dpSinkService: OFF",
                  active = rootStatus.isA2dpSinkServiceRunning,
                )
                DiagnosticChip(
                  label =
                    if (rootStatus.isAudioSinkSdpExposed) "SDP 0x110B: ON"
                    else "SDP 0x110B: OFF",
                  active = rootStatus.isAudioSinkSdpExposed,
                )
                DiagnosticChip(
                  label =
                    if (rootStatus.isAvrcpSdpExposed) "SDP 0x110E: ON"
                    else "SDP 0x110E: OFF",
                  active = rootStatus.isAvrcpSdpExposed,
                )
                DiagnosticChip(
                  label = "CoD: ${rootStatus.classOfDevice.ifBlank { "default" }}",
                  active =
                    rootStatus.classOfDevice == com.example.bluetooth.RootA2dpController.AUDIO_SINK_COD,
                )
                DiagnosticChip(
                  label = if (rootStatus.isOffloadDisabled) "Offload: OFF" else "Offload: ON",
                  active = rootStatus.isOffloadDisabled,
                )
                if (rootStatus.isBtConfigPatched) {
                  DiagnosticChip(label = "bt_config: 240414", active = true)
                }
              }

              if (rootStatus.lastCommandLog.isNotBlank()) {
                Text(
                  text = rootStatus.lastCommandLog,
                  style = MonoLabelStyle,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedButton(
                  onClick = onApplySdpProfile,
                  enabled = !isTogglingA2dpRoot,
                  modifier =
                    Modifier.weight(1f)
                      .minimumInteractiveComponentSize()
                      .testTag("apply_sdp_profile_button"),
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Text(text = stringResource(R.string.action_apply_sdp_profile), maxLines = 1)
                }
                OutlinedButton(
                  onClick = onShowDiagnostics,
                  modifier =
                    Modifier.minimumInteractiveComponentSize().testTag("view_diagnostics_button"),
                  shape = RoundedCornerShape(10.dp),
                ) {
                  Text(
                    text = stringResource(R.string.action_view_shell_logs, shellDiagnosticsCount),
                    maxLines = 1,
                  )
                }
              }
            } else if (shellDiagnosticsCount > 0) {
              OutlinedButton(
                onClick = onShowDiagnostics,
                modifier =
                  Modifier.fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("view_diagnostics_button"),
                shape = RoundedCornerShape(10.dp),
              ) {
                Text(
                  text = stringResource(R.string.action_view_shell_logs, shellDiagnosticsCount),
                  maxLines = 1,
                )
              }
            }
          }
        }
      }
      // Bluetooth Discoverability & Scan Name Callout
      if (bluetoothStatus.isSupported && bluetoothStatus.isEnabled) {
        val visible = bluetoothStatus.isDiscoverable
        val visColor =
          if (visible) acousticColors.connectedBeacon else acousticColors.vuPeakAmber
        Surface(
          modifier = Modifier.fillMaxWidth().testTag("visibility_status_card"),
          shape = RoundedCornerShape(14.dp),
          color = visColor.copy(alpha = 0.10f),
          border = BorderStroke(1.dp, visColor.copy(alpha = 0.35f)),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector =
                if (visible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
              contentDescription = null,
              tint = visColor,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              val remaining = bluetoothStatus.discoverableRemainingSeconds
              Text(
                text =
                  when {
                    visible && remaining > 0 -> {
                      val mins = remaining / 60
                      val secs = remaining % 60
                      val timeLabel =
                        if (mins > 0) "${mins}m ${secs.toString().padStart(2, '0')}s"
                        else "${secs}s"
                      stringResource(R.string.visibility_visible_countdown, timeLabel)
                    }
                    visible -> stringResource(R.string.visibility_visible_title)
                    else -> stringResource(R.string.visibility_hidden_title)
                  },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text =
                  stringResource(
                    R.string.visibility_scan_hint,
                    bluetoothStatus.adapterName,
                  ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            FilledTonalButton(
              onClick = onRequestDiscoverable,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("discoverable_button"),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text(
                text =
                  if (visible) stringResource(R.string.action_discoverable_active)
                  else stringResource(R.string.action_make_discoverable),
                maxLines = 1,
              )
            }
          }
        }
      }

      // Connected Device Panel (always visible across all states)
      val deviceBannerAccent =
        when (status) {
          ConnectionStatus.CONNECTED -> acousticColors.connectedBeacon
          ConnectionStatus.RECONNECTING -> acousticColors.vuPeakAmber
          ConnectionStatus.LISTENING,
          ConnectionStatus.IDLE -> acousticColors.surfaceBorder
        }
      Surface(
        modifier = Modifier.fillMaxWidth().testTag("connected_device_banner"),
        shape = RoundedCornerShape(14.dp),
        color =
          when (status) {
            ConnectionStatus.CONNECTED -> acousticColors.connectedBeacon.copy(alpha = 0.12f)
            ConnectionStatus.RECONNECTING -> acousticColors.vuPeakAmber.copy(alpha = 0.12f)
            ConnectionStatus.LISTENING,
            ConnectionStatus.IDLE -> acousticColors.surfaceWell
          },
        border =
          BorderStroke(
            1.dp,
            if (status == ConnectionStatus.CONNECTED || status == ConnectionStatus.RECONNECTING) {
              deviceBannerAccent.copy(alpha = 0.45f)
            } else {
              acousticColors.surfaceBorder
            },
          ),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector =
              when (status) {
                ConnectionStatus.CONNECTED -> Icons.Rounded.GraphicEq
                ConnectionStatus.RECONNECTING -> Icons.Rounded.Sync
                ConnectionStatus.LISTENING,
                ConnectionStatus.IDLE -> Icons.Rounded.Bluetooth
              },
            contentDescription = null,
            tint =
              when (status) {
                ConnectionStatus.CONNECTED -> acousticColors.connectedBeacon
                ConnectionStatus.RECONNECTING -> acousticColors.vuPeakAmber
                ConnectionStatus.LISTENING,
                ConnectionStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
              },
          )
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = stringResource(R.string.connected_device_label).uppercase(),
              style = MonoLabelStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
              status == ConnectionStatus.CONNECTED && connectedDevice != null -> {
                Text(
                  text = connectedDevice.name,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = connectedDevice.address,
                  style = MonoLabelStyle,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              status == ConnectionStatus.RECONNECTING -> {
                val targetName = lastConnectedDevice?.name ?: "Bluetooth Audio Source"
                val targetAddr = lastConnectedDevice?.address ?: ""
                Text(
                  text = stringResource(R.string.reconnecting_device_title, targetName),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text =
                    stringResource(
                      R.string.reconnecting_attempt_subtitle,
                      reconnectAttempt.coerceAtLeast(1),
                      maxReconnectAttempts,
                      targetAddr,
                    ),
                  style = MonoLabelStyle,
                  color = acousticColors.vuPeakAmber,
                )
              }
              else -> {
                Text(
                  text = stringResource(R.string.connected_device_none),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (lastConnectedDevice != null) {
                  Text(
                    text =
                      stringResource(
                        R.string.last_device_hint,
                        lastConnectedDevice.name,
                        lastConnectedDevice.address,
                      ),
                    style = MonoLabelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
          if (status == ConnectionStatus.CONNECTED && speakerMode == SpeakerMode.A2DP_SINK) {
            FilledTonalButton(
              onClick = onWakeA2dpAudio,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("wake_a2dp_audio_button"),
              shape = RoundedCornerShape(10.dp),
            ) {
              Text(text = stringResource(R.string.action_wake_a2dp_audio), maxLines = 1)
            }
          }
        }
      }

      if (status != ConnectionStatus.CONNECTED) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          when (status) {
            ConnectionStatus.LISTENING ->
              Icon(
                imageVector = Icons.Rounded.BluetoothSearching,
                contentDescription = null,
                tint = acousticColors.listeningBeacon,
                modifier = Modifier.size(18.dp),
              )
            ConnectionStatus.RECONNECTING ->
              Icon(
                imageVector = Icons.Rounded.Sync,
                contentDescription = null,
                tint = acousticColors.vuPeakAmber,
                modifier = Modifier.size(18.dp),
              )
            else -> Unit
          }
          Text(
            text =
              when (status) {
                ConnectionStatus.IDLE -> stringResource(R.string.status_desc_idle)
                ConnectionStatus.LISTENING ->
                  if (speakerMode == SpeakerMode.A2DP_SINK) {
                    stringResource(R.string.status_desc_listening_a2dp, bluetoothStatus.adapterName)
                  } else {
                    stringResource(R.string.status_desc_listening, bluetoothStatus.adapterName)
                  }
                ConnectionStatus.RECONNECTING ->
                  stringResource(
                    R.string.status_desc_reconnecting,
                    lastConnectedDevice?.name ?: "Bluetooth Audio Source",
                    reconnectAttempt.coerceAtLeast(1),
                    maxReconnectAttempts,
                  )
                ConnectionStatus.CONNECTED -> stringResource(R.string.status_desc_connected)
              },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Integrated Master Gain Fader + Mute
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(acousticColors.surfaceWell)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IconButton(
          onClick = onToggleMute,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("mute_button"),
        ) {
          Icon(
            imageVector =
              if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff
              else Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription =
              if (isMuted) stringResource(R.string.action_unmute)
              else stringResource(R.string.action_mute),
            tint =
              if (isMuted) MaterialTheme.colorScheme.error
              else MaterialTheme.colorScheme.primary,
          )
        }
        Slider(
          value = if (isMuted) 0f else gain,
          onValueChange = { newVal ->
            if (isMuted && newVal > 0f) onToggleMute()
            onGainChange(newVal)
          },
          modifier = Modifier.weight(1f).testTag("volume_slider"),
        )
        Text(
          text = if (isMuted) "MUTE" else "${(gain * 100).toInt()}%",
          style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
          color =
            if (isMuted) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.width(42.dp),
        )
      }

      // Primary Speaker Mode Toggle Button
      Button(
        onClick = onToggleSpeakerMode,
        enabled = !isTogglingA2dpRoot,
        modifier =
          Modifier.fillMaxWidth()
            .height(56.dp)
            .minimumInteractiveComponentSize()
            .testTag("speaker_mode_toggle_button"),
        shape = RoundedCornerShape(16.dp),
        colors =
          if (isActive) {
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.errorContainer,
              contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
          } else {
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            )
          },
      ) {
        if (isTogglingA2dpRoot) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.action_configuring_root),
            style = MaterialTheme.typography.labelLarge,
          )
        } else {
          Icon(
            imageVector =
              when {
                isActive -> Icons.Rounded.Stop
                speakerMode == SpeakerMode.A2DP_SINK && !rootStatus.isRootGranted ->
                  Icons.Rounded.Lock
                else -> Icons.Rounded.PlayArrow
              },
            contentDescription = null,
            modifier = Modifier.size(22.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              if (isActive) stringResource(R.string.action_stop_speaker_mode)
              else stringResource(R.string.action_start_speaker_mode),
            style = MaterialTheme.typography.labelLarge,
          )
        }
      }

      // Paired Audio Sources panel (allows connecting directly & reminds about re-pairing stale cache)
      if (speakerMode == SpeakerMode.A2DP_SINK && bluetoothStatus.isSupported && bluetoothStatus.isEnabled) {
        Surface(
          modifier = Modifier.fillMaxWidth().testTag("paired_devices_card"),
          shape = RoundedCornerShape(14.dp),
          color = acousticColors.surfaceWell,
          border = BorderStroke(1.dp, acousticColors.surfaceBorder),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = stringResource(R.string.paired_devices_title).uppercase(),
              style = MonoLabelStyle,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = stringResource(R.string.paired_devices_repair_hint),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pairedDevices.isEmpty()) {
              Text(
                text = stringResource(R.string.paired_devices_empty),
                style = MonoLabelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            } else {
              pairedDevices.forEach { device ->
                val isDeviceConnecting =
                  connectingDeviceAddress.equals(device.address, ignoreCase = true)
                val isDeviceConnected =
                  connectedDevice?.address.equals(device.address, ignoreCase = true) ||
                    device.isConnected
                Row(
                  modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = device.name,
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                      text = device.address,
                      style = MonoLabelStyle,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  FilledTonalButton(
                    onClick = { onConnectPairedDevice(device.address) },
                    enabled = isActive && !isDeviceConnecting && !isDeviceConnected,
                    modifier =
                      Modifier.minimumInteractiveComponentSize()
                        .testTag("connect_paired_${device.address}"),
                    shape = RoundedCornerShape(10.dp),
                  ) {
                    Text(
                      text =
                        when {
                          isDeviceConnected -> stringResource(R.string.status_device_connected)
                          isDeviceConnecting -> stringResource(R.string.action_connecting_device)
                          else -> stringResource(R.string.action_connect_device)
                        },
                      maxLines = 1,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DiagnosticChip(label: String, active: Boolean) {
  val acousticColors = LocalAcousticColors.current
  val chipColor = if (active) acousticColors.connectedBeacon else acousticColors.vuPeakAmber
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = chipColor.copy(alpha = 0.14f),
    border = BorderStroke(1.dp, chipColor.copy(alpha = 0.35f)),
  ) {
    Text(
      text = label,
      style = MonoLabelStyle,
      color = chipColor,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
    )
  }
}

@Composable
fun AudioEngineControlCard(
  lowLatencyMode: Boolean,
  ultraLowLatencyMode: Boolean = false,
  syncOffsetMs: Int = 0,
  qualityProfile: AudioQualityProfile,
  bufferProfile: AudioBufferProfile = AudioBufferProfile.BALANCED,
  hardwareMaxSampleRateHz: Int,
  hardwareSupportsHiRes: Boolean,
  onLowLatencyChange: (Boolean) -> Unit,
  onUltraLowLatencyChange: (Boolean) -> Unit = {},
  onSyncOffsetChange: (Int) -> Unit = {},
  onQualityProfileSelected: (AudioQualityProfile) -> Unit,
  onBufferProfileSelected: (AudioBufferProfile) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val acousticColors = LocalAcousticColors.current
  Card(
    modifier = modifier.fillMaxWidth().testTag("audio_engine_card"),
    shape = RoundedCornerShape(22.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, acousticColors.surfaceBorder),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.HighQuality,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Text(
            text = stringResource(R.string.audio_engine_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = acousticColors.surfaceWell,
          border = BorderStroke(1.dp, acousticColors.surfaceBorder),
        ) {
          Text(
            text =
              if (hardwareSupportsHiRes) "DAC ≤ ${hardwareMaxSampleRateHz / 1000} kHz"
              else "DAC ${hardwareMaxSampleRateHz / 1000} kHz",
            style = MonoLabelStyle,
            color =
              if (hardwareSupportsHiRes) acousticColors.vuPeakAmber
              else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      Surface(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onLowLatencyChange(!lowLatencyMode) }
            .minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(14.dp),
        color =
          if (lowLatencyMode) acousticColors.connectedBeacon.copy(alpha = 0.1f)
          else acousticColors.surfaceWell,
        border =
          BorderStroke(
            1.dp,
            if (lowLatencyMode) acousticColors.connectedBeacon.copy(alpha = 0.45f)
            else acousticColors.surfaceBorder,
          ),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = null,
            tint =
              if (lowLatencyMode) acousticColors.connectedBeacon
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.low_latency_title),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.low_latency_desc),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = lowLatencyMode,
            onCheckedChange = onLowLatencyChange,
            modifier = Modifier.testTag("low_latency_switch"),
          )
        }
      }

      Surface(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onUltraLowLatencyChange(!ultraLowLatencyMode) }
            .minimumInteractiveComponentSize(),
        shape = RoundedCornerShape(14.dp),
        color =
          if (ultraLowLatencyMode) acousticColors.vuSpectrumCyan.copy(alpha = 0.12f)
          else acousticColors.surfaceWell,
        border =
          BorderStroke(
            1.dp,
            if (ultraLowLatencyMode) acousticColors.vuSpectrumCyan.copy(alpha = 0.5f)
            else acousticColors.surfaceBorder,
          ),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Speed,
            contentDescription = null,
            tint =
              if (ultraLowLatencyMode) acousticColors.vuSpectrumCyan
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.ultra_low_latency_title),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.ultra_low_latency_desc),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = ultraLowLatencyMode,
            onCheckedChange = onUltraLowLatencyChange,
            modifier = Modifier.testTag("ultra_low_latency_switch"),
          )
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.hires_profile_label),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AudioQualityProfile.entries.forEach { profile ->
            val selected = profile == qualityProfile
            FilterChip(
              selected = selected,
              onClick = { onQualityProfileSelected(profile) },
              label = { Text(text = profile.shortBadge, style = MonoLabelStyle) },
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("quality_chip_${profile.name}"),
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor =
                    if (profile.isHiRes) acousticColors.vuPeakAmber.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor =
                    if (profile.isHiRes) acousticColors.vuPeakAmber
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.buffer_profile_label),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AudioBufferProfile.entries.forEach { profile ->
            val selected = profile == bufferProfile
            FilterChip(
              selected = selected,
              onClick = { onBufferProfileSelected(profile) },
              label = { Text(text = profile.shortLabel, style = MonoLabelStyle) },
              modifier =
                Modifier.minimumInteractiveComponentSize()
                  .testTag("buffer_chip_${profile.name}"),
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor =
                    if (profile == AudioBufferProfile.HIRES_DEEP) {
                      acousticColors.vuPeakAmber.copy(alpha = 0.18f)
                    } else {
                      MaterialTheme.colorScheme.primaryContainer
                    },
                  selectedLabelColor =
                    if (profile == AudioBufferProfile.HIRES_DEEP) {
                      acousticColors.vuPeakAmber
                    } else {
                      MaterialTheme.colorScheme.onPrimaryContainer
                    },
                ),
            )
          }
        }
      }

      // Manual Audio Synchronization Offset Slider
      val syncActive = syncOffsetMs != 0
      val syncAccent =
        when {
          syncOffsetMs > 0 -> acousticColors.vuPeakAmber
          syncOffsetMs < 0 -> acousticColors.vuSpectrumCyan
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
      Surface(
        modifier = Modifier.fillMaxWidth().testTag("sync_offset_card"),
        shape = RoundedCornerShape(14.dp),
        color =
          if (syncActive) syncAccent.copy(alpha = 0.08f) else acousticColors.surfaceWell,
        border =
          BorderStroke(
            1.dp,
            if (syncActive) syncAccent.copy(alpha = 0.45f) else acousticColors.surfaceBorder,
          ),
      ) {
        Column(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Tune,
              contentDescription = null,
              tint = syncAccent,
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.sync_offset_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = stringResource(R.string.sync_offset_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (syncActive) {
              TextButton(
                onClick = { onSyncOffsetChange(0) },
                modifier =
                  Modifier.minimumInteractiveComponentSize().testTag("sync_offset_reset_button"),
              ) {
                Text(
                  text = stringResource(R.string.sync_offset_reset),
                  style = MonoLabelStyle,
                )
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = stringResource(R.string.sync_offset_range_hint),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
              modifier = Modifier.testTag("sync_offset_value_badge"),
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, syncAccent.copy(alpha = 0.45f)),
            ) {
              Text(
                text = PcmSyncDelayLine.formatOffsetBadge(syncOffsetMs),
                style = MonoLabelStyle,
                color =
                  if (syncActive) syncAccent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            FilledTonalIconButton(
              onClick = {
                onSyncOffsetChange(syncOffsetMs - SpeakerServiceState.SYNC_OFFSET_STEP_MS)
              },
              enabled = syncOffsetMs > SpeakerServiceState.MIN_SYNC_OFFSET_MS,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("sync_offset_minus_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Remove,
                contentDescription = stringResource(R.string.sync_offset_minus_desc),
              )
            }

            Slider(
              value = syncOffsetMs.toFloat(),
              onValueChange = { rawValue ->
                val snapped = PcmSyncDelayLine.clampAndSnapOffset(rawValue.roundToInt())
                if (snapped != syncOffsetMs) {
                  onSyncOffsetChange(snapped)
                }
              },
              valueRange =
                SpeakerServiceState.MIN_SYNC_OFFSET_MS.toFloat()..
                  SpeakerServiceState.MAX_SYNC_OFFSET_MS.toFloat(),
              steps =
                ((SpeakerServiceState.MAX_SYNC_OFFSET_MS -
                  SpeakerServiceState.MIN_SYNC_OFFSET_MS) /
                  SpeakerServiceState.SYNC_OFFSET_STEP_MS) - 1,
              colors =
                SliderDefaults.colors(
                  thumbColor = if (syncActive) syncAccent else MaterialTheme.colorScheme.primary,
                  activeTrackColor =
                    if (syncActive) syncAccent else MaterialTheme.colorScheme.primary,
                ),
              modifier = Modifier.weight(1f).testTag("sync_offset_slider"),
            )

            FilledTonalIconButton(
              onClick = {
                onSyncOffsetChange(syncOffsetMs + SpeakerServiceState.SYNC_OFFSET_STEP_MS)
              },
              enabled = syncOffsetMs < SpeakerServiceState.MAX_SYNC_OFFSET_MS,
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("sync_offset_plus_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.sync_offset_plus_desc),
              )
            }
          }
        }
      }

      val context = LocalContext.current
      val trackPreview =
        remember(qualityProfile, lowLatencyMode, ultraLowLatencyMode, bufferProfile) {
          PcmAudioTrackConfigurator.previewBufferSizing(
            context = context,
            profile = qualityProfile,
            lowLatency = lowLatencyMode,
            bufferProfile = bufferProfile,
            ultraLowLatency = ultraLowLatencyMode,
          )
        }
      val effectivePreviewMs =
        PcmSyncDelayLine.computeEffectiveLatencyMs(trackPreview.bufferDurationMs, syncOffsetMs)
      Surface(
        modifier = Modifier.fillMaxWidth().testTag("audiotrack_spec_banner"),
        shape = RoundedCornerShape(10.dp),
        color = acousticColors.surfaceWell,
        border =
          BorderStroke(
            1.dp,
            if (ultraLowLatencyMode) acousticColors.vuSpectrumCyan.copy(alpha = 0.45f)
            else acousticColors.surfaceBorder,
          ),
      ) {
        Text(
          text =
            stringResource(
              if (ultraLowLatencyMode) R.string.audiotrack_spec_ready_ultra
              else R.string.audiotrack_spec_ready,
              qualityProfile.sampleRateHz / 1000f,
              qualityProfile.bitDepth,
              (trackPreview.bufferSizeBytes / 1024).coerceAtLeast(1),
              trackPreview.bufferFrames,
              effectivePreviewMs,
            ),
          style = MonoLabelStyle,
          color =
            when {
              ultraLowLatencyMode -> acousticColors.vuSpectrumCyan
              qualityProfile.isHiRes -> acousticColors.vuPeakAmber
              else -> acousticColors.vuSpectrumCyan
            },
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemAndProtocolFooterCard(
  permissionStatus: PermissionStatus,
  speakerMode: SpeakerMode,
  localAdapterName: String,
  connectedDevice: ConnectedDeviceInfo?,
  lastConnectedDevice: ConnectedDeviceInfo?,
  audioPipelineDescription: String,
  requirePairing: Boolean,
  autoReconnectEnabled: Boolean,
  lockScreenPlaybackEnabled: Boolean = true,
  onRequirePairingChange: (Boolean) -> Unit,
  onAutoReconnectChange: (Boolean) -> Unit,
  onLockScreenPlaybackChange: (Boolean) -> Unit = {},
  onGrantPermissions: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val acousticColors = LocalAcousticColors.current
  val allGranted = permissionStatus.allGranted

  Card(
    modifier = modifier.fillMaxWidth().testTag("permissions_card"),
    shape = RoundedCornerShape(20.dp),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (allGranted) MaterialTheme.colorScheme.surfaceContainer
          else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
      ),
    border =
      BorderStroke(
        width = 1.dp,
        color =
          if (allGranted) acousticColors.surfaceBorder
          else MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
      ),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector =
            if (allGranted) Icons.Rounded.CheckCircle else Icons.Outlined.Security,
          contentDescription = null,
          tint =
            if (allGranted) acousticColors.connectedBeacon
            else MaterialTheme.colorScheme.secondary,
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.permissions_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (!allGranted) {
            Text(
              text = stringResource(R.string.permissions_desc),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PermissionPill(
          label = stringResource(R.string.perm_bt_connect),
          granted = permissionStatus.bluetoothConnectGranted,
        )
        PermissionPill(
          label = stringResource(R.string.perm_bt_advertise),
          granted = permissionStatus.bluetoothAdvertiseGranted,
        )
        PermissionPill(
          label = stringResource(R.string.perm_post_notifications),
          granted = permissionStatus.postNotificationsGranted,
        )
      }

      if (!allGranted) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = onGrantPermissions,
            modifier =
              Modifier.weight(1f)
                .minimumInteractiveComponentSize()
                .testTag("grant_permissions_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(text = stringResource(R.string.action_grant_permissions))
          }
          OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("open_settings_button"),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text(text = stringResource(R.string.action_open_settings))
          }
        }
      }

      HorizontalDivider(color = acousticColors.surfaceBorder)

      // Auto-Reconnect Last Device Switch
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onAutoReconnectChange(!autoReconnectEnabled) }
            .minimumInteractiveComponentSize()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Sync,
          contentDescription = null,
          tint =
            if (autoReconnectEnabled) acousticColors.connectedBeacon
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.auto_reconnect_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = stringResource(R.string.auto_reconnect_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = autoReconnectEnabled,
          onCheckedChange = onAutoReconnectChange,
          modifier = Modifier.testTag("auto_reconnect_switch"),
        )
      }

      // Work on Lock Screen Switch
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onLockScreenPlaybackChange(!lockScreenPlaybackEnabled) }
            .minimumInteractiveComponentSize()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.ScreenLockPortrait,
          contentDescription = null,
          tint =
            if (lockScreenPlaybackEnabled) acousticColors.connectedBeacon
            else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.lock_screen_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = stringResource(R.string.lock_screen_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = lockScreenPlaybackEnabled,
          onCheckedChange = onLockScreenPlaybackChange,
          modifier = Modifier.testTag("lock_screen_switch"),
        )
      }

      if (speakerMode == SpeakerMode.RFCOMM_SPP) {
        // Pairing Mode Switch (applies to RFCOMM SPP sockets)
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { onRequirePairingChange(!requirePairing) }
              .minimumInteractiveComponentSize()
              .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.require_pairing_title),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = stringResource(R.string.require_pairing_desc),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Switch(
            checked = requirePairing,
            onCheckedChange = onRequirePairingChange,
            modifier = Modifier.testTag("require_pairing_switch"),
          )
        }
      }

      HorizontalDivider(color = acousticColors.surfaceBorder)

      DetailRow(
        label = stringResource(R.string.label_spp_uuid),
        value =
          if (speakerMode == SpeakerMode.A2DP_SINK) "A2DP Sink (0x110B) • CoD 0x240414"
          else BluetoothSpeakerService.SPP_UUID.toString().uppercase(),
      )
      DetailRow(
        label = stringResource(R.string.label_privilege_level),
        value =
          if (speakerMode.requiresRoot) stringResource(R.string.privilege_root_required)
          else stringResource(R.string.privilege_standard),
      )
      DetailRow(
        label = stringResource(R.string.label_audio_pipeline),
        value = audioPipelineDescription,
      )
      DetailRow(
        label = stringResource(R.string.label_local_device),
        value = localAdapterName,
      )
      DetailRow(
        label = stringResource(R.string.label_remote_source),
        value =
          when {
            connectedDevice != null -> "${connectedDevice.name} (${connectedDevice.address})"
            lastConnectedDevice != null -> "Last: ${lastConnectedDevice.name}"
            else -> stringResource(R.string.connected_device_none)
          },
      )

      Surface(
        modifier = Modifier.fillMaxWidth().testTag("a2dp_limitation_note"),
        shape = RoundedCornerShape(10.dp),
        color = acousticColors.surfaceWell,
        border = BorderStroke(1.dp, acousticColors.surfaceBorder),
      ) {
        Text(
          text = stringResource(R.string.a2dp_root_note),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(10.dp),
        )
      }
    }
  }
}

@Composable
private fun PermissionPill(label: String, granted: Boolean) {
  val acousticColors = LocalAcousticColors.current
  val accentColor: Color =
    if (granted) acousticColors.connectedBeacon else MaterialTheme.colorScheme.secondary
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = accentColor.copy(alpha = 0.12f),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector =
          if (granted) Icons.Rounded.CheckCircle else Icons.Outlined.NotificationsActive,
        contentDescription = null,
        tint = accentColor,
        modifier = Modifier.size(13.dp),
      )
      Text(text = label, style = MonoLabelStyle, color = MaterialTheme.colorScheme.onSurface)
    }
  }
}

@Composable
fun BluetoothHardwareBanner(
  bluetoothStatus: BluetoothHardwareStatus,
  onEnableBluetooth: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (bluetoothStatus.isSupported && bluetoothStatus.isEnabled) return

  Card(
    modifier = modifier.fillMaxWidth().testTag("bluetooth_banner"),
    shape = RoundedCornerShape(16.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
      ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.BluetoothDisabled,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text =
            if (!bluetoothStatus.isSupported) stringResource(R.string.bt_unavailable_title)
            else stringResource(R.string.bt_disabled_title),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text =
            if (!bluetoothStatus.isSupported) stringResource(R.string.bt_unavailable_desc)
            else stringResource(R.string.bt_disabled_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (bluetoothStatus.isSupported && !bluetoothStatus.isEnabled) {
        Button(
          onClick = onEnableBluetooth,
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("enable_bluetooth_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Text(text = stringResource(R.string.action_enable_bluetooth))
        }
      }
    }
  }
}

@Composable
fun ErrorBanner(
  message: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth().testTag("error_banner"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.ErrorOutline,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onErrorContainer,
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.weight(1f),
      )
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = stringResource(R.string.action_dismiss),
          tint = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    }
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(end = 12.dp),
    )
    Text(
      text = value,
      style = MonoLabelStyle,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun SppGuideDialog(
  qualityProfile: AudioQualityProfile,
  lowLatencyMode: Boolean,
  adapterName: String,
  onDismiss: () -> Unit,
) {
  val clipboardManager = LocalClipboardManager.current
  var copied by remember { mutableStateOf(false) }

  val pythonScript =
    """
    import math, socket, struct, time

    # Replace with your phone's Bluetooth MAC address
    PHONE_MAC = "XX:XX:XX:XX:XX:XX"
    RFCOMM_CHANNEL = 1  # Or discover via SDP UUID 00001101-0000-1000-8000-00805F9B34FB
    RATE = ${qualityProfile.sampleRateHz}

    sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
    sock.connect((PHONE_MAC, RFCOMM_CHANNEL))
    phase = 0.0
    step = 2.0 * math.pi * 440.0 / RATE
    while True:
        buf = bytearray()
        for _ in range(512):
            sample = int(math.sin(phase) * 12000)
            buf += struct.pack("<hh", sample, sample)
            phase += step
        sock.sendall(buf)
        time.sleep(512 / RATE)
    """.trimIndent()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        text = stringResource(R.string.guide_title),
        style = MaterialTheme.typography.titleLarge,
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text =
            stringResource(
              R.string.guide_body,
              qualityProfile.displayLabel,
              qualityProfile.frameSizeBytes,
              if (lowLatencyMode) stringResource(R.string.low_latency_badge_fast)
              else stringResource(R.string.low_latency_badge_safe),
              adapterName,
            ),
          style = MaterialTheme.typography.bodyMedium,
        )
        HorizontalDivider()
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = stringResource(R.string.about_section_title).uppercase(),
            style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = stringResource(R.string.about_developer_credit),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }
    },
    dismissButton = {
      TextButton(
        onClick = {
          clipboardManager.setText(AnnotatedString(pythonScript))
          copied = true
        },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_python_sender_button"),
      ) {
        Text(
          text =
            if (copied) stringResource(R.string.guide_copied_python)
            else stringResource(R.string.guide_copy_python)
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
        Text(text = stringResource(R.string.guide_close))
      }
    },
  )
}
