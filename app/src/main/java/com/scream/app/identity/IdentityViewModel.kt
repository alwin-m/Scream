package com.scream.app.identity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IdentityViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserPreferencesRepository(application.dataStore)

    val userProfile: StateFlow<UserProfile?> = repository.userProfileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun register(alias: String, age: String, gender: String, emojiAvatar: String) {
        viewModelScope.launch {
            repository.registerUser(alias, age, gender, emojiAvatar)
        }
     }

    fun updateProfile(alias: String, age: String, gender: String, emojiAvatar: String) {
        viewModelScope.launch {
            repository.updateUser(alias, age, gender, emojiAvatar)
        }
    }
}

