package nl.tudelft.trustchain.musicdao.ui.screens.contribute

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService.Companion.SATS_PER_BITCOIN
import nl.tudelft.trustchain.musicdao.ui.SnackbarHandler
import nl.tudelft.trustchain.musicdao.ui.screens.profileMenu.CustomMenuItem
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import java.math.BigDecimal

@Composable
fun ContributeCreateScreen(
    bitcoinWalletViewModel: BitcoinWalletViewModel,
    contributeViewModel: ContributeViewModel,
    navController: NavController
) {
    val amount = rememberSaveable { mutableStateOf("0.1") }
    val coroutine = rememberCoroutineScope()

    fun send() {
        val amountFloat = amount.value.toFloat()

        if (amount.value.isEmpty() || amountFloat <= 0) {
            SnackbarHandler.displaySnackbar("Please enter a valid amount")
            return
        }

        // Check if enough balance available
        val confirmedBalance = bitcoinWalletViewModel.confirmedBalance.value
        val amountSats = (BigDecimal(amountFloat.toDouble()) * SATS_PER_BITCOIN).toLong();
        if (confirmedBalance == null || confirmedBalance.isZero || confirmedBalance.isNegative || confirmedBalance.value < amountSats) {
            SnackbarHandler.displaySnackbar("You don't have enough funds to make a donation")
            return
        }

        coroutine.launch {
            val result = contributeViewModel.contribute(amountFloat)
            when (result) {
                ContributeViewModel.ContributionStatus.NO_LISTEN_ACTIVITY -> {
                    SnackbarHandler.displaySnackbar("You haven't listened to any artists yet")
                    return@launch
                }
                ContributeViewModel.ContributionStatus.NO_NODE_FOUND -> {
                    SnackbarHandler.displaySnackbar("No payout node found, please try again later")
                    return@launch
                }
                ContributeViewModel.ContributionStatus.FAILURE -> {
                    SnackbarHandler.displaySnackbar("Contribution failed")
                    return@launch
                }
                ContributeViewModel.ContributionStatus.SUCCESS -> {
                    contributeViewModel.refresh()

                    SnackbarHandler.displaySnackbar("Contribution created")
                    navController.popBackStack()
                }
            }
        }
    }

    Column(modifier = Modifier.padding(20.dp)) {
        Text(
            text = "Your balance is ${bitcoinWalletViewModel.confirmedBalance.value?.toFriendlyString() ?: "0.00 BTC"}",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Text(
            text = "Amount",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 5.dp)
        )
        OutlinedTextField(
            value = amount.value,
            onValueChange = { amount.value = it },
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Row {
            Button(
                onClick = {
                    amount.value = "0.001"
                },
                modifier = Modifier.padding(end = 10.dp)
            ) {
                Text("0.001")
            }
            Button(
                onClick = {
                    amount.value = "0.01"
                },
                modifier = Modifier.padding(end = 10.dp)
            ) {
                Text("0.01")
            }
            Button(
                onClick = {
                    amount.value = "0.1"
                },
                modifier = Modifier.padding(end = 10.dp)
            ) {
                Text("0.1")
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        CustomMenuItem(text = "Confirm contribution", onClick = { send() })
    }
}
