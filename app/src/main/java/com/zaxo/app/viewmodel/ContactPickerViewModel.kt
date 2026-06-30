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
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ContactPickerViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allContacts = MutableStateFlow<List<User>>(emptyList())
    // F63: Debounced search (300ms) with Dispatchers.IO for performance
    val filteredContacts: StateFlow<List<User>> = combine(
        _allContacts,
        _searchQuery.debounce(300L)
    ) { contacts, query ->
        if (query.isBlank()) contacts
        else contacts.filter {
            it.displayName.contains(query, ignoreCase = true) ||
            it.zaxoNumber.contains(query, ignoreCase = true)
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedContactIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedContactIds: StateFlow<Set<String>> = _selectedContactIds.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadContacts()
    }

    // F62: Force sync on screen open, show loading shimmer
    private fun loadContacts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUserId = auth.currentUser?.uid ?: ""
                val snapshot = firestore.collection("users")
                    .get()
                    .await()

                val contacts = snapshot.documents
                    .filter { it.id != currentUserId }
                    .mapNotNull { doc ->
                        try {
                            User(
                                uid = doc.id,
                                displayName = doc.getString("displayName") ?: "",
                                email = doc.getString("email") ?: "",
                                phone = doc.getString("phone") ?: "",
                                photoUrl = doc.getString("photoUrl") ?: "",
                                about = doc.getString("about") ?: "",
                                isOnline = doc.getBoolean("isOnline") ?: false,
                                zaxoNumber = doc.getString("zaxoNumber") ?: ""
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    // D.4: Sort contacts — online first, then alphabetical
                    .sortedWith(compareByDescending<User> { it.isOnline }.thenBy { it.displayName })

                _allContacts.value = contacts
            } catch (e: Exception) {
                Timber.e(e, "Failed to load contacts")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleContactSelection(uid: String) {
        _selectedContactIds.value = if (uid in _selectedContactIds.value) {
            _selectedContactIds.value - uid
        } else {
            _selectedContactIds.value + uid
        }
    }
}
