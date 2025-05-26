package nl.tudelft.trustchain.musicdao.core.contribute

import nl.tudelft.trustchain.musicdao.core.repositories.model.Artist

data class Contribution(
    val id: String,
    val amount: Double,
    val artists: List<Artist>
)
