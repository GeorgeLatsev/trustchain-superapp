package nl.tudelft.trustchain.musicdao.core.server.persistence.entities

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
        COMPLETED;

        fun next(): PayoutStatus? {
            return when (this) {
                COLLECTING -> AWAITING_FOR_CONFIRMATION
                AWAITING_FOR_CONFIRMATION -> SUBMITTED
                SUBMITTED -> COMPLETED
                COMPLETED -> null
            }
        }
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
