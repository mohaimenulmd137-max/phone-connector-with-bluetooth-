package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sutolink_prefs")

enum class StreamQuality(
  val maxWidthPx: Int,
  val jpegQuality: Int,
  val minFrameIntervalMs: Long,
) {
  FAST(maxWidthPx = 360, jpegQuality = 42, minFrameIntervalMs = 110L),
  BALANCED(maxWidthPx = 480, jpegQuality = 58, minFrameIntervalMs = 85L),
  SHARP(maxWidthPx = 640, jpegQuality = 72, minFrameIntervalMs = 70L),
}

enum class AppLanguage {
  BENGALI,
  ENGLISH,
}

data class LinkPreferences(
  val autoListenOnOpen: Boolean = true,
  val autoRemoveSettingsOnDisconnect: Boolean = true,
  val autoShareOnConnect: Boolean = true,
  val streamQuality: StreamQuality = StreamQuality.BALANCED,
  val language: AppLanguage = AppLanguage.BENGALI,
  val lastDeviceName: String = "",
  val lastDeviceMac: String = "",
)

class SettingsPreferences(private val context: Context) {
  private object Keys {
    val AUTO_LISTEN = booleanPreferencesKey("auto_listen_on_open")
    val AUTO_REMOVE_SETTINGS = booleanPreferencesKey("auto_remove_settings_on_disconnect")
    val AUTO_SHARE_ON_CONNECT = booleanPreferencesKey("auto_share_on_connect")
    val STREAM_QUALITY = stringPreferencesKey("stream_quality")
    val LANGUAGE = stringPreferencesKey("language")
    val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
    val LAST_DEVICE_MAC = stringPreferencesKey("last_device_mac")
  }

  val preferencesFlow: Flow<LinkPreferences> =
    context.dataStore.data.map { prefs ->
      LinkPreferences(
        autoListenOnOpen = prefs[Keys.AUTO_LISTEN] ?: true,
        autoRemoveSettingsOnDisconnect = prefs[Keys.AUTO_REMOVE_SETTINGS] ?: true,
        autoShareOnConnect = prefs[Keys.AUTO_SHARE_ON_CONNECT] ?: true,
        streamQuality =
          prefs[Keys.STREAM_QUALITY]?.let { name ->
            StreamQuality.entries.firstOrNull { it.name == name }
          } ?: StreamQuality.BALANCED,
        language =
          prefs[Keys.LANGUAGE]?.let { name ->
            AppLanguage.entries.firstOrNull { it.name == name }
          } ?: AppLanguage.BENGALI,
        lastDeviceName = prefs[Keys.LAST_DEVICE_NAME] ?: "",
        lastDeviceMac = prefs[Keys.LAST_DEVICE_MAC] ?: "",
      )
    }

  suspend fun setAutoListenOnOpen(enabled: Boolean) {
    context.dataStore.edit { it[Keys.AUTO_LISTEN] = enabled }
  }

  suspend fun setAutoRemoveSettingsOnDisconnect(enabled: Boolean) {
    context.dataStore.edit { it[Keys.AUTO_REMOVE_SETTINGS] = enabled }
  }

  suspend fun setAutoShareOnConnect(enabled: Boolean) {
    context.dataStore.edit { it[Keys.AUTO_SHARE_ON_CONNECT] = enabled }
  }

  suspend fun setStreamQuality(quality: StreamQuality) {
    context.dataStore.edit { it[Keys.STREAM_QUALITY] = quality.name }
  }

  suspend fun setLanguage(language: AppLanguage) {
    context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
  }

  suspend fun saveLastConnectedDevice(name: String, mac: String) {
    context.dataStore.edit {
      it[Keys.LAST_DEVICE_NAME] = name
      it[Keys.LAST_DEVICE_MAC] = mac
    }
  }
}
