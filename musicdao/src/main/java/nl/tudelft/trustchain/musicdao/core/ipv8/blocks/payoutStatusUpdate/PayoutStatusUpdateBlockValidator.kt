package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.payoutStatusUpdate

import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.attestation.trustchain.validation.TransactionValidator
import nl.tudelft.ipv8.attestation.trustchain.validation.ValidationResult
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish.ReleasePublishBlock
import javax.inject.Inject


class PayoutStatusUpdateBlockValidator
    @Inject
    constructor(val musicCommunity: MusicCommunity) :
    TransactionValidator {
        override fun validate(
            block: TrustChainBlock,
            database: TrustChainStore
        ): ValidationResult {
            return if (validate(block)) {
                ValidationResult.Valid
            } else {
                ValidationResult.Invalid(listOf("Not all information included."))
            }
        }

        private fun validate(block: TrustChainBlock): Boolean {
            return validateTransaction(block.transaction)
        }

        fun validateTransaction(transaction: TrustChainTransaction): Boolean {

            val payoutId = transaction["payoutId"]
            val payoutStatus = transaction["payoutStatus"]
            val artistSplits = transaction["artistSplits"]
            val transactionIds = transaction["transactionIds"]
            val payoutTransactionId = transaction["payoutTransactionId"]

            return (
                payoutId is String && payoutId.isNotEmpty() && transaction.containsKey("payoutId") &&
                payoutStatus is String && payoutStatus.isNotEmpty() && transaction.containsKey("payoutStatus") &&
                artistSplits is Map<*, *> && artistSplits.isNotEmpty() && transaction.containsKey("artistSplits") &&
                transactionIds is List<*> && transactionIds.isNotEmpty() && transaction.containsKey("transactionIds") &&
                (payoutTransactionId == null || payoutTransactionId is String) && transaction.containsKey("payoutTransactionId")
            )

        }

        companion object {
            const val BLOCK_TYPE = PayoutUpdateStatusBlock.BLOCK_TYPE
        }
    }
