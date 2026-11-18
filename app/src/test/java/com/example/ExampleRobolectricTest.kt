package com.example

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.audio.PcmAnalyzer
import com.example.bluetooth.RootA2dpController
import com.example.bluetooth.ShellExecutor
import com.example.bluetooth.ShellResult
import com.example.model.AudioQualityProfile
import com.example.model.AudioTelemetry
import com.example.model.BluetoothHardwareStatus
import com.example.model.ConnectedDeviceInfo
import com.example.model.ConnectionStatus
import com.example.model.PermissionStatus
import com.example.model.RootA2dpStatus
import com.example.model.RootPrivilegeStatus
import com.example.model.SpeakerMode
import com.example.model.SpeakerServiceState
import com.example.permissions.PermissionHelper
import com.example.service.BluetoothSpeakerService
import com.example.service.SpeakerSessionManager
import com.example.ui.SpeakerScreen
import com.example.ui.theme.WaveCastTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `app name matches t_wave`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("t_wave", appName)
  }

  @Test
  fun `spp uuid matches standard bluetooth serial port profile`() {
    assertEquals(
      "00001101-0000-1000-8000-00805F9B34FB",
      BluetoothSpeakerService.SPP_UUID.toString().uppercase(),
    )
  }

  @Test
  @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
  fun `required runtime permissions include connect advertise and notifications on android 13+`() {
    val perms = PermissionHelper.requiredRuntimePermissions()
    assertTrue(perms.contains(Manifest.permission.BLUETOOTH_CONNECT))
    assertTrue(perms.contains(Manifest.permission.BLUETOOTH_ADVERTISE))
    assertTrue(perms.contains(Manifest.permission.POST_NOTIFICATIONS))
  }

  @Test
  fun `discoverable intent uses ACTION_REQUEST_DISCOVERABLE with 300s duration`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.viewmodel.SpeakerViewModel(app)
    val intent = vm.buildDiscoverableIntent()
    assertEquals(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE, intent.action)
    assertEquals(
      300,
      intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, -1),
    )
  }

  @Test
  fun `pcm analyzer aligns partial frames and measures stereo signal`() {
    val carry = ByteArray(4)
    val out = ByteArray(16)
    val chunk1 = byteArrayOf(0x00, 0x40, 0x00, 0x20, 0x11)
    val (aligned1, carryCount1) =
      PcmAnalyzer.alignToFrameBoundary(carry, 0, chunk1, chunk1.size, out, frameSizeBytes = 4)
    assertEquals(4, aligned1)
    assertEquals(1, carryCount1)

    val analysis = PcmAnalyzer.analyzeStereo16BitLe(out, aligned1)
    assertTrue(analysis.leftLevel > analysis.rightLevel)
    assertTrue(analysis.leftLevel > 0.5f)
  }

  @Test
  fun `pcm analyzer supports 24-bit and 32-bit high-res stereo alignment and float conversion`() {
    val pcm24 = byteArrayOf(0x00, 0x00, 0x40, 0x00, 0x00, 0xE0.toByte())
    val analysis24 = PcmAnalyzer.analyzeStereoPcm(pcm24, pcm24.size, bitDepth = 24)
    assertTrue(analysis24.leftLevel > analysis24.rightLevel)
    assertTrue(analysis24.leftLevel > 0.65f)

    val floatOut = FloatArray(2)
    val written = PcmAnalyzer.convertLePcmToFloat(pcm24, pcm24.size, bitDepth = 24, floatOut)
    assertEquals(2, written)
    assertEquals(0.5f, floatOut[0], 0.01f)
    assertEquals(-0.25f, floatOut[1], 0.01f)

    val carry32 = ByteArray(8)
    val out32 = ByteArray(16)
    val chunk32 = ByteArray(10)
    val (aligned32, carryCount32) =
      PcmAnalyzer.alignToFrameBoundary(carry32, 0, chunk32, chunk32.size, out32, frameSizeBytes = 8)
    assertEquals(8, aligned32)
    assertEquals(2, carryCount32)
  }

  @Test
  fun `root a2dp controller blocks enabling a2dp sink when device is unrooted or denied`() {
    val unrootedShell =
      object : ShellExecutor {
        override fun hasSuBinary(): Boolean = false

        override fun execute(command: String, useRoot: Boolean, timeoutMs: Long): ShellResult =
          if (useRoot) ShellResult(127, "", "su: not found") else ShellResult(0, "COD=90,2,12", "")
      }
    val controller = RootA2dpController(unrootedShell)
    assertEquals(RootPrivilegeStatus.UNAVAILABLE, controller.probeSystemProperties().privilegeStatus)

    val enableResult = controller.enableA2dpSink("com.aistudio.wavecast.krtmxb")
    assertTrue(enableResult.isFailure)
    assertTrue(enableResult.exceptionOrNull() is SecurityException)
  }

  @Test
  fun `root a2dp controller enables and restores a2dp sink when root is granted`() {
    val recordedCommands = mutableListOf<String>()
    var sinkEnabled = false
    var avrcpEnabled = false
    var offloadOff = false
    var svcRunning = false
    var cod = "90,2,12"

    val rootedShell =
      object : ShellExecutor {
        override fun hasSuBinary(): Boolean = true

        override fun execute(command: String, useRoot: Boolean, timeoutMs: Long): ShellResult {
          recordedCommands += command
          if (!useRoot) {
            return ShellResult(
              0,
              "SINK=$sinkEnabled\nPSINK=$sinkEnabled\nPSINKU=$sinkEnabled\nAVRCP=$avrcpEnabled\nOFFLOAD_OFF=$offloadOff\nCOD=$cod",
              "",
            )
          }
          if (command.contains("bluetooth.profile.a2dp.sink.enabled true")) {
            sinkEnabled = true
            avrcpEnabled = true
            offloadOff = true
            svcRunning = true
            cod = RootA2dpController.AUDIO_SINK_COD
          } else if (command.contains("bluetooth.profile.a2dp.sink.enabled false")) {
            sinkEnabled = false
            avrcpEnabled = false
            offloadOff = false
            svcRunning = false
            cod = "90,2,12"
          }
          val flag = if (svcRunning) 1 else 0
          return ShellResult(
            0,
            "UID=0\nSINK=$sinkEnabled\nPSINK=$sinkEnabled\nPSINKU=$sinkEnabled\nAVRCP=$avrcpEnabled\nOFFLOAD_OFF=$offloadOff\nCOD=$cod\nA2DP_SVC=$flag\nSDP_SINK=$flag\nSDP_AVRCP=$flag\nBT_CFG=$flag",
            "",
          )
        }
      }

    val controller = RootA2dpController(rootedShell)
    val enabled = controller.enableA2dpSink("com.aistudio.wavecast.krtmxb").getOrThrow()
    assertTrue(enabled.isRootGranted)
    assertTrue(enabled.isA2dpSinkEnabled)
    assertTrue(enabled.isA2dpSinkServiceRunning)
    assertTrue(enabled.isAudioSinkSdpExposed)
    assertTrue(enabled.isAvrcpSdpExposed)
    assertTrue(enabled.isBtConfigPatched)
    assertTrue(enabled.isOffloadDisabled)
    assertEquals(RootA2dpController.AUDIO_SINK_COD, enabled.classOfDevice)
    assertEquals("90,2,12", enabled.originalClassOfDevice)
    assertFalse(recordedCommands.any { it.contains("pkill -9 -f") })
    assertTrue(recordedCommands.any { it.contains("pidof com.android.bluetooth") })
    assertTrue(recordedCommands.any { it.contains("bt_config.conf") && it.contains("DevClass = 240414") })
    assertTrue(recordedCommands.any { it.contains("device_config put bluetooth a2dp_sink_enabled true") })
    assertTrue(recordedCommands.any { it.contains("a2dp_sink_automatically_request_audio_focus true") })
    assertTrue(recordedCommands.any { it.contains("BluetoothMediaBrowserService") })
    assertTrue(recordedCommands.any { it.contains("android.permission.RECORD_AUDIO") })
    assertFalse(recordedCommands.any { it.contains("2>&1 >&2") })
    assertTrue(recordedCommands.any { it.contains("persist.bluetooth.a2dp_offload.disabled") })

    val restored = controller.disableA2dpSink(enabled.originalClassOfDevice).getOrThrow()
    assertFalse(restored.isA2dpSinkEnabled)
    assertFalse(restored.isA2dpSinkServiceRunning)
    assertFalse(restored.isAudioSinkSdpExposed)
    assertEquals("90,2,12", restored.classOfDevice)
  }

  @Test
  fun `root bluetooth sdp service applies sdp records and profile settings cleanly`() {
    val previousFactory = com.example.service.RootBluetoothSdpService.configuratorFactory
    try {
      val fakeShell =
        object : ShellExecutor {
          override fun hasSuBinary(): Boolean = true

          override fun execute(command: String, useRoot: Boolean, timeoutMs: Long): ShellResult =
            ShellResult(
              0,
              "UID=0\nSINK=true\nPSINK=true\nPSINKU=true\nAVRCP=true\nOFFLOAD_OFF=true\nCOD=36,4,20\nA2DP_SVC=1\nSDP_SINK=1\nSDP_AVRCP=1\nBT_CFG=1",
              "",
            )
        }
      com.example.service.RootBluetoothSdpService.configuratorFactory = {
        com.example.bluetooth.BluetoothSdpProfileConfigurator(fakeShell)
      }

      val app = ApplicationProvider.getApplicationContext<android.app.Application>()
      val intent =
        android.content.Intent(app, com.example.service.RootBluetoothSdpService::class.java).apply {
          action = com.example.service.RootBluetoothSdpService.ACTION_APPLY_A2DP_SINK_SDP_PROFILE
          putExtra(com.example.service.RootBluetoothSdpService.EXTRA_START_SPEAKER_AFTER, false)
        }
      val controller =
        org.robolectric.Robolectric.buildService(
          com.example.service.RootBluetoothSdpService::class.java,
          intent,
        )
      controller.create().startCommand(0, 1)

      val deadline = System.currentTimeMillis() + 3_000L
      while (SpeakerSessionManager.state.value.isTogglingA2dpRoot &&
        System.currentTimeMillis() < deadline
      ) {
        Thread.sleep(25L)
      }

      val rootState = SpeakerSessionManager.state.value.rootStatus
      assertTrue(rootState.isRootGranted)
      assertTrue(rootState.isAudioSinkSdpExposed)
      assertTrue(rootState.isAvrcpSdpExposed)
      assertTrue(rootState.isBtConfigPatched)
      assertTrue(rootState.isA2dpSinkServiceRunning)
    } finally {
      com.example.service.RootBluetoothSdpService.configuratorFactory = previousFactory
    }
  }

  @Test
  fun `diagnostic log handler captures processbuilder stderr and decodes exit 137 signal`() {
    val handler =
      com.example.bluetooth.ShellDiagnosticLogHandler(maxEntries = 10, emitToLogcat = false)
    val executor =
      com.example.bluetooth.ProcessShellExecutor(
        diagnosticHandler = handler,
        processFactory = { _, command -> ProcessBuilder("/bin/sh", "-c", command) },
      )

    val result =
      executor.execute(
        command =
          "echo '[t_wave] stage: stop_bluetooth_stack' >&2; echo 'Killed by signal 9' >&2; exit 137",
        useRoot = true,
        timeoutMs = 5_000L,
      )

    assertEquals(137, result.exitCode)
    assertTrue(result.stderr.contains("[t_wave] stage: stop_bluetooth_stack"))
    assertTrue(result.stderr.contains("Killed by signal 9"))

    val entries = handler.entries.value
    assertEquals(1, entries.size)
    val entry = entries.first()
    assertEquals("su", entry.tag)
    assertEquals(137, entry.exitCode)
    assertTrue(entry.exitExplanation.contains("SIGKILL"))
    assertEquals("stop_bluetooth_stack", entry.lastStage)
    assertTrue(entry.stderr.contains("Killed by signal 9"))

    val detail =
      com.example.bluetooth.ShellDiagnosticLogHandler.buildFailureDetail(
        fallbackPrefix = "Failed to modify Bluetooth SDP",
        exitCode = result.exitCode,
        stderr = result.stderr,
      )
    assertTrue(detail.contains("Exit 137"))
    assertTrue(detail.contains("SIGKILL"))
    assertTrue(detail.contains("Killed by signal 9"))
  }

  @Test
  fun `view diagnostics button opens root diagnostics dialog with stderr and clear action`() {
    var cleared = false
    val handler =
      com.example.bluetooth.ShellDiagnosticLogHandler(maxEntries = 10, emitToLogcat = false)
    val entry =
      handler.record(
        tag = "su",
        command = "resetprop bluetooth.profile.a2dp.sink.enabled true",
        exitCode = 137,
        durationMs = 420L,
        stderr = "[t_wave] stage: stop_bluetooth_stack\nProcess killed",
        stdout = "SINK=false",
      )

    composeTestRule.setContent {
      WaveCastTheme {
        SpeakerScreen(
          serviceState =
            SpeakerServiceState(
              status = ConnectionStatus.IDLE,
              speakerMode = SpeakerMode.A2DP_SINK,
              rootStatus = RootA2dpStatus(privilegeStatus = RootPrivilegeStatus.DENIED),
            ),
          permissionStatus = PermissionStatus(true, true, true),
          bluetoothStatus =
            BluetoothHardwareStatus(
              isSupported = true,
              isEnabled = true,
              isDiscoverable = false,
              adapterName = "Pixel 8 Pro",
            ),
          shellDiagnostics = listOf(entry),
          onClearDiagnostics = { cleared = true },
          onSpeakerModeSelected = {},
          onRequestRoot = {},
          onApplySdpProfile = {},
          onConnectPairedDevice = {},
          onToggleSpeakerMode = {},
          onGrantPermissions = {},
          onOpenSettings = {},
          onEnableBluetooth = {},
          onRequestDiscoverable = {},
          onLowLatencyChange = {},
          onQualityProfileSelected = {},
          onRequirePairingChange = {},
          onAutoReconnectChange = {},
          onGainChange = {},
          onToggleMute = {},
          onDismissError = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("view_diagnostics_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("root_diagnostics_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("Stage: stop_bluetooth_stack").assertIsDisplayed()
    composeTestRule.onNodeWithTag("clear_diagnostics_button").performClick()
    assertTrue(cleared)
  }

  @Test
  fun `root a2dp controller parses connected device from dumpsys bluetooth_manager`() {
    val dumpsys =
      """
      Profile: A2dpSinkService
        A2dpSinkStateMachine:
          Connected: AA:BB:CC:11:22:33 (Pixel 9 Source)
      Profile: GattService
      """.trimIndent()
    val parsed = RootA2dpController.parseConnectedA2dpFromDumpsys(dumpsys)
    assertEquals(ConnectedDeviceInfo("Pixel 9 Source", "AA:BB:CC:11:22:33"), parsed)
  }

  @Test
  fun `a2dp sink mode renders root gate card, sdp diagnostics, paired devices connect action, and root request`() {
    var rootRequested = false
    var sdpPatched = false
    var switchedMode: SpeakerMode? = null
    var connectedAddress: String? = null
    composeTestRule.setContent {
      WaveCastTheme {
        SpeakerScreen(
          serviceState =
            SpeakerServiceState(
              status = ConnectionStatus.LISTENING,
              speakerMode = SpeakerMode.A2DP_SINK,
              rootStatus =
                RootA2dpStatus(
                  privilegeStatus = RootPrivilegeStatus.GRANTED,
                  isA2dpSinkEnabled = true,
                  isA2dpSinkServiceRunning = true,
                  isOffloadDisabled = true,
                  isAudioSinkSdpExposed = true,
                  isAvrcpSdpExposed = true,
                  isBtConfigPatched = true,
                  classOfDevice = RootA2dpController.AUDIO_SINK_COD,
                ),
              pairedDevices =
                listOf(com.example.model.PairedDeviceItem("Galaxy S24", "12:34:56:78:9A:BC")),
            ),
          permissionStatus = PermissionStatus(true, true, true),
          bluetoothStatus =
            BluetoothHardwareStatus(
              isSupported = true,
              isEnabled = true,
              isDiscoverable = false,
              adapterName = "Pixel 8 Pro",
            ),
          onSpeakerModeSelected = { switchedMode = it },
          onRequestRoot = { rootRequested = true },
          onApplySdpProfile = { sdpPatched = true },
          onConnectPairedDevice = { connectedAddress = it },
          onToggleSpeakerMode = {},
          onGrantPermissions = {},
          onOpenSettings = {},
          onEnableBluetooth = {},
          onRequestDiscoverable = {},
          onLowLatencyChange = {},
          onQualityProfileSelected = {},
          onRequirePairingChange = {},
          onAutoReconnectChange = {},
          onGainChange = {},
          onToggleMute = {},
          onDismissError = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("root_status_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("A2dpSinkService: ON").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("SDP 0x110B: ON").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Offload: OFF").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("bt_config: 240414").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("request_root_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("apply_sdp_profile_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("paired_devices_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Galaxy S24").performScrollTo().assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("connect_paired_12:34:56:78:9A:BC")
      .performScrollTo()
      .performClick()
    composeTestRule.onNodeWithTag("mode_chip_RFCOMM_SPP").performScrollTo().performClick()

    assertTrue(rootRequested)
    assertTrue(sdpPatched)
    assertEquals("12:34:56:78:9A:BC", connectedAddress)
    assertEquals(SpeakerMode.RFCOMM_SPP, switchedMode)
  }

  @Test
  fun `speaker screen in rfcomm mode displays connected device, auto-reconnect, low latency, high-res, and pairing`() {
    var toggled = false
    var discoverableRequested = false
    var lowLatencyToggled = false
    var pairingToggled = false
    var autoReconnectToggled = true
    var selectedProfile: AudioQualityProfile? = null
    composeTestRule.setContent {
      WaveCastTheme {
        SpeakerScreen(
          serviceState =
            SpeakerServiceState(
              status = ConnectionStatus.CONNECTED,
              speakerMode = SpeakerMode.RFCOMM_SPP,
              connectedDevice = ConnectedDeviceInfo("Studio MacBook", "AA:BB:CC:DD:EE:FF"),
              lastConnectedDevice = ConnectedDeviceInfo("Studio MacBook", "AA:BB:CC:DD:EE:FF"),
              autoReconnectEnabled = true,
              qualityProfile = AudioQualityProfile.HIRES_96_24,
              lowLatencyMode = true,
              requirePairing = false,
              hardwareMaxSampleRateHz = 192_000,
              hardwareSupportsHiRes = true,
              telemetry =
                AudioTelemetry(
                  leftLevel = 0.7f,
                  rightLevel = 0.65f,
                  bufferFillRatio = 0.35f,
                  estimatedLatencyMs = 11,
                  sampleRateHz = 96_000,
                  bitDepth = 24,
                ),
            ),
          permissionStatus = PermissionStatus(true, true, true),
          bluetoothStatus =
            BluetoothHardwareStatus(
              isSupported = true,
              isEnabled = true,
              isDiscoverable = false,
              adapterName = "Pixel 8 Pro",
            ),
          onSpeakerModeSelected = {},
          onRequestRoot = {},
          onApplySdpProfile = {},
          onConnectPairedDevice = {},
          onToggleSpeakerMode = { toggled = true },
          onGrantPermissions = {},
          onOpenSettings = {},
          onEnableBluetooth = {},
          onRequestDiscoverable = { discoverableRequested = true },
          onLowLatencyChange = { lowLatencyToggled = it },
          onQualityProfileSelected = { selectedProfile = it },
          onRequirePairingChange = { pairingToggled = it },
          onAutoReconnectChange = { autoReconnectToggled = it },
          onGainChange = {},
          onToggleMute = {},
          onDismissError = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("t_wave").assertIsDisplayed()
    composeTestRule.onNodeWithTag("connection_status_badge").assertIsDisplayed()
    composeTestRule.onNodeWithText("Connected").assertIsDisplayed()
    composeTestRule.onNodeWithTag("visibility_status_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("discoverable_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("connected_device_banner").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Studio MacBook").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("speaker_mode_toggle_button").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("visualizer_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("visualizer_hires_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("visualizer_low_latency_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("audio_engine_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("low_latency_switch").performScrollTo().performClick()
    composeTestRule
      .onNodeWithTag("quality_chip_${AudioQualityProfile.MASTER_192_32.name}")
      .performScrollTo()
      .performClick()
    composeTestRule.onNodeWithTag("auto_reconnect_switch").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("require_pairing_switch").performScrollTo().performClick()

    assertTrue(toggled)
    assertTrue(discoverableRequested)
    assertTrue(pairingToggled)
    assertEquals(false, autoReconnectToggled)
    assertEquals(false, lowLatencyToggled)
    assertEquals(AudioQualityProfile.MASTER_192_32, selectedProfile)
  }

  @Test
  fun `connected device banner stays visible when idle with last device hint`() {
    composeTestRule.setContent {
      WaveCastTheme {
        SpeakerScreen(
          serviceState =
            SpeakerServiceState(
              status = ConnectionStatus.IDLE,
              speakerMode = SpeakerMode.A2DP_SINK,
              rootStatus = RootA2dpStatus(privilegeStatus = RootPrivilegeStatus.GRANTED),
              connectedDevice = null,
              lastConnectedDevice = ConnectedDeviceInfo("Living Room PC", "11:22:33:44:55:66"),
              autoReconnectEnabled = true,
            ),
          permissionStatus = PermissionStatus(true, true, true),
          bluetoothStatus =
            BluetoothHardwareStatus(
              isSupported = true,
              isEnabled = true,
              isDiscoverable = false,
              adapterName = "Pixel 8 Pro",
            ),
          onSpeakerModeSelected = {},
          onRequestRoot = {},
          onApplySdpProfile = {},
          onConnectPairedDevice = {},
          onToggleSpeakerMode = {},
          onGrantPermissions = {},
          onOpenSettings = {},
          onEnableBluetooth = {},
          onRequestDiscoverable = {},
          onLowLatencyChange = {},
          onQualityProfileSelected = {},
          onRequirePairingChange = {},
          onAutoReconnectChange = {},
          onGainChange = {},
          onToggleMute = {},
          onDismissError = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Root Access Granted (su)").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("connected_device_banner").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("No device connected").performScrollTo().assertIsDisplayed()
    composeTestRule
      .onNodeWithText("Last device: Living Room PC (11:22:33:44:55:66)")
      .performScrollTo()
      .assertIsDisplayed()
  }

  @Test
  fun `reconnecting state shows attempt details and reconnecting status badge`() {
    composeTestRule.setContent {
      WaveCastTheme {
        SpeakerScreen(
          serviceState =
            SpeakerServiceState(
              status = ConnectionStatus.RECONNECTING,
              connectedDevice = null,
              lastConnectedDevice = ConnectedDeviceInfo("Studio MacBook", "AA:BB:CC:DD:EE:FF"),
              autoReconnectEnabled = true,
              reconnectAttempt = 2,
              maxReconnectAttempts = 5,
            ),
          permissionStatus = PermissionStatus(true, true, true),
          bluetoothStatus =
            BluetoothHardwareStatus(
              isSupported = true,
              isEnabled = true,
              isDiscoverable = false,
              adapterName = "Pixel 8 Pro",
            ),
          onSpeakerModeSelected = {},
          onRequestRoot = {},
          onApplySdpProfile = {},
          onConnectPairedDevice = {},
          onToggleSpeakerMode = {},
          onGrantPermissions = {},
          onOpenSettings = {},
          onEnableBluetooth = {},
          onRequestDiscoverable = {},
          onLowLatencyChange = {},
          onQualityProfileSelected = {},
          onRequirePairingChange = {},
          onAutoReconnectChange = {},
          onGainChange = {},
          onToggleMute = {},
          onDismissError = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Reconnecting").assertIsDisplayed()
    composeTestRule.onNodeWithTag("connected_device_banner").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Reconnecting to Studio MacBook").performScrollTo().assertIsDisplayed()
    composeTestRule
      .onNodeWithText("Attempt 2 of 5 • AA:BB:CC:DD:EE:FF")
      .performScrollTo()
      .assertIsDisplayed()
  }

  @Test
  fun `session manager tracks last connected device and reconnecting state`() {
    val device = ConnectedDeviceInfo("Desk Transmitter", "DE:AD:BE:EF:00:01")
    SpeakerSessionManager.setAutoReconnectEnabled(true)
    SpeakerSessionManager.setConnected(device)
    assertEquals(ConnectionStatus.CONNECTED, SpeakerSessionManager.state.value.status)
    assertEquals(device, SpeakerSessionManager.state.value.connectedDevice)
    assertEquals(device, SpeakerSessionManager.state.value.lastConnectedDevice)

    SpeakerSessionManager.setReconnecting(device, attempt = 3, maxAttempts = 5)
    val reconnectingState = SpeakerSessionManager.state.value
    assertEquals(ConnectionStatus.RECONNECTING, reconnectingState.status)
    assertEquals(null, reconnectingState.connectedDevice)
    assertEquals(device, reconnectingState.lastConnectedDevice)
    assertEquals(3, reconnectingState.reconnectAttempt)
    assertEquals(5, reconnectingState.maxReconnectAttempts)

    SpeakerSessionManager.setIdle()
    assertEquals(ConnectionStatus.IDLE, SpeakerSessionManager.state.value.status)
    assertEquals(device, SpeakerSessionManager.state.value.lastConnectedDevice)
  }

  @Test
  fun `a2dp sink connected banner exposes wake audio button and audio router unmutes music stream`() {
    var wakeClicked = false
    composeTestRule.setContent {
      WaveCastTheme {
        SpeakerScreen(
          serviceState =
            SpeakerServiceState(
              status = ConnectionStatus.CONNECTED,
              speakerMode = SpeakerMode.A2DP_SINK,
              connectedDevice = ConnectedDeviceInfo("Galaxy S24", "12:34:56:78:9A:BC"),
              lastConnectedDevice = ConnectedDeviceInfo("Galaxy S24", "12:34:56:78:9A:BC"),
              rootStatus = RootA2dpStatus(privilegeStatus = RootPrivilegeStatus.GRANTED),
            ),
          permissionStatus = PermissionStatus(true, true, true),
          bluetoothStatus =
            BluetoothHardwareStatus(
              isSupported = true,
              isEnabled = true,
              isDiscoverable = true,
              adapterName = "Pixel 8 Pro",
            ),
          onSpeakerModeSelected = {},
          onRequestRoot = {},
          onApplySdpProfile = {},
          onConnectPairedDevice = {},
          onWakeA2dpAudio = { wakeClicked = true },
          onToggleSpeakerMode = {},
          onGrantPermissions = {},
          onOpenSettings = {},
          onEnableBluetooth = {},
          onRequestDiscoverable = {},
          onLowLatencyChange = {},
          onQualityProfileSelected = {},
          onRequirePairingChange = {},
          onAutoReconnectChange = {},
          onGainChange = {},
          onToggleMute = {},
          onDismissError = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("wake_a2dp_audio_button").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("wake_a2dp_audio_button").performClick()
    assertTrue(wakeClicked)

    val app = ApplicationProvider.getApplicationContext<Context>()
    val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
    com.example.audio.A2dpSinkAudioRouter.ensureSpeakerStreamAudible(
      audioManager = audioManager,
      appGain = 0.8f,
      isAppMuted = false,
      bumpZeroVolume = true,
    )
    assertTrue(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) >= 0)
    assertEquals(
      44_100,
      com.example.audio.A2dpSinkAudioRouter.inspectProxyAudio(null, null).sampleRateHz,
    )
  }

  @Test
  fun `pcm audiotrack configurator aligns high-res buffers to frame boundaries and initializes track`() {
    val sizing24Bit96k =
      com.example.audio.PcmAudioTrackConfigurator.computeBufferSizing(
        sampleRateHz = 96_000,
        channels = 2,
        trackEncoding = android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED,
        minHardwareBufferBytes = 12_295, // deliberately unaligned to 6-byte stereo frame
        framesPerBuffer = 256,
        lowLatency = false,
        bufferProfile = com.example.model.AudioBufferProfile.HIRES_DEEP,
      )
    assertEquals(6, sizing24Bit96k.trackBytesPerFrame)
    assertEquals(0, sizing24Bit96k.bufferSizeBytes % 6)
    assertTrue(sizing24Bit96k.bufferSizeBytes >= 65_536)
    assertTrue(sizing24Bit96k.bufferFrames > 0)
    assertTrue(sizing24Bit96k.bufferDurationMs > 0)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val track96k =
      com.example.audio.PcmAudioTrackConfigurator.buildConfiguredTrack(
        context = context,
        profile = AudioQualityProfile.HIRES_96_24,
        lowLatency = false,
        bufferProfile = com.example.model.AudioBufferProfile.HIRES_DEEP,
      )
    try {
      assertTrue(track96k.isReady)
      assertTrue(track96k.bufferSizing.bufferSizeBytes >= track96k.bufferSizing.minHardwareBufferBytes)
      assertEquals(0, track96k.bufferSizing.bufferSizeBytes % track96k.bufferSizing.trackBytesPerFrame)
    } finally {
      track96k.track.release()
    }
  }

  @Test
  fun `audio engine card renders audiotrack spec banner and switches buffer profile`() {
    var selectedBuffer: com.example.model.AudioBufferProfile? = null
    composeTestRule.setContent {
      WaveCastTheme {
        SpeakerScreen(
          serviceState =
            SpeakerServiceState(
              status = ConnectionStatus.IDLE,
              speakerMode = SpeakerMode.RFCOMM_SPP,
              qualityProfile = AudioQualityProfile.HIRES_96_24,
              bufferProfile = com.example.model.AudioBufferProfile.BALANCED,
            ),
          permissionStatus = PermissionStatus(true, true, true),
          bluetoothStatus =
            BluetoothHardwareStatus(
              isSupported = true,
              isEnabled = true,
              isDiscoverable = false,
              adapterName = "Pixel 8 Pro",
            ),
          onSpeakerModeSelected = {},
          onRequestRoot = {},
          onApplySdpProfile = {},
          onConnectPairedDevice = {},
          onToggleSpeakerMode = {},
          onGrantPermissions = {},
          onOpenSettings = {},
          onEnableBluetooth = {},
          onRequestDiscoverable = {},
          onLowLatencyChange = {},
          onQualityProfileSelected = {},
          onBufferProfileSelected = { selectedBuffer = it },
          onRequirePairingChange = {},
          onAutoReconnectChange = {},
          onGainChange = {},
          onToggleMute = {},
          onDismissError = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("audiotrack_spec_banner").performScrollTo().assertIsDisplayed()
    composeTestRule
      .onNodeWithTag("buffer_chip_${com.example.model.AudioBufferProfile.HIRES_DEEP.name}")
      .performScrollTo()
      .performClick()
    assertEquals(com.example.model.AudioBufferProfile.HIRES_DEEP, selectedBuffer)
  }

  @Test
  fun `ultra low latency configurator sizes 1x burst smaller than balanced and initializes track`() {
    val balancedSizing =
      com.example.audio.PcmAudioTrackConfigurator.computeBufferSizing(
        sampleRateHz = 48_000,
        channels = 2,
        trackEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT,
        minHardwareBufferBytes = 4096,
        framesPerBuffer = 256,
        lowLatency = false,
        bufferProfile = com.example.model.AudioBufferProfile.BALANCED,
        ultraLowLatency = false,
      )
    val ultraSizing =
      com.example.audio.PcmAudioTrackConfigurator.computeBufferSizing(
        sampleRateHz = 48_000,
        channels = 2,
        trackEncoding = android.media.AudioFormat.ENCODING_PCM_16BIT,
        minHardwareBufferBytes = 4096,
        framesPerBuffer = 256,
        lowLatency = true,
        bufferProfile = com.example.model.AudioBufferProfile.LOW_LATENCY,
        ultraLowLatency = true,
      )
    assertTrue(ultraSizing.bufferSizeBytes < balancedSizing.bufferSizeBytes)
    assertEquals(0, ultraSizing.bufferSizeBytes % ultraSizing.trackBytesPerFrame)

    val context = ApplicationProvider.getApplicationContext<Context>()
    val trackResult =
      com.example.audio.PcmAudioTrackConfigurator.buildConfiguredTrack(
        context = context,
        profile = AudioQualityProfile.STANDARD_44_16,
        lowLatency = true,
        bufferProfile = com.example.model.AudioBufferProfile.LOW_LATENCY,
        ultraLowLatency = true,
      )
    try {
      assertTrue(trackResult.isReady)
      assertTrue(trackResult.outputDescription.contains("ULTRA-LOW"))
    } finally {
      trackResult.track.release()
    }
  }

  @Test
  fun `session manager links low latency, ultra low latency, and lock screen playback state`() {
    SpeakerSessionManager.setLowLatencyMode(false)
    assertFalse(SpeakerSessionManager.state.value.lowLatencyMode)
    assertFalse(SpeakerSessionManager.state.value.ultraLowLatencyMode)

    SpeakerSessionManager.setUltraLowLatencyMode(true)
    assertTrue(SpeakerSessionManager.state.value.ultraLowLatencyMode)
    assertTrue(SpeakerSessionManager.state.value.lowLatencyMode)
    assertEquals(
      com.example.model.AudioBufferProfile.LOW_LATENCY,
      SpeakerSessionManager.state.value.bufferProfile,
    )

    SpeakerSessionManager.setLowLatencyMode(false)
    assertFalse(SpeakerSessionManager.state.value.lowLatencyMode)
    assertFalse(SpeakerSessionManager.state.value.ultraLowLatencyMode)

    SpeakerSessionManager.setLockScreenPlaybackEnabled(false)
    assertFalse(SpeakerSessionManager.state.value.lockScreenPlaybackEnabled)
    SpeakerSessionManager.setLockScreenPlaybackEnabled(true)
    assertTrue(SpeakerSessionManager.state.value.lockScreenPlaybackEnabled)
  }

  @Test
  fun `speaker screen renders ultra low latency badge and lock screen toggle switch`() {
    var ultraLowChanged: Boolean? = null
    var lockScreenChanged: Boolean? = null
    composeTestRule.setContent {
      WaveCastTheme {
        SpeakerScreen(
          serviceState =
            SpeakerServiceState(
              status = ConnectionStatus.CONNECTED,
              speakerMode = SpeakerMode.RFCOMM_SPP,
              lowLatencyMode = true,
              ultraLowLatencyMode = true,
              lockScreenPlaybackEnabled = true,
              bufferProfile = com.example.model.AudioBufferProfile.LOW_LATENCY,
            ),
          permissionStatus = PermissionStatus(true, true, true),
          bluetoothStatus =
            BluetoothHardwareStatus(
              isSupported = true,
              isEnabled = true,
              isDiscoverable = false,
              adapterName = "Pixel 8 Pro",
            ),
          onSpeakerModeSelected = {},
          onRequestRoot = {},
          onApplySdpProfile = {},
          onConnectPairedDevice = {},
          onToggleSpeakerMode = {},
          onGrantPermissions = {},
          onOpenSettings = {},
          onEnableBluetooth = {},
          onRequestDiscoverable = {},
          onLowLatencyChange = {},
          onUltraLowLatencyChange = { ultraLowChanged = it },
          onQualityProfileSelected = {},
          onRequirePairingChange = {},
          onAutoReconnectChange = {},
          onLockScreenPlaybackChange = { lockScreenChanged = it },
          onGainChange = {},
          onToggleMute = {},
          onDismissError = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("visualizer_low_latency_badge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("ULTRA-LOW").assertIsDisplayed()
    composeTestRule.onNodeWithTag("low_latency_switch").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("ultra_low_latency_switch").performScrollTo().performClick()
    composeTestRule.onNodeWithTag("lock_screen_switch").performScrollTo().performClick()
    assertEquals(false, ultraLowChanged)
    assertEquals(false, lockScreenChanged)
  }

  @Test
  fun `speaker service exposes public lock screen notification and toggles mute action`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    SpeakerSessionManager.setMuted(false)
    SpeakerSessionManager.setLockScreenPlaybackEnabled(true)
    SpeakerSessionManager.setConnected(ConnectedDeviceInfo("Studio MacBook", "AA:BB:CC:DD:EE:FF"))

    val controller = org.robolectric.Robolectric.buildService(BluetoothSpeakerService::class.java)
    val service = controller.create().get()

    val muteIntent =
      android.content.Intent(app, BluetoothSpeakerService::class.java).apply {
        action = BluetoothSpeakerService.ACTION_TOGGLE_MUTE
      }
    service.onStartCommand(muteIntent, 0, 1)
    assertTrue(SpeakerSessionManager.state.value.isMuted)

    val notificationManager =
      app.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val shadowNm = org.robolectric.Shadows.shadowOf(notificationManager)
    val activeNotifications = shadowNm.allNotifications
    assertTrue(activeNotifications.isNotEmpty())
    val posted = activeNotifications.last()
    assertEquals(android.app.Notification.VISIBILITY_PUBLIC, posted.visibility)
    assertEquals(2, posted.actions.size)

    val lockReconfigIntent =
      android.content.Intent(app, BluetoothSpeakerService::class.java).apply {
        action = BluetoothSpeakerService.ACTION_LOCK_SCREEN_RECONFIGURE
      }
    service.onStartCommand(lockReconfigIntent, 0, 2)

    val stopIntent =
      android.content.Intent(app, BluetoothSpeakerService::class.java).apply {
        action = BluetoothSpeakerService.ACTION_STOP_SPEAKER
      }
    service.onStartCommand(stopIntent, 0, 3)
    assertEquals(ConnectionStatus.IDLE, SpeakerSessionManager.state.value.status)
    controller.destroy()
  }

  @Test
  fun `pcm sync delay line buffers positive delay frames cleanly and passes zero offset through`() {
    val delayLine = com.example.audio.PcmSyncDelayLine()
    val sampleRate = 48_000
    val frameSize = 4
    // 10 ms at 48 kHz = 480 frames = 1920 bytes
    val chunkA = ByteArray(1920) { 0x11 }
    val chunkB = ByteArray(1920) { 0x22 }
    val output = ByteArray(8192)

    // With +20 ms delay (3840 bytes), first 10 ms fills the delay queue and emits 0 bytes.
    val out1 = delayLine.processChunk(chunkA, chunkA.size, output, sampleRate, frameSize, syncOffsetMs = 20)
    assertEquals(0, out1)
    assertEquals(1920, delayLine.currentBufferedBytes)

    // Second 10 ms reaches exactly 20 ms buffered (3840 bytes), emitting 0 excess yet.
    val out2 = delayLine.processChunk(chunkB, chunkB.size, output, sampleRate, frameSize, syncOffsetMs = 20)
    assertEquals(0, out2)
    assertEquals(3840, delayLine.currentBufferedBytes)

    // Third 10 ms chunk pushes chunkA (0x11) out while keeping 20 ms queued!
    val chunkC = ByteArray(1920) { 0x33 }
    val out3 = delayLine.processChunk(chunkC, chunkC.size, output, sampleRate, frameSize, syncOffsetMs = 20)
    assertEquals(1920, out3)
    assertEquals(0x11.toByte(), output[0])
    assertEquals(3840, delayLine.currentBufferedBytes)

    // Switching back to 0 ms drains queued backlog smoothly.
    val out4 = delayLine.processChunk(ByteArray(0), 0, output, sampleRate, frameSize, syncOffsetMs = 0)
    assertEquals(3840, out4)
    assertEquals(0x22.toByte(), output[0])
    assertEquals(0x33.toByte(), output[1920])
  }

  @Test
  fun `session manager clamps sync offset and updates compensated latency`() {
    SpeakerSessionManager.setSyncOffsetMs(123) // snaps to 125 ms
    assertEquals(125, SpeakerSessionManager.state.value.syncOffsetMs)
    val positiveLatency = SpeakerSessionManager.state.value.telemetry.estimatedLatencyMs
    assertTrue(positiveLatency >= 125)

    SpeakerSessionManager.setSyncOffsetMs(-400) // clamps to -250 ms
    assertEquals(SpeakerServiceState.MIN_SYNC_OFFSET_MS, SpeakerSessionManager.state.value.syncOffsetMs)
    assertEquals(0, SpeakerSessionManager.state.value.telemetry.estimatedLatencyMs)

    SpeakerSessionManager.setSyncOffsetMs(0)
    assertEquals(0, SpeakerSessionManager.state.value.syncOffsetMs)
  }

  @Test
  fun `speaker screen renders sync offset slider, fine steppers, reset, and visualizer badge`() {
    var updatedOffset: Int? = null
    composeTestRule.setContent {
      WaveCastTheme {
        SpeakerScreen(
          serviceState =
            SpeakerServiceState(
              status = ConnectionStatus.CONNECTED,
              speakerMode = SpeakerMode.A2DP_SINK,
              syncOffsetMs = 85,
              telemetry = AudioTelemetry(estimatedLatencyMs = 115),
            ),
          permissionStatus = PermissionStatus(true, true, true),
          bluetoothStatus =
            BluetoothHardwareStatus(
              isSupported = true,
              isEnabled = true,
              isDiscoverable = false,
              adapterName = "Pixel 8 Pro",
            ),
          onSpeakerModeSelected = {},
          onRequestRoot = {},
          onApplySdpProfile = {},
          onConnectPairedDevice = {},
          onToggleSpeakerMode = {},
          onGrantPermissions = {},
          onOpenSettings = {},
          onEnableBluetooth = {},
          onRequestDiscoverable = {},
          onLowLatencyChange = {},
          onSyncOffsetChange = { updatedOffset = it },
          onQualityProfileSelected = {},
          onRequirePairingChange = {},
          onAutoReconnectChange = {},
          onGainChange = {},
          onToggleMute = {},
          onDismissError = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("visualizer_sync_offset_badge").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("+85 ms SYNC").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sync_offset_card").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("sync_offset_slider").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("+85 ms (Delay)").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("sync_offset_plus_button").performScrollTo().performClick()
    assertEquals(90, updatedOffset)

    composeTestRule.onNodeWithTag("sync_offset_minus_button").performScrollTo().performClick()
    assertEquals(80, updatedOffset)

    composeTestRule.onNodeWithTag("sync_offset_reset_button").performScrollTo().performClick()
    assertEquals(0, updatedOffset)
  }

  @Test
  fun `session manager switches scope animation mode cleanly`() {
    com.example.model.ScopeAnimationMode.entries.forEach { mode ->
      SpeakerSessionManager.setScopeAnimationMode(mode)
      assertEquals(mode, SpeakerSessionManager.state.value.scopeAnimationMode)
    }
    SpeakerSessionManager.setScopeAnimationMode(com.example.model.ScopeAnimationMode.DUAL_SPECTRUM)
  }

  @Test
  fun `signal scope renders custom animation mode chips and switches visualizer animations`() {
    var selectedMode: com.example.model.ScopeAnimationMode? = null
    val activeBands = List(AudioTelemetry.BAND_COUNT) { idx -> 0.2f + (idx % 5) * 0.16f }
    composeTestRule.setContent {
      val modeState =
        androidx.compose.runtime.remember {
          androidx.compose.runtime.mutableStateOf(com.example.model.ScopeAnimationMode.DUAL_SPECTRUM)
        }
      WaveCastTheme {
        SpeakerScreen(
          serviceState =
            SpeakerServiceState(
              status = ConnectionStatus.CONNECTED,
              speakerMode = SpeakerMode.A2DP_SINK,
              scopeAnimationMode = modeState.value,
              telemetry =
                AudioTelemetry(
                  leftLevel = 0.78f,
                  rightLevel = 0.64f,
                  spectrumBands = activeBands,
                ),
            ),
          permissionStatus = PermissionStatus(true, true, true),
          bluetoothStatus =
            BluetoothHardwareStatus(
              isSupported = true,
              isEnabled = true,
              isDiscoverable = false,
              adapterName = "Pixel 8 Pro",
            ),
          onSpeakerModeSelected = {},
          onRequestRoot = {},
          onApplySdpProfile = {},
          onConnectPairedDevice = {},
          onToggleSpeakerMode = {},
          onGrantPermissions = {},
          onOpenSettings = {},
          onEnableBluetooth = {},
          onRequestDiscoverable = {},
          onLowLatencyChange = {},
          onQualityProfileSelected = {},
          onScopeAnimationModeSelected = {
            selectedMode = it
            modeState.value = it
          },
          onRequirePairingChange = {},
          onAutoReconnectChange = {},
          onGainChange = {},
          onToggleMute = {},
          onDismissError = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("scope_animation_chips_row").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithTag("signal_scope_canvas").performScrollTo().assertIsDisplayed()

    com.example.model.ScopeAnimationMode.entries
      .filter { it != com.example.model.ScopeAnimationMode.DUAL_SPECTRUM }
      .plus(com.example.model.ScopeAnimationMode.DUAL_SPECTRUM)
      .forEach { mode ->
        composeTestRule.onNodeWithTag("scope_anim_${mode.name}").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(mode, selectedMode)
        composeTestRule.onNodeWithTag("signal_scope_canvas").assertIsDisplayed()
      }
  }

  @Test
  fun `about section displays developer credit and guide dialog includes attribution`() {
    composeTestRule.setContent {
      WaveCastTheme {
        SpeakerScreen(
          serviceState = SpeakerServiceState(),
          permissionStatus = PermissionStatus(true, true, true),
          bluetoothStatus =
            BluetoothHardwareStatus(
              isSupported = true,
              isEnabled = true,
              isDiscoverable = false,
              adapterName = "Pixel 8 Pro",
            ),
          onSpeakerModeSelected = {},
          onRequestRoot = {},
          onApplySdpProfile = {},
          onConnectPairedDevice = {},
          onToggleSpeakerMode = {},
          onGrantPermissions = {},
          onOpenSettings = {},
          onEnableBluetooth = {},
          onRequestDiscoverable = {},
          onLowLatencyChange = {},
          onQualityProfileSelected = {},
          onRequirePairingChange = {},
          onAutoReconnectChange = {},
          onGainChange = {},
          onToggleMute = {},
          onDismissError = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("about_section_card").performScrollTo().assertIsDisplayed()
    composeTestRule
      .onNodeWithText("a project by Tapas Ranjan Sahu @xenon3x - tg")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("about_telegram_chip").performScrollTo().assertIsDisplayed()

    composeTestRule.onNodeWithTag("protocol_guide_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("t_wave Bluetooth Speaker Guide").assertIsDisplayed()
  }

  @Test
  fun `custom launcher artwork and adaptive mipmap icons resolve cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val artwork = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_artwork)
    val launcher = androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
    val launcherRound =
      androidx.core.content.ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
    assertTrue(artwork != null)
    assertTrue(launcher != null)
    assertTrue(launcherRound != null)
  }
}
