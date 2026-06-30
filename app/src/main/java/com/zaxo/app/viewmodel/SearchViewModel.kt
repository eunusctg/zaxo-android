package com.zaxo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaxo.app.data.repository.ChatRepository
import com.zaxo.app.model.ChatSearchGroup
import com.zaxo.app.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ChatSearchGroup>>(emptyList())
    val searchResults: StateFlow<List<ChatSearchGroup>> = _searchResults.asStateFlow()

    private val _recentChats = MutableStateFlow<List<com.zaxo.app.model.Chat>>(emptyList())
    val recentChats: StateFlow<List<com.zaxo.app.model.Chat>> = _recentChats.asStateFlow()

    // F17: Pagination state
    private var currentOffset = 0
    private val batchSize = 100
    private var canLoadMore = true

    init {
        viewModelScope.launch {
            repository.getAllChats().collect { chats ->
                _recentChats.value = chats.take(5)
            }
        }

        // F15: Debounced search on IO dispatcher (300ms)
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .filter { it.trim().length >= 2 }
                .distinctUntilChanged()
                .collect { query ->
                    // Reset pagination for new query
                    currentOffset = 0
                    canLoadMore = true
                    performSearch(query.trim())
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            canLoadMore = true
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                // F14/F17: FTS4 search with 100 result limit
                val results = repository.searchMessagesGrouped(query)
                _searchResults.value = results.take(batchSize)
                canLoadMore = results.size >= batchSize
                currentOffset = results.size
            } catch (e: Exception) {
                Timber.e(e, "Search failed for query: $query")
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * F17: Load more results for pagination.
     * Currently loads the next batch of search results.
     */
    fun loadMore() {
        if (!canLoadMore || _isSearching.value) return

        val query = _searchQuery.value.trim()
        if (query.length < 2) return

        viewModelScope.launch {
            _isSearching.value = true
            try {
                val moreResults = repository.searchMessagesGrouped(query)
                val currentResults = _searchResults.value.toMutableList()

                // Merge new results with existing, avoiding duplicates by chatId
                val existingChatIds = currentResults.map { it.chatId }.toSet()
                for (group in moreResults) {
                    if (group.chatId !in existingChatIds) {
                        currentResults.add(group)
                    } else {
                        // Merge messages for existing chat group
                        val existingIndex = currentResults.indexOfFirst { it.chatId == group.chatId }
                        if (existingIndex >= 0) {
                            val existing = currentResults[existingIndex]
                            val existingMsgIds = existing.messages.map { it.id }.toSet()
                            val newMsgs = group.messages.filter { it.id !in existingMsgIds }
                            currentResults[existingIndex] = existing.copy(
                                messages = existing.messages + newMsgs
                            )
                        }
                    }
                }

                _searchResults.value = currentResults
                canLoadMore = moreResults.size >= batchSize
                currentOffset += moreResults.size
            } catch (e: Exception) {
                Timber.e(e, "Load more failed for query: $query")
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        canLoadMore = true
        currentOffset = 0
    }
}
