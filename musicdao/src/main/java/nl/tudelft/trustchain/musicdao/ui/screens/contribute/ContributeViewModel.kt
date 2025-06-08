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
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.listenActivity.ListenActivityBlockRepository
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.core.util.TrustChainHelper
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import java.util.*;

@HiltViewModel
class ContributeViewModel
    @Inject
    constructor(
        private val artistRepository: ArtistRepository,
        private val contributionRepository: ContributionRepository,
//        val contributionPool: ContributionPool
        private val listenActivityBlockRepository: ListenActivityBlockRepository
    ) : ViewModel() {

    private val _isRefreshing: MutableLiveData<Boolean> = MutableLiveData()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _contributions: MutableStateFlow<List<Contribution>> = MutableStateFlow(listOf())
    val contributions: StateFlow<List<Contribution>> = _contributions

    private val _contributionPool: MutableStateFlow<ContributionPool> = MutableStateFlow(ContributionPool(artistRepository))
    val contributionPool: StateFlow<ContributionPool> = _contributionPool

    private val _listenActivity: MutableStateFlow<Map<String, Double>> = MutableStateFlow(mapOf())
    val listenActivity: StateFlow<Map<String, Double>> = _listenActivity

//    private val contributionPool = ContributionPool

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
            val poolBlocks = musicCommunity.database.getBlocksWithType("contribution-pool")
                .sortedByDescending { it.timestamp }

            if (poolBlocks.isNotEmpty()) {
                val latestBlock = poolBlocks.first()
                val serializedData = latestBlock.transaction["data"] as String
                contributionPool.value.deserialize(serializedData)
            }

            val contributionsFromRepo = contributionRepository.getContributions()
            val flushedContributions = contributionPool.value.getFlushedContributions()

            // set the _contributions value to be all contributions from the repository not contained in flushedContributions
            _contributions.value = contributionsFromRepo.filterNot { contribution ->
                flushedContributions.contains(contribution.id)
            }

            _listenActivity.value = listenActivityBlockRepository.getMinutesPerArtist()
        }
    }

    // Add contributions to the shared pool
    fun contribute(amount: Float): Boolean {
        _listenActivity.value = listenActivityBlockRepository.getMinutesPerArtist()

//        val artists = artistRepository.getArtists()

        if (listenActivity.value.isNotEmpty()) {
            val totalListenedTime = listenActivity.value.values.sum()
            val sharePerArtist = listenActivity.value.mapValues { (_, minutes) ->
                (minutes / totalListenedTime).toFloat()
            }

            sharePerArtist.forEach() { artist ->
                contributionPool.value.addContribution(artist.key, amount * artist.value)
            }

            val id = UUID.randomUUID().toString()

            // create a contribution object with a unique ID, amount, and artists
            val contribution = Contribution(
                id = id,
                amount = amount,
                artists = sharePerArtist.keys.toList()
            )

            contributionPool.value.addContributionObject(contribution)

            val serializedPool = contributionPool.value.serialize()
            val poolTransaction = mutableMapOf(
                "data" to serializedPool
            )

            val myPeer = IPv8Android.getInstance().myPeer

            musicCommunity.createProposalBlock("contribution-pool", poolTransaction, myPeer.publicKey.keyToBin())

//            val contribution = Contribution(amount, artists)

            // persist the contribution to the blockchain
            val transaction = mutableMapOf(
                "id" to id,
                "amount" to amount,
                "artists" to sharePerArtist.keys.toList()
//                // create a string of all the artist public keys separated by @'s
//                "artists" to artists.joinToString(separator = "@") { it.publicKey }
            )



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
                val poolBlocks = musicCommunity.database.getBlocksWithType("contribution-pool")
                    .sortedByDescending { it.timestamp }

                if (poolBlocks.isNotEmpty()) {
                    val latestBlock = poolBlocks.first()
                    val serializedData = latestBlock.transaction["data"] as String
                    contributionPool.value.deserialize(serializedData)
                }


                // TODO: Fill in the code that finds the contributions stored
                val contributionsFromRepo = contributionRepository.getContributions()
                val flushedContributionIds = contributionPool.value.getFlushedContributions()

                // get only the entries from contributionsFromRepo whose IDs are not in flushedContributionIds
                val contributions = contributionsFromRepo.filterNot { contribution ->
                    flushedContributionIds.contains(contribution.id)
                }

                withContext(Dispatchers.Main) {
                    _contributions.value = contributions
                    _isRefreshing.value = false
                }

            }
        }
    }
}
