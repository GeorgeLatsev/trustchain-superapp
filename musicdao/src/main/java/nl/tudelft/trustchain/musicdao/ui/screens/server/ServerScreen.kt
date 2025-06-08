package nl.tudelft.trustchain.musicdao.ui.screens.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import nl.tudelft.trustchain.musicdao.ui.navigation.Screen

@ExperimentalMaterialApi
@Composable
fun ServerScreen(
    navController: NavController,
    serverScreenViewModel: ServerScreenViewModel
) {
    val coroutine = rememberCoroutineScope()

    Column {
        ListItem(
            text = { Text(text = "Server information") },
            modifier = Modifier.clickable {
                coroutine.launch {
                    serverScreenViewModel.seversome()
                }
            }
        )
        ListItem(
            text = { Text(text = "Payouts") },
            modifier = Modifier.clickable {
                navController.navigate(Screen.ServerPayouts.route)
            }
        )
        ListItem(
            text = { Text(text = "Unverified contributions") },
            modifier = Modifier.clickable {
                navController.navigate(Screen.ServerContributions.route)
            }
        )
        ListItem(
            text = { Text(text = "Test") },
            modifier = Modifier.clickable {
                coroutine.launch {
                    serverScreenViewModel.test()
                }
            }
        )
    }
}
