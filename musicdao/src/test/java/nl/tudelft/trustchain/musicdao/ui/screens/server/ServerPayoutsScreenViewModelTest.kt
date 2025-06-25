package nl.tudelft.trustchain.musicdao.ui.screens.server

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import nl.tudelft.trustchain.musicdao.core.node.PayoutManager
import nl.tudelft.trustchain.musicdao.core.node.persistence.ServerDatabase
import nl.tudelft.trustchain.musicdao.core.node.persistence.PayoutDao
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutWithArtists
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ArtistPayoutEntity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class ServerPayoutsScreenViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: ServerPayoutsScreenViewModel
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

        viewModel = ServerPayoutsScreenViewModel(serverDatabase, payoutManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `ensureACollectingPayoutIsShown calls getOrCreateNextPayout on payoutManager`() = runTest {
        coEvery { payoutManager.getOrCreateNextPayout() } returns "new-payout-id"

        viewModel.ensureACollectingPayoutIsShown()

        coVerify(exactly = 1) { payoutManager.getOrCreateNextPayout() }
    }

    private fun createMockPayoutWithArtists(
        id: String,
        status: PayoutEntity.PayoutStatus,
        artists: List<Pair<String, Long>>
    ): PayoutWithArtists {
        val payout = PayoutEntity(
            id = id,
            payoutStatus = status,
            createdAt = System.currentTimeMillis()
        )

        val artistPayouts = artists.map { (address, amount) ->
            ArtistPayoutEntity(
                payoutId = id,
                artistAddress = address,
                payoutAmount = amount
            )
        }

        return PayoutWithArtists(payout, artistPayouts)
    }
}
