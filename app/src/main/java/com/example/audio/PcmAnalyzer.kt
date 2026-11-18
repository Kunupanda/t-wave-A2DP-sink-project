package com.example.audio

import com.example.model.AudioTelemetry
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Utility for frame-aligning incoming 16-bit, 24-bit packed, or 32-bit Stereo PCM chunks,
 * converting 24/32-bit LE PCM to normalized 32-bit float samples when required by AudioTrack,
 * and computing real-time L/R RMS levels plus multi-band waveform bars for visualization.
 */
object PcmAnalyzer {
  const val DEFAULT_SAMPLE_RATE_HZ = 44_100
  const val MAX_FRAME_SIZE_BYTES = 8 // Stereo 32-bit PCM = 2 * 4 bytes

  private const val MAX_16BIT = 32768f
  private const val MAX_24BIT = 8_388_608f
  private const val MAX_32BIT = 2_147_483_648f

  data class AnalysisResult(
    val leftLevel: Float,
    val rightLevel: Float,
    val bands: List<Float>,
  )

  fun frameSizeForBitDepth(bitDepth: Int, channels: Int = 2): Int =
    channels * (bitDepth / 8).coerceAtLeast(2)

  fun analyzeStereoPcm(
    pcmBytes: ByteArray,
    length: Int,
    bitDepth: Int = 16,
    bandCount: Int = AudioTelemetry.BAND_COUNT,
  ): AnalysisResult {
    val frameSizeBytes = frameSizeForBitDepth(bitDepth)
    val usableBytes = (length / frameSizeBytes) * frameSizeBytes
    if (usableBytes <= 0) {
      return AnalysisResult(0f, 0f, List(bandCount) { 0f })
    }

    val frameCount = usableBytes / frameSizeBytes
    var leftSumSq = 0.0
    var rightSumSq = 0.0

    val bands = FloatArray(bandCount)
    val framesPerBand = (frameCount / bandCount).coerceAtLeast(1)

    var frameIdx = 0
    var byteIdx = 0
    while (byteIdx < usableBytes) {
      val (normL, normR) =
        when (bitDepth) {
          24 -> {
            val left24 =
              (pcmBytes[byteIdx].toInt() and 0xFF) or
                ((pcmBytes[byteIdx + 1].toInt() and 0xFF) shl 8) or
                (pcmBytes[byteIdx + 2].toInt() shl 16)
            val right24 =
              (pcmBytes[byteIdx + 3].toInt() and 0xFF) or
                ((pcmBytes[byteIdx + 4].toInt() and 0xFF) shl 8) or
                (pcmBytes[byteIdx + 5].toInt() shl 16)
            (left24 / MAX_24BIT) to (right24 / MAX_24BIT)
          }
          32 -> {
            val left32 =
              (pcmBytes[byteIdx].toInt() and 0xFF) or
                ((pcmBytes[byteIdx + 1].toInt() and 0xFF) shl 8) or
                ((pcmBytes[byteIdx + 2].toInt() and 0xFF) shl 16) or
                (pcmBytes[byteIdx + 3].toInt() shl 24)
            val right32 =
              (pcmBytes[byteIdx + 4].toInt() and 0xFF) or
                ((pcmBytes[byteIdx + 5].toInt() and 0xFF) shl 8) or
                ((pcmBytes[byteIdx + 6].toInt() and 0xFF) shl 16) or
                (pcmBytes[byteIdx + 7].toInt() shl 24)
            (left32 / MAX_32BIT) to (right32 / MAX_32BIT)
          }
          else -> {
            val left16 =
              ((pcmBytes[byteIdx + 1].toInt() shl 8) or (pcmBytes[byteIdx].toInt() and 0xFF))
                .toShort()
            val right16 =
              ((pcmBytes[byteIdx + 3].toInt() shl 8) or (pcmBytes[byteIdx + 2].toInt() and 0xFF))
                .toShort()
            (left16 / MAX_16BIT) to (right16 / MAX_16BIT)
          }
        }

      leftSumSq += (normL * normL).toDouble()
      rightSumSq += (normR * normR).toDouble()

      val bandIdx = (frameIdx / framesPerBand).coerceAtMost(bandCount - 1)
      val monoPeak = (abs(normL) + abs(normR)) * 0.5f
      if (monoPeak > bands[bandIdx]) {
        bands[bandIdx] = monoPeak
      }

      frameIdx++
      byteIdx += frameSizeBytes
    }

    val leftRms = sqrt(leftSumSq / frameCount).toFloat()
    val rightRms = sqrt(rightSumSq / frameCount).toFloat()

    return AnalysisResult(
      leftLevel = sqrt(leftRms).coerceIn(0f, 1f),
      rightLevel = sqrt(rightRms).coerceIn(0f, 1f),
      bands = bands.map { sqrt(it).coerceIn(0f, 1f) },
    )
  }

  /**
   * Backwards-compatible convenience overload for 16-bit stereo PCM.
   */
  fun analyzeStereo16BitLe(
    pcmBytes: ByteArray,
    length: Int,
    bandCount: Int = AudioTelemetry.BAND_COUNT,
  ): AnalysisResult = analyzeStereoPcm(pcmBytes, length, bitDepth = 16, bandCount = bandCount)

  /**
   * Converts little-endian 16-bit, 24-bit packed, or 32-bit integer PCM bytes into normalized
   * [-1f, 1f] floating-point samples for [android.media.AudioFormat.ENCODING_PCM_FLOAT].
   * Returns the number of float samples written into [floatOutput].
   */
  fun convertLePcmToFloat(
    pcmBytes: ByteArray,
    length: Int,
    bitDepth: Int,
    floatOutput: FloatArray,
  ): Int {
    val bytesPerSample = (bitDepth / 8).coerceAtLeast(2)
    val sampleCount = (length / bytesPerSample).coerceAtMost(floatOutput.size)
    var byteIdx = 0
    for (i in 0 until sampleCount) {
      floatOutput[i] =
        when (bitDepth) {
          24 -> {
            val s =
              (pcmBytes[byteIdx].toInt() and 0xFF) or
                ((pcmBytes[byteIdx + 1].toInt() and 0xFF) shl 8) or
                (pcmBytes[byteIdx + 2].toInt() shl 16)
            s / MAX_24BIT
          }
          32 -> {
            val s =
              (pcmBytes[byteIdx].toInt() and 0xFF) or
                ((pcmBytes[byteIdx + 1].toInt() and 0xFF) shl 8) or
                ((pcmBytes[byteIdx + 2].toInt() and 0xFF) shl 16) or
                (pcmBytes[byteIdx + 3].toInt() shl 24)
            s / MAX_32BIT
          }
          else -> {
            val s =
              ((pcmBytes[byteIdx + 1].toInt() shl 8) or (pcmBytes[byteIdx].toInt() and 0xFF))
                .toShort()
            s / MAX_16BIT
          }
        }
      byteIdx += bytesPerSample
    }
    return sampleCount
  }

  /**
   * Prepends leftover partial-frame bytes from the previous RFCOMM read and splits
   * the resulting byte stream into a frame-aligned output slice and any trailing carry bytes.
   */
  fun alignToFrameBoundary(
    carryBuffer: ByteArray,
    carryCount: Int,
    incoming: ByteArray,
    incomingLength: Int,
    outputBuffer: ByteArray,
    frameSizeBytes: Int = 4,
  ): Pair<Int, Int> {
    var outPos = 0
    for (i in 0 until carryCount) {
      outputBuffer[outPos++] = carryBuffer[i]
    }
    System.arraycopy(incoming, 0, outputBuffer, outPos, incomingLength)
    val totalBytes = carryCount + incomingLength
    val alignedLength = (totalBytes / frameSizeBytes) * frameSizeBytes
    val newCarryCount = totalBytes - alignedLength
    for (i in 0 until newCarryCount) {
      carryBuffer[i] = outputBuffer[alignedLength + i]
    }
    return alignedLength to newCarryCount
  }
}
