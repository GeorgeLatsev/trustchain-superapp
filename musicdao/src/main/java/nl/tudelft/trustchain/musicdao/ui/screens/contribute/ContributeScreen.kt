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
import nl.tudelft.trustchain.musicdao.ui.screens.dao.DaoViewModel
import nl.tudelft.trustchain.musicdao.ui.screens.profileMenu.CustomMenuItem
import nl.tudelft.trustchain.musicdao.ui.screens.wallet.BitcoinWalletViewModel

@ExperimentalMaterialApi
@Composable
fun ContributeScreen(
    navController: NavController,
    contributeViewModel: ContributeViewModel,
    bitcoinWalletViewModel: BitcoinWalletViewModel,
//    contributionPool: ContributionPool
) {
    val isRefreshing by contributeViewModel.isRefreshing.observeAsState(false)
    val refreshState = rememberSwipeRefreshState(isRefreshing)
    val contributions by contributeViewModel.contributions.collectAsState()

    val musicCommunity: MusicCommunity by lazy {
        IPv8Android.getInstance()
            .getOverlay() as? MusicCommunity
            ?: throw IllegalStateException("MusicCommunity is not configured")
    }

    fun bla() {
        coroutineScope?.launch {
            contributeViewModel.contributionPool.value.distributePooledContributions(bitcoinWalletViewModel)

//            // iterate over contributeViewMode.contributions
//            contributions.forEach { contribution ->
//                contributeViewModel.contributionPool.value.updateFlushedContributions(contribution)
//            }
            contributeViewModel.contributionPool.value.updateFlushedContributions()

            val serializedPool = contributeViewModel.contributionPool.value.serialize()
            val poolTransaction = mutableMapOf(
                "data" to serializedPool
            )

            val myPeer = IPv8Android.getInstance().myPeer

            musicCommunity.createProposalBlock("contribution-pool", poolTransaction, myPeer.publicKey.keyToBin())

            contributeViewModel.refresh()

//            contributeViewModel.clearContributions()
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
                            ContributionItem(contribution = contributions[index])
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(8.dp))

                CustomMenuItem(
                    text = "Distribute pooled contributions",
                    onClick = {
                        bla()
                    }
                )
            }
        }
    }
}

@Composable
fun ContributionItem(contribution: Contribution) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Amount: ${contribution.amount} BTC")
            Text("Artists: ${contribution.artists.joinToString { it.name }}")
        }
    }
}
