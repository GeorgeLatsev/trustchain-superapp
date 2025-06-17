//package nl.tudelft.trustchain.musicdao.core.ipv8
//
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.isActive
//import kotlinx.coroutines.launch
//import nl.tudelft.ipv8.Peer
//import nl.tudelft.trustchain.musicdao.core.ipv8.ArtistBlockGossiper.Config
//import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.artistAnnounce.ArtistAnnounceBlock
//import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.payoutUpdate.PayoutUpdateBlock
//import javax.inject.Inject
//
//class PayoutUpdateGossiper
//    @Inject
//    constructor(
//        private val musicCommunity: MusicCommunity
//    ) {
//        // val contributionTxId
//        // var myContributionStatus
//
//        fun startGossip(coroutineScope: CoroutineScope) {
//            coroutineScope.launch {
//                while (coroutineScope.isActive) {
//                    gossip()
//                    delay(Config.DELAY)
//                }
//            }
//        }
//
//        private fun gossip() {
//            val payoutUpdateBlocks =
//                musicCommunity.database.getBlocksWithType(PayoutUpdateBlock.BLOCK_TYPE)
//                    .sortedByDescending { it.timestamp }
//
//            // for block in blocks:
//                // if block.transaction.transactionIds.conatins(contribtionTxId)
//                    // update status of contribution
//        }
//
//    }
