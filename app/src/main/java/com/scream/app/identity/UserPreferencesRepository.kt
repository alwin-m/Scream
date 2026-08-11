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
    val isPermanentOffline: Boolean = false
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
    }

    val userProfileFlow: Flow<UserProfile> = dataStore.data.map { preferences ->
        val modeStr = preferences[BACKGROUND_MODE_KEY] ?: BackgroundMode.ACTIVE.name
        val bgMode = runCatching { BackgroundMode.valueOf(modeStr) }.getOrDefault(BackgroundMode.ACTIVE)
        val battVisStr = preferences[BATTERY_VISIBILITY_KEY] ?: BatteryVisibility.FRIENDS.name
        val battVis = runCatching { BatteryVisibility.valueOf(battVisStr) }.getOrDefault(BatteryVisibility.FRIENDS)

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
            isPermanentOffline = preferences[PERMANENT_OFFLINE_KEY] ?: false
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
}

