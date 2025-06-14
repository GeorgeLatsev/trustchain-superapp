package nl.tudelft.trustchain.musicdao.ui.screens.server

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import nl.tudelft.trustchain.musicdao.core.node.persistence.ServerDatabase
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ContributionEntity
import javax.inject.Inject

@HiltViewModel
class ServerContributionsScreenViewModel
@Inject
constructor(
    private val db: ServerDatabase
) : ViewModel() {

    val unverifiedContributions: Flow<List<ContributionEntity>> =
        db.payoutDao.getUnverifiedContributions()
}
