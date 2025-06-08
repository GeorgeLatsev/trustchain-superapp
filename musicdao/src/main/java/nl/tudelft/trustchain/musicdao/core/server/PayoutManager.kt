package nl.tudelft.trustchain.musicdao.core.server

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.core.server.persistence.ServerDatabase
import nl.tudelft.trustchain.musicdao.core.server.persistence.entities.ContributionEntity
import nl.tudelft.trustchain.musicdao.core.server.persistence.entities.PayoutEntity
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class PayoutManager @Inject constructor(
    private val database: ServerDatabase,
    private val walletService: WalletService,
    @Named("serverWallet")
    private val serverWalletService: WalletService,
) {
    private val _currentPayoutId = MutableStateFlow<String?>(null)
    val currentPayoutId: StateFlow<String?> = _currentPayoutId

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        serverWalletService.wallet().addCoinsReceivedEventListener { wallet, tx, prevBalance, newBalance ->
            Log.d("PayoutManager", "Coins received: ${tx.txId} - New balance: $newBalance")
            coroutineScope.launch {
                onTransactionReceived(tx.txId.toString(), newBalance.value.toLong() - prevBalance.value.toLong())
            }
        }
        // TODO: check if there are any completed transactions when the service starts
    }

    private suspend fun onTransactionReceived(txid: String, amountSats: Long) {
        Log.d("PayoutManager", "Received transaction $txid with amount $amountSats")
        val payoutId = getOrCreateNextPayout()
        Log.d("PayoutManager", "Current payout ID: $payoutId")
        database.payoutDao.verifyContributionAndDistributeFunds(txid, amountSats);
        Log.d("PayoutManager", "Funds distributed for transaction $txid, ${database.payoutDao.getArtistPayoutsForPayoutId(payoutId).toString()})")

    }

    suspend fun registerContribution(
        transactionHash: String,
        contributorAddress: String,
        signature: String,
        artistSplits: Map<String, Float>,
    ) {
        Log.d("PayoutManager", serverWalletService.walletTransactions().toString())
        Log.d("PayoutManager", "Registering contribution from $contributorAddress with txid $transactionHash with splits $artistSplits")
        database.payoutDao.insertContribution(ContributionEntity(
            transactionHash = transactionHash,
            contributorAddress = contributorAddress,
            signature = signature,
            artistSplits = artistSplits,
        ))
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

    suspend fun goToNextStage() {
        Log.d("PayoutManager", "Going to next payout stage")
        val payoutId = getOrCreateNextPayout()
        database.payoutDao.goToNextStage(payoutId)
        Log.d("PayoutManager", "Current payout ID after going to next stage: $payoutId")
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
