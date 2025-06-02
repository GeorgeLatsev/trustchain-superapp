package nl.tudelft.trustchain.musicdao.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.listenActivity.ListenActivityBlockRepository
import javax.inject.Inject

@HiltViewModel
class MusicStatsViewModel @Inject constructor(
    private val listenRepo: ListenActivityBlockRepository
) : ViewModel() {

    private val _minutesPerArtist = MutableStateFlow<Map<String, Double>>(emptyMap())
    val minutesPerArtist: StateFlow<Map<String, Double>> = _minutesPerArtist

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _minutesPerArtist.value = listenRepo.getMinutesPerArtist()
        }
    }
}
