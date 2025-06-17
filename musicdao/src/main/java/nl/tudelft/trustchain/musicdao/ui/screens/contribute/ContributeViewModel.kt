package nl.tudelft.trustchain.musicdao.ui.screens.contribute

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.trustchain.musicdao.core.cache.CacheDatabase
import nl.tudelft.trustchain.musicdao.core.contribute.Contribution
import nl.tudelft.trustchain.musicdao.core.contribute.ContributionRepository
import nl.tudelft.trustchain.musicdao.core.contribute.PayoutService
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.listenActivity.ListenActivityBlockRepository

@HiltViewModel
class ContributeViewModel
    @Inject
    constructor(
        private val contributionRepository: ContributionRepository,
        private val listenActivityBlockRepository: ListenActivityBlockRepository,
        val artistRepository: ArtistRepository,
        private val payoutService: PayoutService,
        val cacheDatabase: CacheDatabase
    ) : ViewModel() {

    private val _isRefreshing: MutableLiveData<Boolean> = MutableLiveData()
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _listenActivity: MutableStateFlow<Map<String, Double>> = MutableStateFlow(mapOf())
    private val listenActivity: StateFlow<Map<String, Double>> = _listenActivity

    private val musicCommunity: MusicCommunity by lazy {
        IPv8Android.getInstance()
            .getOverlay() as? MusicCommunity
            ?: throw IllegalStateException("MusicCommunity is not configured")
    }

    init {
        viewModelScope.launch {
            _listenActivity.value = listenActivityBlockRepository.getMinutesPerArtist()
        }
    }

    /**
     * Contributes the given amount to the artists.
     * The contribution is split based on the proportion of time listened to each artist.
     *
     * @param amount The amount to contribute.
     * @return [ContributionStatus] indicating the result of the contribution attempt.
     */
    suspend fun contribute(amount: Float): ContributionStatus {
        _listenActivity.value = listenActivityBlockRepository.getMinutesPerArtist()

        if (listenActivity.value.isEmpty()) {
            Log.w("ContributeViewModel", "No listen activity found, cannot contribute")
            return ContributionStatus.NO_LISTEN_ACTIVITY
        }

        if (!payoutService.isNodeFound.value) {
            Log.w("ContributeViewModel", "No payout node found, cannot contribute")
            return ContributionStatus.NO_NODE_FOUND
        }

        val totalListenedTime = listenActivity.value.values.sum()
        val sharePerArtist = listenActivity.value.mapValues { (_, minutes) ->
            (minutes / totalListenedTime).toFloat()
        }

        val sharePerAddress = sharePerArtist.mapNotNull { (key, value) ->
            val address = if ('|' in key) {
                key.substringAfter('|')
            } else {
                Log.d("ContributeViewModel", "Getting artist from repository based on name ${artistRepository.getArtist(key)}")
                artistRepository.getArtist(key)?.bitcoinAddress
            }
            address?.let { it to value }
        }.toMap()

        val result = payoutService.makeContribution(amount, sharePerAddress)
        if (result != null) {
            val contribution = Contribution(
                txid = result,
                amount = amount,
                artists = sharePerArtist.keys.toList(),
                satisfied = false
            )
            val transaction = mutableMapOf(
                "txid" to result,
                "amount" to amount,
                "artists" to sharePerArtist.keys.toList()
            )
            val myPeer = IPv8Android.getInstance().myPeer

            musicCommunity.createProposalBlock("contribute-proposal", transaction, myPeer.publicKey.keyToBin())
            listenActivityBlockRepository.clearListenActivityData()
            cacheDatabase.dao.insert(contribution.toEntity())

            return ContributionStatus.SUCCESS
        }

        return ContributionStatus.FAILURE
    }

    enum class ContributionStatus {
        SUCCESS,
        NO_NODE_FOUND,
        NO_LISTEN_ACTIVITY,
        FAILURE
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(500)

            withContext(Dispatchers.IO) {
                contributionRepository.getContributions() // only needed if we want to add other contributions

                withContext(Dispatchers.Main) {
                    _isRefreshing.value = false
                }

            }
        }
    }
}
