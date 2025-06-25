package nl.tudelft.trustchain.musicdao.ui.screens.contribute

        import android.util.Log
        import androidx.arch.core.executor.testing.InstantTaskExecutorRule
        import io.mockk.*
        import kotlinx.coroutines.Dispatchers
        import kotlinx.coroutines.ExperimentalCoroutinesApi
        import kotlinx.coroutines.flow.MutableStateFlow
        import kotlinx.coroutines.test.resetMain
        import kotlinx.coroutines.test.runTest
        import kotlinx.coroutines.test.setMain
        import nl.tudelft.ipv8.android.IPv8Android
        import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
        import nl.tudelft.trustchain.musicdao.core.cache.CacheDao
        import nl.tudelft.trustchain.musicdao.core.cache.CacheDatabase
        import nl.tudelft.trustchain.musicdao.core.cache.entities.ContributionEntity
        import nl.tudelft.trustchain.musicdao.core.contribute.ContributionRepository
        import nl.tudelft.trustchain.musicdao.core.contribute.PayoutService
        import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
        import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.listenActivity.ListenActivityBlockRepository
        import nl.tudelft.trustchain.musicdao.core.repositories.ArtistRepository
        import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist
        import org.junit.After
        import org.junit.Assert.assertEquals
        import org.junit.Assert.assertTrue
        import org.junit.Before
        import org.junit.Rule
        import org.junit.Test

        @ExperimentalCoroutinesApi
        class ContributeViewModelTest {

            @get:Rule
            val instantTaskExecutorRule = InstantTaskExecutorRule()

            private lateinit var viewModel: ContributeViewModel
            private lateinit var contributionRepository: ContributionRepository
            private lateinit var listenActivityRepository: ListenActivityBlockRepository
            private lateinit var artistRepository: ArtistRepository
            private lateinit var payoutService: PayoutService
            private lateinit var cacheDatabase: CacheDatabase
            private lateinit var cacheDao: CacheDao
            private lateinit var musicCommunity: MusicCommunity

            private val isNodeFound = MutableStateFlow(true)
            private val nodeAddress = MutableStateFlow("127.0.0.1:8090")

            @Before
            fun setup() {
                Dispatchers.setMain(Dispatchers.Unconfined)

                mockkObject(IPv8Android)

                contributionRepository = mockk(relaxed = true)
                listenActivityRepository = mockk(relaxed = true)
                artistRepository = mockk(relaxed = true)
                payoutService = mockk(relaxed = true)
                cacheDatabase = mockk(relaxed = true)
                cacheDao = mockk(relaxed = true)
                musicCommunity = mockk(relaxed = true)

                every { cacheDatabase.dao } returns cacheDao
                every { payoutService.isNodeFound } returns isNodeFound
                every { payoutService.nodeAddress } returns nodeAddress

                mockkStatic(Log::class)
                every { Log.d(any(), any()) } returns 0
                every { Log.i(any(), any()) } returns 0
                every { Log.w(any<String>(), any<String>()) } returns 0
                every { Log.e(any(), any()) } returns 0

                val mockBlock = mockk<TrustChainBlock>(relaxed = true)

                val mockPeer = mockk<nl.tudelft.ipv8.Peer>()
                val mockKey = mockk<nl.tudelft.ipv8.keyvault.PublicKey>()
                every { mockPeer.publicKey } returns mockKey
                every { mockKey.keyToBin() } returns ByteArray(10)
                every { IPv8Android.getInstance() } returns mockk {
                    every { myPeer } returns mockPeer
                    every { getOverlay<MusicCommunity>() } returns musicCommunity
                }

                every { musicCommunity.createProposalBlock(any(), any(), any()) } returns mockBlock

                viewModel = ContributeViewModel(
                    contributionRepository,
                    listenActivityRepository,
                    artistRepository,
                    payoutService,
                    cacheDatabase
                )
            }

            @After
            fun tearDown() {
                Dispatchers.resetMain()
                unmockkAll()
            }

            @Test
            fun `when no listen activity data exists, contribution should fail`() = runTest {
                every { listenActivityRepository.getMinutesPerArtist() } returns emptyMap()
                val result = viewModel.contribute(100f)
                assertEquals(ContributeViewModel.ContributionStatus.NO_LISTEN_ACTIVITY, result)
            }

            @Test
            fun `when no payout node is found, contribution should fail`() = runTest {
                val listenData = mapOf("artist1" to 60.0)
                every { listenActivityRepository.getMinutesPerArtist() } returns listenData

                isNodeFound.value = false

                val result = viewModel.contribute(100f)

                assertEquals(ContributeViewModel.ContributionStatus.NO_NODE_FOUND, result)
            }

            @Test
            fun `when contribution succeeds, should create proposal block and cache contribution`() = runTest {
                val listenData = mapOf("artist1" to 60.0, "artist2" to 30.0)
                val amount = 100f
                val txid = "transaction123"

                every { listenActivityRepository.getMinutesPerArtist() } returns listenData
                every { payoutService.makeContribution(amount, any()) } returns txid

                val result = viewModel.contribute(amount)

                assertEquals(ContributeViewModel.ContributionStatus.SUCCESS, result)
                coVerify { cacheDao.insert(match<ContributionEntity> { it.id == txid && it.amount == amount }) }
                verify { listenActivityRepository.clearListenActivityData() }
                verify { musicCommunity.createProposalBlock("contribute-proposal", any(), any()) }
            }

            @Test
            fun `when payment transaction fails, contribution should fail`() = runTest {
                val listenData = mapOf("artist1" to 60.0, "artist2" to 30.0)
                every { listenActivityRepository.getMinutesPerArtist() } returns listenData
                every { payoutService.makeContribution(any(), any()) } returns null

                val result = viewModel.contribute(100f)

                assertEquals(ContributeViewModel.ContributionStatus.FAILURE, result)
            }

            @Test
            fun `when artists have bitcoin addresses in repository, they should be used in contribution split`() = runTest {
                val artistKey1 = "artist1publickey"
                val artistKey2 = "artist2publickey"
                val address1 = "bc1address1"
                val address2 = "bc1address2"
                val listenData = mapOf(artistKey1 to 60.0, artistKey2 to 30.0)
                val artist1 = Artist(artistKey1, address1, "Artist 1", "", "", emptyList())
                val artist2 = Artist(artistKey2, address2, "Artist 2", "", "", emptyList())

                coEvery { artistRepository.getArtist(artistKey1) } returns artist1
                coEvery { artistRepository.getArtist(artistKey2) } returns artist2
                every { listenActivityRepository.getMinutesPerArtist() } returns listenData
                every { payoutService.makeContribution(100f, match {
                    it.containsKey(address1) && it.containsKey(address2)
                }) } returns "tx123"

                val result = viewModel.contribute(100f)

                assertEquals(ContributeViewModel.ContributionStatus.SUCCESS, result)
                verify { payoutService.makeContribution(100f, match {
                    val sum = it.values.sum()
                    Math.abs(sum - 1.0f) < 0.0001f
                }) }
            }

            @Test
            fun `when artist keys contain bitcoin address format, they should be used directly`() = runTest {
                val artistName = "Artist 1"
                val bitcoinAddress = "bc1example"
                val artistKey = "$artistName|$bitcoinAddress"
                val listenData = mapOf(artistKey to 60.0)

                every { listenActivityRepository.getMinutesPerArtist() } returns listenData
                every { payoutService.makeContribution(100f, match {
                    it.containsKey(bitcoinAddress)
                }) } returns "tx123"

                val result = viewModel.contribute(100f)

                assertEquals(ContributeViewModel.ContributionStatus.SUCCESS, result)
                verify { payoutService.makeContribution(100f, match {
                    it.containsKey(bitcoinAddress) && Math.abs(it[bitcoinAddress]!! - 1.0f) < 0.0001f
                }) }
            }

            @Test
            fun `contribute should correctly calculate proportions when multiple artists have different listen times`() = runTest {
                val listenData = mapOf(
                    "artist1" to 100.0,
                    "artist2" to 25.0,
                    "artist3" to 75.0
                )
                val amount = 200f
                val txid = "transaction456"

                every { listenActivityRepository.getMinutesPerArtist() } returns listenData

                val artist1 = Artist("artist1", "addr1", "Artist 1", "", "", emptyList())
                val artist2 = Artist("artist2", "addr2", "Artist 2", "", "", emptyList())
                val artist3 = Artist("artist3", "addr3", "Artist 3", "", "", emptyList())

                coEvery { artistRepository.getArtist("artist1") } returns artist1
                coEvery { artistRepository.getArtist("artist2") } returns artist2
                coEvery { artistRepository.getArtist("artist3") } returns artist3

                val proportionsSlot = slot<Map<String, Float>>()
                every { payoutService.makeContribution(amount, capture(proportionsSlot)) } returns txid

                val result = viewModel.contribute(amount)

                assertEquals(ContributeViewModel.ContributionStatus.SUCCESS, result)

                val proportionsSum = proportionsSlot.captured.values.sum()
                assertEquals(1.0f, proportionsSum, 0.0001f)

                assertEquals(0.5f, proportionsSlot.captured["addr1"]!!, 0.0001f)
                assertEquals(0.125f, proportionsSlot.captured["addr2"]!!, 0.0001f)
                assertEquals(0.375f, proportionsSlot.captured["addr3"]!!, 0.0001f)
            }

            @Test
            fun `contribute should handle mixed artist identification formats`() = runTest {
                val listenData = mapOf(
                    "artist1" to 50.0,
                    "ArtistName|bc1direct" to 50.0
                )
                val amount = 75f
                val txid = "txMixedFormats"

                every { listenActivityRepository.getMinutesPerArtist() } returns listenData

                coEvery { artistRepository.getArtist("artist1") } returns Artist("artist1", "addr1", "Artist 1", "", "", emptyList())

                val shareSlot = slot<Map<String, Float>>()
                every { payoutService.makeContribution(amount, capture(shareSlot)) } returns txid

                val result = viewModel.contribute(amount)

                assertEquals(ContributeViewModel.ContributionStatus.SUCCESS, result)

                assertEquals(2, shareSlot.captured.size)
                assertTrue(shareSlot.captured.containsKey("addr1"))
                assertTrue(shareSlot.captured.containsKey("bc1direct"))

                assertEquals(0.5f, shareSlot.captured["addr1"]!!, 0.0001f)
                assertEquals(0.5f, shareSlot.captured["bc1direct"]!!, 0.0001f)
            }

            @Test
            fun `contribute should create proposal block with correct transaction data`() = runTest {
                val listenData = mapOf(
                    "artist1" to 100.0
                )
                val amount = 50f
                val txid = "txProposalTest"

                every { listenActivityRepository.getMinutesPerArtist() } returns listenData
                coEvery { artistRepository.getArtist("artist1") } returns Artist("artist1", "addr1", "Artist 1", "", "", emptyList())
                every { payoutService.makeContribution(any(), any()) } returns txid

                val transactionSlot = slot<Map<String, Any>>()
                every { musicCommunity.createProposalBlock(
                    eq("contribute-proposal"),
                    capture(transactionSlot),
                    any()
                ) } returns mockk()

                val result = viewModel.contribute(amount)

                assertEquals(ContributeViewModel.ContributionStatus.SUCCESS, result)

                assertEquals(txid, transactionSlot.captured["txid"])
                assertEquals(amount, transactionSlot.captured["amount"])
                assertTrue((transactionSlot.captured["artists"] as List<*>).contains("artist1"))

                coVerify { cacheDao.insert(match<ContributionEntity> {
                    it.id == txid && it.amount == amount && it.artists.contains("artist1")
                }) }
            }

            @Test
            fun `contribute should handle very small listen times correctly`() = runTest {
                val listenData = mapOf(
                    "artist1" to 0.001,
                    "artist2" to 0.002
                )
                val amount = 10f
                val txid = "txSmallTimes"

                every { listenActivityRepository.getMinutesPerArtist() } returns listenData

                coEvery { artistRepository.getArtist("artist1") } returns Artist("artist1", "addr1", "Artist 1", "", "", emptyList())
                coEvery { artistRepository.getArtist("artist2") } returns Artist("artist2", "addr2", "Artist 2", "", "", emptyList())

                val proportionsSlot = slot<Map<String, Float>>()
                every { payoutService.makeContribution(amount, capture(proportionsSlot)) } returns txid

                val result = viewModel.contribute(amount)

                assertEquals(ContributeViewModel.ContributionStatus.SUCCESS, result)

                val proportionsSum = proportionsSlot.captured.values.sum()
                assertEquals(1.0f, proportionsSum, 0.0001f)

                assertEquals(1f/3f, proportionsSlot.captured["addr1"]!!, 0.0001f)
                assertEquals(2f/3f, proportionsSlot.captured["addr2"]!!, 0.0001f)
            }

            @Test
            fun `contribute should clear listen activity data after successful contribution`() = runTest {
                val listenData = mapOf("artist1" to 100.0)
                val amount = 10f

                every { listenActivityRepository.getMinutesPerArtist() } returns listenData
                coEvery { artistRepository.getArtist("artist1") } returns Artist("artist1", "addr1", "Artist 1", "", "", emptyList())
                every { payoutService.makeContribution(any(), any()) } returns "tx123"

                viewModel.contribute(amount)

                verify { listenActivityRepository.clearListenActivityData() }
            }
        }
