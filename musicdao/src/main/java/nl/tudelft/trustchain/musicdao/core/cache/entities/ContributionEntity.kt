package nl.tudelft.trustchain.musicdao.core.cache.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import nl.tudelft.trustchain.musicdao.core.contribute.Contribution

@Entity
data class ContributionEntity(
    @PrimaryKey val id: String,
    val amount: Float,
    val artists: List<String>,
    val satisfied: Boolean
) {
    fun toContribution(): Contribution {
        return Contribution(
            txid = id,
            amount = amount,
            artists = artists,
            satisfied = satisfied
        )
    }
}
