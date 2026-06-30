package com.zaxo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.zaxo.app.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val userId: String = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _user = MutableStateFlow(User())
    val user: StateFlow<User> = _user.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _about = MutableStateFlow("")
    val about: StateFlow<String> = _about.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveComplete = MutableStateFlow(false)
    val saveComplete: StateFlow<Boolean> = _saveComplete.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                val doc = firestore.collection("users").document(userId).get().await()
                val user = doc.toObject(User::class.java) ?: User()
                _user.value = user
                _displayName.value = user.displayName
                _about.value = user.about
                _phone.value = user.phone
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateDisplayName(name: String) {
        _displayName.value = name
    }

    fun updateAbout(about: String) {
        _about.value = about
    }

    fun saveProfile() {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val updates = mapOf(
                    "displayName" to _displayName.value.trim(),
                    "about" to _about.value.trim()
                )
                firestore.collection("users").document(userId).update(updates).await()
                _saveComplete.value = true
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isSaving.value = false
            }
        }
    }
}
