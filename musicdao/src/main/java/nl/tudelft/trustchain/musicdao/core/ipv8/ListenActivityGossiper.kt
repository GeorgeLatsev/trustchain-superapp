package nl.tudelft.trustchain.musicdao.core.ipv8.modules

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.listenActivity.ListenActivityBlock
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.listenActivity.ListenActivityBlockValidator
import javax.inject.Inject

class ListenActivityGossiper
@Inject
constructor(
    private val musicCommunity: MusicCommunity,
    private val listenActivityValidator: ListenActivityBlockValidator
) {

    fun startGossip(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            while (coroutineScope.isActive) {
                gossip()
                delay(Config.DELAY)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun gossip() {
        val peer = pickRandomPeer() ?: return
    
        val listenBlocks = musicCommunity.database
            .getBlocksWithType(ListenActivityBlock.BLOCK_TYPE)
            .filter {
                val tx = it.transaction as? Map<String, Any?>
                tx != null && listenActivityValidator.validateTransaction(tx)
            }
            .shuffled()
            .take(Config.BLOCKS)
    
        listenBlocks.forEach { block ->
            musicCommunity.sendBlock(block, peer)
        }
    }

    private fun pickRandomPeer(): Peer? {
        val peers = musicCommunity.getPeers()
        return if (peers.isEmpty()) null else peers.random()
    }

    object Config {
        const val BLOCKS = 10          // max blocks to gossip at once
        const val DELAY = 10_000L      // 10 seconds between gossip attempts
    }
}