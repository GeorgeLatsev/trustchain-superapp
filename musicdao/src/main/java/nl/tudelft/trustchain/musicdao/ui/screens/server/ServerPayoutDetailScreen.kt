package nl.tudelft.trustchain.musicdao.ui.screens.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Card
import androidx.compose.material.TabRow
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.tudelft.trustchain.musicdao.core.server.persistence.entities.ArtistPayoutEntity
import nl.tudelft.trustchain.musicdao.core.server.persistence.entities.ContributionEntity

@ExperimentalMaterialApi
@Composable
fun ServerPayoutDetailScreen(
    payoutId: String,
    viewModel: ServerPayoutDetailScreenViewModel
) {
    val tabs = listOf("Contributions", "Artist Payouts")
    var state by remember { mutableStateOf(0) }

    val contributions by viewModel.getContributionsForPayout(payoutId).collectAsState(initial = emptyList())
    val artistPayouts by viewModel.getArtistPayouts(payoutId).collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = state == index,
                    onClick = { state = index },
                    text = { Text(title) }
                )
            }
        }

        when (state) {
            0 -> ContributionsList(contributions)
            1 -> ArtistPayoutsList(artistPayouts)
        }
    }
}

@Composable
fun ContributionsList(contributions: List<ContributionEntity>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(contributions.size) { index ->
            val contribution = contributions[index]

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tx ID: ${contribution.transactionHash}", style = MaterialTheme.typography.body1)
                    Text("Amount: ${contribution.donationAmount} sats")
                    Text("Status: ${contribution.status}")
                }
            }
        }
    }
}

@Composable
fun ArtistPayoutsList(payouts: List<ArtistPayoutEntity>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        items(payouts.size) { index ->
            val payout = payouts[index]

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Artist: ${payout.artistAddress}", style = MaterialTheme.typography.body1)
                    Text("Amount Paid: ${payout.payoutAmount} sats")
                }
            }
        }
    }
}
