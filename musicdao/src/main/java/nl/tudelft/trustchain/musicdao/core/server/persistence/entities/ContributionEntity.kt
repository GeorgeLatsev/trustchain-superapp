package nl.tudelft.trustchain.musicdao.core.server.persistence.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ContributionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long,

    val transactionHash: String,
    val contributorAddress: String,

    val artistSplits: Map<String, Float>,
    val donationAmount: Long?,

    val status: ContributionStatus
) {
    enum class ContributionStatus {
        UNVERIFIED,
        VERIFIED,
        REJECTED,
        PENDING,
        VALIDATING,
        COMPLETED,
    }
}
