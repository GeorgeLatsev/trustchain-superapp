package nl.tudelft.trustchain.musicdao.core.node

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runBlockingTest
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.node.persistence.ServerDatabase
import nl.tudelft.trustchain.musicdao.core.node.persistence.PayoutDao
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.*
import nl.tudelft.trustchain.musicdao.core.torrent.TorrentEngine
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PayoutManagerTest {
    private lateinit var payoutManager: PayoutManager
    private lateinit var database: ServerDatabase
    private lateinit var payoutDao: PayoutDao
    private lateinit var walletService: WalletService
    private lateinit var payoutWalletService: WalletService
    private lateinit var musicCommunity: MusicCommunity
    private lateinit var context: Context
    private lateinit var torrentEngine: TorrentEngine

    @Before
    fun setup() {
        database = mockk(relaxed = true)
        payoutDao = mockk(relaxed = true)
        every { database.payoutDao } returns payoutDao
        walletService = mockk(relaxed = true)
        payoutWalletService = mockk(relaxed = true)
        musicCommunity = mockk(relaxed = true)
        context = mockk(relaxed = true)
        torrentEngine = mockk(relaxed = true)

        payoutManager =
            PayoutManager(
                database,
                walletService,
                payoutWalletService,
                musicCommunity,
                context,
                torrentEngine
            )
    }

    @Test
    fun testIsEnabledReturnsFalseByDefault() {
        every { context.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
        assertFalse(payoutManager.isEnabled())
    }

    @Test
    fun testEnableSetsPreferences() {
        val prefs = mockk<android.content.SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.edit() } returns mockk(relaxed = true)
        every { payoutWalletService.protocolAddress() } returns mockk(relaxed = true)
        payoutManager.enable()
        verify { prefs.edit() }
    }

    @Test
    fun testGetOrCreateNextPayoutReturnsExisting() =
        runBlockingTest {
            coEvery { payoutDao.getCurrentCollectingPayoutId() } returns "existing"
            val result = payoutManager.getOrCreateNextPayout()
            assertEquals("existing", result)
        }

    @Test
    fun testGetOrCreateNextPayoutCreatesNew() =
        runBlockingTest {
            coEvery { payoutDao.getCurrentCollectingPayoutId() } returns null
            coEvery { payoutDao.createPayout(any()) } answers { }
            val slot = slot<PayoutEntity>()
            coEvery { payoutDao.createPayout(capture(slot)) } answers { }
            val result = payoutManager.getOrCreateNextPayout()
            assertEquals(slot.captured.id, result)
        }

    @Test
    fun testIsEnabledReturnsFalse_whenPreferenceNotSet() {
        val preferences = mockk<SharedPreferences>()
        every { PreferenceManager.getDefaultSharedPreferences(context) } returns preferences
        every { preferences.getBoolean(PREF_KEY_IS_NODE_ENABLED, false) } returns false

        val result = payoutManager.isEnabled()

        assertFalse(result)
    }

    @Test
    fun testEnable_setsPreferencesCorrectly() {
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { PreferenceManager.getDefaultSharedPreferences(context) } returns preferences
        every { preferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.apply() } just Runs
        every { payoutWalletService.protocolAddress().toString() } returns "testAddress"

        payoutManager.enable()

        verify { editor.putBoolean(PREF_KEY_IS_NODE_ENABLED, true) }
        verify { editor.putString(PREF_KEY_NODE_BITCOIN_ADDRESS, "testAddress") }
    }

    @Test
    fun testGetOrCreateNextPayout_returnsCurrentId_whenAlreadySet() =
        runBlockingTest {
            val field = PayoutManager::class.java.getDeclaredField("currentPayoutId")
            field.isAccessible = true
            val stateFlow = field.get(payoutManager) as MutableStateFlow<String?>
            stateFlow.value = "existingId"

            val result = payoutManager.getOrCreateNextPayout()

            assertEquals("existingId", result)
            coVerify(exactly = 0) { payoutDao.getCurrentCollectingPayoutId() }
        }

    @Test
    fun testGetOrCreateNextPayout_returnsExistingFromDb() =
        runBlockingTest {
            coEvery { payoutDao.getCurrentCollectingPayoutId() } returns "dbPayoutId"
            val result = payoutManager.getOrCreateNextPayout()

            assertEquals("dbPayoutId", result)
            coVerify(exactly = 0) { payoutDao.createPayout(any()) }
        }
}
