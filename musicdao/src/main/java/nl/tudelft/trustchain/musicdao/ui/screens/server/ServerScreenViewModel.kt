package nl.tudelft.trustchain.musicdao.ui.screens.server

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import nl.tudelft.trustchain.musicdao.core.server.PayoutManager
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ServerScreenViewModel
@Inject
constructor(
    private val server: PayoutManager,
    private val walletService: WalletService,
    @Named("serverWallet") private val serverWalletService: WalletService
) : ViewModel() {
    suspend fun seversome() {
        Log.e("aaaaa", "server")
        server.a()
    }

    suspend fun test() {

        val txId = walletService.sendCoins(serverWalletService.protocolAddress().toString(), "0.1");
        if (txId == null) {
            Log.e("ServerScreenViewModel", "Failed to send coins");
            return;
        }
        server.registerContribution(
            transactionHash = txId,
            contributorAddress = walletService.protocolAddress().toString(),
            signature = "test",
            artistSplits = mapOf("test" to 0.5f, "test2" to 0.5f)
        )
    }
}
