package nl.tudelft.trustchain.musicdao.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import nl.tudelft.trustchain.musicdao.core.contribute.PayoutService
import nl.tudelft.trustchain.musicdao.ui.screens.profile.MyProfileScreenViewModel

@ExperimentalMaterialApi
@Composable
fun Drawer(
    navController: NavController,
    profileScreenViewModel: MyProfileScreenViewModel
) {
    val profile = profileScreenViewModel.profile.collectAsState()

    Column {
        Column(
            modifier =
                Modifier.padding(
                    start = 15.dp,
                    end = 15.dp,
                    top = 20.dp,
                    bottom = 20.dp
                )
        ) {
            Column(modifier = Modifier.padding(bottom = 20.dp)) {
                Box(
                    modifier =
                        Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                )
            }
            Row {
                Column {
                    Text(profile.value?.name ?: "[name]", style = MaterialTheme.typography.h6)
                    Text(
                        profileScreenViewModel.publicKey(),
                        style = MaterialTheme.typography.subtitle1
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {}) {
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }
        }
        Divider()
        Column {
            DropdownMenuItem(onClick = { navController.navigate(Screen.Debug.route) }) {
                Text("Active Torrents")
            }
            DropdownMenuItem(onClick = { navController.navigate(Screen.Settings.route) }) {
                Text("Settings")
            }
            PayoutDropdownMenuItem(
                profileScreenViewModel = profileScreenViewModel
            )
        }
    }
}

@Composable
fun PayoutDropdownMenuItem(
    profileScreenViewModel: MyProfileScreenViewModel
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    var stepsRemaining by remember { mutableStateOf(4) }
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isNodeFound by profileScreenViewModel.isNodeFound.collectAsState()
    val nodeAddress by profileScreenViewModel.nodeAddress.collectAsState()

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Become the payout node") },
            text = {
                Text("Would you like to become the payout node?")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    profileScreenViewModel.enablePayoutNode()
                    Toast.makeText(context, "Now acting as the payout node", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    DropdownMenuItem(
        onClick = {
            if (isNodeFound) {
                return@DropdownMenuItem
            }

            val currentTime = System.currentTimeMillis()

            if (currentTime - lastTapTime < 600) {
                tapCount++
                stepsRemaining = 4 - tapCount
                if (tapCount >= 4) {
                    tapCount = 0
                    stepsRemaining = 4
                    showDialog = true
                }
            } else {
                tapCount = 1
                stepsRemaining = 3
            }

            lastTapTime = currentTime
        }
    ) {
        Text(
            if (isNodeFound)
                "Payout node: ${nodeAddress ?: "unknown"}"
            else
                "Payout node: searching..."
        )
    }
}
