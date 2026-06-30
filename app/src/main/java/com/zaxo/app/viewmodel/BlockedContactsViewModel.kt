package com.zaxo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaxo.app.data.dao.BlockedCallerDao
import com.zaxo.app.model.BlockedCaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockedContactsViewModel @Inject constructor(
    private val blockedCallerDao: BlockedCallerDao
) : ViewModel() {

    val blockedContacts: StateFlow<List<BlockedCaller>> = blockedCallerDao.getAllBlockedCallers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unblockContact(userId: String) {
        viewModelScope.launch {
            blockedCallerDao.deleteBlockedCaller(userId)
        }
    }
}
