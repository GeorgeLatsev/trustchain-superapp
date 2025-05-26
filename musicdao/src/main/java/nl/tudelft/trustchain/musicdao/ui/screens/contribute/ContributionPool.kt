package nl.tudelft.trustchain.musicdao.ui.screens.contribute

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.musicdao.core.contribute.Contribution
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import javax.inject.Inject

class ContributionPool @Inject constructor(
    private val artistRepository: ArtistRepository
) {
    private val pool: MutableMap<Artist, Double> = mutableMapOf()
    private val contributionObjects: MutableList<String> = mutableListOf()
    private val flushedContributions: MutableSet<String> = mutableSetOf()

    @Serializable
    data class ContributionPoolData(
        val pool: Map<String, Double>,
        val contributionObjects: List<String>,
        val flushedContributions: Set<String>
    )

    fun serialize(): String {
        val state = ContributionPoolData(
            pool = pool.mapKeys { it.key.publicKey },
            contributionObjects = contributionObjects,
            flushedContributions = flushedContributions
        )

        return Json.encodeToString(ContributionPoolData.serializer(), state)
    }

    suspend fun deserialize(data: String) {
        val state = Json.decodeFromString(ContributionPoolData.serializer(), data)
        pool.clear()

        for ((publicKey, amount) in state.pool) {
            artistRepository.getArtist(publicKey)?.let { artist ->
                pool[artist] = amount
            }
        }

        contributionObjects.clear()
        contributionObjects.addAll(state.contributionObjects)

        flushedContributions.clear()
        flushedContributions.addAll(state.flushedContributions)
    }

    fun addContribution(artist: Artist, amount: Double) {
        pool[artist] = pool.getOrDefault(artist, 0.0) + amount
    }

    fun addContributionObject(contribution: Contribution) {
        contributionObjects.add(contribution.id)
    }

    fun getPooledAmount(artist: Artist): Double {
        return pool.getOrDefault(artist, 0.0)
    }

    fun getFlushedContributions(): Set<String> {
        return flushedContributions.toSet()
    }

    fun clearPool(): Map<Artist, Double> {
        val contributions = pool.toMap()
        pool.clear()
        return contributions
    }

    // Distribute pooled contributions in bulk
    suspend fun distributePooledContributions(bitcoinWalletViewModel: BitcoinWalletViewModel): Boolean {
        val pooledContributions = clearPool()
        for ((artist, totalAmount) in pooledContributions) {
            val success = bitcoinWalletViewModel.donate(artist.publicKey, totalAmount.toString())
            if (!success) return false
        }
        return true
    }

    fun updateFlushedContributions() {
        for (contribution in contributionObjects) {
            flushedContributions.add(contribution)
        }

        clearContributionObjects()
    }

    private fun clearContributionObjects() {
        contributionObjects.clear()
    }
}
