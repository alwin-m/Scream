package com.scream.app.identity

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

import com.scream.app.model.BackgroundMode
import com.scream.app.model.BatteryVisibility

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scream_identity")

data class UserProfile(
    val uuid: String,
    val alias: String,
    val age: String,
    val gender: String,
    val emojiAvatar: String,
    val profileImage: String? = null,
    val isRegistered: Boolean,
    val backgroundMode: BackgroundMode = BackgroundMode.ACTIVE,
    val batteryVisibility: BatteryVisibility = BatteryVisibility.FRIENDS,
    val isAutoDeepSleepEnabled: Boolean = true,
    val autoDeepSleepThreshold: Int = 20,
    val isPermanentOffline: Boolean = false,
    val scanIntervalMs: Long = 4000L,
    val isScheduledDeepSleepEnabled: Boolean = false,
    val deepSleepStartHour: Int = 23,
    val deepSleepEndHour: Int = 7,
    val isStealthModeEnabled: Boolean = false,
    val photoVisibility: com.scream.app.model.PhotoVisibility = com.scream.app.model.PhotoVisibility.PRIVATE_CHATS_ONLY
)

class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        val UUID_KEY = stringPreferencesKey("uuid")
        val ALIAS_KEY = stringPreferencesKey("alias")
        val AGE_KEY = stringPreferencesKey("age")
        val GENDER_KEY = stringPreferencesKey("gender")
        val EMOJI_AVATAR_KEY = stringPreferencesKey("emoji_avatar")
        val PROFILE_IMAGE_KEY = stringPreferencesKey("profile_image")
        val IS_REGISTERED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("is_registered")
        val BACKGROUND_MODE_KEY = stringPreferencesKey("background_mode")
        val BATTERY_VISIBILITY_KEY = stringPreferencesKey("battery_visibility")
        val AUTO_DEEP_SLEEP_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("auto_deep_sleep_enabled")
        val AUTO_DEEP_SLEEP_THRESHOLD_KEY = androidx.datastore.preferences.core.intPreferencesKey("auto_deep_sleep_threshold")
        val PERMANENT_OFFLINE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("permanent_offline")
        val SCAN_INTERVAL_KEY = androidx.datastore.preferences.core.longPreferencesKey("scan_interval_ms")
        val SCHEDULED_DEEP_SLEEP_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("scheduled_deep_sleep_enabled")
        val DEEP_SLEEP_START_HOUR_KEY = androidx.datastore.preferences.core.intPreferencesKey("deep_sleep_start_hour")
        val DEEP_SLEEP_END_HOUR_KEY = androidx.datastore.preferences.core.intPreferencesKey("deep_sleep_end_hour")
        val STEALTH_MODE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("stealth_mode_enabled")
        val PHOTO_VISIBILITY_KEY = stringPreferencesKey("photo_visibility")
    }

    val userProfileFlow: Flow<UserProfile> = dataStore.data.map { preferences ->
        val modeStr = preferences[BACKGROUND_MODE_KEY] ?: BackgroundMode.ACTIVE.name
        val bgMode = runCatching { BackgroundMode.valueOf(modeStr) }.getOrDefault(BackgroundMode.ACTIVE)
        val battVisStr = preferences[BATTERY_VISIBILITY_KEY] ?: BatteryVisibility.FRIENDS.name
        val battVis = runCatching { BatteryVisibility.valueOf(battVisStr) }.getOrDefault(BatteryVisibility.FRIENDS)
        val photoVisStr = preferences[PHOTO_VISIBILITY_KEY] ?: com.scream.app.model.PhotoVisibility.PRIVATE_CHATS_ONLY.name
        val photoVis = runCatching { com.scream.app.model.PhotoVisibility.valueOf(photoVisStr) }.getOrDefault(com.scream.app.model.PhotoVisibility.PRIVATE_CHATS_ONLY)

        UserProfile(
            uuid = preferences[UUID_KEY] ?: "",
            alias = preferences[ALIAS_KEY] ?: "",
            age = preferences[AGE_KEY] ?: "",
            gender = preferences[GENDER_KEY] ?: "",
            emojiAvatar = preferences[EMOJI_AVATAR_KEY] ?: "😎",
            profileImage = preferences[PROFILE_IMAGE_KEY],
            isRegistered = preferences[IS_REGISTERED_KEY] ?: false,
            backgroundMode = bgMode,
            batteryVisibility = battVis,
            isAutoDeepSleepEnabled = preferences[AUTO_DEEP_SLEEP_KEY] ?: true,
            autoDeepSleepThreshold = preferences[AUTO_DEEP_SLEEP_THRESHOLD_KEY] ?: 20,
            isPermanentOffline = preferences[PERMANENT_OFFLINE_KEY] ?: false,
            scanIntervalMs = preferences[SCAN_INTERVAL_KEY] ?: 4000L,
            isScheduledDeepSleepEnabled = preferences[SCHEDULED_DEEP_SLEEP_KEY] ?: false,
            deepSleepStartHour = preferences[DEEP_SLEEP_START_HOUR_KEY] ?: 23,
            deepSleepEndHour = preferences[DEEP_SLEEP_END_HOUR_KEY] ?: 7,
            isStealthModeEnabled = preferences[STEALTH_MODE_KEY] ?: false,
            photoVisibility = photoVis
        )
    }

    suspend fun registerUser(alias: String, age: String, gender: String, emojiAvatar: String, profileImage: String? = null) {
        val newUuid = UUID.randomUUID().toString()
        dataStore.edit { preferences ->
            preferences[UUID_KEY] = newUuid
            preferences[ALIAS_KEY] = alias
            preferences[AGE_KEY] = age
            preferences[GENDER_KEY] = gender
            preferences[EMOJI_AVATAR_KEY] = emojiAvatar
            profileImage?.let { preferences[PROFILE_IMAGE_KEY] = it }
            preferences[IS_REGISTERED_KEY] = true
        }
    }

    suspend fun updateUser(alias: String, age: String, gender: String, emojiAvatar: String, profileImage: String? = null) {
        dataStore.edit { preferences ->
            preferences[ALIAS_KEY] = alias
            preferences[AGE_KEY] = age
            preferences[GENDER_KEY] = gender
            preferences[EMOJI_AVATAR_KEY] = emojiAvatar
            if (profileImage.isNullOrBlank()) preferences.remove(PROFILE_IMAGE_KEY)
            else preferences[PROFILE_IMAGE_KEY] = profileImage
        }
    }

    suspend fun setBackgroundMode(mode: BackgroundMode) {
        dataStore.edit { preferences ->
            preferences[BACKGROUND_MODE_KEY] = mode.name
        }
    }

    suspend fun setBatteryVisibility(visibility: BatteryVisibility) {
        dataStore.edit { preferences ->
            preferences[BATTERY_VISIBILITY_KEY] = visibility.name
        }
    }

    suspend fun setAutoDeepSleep(enabled: Boolean, thresholdPercent: Int = 20) {
        dataStore.edit { preferences ->
            preferences[AUTO_DEEP_SLEEP_KEY] = enabled
            preferences[AUTO_DEEP_SLEEP_THRESHOLD_KEY] = thresholdPercent
        }
    }

    suspend fun setPermanentOffline(offline: Boolean) {
        dataStore.edit { preferences ->
            preferences[PERMANENT_OFFLINE_KEY] = offline
        }
    }

    suspend fun setScanIntervalMs(intervalMs: Long) {
        dataStore.edit { preferences ->
            preferences[SCAN_INTERVAL_KEY] = intervalMs
        }
    }

    suspend fun setScheduledDeepSleep(enabled: Boolean, startHour: Int = 23, endHour: Int = 7) {
        dataStore.edit { preferences ->
            preferences[SCHEDULED_DEEP_SLEEP_KEY] = enabled
            preferences[DEEP_SLEEP_START_HOUR_KEY] = startHour
            preferences[DEEP_SLEEP_END_HOUR_KEY] = endHour
        }
    }

    suspend fun setStealthMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[STEALTH_MODE_KEY] = enabled
        }
    }

    suspend fun setPhotoVisibility(visibility: com.scream.app.model.PhotoVisibility) {
        dataStore.edit { preferences ->
            preferences[PHOTO_VISIBILITY_KEY] = visibility.name
        }
    }
}
