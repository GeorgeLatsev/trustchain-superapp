package nl.tudelft.trustchain.musicdao.ui.screens.server

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import nl.tudelft.trustchain.musicdao.core.node.PayoutManager
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ServerScreenViewModel
@Inject
constructor(
    private val server: PayoutManager,
    private val walletService: WalletService,
    @Named("payoutWallet")
    private val serverWalletService: WalletService,
) : ViewModel() {

}
