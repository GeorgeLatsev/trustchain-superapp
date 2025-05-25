package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.listenActivity

import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import javax.inject.Inject

class ListenActivityBlockRepository @Inject constructor(
    private val musicCommunity: MusicCommunity
) {
    fun getAllBlocks(): List<ListenActivityBlock> {
        return musicCommunity.database
            .getBlocksWithType(ListenActivityBlock.BLOCK_TYPE)
            .map { ListenActivityBlock.fromTrustChainTransaction(it.transaction) }
    }

    fun getMinutesPerArtist(): Map<String, Double> {
        return getAllBlocks()
            .groupBy { it.artistId }
            .mapValues { (_, blocks) -> blocks.sumOf { it.listenedMillis } / 60000.0 }
    }
}
