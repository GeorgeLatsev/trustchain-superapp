package nl.tudelft.trustchain.musicdao.core.contribute

import android.util.Log
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.modules.contribution.ContributionMessage
import nl.tudelft.trustchain.musicdao.core.node.PayoutManager
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class PayoutService
@Inject
constructor(
    private val musicCommunity: MusicCommunity,
    private val payoutManager: PayoutManager,
    private val walletService: WalletService,
    @Named("payoutWallet")
    private val payoutWalletService: WalletService,
) {
    private val _isNodeFound = MutableStateFlow(false)
    val isNodeFound: StateFlow<Boolean> = _isNodeFound

    private val _nodeAddress = MutableStateFlow<String?>(null)
    val nodeAddress: StateFlow<String?> = _nodeAddress

    private var payoutWalletAddress: String? = null;

    init {
        if (payoutManager.isEnabled()) {
            Log.i("PayoutService", "Payout node is enabled, initializing")

            _nodeAddress.value = musicCommunity.myPeer.address.toString()
            _isNodeFound.value = true
            payoutWalletAddress = payoutWalletService.protocolAddress().toString()
        } else {
            Log.i("PayoutService", "Initializing, setting up payout node peer listener")
            musicCommunity.setOnPayoutNodePeerFound { peer, bitcoinAddress ->
                Log.i("PayoutService", "Payout node peer found: $peer with wallet address: $bitcoinAddress")

                _nodeAddress.value = peer.address.toString()
                _isNodeFound.value = true;
                payoutWalletAddress = bitcoinAddress
            }
        }
    }

    fun makeContribution(amount: Float, split: Map<String, Float>): Boolean {
        if (isNodeFound.value.not() || payoutWalletAddress.isNullOrEmpty()) {
            Log.w("PayoutService", "No payout node found, cannot make contribution")
            return false
        }

        Log.i("PayoutService", "Making contribution: $amount, split: $split")

        val txid = walletService.sendCoins(payoutWalletAddress!!, amount.toString())
        if (txid == null) {
            Log.e("PayoutService", "Failed to send coins for contribution")
            return false
        }

        val msg = ContributionMessage(
            txid = txid,
            artistSplits = split,
        )
        msg.signature = walletService.signMessage(msg.getSignableString()).toString()

        musicCommunity.sendPacketToPayoutNode(MusicCommunity.MessageId.CONTRIBUTION_MESSAGE, msg)
        Log.i("PayoutService", "Contribution sent: $msg")

        return true
    }
}
