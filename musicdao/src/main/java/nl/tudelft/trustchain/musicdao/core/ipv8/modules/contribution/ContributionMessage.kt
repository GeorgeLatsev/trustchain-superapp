package nl.tudelft.trustchain.musicdao.core.ipv8.modules.contribution

import nl.tudelft.ipv8.messaging.*

class ContributionMessage(
    val txid: String,
    val artistSplits: Map<String, Float>,
    var signature: String = "",
) : Serializable {

    /**
     * Generates a signable string representation of the contribution message.
     */
    fun getSignableString(): String {
        val sortedSplits = artistSplits.toSortedMap()

        val builder = StringBuilder()
        builder.append(txid)

        for ((artistId, share) in sortedSplits) {
            builder.append("|").append(artistId).append(":").append(share)
        }

        return builder.toString()
    }

    override fun serialize(): ByteArray {
        var result = serializeVarLen(txid.toByteArray(Charsets.UTF_8))
        result += serializeVarLen(signature.toByteArray(Charsets.UTF_8))
        result += serializeUInt(artistSplits.size.toUInt())

        for ((artistId, share) in artistSplits) {
            result += serializeVarLen(artistId.toByteArray(Charsets.UTF_8))
            result += serializeFloat(share)
        }

        return result
    }

    companion object Deserializer : Deserializable<ContributionMessage> {
        override fun deserialize(buffer: ByteArray, offset: Int): Pair<ContributionMessage, Int> {
            var localOffset = 0

            val (txidBytes, txidLen) = deserializeVarLen(buffer, offset + localOffset)
            val txid = txidBytes.toString(Charsets.UTF_8)
            localOffset += txidLen

            val (signatureBytes, signatureLen) = deserializeVarLen(buffer, offset + localOffset)
            val signature = signatureBytes.toString(Charsets.UTF_8)
            localOffset += signatureLen

            val mapSize = deserializeUInt(buffer, offset + localOffset).toInt()
            localOffset += SERIALIZED_UINT_SIZE

            val artistSplits = mutableMapOf<String, Float>()

            repeat(mapSize) {
                val (artistIdBytes, artistIdLen) = deserializeVarLen(buffer, offset + localOffset)
                val artistId = artistIdBytes.toString(Charsets.UTF_8)
                localOffset += artistIdLen

                val share = deserializeFloat(buffer, offset + localOffset)
                localOffset += SERIALIZED_FLOAT_SIZE

                artistSplits[artistId] = share
            }

            return Pair(ContributionMessage(txid, artistSplits, signature), localOffset)
        }
    }

    override fun equals(other: Any?): Boolean {
        return other is ContributionMessage &&
            txid == other.txid &&
            artistSplits == other.artistSplits &&
            signature == other.signature
    }
}

const val SERIALIZED_FLOAT_SIZE = 4

fun serializeFloat(value: Float): ByteArray {
    return java.nio.ByteBuffer.allocate(SERIALIZED_FLOAT_SIZE).putFloat(value).array()
}

fun deserializeFloat(buffer: ByteArray, offset: Int): Float {
    return java.nio.ByteBuffer.wrap(buffer, offset, SERIALIZED_FLOAT_SIZE).float
}

