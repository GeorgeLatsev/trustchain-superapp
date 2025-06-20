package nl.tudelft.trustchain.musicdao.ui.screens.server

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import nl.tudelft.trustchain.musicdao.core.node.PayoutManager
import nl.tudelft.trustchain.musicdao.core.node.persistence.ServerDatabase
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ArtistPayoutEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ContributionEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutEntity
import javax.inject.Inject

@HiltViewModel
class ServerPayoutDetailScreenViewModel
@Inject
constructor(
    private val database: ServerDatabase,
    private val payoutManager: PayoutManager
) : ViewModel() {

    fun getContributionsForPayout(payoutId: String): Flow<List<ContributionEntity>> {
        return database.payoutDao.getContributionsByPayoutId(payoutId)
    }

    fun getArtistPayouts(payoutId: String): Flow<List<ArtistPayoutEntity>> {
        return database.payoutDao.getArtistPayoutsByPayoutId(payoutId)
    }

    suspend fun getPayoutStatus(payoutId: String): PayoutEntity.PayoutStatus? {
        return database.payoutDao.getPayoutStatus(payoutId)
    }

    suspend fun setPayoutStatus(payoutId: String, status: PayoutEntity.PayoutStatus): PayoutEntity.PayoutStatus? {
        return payoutManager.setPayoutStatus(payoutId, status)
    }
}
