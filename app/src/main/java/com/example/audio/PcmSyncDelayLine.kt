package com.example.audio

import com.example.model.SpeakerServiceState

/**
 * Frame-aligned circular PCM ring buffer that applies manual synchronization delay (+5..+500 ms)
 * or advances/trims queued PCM frames on negative offsets (-250..-5 ms) to compensate for varying
 * Bluetooth hardware latencies across senders and receivers.
 */
class PcmSyncDelayLine {
  private var ringBuffer = ByteArray(0)
  private var readPos = 0
  private var writePos = 0
  private var bufferedBytes = 0
  private var lastSampleRateHz = 0
  private var lastFrameSizeBytes = 0
  private var lastNegativeOffsetMs = 0
  private var pendingAdvanceBytes = 0

  val currentBufferedBytes: Int
    get() = bufferedBytes

  fun reset() {
    readPos = 0
    writePos = 0
    bufferedBytes = 0
    pendingAdvanceBytes = 0
  }

  /**
   * Processes [length] frame-aligned PCM bytes from [input] into [output], applying [syncOffsetMs].
   * Returns the number of frame-aligned bytes written into [output].
   */
  fun processChunk(
    input: ByteArray,
    length: Int,
    output: ByteArray,
    sampleRateHz: Int,
    frameSizeBytes: Int,
    syncOffsetMs: Int,
  ): Int {
    val safeFrameSize = frameSizeBytes.coerceAtLeast(2)
    val safeRate = sampleRateHz.coerceIn(8_000, 192_000)
    val usableInput = ((length / safeFrameSize) * safeFrameSize).coerceAtMost(input.size)

    if (lastSampleRateHz != safeRate || lastFrameSizeBytes != safeFrameSize) {
      reset()
      lastSampleRateHz = safeRate
      lastFrameSizeBytes = safeFrameSize
    }

    val clampedOffset = syncOffsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)

    if (clampedOffset < 0) {
      if (clampedOffset < lastNegativeOffsetMs) {
        val deltaMs = lastNegativeOffsetMs - clampedOffset
        val deltaFrames = (safeRate.toLong() * deltaMs) / 1000L
        pendingAdvanceBytes =
          (pendingAdvanceBytes + (deltaFrames * safeFrameSize).toInt())
            .coerceAtMost(MAX_ADVANCE_BYTES)
      }
      lastNegativeOffsetMs = clampedOffset
    } else {
      lastNegativeOffsetMs = 0
      pendingAdvanceBytes = 0
    }

    if (usableInput <= 0 && bufferedBytes <= 0) return 0

    if (clampedOffset > 0) {
      val targetFrames = (safeRate.toLong() * clampedOffset) / 1000L
      val targetDelayBytes = (targetFrames * safeFrameSize).toInt()
      val requiredCapacity = maxOf(targetDelayBytes + usableInput * 2, MIN_RING_CAPACITY_BYTES)
      ensureCapacity(requiredCapacity)

      if (usableInput > 0) {
        enqueue(input, usableInput)
      }

      val excessBytes = bufferedBytes - targetDelayBytes
      if (excessBytes <= 0) {
        return 0
      }

      val maxOut = (output.size / safeFrameSize) * safeFrameSize
      val desiredOut = minOf(excessBytes, maxOf(usableInput * 2, safeFrameSize))
      val toRead = (minOf(desiredOut, excessBytes, maxOut) / safeFrameSize) * safeFrameSize
      return if (toRead > 0) dequeue(output, toRead) else 0
    }

    var outPos = 0
    val maxOut = (output.size / safeFrameSize) * safeFrameSize

    if (bufferedBytes > 0) {
      if (pendingAdvanceBytes > 0) {
        val toDrop = minOf(bufferedBytes, pendingAdvanceBytes)
        val alignedDrop = (toDrop / safeFrameSize) * safeFrameSize
        if (alignedDrop > 0) {
          discard(alignedDrop)
          pendingAdvanceBytes -= alignedDrop
        }
      }
      val drainCount = ((minOf(bufferedBytes, maxOut - outPos)) / safeFrameSize) * safeFrameSize
      if (drainCount > 0) {
        outPos += dequeue(output, drainCount, destOffset = outPos)
      }
    }

    if (usableInput <= 0) return outPos

    var inOffset = 0
    if (pendingAdvanceBytes > 0) {
      val dropFromInput =
        ((minOf(usableInput, pendingAdvanceBytes)) / safeFrameSize) * safeFrameSize
      inOffset += dropFromInput
      pendingAdvanceBytes -= dropFromInput
    }

    val remainingIn =
      ((minOf(usableInput - inOffset, maxOut - outPos)) / safeFrameSize) * safeFrameSize
    if (remainingIn > 0) {
      System.arraycopy(input, inOffset, output, outPos, remainingIn)
      outPos += remainingIn
    }
    return outPos
  }

  private fun ensureCapacity(minCapacity: Int) {
    if (ringBuffer.size >= minCapacity) return
    val next = ByteArray(maxOf(minCapacity, ringBuffer.size * 2))
    if (bufferedBytes > 0) {
      val firstPart = minOf(bufferedBytes, ringBuffer.size - readPos)
      System.arraycopy(ringBuffer, readPos, next, 0, firstPart)
      val secondPart = bufferedBytes - firstPart
      if (secondPart > 0) {
        System.arraycopy(ringBuffer, 0, next, firstPart, secondPart)
      }
    }
    ringBuffer = next
    readPos = 0
    writePos = bufferedBytes
  }

  private fun enqueue(src: ByteArray, length: Int) {
    val firstPart = minOf(length, ringBuffer.size - writePos)
    System.arraycopy(src, 0, ringBuffer, writePos, firstPart)
    val secondPart = length - firstPart
    if (secondPart > 0) {
      System.arraycopy(src, firstPart, ringBuffer, 0, secondPart)
    }
    writePos = (writePos + length) % ringBuffer.size
    bufferedBytes += length
  }

  private fun dequeue(dest: ByteArray, length: Int, destOffset: Int = 0): Int {
    val actual = minOf(length, bufferedBytes)
    if (actual <= 0) return 0
    val firstPart = minOf(actual, ringBuffer.size - readPos)
    System.arraycopy(ringBuffer, readPos, dest, destOffset, firstPart)
    val secondPart = actual - firstPart
    if (secondPart > 0) {
      System.arraycopy(ringBuffer, 0, dest, destOffset + firstPart, secondPart)
    }
    readPos = (readPos + actual) % ringBuffer.size
    bufferedBytes -= actual
    return actual
  }

  private fun discard(length: Int) {
    val actual = minOf(length, bufferedBytes)
    if (actual <= 0) return
    readPos = (readPos + actual) % ringBuffer.size
    bufferedBytes -= actual
  }

  companion object {
    const val MIN_OFFSET_MS = SpeakerServiceState.MIN_SYNC_OFFSET_MS
    const val MAX_OFFSET_MS = SpeakerServiceState.MAX_SYNC_OFFSET_MS
    const val STEP_MS = SpeakerServiceState.SYNC_OFFSET_STEP_MS
    private const val MIN_RING_CAPACITY_BYTES = 16_384
    private const val MAX_ADVANCE_BYTES = 192_000 * 8

    fun clampAndSnapOffset(offsetMs: Int): Int {
      val clamped = offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
      val rounded =
        ((clamped + if (clamped >= 0) STEP_MS / 2 else -(STEP_MS / 2)) / STEP_MS) * STEP_MS
      return rounded.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
    }

    fun computeEffectiveLatencyMs(baseLatencyMs: Int, syncOffsetMs: Int): Int =
      (baseLatencyMs + syncOffsetMs).coerceAtLeast(0)

    fun formatOffsetLabel(syncOffsetMs: Int): String =
      when {
        syncOffsetMs > 0 -> "+${syncOffsetMs} ms"
        syncOffsetMs < 0 -> "${syncOffsetMs} ms"
        else -> "0 ms"
      }

    fun formatOffsetBadge(syncOffsetMs: Int): String =
      when {
        syncOffsetMs > 0 -> "+${syncOffsetMs} ms (Delay)"
        syncOffsetMs < 0 -> "${syncOffsetMs} ms (Advance)"
        else -> "0 ms (Native)"
      }

    fun targetDelayFrames(sampleRateHz: Int, syncOffsetMs: Int): Int =
      if (syncOffsetMs <= 0) 0
      else ((sampleRateHz.toLong() * syncOffsetMs.coerceAtMost(MAX_OFFSET_MS)) / 1000L).toInt()
  }
}
