package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.listenActivity

import nl.tudelft.ipv8.attestation.trustchain.TrustChainTransaction

data class ListenActivityBlock(
    val artistId: String,
    val trackId: String,
    val listenedMillis: Long
) {
    companion object {
        const val BLOCK_TYPE = "listen_activity"

        fun fromTrustChainTransaction(tx: TrustChainTransaction): ListenActivityBlock {
            return ListenActivityBlock(
                artistId = tx["artistId"] as String,
                trackId = tx["trackId"] as String,
                listenedMillis = (tx["listenedMillis"] as Number).toLong()
            )
        }
    }
}