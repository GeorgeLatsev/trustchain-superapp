package nl.tudelft.trustchain.musicdao.ui.screens.contribute

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
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.core.util.TrustChainHelper
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import java.util.*;

@HiltViewModel
class ContributeViewModel
    @Inject
    constructor(
        private val artistRepository: ArtistRepository,
        private val contributionRepository: ContributionRepository
    ) : ViewModel() {

    private val _isRefreshing: MutableLiveData<Boolean> = MutableLiveData()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _contributions: MutableStateFlow<List<Contribution>> = MutableStateFlow(listOf())
    val contributions: StateFlow<List<Contribution>> = _contributions

    private val contributionPool = ContributionPool

    private fun getTrustChainCommunity(): TrustChainCommunity {
        return IPv8Android.getInstance().getOverlay()
            ?: throw IllegalStateException("TrustChainCommunity is not configured")
    }

    private val musicCommunity: MusicCommunity by lazy {
        IPv8Android.getInstance()
            .getOverlay() as? MusicCommunity
            ?: throw IllegalStateException("MusicCommunity is not configured")
    }

//    private val trustchain: TrustChainHelper by lazy {
//        TrustChainHelper(trustchainCommunity)
//    }

    init {
        viewModelScope.launch {
            _contributions.value = contributionRepository.getContributions()
        }
    }

    // Add contributions to the shared pool
    fun contribute(amount: Double): Boolean {
        val artists = artistRepository.getArtists()

        if (artists.isNotEmpty()) {
            val share = amount / artists.size
            artists.forEach { artist ->
                contributionPool.addContribution(artist, share)
            }
            val contribution = Contribution(amount, artists)

            // persist the contribution to the blockchain
            val transaction = mutableMapOf(
                "amount" to amount,
                "artists" to artists.map { it.publicKey }
//                // create a string of all the artist public keys separated by @'s
//                "artists" to artists.joinToString(separator = "@") { it.publicKey }
            )

            val myPeer = IPv8Android.getInstance().myPeer

//            trustchain.createProposalBlock(transaction, myPeer.publicKey.keyToBin(), "contribute-proposal")

            musicCommunity.createProposalBlock("contribute-proposal", transaction, myPeer.publicKey.keyToBin())

            _contributions.value = _contributions.value + contribution
            return true
        }

        return false
    }

//    // make a method that calls distributePooledContributions from the shared pool
//    fun distributeContributions() {
//        CoroutineScope(Dispatchers.IO).launch {
//            contributionPool.distributePooledContributions(bitcoinWalletViewModel)
//        }
//    }

    fun clearContributions() {
        _contributions.value = listOf()
    }

    // old one before shared pool
//    // return true if all donations were successful and false if any failed
//    suspend fun contribute(amount: Long): Boolean {
//
//        // TODO: replace this with only the artists I have listened to
//        val artists = artistRepository.getArtists()
//
//        if (artists.isNotEmpty()) {
//            val share = amount / artists.size
//            artists.forEach { artist ->
//                val succ = bitcoinWalletViewModel.donate(artist.publicKey, share.toString())
//
//                if (!succ) {
//                    return false
//                }
//            }
//            val contribution = Contribution(amount, artists)
//            _contributions.value = _contributions.value + contribution
//        }
//
//
//        return true
//    }


    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(500)

            withContext(Dispatchers.IO) {
                // TODO: Fill in the code that finds the contributions stored
                val contributions = contributionRepository.getContributions()

                withContext(Dispatchers.Main) {
                    _contributions.value = contributions
                    _isRefreshing.value = false
                }

            }
        }
    }
}
