package nl.tudelft.trustchain.musicdao.core.node

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.tudelft.ipv8.android.IPv8Android
import nl.tudelft.trustchain.musicdao.core.ipv8.MusicCommunity
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.payoutStatusUpdate.PayoutUpdateStatusBlock
import nl.tudelft.trustchain.musicdao.core.ipv8.modules.contribution.ContributionMessage
import nl.tudelft.trustchain.musicdao.core.node.persistence.ServerDatabase
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ContributionEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutEntity
import nl.tudelft.trustchain.musicdao.core.torrent.TorrentEngine
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService
import nl.tudelft.trustchain.musicdao.core.wallet.WalletService.Companion.SATS_PER_BITCOIN
import java.io.File
import java.io.FileWriter
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class PayoutManager
    @OptIn(DelicateCoroutinesApi::class)
    @Inject
    constructor(
        private val database: ServerDatabase,
        private val walletService: WalletService,
        @Named("payoutWallet")
        private val payoutWalletService: WalletService,
        private val musicCommunity: MusicCommunity,
        @ApplicationContext
        val context: Context,
        val torrentEngine: TorrentEngine,
    ) {
        private val currentPayoutId = MutableStateFlow<String?>(null)

        private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        init {
            if (!isEnabled()) {
                Log.d("PayoutManager", "PayoutManager is not enabled, skipping initialization")
            } else {
                Log.d("PayoutManager", "PayoutManager is enabled, initializing")
                init()
            }
        }

        /**
         * Checks if the PayoutManager is enabled by reading the shared preferences.
         */
        fun isEnabled(): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(PREF_KEY_IS_NODE_ENABLED, false)
        }

        /**
         * Enables the app instance to act as a payout node.
         */
        fun enable() {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit {
                putBoolean(PREF_KEY_IS_NODE_ENABLED, true)
                putString(PREF_KEY_NODE_BITCOIN_ADDRESS, payoutWalletService.protocolAddress().toString())
            }
            Log.d("PayoutManager", "PayoutManager enabled, initializing")
            init()
        }

        private fun init() {
            musicCommunity.setMessageHandler(MusicCommunity.MessageId.CONTRIBUTION_MESSAGE) { packet ->
                val (peer, message) = packet.getAuthPayload(ContributionMessage.Deserializer)
                Log.d("PayoutManager", "Received contribution message (${message.getSignableString()}) from peer: $peer")

                coroutineScope.launch {
                    registerContribution(message.txid, message.signature, message.artistSplits)
                }
            }

            payoutWalletService.wallet().addCoinsReceivedEventListener { _, tx, prevBalance, newBalance ->
                Log.d("PayoutManager", "Coins received: ${tx.txId} - New balance: $newBalance")

                coroutineScope.launch {
                    onTransactionReceived(tx.txId.toString(), newBalance.value - prevBalance.value)
                }
            }

            coroutineScope.launch {
                val unconfirmedContributions = database.payoutDao.getUnverifiedContributionsTransactionHashes()

                for (transaction in unconfirmedContributions) {
                    val tx = walletService.userTransactions.value.find { it.transaction.txId.toString() == transaction }
                    if (tx != null) {
                        onTransactionReceived(tx.transaction.txId.toString(), tx.value.value)
                    }
                }
            }
        }

        /**
         * Handles an incomming transaction by verifying contributions associated with it.
         */
        private suspend fun onTransactionReceived(
            txid: String,
            amountSats: Long
        ) {
            Log.d("PayoutManager", "Received transaction $txid with amount $amountSats")
            try {
                val contributions = database.payoutDao.getUnverifiedContributionsByTransactionId(txid)

                for (contribution in contributions) {
                    if (contribution.status == ContributionEntity.ContributionStatus.UNVERIFIED) {
                        val signableString =
                            ContributionMessage(
                                contribution.transactionHash,
                                contribution.artistSplits
                            ).getSignableString()

                        // val recoveredKey = ECKey.signedMessageToKey(signableString, contribution.signature) // can throw java.security.SignatureException: Signature truncated, expected 65 bytes and got 3

                        val isValid = true; // TODO: compare sender with recovered key
                        if (isValid) {
                            database.payoutDao.verifyContributionAndDistributeFunds(txid, amountSats)
                            return
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PayoutManager", "Failed to verify contribution $txid", e)
                return
            }
            Log.d("PayoutManager", "Successfully verified contribution $txid")
        }

        /**
         * Registers a contribution with the given transaction hash, signature, and artist splits.
         */
        suspend fun registerContribution(
            transactionHash: String,
            signature: String,
            artistSplits: Map<String, Float>,
        ) {
            Log.d(
                "PayoutManager",
                "Registering contribution with txid $transactionHash and splits $artistSplits"
            )
            database.payoutDao.insertContribution(
                ContributionEntity(
                    transactionHash = transactionHash,
                    signature = signature,
                    artistSplits = artistSplits,
                )
            )

            val transaction = walletService.userTransactions.value.find { it.transaction.txId.toString() == transactionHash }
            if (transaction != null) {
                onTransactionReceived(transaction.transaction.txId.toString(), transaction.value.value)
            }
        }

        /**
         * Retrieves the current collecting payout ID, or creates a new one if it doesn't exist.
         */
        suspend fun getOrCreateNextPayout(): String {
            currentPayoutId.value?.let { return it }

            database.payoutDao.getCurrentCollectingPayoutId()?.let {
                currentPayoutId.value = it
                return it
            }

            val nextPayout = PayoutEntity()
            database.payoutDao.createPayout(nextPayout)
            currentPayoutId.value = nextPayout.id
            return nextPayout.id
        }

        /**
         * Sets the status of a payout with the given ID.
         */
        suspend fun setPayoutStatus(
            payoutId: String,
            status: PayoutEntity.PayoutStatus
        ): PayoutEntity.PayoutStatus? {
            Log.d("PayoutManager", "Setting payout status for ID $payoutId to $status")
            database.payoutDao.updatePayoutStatus(payoutId, status)

            val torrentMagnet = preparePayoutTorrent(payoutId)
            Log.i("PayoutManager", "Prepared torrent magnet for payout $payoutId: $torrentMagnet")

            when (status) {
                PayoutEntity.PayoutStatus.AWAITING_FOR_CONFIRMATION -> {
                    Log.d("PayoutManager", "Payout $payoutId is now awaiting confirmation")
                    currentPayoutId.value = null
                    getOrCreateNextPayout()

                    val payout = database.payoutDao.getPayoutWithArtistsById(payoutId)
                    val artistSplits = payout.artistPayouts.associate { it.artistAddress to it.payoutAmount.toFloat() }
                    val transactionIds = database.payoutDao.getVerifiedContributionsTransactionHashesByPayoutId(payoutId)

                    // limit to 100 transactions to avoid too large blocks
                    val transaction =
                        mutableMapOf(
                            "payoutId" to payoutId,
                            "payoutStatus" to status.toString(),
                            "artistSplits" to artistSplits,
                            "torrentMagnet" to torrentMagnet,
                            "transactionIds" to transactionIds.take(100),
                            "payoutTransactionId" to "",
                        )

                    musicCommunity.createProposalBlock(
                        PayoutUpdateStatusBlock.BLOCK_TYPE,
                        transaction,
                        IPv8Android
                            .getInstance()
                            .myPeer.publicKey
                            .keyToBin()
                    )

                    return PayoutEntity.PayoutStatus.AWAITING_FOR_CONFIRMATION
                }
                PayoutEntity.PayoutStatus.SUBMITTED -> {
                    val payout = database.payoutDao.getPayoutWithArtistsById(payoutId)
                    if (payout.artistPayouts.isEmpty()) {
                        Log.e("PayoutManager", "No artist payouts found for payout ID $payoutId")
                        val transaction =
                            mutableMapOf(
                                "payoutId" to payoutId,
                                "payoutStatus" to status.toString(),
                                "artistSplits" to emptyMap<String, Float>(),
                                "torrentMagnet" to torrentMagnet,
                                "transactionIds" to emptyList<String>(),
                                "payoutTransactionId" to "",
                            )

                        musicCommunity.createProposalBlock(
                            PayoutUpdateStatusBlock.BLOCK_TYPE,
                            transaction,
                            IPv8Android
                                .getInstance()
                                .myPeer.publicKey
                                .keyToBin()
                        )

                        return PayoutEntity.PayoutStatus.SUBMITTED
                    }

                    val txid =
                        payoutWalletService.sendCoinsMulti(
                            payout.artistPayouts.associate {
                                it.artistAddress to (it.payoutAmount.toFloat() / SATS_PER_BITCOIN.toFloat())
                            }
                        )

                    if (txid != null) {
                        Log.d("PayoutManager", "Successfully sent payout for ID $payoutId with txid $txid")

                        val artistSplits = payout.artistPayouts.associate { it.artistAddress to it.payoutAmount.toFloat() }
                        val transactionIds = database.payoutDao.getVerifiedContributionsTransactionHashesByPayoutId(payoutId)

                        // limit to 100 transactions to avoid too large blocks
                        val transaction =
                            mutableMapOf(
                                "payoutId" to payoutId,
                                "payoutStatus" to status.toString(),
                                "artistSplits" to artistSplits,
                                "torrentMagnet" to torrentMagnet,
                                "transactionIds" to transactionIds.take(100),
                                "payoutTransactionId" to txid,
                            )

                        musicCommunity.createProposalBlock(
                            PayoutUpdateStatusBlock.BLOCK_TYPE,
                            transaction,
                            IPv8Android
                                .getInstance()
                                .myPeer.publicKey
                                .keyToBin()
                        )

                        return PayoutEntity.PayoutStatus.SUBMITTED
                    } else {
                        Log.e("PayoutManager", "Failed to send payout for ID $payoutId, reverting to awaiting confirmation")
                        // revert to awaiting confirmation as sending failed
                        database.payoutDao.updatePayoutStatus(payoutId, PayoutEntity.PayoutStatus.AWAITING_FOR_CONFIRMATION)
                        return PayoutEntity.PayoutStatus.AWAITING_FOR_CONFIRMATION
                    }
                }
                else -> {
                    Log.d("PayoutManager", "Payout $payoutId status changed to $status, no further action required")
                    return null
                }
            }
        }

        /**
         * Prepares a torrent for the payout with the given ID by creating CSV files for contributions and splits,
         * and simulating a download to create a magnet link.
         */
        @OptIn(DelicateCoroutinesApi::class)
        suspend fun preparePayoutTorrent(payoutId: String): String? {
            val tempDir = File(context.cacheDir, "temp_files")
            if (!tempDir.exists()) tempDir.mkdirs()

            val contributions = database.payoutDao.getVerifiedContributionsByPayoutId(payoutId)

            val contributionsFile = File(context.cacheDir, "${payoutId}_contributions.csv")
            withContext(Dispatchers.IO) {
                FileWriter(contributionsFile).use { writer ->
                    writer.appendLine("transactionHash,signature,artistSplits,donationAmount")

                    for (contribution in contributions) {
                        val artistSplitsString = contribution.artistSplits.entries.joinToString(";") { "${it.key}:${it.value}" }
                        val donationAmountString = contribution.donationAmount?.toString() ?: ""

                        writer.appendLine(
                            listOf(
                                contribution.transactionHash,
                                contribution.signature,
                                artistSplitsString,
                                donationAmountString
                            ).joinToString(",")
                        )
                    }
                }
            }

            val payout = database.payoutDao.getPayoutWithArtistsById(payoutId)
            val splitsFile = File(context.cacheDir, "${payoutId}_splits.csv")
            withContext(Dispatchers.IO) {
                FileWriter(splitsFile).use { writer ->
                    writer.appendLine("artistAddress,payoutAmount")

                    for (artistPayout in payout.artistPayouts) {
                        writer.appendLine("${artistPayout.artistAddress},${artistPayout.payoutAmount}")
                    }
                }
            }

            val uris =
                listOf(contributionsFile, splitsFile).map { file ->
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                }
            val root = torrentEngine.simulateDownload(context, uris)
            if (root == null) {
                Log.d("PayoutManager", "preparePayoutTorrent: could not simulate download")
                return null
            }

            val magnet = root.second.makeMagnetUri()
            torrentEngine.download(magnet, root.first)

            return magnet
        }
    }
