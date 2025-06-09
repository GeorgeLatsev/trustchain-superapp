package nl.tudelft.trustchain.musicdao.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ListeningStatsScreen(viewModel: MusicStatsViewModel = hiltViewModel()) {
    val stats by viewModel.minutesPerArtist.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Minutes Listened Per Artist", style = MaterialTheme.typography.h6)
        Spacer(modifier = Modifier.height(16.dp))

        if (stats.isEmpty()) {
            Text("No listening data available.")
        } else {
            LazyColumn {
                items(stats.entries.toList()) { (artistId, minutes) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = 4.dp
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Artist: $artistId", style = MaterialTheme.typography.subtitle1)
                            Text("Listened: ${"%.2f".format(minutes)} min")
                        }
                    }
                }
            }
        }
    }
}
