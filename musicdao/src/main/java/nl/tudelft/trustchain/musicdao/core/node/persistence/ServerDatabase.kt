package nl.tudelft.trustchain.musicdao.core.node.persistence

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ArtistPayoutEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ContributionEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.parser.Converters

@Database(
    entities = [ArtistPayoutEntity::class, ContributionEntity::class, PayoutEntity::class],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ServerDatabase : RoomDatabase() {
    abstract val payoutDao: PayoutDao
}

