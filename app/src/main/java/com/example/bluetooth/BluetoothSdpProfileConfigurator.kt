package com.example.bluetooth

import android.bluetooth.BluetoothAdapter
import android.os.ParcelUuid
import com.example.model.RootA2dpStatus
import com.example.model.RootPrivilegeStatus

/**
 * Root-privileged Bluetooth SDP and profile configurator.
 *
 * Modifies Android Bluetooth stack profile settings (`resetprop`, `setprop`, `device_config`),
 * local adapter SDP Class of Device (`bluetooth.device.class_of_device = 36,4,20` / `0x240414`),
 * persistent Fluoride/GD adapter configuration (`bt_config.conf` `[Adapter] DevClass = 240414`),
 * disables A2DP hardware offload / conflicting source profiles, cold-restarts `com.android.bluetooth`,
 * and verifies that SDP records `0000110b` (A2DP Audio Sink) and `0000110e` (AVRCP Controller)
 * are exposed to nearby Bluetooth devices.
 */
class BluetoothSdpProfileConfigurator(
  private val shell: ShellExecutor = ProcessShellExecutor(),
) {

  fun inspectCurrentState(
    adapter: BluetoothAdapter? = null,
    requireRoot: Boolean = false,
  ): RootA2dpStatus {
    val cmd =
      if (requireRoot) {
        "echo UID=$(id -u); $INSPECT_ALL_CMD"
      } else {
        INSPECT_NON_ROOT_CMD
      }
    val suAvailable = shell.hasSuBinary()
    val result = shell.execute(cmd, useRoot = requireRoot, timeoutMs = 8_000L)

    if (requireRoot) {
      if (result.exitCode == 127 || (!suAvailable && result.exitCode != 0)) {
        return RootA2dpStatus(
          privilegeStatus = RootPrivilegeStatus.UNAVAILABLE,
          lastCommandLog = result.stderr.ifBlank { "No su binary available on this device" },
        )
      }
      val uidLine = result.stdout.lines().firstOrNull { it.startsWith("UID=") }
      val isRootUid = uidLine == "UID=0" || result.stdout.contains("uid=0(")
      if (!result.isSuccess || !isRootUid) {
        return RootA2dpStatus(
          privilegeStatus = RootPrivilegeStatus.DENIED,
          lastCommandLog = result.stderr.ifBlank { "Superuser access denied (exit ${result.exitCode})" },
        )
      }
    }

    val parsed = parseStatusOutput(result.stdout)
    val enriched = enrichWithAdapterSdpUuids(parsed, adapter)
    val privilege =
      when {
        requireRoot -> RootPrivilegeStatus.GRANTED
        !suAvailable -> RootPrivilegeStatus.UNAVAILABLE
        else -> RootPrivilegeStatus.UNKNOWN
      }
    val summary =
      when {
        !requireRoot -> enriched.lastCommandLog
        enriched.isA2dpSinkServiceRunning && enriched.isAudioSinkSdpExposed ->
          "Root UID=0 • A2dpSinkService & SDP 0x110B active"
        enriched.isA2dpSinkServiceRunning ->
          "Root UID=0 • A2dpSinkService running"
        else -> "Root UID=0 verified"
      }

    return enriched.copy(
      privilegeStatus = privilege,
      lastCommandLog = summary,
    )
  }

  fun applyAudioSinkSdpAndProfile(
    packageName: String,
    cachedOriginalCod: String? = null,
    adapter: BluetoothAdapter? = null,
  ): Result<RootA2dpStatus> {
    val inspected = inspectCurrentState(adapter = adapter, requireRoot = true)
    if (!inspected.isRootGranted) {
      val reason =
        when (inspected.privilegeStatus) {
          RootPrivilegeStatus.UNAVAILABLE ->
            "Root binary (su) not found. Root privilege is required to modify Bluetooth SDP records and A2DP Sink settings."
          else ->
            "Root privilege (su) was denied. Grant superuser access to expose this device as an A2DP speaker sink."
        }
      return Result.failure(SecurityException(reason))
    }

    val originalCod =
      cachedOriginalCod?.takeIf { it.isNotBlank() && it != RootA2dpController.AUDIO_SINK_COD }
        ?: inspected.classOfDevice.takeIf { it.isNotBlank() && it != RootA2dpController.AUDIO_SINK_COD }

    val safePackage = packageName.replace(Regex("[^A-Za-z0-9._]"), "")
    val script = buildApplySdpAndProfileScript(safePackage)
    val execResult = shell.execute(script, useRoot = true, timeoutMs = 24_000L)
    if (!execResult.isSuccess) {
      val detail =
        ShellDiagnosticLogHandler.buildFailureDetail(
          fallbackPrefix = "Failed to modify Bluetooth SDP records and A2DP Sink profile settings",
          exitCode = execResult.exitCode,
          stderr = execResult.stderr,
          stdout = execResult.stdout,
        )
      return Result.failure(IllegalStateException(detail))
    }

    val updated = enrichWithAdapterSdpUuids(parseStatusOutput(execResult.stdout), adapter)
    if (!updated.isA2dpSinkEnabled && !updated.isA2dpSinkServiceRunning) {
      return Result.failure(
        IllegalStateException(
          "A2DP Sink system property (${RootA2dpController.PROP_A2DP_SINK}) remained false. Ensure Magisk/KernelSU resetprop is available."
        )
      )
    }

    val logMsg =
      when {
        updated.isA2dpSinkServiceRunning && updated.isAudioSinkSdpExposed ->
          "SDP 0x110B exposed • A2dpSinkService active • CoD ${updated.classOfDevice.ifBlank { RootA2dpController.AUDIO_SINK_COD }}"
        updated.isA2dpSinkServiceRunning ->
          "A2dpSinkService active (0x110B) • CoD ${updated.classOfDevice.ifBlank { RootA2dpController.AUDIO_SINK_COD }}"
        else ->
          "SDP/props updated (CoD ${updated.classOfDevice.ifBlank { RootA2dpController.AUDIO_SINK_COD }}), waiting on A2dpSinkService — unpair & re-pair or check ROM BT overlay"
      }

    return Result.success(
      updated.copy(
        privilegeStatus = RootPrivilegeStatus.GRANTED,
        originalClassOfDevice = originalCod,
        lastCommandLog = logMsg,
      )
    )
  }

  fun restoreStandardPhoneProfile(
    originalCod: String?,
    adapter: BluetoothAdapter? = null,
  ): Result<RootA2dpStatus> {
    val inspected = inspectCurrentState(adapter = adapter, requireRoot = true)
    if (!inspected.isRootGranted) {
      return Result.failure(
        SecurityException("Root privilege (su) is required to restore standard Bluetooth SDP and profile settings.")
      )
    }

    val codRestore =
      originalCod?.takeIf { it.isNotBlank() && it != RootA2dpController.AUDIO_SINK_COD }
        ?: RootA2dpController.DEFAULT_PHONE_COD
    val devClassHexRestore = codTupleToHex(codRestore)

    val script = buildRestoreProfileScript(codRestore, devClassHexRestore)
    val execResult = shell.execute(script, useRoot = true, timeoutMs = 24_000L)
    if (!execResult.isSuccess) {
      val detail =
        ShellDiagnosticLogHandler.buildFailureDetail(
          fallbackPrefix = "Failed to restore standard Bluetooth A2DP Source SDP profile",
          exitCode = execResult.exitCode,
          stderr = execResult.stderr,
          stdout = execResult.stdout,
        )
      return Result.failure(IllegalStateException(detail))
    }

    val updated = enrichWithAdapterSdpUuids(parseStatusOutput(execResult.stdout), adapter)
    return Result.success(
      updated.copy(
        privilegeStatus = RootPrivilegeStatus.GRANTED,
        originalClassOfDevice = originalCod,
        lastCommandLog = "Restored standard Phone SDP & A2DP Source profile",
      )
    )
  }

  @Suppress("UNCHECKED_CAST")
  fun readAdapterSdpUuids(adapter: BluetoothAdapter?): List<String> {
    if (adapter == null) return emptyList()
    return try {
      val method = adapter.javaClass.getMethod("getUuids")
      val uuids = method.invoke(adapter) as? Array<ParcelUuid> ?: return emptyList()
      uuids.map { it.uuid.toString().lowercase() }.distinct()
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun enrichWithAdapterSdpUuids(
    status: RootA2dpStatus,
    adapter: BluetoothAdapter?,
  ): RootA2dpStatus {
    val adapterUuids = readAdapterSdpUuids(adapter)
    if (adapterUuids.isEmpty()) return status
    val combinedUuids = (status.exposedSdpUuids + adapterUuids).distinct()
    val hasSinkUuid = status.isAudioSinkSdpExposed || adapterUuids.any { it.startsWith(UUID_PREFIX_AUDIO_SINK) }
    val hasAvrcpUuid = status.isAvrcpSdpExposed || adapterUuids.any { it.startsWith(UUID_PREFIX_AVRCP_CTRL) }
    return status.copy(
      isAudioSinkSdpExposed = hasSinkUuid,
      isAvrcpSdpExposed = hasAvrcpUuid,
      exposedSdpUuids = combinedUuids,
    )
  }

  internal fun parseStatusOutput(stdout: String): RootA2dpStatus {
    val map = mutableMapOf<String, String>()
    stdout.lines().forEach { line ->
      val idx = line.indexOf('=')
      if (idx > 0) {
        map[line.substring(0, idx).trim()] = line.substring(idx + 1).trim()
      }
    }
    val sink = map["SINK"]?.equals("true", ignoreCase = true) == true
    val persistSink = map["PSINK"]?.equals("true", ignoreCase = true) == true
    val persistSinkUnderscore = map["PSINKU"]?.equals("true", ignoreCase = true) == true
    val avrcp = map["AVRCP"]?.equals("true", ignoreCase = true) == true
    val offloadDisabled = map["OFFLOAD_OFF"]?.equals("true", ignoreCase = true) == true
    val svcRunning = map["A2DP_SVC"]?.equals("1", ignoreCase = true) == true
    val sdpSink = map["SDP_SINK"]?.equals("1", ignoreCase = true) == true
    val sdpAvrcp = map["SDP_AVRCP"]?.equals("1", ignoreCase = true) == true
    val btConfigPatched = map["BT_CFG"]?.equals("1", ignoreCase = true) == true
    val cod = map["COD"].orEmpty()

    val detectedUuids = buildList {
      if (sdpSink) add(AUDIO_SINK_SDP_UUID)
      if (sdpAvrcp) add(AVRCP_CONTROLLER_SDP_UUID)
    }

    return RootA2dpStatus(
      isA2dpSinkEnabled = sink || persistSink || persistSinkUnderscore || svcRunning,
      isA2dpSinkServiceRunning = svcRunning,
      isAvrcpControllerEnabled = avrcp || sdpAvrcp,
      isOffloadDisabled = offloadDisabled,
      isAudioSinkSdpExposed = sdpSink || svcRunning,
      isAvrcpSdpExposed = sdpAvrcp,
      isBtConfigPatched = btConfigPatched,
      exposedSdpUuids = detectedUuids,
      classOfDevice = cod,
    )
  }

  companion object {
    const val AUDIO_SINK_SDP_UUID = "0000110b-0000-1000-8000-00805f9b34fb"
    const val AVRCP_CONTROLLER_SDP_UUID = "0000110e-0000-1000-8000-00805f9b34fb"
    private const val UUID_PREFIX_AUDIO_SINK = "0000110b"
    private const val UUID_PREFIX_AVRCP_CTRL = "0000110e"
    const val AUDIO_SINK_DEV_CLASS_HEX = "240414"

    private const val RESETPROP_LOOKUP =
      """
      RESETPROP=""
      for candidate in resetprop /data/adb/ksu/bin/resetprop /data/adb/ap/bin/resetprop /data/adb/magisk/resetprop /sbin/resetprop /system/bin/resetprop; do
        if [ -x "${'$'}candidate" ] || command -v "${'$'}candidate" >/dev/null 2>&1; then
          RESETPROP="${'$'}candidate"
          break
        fi
      done
      """

    private val INSPECT_NON_ROOT_CMD =
      "echo SINK=\$(getprop ${RootA2dpController.PROP_A2DP_SINK}); " +
        "echo PSINK=\$(getprop ${RootA2dpController.PROP_PERSIST_A2DP_SINK}); " +
        "echo PSINKU=\$(getprop ${RootA2dpController.PROP_PERSIST_A2DP_SINK_UNDERSCORE}); " +
        "echo AVRCP=\$(getprop ${RootA2dpController.PROP_AVRCP_CTRL}); " +
        "echo OFFLOAD_OFF=\$(getprop ${RootA2dpController.PROP_OFFLOAD_DISABLED}); " +
        "echo COD=\$(getprop ${RootA2dpController.PROP_CLASS_OF_DEVICE})"

    private val INSPECT_ALL_CMD =
      INSPECT_NON_ROOT_CMD +
        "; if dumpsys bluetooth_manager 2>/dev/null | grep -qiE 'A2dpSinkService|A2dpSinkStateMachine'; then echo A2DP_SVC=1; else echo A2DP_SVC=0; fi" +
        "; if dumpsys bluetooth_manager 2>/dev/null | grep -qiE '0000110b|AudioSink'; then echo SDP_SINK=1; else echo SDP_SINK=0; fi" +
        "; if dumpsys bluetooth_manager 2>/dev/null | grep -qiE '0000110e|AvrcpController'; then echo SDP_AVRCP=1; else echo SDP_AVRCP=0; fi" +
        "; if grep -qi 'DevClass = $AUDIO_SINK_DEV_CLASS_HEX' /data/misc/bluedroid/bt_config.conf /data/misc/bluetooth/bt_config.conf 2>/dev/null; then echo BT_CFG=1; else echo BT_CFG=0; fi"

    internal fun codTupleToHex(codTuple: String): String {
      val parts = codTuple.split(',').mapNotNull { it.trim().toIntOrNull() }
      if (parts.size != 3) return "5a020c"
      return parts.joinToString("") { (it and 0xFF).toString(16).padStart(2, '0') }
    }

    private const val KILL_BT_PROCESS_SAFELY =
      """
      for bt_pid in $(pidof com.android.bluetooth 2>/dev/null); do
        if [ "${'$'}bt_pid" != "${'$'}${'$'}" ] && [ "${'$'}bt_pid" != "${'$'}PPID" ]; then
          kill -9 "${'$'}bt_pid" >/dev/null 2>&1 || true
        fi
      done
      killall -9 com.android.bluetooth >/dev/null 2>&1 || true
      """

    private fun buildApplySdpAndProfileScript(safePackage: String): String =
      """
      echo "[t_wave] stage: lookup_resetprop" >&2
      $RESETPROP_LOOKUP
      echo "[t_wave] stage: apply_properties (RESETPROP=${'$'}{RESETPROP:-none})" >&2
      if [ -n "${'$'}RESETPROP" ]; then
        "${'$'}RESETPROP" ${RootA2dpController.PROP_A2DP_SINK} true >&2 2>&1 || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_PERSIST_A2DP_SINK} true >&2 2>&1 || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_PERSIST_A2DP_SINK_UNDERSCORE} true >&2 2>&1 || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_AVRCP_CTRL} true >&2 2>&1 || true
        "${'$'}RESETPROP" persist.bluetooth.avrcpcontroller.enabled true >&2 2>&1 || true
        "${'$'}RESETPROP" bluetooth.a2dp_sink.automatically_request_audio_focus true >&2 2>&1 || true
        "${'$'}RESETPROP" persist.bluetooth.a2dp_sink.request_audio_focus true >&2 2>&1 || true
        "${'$'}RESETPROP" persist.bluetooth.bluetooth_audio_hal.disabled false >&2 2>&1 || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_A2DP_SOURCE} false >&2 2>&1 || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_AVRCP_TARGET} false >&2 2>&1 || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_OFFLOAD_DISABLED} true >&2 2>&1 || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_CLASS_OF_DEVICE} ${RootA2dpController.AUDIO_SINK_COD} >&2 2>&1 || true
      fi
      setprop ${RootA2dpController.PROP_PERSIST_A2DP_SINK} true 2>/dev/null || true
      setprop ${RootA2dpController.PROP_PERSIST_A2DP_SINK_UNDERSCORE} true 2>/dev/null || true
      setprop ${RootA2dpController.PROP_A2DP_SINK} true 2>/dev/null || true
      setprop ${RootA2dpController.PROP_AVRCP_CTRL} true 2>/dev/null || true
      setprop persist.bluetooth.avrcpcontroller.enabled true 2>/dev/null || true
      setprop persist.bluetooth.a2dp_sink.request_audio_focus true 2>/dev/null || true
      setprop persist.bluetooth.bluetooth_audio_hal.disabled false 2>/dev/null || true
      setprop ${RootA2dpController.PROP_A2DP_SOURCE} false 2>/dev/null || true
      setprop ${RootA2dpController.PROP_AVRCP_TARGET} false 2>/dev/null || true
      setprop ${RootA2dpController.PROP_OFFLOAD_DISABLED} true 2>/dev/null || true
      setprop ${RootA2dpController.PROP_CLASS_OF_DEVICE} ${RootA2dpController.AUDIO_SINK_COD} 2>/dev/null || true
      echo "[t_wave] stage: device_config_and_grant" >&2
      cmd device_config put bluetooth a2dp_sink_enabled true >&2 2>&1 || true
      cmd device_config put bluetooth avrcp_controller_enabled true >&2 2>&1 || true
      cmd device_config put bluetooth a2dp_sink_automatically_request_audio_focus true >&2 2>&1 || true
      cmd device_config put bluetooth avrcp_controller_browsing_enabled true >&2 2>&1 || true
      settings put global bluetooth_a2dp_sink_priority 100 >&2 2>&1 || true
      pm grant $safePackage android.permission.BLUETOOTH_PRIVILEGED >&2 2>&1 || true
      pm grant $safePackage android.permission.RECORD_AUDIO >&2 2>&1 || true
      for bt_pkg in com.android.bluetooth com.google.android.bluetooth com.android.btservices; do
        pm enable "${'$'}bt_pkg/com.android.bluetooth.avrcpcontroller.BluetoothMediaBrowserService" >/dev/null 2>&1 || true
      done
      echo "[t_wave] stage: stop_bluetooth_stack" >&2
      cmd bluetooth_manager disable >/dev/null || svc bluetooth disable >/dev/null || true
      sleep 1
      $KILL_BT_PROCESS_SAFELY
      sleep 2
      echo "[t_wave] stage: patch_bt_config" >&2
      for cfg in /data/misc/bluedroid/bt_config.conf /data/misc/bluetooth/bt_config.conf; do
        if [ -f "${'$'}cfg" ] && grep -q '^\[Adapter\]' "${'$'}cfg"; then
          if [ ! -f "${'$'}cfg.twave.bak" ]; then
            cp -a "${'$'}cfg" "${'$'}cfg.twave.bak" || true
          fi
          if sed -n '/^\[Adapter\]/,/^\[/p' "${'$'}cfg" | grep -q '^DevClass ='; then
            sed -i '/^\[Adapter\]/,/^\[/{s/^DevClass = .*/DevClass = $AUDIO_SINK_DEV_CLASS_HEX/}' "${'$'}cfg" || true
          else
            sed -i '/^\[Adapter\]/a\
DevClass = $AUDIO_SINK_DEV_CLASS_HEX' "${'$'}cfg" || true
          fi
        fi
      done
      echo "[t_wave] stage: start_bluetooth_stack" >&2
      cmd bluetooth_manager enable >/dev/null || svc bluetooth enable >/dev/null || true
      sleep 3
      echo "[t_wave] stage: inspect_sdp_records" >&2
      $INSPECT_ALL_CMD
      exit 0
      """.trimIndent()

    private fun buildRestoreProfileScript(codRestore: String, devClassHexRestore: String): String =
      """
      echo "[t_wave] stage: restore_lookup_resetprop" >&2
      $RESETPROP_LOOKUP
      echo "[t_wave] stage: restore_properties (RESETPROP=${'$'}{RESETPROP:-none})" >&2
      if [ -n "${'$'}RESETPROP" ]; then
        "${'$'}RESETPROP" ${RootA2dpController.PROP_A2DP_SINK} false || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_PERSIST_A2DP_SINK} false || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_PERSIST_A2DP_SINK_UNDERSCORE} false || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_AVRCP_CTRL} false || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_A2DP_SOURCE} true || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_AVRCP_TARGET} true || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_OFFLOAD_DISABLED} false || true
        "${'$'}RESETPROP" ${RootA2dpController.PROP_CLASS_OF_DEVICE} $codRestore || true
      fi
      setprop ${RootA2dpController.PROP_PERSIST_A2DP_SINK} false 2>/dev/null || true
      setprop ${RootA2dpController.PROP_PERSIST_A2DP_SINK_UNDERSCORE} false 2>/dev/null || true
      setprop ${RootA2dpController.PROP_A2DP_SINK} false 2>/dev/null || true
      setprop ${RootA2dpController.PROP_AVRCP_CTRL} false 2>/dev/null || true
      setprop ${RootA2dpController.PROP_A2DP_SOURCE} true 2>/dev/null || true
      setprop ${RootA2dpController.PROP_AVRCP_TARGET} true 2>/dev/null || true
      setprop ${RootA2dpController.PROP_OFFLOAD_DISABLED} false 2>/dev/null || true
      setprop ${RootA2dpController.PROP_CLASS_OF_DEVICE} $codRestore 2>/dev/null || true
      cmd device_config put bluetooth a2dp_sink_enabled false || true
      cmd device_config put bluetooth avrcp_controller_enabled false || true
      echo "[t_wave] stage: restore_stop_bluetooth_stack" >&2
      cmd bluetooth_manager disable >/dev/null || svc bluetooth disable >/dev/null || true
      sleep 1
      $KILL_BT_PROCESS_SAFELY
      sleep 2
      echo "[t_wave] stage: restore_bt_config" >&2
      for cfg in /data/misc/bluedroid/bt_config.conf /data/misc/bluetooth/bt_config.conf; do
        if [ -f "${'$'}cfg.twave.bak" ]; then
          cp -a "${'$'}cfg.twave.bak" "${'$'}cfg" || true
          rm -f "${'$'}cfg.twave.bak" || true
        elif [ -f "${'$'}cfg" ] && grep -q '^\[Adapter\]' "${'$'}cfg"; then
          sed -i '/^\[Adapter\]/,/^\[/{s/^DevClass = .*/DevClass = $devClassHexRestore/}' "${'$'}cfg" || true
        fi
      done
      echo "[t_wave] stage: restore_start_bluetooth_stack" >&2
      cmd bluetooth_manager enable >/dev/null || svc bluetooth enable >/dev/null || true
      sleep 2
      echo "[t_wave] stage: restore_inspect" >&2
      $INSPECT_ALL_CMD
      exit 0
      """.trimIndent()
  }
}
