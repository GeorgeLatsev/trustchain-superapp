package nl.tudelft.trustchain.musicdao.core.node.persistence.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = PayoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["payoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("payoutId")]
)
data class ContributionEntity(
    @PrimaryKey
    val transactionHash: String,
    val contributorAddress: String,
    val signature: String,

    val artistSplits: Map<String, Float>,
    val donationAmount: Long? = null,

    val status: ContributionStatus = ContributionStatus.UNVERIFIED,
    val payoutId: String? = null,
) {
    enum class ContributionStatus {
        UNVERIFIED,
        VERIFIED,
        REJECTED,
        VALIDATING,
        COMPLETED,
    }
}
