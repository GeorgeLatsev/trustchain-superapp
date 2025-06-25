package nl.tudelft.trustchain.musicdao.core.ipv8

import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import nl.tudelft.ipv8.attestation.trustchain.BlockListener
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.trustchain.musicdao.core.cache.CacheDatabase
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.payoutStatusUpdate.PayoutUpdateStatusBlock
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish.ReleasePublishBlockSigner
import nl.tudelft.trustchain.musicdao.core.ipv8.blocks.releasePublish.ReleasePublishBlockValidator
import nl.tudelft.trustchain.musicdao.core.torrent.TorrentEngine
import javax.inject.Inject

class SetupMusicCommunity
    @OptIn(DelicateCoroutinesApi::class)
    @Inject
    constructor(
        private val musicCommunity: MusicCommunity,
        private val releasePublishBlockSigner: ReleasePublishBlockSigner,
        private val releasePublishBlockValidator: ReleasePublishBlockValidator,
        private val torrentEngine: TorrentEngine,
        private val db: CacheDatabase
    ) {
        @OptIn(DelicateCoroutinesApi::class)
        fun registerListeners() {
            musicCommunity.registerTransactionValidator(
                ReleasePublishBlockValidator.BLOCK_TYPE,
                releasePublishBlockValidator
            )
            musicCommunity.registerBlockSigner(
                ReleasePublishBlockSigner.BLOCK_TYPE,
                releasePublishBlockSigner
            )
            musicCommunity.addListener(
                PayoutUpdateStatusBlock.BLOCK_TYPE,
                object : BlockListener {
                    override fun onBlockReceived(block: TrustChainBlock) {
                        val update = PayoutUpdateStatusBlock.fromTrustChainTransaction(block.transaction)
                        Log.d("MusicCommunity", "Payout Update: $update")

                        torrentEngine.download(update.torrentMagnet)

                        if (update.payoutStatus == "SUBMITTED") {
                            GlobalScope.launch {
                                db.dao.markContributionsAsSatisfied(update.transactionIds)
                            }
                        }
                    }
                }
            )
        }
    }
