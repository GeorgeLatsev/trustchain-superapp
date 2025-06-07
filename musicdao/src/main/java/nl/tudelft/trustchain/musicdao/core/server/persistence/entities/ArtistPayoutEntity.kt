package nl.tudelft.trustchain.musicdao.core.server.persistence.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(primaryKeys = ["artistAddress", "payoutId"])
data class ArtistPayoutEntity ( // we need to freeze the payout amount for the artist when entering phase 2, we could save it somewhere else, or add a payout id or sth
    val artistAddress: String,
    val payoutId: String,
    val payoutAmount: Long,
) {

}
