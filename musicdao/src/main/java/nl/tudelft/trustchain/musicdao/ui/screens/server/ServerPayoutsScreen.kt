package nl.tudelft.trustchain.musicdao.ui.screens.server

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutEntity
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Card
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import nl.tudelft.trustchain.musicdao.ui.navigation.Screen
import nl.tudelft.trustchain.musicdao.ui.styling.MusicDAOTheme


@ExperimentalMaterialApi
@Composable
fun ServerPayoutsScreen(
    navController: NavController,
    serverPayoutsScreenViewModel: ServerPayoutsScreenViewModel
) {
    val payoutsWithArtists by serverPayoutsScreenViewModel.payoutsWithArtists.collectAsState()

    val formatter = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    }
    Log.d("ServerPayoutsScreen", "Payouts with artists: $payoutsWithArtists")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(payoutsWithArtists.size) { index ->
            val payout = payoutsWithArtists[index].payout
            val artistPayouts = payoutsWithArtists[index].artistPayouts
            val totalAmount = artistPayouts.sumOf { it.payoutAmount }

            val backgroundColor = when (payout.payoutStatus) {
                PayoutEntity.PayoutStatus.COLLECTING -> MusicDAOTheme.DarkColors.primary
                PayoutEntity.PayoutStatus.AWAITING_FOR_CONFIRMATION -> Color(0xFFfffac8)
                else -> Color.LightGray
            }

            Card(
                backgroundColor = backgroundColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable {
                        navController.navigate( Screen.ServerPayoutDetail.createRoute(payout.id))
                    },
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Payout ID: ${payout.id}", style = MaterialTheme.typography.body1)
                    Text("Status: ${payout.payoutStatus}", style = MaterialTheme.typography.body2)
                    Text("Artists included: ${artistPayouts.size}", style = MaterialTheme.typography.body2)
                    Text("Total: $totalAmount sats", style = MaterialTheme.typography.body2)

                    val dateText = try {
                        formatter.format(java.util.Date(payout.createdAt))
                    } catch (e: Exception) {
                        "N/A"
                    }

                    Text("Created: $dateText", style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}
