package nl.tudelft.trustchain.musicdao.ui.screens.contribute

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.trustchain.musicdao.core.contribute.Contribution
import nl.tudelft.trustchain.musicdao.core.contribute.ContributionRepository
import nl.tudelft.trustchain.musicdao.core.ipv8.ContributionMessage
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.listenActivity.ListenActivityBlockRepository
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.core.util.TrustChainHelper
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import java.util.*;

@HiltViewModel
class ContributeViewModel
    @Inject
    constructor(
        private val contributionRepository: ContributionRepository,
        private val listenActivityBlockRepository: ListenActivityBlockRepository,
        private val walletService: WalletService
    ) : ViewModel() {

    private val _isRefreshing: MutableLiveData<Boolean> = MutableLiveData()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _contributions: MutableStateFlow<List<Contribution>> = MutableStateFlow(listOf())
    val contributions: StateFlow<List<Contribution>> = _contributions

    private val _listenActivity: MutableStateFlow<Map<String, Double>> = MutableStateFlow(mapOf())
    val listenActivity: StateFlow<Map<String, Double>> = _listenActivity

    private val musicCommunity: MusicCommunity by lazy {
        IPv8Android.getInstance()
            .getOverlay() as? MusicCommunity
            ?: throw IllegalStateException("MusicCommunity is not configured")
    }

    init {
        viewModelScope.launch {
            val contributionsFromRepo = contributionRepository.getContributions()

            // set the _contributions value to be all contributions from the repository not contained in flushedContributions
            _contributions.value = contributionsFromRepo

            _listenActivity.value = listenActivityBlockRepository.getMinutesPerArtist()
        }
    }

    // Add contributions to the shared pool
    fun contribute(amount: Float): Boolean {
        _listenActivity.value = listenActivityBlockRepository.getMinutesPerArtist()

        if (listenActivity.value.isNotEmpty()) {
            val totalListenedTime = listenActivity.value.values.sum()

            val txid = musicCommunity.getPayoutNodeWalletAddress()
                ?.let { walletService.sendCoins(it, amount.toString()) }

            if (txid == null) {
                Log.e("ContributeViewModel", "Failed to send coins")
                return false
            }

            val sharePerArtist = listenActivity.value.mapValues { (_, minutes) ->
                (minutes / totalListenedTime).toFloat()
            }
            listenActivityBlockRepository.clearListenActivityData()

            sharePerArtist.forEach() { artist ->
                val share = amount * artist.value
                walletService.sendCoins("server-bitcoin-address", share.toString())
            }

            val id = UUID.randomUUID().toString()

            // create a contribution object with a unique ID, amount, and artists
            val contribution = Contribution(
                id = id,
                amount = amount,
                artists = sharePerArtist.keys.toList()
            )

            val myPeer = IPv8Android.getInstance().myPeer

            // persist the contribution to the blockchain
            val transaction = mutableMapOf(
                "id" to id,
                "amount" to amount,
                "artists" to sharePerArtist.keys.toList()
            )

            musicCommunity.createProposalBlock("contribute-proposal", transaction, myPeer.publicKey.keyToBin())

            _contributions.value += contribution
            return true
        }

        return false
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(500)

            withContext(Dispatchers.IO) {
                val contributionsFromRepo = contributionRepository.getContributions()

                // get only the entries from contributionsFromRepo whose IDs are not in flushedContributionIds
                val contributions = contributionsFromRepo

                withContext(Dispatchers.Main) {
                    _contributions.value = contributions
                    _isRefreshing.value = false
                }

            }
        }
    }
}
