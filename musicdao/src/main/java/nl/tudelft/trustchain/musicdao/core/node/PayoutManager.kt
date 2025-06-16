package nl.tudelft.trustchain.musicdao.core.node

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.modules.contribution.ContributionMessage
import nl.tudelft.trustchain.musicdao.core.node.persistence.ServerDatabase
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ContributionEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutEntity
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import org.bitcoinj.core.Address
import org.bitcoinj.core.Transaction
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class PayoutManager
@Inject
constructor(
    private val database: ServerDatabase,
    private val walletService: WalletService,
    @Named("payoutWallet")
    private val payoutWalletService: WalletService,
    private val musicCommunity: MusicCommunity,
    @ApplicationContext
    val context: Context
) {
    private val _currentPayoutId = MutableStateFlow<String?>(null)

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        if (!isEnabled()) {
            Log.d("PayoutManager", "PayoutManager is not enabled, skipping initialization")
        } else {
            Log.d("PayoutManager", "PayoutManager is enabled, initializing")
            init()
        }
    }

    fun isEnabled(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(PREF_KEY_IS_NODE_ENABLED, false)
    }

    fun enable() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit { putBoolean(PREF_KEY_IS_NODE_ENABLED, true) }
        Log.d("PayoutManager", "PayoutManager enabled, initializing")
        init()
    }

    private fun init() {
        musicCommunity.setMessageHandler(MusicCommunity.MessageId.CONTRIBUTION_MESSAGE) { packet ->
            val (peer, message) = packet.getAuthPayload(ContributionMessage.Deserializer);
            Log.d("PayoutManager", "Received contribution message (${message.getSignableString()}) from peer: $peer")

            coroutineScope.launch {
                registerContribution(message.txid, message.signature, message.artistSplits)
            }
        }

        payoutWalletService.wallet().addCoinsReceivedEventListener { _, tx, prevBalance, newBalance ->
            Log.d("PayoutManager", "Coins received: ${tx.txId} - New balance: $newBalance")

            val sender = getTransactionSenderAddress(tx)
            if (sender == null) {
                Log.d("PayoutManager", "No relevant sender address found for transaction: ${tx.txId}")
                return@addCoinsReceivedEventListener
            }

            coroutineScope.launch {
                onTransactionReceived(tx.txId.toString(), sender, newBalance.value - prevBalance.value)
            }
        }
        // TODO: check if there are any completed transactions when the service starts
    }

    private fun getTransactionSenderAddress(tx: Transaction): Address? {
        if (tx.isCoinBase()) {
            Log.d("PayoutManager", "Ignoring coinbase transaction: ${tx.txId}")
            return null
        }

        return walletService.protocolAddress(); // TODO: return the actual sender address from the transaction
    }

    private suspend fun onTransactionReceived(txid: String, sender: Address, amountSats: Long) {
        Log.d("PayoutManager", "Received transaction $txid with amount $amountSats")
        try {
            val contributions = database.payoutDao.getUnverifiedContributionsByTransactionId(txid)

            for (contribution in contributions) {
                if (contribution.status == ContributionEntity.ContributionStatus.UNVERIFIED) {
                    val signableString = ContributionMessage(contribution.transactionHash, contribution.artistSplits).getSignableString()

                    // val recoveredKey = ECKey.signedMessageToKey(signableString, contribution.signature) // can throw java.security.SignatureException: Signature truncated, expected 65 bytes and got 3

                    val isValid = true; // TODO: compare sender with recovered key
                    if (isValid) {
                        database.payoutDao.verifyContributionAndDistributeFunds(txid, amountSats)
                        return
                    }
                }
            }
        }
        catch (e: Exception) {
            Log.e("PayoutManager", "Failed to verify contribution $txid", e)
            return
        }
        Log.d("PayoutManager", "Successfully verified contribution $txid")
    }

    suspend fun registerContribution(
        transactionHash: String,
        signature: String,
        artistSplits: Map<String, Float>,
    ) {
        Log.d(
            "PayoutManager",
            "Registering contribution with txid $transactionHash and splits $artistSplits"
        )
        database.payoutDao.insertContribution(
            ContributionEntity(
                transactionHash = transactionHash,
                signature = signature,
                artistSplits = artistSplits,
            )
        )

        val transaction = walletService.userTransactions.value.find { it.transaction.txId.toString() == transactionHash }
        if (transaction != null) {
            val sender = getTransactionSenderAddress(transaction.transaction) ?: return
            onTransactionReceived(transaction.transaction.txId.toString(), sender, transaction.value.value)
        }
    }

    suspend fun getOrCreateNextPayout(): String {
        _currentPayoutId.value?.let { return it }

        database.payoutDao.getCurrentCollectingPayoutId()?.let {
            _currentPayoutId.value = it
            return it
        }

        val nextPayout = PayoutEntity()
        database.payoutDao.createPayout(nextPayout)
        _currentPayoutId.value = nextPayout.id
        return nextPayout.id
    }

    suspend fun setPayoutStatus(payoutId: String, status: PayoutEntity.PayoutStatus): PayoutEntity.PayoutStatus? {
        Log.d("PayoutManager", "Setting payout status for ID $payoutId to $status")
        database.payoutDao.updatePayoutStatus(payoutId, status)

        when (status) {
            PayoutEntity.PayoutStatus.AWAITING_FOR_CONFIRMATION -> {
                Log.d("PayoutManager", "Payout $payoutId is now awaiting confirmation")
                _currentPayoutId.value = null;
                getOrCreateNextPayout()
                return PayoutEntity.PayoutStatus.AWAITING_FOR_CONFIRMATION
            }
            PayoutEntity.PayoutStatus.SUBMITTED -> {
                val payout = database.payoutDao.getPayoutWithArtistsById(payoutId)
                if (payout.artistPayouts.isEmpty()) {
                    Log.e("PayoutManager", "No artist payouts found for payout ID $payoutId")
                    return PayoutEntity.PayoutStatus.SUBMITTED
                }

                val artistSplits = payout.artistPayouts.associate { it.artistAddress to it.payoutAmount.toFloat() }
                val txid = walletService.sendCoinsMulti(artistSplits)
                val transactionIds = payout.payout.transactionsIds

                val transaction = mutableMapOf(
                    "payoutId" to payoutId,
                    "payoutStatus" to status,
                    "artistSplits" to artistSplits,
                    "transactionIds" to transactionIds,
                    "transactionId" to (txid ?: ""),
                )

                musicCommunity.createProposalBlock(
                    "payoutStatusUpdate",
                    transaction,
                    IPv8Android.getInstance().myPeer.publicKey.keyToBin()
                )

                Log.i("PayoutManager", "Created proposal block for payout status update: $transaction")

                if (txid != null) {
                    Log.d("PayoutManager", "Successfully sent payout for ID $payoutId with txid $txid")
                    return PayoutEntity.PayoutStatus.SUBMITTED
                } else {
                    Log.e("PayoutManager", "Failed to send payout for ID $payoutId, reverting to awaiting confirmation")
                    // revert to awaiting confirmation as sending failed
                    database.payoutDao.updatePayoutStatus(payoutId, PayoutEntity.PayoutStatus.AWAITING_FOR_CONFIRMATION)
                    return PayoutEntity.PayoutStatus.AWAITING_FOR_CONFIRMATION
                }
            }
            else -> {
                Log.d("PayoutManager", "Payout $payoutId status changed to $status, no further action required")
                return null
            }
        }
    }
}
