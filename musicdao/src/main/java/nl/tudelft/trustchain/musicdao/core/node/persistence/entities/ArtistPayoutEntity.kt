package nl.tudelft.trustchain.musicdao.core.node.persistence.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    primaryKeys = ["artistAddress", "payoutId"],
    foreignKeys = [
        ForeignKey(
            entity = PayoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["payoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("payoutId")])
data class ArtistPayoutEntity (
    val artistAddress: String,
    val payoutId: String,
    val payoutAmount: Long,
)
