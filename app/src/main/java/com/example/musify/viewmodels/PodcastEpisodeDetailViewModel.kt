package com.example.musify.viewmodels

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.musify.data.repositories.podcastsrepository.PodcastsRepository
import com.example.musify.data.utils.FetchedResource
import com.example.musify.data.utils.MapperImageSize
import com.example.musify.domain.PodcastEpisode
import com.example.musify.ui.navigation.MusifyNavigationDestinations
import com.example.musify.usecases.getCurrentlyPlayingEpisodePlaybackStateUseCase.GetCurrentlyPlayingEpisodePlaybackStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.musify.usecases.getCurrentlyPlayingEpisodePlaybackStateUseCase.GetCurrentlyPlayingEpisodePlaybackStateUseCase.PlaybackState as UseCasePlaybackState

@HiltViewModel
class PodcastEpisodeDetailViewModel @Inject constructor(
    application: Application,
    private val podcastsRepository: PodcastsRepository,
    private val savedStateHandle: SavedStateHandle,
    getEpisodePlaybackStateUseCase: GetCurrentlyPlayingEpisodePlaybackStateUseCase,
) : AndroidViewModel(application) {

    enum class UiSate { IDLE, LOADING, PLAYBACK_LOADING, ERROR }

    private val _uiState = mutableStateOf(UiSate.IDLE)
    val uiState = _uiState as State<UiSate>
    private val _podcastEpisode = mutableStateOf<PodcastEpisode?>(null)
    val podcastEpisode = _podcastEpisode as State<PodcastEpisode?>


    private var _isEpisodeCurrentlyPlaying = mutableStateOf(false)
    val isEpisodeCurrentlyPlaying = _isEpisodeCurrentlyPlaying as State<Boolean>

    init {
        fetchEpisodeUpdatingUiState()
        getEpisodePlaybackStateUseCase
            .currentlyPlayingEpisodePlaybackStateStream
            .onEach {
                when (it) {
                    is UseCasePlaybackState.Ended -> _isEpisodeCurrentlyPlaying.value = false
                    is UseCasePlaybackState.Loading -> _uiState.value = UiSate.PLAYBACK_LOADING
                    is UseCasePlaybackState.Paused -> _isEpisodeCurrentlyPlaying.value = false
                    is UseCasePlaybackState.Playing -> {
                        if (_uiState.value == UiSate.PLAYBACK_LOADING) _uiState.value = UiSate.IDLE
                        _isEpisodeCurrentlyPlaying.value = true
                    }
                }
            }.launchIn(viewModelScope)
    }

    private fun fetchEpisodeUpdatingUiState() {
        viewModelScope.launch {
            _uiState.value = UiSate.LOADING
            val episode = fetchEpisode()
            _uiState.value = if (episode == null) {
                UiSate.ERROR
            } else {
                _podcastEpisode.value = episode
                UiSate.IDLE
            }
        }
    }

    private suspend fun fetchEpisode(): PodcastEpisode? {
        val fetchedResource = podcastsRepository.fetchPodcastEpisode(
            episodeId = savedStateHandle[MusifyNavigationDestinations.PodcastEpisodeDetailScreen.NAV_ARG_PODCAST_EPISODE_ID]!!,
            countryCode = getCountryCode(),
            imageSize = MapperImageSize.LARGE // image would be used for both the mini player and the full screen player
        )
        return if (fetchedResource is FetchedResource.Success) fetchedResource.data else null
    }

    fun retryFetchingEpisode() {
        fetchEpisodeUpdatingUiState()
    }
}
