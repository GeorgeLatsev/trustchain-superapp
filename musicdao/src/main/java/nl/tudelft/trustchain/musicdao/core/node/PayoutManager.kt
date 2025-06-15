package nl.tudelft.trustchain.musicdao.core.node

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.core.node.persistence.ServerDatabase
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ContributionEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutEntity
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class PayoutManager
@Inject
constructor(
    private val database: ServerDatabase,
    private val walletService: WalletService,
    @Named("payoutWallet")
    private val serverWalletService: WalletService,
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
        serverWalletService.wallet().addCoinsReceivedEventListener { _, tx, prevBalance, newBalance ->
            Log.d("PayoutManager", "Coins received: ${tx.txId} - New balance: $newBalance")
            coroutineScope.launch {
                onTransactionReceived(tx.txId.toString(), newBalance.value - prevBalance.value)
            }
        }
        // TODO: check if there are any completed transactions when the service starts
    }

    private suspend fun onTransactionReceived(txid: String, amountSats: Long) {
        Log.d("PayoutManager", "Received transaction $txid with amount $amountSats")
        try {
            database.payoutDao.verifyContributionAndDistributeFunds(txid, amountSats);
        }
        catch (e: Exception) {
            Log.e("PayoutManager", "Failed to verify contribution $txid", e)
            return
        }
        Log.d("PayoutManager", "Successfully verified contribution $txid")
    }

    suspend fun registerContribution(
        transactionHash: String,
        contributorAddress: String,
        signature: String,
        artistSplits: Map<String, Float>,
    ) {
        Log.d(
            "PayoutManager",
            "Registering contribution from $contributorAddress with txid $transactionHash and splits $artistSplits"
        )
        database.payoutDao.insertContribution(
            ContributionEntity(
                transactionHash = transactionHash,
                contributorAddress = contributorAddress,
                signature = signature,
                artistSplits = artistSplits,
            )
        )

        val transaction = walletService.userTransactions.value.find { it.transaction.txId.toString() == transactionHash }
        if (transaction != null) {
            database.payoutDao.verifyContributionAndDistributeFunds(transaction.transaction.txId.toString(), transaction.value.value);
            return
        }
    }

    private suspend fun getOrCreateNextPayout(): String {
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

    suspend fun setPayoutStatus(payoutId: String, status: PayoutEntity.PayoutStatus) {
        Log.d("PayoutManager", "Setting payout status for ID $payoutId to $status")
        database.payoutDao.updatePayoutStatus(payoutId, status)

        if (status == PayoutEntity.PayoutStatus.SUBMITTED) {
            val payout = database.payoutDao.getPayoutWithArtistsById(payoutId)
            val txid = walletService.sendCoinsMulti(payout.artistPayouts.map { it.artistAddress to it.payoutAmount.toFloat() }.toMap())

            if (txid != null) {
                Log.d("PayoutManager", "Successfully sent payout for ID $payoutId with txid $txid")
                _currentPayoutId.value = null
            } else {
                Log.e("PayoutManager", "Failed to send payout for ID $payoutId")
            }
        }
    }

    suspend fun a() {
        Log.d("MusicDao", "Node a() called")
        val payoutId = getOrCreateNextPayout()
        Log.d("MusicDao", database.payoutDao.getArtistPayoutsForPayoutId(payoutId).toString())
        database.payoutDao.addFundsToArtist(payoutId, "123", 1000L)
        Log.d("MusicDao", database.payoutDao.getArtistPayoutsForPayoutId(payoutId).toString())
        Log.d("Wallet", "User wallet: ${walletService.protocolAddress()}")
        Log.d("Wallet", "Server wallet: ${serverWalletService.protocolAddress()}")
    }
}
