package nl.tudelft.trustchain.musicdao.ui.screens.contribute

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.ui.SnackbarHandler
import nl.tudelft.trustchain.musicdao.ui.screens.profileMenu.CustomMenuItem
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel

@Composable
fun ContributeCreateScreen(
    bitcoinWalletViewModel: BitcoinWalletViewModel,
    contributeViewModel: ContributeViewModel,
    navController: NavController
) {
    val amount = rememberSaveable { mutableStateOf("0.1") }
    val ipAddress = rememberSaveable { mutableStateOf("") }
    val walletKey = rememberSaveable { mutableStateOf("") }
    val coroutine = rememberCoroutineScope()

    val context = LocalContext.current

    val isFirstContribution by contributeViewModel.isFirstContribution.collectAsState()

    // Load wallet info if it exists
    LaunchedEffect(Unit) {
        contributeViewModel.checkFirstContribution()
    }

    fun send() {
        val amountFloat = amount.value.toFloat()

        if (amount.value.isEmpty() || amountFloat <= 0) {
            SnackbarHandler.displaySnackbar("Please enter a valid amount")
            return
        }

        // Check if enough balance available
        val confirmedBalance = bitcoinWalletViewModel.confirmedBalance.value
        if (confirmedBalance == null || confirmedBalance.isZero || confirmedBalance.isNegative) {
            SnackbarHandler.displaySnackbar("You don't have enough funds to make a donation")
            return
        }

        // Check if wallet information is provided for first-time contributors
        if (isFirstContribution && (ipAddress.value.isEmpty() || walletKey.value.isEmpty())) {
            SnackbarHandler.displaySnackbar("Please provide IP address and wallet key")
            return
        }

        coroutine.launch {
            val result = if (isFirstContribution) {
                contributeViewModel.contributeFirstTime(
                    amountFloat,
                    ipAddress.value,
                    walletKey.value
                )
            } else {
                contributeViewModel.contribute(amountFloat)
            }

            if (result) {
//                contributeViewModel.createContribution(
//                    amount.value.toLong(),
//                    context
//                )
//

                contributeViewModel.refresh()

                SnackbarHandler.displaySnackbar("Contribution created")
                navController.popBackStack()
            } else {
                SnackbarHandler.displaySnackbar("Contribution failed")
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

        // First-time contributor fields
        if (isFirstContribution) {
            Text(
                text = "First Time Contribution",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
            )

            Text(
                text = "IP Address",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 5.dp)
            )
            OutlinedTextField(
                value = ipAddress.value,
                onValueChange = { ipAddress.value = it },
                modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth()
            )

            Text(
                text = "Public Wallet Key",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 5.dp)
            )
            OutlinedTextField(
                value = walletKey.value,
                onValueChange = { walletKey.value = it },
                modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        CustomMenuItem(
            text = if (isFirstContribution) "Confirm First Contribution" else "Confirm Contribution",
            onClick = { send() }
        )
    }
}
