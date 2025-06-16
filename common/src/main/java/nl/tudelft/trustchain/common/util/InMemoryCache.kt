package nl.tudelft.trustchain.common.util

object InMemoryCache {
    private val cache = mutableMapOf<String, Any>()

    @Synchronized
    fun put(key: String, value: Any) {
        cache[key] = value
    }

    @Synchronized
    fun get(key: String): Any? {
        return cache[key]
    }

    @Synchronized
    fun remove(key: String) {
        cache.remove(key)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    @Synchronized
    fun containsKey(key: String): Boolean {
        return cache.containsKey(key)
    }

    @Synchronized
    fun size(): Int {
        return cache.size
    }
}
