package nl.tudelft.trustchain.musicdao.ui.screens.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import nl.tudelft.trustchain.musicdao.core.node.PayoutManager
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutWithArtists
import nl.tudelft.trustchain.musicdao.core.node.persistence.ServerDatabase
import javax.inject.Inject

@HiltViewModel
class ServerPayoutsScreenViewModel
@Inject
constructor(
    val db: ServerDatabase,
    val payoutManager: PayoutManager
) : ViewModel() {

    val payoutsWithArtists: StateFlow<List<PayoutWithArtists>> =
        db.payoutDao.getAllPayoutsWithArtists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    suspend fun ensureACollectingPayoutIsShown() {
        payoutManager.getOrCreateNextPayout()
    }
}
