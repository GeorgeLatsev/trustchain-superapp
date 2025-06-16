package nl.tudelft.trustchain.musicdao.core.contribute

import android.annotation.SuppressLint
import kotlinx.coroutines.flow.MutableStateFlow
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContributionRepository
    @Inject
    constructor(
        private val musicCommunity: MusicCommunity,
    ) {

    suspend fun getContribution(publicKey: String): Contribution? {
        return getOrCrawl(publicKey)?.let { toContribution(it) }
    }

    fun getContributions(): List<Contribution> {
        val blocks = musicCommunity.database.getBlocksWithType("contribute-proposal")
            .sortedByDescending { it.sequenceNumber }

        return blocks.map { toContribution(it) }
    }

    private fun toContribution(block: TrustChainBlock): Contribution {
        val artists = block.transaction["artists"] as List<String>

        return Contribution(
            txid = block.transaction["txid"] as String,
            amount = block.transaction["amount"] as Float,
            artists = artists
        )
    }

    private suspend fun getOrCrawl(publicKey: String): TrustChainBlock? {
        val block = get(publicKey)
        return if (block != null) {
            block
        } else {
            crawl(publicKey)
            get(publicKey)
        }
    }

    @SuppressLint("NewApi")
    private suspend fun crawl(publicKey: String) {
        val key = defaultCryptoProvider.keyFromPublicBin(publicKey.hexToBytes()).keyToBin()
        val peer = musicCommunity.network.getVerifiedByPublicKeyBin(key)

        if (peer != null) {
            musicCommunity.crawlChain(peer)
        } else {
            val randomPeers =
                musicCommunity.network.getRandomPeers(10) - musicCommunity.myPeer

            try {
                randomPeers.forEach {
                    musicCommunity.sendCrawlRequest(it, key, LongRange(-1, -1))
                }
            } catch (e: Exception) {
                // Handle exception
                return
            }
        }
    }

    fun get(publicKey: String): TrustChainBlock? {
        val blocks = musicCommunity.database.getBlocksWithType("contribute-proposal")
            .filter { it.publicKey.toHex() == publicKey }
            .sortedByDescending { it.sequenceNumber }
            .take(1)

        return if (blocks.isNotEmpty()) {
            val mostUpdatedContribution = blocks[0]
            mostUpdatedContribution
        } else {
            return null
        }
    }
}
