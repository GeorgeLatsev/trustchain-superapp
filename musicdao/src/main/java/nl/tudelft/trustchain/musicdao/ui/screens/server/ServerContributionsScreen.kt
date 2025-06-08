package nl.tudelft.trustchain.musicdao.ui.screens.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn

@ExperimentalMaterialApi
@Composable
fun ServerContributionsScreen(
    viewModel: ServerContributionsScreenViewModel
) {
    val unverifiedContributions by viewModel.unverifiedContributions.collectAsState(initial = emptyList())

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
        Text(
            text = "Unverified Contributions",
            style = MaterialTheme.typography.h6,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn {
            items(unverifiedContributions.size) { index ->
                val contribution = unverifiedContributions[index]

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
                        Text("Artist Splits: ${contribution.artistSplits}")
                    }
                }
            }
        }
    }
}
