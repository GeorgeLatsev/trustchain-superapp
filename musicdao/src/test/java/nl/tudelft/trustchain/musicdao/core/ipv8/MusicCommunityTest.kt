package nl.tudelft.trustchain.musicdao.core.ipv8

import io.mockk.*
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.keyvault.PublicKey
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MusicCommunityTest {
 private lateinit var settings: TrustChainSettings
 private lateinit var database: TrustChainStore
 private lateinit var crawler: TrustChainCrawler
 private lateinit var community: MusicCommunity

 @Before
 fun setUp() {
  settings = mockk(relaxed = true)
  database = mockk(relaxed = true)
  crawler = mockk(relaxed = true)
  community = MusicCommunity(settings, database, crawler)

  val BIN_PREFIX = "LibNaCLPK:".toByteArray(Charsets.US_ASCII)
  val PUBLICKEY_BYTES = 32
  val SIGN_PUBLICKEY_BYTES = 32
  val publicKeyBytes = ByteArray(PUBLICKEY_BYTES) { 1 }
  val verifyKeyBytes = ByteArray(SIGN_PUBLICKEY_BYTES) { 2 }
  val validKey = BIN_PREFIX + publicKeyBytes + verifyKeyBytes

  val publicKeyMock = mockk<PublicKey>(relaxed = true)
  every { publicKeyMock.keyToBin() } returns validKey

  val peerMock = mockk<Peer>(relaxed = true)
  every { peerMock.publicKey } returns publicKeyMock

  community.myPeer = peerMock
 }

 @After
 fun tearDown() {
  unmockkAll()
 }

 @Test
 fun testLocalKeywordSearch_FindsByTitle() {
  val block = mockk<TrustChainBlock>(relaxed = true)
  every { block.transaction } returns mapOf("title" to "Test Song", "artists" to "Artist")
  every { database.getBlocksWithType("publish_release") } returns listOf(block)

  val result = community.localKeywordSearch("test")
  assertEquals(block, result)
 }

 @Test
 fun testLocalKeywordSearch_FindsByArtist() {
  val block = mockk<TrustChainBlock>(relaxed = true)
  every { block.transaction } returns mapOf("title" to "Other", "artists" to "Test Artist")
  every { database.getBlocksWithType("publish_release") } returns listOf(block)

  val result = community.localKeywordSearch("test")
  assertEquals(block, result)
 }

 @Test
 fun testLocalKeywordSearch_ReturnsNullIfNotFound() {
  every { database.getBlocksWithType("publish_release") } returns emptyList()
  val result = community.localKeywordSearch("notfound")
  assertNull(result)
 }

 @Test
 fun testPublicKeyHex_ReturnsHexString() {
  val hex = community.publicKeyHex()
  assertTrue(hex.matches(Regex("[0-9a-fA-F]+")))
 }

 @Test
 fun testPublicKeyStringToByteArray_AndBack() {
  val hex = community.publicKeyHex()
  val bytes = community.publicKeyStringToByteArray(hex)
  assertArrayEquals(community.myPeer.publicKey.keyToBin(), bytes)
 }
}
