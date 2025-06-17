package nl.tudelft.trustchain.musicdao.core.ipv8

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.Peer
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.payoutStatusUpdate.PayoutStatusUpdateBlockValidator
import nl.tudelft.trustchain.musicdao.ui.SnackbarHandler.coroutineScope
import javax.inject.Inject

class PayoutStatusUpdateGossiper
    @Inject
    constructor(
        private val musicCommunity: MusicCommunity,
        private val payoutStatusUpdateBlockValidator: PayoutStatusUpdateBlockValidator
    ) {

     fun startGossip(coroutineScope: CoroutineScope){
         coroutineScope.launch {
             while (coroutineScope.isActive) {
                 gossip()
                 delay(Config.DELAY)
             }
         }
     }
     private fun gossip () {
         val randomPeer = pickRandomPeer()
         val payoutStatusUpdateBlocks =
             musicCommunity.database.getBlocksWithType(PayoutStatusUpdateBlockValidator.BLOCK_TYPE)
                 .filter { payoutStatusUpdateBlockValidator.validateTransaction(it.transaction) }
                 .shuffled()
                 .take(Config.BLOCKS)
         payoutStatusUpdateBlocks.forEach {
             musicCommunity.sendBlock(it, randomPeer)
         }
     }

    object Config {
        const val BLOCKS = 10
        const val DELAY = 5_000L
    }

    private fun pickRandomPeer(): Peer? {
        val peers = musicCommunity.getPeers()
        if (peers.isEmpty()) return null
        return peers.random()
    }
    }
