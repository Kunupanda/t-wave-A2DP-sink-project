package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.PcmSyncDelayLine
import com.example.model.AudioQualityProfile
import com.example.model.AudioTelemetry
import com.example.model.ConnectionStatus
import com.example.model.ScopeAnimationMode
import com.example.ui.theme.LocalAcousticColors
import com.example.ui.theme.MonoLabelStyle
import com.example.ui.theme.MonoMetricStyle
import java.util.Locale
import kotlin.math.PI

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioVisualizerPanel(
  status: ConnectionStatus,
  telemetry: AudioTelemetry,
  qualityProfile: AudioQualityProfile,
  lowLatencyMode: Boolean,
  isMuted: Boolean,
  ultraLowLatencyMode: Boolean = false,
  syncOffsetMs: Int = 0,
  scopeAnimationMode: ScopeAnimationMode = ScopeAnimationMode.DUAL_SPECTRUM,
  onScopeAnimationModeSelected: (ScopeAnimationMode) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val acousticColors = LocalAcousticColors.current

  val targetLeft = if (status == ConnectionStatus.CONNECTED && !isMuted) telemetry.leftLevel else 0f
  val targetRight = if (status == ConnectionStatus.CONNECTED && !isMuted) telemetry.rightLevel else 0f
  val vuAnimMs =
    when {
      ultraLowLatencyMode -> 25
      lowLatencyMode -> 45
      else -> 70
    }

  val smoothLeft by
    animateFloatAsState(
      targetValue = targetLeft,
      animationSpec = tween(durationMillis = vuAnimMs),
      label = "left_vu",
    )
  val smoothRight by
    animateFloatAsState(
      targetValue = targetRight,
      animationSpec = tween(durationMillis = vuAnimMs),
      label = "right_vu",
    )
  val smoothBuffer by
    animateFloatAsState(
      targetValue = if (status == ConnectionStatus.CONNECTED) telemetry.bufferFillRatio else 0f,
      animationSpec =
        tween(durationMillis = if (ultraLowLatencyMode) 50 else if (lowLatencyMode) 75 else 120),
      label = "buffer_fill",
    )

  var peakHoldBands by remember { mutableStateOf(List(AudioTelemetry.BAND_COUNT) { 0f }) }
  LaunchedEffect(telemetry.spectrumBands, status, isMuted) {
    val activeBands =
      if (status == ConnectionStatus.CONNECTED && !isMuted) telemetry.spectrumBands
      else List(AudioTelemetry.BAND_COUNT) { 0f }
    peakHoldBands =
      peakHoldBands.mapIndexed { index, previousPeak ->
        val incoming = activeBands.getOrElse(index) { 0f }
        if (incoming >= previousPeak) incoming else (previousPeak - 0.04f).coerceAtLeast(0f)
      }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "scope_animations")
  val standbyPhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "standby_phase",
    )
  val wavePhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = (2f * PI).toFloat(),
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis = if (ultraLowLatencyMode) 1100 else if (lowLatencyMode) 1400 else 1800,
              easing = LinearEasing,
            ),
          repeatMode = RepeatMode.Restart,
        ),
      label = "wave_phase",
    )
  val pulsePhase by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "pulse_phase",
    )

  Surface(
    modifier = modifier.fillMaxWidth().testTag("visualizer_card"),
    shape = RoundedCornerShape(24.dp),
    color = acousticColors.surfaceWell,
    border = BorderStroke(1.dp, acousticColors.surfaceBorder),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      // Scope Header Strip
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.visualizer_title).uppercase(),
          style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Surface(
            modifier = Modifier.testTag("visualizer_format_badge"),
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, acousticColors.surfaceBorder),
          ) {
            Text(
              text = qualityProfile.shortBadge,
              style = MonoLabelStyle,
              color = acousticColors.vuSpectrumCyan,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
          if (qualityProfile.isHiRes) {
            Surface(
              modifier = Modifier.testTag("visualizer_hires_badge"),
              shape = RoundedCornerShape(6.dp),
              color = acousticColors.vuPeakAmber.copy(alpha = 0.16f),
              border = BorderStroke(1.dp, acousticColors.vuPeakAmber.copy(alpha = 0.5f)),
            ) {
              Text(
                text = stringResource(R.string.hires_badge),
                style = MonoLabelStyle,
                color = acousticColors.vuPeakAmber,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
              )
            }
          }
          if (lowLatencyMode || ultraLowLatencyMode) {
            val badgeColor =
              if (ultraLowLatencyMode) acousticColors.vuSpectrumCyan
              else acousticColors.connectedBeacon
            Surface(
              modifier = Modifier.testTag("visualizer_low_latency_badge"),
              shape = RoundedCornerShape(6.dp),
              color = badgeColor.copy(alpha = 0.16f),
              border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f)),
            ) {
              Text(
                text =
                  if (ultraLowLatencyMode) stringResource(R.string.low_latency_badge_ultra)
                  else stringResource(R.string.low_latency_badge_fast),
                style = MonoLabelStyle,
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
              )
            }
          }
          if (syncOffsetMs != 0) {
            Surface(
              modifier = Modifier.testTag("visualizer_sync_offset_badge"),
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.secondaryContainer,
              border =
                BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
            ) {
              Text(
                text = "${PcmSyncDelayLine.formatOffsetLabel(syncOffsetMs)} SYNC",
                style = MonoLabelStyle,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
              )
            }
          }
        }
      }

      // Custom Animation Mode Switcher
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("scope_animation_chips_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ScopeAnimationMode.entries.forEach { mode ->
          val selected = scopeAnimationMode == mode
          val labelText =
            when (mode) {
              ScopeAnimationMode.DUAL_SPECTRUM -> stringResource(R.string.scope_anim_dual_spectrum)
              ScopeAnimationMode.SPECTRUM_QUADS_NARROW ->
                stringResource(R.string.scope_anim_spectrum_quads_narrow)
              ScopeAnimationMode.NEON_WAVE -> stringResource(R.string.scope_anim_neon_wave)
              ScopeAnimationMode.STEREO_WAVE -> stringResource(R.string.scope_anim_stereo_wave)
              ScopeAnimationMode.RADIAL_PULSE -> stringResource(R.string.scope_anim_radial_pulse)
              ScopeAnimationMode.AURORA_RIBBONS -> stringResource(R.string.scope_anim_aurora_ribbons)
              ScopeAnimationMode.FLOOR_EQ -> stringResource(R.string.scope_anim_floor_eq)
              ScopeAnimationMode.HARMONIC_DOTS -> stringResource(R.string.scope_anim_harmonic_dots)
            }
          val icon =
            when (mode) {
              ScopeAnimationMode.DUAL_SPECTRUM -> Icons.Rounded.GraphicEq
              ScopeAnimationMode.SPECTRUM_QUADS_NARROW -> Icons.Rounded.ViewQuilt
              ScopeAnimationMode.NEON_WAVE -> Icons.Rounded.ShowChart
              ScopeAnimationMode.STEREO_WAVE -> Icons.Rounded.Timeline
              ScopeAnimationMode.RADIAL_PULSE -> Icons.Rounded.Radar
              ScopeAnimationMode.AURORA_RIBBONS -> Icons.Rounded.Waves
              ScopeAnimationMode.FLOOR_EQ -> Icons.Rounded.Equalizer
              ScopeAnimationMode.HARMONIC_DOTS -> Icons.Rounded.BlurOn
            }
          FilterChip(
            selected = selected,
            onClick = { onScopeAnimationModeSelected(mode) },
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("scope_anim_${mode.name}"),
            leadingIcon = {
              Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            },
            label = {
              Text(
                text = labelText,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
              )
            },
            colors =
              FilterChipDefaults.filterChipColors(
                selectedContainerColor = acousticColors.vuSpectrumCyan.copy(alpha = 0.16f),
                selectedLabelColor = acousticColors.vuSpectrumCyan,
                selectedLeadingIconColor = acousticColors.vuSpectrumCyan,
              ),
          )
        }
      }

      // Responsive Signal Scope Canvas
      val modeLabel =
        when (scopeAnimationMode) {
          ScopeAnimationMode.DUAL_SPECTRUM -> stringResource(R.string.scope_anim_dual_spectrum)
          ScopeAnimationMode.SPECTRUM_QUADS_NARROW ->
            stringResource(R.string.scope_anim_spectrum_quads_narrow)
          ScopeAnimationMode.NEON_WAVE -> stringResource(R.string.scope_anim_neon_wave)
          ScopeAnimationMode.STEREO_WAVE -> stringResource(R.string.scope_anim_stereo_wave)
          ScopeAnimationMode.RADIAL_PULSE -> stringResource(R.string.scope_anim_radial_pulse)
          ScopeAnimationMode.AURORA_RIBBONS -> stringResource(R.string.scope_anim_aurora_ribbons)
          ScopeAnimationMode.FLOOR_EQ -> stringResource(R.string.scope_anim_floor_eq)
          ScopeAnimationMode.HARMONIC_DOTS -> stringResource(R.string.scope_anim_harmonic_dots)
        }
      val scopeDescription = stringResource(R.string.scope_canvas_desc, modeLabel)
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .border(1.dp, acousticColors.surfaceBorder.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
      ) {
        val palette =
          ScopePalette(
            grid = acousticColors.surfaceBorder.copy(alpha = 0.45f),
            cyan = acousticColors.vuSpectrumCyan,
            green = acousticColors.vuSignalGreen,
            amber = acousticColors.vuPeakAmber,
            idle = acousticColors.idleBeacon.copy(alpha = 0.25f),
          )

        Canvas(
          modifier =
            Modifier.fillMaxWidth()
              .height(124.dp)
              .semantics { contentDescription = scopeDescription }
              .testTag("signal_scope_canvas")
        ) {
          when (scopeAnimationMode) {
            ScopeAnimationMode.DUAL_SPECTRUM ->
              drawDualSpectrumScope(
                status = status,
                isMuted = isMuted,
                bands = telemetry.spectrumBands,
                peakHoldBands = peakHoldBands,
                breathPhase = standbyPhase,
                palette = palette,
              )
            ScopeAnimationMode.SPECTRUM_QUADS_NARROW ->
              drawSpectrumQuadsNarrowScope(
                status = status,
                isMuted = isMuted,
                bands = telemetry.spectrumBands,
                peakHoldBands = peakHoldBands,
                leftLevel = smoothLeft,
                rightLevel = smoothRight,
                wavePhase = wavePhase,
                breathPhase = standbyPhase,
                palette = palette,
              )
            ScopeAnimationMode.NEON_WAVE ->
              drawNeonWaveScope(
                status = status,
                isMuted = isMuted,
                bands = telemetry.spectrumBands,
                leftLevel = smoothLeft,
                rightLevel = smoothRight,
                wavePhase = wavePhase,
                breathPhase = standbyPhase,
                palette = palette,
              )
            ScopeAnimationMode.STEREO_WAVE ->
              drawStereoWaveScope(
                status = status,
                isMuted = isMuted,
                bands = telemetry.spectrumBands,
                leftLevel = smoothLeft,
                rightLevel = smoothRight,
                wavePhase = wavePhase,
                breathPhase = standbyPhase,
                palette = palette,
              )
            ScopeAnimationMode.RADIAL_PULSE ->
              drawRadialPulseScope(
                status = status,
                isMuted = isMuted,
                bands = telemetry.spectrumBands,
                peakHoldBands = peakHoldBands,
                leftLevel = smoothLeft,
                rightLevel = smoothRight,
                pulsePhase = pulsePhase,
                wavePhase = wavePhase,
                breathPhase = standbyPhase,
                palette = palette,
              )
            ScopeAnimationMode.AURORA_RIBBONS ->
              drawAuroraRibbonsScope(
                status = status,
                isMuted = isMuted,
                bands = telemetry.spectrumBands,
                leftLevel = smoothLeft,
                rightLevel = smoothRight,
                wavePhase = wavePhase,
                pulsePhase = pulsePhase,
                breathPhase = standbyPhase,
                palette = palette,
              )
            ScopeAnimationMode.FLOOR_EQ ->
              drawFloorEqScope(
                status = status,
                isMuted = isMuted,
                bands = telemetry.spectrumBands,
                peakHoldBands = peakHoldBands,
                breathPhase = standbyPhase,
                palette = palette,
              )
            ScopeAnimationMode.HARMONIC_DOTS ->
              drawHarmonicDotsScope(
                status = status,
                isMuted = isMuted,
                bands = telemetry.spectrumBands,
                leftLevel = smoothLeft,
                rightLevel = smoothRight,
                wavePhase = wavePhase,
                pulsePhase = pulsePhase,
                breathPhase = standbyPhase,
                palette = palette,
              )
          }
        }
      }

      // Split Side-by-Side Stereo L / R VU Bars
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CompactStereoVuBar(
          channelLabel = stringResource(R.string.label_channel_left),
          level = smoothLeft,
          cyan = acousticColors.vuSpectrumCyan,
          green = acousticColors.vuSignalGreen,
          amber = acousticColors.vuPeakAmber,
          wellColor = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.weight(1f),
        )
        CompactStereoVuBar(
          channelLabel = stringResource(R.string.label_channel_right),
          level = smoothRight,
          cyan = acousticColors.vuSpectrumCyan,
          green = acousticColors.vuSignalGreen,
          amber = acousticColors.vuPeakAmber,
          wellColor = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.weight(1f),
        )
      }

      // Ring Buffer Fill + 3-Metric Strip
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.label_buffer_fill).uppercase(),
            style = MonoLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text =
              if (telemetry.bufferCapacityBytes > 0) {
                "${(smoothBuffer * 100).toInt()}% • ${telemetry.bufferedBytes / 1024}/${telemetry.bufferCapacityBytes / 1024} KB"
              } else {
                "${(smoothBuffer * 100).toInt()}%"
              },
            style = MonoLabelStyle,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        val bufferWell = MaterialTheme.colorScheme.surfaceContainer
        val bufferFillBrush =
          Brush.horizontalGradient(
            colors =
              listOf(
                acousticColors.vuSpectrumCyan,
                acousticColors.vuSignalGreen,
                acousticColors.vuPeakAmber,
              )
          )
        Canvas(
          modifier =
            Modifier.fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(4.dp))
              .testTag("audio_buffer_bar")
        ) {
          drawRoundRect(
            color = bufferWell,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
          )
          if (smoothBuffer > 0.01f) {
            drawRoundRect(
              brush = bufferFillBrush,
              topLeft = Offset.Zero,
              size = Size(size.width * smoothBuffer, size.height),
              cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            )
          }
        }
      }

      // Unified 3-Metric Readout Bar
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, acousticColors.surfaceBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
      ) {
        TelemetryInlineStat(
          label = stringResource(R.string.label_throughput),
          value =
            if (status == ConnectionStatus.CONNECTED) {
              String.format(Locale.US, "%.0f kbps", telemetry.throughputKbps)
            } else {
              "0 kbps"
            },
          modifier = Modifier.weight(1f),
        )
        val basePreviewLatency =
          when {
            ultraLowLatencyMode -> 2
            lowLatencyMode -> 10
            else -> 65
          }
        val previewEffectiveLatency =
          PcmSyncDelayLine.computeEffectiveLatencyMs(basePreviewLatency, syncOffsetMs)
        TelemetryInlineStat(
          label = stringResource(R.string.label_latency),
          value =
            if (status == ConnectionStatus.CONNECTED) {
              "${telemetry.estimatedLatencyMs} ms"
            } else {
              "~${previewEffectiveLatency} ms"
            },
          modifier = Modifier.weight(1f),
        )
        TelemetryInlineStat(
          label = stringResource(R.string.label_received),
          value = formatByteCount(telemetry.totalBytesReceived),
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun CompactStereoVuBar(
  channelLabel: String,
  level: Float,
  cyan: Color,
  green: Color,
  amber: Color,
  wellColor: Color,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = channelLabel,
      style = MonoLabelStyle.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(12.dp),
    )
    Canvas(modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))) {
      val segments = 14
      val gap = 2.5.dp.toPx()
      val segWidth = (size.width - gap * (segments - 1)) / segments
      val litSegments = (level * segments).toInt()
      for (i in 0 until segments) {
        val x = i * (segWidth + gap)
        val segColor =
          when {
            i >= litSegments -> wellColor
            i >= 11 -> amber
            i >= 7 -> green
            else -> cyan
          }
        drawRoundRect(
          color = segColor,
          topLeft = Offset(x, 0f),
          size = Size(segWidth, size.height),
          cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx()),
        )
      }
    }
  }
}

@Composable
private fun TelemetryInlineStat(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
    Text(
      text = value,
      style = MonoMetricStyle,
      color = MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
    )
  }
}

private fun formatByteCount(bytes: Long): String {
  if (bytes < 1024L) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
  val mb = kb / 1024.0
  return String.format(Locale.US, "%.2f MB", mb)
}
