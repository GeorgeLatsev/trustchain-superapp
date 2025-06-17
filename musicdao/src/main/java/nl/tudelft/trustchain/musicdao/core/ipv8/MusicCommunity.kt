package nl.tudelft.trustchain.musicdao.core.ipv8

import android.annotation.SuppressLint
import android.util.Log
import nl.tudelft.trustchain.musicdao.core.ipv8.modules.search.KeywordSearchMessage
import com.frostwire.jlibtorrent.Sha1Hash
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.keyvault.PublicKey
import nl.tudelft.ipv8.keyvault.defaultCryptoProvider
import nl.tudelft.ipv8.messaging.Packet
import nl.tudelft.ipv8.messaging.Serializable
import nl.tudelft.ipv8.messaging.payload.IntroductionRequestPayload
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.common.util.InMemoryCache
import java.util.*
import nl.tudelft.trustchain.common.util.PreferenceHelper
import nl.tudelft.trustchain.musicdao.core.node.PREF_KEY_IS_NODE_ENABLED
import nl.tudelft.trustchain.musicdao.core.node.PREF_KEY_NODE_BITCOIN_ADDRESS

@Suppress("DEPRECATION")
class MusicCommunity(
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "29384902d2938f34872398758cf7ca9238ccc333"

    private var _payoutNodePeer: Peer? = null
    private var _onPayoutNodePeerFound: ((node: Peer, nodeBitcoinAddress: String) -> Unit)? = null

    var swarmHealthMap = mutableMapOf<Sha1Hash, SwarmHealth>() // All recent swarm health data that
    // has been received from peers

    val discoveredAddressesContacted: MutableMap<IPv4Address, Date> = mutableMapOf()

    class Factory(
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<MusicCommunity>(MusicCommunity::class.java) {
        override fun create(): MusicCommunity {
            return MusicCommunity(settings, database, crawler)
        }
    }

    init {
        messageHandlers[MessageId.KEYWORD_SEARCH_MESSAGE] = ::onKeywordSearch
        messageHandlers[MessageId.SWARM_HEALTH_MESSAGE] = ::onSwarmHealth
    }

    fun performRemoteKeywordSearch(
        keyword: String,
        ttl: UInt = 1u,
        originPublicKey: ByteArray = myPeer.publicKey.keyToBin()
    ): Int {
        val maxPeersToAsk = 20 // This is a magic number, tweak during/after experiments
        var count = 0
        for ((index, peer) in getPeers().withIndex()) {
            if (index >= maxPeersToAsk) break
            val packet =
                serializePacket(
                    MessageId.KEYWORD_SEARCH_MESSAGE,
                    KeywordSearchMessage(originPublicKey, ttl, keyword)
                )
            send(peer, packet)
            count += 1
        }
        return count
    }

    /**
     * When a peer asks for some music content with keyword, browse through my local collection of
     * blocks to find whether I have something. If I do, send the corresponding block directly back
     * to the original asker. If I don't, I will ask my peers to find it
     */
    private fun onKeywordSearch(packet: Packet) {
        val (peer, payload) = packet.getAuthPayload(KeywordSearchMessage)
        val keyword = payload.keyword.lowercase(Locale.ROOT)
        val block = localKeywordSearch(keyword)
        if (block != null) sendBlock(block, peer)
        if (block == null) {
            if (!payload.checkTTL()) return
            performRemoteKeywordSearch(keyword, payload.ttl, payload.originPublicKey)
        }
        Log.i("KeywordSearch", peer.mid + ": " + payload.keyword)
    }

    /**
     * Peers in the MusicCommunity iteratively gossip a few swarm health statistics of the torrents
     * they are currently tracking
     */
    private fun onSwarmHealth(packet: Packet) {
        val (_, swarmHealth) = packet.getAuthPayload(SwarmHealth)
        swarmHealthMap[Sha1Hash(swarmHealth.infoHash)] = swarmHealth
    }

    /**
     * Send a SwarmHealth message to a random peer
     */
    fun sendSwarmHealthMessage(swarmHealth: SwarmHealth): Boolean {
        val peer = pickRandomPeer() ?: return false
        send(peer, serializePacket(MessageId.SWARM_HEALTH_MESSAGE, swarmHealth))
        return true
    }

    /**
     * Filter local database to find a release block that matches a certain title or artist, using
     * keyword search
     */
    @SuppressLint("NewApi")
    fun localKeywordSearch(keyword: String): TrustChainBlock? {
        database.getBlocksWithType("publish_release").forEach {
            val transaction = it.transaction
            val title = transaction["title"]?.toString()?.lowercase(Locale.ROOT)
            val artists = transaction["artists"]?.toString()?.lowercase(Locale.ROOT)
            if (title != null && title.contains(keyword)) {
                return it
            } else if (artists != null && artists.contains(keyword)) {
                return it
            }
        }
        return null
    }

    private fun pickRandomPeer(): Peer? {
        val peers = getPeers()
        if (peers.isEmpty()) return null
        return peers.random()
    }

    fun publicKeyHex(): String {
        return this.myPeer.publicKey.keyToBin().toHex()
    }

    fun publicKeyStringToPublicKey(publicKey: String): PublicKey {
        return defaultCryptoProvider.keyFromPublicBin(publicKey.hexToBytes())
    }

    fun publicKeyStringToByteArray(publicKey: String): ByteArray {
        return publicKeyStringToPublicKey(publicKey).keyToBin()
    }

    object MessageId {
        const val INTRODUCTION_REQUEST = nl.tudelft.ipv8.Community.MessageId.INTRODUCTION_REQUEST
        const val KEYWORD_SEARCH_MESSAGE = 10
        const val SWARM_HEALTH_MESSAGE = 11
        const val CONTRIBUTION_MESSAGE = 12
    }

    /**
     * Helper function to check if the current application is running as a payout node.
     */
    private fun isPayoutNodeEnabled(): Boolean {
        return PreferenceHelper.get(PREF_KEY_IS_NODE_ENABLED, false)
    }

    /**
     * Extra bytes that are sent in the introductions
     */
    object IntroductionExtraBytes {
        const val IS_PAYOUT_NODE: Byte = 0x01
        const val IS_LOOKING_FOR_PAYOUT_NODE: Byte = 0x02
    }

    override fun walkTo(address: IPv4Address) {
        if (isPayoutNodeEnabled()) {
            val extraBytes: ByteArray = byteArrayOf(IntroductionExtraBytes.IS_PAYOUT_NODE)
            val packet = createIntroductionRequest(address, extraBytes)

            Log.i("MusicCommunity (PAYOUT_NODE)", "Walking to address: $address")
            send(address, packet)
        } else if (_payoutNodePeer == null) {
            val extraBytes: ByteArray = byteArrayOf(IntroductionExtraBytes.IS_LOOKING_FOR_PAYOUT_NODE)
            val packet = createIntroductionRequest(address, extraBytes)

            Log.i("MusicCommunity (LOOKING FOR PAYOUT_NODE)", "Walking to address: $address")
            send(address, packet)
        } else {
            Log.i("MusicCommunity", "Walking to address: $address")
            super.walkTo(address)
        }

        if (isPayoutNodeEnabled()) {
            discoveredAddressesContacted [address] = Date()
            Log.i(
                "MusicCommunity",
                "Discovered addresses contacted: ${discoveredAddressesContacted.keys.joinToString(", ")}"
            )
        }
    }

    override fun onPacket(packet: Packet) {
        super.onPacket(packet)

        if (!isPayoutNodeEnabled() && _payoutNodePeer == null) {
            val data = packet.data

            val msgId = data[prefix.size].toUByte().toInt()

            if (msgId == MessageId.INTRODUCTION_REQUEST) {
                val (peer, payload) = packet.getAuthPayload(IntroductionRequestPayload.Deserializer)
                Log.i("MusicCommunity", "Received from: ${peer.address} (${payload})")
                if (payload.extraBytes.isNotEmpty() && payload.extraBytes[0] == IntroductionExtraBytes.IS_PAYOUT_NODE) {
                    val addressBytes = payload.extraBytes.drop(1).toByteArray()
                    val address = addressBytes.toString(Charsets.UTF_8)

                    Log.i(
                        "MusicCommunity",
                        "Found payout node: ${peer.address} (${peer.mid}), address: $address"
                    )

                    _payoutNodePeer = peer
                    InMemoryCache.put(PREF_KEY_NODE_BITCOIN_ADDRESS, address)

                    if (_onPayoutNodePeerFound != null) {
                        _onPayoutNodePeerFound!!(peer, address)
                    }
                } else if (payload.extraBytes.contentEquals(byteArrayOf(IntroductionExtraBytes.IS_LOOKING_FOR_PAYOUT_NODE))
                    && _payoutNodePeer != null
                ) {
                    val globalTime = claimGlobalTime()

                    val prefix: Byte = IntroductionExtraBytes.IS_PAYOUT_NODE
                    val addressBytes: ByteArray = (InMemoryCache.get(PREF_KEY_NODE_BITCOIN_ADDRESS) as String).toByteArray(Charsets.UTF_8)

                    val extraBytes: ByteArray = byteArrayOf(prefix) + addressBytes

                    val payloadNew = IntroductionRequestPayload(
                        peer.address,
                        _payoutNodePeer?.lanAddress ?: myEstimatedLan,
                        _payoutNodePeer?.wanAddress ?: myEstimatedWan,
                        true,
                        network.wanLog.estimateConnectionType(),
                        (globalTime % UShort.MAX_VALUE).toInt(),
                        extraBytes
                    )

                    Log.i(
                        "MusicCommunity",
                        "Forward payout node info: ${peer.address} (${peer.mid})"
                    )

                    send(
                        peer.address,
                        serializePacket(MessageId.INTRODUCTION_REQUEST, payloadNew)
                    )
                }
            }
        }

        if (isPayoutNodeEnabled() && packet.source is IPv4Address) {
            val peerAddress = packet.source as IPv4Address;

            Log.i(
                "MusicCommunity (PAYOUT_NODE)",
                "Received message from ${peerAddress}"
            )

            val prefix: Byte = IntroductionExtraBytes.IS_PAYOUT_NODE
            val addressBytes: ByteArray = (InMemoryCache.get(PREF_KEY_NODE_BITCOIN_ADDRESS) as String).toByteArray(Charsets.UTF_8)

            val extraBytes: ByteArray = byteArrayOf(prefix) + addressBytes
            val packetNew = createIntroductionRequest(peerAddress, extraBytes)
            if (!discoveredAddressesContacted.containsKey(peerAddress)) {
                send(peerAddress, packetNew)
                Log.i(
                    "MusicCommunity (PAYOUT_NODE)",
                    "Someone asked about the node: ${peerAddress}"
                )
                discoveredAddressesContacted[peerAddress] = Date()
            }
        }
    }

    /**
     * Sends a packet to the payout node if it is known, or to itself if it is running as a payout node.
     */
    fun sendPacketToPayoutNode(
        messageId: Int,
        payload: Serializable,
        peer: Peer? = null
    ): Boolean {
        if (isPayoutNodeEnabled()) {
            Log.i(
                "MusicCommunity (PAYOUT_NODE)",
                "Sending packet to self: $messageId, payload: $payload"
            )

            messageHandlers[messageId]?.invoke(
                Packet(myPeer.address, serializePacket(messageId, payload))
            ) ?: run {
                Log.w(
                    "MusicCommunity (PAYOUT_NODE)",
                    "No handler registered for message ID: $messageId"
                )
            }

            return true
        }

        val targetPeer = peer ?: _payoutNodePeer ?: return false

        val packet = serializePacket(messageId, payload)
        send(targetPeer, packet)

        return true
    }

    /**
     * Sets a callback that will be invoked when a payout node peer is found.
     */
    fun setOnPayoutNodePeerFound(
        handler: (node: Peer, nodeBitcoinAddress: String) -> Unit
    ) {
        _onPayoutNodePeerFound = handler
    }

    /**
     * Sets a message handler for a specific message ID. Doing so will override any existing handler.
     */
    fun setMessageHandler(
        messageId: Int,
        handler: (packet: Packet) -> Unit
    ) {
        messageHandlers[messageId] = handler
    }
}
