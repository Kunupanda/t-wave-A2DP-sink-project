package com.example.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.model.AudioTelemetry
import com.example.model.ConnectionStatus
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class ScopePalette(
  val grid: Color,
  val cyan: Color,
  val green: Color,
  val amber: Color,
  val idle: Color,
)

private fun resolveBandAmplitude(
  index: Int,
  status: ConnectionStatus,
  isMuted: Boolean,
  bands: List<Float>,
  breathPhase: Float,
  travelPhase: Float,
): Float =
  when {
    status == ConnectionStatus.CONNECTED && !isMuted -> bands.getOrElse(index) { 0f }
    status == ConnectionStatus.LISTENING ->
      0.10f + 0.09f * sin(breathPhase + travelPhase * 0.5f + index * 0.35f).coerceAtLeast(0f)
    status == ConnectionStatus.RECONNECTING ->
      0.14f + 0.13f * sin(breathPhase * 1.4f + index * 0.42f).coerceAtLeast(0f)
    else -> 0.04f
  }

internal fun DrawScope.drawDualSpectrumScope(
  status: ConnectionStatus,
  isMuted: Boolean,
  bands: List<Float>,
  peakHoldBands: List<Float>,
  breathPhase: Float,
  palette: ScopePalette,
) {
  val w = size.width
  val h = size.height
  val centerY = h * 0.5f
  val barCount = AudioTelemetry.BAND_COUNT
  val gap = 4.dp.toPx()
  val barWidth = ((w - gap * (barCount - 1)) / barCount).coerceAtLeast(2f)

  drawLine(
    color = palette.grid.copy(alpha = 0.8f),
    start = Offset(0f, centerY),
    end = Offset(w, centerY),
    strokeWidth = 1.dp.toPx(),
  )
  listOf(0.22f, 0.78f).forEach { frac ->
    drawLine(
      color = palette.grid,
      start = Offset(0f, h * frac),
      end = Offset(w, h * frac),
      strokeWidth = 0.8.dp.toPx(),
    )
  }

  for (i in 0 until barCount) {
    val x = i * (barWidth + gap)
    val amplitude = resolveBandAmplitude(i, status, isMuted, bands, breathPhase, 0f)
    val halfBarHeight = ((h * 0.48f) * amplitude).coerceAtLeast(3.dp.toPx())
    val topY = centerY - halfBarHeight
    val totalHeight = halfBarHeight * 2f

    val barBrush =
      when (status) {
        ConnectionStatus.CONNECTED ->
          Brush.verticalGradient(
            colors =
              listOf(palette.amber, palette.green, palette.cyan, palette.green, palette.amber),
            startY = 0f,
            endY = h,
          )
        ConnectionStatus.LISTENING ->
          Brush.verticalGradient(
            colors =
              listOf(
                palette.cyan.copy(alpha = 0.25f),
                palette.cyan.copy(alpha = 0.75f),
                palette.cyan.copy(alpha = 0.25f),
              ),
            startY = topY,
            endY = topY + totalHeight,
          )
        ConnectionStatus.RECONNECTING ->
          Brush.verticalGradient(
            colors =
              listOf(
                palette.amber.copy(alpha = 0.3f),
                palette.amber.copy(alpha = 0.85f),
                palette.amber.copy(alpha = 0.3f),
              ),
            startY = topY,
            endY = topY + totalHeight,
          )
        ConnectionStatus.IDLE -> Brush.verticalGradient(colors = listOf(palette.idle, palette.idle))
      }

    drawRoundRect(
      brush = barBrush,
      topLeft = Offset(x, topY),
      size = Size(barWidth, totalHeight),
      cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
    )

    val peakValue = peakHoldBands.getOrElse(i) { 0f }
    if (status == ConnectionStatus.CONNECTED && peakValue > 0.05f) {
      val capH = 2.5.dp.toPx()
      val peakHalf = (h * 0.48f) * peakValue
      val topCapY = (centerY - peakHalf - capH).coerceAtLeast(0f)
      val botCapY = (centerY + peakHalf).coerceAtMost(h - capH)
      drawRoundRect(
        color = palette.amber,
        topLeft = Offset(x, topCapY),
        size = Size(barWidth, capH),
        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
      )
      drawRoundRect(
        color = palette.amber,
        topLeft = Offset(x, botCapY),
        size = Size(barWidth, capH),
        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
      )
    }
  }
}

internal fun DrawScope.drawNeonWaveScope(
  status: ConnectionStatus,
  isMuted: Boolean,
  bands: List<Float>,
  leftLevel: Float,
  rightLevel: Float,
  wavePhase: Float,
  breathPhase: Float,
  palette: ScopePalette,
) {
  val w = size.width
  val h = size.height
  val centerY = h * 0.5f

  // Oscilloscope reticle grid
  drawLine(
    color = palette.grid.copy(alpha = 0.75f),
    start = Offset(0f, centerY),
    end = Offset(w, centerY),
    strokeWidth = 1.dp.toPx(),
  )
  listOf(0.25f, 0.5f, 0.75f).forEach { xFrac ->
    drawLine(
      color = palette.grid.copy(alpha = 0.45f),
      start = Offset(w * xFrac, 0f),
      end = Offset(w * xFrac, h),
      strokeWidth = 0.75.dp.toPx(),
    )
  }

  val sampleCount = 56
  val primaryColor =
    when (status) {
      ConnectionStatus.CONNECTED -> palette.cyan
      ConnectionStatus.LISTENING -> palette.cyan.copy(alpha = 0.8f)
      ConnectionStatus.RECONNECTING -> palette.amber.copy(alpha = 0.85f)
      ConnectionStatus.IDLE -> palette.idle
    }
  val secondaryColor =
    when (status) {
      ConnectionStatus.CONNECTED -> palette.green
      ConnectionStatus.LISTENING -> palette.green.copy(alpha = 0.55f)
      ConnectionStatus.RECONNECTING -> palette.amber.copy(alpha = 0.55f)
      ConnectionStatus.IDLE -> palette.idle.copy(alpha = 0.5f)
    }

  val stereoEnergy =
    when {
      status == ConnectionStatus.CONNECTED && !isMuted -> ((leftLevel + rightLevel) * 0.5f).coerceIn(0.08f, 1f)
      status == ConnectionStatus.LISTENING -> 0.22f
      status == ConnectionStatus.RECONNECTING -> 0.28f
      else -> 0.04f
    }

  fun buildHarmonicPath(freqMult: Float, phaseShift: Float, ampScale: Float): Path {
    val path = Path()
    var prevX = 0f
    var prevY = centerY
    for (s in 0..sampleCount) {
      val t = s / sampleCount.toFloat()
      val x = t * w
      val bandFloat = t * (AudioTelemetry.BAND_COUNT - 1)
      val idxLow = bandFloat.toInt().coerceIn(0, AudioTelemetry.BAND_COUNT - 1)
      val idxHigh = (idxLow + 1).coerceAtMost(AudioTelemetry.BAND_COUNT - 1)
      val frac = bandFloat - idxLow
      val a0 = resolveBandAmplitude(idxLow, status, isMuted, bands, breathPhase, wavePhase)
      val a1 = resolveBandAmplitude(idxHigh, status, isMuted, bands, breathPhase, wavePhase)
      val interpolated = a0 + (a1 - a0) * frac
      val envelope = sin(t * PI).toFloat().coerceAtLeast(0.18f)
      val carrier = sin(t * 2f * PI.toFloat() * freqMult + wavePhase + phaseShift)
      val y = centerY - carrier * (0.28f + interpolated * 0.72f) * stereoEnergy * h * 0.45f * ampScale * envelope
      if (s == 0) {
        path.moveTo(x, y)
      } else {
        val midX = (prevX + x) * 0.5f
        path.cubicTo(midX, prevY, midX, y, x, y)
      }
      prevX = x
      prevY = y
    }
    return path
  }

  val primaryPath = buildHarmonicPath(freqMult = 3.2f, phaseShift = 0f, ampScale = 1f)
  val fillPath =
    Path().apply {
      addPath(primaryPath)
      lineTo(w, h)
      lineTo(0f, h)
      close()
    }
  drawPath(
    path = fillPath,
    brush =
      Brush.verticalGradient(
        colors = listOf(primaryColor.copy(alpha = 0.28f), Color.Transparent),
        startY = h * 0.1f,
        endY = h,
      ),
  )

  val secondaryPath = buildHarmonicPath(freqMult = 5.0f, phaseShift = 1.35f, ampScale = 0.68f)
  drawPath(
    path = secondaryPath,
    color = secondaryColor,
    style = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )

  // Soft outer glow + crisp main trace
  drawPath(
    path = primaryPath,
    color = primaryColor.copy(alpha = 0.30f),
    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
  drawPath(
    path = primaryPath,
    brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor, palette.amber.copy(alpha = 0.9f), primaryColor)),
    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )

  if (status == ConnectionStatus.CONNECTED && stereoEnergy > 0.35f) {
    val peakPath = buildHarmonicPath(freqMult = 7.0f, phaseShift = 2.4f, ampScale = 0.42f)
    drawPath(
      path = peakPath,
      color = palette.amber.copy(alpha = (stereoEnergy * 0.85f).coerceIn(0f, 1f)),
      style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
    )
  }
}

internal fun DrawScope.drawRadialPulseScope(
  status: ConnectionStatus,
  isMuted: Boolean,
  bands: List<Float>,
  peakHoldBands: List<Float>,
  leftLevel: Float,
  rightLevel: Float,
  pulsePhase: Float,
  wavePhase: Float,
  breathPhase: Float,
  palette: ScopePalette,
) {
  val w = size.width
  val h = size.height
  val center = Offset(w * 0.5f, h * 0.5f)
  val maxRadius = (min(w, h) * 0.46f).coerceAtLeast(12.dp.toPx())
  val coreRadius = (maxRadius * 0.26f)
  val energy =
    when {
      status == ConnectionStatus.CONNECTED && !isMuted -> max(leftLevel, rightLevel).coerceIn(0.08f, 1f)
      status == ConnectionStatus.LISTENING -> 0.25f
      status == ConnectionStatus.RECONNECTING -> 0.35f
      else -> 0.05f
    }

  val ringBaseColor =
    when (status) {
      ConnectionStatus.CONNECTED -> palette.cyan
      ConnectionStatus.LISTENING -> palette.cyan
      ConnectionStatus.RECONNECTING -> palette.amber
      ConnectionStatus.IDLE -> palette.idle
    }

  // Concentric sonar pulse rings
  for (ringIndex in 0 until 3) {
    val progress = (pulsePhase + ringIndex / 3f) % 1f
    val radius = coreRadius + progress * (maxRadius - coreRadius) * (0.65f + 0.35f * energy)
    val alpha = ((1f - progress) * (0.18f + 0.5f * energy)).coerceIn(0f, 0.7f)
    drawCircle(
      color = ringBaseColor.copy(alpha = alpha),
      radius = radius,
      center = center,
      style = Stroke(width = 1.4.dp.toPx()),
    )
  }

  // Radial spectrum rays (28 bands arranged 360°)
  val rayCount = AudioTelemetry.BAND_COUNT
  val rotationOffset = wavePhase * 0.18f
  for (i in 0 until rayCount) {
    val angle = rotationOffset + (i.toFloat() / rayCount) * (2f * PI.toFloat())
    val amplitude = resolveBandAmplitude(i, status, isMuted, bands, breathPhase, wavePhase)
    val startR = coreRadius + 3.dp.toPx()
    val rayLen = ((maxRadius - startR) * amplitude).coerceAtLeast(3.dp.toPx())
    val cosA = cos(angle)
    val sinA = sin(angle)
    val start = Offset(center.x + cosA * startR, center.y + sinA * startR)
    val end = Offset(center.x + cosA * (startR + rayLen), center.y + sinA * (startR + rayLen))
    val rayColor =
      when {
        status == ConnectionStatus.IDLE -> palette.idle
        status == ConnectionStatus.RECONNECTING -> palette.amber.copy(alpha = 0.8f)
        amplitude > 0.72f -> palette.amber
        amplitude > 0.40f -> palette.green
        else -> palette.cyan
      }
    drawLine(
      color = rayColor,
      start = start,
      end = end,
      strokeWidth = 2.4.dp.toPx(),
      cap = StrokeCap.Round,
    )

    val peakVal = peakHoldBands.getOrElse(i) { 0f }
    if (status == ConnectionStatus.CONNECTED && peakVal > 0.08f) {
      val peakR = startR + (maxRadius - startR) * peakVal + 2.5.dp.toPx()
      drawCircle(
        color = palette.amber,
        radius = 1.6.dp.toPx(),
        center = Offset(center.x + cosA * peakR, center.y + sinA * peakR),
      )
    }
  }

  // Central acoustic glow core
  drawCircle(
    brush =
      Brush.radialGradient(
        colors = listOf(ringBaseColor.copy(alpha = 0.45f + 0.4f * energy), Color.Transparent),
        center = center,
        radius = coreRadius * 1.35f,
      ),
    radius = coreRadius * 1.35f,
    center = center,
  )
  drawCircle(
    color = ringBaseColor.copy(alpha = 0.85f),
    radius = coreRadius * (0.52f + 0.24f * energy),
    center = center,
    style = Stroke(width = 2.dp.toPx()),
  )
}

internal fun DrawScope.drawAuroraRibbonsScope(
  status: ConnectionStatus,
  isMuted: Boolean,
  bands: List<Float>,
  leftLevel: Float,
  rightLevel: Float,
  wavePhase: Float,
  pulsePhase: Float,
  breathPhase: Float,
  palette: ScopePalette,
) {
  val w = size.width
  val h = size.height
  val centerY = h * 0.5f

  drawLine(
    color = palette.grid.copy(alpha = 0.7f),
    start = Offset(0f, centerY),
    end = Offset(w, centerY),
    strokeWidth = 0.9.dp.toPx(),
  )

  val bandCount = AudioTelemetry.BAND_COUNT
  val leftBoost =
    if (status == ConnectionStatus.CONNECTED && !isMuted) (0.4f + leftLevel * 0.6f) else 1f
  val rightBoost =
    if (status == ConnectionStatus.CONNECTED && !isMuted) (0.4f + rightLevel * 0.6f) else 1f

  fun buildRibbonPath(direction: Float, boost: Float, phaseShift: Float): Pair<Path, Path> {
    val ridgePath = Path()
    val areaPath = Path()
    areaPath.moveTo(0f, centerY)
    var prevX = 0f
    var prevY = centerY
    for (i in 0 until bandCount) {
      val t = i / (bandCount - 1).toFloat()
      val x = t * w
      val amp = resolveBandAmplitude(i, status, isMuted, bands, breathPhase, wavePhase)
      val shimmer = 0.85f + 0.15f * sin(wavePhase + phaseShift + i * 0.45f)
      val offset = (amp * boost * shimmer * h * 0.46f).coerceAtLeast(3.dp.toPx())
      val y = centerY + direction * offset
      if (i == 0) {
        ridgePath.moveTo(x, y)
        areaPath.lineTo(x, y)
      } else {
        val midX = (prevX + x) * 0.5f
        ridgePath.cubicTo(midX, prevY, midX, y, x, y)
        areaPath.cubicTo(midX, prevY, midX, y, x, y)
      }
      prevX = x
      prevY = y
    }
    areaPath.lineTo(w, centerY)
    areaPath.close()
    return ridgePath to areaPath
  }

  val (topRidge, topArea) = buildRibbonPath(direction = -1f, boost = leftBoost, phaseShift = 0f)
  val (botRidge, botArea) = buildRibbonPath(direction = 1f, boost = rightBoost, phaseShift = 1.7f)

  val topColors =
    when (status) {
      ConnectionStatus.CONNECTED ->
        listOf(palette.green.copy(alpha = 0.55f), palette.cyan.copy(alpha = 0.12f))
      ConnectionStatus.LISTENING ->
        listOf(palette.cyan.copy(alpha = 0.40f), palette.cyan.copy(alpha = 0.06f))
      ConnectionStatus.RECONNECTING ->
        listOf(palette.amber.copy(alpha = 0.45f), palette.amber.copy(alpha = 0.08f))
      ConnectionStatus.IDLE -> listOf(palette.idle, Color.Transparent)
    }

  val botColors =
    when (status) {
      ConnectionStatus.CONNECTED ->
        listOf(palette.cyan.copy(alpha = 0.12f), palette.amber.copy(alpha = 0.48f))
      ConnectionStatus.LISTENING ->
        listOf(palette.cyan.copy(alpha = 0.06f), palette.green.copy(alpha = 0.34f))
      ConnectionStatus.RECONNECTING ->
        listOf(palette.amber.copy(alpha = 0.08f), palette.amber.copy(alpha = 0.42f))
      ConnectionStatus.IDLE -> listOf(Color.Transparent, palette.idle)
    }

  drawPath(
    path = topArea,
    brush = Brush.verticalGradient(colors = topColors, startY = 0f, endY = centerY),
  )
  drawPath(
    path = botArea,
    brush = Brush.verticalGradient(colors = botColors, startY = centerY, endY = h),
  )

  drawPath(
    path = topRidge,
    color = if (status == ConnectionStatus.IDLE) palette.idle else palette.cyan,
    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
  drawPath(
    path = botRidge,
    color = if (status == ConnectionStatus.IDLE) palette.idle else palette.green,
    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )

  // Floating acoustic crest particles
  if (status != ConnectionStatus.IDLE) {
    for (p in 0 until 8) {
      val bandIndex = (p * 3 + 2).coerceAtMost(bandCount - 1)
      val amp = resolveBandAmplitude(bandIndex, status, isMuted, bands, breathPhase, wavePhase)
      if (amp > 0.12f) {
        val drift = (pulsePhase + p * 0.13f) % 1f
        val x = (bandIndex / (bandCount - 1).toFloat()) * w
        val dir = if (p % 2 == 0) -1f else 1f
        val baseOffset = amp * h * 0.32f
        val y = (centerY + dir * (baseOffset + drift * h * 0.14f)).coerceIn(4.dp.toPx(), h - 4.dp.toPx())
        drawCircle(
          color = (if (amp > 0.55f) palette.amber else palette.cyan).copy(alpha = (1f - drift) * 0.85f),
          radius = (1.6f + amp * 1.8f).dp.toPx(),
          center = Offset(x, y),
        )
      }
    }
  }
}

internal fun DrawScope.drawSpectrumQuadsNarrowScope(
  status: ConnectionStatus,
  isMuted: Boolean,
  bands: List<Float>,
  peakHoldBands: List<Float>,
  leftLevel: Float,
  rightLevel: Float,
  wavePhase: Float,
  breathPhase: Float,
  palette: ScopePalette,
) {
  val w = size.width
  val h = size.height
  val centerX = w * 0.5f
  val centerY = h * 0.5f

  // 4-Quadrant crosshair reticle
  drawLine(
    color = palette.grid.copy(alpha = 0.85f),
    start = Offset(0f, centerY),
    end = Offset(w, centerY),
    strokeWidth = 1.dp.toPx(),
  )
  drawLine(
    color = palette.grid.copy(alpha = 0.85f),
    start = Offset(centerX, 0f),
    end = Offset(centerX, h),
    strokeWidth = 1.dp.toPx(),
  )

  val barsPerQuad = AudioTelemetry.BAND_COUNT
  val centerSpineGap = 6.dp.toPx()
  val quadWidth = ((w - centerSpineGap) * 0.5f).coerceAtLeast(16f)
  val narrowGap = 1.5.dp.toPx()
  val narrowBarWidth = ((quadWidth - narrowGap * (barsPerQuad - 1)) / barsPerQuad).coerceAtLeast(1.2f)
  val centerBandGap = 2.dp.toPx()

  val leftBalance =
    if (status == ConnectionStatus.CONNECTED && !isMuted) (0.72f + 0.28f * leftLevel) else 1f
  val rightBalance =
    if (status == ConnectionStatus.CONNECTED && !isMuted) (0.72f + 0.28f * rightLevel) else 1f

  for (i in 0 until barsPerQuad) {
    val baseAmp = resolveBandAmplitude(i, status, isMuted, bands, breathPhase, wavePhase)
    val peakAmp = peakHoldBands.getOrElse(i) { 0f }
    val stepOffset = i * (narrowBarWidth + narrowGap)

    // Left Quadrants radiate outward from centerX - centerSpineGap/2 -> 0 (low freqs near core)
    val xLeftQuad = centerX - (centerSpineGap * 0.5f) - stepOffset - narrowBarWidth
    // Right Quadrants radiate outward from centerX + centerSpineGap/2 -> w
    val xRightQuad = centerX + (centerSpineGap * 0.5f) + stepOffset

    val ampLeft = (baseAmp * leftBalance).coerceIn(0f, 1f)
    val ampRight = (baseAmp * rightBalance).coerceIn(0f, 1f)

    val topHeightL = ((centerY - centerBandGap) * ampLeft).coerceAtLeast(2.dp.toPx())
    val topHeightR = ((centerY - centerBandGap) * ampRight).coerceAtLeast(2.dp.toPx())
    val botHeightL = ((centerY - centerBandGap) * (ampLeft * 0.88f)).coerceAtLeast(2.dp.toPx())
    val botHeightR = ((centerY - centerBandGap) * (ampRight * 0.88f)).coerceAtLeast(2.dp.toPx())

    val quadColorTop =
      when {
        status == ConnectionStatus.IDLE -> palette.idle
        status == ConnectionStatus.RECONNECTING -> palette.amber.copy(alpha = 0.8f)
        baseAmp > 0.72f -> palette.amber
        baseAmp > 0.38f -> palette.green
        else -> palette.cyan
      }
    val quadColorBot =
      when {
        status == ConnectionStatus.IDLE -> palette.idle
        status == ConnectionStatus.RECONNECTING -> palette.amber.copy(alpha = 0.65f)
        baseAmp > 0.65f -> palette.green
        else -> palette.cyan.copy(alpha = 0.82f)
      }

    // Q1 Top-Left
    drawRoundRect(
      color = quadColorTop,
      topLeft = Offset(xLeftQuad, centerY - centerBandGap - topHeightL),
      size = Size(narrowBarWidth, topHeightL),
      cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
    )
    // Q2 Top-Right
    drawRoundRect(
      color = quadColorTop,
      topLeft = Offset(xRightQuad, centerY - centerBandGap - topHeightR),
      size = Size(narrowBarWidth, topHeightR),
      cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
    )
    // Q3 Bottom-Left
    drawRoundRect(
      color = quadColorBot,
      topLeft = Offset(xLeftQuad, centerY + centerBandGap),
      size = Size(narrowBarWidth, botHeightL),
      cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
    )
    // Q4 Bottom-Right
    drawRoundRect(
      color = quadColorBot,
      topLeft = Offset(xRightQuad, centerY + centerBandGap),
      size = Size(narrowBarWidth, botHeightR),
      cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
    )

    if (status == ConnectionStatus.CONNECTED && peakAmp > 0.08f) {
      val tipH = 1.8.dp.toPx()
      val peakOffsetTop = (centerY - centerBandGap) * peakAmp
      drawRect(
        color = palette.amber,
        topLeft = Offset(xLeftQuad, (centerY - centerBandGap - peakOffsetTop - tipH).coerceAtLeast(0f)),
        size = Size(narrowBarWidth, tipH),
      )
      drawRect(
        color = palette.amber,
        topLeft = Offset(xRightQuad, (centerY - centerBandGap - peakOffsetTop - tipH).coerceAtLeast(0f)),
        size = Size(narrowBarWidth, tipH),
      )
    }
  }
}

internal fun DrawScope.drawStereoWaveScope(
  status: ConnectionStatus,
  isMuted: Boolean,
  bands: List<Float>,
  leftLevel: Float,
  rightLevel: Float,
  wavePhase: Float,
  breathPhase: Float,
  palette: ScopePalette,
) {
  val w = size.width
  val h = size.height
  val leftBaseline = h * 0.34f
  val rightBaseline = h * 0.66f

  listOf(leftBaseline, rightBaseline).forEach { lineY ->
    drawLine(
      color = palette.grid.copy(alpha = 0.65f),
      start = Offset(0f, lineY),
      end = Offset(w, lineY),
      strokeWidth = 0.8.dp.toPx(),
    )
  }

  val steps = 64
  val leftEnergy =
    when {
      status == ConnectionStatus.CONNECTED && !isMuted -> leftLevel.coerceIn(0.10f, 1f)
      status == ConnectionStatus.LISTENING -> 0.24f
      status == ConnectionStatus.RECONNECTING -> 0.30f
      else -> 0.05f
    }
  val rightEnergy =
    when {
      status == ConnectionStatus.CONNECTED && !isMuted -> rightLevel.coerceIn(0.10f, 1f)
      status == ConnectionStatus.LISTENING -> 0.20f
      status == ConnectionStatus.RECONNECTING -> 0.28f
      else -> 0.05f
    }

  fun buildChannelWave(baselineY: Float, energy: Float, polarity: Float, phaseLag: Float): Path {
    val path = Path()
    var prevX = 0f
    var prevY = baselineY
    for (s in 0..steps) {
      val t = s / steps.toFloat()
      val x = t * w
      val bandIndex = (t * (AudioTelemetry.BAND_COUNT - 1)).toInt().coerceIn(0, AudioTelemetry.BAND_COUNT - 1)
      val bandAmp = resolveBandAmplitude(bandIndex, status, isMuted, bands, breathPhase, wavePhase)
      val env = sin(t * PI).toFloat().coerceAtLeast(0.15f)
      val carrier =
        sin(t * 2f * PI.toFloat() * 4.5f + wavePhase * polarity + phaseLag) * 0.68f +
          cos(t * 2f * PI.toFloat() * 9.0f - wavePhase) * 0.32f
      val y = baselineY + carrier * (0.25f + bandAmp * 0.75f) * energy * h * 0.24f * env
      if (s == 0) {
        path.moveTo(x, y)
      } else {
        val midX = (prevX + x) * 0.5f
        path.cubicTo(midX, prevY, midX, y, x, y)
      }
      prevX = x
      prevY = y
    }
    return path
  }

  val leftPath = buildChannelWave(leftBaseline, leftEnergy, polarity = 1f, phaseLag = 0f)
  val rightPath = buildChannelWave(rightBaseline, rightEnergy, polarity = -1f, phaseLag = 1.2f)

  val leftColor =
    when (status) {
      ConnectionStatus.IDLE -> palette.idle
      ConnectionStatus.RECONNECTING -> palette.amber
      else -> palette.cyan
    }
  val rightColor =
    when (status) {
      ConnectionStatus.IDLE -> palette.idle
      ConnectionStatus.RECONNECTING -> palette.amber.copy(alpha = 0.85f)
      else -> palette.green
    }

  drawPath(
    path = leftPath,
    color = leftColor.copy(alpha = 0.26f),
    style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
  drawPath(
    path = leftPath,
    color = leftColor,
    style = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )

  drawPath(
    path = rightPath,
    color = rightColor.copy(alpha = 0.24f),
    style = Stroke(width = 5.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
  drawPath(
    path = rightPath,
    color = rightColor,
    style = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

internal fun DrawScope.drawFloorEqScope(
  status: ConnectionStatus,
  isMuted: Boolean,
  bands: List<Float>,
  peakHoldBands: List<Float>,
  breathPhase: Float,
  palette: ScopePalette,
) {
  val w = size.width
  val h = size.height
  val colCount = AudioTelemetry.BAND_COUNT
  val colGap = 3.dp.toPx()
  val colWidth = ((w - colGap * (colCount - 1)) / colCount).coerceAtLeast(2f)
  val segmentsPerCol = 10
  val segGap = 2.dp.toPx()
  val segHeight = ((h - segGap * (segmentsPerCol - 1)) / segmentsPerCol).coerceAtLeast(2f)

  for (c in 0 until colCount) {
    val x = c * (colWidth + colGap)
    val amp = resolveBandAmplitude(c, status, isMuted, bands, breathPhase, 0f)
    val litCount = (amp * segmentsPerCol).toInt().coerceIn(if (status == ConnectionStatus.IDLE) 0 else 1, segmentsPerCol)
    val peakSeg = ((peakHoldBands.getOrElse(c) { 0f }) * segmentsPerCol).toInt().coerceIn(0, segmentsPerCol - 1)

    for (s in 0 until segmentsPerCol) {
      val y = h - (s + 1) * segHeight - s * segGap
      val isLit = s < litCount
      val isPeakCap = status == ConnectionStatus.CONNECTED && s == peakSeg && peakSeg > 0
      val color =
        when {
          isPeakCap -> palette.amber
          !isLit -> palette.grid.copy(alpha = 0.30f)
          status == ConnectionStatus.IDLE -> palette.idle
          status == ConnectionStatus.RECONNECTING -> palette.amber
          s >= 8 -> palette.amber
          s >= 5 -> palette.green
          else -> palette.cyan
        }
      drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(colWidth, segHeight),
        cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx()),
      )
    }
  }
}

internal fun DrawScope.drawHarmonicDotsScope(
  status: ConnectionStatus,
  isMuted: Boolean,
  bands: List<Float>,
  leftLevel: Float,
  rightLevel: Float,
  wavePhase: Float,
  pulsePhase: Float,
  breathPhase: Float,
  palette: ScopePalette,
) {
  val w = size.width
  val h = size.height
  val centerY = h * 0.5f

  drawLine(
    color = palette.grid.copy(alpha = 0.7f),
    start = Offset(0f, centerY),
    end = Offset(w, centerY),
    strokeWidth = 0.8.dp.toPx(),
  )

  val count = AudioTelemetry.BAND_COUNT
  val points = ArrayList<Offset>(count)
  val spreadEnergy =
    if (status == ConnectionStatus.CONNECTED && !isMuted) ((leftLevel + rightLevel) * 0.5f).coerceIn(0.15f, 1f)
    else 0.4f

  for (i in 0 until count) {
    val t = i / (count - 1).toFloat()
    val x = t * (w - 12.dp.toPx()) + 6.dp.toPx()
    val amp = resolveBandAmplitude(i, status, isMuted, bands, breathPhase, wavePhase)
    val swing = sin(wavePhase + i * 0.55f)
    val y = (centerY + swing * amp * (0.4f + 0.6f * spreadEnergy) * (h * 0.42f))
      .coerceIn(6.dp.toPx(), h - 6.dp.toPx())
    val pt = Offset(x, y)
    points.add(pt)

    if (i > 0) {
      drawLine(
        color = (if (status == ConnectionStatus.IDLE) palette.idle else palette.cyan).copy(alpha = 0.32f),
        start = points[i - 1],
        end = pt,
        strokeWidth = 1.3.dp.toPx(),
      )
    }
  }

  points.forEachIndexed { idx, pt ->
    val amp = resolveBandAmplitude(idx, status, isMuted, bands, breathPhase, wavePhase)
    val dotColor =
      when {
        status == ConnectionStatus.IDLE -> palette.idle
        status == ConnectionStatus.RECONNECTING -> palette.amber
        amp > 0.68f -> palette.amber
        amp > 0.36f -> palette.green
        else -> palette.cyan
      }
    val nodeRadius = (2.2f + amp * 3.2f).dp.toPx()
    if (status != ConnectionStatus.IDLE && amp > 0.35f) {
      val haloScale = 1.6f + 0.6f * sin(pulsePhase * 2f * PI.toFloat() + idx)
      drawCircle(
        color = dotColor.copy(alpha = 0.22f),
        radius = nodeRadius * haloScale,
        center = pt,
      )
    }
    drawCircle(
      color = dotColor,
      radius = nodeRadius,
      center = pt,
    )
  }
}
