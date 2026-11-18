package com.example.bluetooth

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ShellDiagnosticEntry(
  val id: Long,
  val timestampMs: Long,
  val tag: String,
  val commandPreview: String,
  val exitCode: Int,
  val exitExplanation: String,
  val durationMs: Long,
  val stderr: String,
  val stdout: String,
  val lastStage: String,
) {
  val isSuccess: Boolean
    get() = exitCode == 0
}

/**
 * Thread-safe diagnostic log handler that captures stderr, stdout, execution duration,
 * stage trace markers, and Unix signal explanations (such as exit 137 / SIGKILL)
 * from `ProcessBuilder` shell executions.
 */
class ShellDiagnosticLogHandler(
  private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
  private val emitToLogcat: Boolean = true,
) {
  private val idCounter = AtomicLong(0L)
  private val _entries = MutableStateFlow<List<ShellDiagnosticEntry>>(emptyList())
  val entries: StateFlow<List<ShellDiagnosticEntry>> = _entries.asStateFlow()

  fun record(
    tag: String,
    command: String,
    exitCode: Int,
    durationMs: Long,
    stderr: String,
    stdout: String,
    timestampMs: Long = System.currentTimeMillis(),
  ): ShellDiagnosticEntry {
    val normalizedStderr = stderr.trimEnd().takeLast(MAX_STREAM_CHARS)
    val normalizedStdout = stdout.trimEnd().takeLast(MAX_STREAM_CHARS)
    val explanation = decodeExitCode(exitCode)
    val lastStage = extractLastStage(normalizedStderr)
    val entry =
      ShellDiagnosticEntry(
        id = idCounter.incrementAndGet(),
        timestampMs = timestampMs,
        tag = tag,
        commandPreview = summarizeCommand(command),
        exitCode = exitCode,
        exitExplanation = explanation,
        durationMs = durationMs,
        stderr = normalizedStderr,
        stdout = normalizedStdout,
        lastStage = lastStage,
      )

    _entries.update { current ->
      (listOf(entry) + current).take(maxEntries)
    }

    if (emitToLogcat) {
      logToAndroidLogcat(entry)
    }
    return entry
  }

  fun clear() {
    _entries.value = emptyList()
  }

  private fun logToAndroidLogcat(entry: ShellDiagnosticEntry) {
    try {
      val msg =
        buildString {
          append("[${entry.tag}] ${entry.exitExplanation} (${entry.durationMs}ms)")
          if (entry.lastStage.isNotBlank()) {
            append(" | stage=${entry.lastStage}")
          }
          if (entry.stderr.isNotBlank()) {
            append("\nstderr:\n${entry.stderr}")
          }
        }
      if (entry.isSuccess) {
        Log.d(LOG_TAG, msg)
      } else {
        Log.e(LOG_TAG, msg)
      }
    } catch (_: RuntimeException) {
      // Safe fallback when invoked in pure JVM tests without Android Log mock
    }
  }

  companion object {
    private const val LOG_TAG = "t_wave_root_shell"
    private const val DEFAULT_MAX_ENTRIES = 30
    private const val MAX_STREAM_CHARS = 4_000
    private const val STAGE_PREFIX = "[t_wave] stage:"

    val shared = ShellDiagnosticLogHandler()

    fun decodeExitCode(exitCode: Int): String =
      when (exitCode) {
        0 -> "Exit 0 (OK)"
        124 -> "Exit 124 (Command timed out)"
        126 -> "Exit 126 (Permission denied / not executable)"
        127 -> "Exit 127 (Command or su binary not found)"
        130 -> "Exit 130 (SIGINT — interrupted)"
        137 -> "Exit 137 (SIGKILL / signal 9 — killed externally or by process killer)"
        139 -> "Exit 139 (SIGSEGV — segmentation fault)"
        143 -> "Exit 143 (SIGTERM — terminated)"
        in 129..255 -> "Exit $exitCode (Terminated by signal ${exitCode - 128})"
        else -> "Exit $exitCode"
      }

    fun extractLastStage(stderr: String): String {
      val lines = stderr.lines().map { it.trim() }.filter { it.isNotEmpty() }
      val stageLine = lines.lastOrNull { it.startsWith(STAGE_PREFIX) }
      if (stageLine != null) {
        return stageLine.removePrefix(STAGE_PREFIX).trim()
      }
      return lines.lastOrNull().orEmpty()
    }

    fun buildFailureDetail(
      fallbackPrefix: String,
      exitCode: Int,
      stderr: String,
      stdout: String = "",
    ): String {
      val explanation = decodeExitCode(exitCode)
      val cleanStderr = stderr.trim()
      val stage = extractLastStage(cleanStderr)
      return buildString {
        append(fallbackPrefix)
        append(" — ")
        append(explanation)
        if (stage.isNotBlank() && !cleanStderr.endsWith(stage)) {
          append(" • Last stage: ")
          append(stage)
        }
        if (cleanStderr.isNotBlank()) {
          append("\nstderr: ")
          append(cleanStderr.lines().takeLast(6).joinToString(" | "))
        } else if (stdout.isNotBlank()) {
          append("\nstdout: ")
          append(stdout.trim().lines().takeLast(4).joinToString(" | "))
        }
      }
    }

    fun formatReport(entries: List<ShellDiagnosticEntry>): String {
      if (entries.isEmpty()) return "No root shell diagnostic entries recorded yet."
      val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
      return entries.joinToString("\n\n----------------------------------------\n\n") { e ->
        buildString {
          append("[${fmt.format(Date(e.timestampMs))}] ${e.tag} • ${e.exitExplanation} (${e.durationMs}ms)\n")
          append("cmd: ${e.commandPreview}\n")
          if (e.lastStage.isNotBlank()) {
            append("lastStage: ${e.lastStage}\n")
          }
          append("stderr:\n${e.stderr.ifBlank { "<empty>" }}\n")
          if (e.stdout.isNotBlank()) {
            append("stdout:\n${e.stdout}")
          }
        }
      }
    }

    internal fun summarizeCommand(command: String): String {
      val meaningfulLine =
        command
          .lines()
          .map { it.trim() }
          .firstOrNull { it.isNotEmpty() && !it.startsWith("[t_wave]") }
          ?: command.trim()
      return if (meaningfulLine.length > 96) {
        meaningfulLine.take(93) + "…"
      } else {
        meaningfulLine
      }
    }
  }
}
