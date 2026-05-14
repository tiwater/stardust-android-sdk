package cn.ticos.stardust.sample.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.ticos.stardust.sample.data.TtsApiClient
import cn.ticos.stardust.sample.model.DEFAULT_REALTIME_URL
import cn.ticos.stardust.sample.model.Speaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TtsViewModel(
    private val ttsClient: TtsApiClient,
) : ViewModel() {

    data class FilterState(
        val language: String? = "chinese",
        val gender: String? = null,
        val provider: String? = null,
        val tags: String? = null,
        val nameQuery: String? = null,
    )

    private val _filters = MutableStateFlow(FilterState())
    val filters: StateFlow<FilterState> = _filters.asStateFlow()

    private val _speakers = MutableStateFlow<List<Speaker>>(emptyList())
    val speakers: StateFlow<List<Speaker>> = _speakers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private var currentSkip = 0
    private var lastServerUrl: String = DEFAULT_REALTIME_URL
    private val pageSize = 20

    fun updateFilters(transform: (FilterState) -> FilterState) {
        _filters.value = transform(_filters.value)
        loadSpeakers(lastServerUrl)
    }

    fun loadSpeakers(serverUrl: String) {
        lastServerUrl = serverUrl
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _speakers.value = emptyList()
            currentSkip = 0
            val f = _filters.value
            val result = ttsClient.getSpeakers(
                serverUrl = serverUrl,
                language = f.language,
                gender = f.gender,
                provider = f.provider,
                tags = f.tags,
                name = f.nameQuery,
                skip = 0,
                top = pageSize,
                all = true,
            )
            result.onSuccess { page ->
                _speakers.value = page.speakers
                currentSkip = page.speakers.size
                _hasMore.value = currentSkip < page.totalSpeakersCount
            }.onFailure { t ->
                _error.value = t.message ?: "Failed to load speakers"
                _hasMore.value = false
            }
            _isLoading.value = false
        }
    }

    fun loadMore(serverUrl: String) {
        if (_isLoading.value || !_hasMore.value) return
        viewModelScope.launch {
            _isLoading.value = true
            val f = _filters.value
            val result = ttsClient.getSpeakers(
                serverUrl = serverUrl,
                language = f.language,
                gender = f.gender,
                provider = f.provider,
                tags = f.tags,
                name = f.nameQuery,
                skip = currentSkip,
                top = pageSize,
                all = true,
            )
            result.onSuccess { page ->
                _speakers.value = _speakers.value + page.speakers
                currentSkip += page.speakers.size
                _hasMore.value = currentSkip < page.totalSpeakersCount
            }.onFailure { t ->
                _error.value = t.message ?: "Failed to load more"
            }
            _isLoading.value = false
        }
    }
}
