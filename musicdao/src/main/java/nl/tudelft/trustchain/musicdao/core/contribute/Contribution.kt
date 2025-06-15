package nl.tudelft.trustchain.musicdao.core.contribute

data class Contribution(
    val id: String,
    val amount: Float,
    val artists: List<String>
)
