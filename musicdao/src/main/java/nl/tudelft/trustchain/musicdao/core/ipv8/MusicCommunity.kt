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
import nl.tudelft.ipv8.messaging.payload.IntroductionResponsePayload
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import nl.tudelft.trustchain.common.util.InMemoryCache
import java.util.*
import nl.tudelft.trustchain.common.util.PreferenceHelper
import nl.tudelft.trustchain.musicdao.core.node.PREF_KEY_IS_NODE_ENABLED
import nl.tudelft.trustchain.musicdao.core.node.PREF_KEY_NODE_BITCOIN_ADDRESS
import kotlin.random.Random

@Suppress("DEPRECATION")
class MusicCommunity(
    settings: TrustChainSettings,
    database: TrustChainStore,
    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "29384902d2938f34872398758cf7ca9238ccc333"

    private var _payoutNodePeer: Peer? = null
    private var _onPayoutNodePeerFound: ((node: Peer, nodeBitcoinAddress: String) -> Unit)? = null
    private var _onPayoutNodePeerFoundCache = mutableListOf<Pair<Peer, String>>()

    var swarmHealthMap = mutableMapOf<Sha1Hash, SwarmHealth>() // All recent swarm health data that
    // has been received from peers

    val discoveredAddressesContacted: MutableMap<IPv4Address, Date> = mutableMapOf()

    class Factory(
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<MusicCommunity>(MusicCommunity::class.java) {
        override fun create(): MusicCommunity = MusicCommunity(settings, database, crawler)
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

    fun publicKeyHex(): String =
        this.myPeer.publicKey
            .keyToBin()
            .toHex()

    fun publicKeyStringToPublicKey(publicKey: String): PublicKey = defaultCryptoProvider.keyFromPublicBin(publicKey.hexToBytes())

    fun publicKeyStringToByteArray(publicKey: String): ByteArray = publicKeyStringToPublicKey(publicKey).keyToBin()

    object MessageId {
        const val INTRODUCTION_REQUEST = nl.tudelft.ipv8.Community.MessageId.INTRODUCTION_REQUEST
        const val INTRODUCTION_RESPONSE = nl.tudelft.ipv8.Community.MessageId.INTRODUCTION_RESPONSE
        const val KEYWORD_SEARCH_MESSAGE = 10
        const val SWARM_HEALTH_MESSAGE = 11
        const val CONTRIBUTION_MESSAGE = 12
    }

    /**
     * Helper function to check if the current application is running as a payout node.
     */
    private fun isPayoutNodeEnabled(): Boolean = PreferenceHelper.get(PREF_KEY_IS_NODE_ENABLED, false)

    /**
     * Extra bytes that are sent in the introductions
     */
    object IntroductionExtraBytes {
        const val IS_PAYOUT_NODE: Byte = 0x01
        const val IS_LOOKING_FOR_PAYOUT_NODE: Byte = 0x02
        const val KNOWS_PAYOUT_NODE: Byte = 0x03
    }

    // walkTo
    override fun walkTo(address: IPv4Address) {
        if (isPayoutNodeEnabled()) {
            val addressBytes = (PreferenceHelper.get(PREF_KEY_NODE_BITCOIN_ADDRESS, "")).toByteArray(Charsets.UTF_8)
            val extraBytes = byteArrayOf(IntroductionExtraBytes.IS_PAYOUT_NODE) + addressBytes

            val packet = createIntroductionRequest(address, extraBytes)

            Log.i("Connectivity (PAYOUT_NODE)", "Walking to address: $address")
            send(address, packet)
        } else if (_payoutNodePeer == null) {
            val extraBytes: ByteArray = byteArrayOf(IntroductionExtraBytes.IS_LOOKING_FOR_PAYOUT_NODE)
            val packet = createIntroductionRequest(address, extraBytes)

            Log.i("Connectivity (SEARCHING)", "Walking to address: $address")
            send(address, packet)
        } else {
            super.walkTo(address)
        }
    }

    override fun getNewIntroduction(fromPeer: Peer?) {
        var address = fromPeer?.address

        if (address == null) {
            val available = getPeers()
            address =
                if (available.isNotEmpty()) {
                    // With a small chance, try to remedy any disconnected network phenomena.
                    if (Random.nextFloat() < 0.5f && endpoint.udpEndpoint != null) {
                        DEFAULT_ADDRESSES.random()
                    } else {
                        available.random().address
                    }
                } else {
                    bootstrap()
                    return
                }
        }

        val packet =
            if (isPayoutNodeEnabled()) {
                val addressBytes = (PreferenceHelper.get(PREF_KEY_NODE_BITCOIN_ADDRESS, "")).toByteArray(Charsets.UTF_8)
                val extraBytes = byteArrayOf(IntroductionExtraBytes.IS_PAYOUT_NODE) + addressBytes

                createIntroductionRequest(address, extraBytes)
            } else if (_payoutNodePeer == null) {
                val extraBytes: ByteArray = byteArrayOf(IntroductionExtraBytes.IS_LOOKING_FOR_PAYOUT_NODE)

                createIntroductionRequest(address, extraBytes)
            } else {
                createIntroductionRequest(address)
            }
        send(address, packet)
    }

    override fun onPacket(packet: Packet) {
        val data = packet.data

        val packetPrefix = data.copyOfRange(0, prefix.size)
        if (!packetPrefix.contentEquals(prefix)) {
            return
        }

        val msgId = data[prefix.size].toUByte().toInt()

        run payoutNodeCheck@{
            if (_payoutNodePeer != null || isPayoutNodeEnabled()) {
                return@payoutNodeCheck
            }

            when (msgId) {
                MessageId.INTRODUCTION_REQUEST -> {
                    val (peer, payload) =
                        packet.getAuthPayload(IntroductionRequestPayload.Deserializer)

                    if (payload.extraBytes.isEmpty()) {
                        return@payoutNodeCheck
                    }

                    if (payload.extraBytes[0] == IntroductionExtraBytes.IS_PAYOUT_NODE) {
                        val walletBytes = payload.extraBytes.drop(1).toByteArray()
                        val address = walletBytes.toString(Charsets.UTF_8)

                        Log.i(
                            "Connectivity (SEARCHING)",
                            "Found payout node: ${peer.address} (${peer.mid}), wallet address: $address"
                        )

                        _payoutNodePeer = peer
                        InMemoryCache.put(PREF_KEY_NODE_BITCOIN_ADDRESS, address)
                        if (_onPayoutNodePeerFound != null) {
                            _onPayoutNodePeerFound?.invoke(peer, address)
                        } else {
                            _onPayoutNodePeerFoundCache.add(Pair(peer, address))
                        }
                    }
                }
                MessageId.INTRODUCTION_RESPONSE -> {
                    val (peer, payload) =
                        packet.getAuthPayload(IntroductionResponsePayload.Deserializer)

                    if (payload.extraBytes.isEmpty()) {
                        return@payoutNodeCheck
                    }

                    when (payload.extraBytes[0]) {
                        IntroductionExtraBytes.IS_PAYOUT_NODE -> {
                            val walletBytes = payload.extraBytes.drop(1).toByteArray()
                            val address = walletBytes.toString(Charsets.UTF_8)

                            Log.i(
                                "Connectivity (SEARCHING)",
                                "Found payout node: ${peer.address} (${peer.mid}), wallet address: $address"
                            )

                            _payoutNodePeer = peer
                            InMemoryCache.put(PREF_KEY_NODE_BITCOIN_ADDRESS, address)
                            if (_onPayoutNodePeerFound != null) {
                                _onPayoutNodePeerFound?.invoke(peer, address)
                            } else {
                                _onPayoutNodePeerFoundCache.add(Pair(peer, address))
                            }
                        }
                        IntroductionExtraBytes.KNOWS_PAYOUT_NODE -> {
                            val addressBytes =
                                payload.extraBytes
                                    .drop(
                                        1
                                    ).toByteArray()
                                    .toString(Charsets.UTF_8) // TODO: handle appropriately, walkTo address

                            Log.i(
                                "Connectivity (SEARCHING)",
                                "Found payout node connection: ${peer.address} (${peer.mid}), node address: $addressBytes"
                            )
                        }
                        else -> {}
                    }
                }
                else -> {}
            }
        }

        if (msgId != MessageId.INTRODUCTION_REQUEST) {
            return super.onPacket(packet)
        }

        val (peer, payload) =
            packet.getAuthPayload(IntroductionRequestPayload.Deserializer)

        val newPeer =
            peer.copy(
                lanAddress = payload.sourceLanAddress,
                wanAddress = payload.sourceWanAddress
            )

        if (maxPeers < 0 || getPeers().size < maxPeers) {
            addVerifiedPeer(newPeer)
        }

        val isLookingForPayoutNode =
            payload.extraBytes.isNotEmpty() &&
                payload.extraBytes[0] == IntroductionExtraBytes.IS_LOOKING_FOR_PAYOUT_NODE
        val packet =
            if (isLookingForPayoutNode && isPayoutNodeEnabled()) {
                val addressBytes =
                    (
                        PreferenceHelper.get(
                            PREF_KEY_NODE_BITCOIN_ADDRESS,
                            ""
                        )
                    ).toByteArray(Charsets.UTF_8) // TODO: set when enabling payout node + remove from app
                val extraBytes = byteArrayOf(IntroductionExtraBytes.IS_PAYOUT_NODE) + addressBytes

                createIntroductionResponse(newPeer, payload.identifier, extraBytes = extraBytes)
            } else if (isLookingForPayoutNode && _payoutNodePeer != null) {
                val nodeAddressBytes = (_payoutNodePeer!!.address.ip + ":" + _payoutNodePeer!!.address.port).toByteArray(Charsets.UTF_8)
                val extraBytes: ByteArray = byteArrayOf(IntroductionExtraBytes.KNOWS_PAYOUT_NODE) + nodeAddressBytes

                createIntroductionResponse(newPeer, payload.identifier, extraBytes = extraBytes)
            } else {
                createIntroductionResponse(newPeer, payload.identifier)
            }
        send(peer, packet)
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
    fun setOnPayoutNodePeerFound(handler: (node: Peer, nodeBitcoinAddress: String) -> Unit) {
        _onPayoutNodePeerFound = handler
        for ((peer, address) in _onPayoutNodePeerFoundCache) {
            handler(peer, address)
        }
        _onPayoutNodePeerFoundCache.clear()
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
