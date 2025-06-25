package nl.tudelft.trustchain.musicdao.ui.screens.server

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import nl.tudelft.trustchain.musicdao.core.node.PayoutManager
import nl.tudelft.trustchain.musicdao.core.node.persistence.ServerDatabase
import nl.tudelft.trustchain.musicdao.core.node.persistence.PayoutDao
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ArtistPayoutEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ContributionEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutEntity
import org.junit.*
import org.junit.Assert.*
import kotlinx.coroutines.flow.first

@ExperimentalCoroutinesApi
class ServerPayoutDetailScreenViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: ServerPayoutDetailScreenViewModel
    private lateinit var payoutManager: PayoutManager
    private lateinit var serverDatabase: ServerDatabase
    private lateinit var payoutDao: PayoutDao

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        payoutManager = mockk(relaxed = true)
        serverDatabase = mockk(relaxed = true)
        payoutDao = mockk(relaxed = true)

        every { serverDatabase.payoutDao } returns payoutDao

        viewModel = ServerPayoutDetailScreenViewModel(serverDatabase, payoutManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `getContributionsForPayout returns correct flow from dao`() =
        runTest {
            val payoutId = "payout-1"
            val contributions =
                listOf(
                    mockk<ContributionEntity>(),
                    mockk<ContributionEntity>()
                )
            every { payoutDao.getContributionsByPayoutId(payoutId) } returns flowOf(contributions)

            val result = viewModel.getContributionsForPayout(payoutId)
            assertEquals(contributions, result.first())
        }

    @Test
    fun `getArtistPayouts returns correct flow from dao`() =
        runTest {
            val payoutId = "payout-2"
            val artistPayouts =
                listOf(
                    mockk<ArtistPayoutEntity>(),
                    mockk<ArtistPayoutEntity>()
                )
            every { payoutDao.getArtistPayoutsByPayoutId(payoutId) } returns flowOf(artistPayouts)

            val result = viewModel.getArtistPayouts(payoutId)
            assertEquals(artistPayouts, result.first())
        }

    @Test
    fun `getPayoutStatus returns correct status from dao`() =
        runTest {
            val payoutId = "payout-3"
            val status = PayoutEntity.PayoutStatus.AWAITING_FOR_CONFIRMATION
            coEvery { payoutDao.getPayoutStatus(payoutId) } returns status

            val result = viewModel.getPayoutStatus(payoutId)
            assertEquals(status, result)
        }

    @Test
    fun `setPayoutStatus calls payoutManager and returns status`() =
        runTest {
            val payoutId = "payout-4"
            val status = PayoutEntity.PayoutStatus.SUBMITTED
            coEvery { payoutManager.setPayoutStatus(payoutId, status) } returns status

            val result = viewModel.setPayoutStatus(payoutId, status)
            assertEquals(status, result)
            coVerify { payoutManager.setPayoutStatus(payoutId, status) }
        }
}
