package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.audio.PcmSyncDelayLine
import com.example.model.AudioBufferProfile
import com.example.model.AudioQualityProfile
import com.example.model.ConnectedDeviceInfo
import com.example.model.ScopeAnimationMode
import com.example.model.SpeakerMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.speakerDataStore by preferencesDataStore(name = "twave_speaker_prefs")

class SpeakerPreferences(private val context: Context) {

  data class SavedSettings(
    val autoReconnectEnabled: Boolean = true,
    val speakerMode: SpeakerMode = SpeakerMode.A2DP_SINK,
    val qualityProfile: AudioQualityProfile = AudioQualityProfile.STANDARD_44_16,
    val bufferProfile: AudioBufferProfile = AudioBufferProfile.BALANCED,
    val scopeAnimationMode: ScopeAnimationMode = ScopeAnimationMode.DUAL_SPECTRUM,
    val lowLatencyMode: Boolean = false,
    val ultraLowLatencyMode: Boolean = false,
    val syncOffsetMs: Int = 0,
    val lockScreenPlaybackEnabled: Boolean = true,
    val originalClassOfDevice: String? = null,
    val lastDevice: ConnectedDeviceInfo? = null,
  )

  val settingsFlow: Flow<SavedSettings> =
    context.speakerDataStore.data.map { prefs ->
      val autoReconnect = prefs[KEY_AUTO_RECONNECT] ?: true
      val modeName = prefs[KEY_SPEAKER_MODE]
      val speakerMode =
        SpeakerMode.entries.firstOrNull { it.name == modeName } ?: SpeakerMode.A2DP_SINK
      val qualityName = prefs[KEY_QUALITY_PROFILE]
      val qualityProfile =
        AudioQualityProfile.entries.firstOrNull { it.name == qualityName }
          ?: AudioQualityProfile.STANDARD_44_16
      val bufferName = prefs[KEY_BUFFER_PROFILE]
      val bufferProfile =
        AudioBufferProfile.entries.firstOrNull { it.name == bufferName }
          ?: AudioBufferProfile.BALANCED
      val scopeAnimName = prefs[KEY_SCOPE_ANIMATION_MODE]
      val scopeAnimationMode =
        ScopeAnimationMode.entries.firstOrNull { it.name == scopeAnimName }
          ?: ScopeAnimationMode.DUAL_SPECTRUM
      val lowLatency = prefs[KEY_LOW_LATENCY] ?: false
      val ultraLowLatency = prefs[KEY_ULTRA_LOW_LATENCY] ?: false
      val syncOffsetMs = PcmSyncDelayLine.clampAndSnapOffset(prefs[KEY_SYNC_OFFSET_MS] ?: 0)
      val lockScreenEnabled = prefs[KEY_LOCK_SCREEN_PLAYBACK] ?: true
      val originalCod = prefs[KEY_ORIGINAL_COD]?.takeIf { it.isNotBlank() }
      val lastName = prefs[KEY_LAST_DEVICE_NAME]
      val lastAddress = prefs[KEY_LAST_DEVICE_ADDRESS]
      val lastDevice =
        if (!lastAddress.isNullOrBlank()) {
          ConnectedDeviceInfo(
            name = lastName?.takeIf { it.isNotBlank() } ?: "Bluetooth Audio Source",
            address = lastAddress,
          )
        } else {
          null
        }
      SavedSettings(
        autoReconnectEnabled = autoReconnect,
        speakerMode = speakerMode,
        qualityProfile = qualityProfile,
        bufferProfile = bufferProfile,
        scopeAnimationMode = scopeAnimationMode,
        lowLatencyMode = lowLatency || ultraLowLatency,
        ultraLowLatencyMode = ultraLowLatency,
        syncOffsetMs = syncOffsetMs,
        lockScreenPlaybackEnabled = lockScreenEnabled,
        originalClassOfDevice = originalCod,
        lastDevice = lastDevice,
      )
    }

  suspend fun setAutoReconnectEnabled(enabled: Boolean) {
    context.speakerDataStore.edit { prefs -> prefs[KEY_AUTO_RECONNECT] = enabled }
  }

  suspend fun setSpeakerMode(mode: SpeakerMode) {
    context.speakerDataStore.edit { prefs -> prefs[KEY_SPEAKER_MODE] = mode.name }
  }

  suspend fun saveOriginalClassOfDevice(cod: String) {
    if (cod.isBlank()) return
    context.speakerDataStore.edit { prefs -> prefs[KEY_ORIGINAL_COD] = cod }
  }

  suspend fun saveLastConnectedDevice(device: ConnectedDeviceInfo) {
    context.speakerDataStore.edit { prefs ->
      prefs[KEY_LAST_DEVICE_NAME] = device.name
      prefs[KEY_LAST_DEVICE_ADDRESS] = device.address
    }
  }

  suspend fun setQualityProfile(profile: AudioQualityProfile) {
    context.speakerDataStore.edit { prefs -> prefs[KEY_QUALITY_PROFILE] = profile.name }
  }

  suspend fun setBufferProfile(bufferProfile: AudioBufferProfile) {
    context.speakerDataStore.edit { prefs -> prefs[KEY_BUFFER_PROFILE] = bufferProfile.name }
  }

  suspend fun setLowLatencyMode(enabled: Boolean) {
    context.speakerDataStore.edit { prefs -> prefs[KEY_LOW_LATENCY] = enabled }
  }

  suspend fun setUltraLowLatencyMode(enabled: Boolean) {
    context.speakerDataStore.edit { prefs ->
      prefs[KEY_ULTRA_LOW_LATENCY] = enabled
      if (enabled) prefs[KEY_LOW_LATENCY] = true
    }
  }

  suspend fun setLockScreenPlaybackEnabled(enabled: Boolean) {
    context.speakerDataStore.edit { prefs -> prefs[KEY_LOCK_SCREEN_PLAYBACK] = enabled }
  }

  suspend fun setSyncOffsetMs(offsetMs: Int) {
    val snapped = PcmSyncDelayLine.clampAndSnapOffset(offsetMs)
    context.speakerDataStore.edit { prefs -> prefs[KEY_SYNC_OFFSET_MS] = snapped }
  }

  suspend fun setScopeAnimationMode(mode: ScopeAnimationMode) {
    context.speakerDataStore.edit { prefs -> prefs[KEY_SCOPE_ANIMATION_MODE] = mode.name }
  }

  companion object {
    private val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect_enabled")
    private val KEY_SPEAKER_MODE = stringPreferencesKey("speaker_mode")
    private val KEY_QUALITY_PROFILE = stringPreferencesKey("quality_profile")
    private val KEY_BUFFER_PROFILE = stringPreferencesKey("buffer_profile")
    private val KEY_SCOPE_ANIMATION_MODE = stringPreferencesKey("scope_animation_mode")
    private val KEY_LOW_LATENCY = booleanPreferencesKey("low_latency_mode")
    private val KEY_ULTRA_LOW_LATENCY = booleanPreferencesKey("ultra_low_latency_mode")
    private val KEY_SYNC_OFFSET_MS = intPreferencesKey("sync_offset_ms")
    private val KEY_LOCK_SCREEN_PLAYBACK = booleanPreferencesKey("lock_screen_playback_enabled")
    private val KEY_ORIGINAL_COD = stringPreferencesKey("original_class_of_device")
    private val KEY_LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
    private val KEY_LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
  }
}
