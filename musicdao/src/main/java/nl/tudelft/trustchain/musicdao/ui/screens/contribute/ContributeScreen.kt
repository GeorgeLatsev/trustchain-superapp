package nl.tudelft.trustchain.musicdao.ui.screens.contribute

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.trustchain.musicdao.core.contribute.Contribution
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.ui.SnackbarHandler.coroutineScope
import nl.tudelft.trustchain.musicdao.ui.components.EmptyState
import nl.tudelft.trustchain.musicdao.ui.navigation.Screen
import nl.tudelft.trustchain.musicdao.ui.screens.profileMenu.CustomMenuItem
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.tudelft.trustchain.musicdao.core.cache.entities.ContributionEntity
import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository

@ExperimentalMaterialApi
@Composable
fun ContributeScreen(
    navController: NavController,
    contributeViewModel: ContributeViewModel
) {
    val isRefreshing by contributeViewModel.isRefreshing.observeAsState(false)
    val refreshState = rememberSwipeRefreshState(isRefreshing)
    val _contributions by contributeViewModel.cacheDatabase.dao.getAllContributions().collectAsState(initial = emptyList())
    val contributions = _contributions.reversed()
    val artistRepository = contributeViewModel.artistRepository

    fun bla() {
        coroutineScope?.launch {
            contributeViewModel.refresh()
        }
    }

    SwipeRefresh(
        state = refreshState,
        onRefresh = { contributeViewModel.refresh() }
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
        ) {
            CustomMenuItem(
                text = "Create a new Contribution",
                onClick = {
                    navController.navigate(Screen.NewContributionRoute.route)
                }
            )

            if (contributions.isEmpty()) {
                EmptyState(
                    firstLine = "No contributions yet.",
                    secondLine = "Start by creating a new contribution."
                )
            } else {
                // Make this Column take up all available vertical space
                Column(modifier = Modifier.weight(1f)) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(contributions.size) { index ->
                            ContributionItem(contribution = contributions[index], artistRepository)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContributionItem(
    contribution: ContributionEntity,
    artistRepository: ArtistRepository
) {
    var artistNames by remember(contribution) { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(contribution) {
        val names = contribution.artists.map { key ->
            if ('|' in key) {
                key.substringBefore('|')
            } else {
                withContext(Dispatchers.IO) {
                    artistRepository.getArtist(key)?.name ?: key
                }
            }
        }
        artistNames = names
    }

    val backgroundColor = if (contribution.satisfied) {
        androidx.compose.material.MaterialTheme.colors.primary
    } else {
        androidx.compose.material.MaterialTheme.colors.surface
    }

    Card(
        backgroundColor = backgroundColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Amount: ${contribution.amount} BTC")
            Text("Artists: ${artistNames.joinToString()}")
        }
    }
}
