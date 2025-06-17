package nl.tudelft.trustchain.musicdao.core.contribute

import nl.tudelft.trustchain.musicdao.core.cache.entities.ContributionEntity

data class Contribution(
    val txid: String,
    val amount: Float,
    val artists: List<String>,
    val satisfied: Boolean
) {
    fun toEntity(): ContributionEntity {
        return ContributionEntity(
            id = txid,
            amount = amount,
            artists = artists,
            satisfied = satisfied
        )
    }
}
