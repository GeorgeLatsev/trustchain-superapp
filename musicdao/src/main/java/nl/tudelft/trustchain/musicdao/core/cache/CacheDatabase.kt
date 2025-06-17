package nl.tudelft.trustchain.musicdao.core.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import nl.tudelft.trustchain.musicdao.core.cache.entities.AlbumEntity
import nl.tudelft.trustchain.musicdao.core.cache.entities.ContributionEntity
import nl.tudelft.trustchain.musicdao.core.cache.parser.Converters

@Database(
    entities = [AlbumEntity::class, ContributionEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract val dao: CacheDao
}
