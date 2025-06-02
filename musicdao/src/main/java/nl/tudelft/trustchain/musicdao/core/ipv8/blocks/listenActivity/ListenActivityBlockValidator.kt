package nl.tudelft.trustchain.musicdao.core.ipv8.blocks.listenActivity

class ListenActivityBlockValidator {
    fun validateTransaction(transaction: Map<String, Any?>): Boolean {
        return transaction["artistId"] is String &&
               transaction["trackId"] is String &&
               (transaction["listenedMillis"] as? Number)?.toLong()?.let { it > 0 } == true
    }
}