package com.example.bluetooth

import com.example.model.ConnectedDeviceInfo
import com.example.model.RootA2dpStatus
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class ShellResult(
  val exitCode: Int,
  val stdout: String,
  val stderr: String,
) {
  val isSuccess: Boolean
    get() = exitCode == 0
}

interface ShellExecutor {
  fun hasSuBinary(): Boolean

  fun execute(command: String, useRoot: Boolean, timeoutMs: Long = 15_000L): ShellResult
}

class ProcessShellExecutor(
  private val diagnosticHandler: ShellDiagnosticLogHandler = ShellDiagnosticLogHandler.shared,
  private val processFactory: (useRoot: Boolean, command: String) -> ProcessBuilder = { useRoot, command ->
    val cmd = if (useRoot) arrayOf("su", "-c", command) else arrayOf("sh", "-c", command)
    ProcessBuilder(*cmd)
  },
) : ShellExecutor {
  override fun hasSuBinary(): Boolean {
    if (SU_CANDIDATE_PATHS.any { path -> File(path).exists() }) {
      return true
    }
    val whichResult = execute("which su", useRoot = false, timeoutMs = 2_000L)
    return whichResult.isSuccess && whichResult.stdout.trim().isNotEmpty()
  }

  override fun execute(command: String, useRoot: Boolean, timeoutMs: Long): ShellResult {
    val startMs = System.currentTimeMillis()
    val tag = if (useRoot) "su" else "sh"
    val process =
      try {
        processFactory(useRoot, command).start()
      } catch (e: IOException) {
        val err = e.localizedMessage ?: "Command binary unavailable"
        if (useRoot) {
          diagnosticHandler.record(
            tag = tag,
            command = command,
            exitCode = 127,
            durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
            stderr = err,
            stdout = "",
          )
        }
        return ShellResult(exitCode = 127, stdout = "", stderr = err)
      }

    val stdoutBuf = StringBuilder()
    val stderrBuf = StringBuilder()
    val outLock = Any()
    val errLock = Any()

    val outThread =
      thread(start = true) {
        try {
          process.inputStream.bufferedReader().use { reader ->
            var line = reader.readLine()
            while (line != null) {
              synchronized(outLock) {
                if (stdoutBuf.isNotEmpty()) stdoutBuf.append('\n')
                stdoutBuf.append(line)
              }
              line = reader.readLine()
            }
          }
        } catch (_: IOException) {
        }
      }

    val errThread =
      thread(start = true) {
        try {
          process.errorStream.bufferedReader().use { reader ->
            var line = reader.readLine()
            while (line != null) {
              synchronized(errLock) {
                if (stderrBuf.isNotEmpty()) stderrBuf.append('\n')
                stderrBuf.append(line)
              }
              line = reader.readLine()
            }
          }
        } catch (_: IOException) {
        }
      }

    val finished =
      try {
        process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
      } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
      }

    if (!finished) {
      process.destroyForcibly()
      outThread.join(500)
      errThread.join(500)
      val capturedOut = synchronized(outLock) { stdoutBuf.toString() }
      val capturedErr =
        synchronized(errLock) {
          if (stderrBuf.isNotEmpty()) {
            "$stderrBuf\n[t_wave] Shell command timed out after ${timeoutMs}ms"
          } else {
            "Shell command timed out after ${timeoutMs}ms"
          }
        }
      if (useRoot) {
        diagnosticHandler.record(
          tag = tag,
          command = command,
          exitCode = 124,
          durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
          stderr = capturedErr,
          stdout = capturedOut,
        )
      }
      return ShellResult(exitCode = 124, stdout = capturedOut, stderr = capturedErr)
    }

    outThread.join(1000)
    errThread.join(1000)
    val exitCode = process.exitValue()
    val capturedOut = synchronized(outLock) { stdoutBuf.toString() }
    val capturedErr = synchronized(errLock) { stderrBuf.toString() }

    if (useRoot || exitCode != 0) {
      diagnosticHandler.record(
        tag = tag,
        command = command,
        exitCode = exitCode,
        durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(0L),
        stderr = capturedErr,
        stdout = capturedOut,
      )
    }

    return ShellResult(exitCode = exitCode, stdout = capturedOut, stderr = capturedErr)
  }

  companion object {
    private val SU_CANDIDATE_PATHS =
      listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/vendor/bin/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
      )
  }
}

/**
 * Controls Android's privileged A2DP Sink profile, A2DP offload bypass, Bluetooth process cold
 * restart, and Class of Device via superuser (su) so other phones and PCs recognize and connect
 * to this device as a native Bluetooth Audio Speaker.
 */
class RootA2dpController(
  private val shell: ShellExecutor = ProcessShellExecutor(),
) {

  private val sdpConfigurator = BluetoothSdpProfileConfigurator(shell)

  fun probeSystemProperties(): RootA2dpStatus =
    sdpConfigurator.inspectCurrentState(requireRoot = false)

  fun requestRootAndInspect(): RootA2dpStatus =
    sdpConfigurator.inspectCurrentState(requireRoot = true)

  fun enableA2dpSink(
    packageName: String,
    cachedOriginalCod: String? = null,
  ): Result<RootA2dpStatus> =
    sdpConfigurator.applyAudioSinkSdpAndProfile(
      packageName = packageName,
      cachedOriginalCod = cachedOriginalCod,
    )

  fun disableA2dpSink(originalCod: String?): Result<RootA2dpStatus> =
    sdpConfigurator.restoreStandardPhoneProfile(originalCod = originalCod)

  /**
   * Grants signature|privileged Bluetooth permissions via root when available so the app can
   * adjust A2DP Sink connection policies on bonded remote sources.
   */
  fun setA2dpSinkPolicyViaRoot(packageName: String): Boolean {
    val safePackage = packageName.replace(Regex("[^A-Za-z0-9._]"), "")
    val result =
      shell.execute(
        "pm grant $safePackage android.permission.BLUETOOTH_PRIVILEGED 2>/dev/null || true",
        useRoot = true,
        timeoutMs = 4_000L,
      )
    return result.isSuccess
  }

  fun grantRecordAudioViaRoot(packageName: String): Boolean {
    val safePackage = packageName.replace(Regex("[^A-Za-z0-9._]"), "")
    val result =
      shell.execute(
        "pm grant $safePackage android.permission.RECORD_AUDIO 2>/dev/null || true",
        useRoot = true,
        timeoutMs = 4_000L,
      )
    return result.isSuccess
  }

  fun wakeA2dpAudioViaRoot(packageName: String): Boolean {
    val safePackage = packageName.replace(Regex("[^A-Za-z0-9._]"), "")
    val cmd =
      """
      pm grant $safePackage android.permission.RECORD_AUDIO >&2 2>&1 || true
      for bt_pkg in com.android.bluetooth com.google.android.bluetooth com.android.btservices; do
        pm enable "${'$'}bt_pkg/com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService" >/dev/null 2>&1 || true
      done
      cmd device_config put bluetooth a2dp_sink_automatically_request_audio_focus true >&2 2>&1 || true
      cmd audio set-force-use MEDIA NONE >&2 2>&1 || true
      cmd media_session dispatch play >&2 2>&1 || true
      """.trimIndent()
    return shell.execute(cmd, useRoot = true, timeoutMs = 5_000L).isSuccess
  }

  fun queryConnectedA2dpDevice(): ConnectedDeviceInfo? {
    val result = shell.execute("dumpsys bluetooth_manager", useRoot = true, timeoutMs = 4_000L)
    if (!result.isSuccess || result.stdout.isBlank()) return null
    return parseConnectedA2dpFromDumpsys(result.stdout)
  }

  companion object {
    const val A2DP_SINK_PROFILE_ID = 11
    const val A2DP_SOURCE_PROFILE_ID = 2
    const val CONNECTION_POLICY_ALLOWED = 100
    const val CONNECTION_POLICY_FORBIDDEN = 0
    const val ACTION_A2DP_SINK_CONNECTION_STATE_CHANGED =
      "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED"
    const val AUDIO_SINK_COD = "36,4,20" // 0x240414: Rendering + Audio + Loudspeaker
    const val DEFAULT_PHONE_COD = "90,2,12" // 0x5A020C: Smartphone default CoD
    const val PROP_A2DP_SINK = "bluetooth.profile.a2dp.sink.enabled"
    const val PROP_PERSIST_A2DP_SINK = "persist.bluetooth.a2dpsink.enabled"
    const val PROP_PERSIST_A2DP_SINK_UNDERSCORE = "persist.bluetooth.a2dp_sink.enabled"
    const val PROP_AVRCP_CTRL = "bluetooth.profile.avrcp.controller.enabled"
    const val PROP_A2DP_SOURCE = "bluetooth.profile.a2dp.source.enabled"
    const val PROP_AVRCP_TARGET = "bluetooth.profile.avrcp.target.enabled"
    const val PROP_OFFLOAD_DISABLED = "persist.bluetooth.a2dp_offload.disabled"
    const val PROP_CLASS_OF_DEVICE = "bluetooth.device.class_of_device"

    private val MAC_REGEX = Regex("([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})")
    private val NAMED_MAC_REGEX =
      Regex("([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})\\s*\\(([^)]+)\\)")

    internal fun parseConnectedA2dpFromDumpsys(dumpsys: String): ConnectedDeviceInfo? {
      var inA2dpSinkSection = false
      for (rawLine in dumpsys.lines()) {
        val line = rawLine.trim()
        if (line.contains("Profile: A2dpSinkService", ignoreCase = true) ||
          line.contains("A2dpSinkStateMachine", ignoreCase = true)
        ) {
          inA2dpSinkSection = true
        } else if (inA2dpSinkSection &&
          line.startsWith("Profile: ") &&
          !line.contains("A2dpSink", ignoreCase = true)
        ) {
          inA2dpSinkSection = false
        }

        if (inA2dpSinkSection &&
          line.contains("Connected", ignoreCase = true) &&
          !line.contains("Disconnected", ignoreCase = true) &&
          !line.contains("mMaxConnectedAudioDevices", ignoreCase = true)
        ) {
          val namedMatch = NAMED_MAC_REGEX.find(line)
          if (namedMatch != null) {
            return ConnectedDeviceInfo(
              name = namedMatch.groupValues[2].trim().ifBlank { "Bluetooth Audio Source" },
              address = namedMatch.groupValues[1].uppercase(),
            )
          }
          val macMatch = MAC_REGEX.find(line)
          if (macMatch != null) {
            return ConnectedDeviceInfo(
              name = "Bluetooth Audio Source",
              address = macMatch.groupValues[1].uppercase(),
            )
          }
        }
      }
      return null
    }
  }
}
