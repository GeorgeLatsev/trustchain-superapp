package nl.tudelft.trustchain.musicdao.core.server.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import nl.tudelft.trustchain.musicdao.core.server.persistence.entities.ArtistPayoutEntity
import nl.tudelft.trustchain.musicdao.core.server.persistence.entities.ContributionEntity
import nl.tudelft.trustchain.musicdao.core.server.persistence.entities.PayoutEntity
import nl.tudelft.trustchain.musicdao.core.server.persistence.parser.Converters

@Database(
    entities = [ArtistPayoutEntity::class, ContributionEntity::class, PayoutEntity::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ServerDatabase : RoomDatabase() {
    abstract val payoutDao: PayoutDao
}

