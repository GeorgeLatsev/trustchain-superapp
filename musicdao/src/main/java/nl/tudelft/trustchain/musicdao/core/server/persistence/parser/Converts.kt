package nl.tudelft.trustchain.musicdao.core.server.persistence.parser

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import com.google.common.reflect.TypeToken
import nl.tudelft.trustchain.musicdao.core.cache.entities.SongEntity
import nl.tudelft.trustchain.musicdao.core.cache.parser.JsonParser

@ProvidedTypeConverter
class Converters(
    private val jsonParser: JsonParser
) {
    @TypeConverter
    fun fromArtistsSplitsJson(json: String): Map<String, Float> {
        return jsonParser.fromJson<Map<String, Float>>(
            json,
            object : TypeToken<Map<String, Float>>() {}.type
        ) ?: emptyMap()
    }

    @TypeConverter
    fun toArtistsSplitsJson(meanings: Map<String, Float>): String {
        return jsonParser.toJson(
            meanings,
            object : TypeToken<Map<String, Float>>() {}.type
        ) ?: ""
    }
}
