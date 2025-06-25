import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.trustchain.musicdao.core.contribute.PayoutService
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.node.PayoutManager
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import nl.tudelft.ipv8.Peer
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify

 class PayoutServiceTest {

     @Mock
     private lateinit var musicCommunity: MusicCommunity

     @Mock
     private lateinit var payoutManager: PayoutManager

     @Mock
     private lateinit var walletService: WalletService

     @Mock
     private lateinit var payoutWalletService: WalletService

     private lateinit var payoutService: PayoutService

     @Before
     fun setUp() {
         MockitoAnnotations.openMocks(this)
     }

     @Test
     fun `init sets node address and payoutWalletAddress when payout node is enabled`() = runTest {
         whenever(payoutManager.isEnabled()).thenReturn(true)
         whenever(musicCommunity.myPeer).thenReturn(mock())
         whenever(musicCommunity.myPeer.address).thenReturn(mock())
         whenever(musicCommunity.myPeer.address.toString()).thenReturn("1.2.3.4:1234")
         whenever(payoutWalletService.protocolAddress()).thenReturn(mock())
         whenever(payoutWalletService.protocolAddress().toString()).thenReturn("btc_addr")

         payoutService = PayoutService(musicCommunity, payoutManager, walletService, payoutWalletService)

         assertTrue(payoutService.isNodeFound.value)
         assertEquals("1.2.3.4:1234", payoutService.nodeAddress.value)
     }

    @Test
    fun `init sets up payout node peer listener when payout node is not enabled`() = runTest {
        whenever(payoutManager.isEnabled()).thenReturn(false)

        payoutService = PayoutService(musicCommunity, payoutManager, walletService, payoutWalletService)

        val handlerCaptor = argumentCaptor<(Peer, String) -> Unit>()
        verify(musicCommunity).setOnPayoutNodePeerFound(handlerCaptor.capture())

        val peerMock = mock(Peer::class.java)
        whenever(peerMock.address).thenReturn(IPv4Address("127.0.0.1", 12345))

        handlerCaptor.firstValue.invoke(peerMock, "btc_addr_2")

        assertEquals("btc_addr_2", payoutService.getPayoutWalletAddress())
    }

     @Test
     fun `makeContribution returns null if node not found`() {
         whenever(payoutManager.isEnabled()).thenReturn(false)
         payoutService = PayoutService(musicCommunity, payoutManager, walletService, payoutWalletService)

         val result = payoutService.makeContribution(1.0f, mapOf("artist1" to 1.0f))
         assertNull(result)
     }

     @Test
     fun `makeContribution returns null if sendCoins fails`() {
         whenever(payoutManager.isEnabled()).thenReturn(true)
         whenever(musicCommunity.myPeer).thenReturn(mock())
         whenever(musicCommunity.myPeer.address).thenReturn(mock())
         whenever(musicCommunity.myPeer.address.toString()).thenReturn("1.2.3.4:1234")
         whenever(payoutWalletService.protocolAddress()).thenReturn(mock())
         whenever(payoutWalletService.protocolAddress().toString()).thenReturn("btc_addr")
         whenever(walletService.sendCoins(anyString(), anyString())).thenReturn(null)

         payoutService = PayoutService(musicCommunity, payoutManager, walletService, payoutWalletService)

         val result = payoutService.makeContribution(1.0f, mapOf("artist1" to 1.0f))
         assertNull(result)
     }

 }
