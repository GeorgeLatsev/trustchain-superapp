package nl.tudelft.trustchain.musicdao.core.node.persistence.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

@Entity
data class PayoutEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val payoutStatus: PayoutStatus = PayoutStatus.COLLECTING,
    val transactionsIds: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class PayoutStatus {
        COLLECTING,
        AWAITING_FOR_CONFIRMATION,
        SUBMITTED,
        COMPLETED
    }
}

data class PayoutWithArtists(
    @Embedded val payout: PayoutEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "payoutId"
    )
    val artistPayouts: List<ArtistPayoutEntity>
)
