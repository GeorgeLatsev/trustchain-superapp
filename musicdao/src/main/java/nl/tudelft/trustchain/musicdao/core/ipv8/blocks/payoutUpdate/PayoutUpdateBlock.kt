//package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.payoutUpdate
//
//import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
//
//data class PayoutUpdateBlock(
//    val payoutId: String,
//    val payoutStatus: String,
//    val artistSplits: Map<String, Float>,
//    val transactionIds: List<String>,
//    val payoutTransactionId: String? = null
//) {
//    companion object {
//        const val BLOCK_TYPE = "payout_status_update"
//
//        fun fromTrustChainTransaction(transaction: TrustChainTransaction): PayoutUpdateBlock {
//            val payoutId = transaction["payoutId"] as String
//            val payoutStatus = transaction["payoutStatus"] as String
//            val artistSplits = transaction["artistSplits"] as Map<String, Float>
//            val transactionIds = transaction["transactionIds"] as List<String>
//            val payoutTransactionId = transaction["payoutTransactionId"] as? String
//
//            return PayoutUpdateBlock(
//                payoutId,
//                payoutStatus,
//                artistSplits,
//                transactionIds,
//                payoutTransactionId
//            )
//        }
//    }
//}
//
//
//
//
//
////val transaction = mutableMapOf(
////    "payoutId" to payoutId,
////    "payoutStatus" to status.toString(),
////    "artistSplits" to artistSplits,
////    "transactionIds" to transactionIds,
////    "payoutTransactionId" to (txid ?: ""),
////)
