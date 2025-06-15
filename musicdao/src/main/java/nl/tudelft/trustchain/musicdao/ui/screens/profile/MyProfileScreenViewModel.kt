package nl.tudelft.trustchain.musicdao.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.core.contribute.PayoutService
import nl.tudelft.trustchain.musicdao.core.node.PayoutManager
import javax.inject.Inject

@HiltViewModel
class MyProfileScreenViewModel
    @Inject
    constructor(
        private val artistRepository: ArtistRepository,
        private val musicCommunity: MusicCommunity,
        private val payoutService: PayoutService,
        private val payoutManager: PayoutManager,
    ) : ViewModel() {
        private val _profile: MutableStateFlow<Artist?> = MutableStateFlow(null)
        var profile: StateFlow<Artist?> = _profile

        val isNodeFound = payoutService.isNodeFound
        val nodeAddress = payoutService.nodeAddress

        fun publicKey(): String {
            return musicCommunity.publicKeyHex()
        }

        fun enablePayoutNode() {
            if (!payoutManager.isEnabled()) {
                payoutManager.enable()
            }
        }

        suspend fun publishEdit(
            name: String,
            bitcoinAddress: String,
            socials: String,
            biography: String
        ): Boolean {
            return artistRepository.edit(name, bitcoinAddress, socials, biography)
        }

        init {
            viewModelScope.launch {
                profile = artistRepository.getArtistStateFlow(publicKey())
            }
        }
    }
