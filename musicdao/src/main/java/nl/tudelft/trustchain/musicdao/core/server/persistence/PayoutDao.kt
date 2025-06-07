package nl.tudelft.trustchain.musicdao.core.server.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import nl.tudelft.trustchain.musicdao.core.server.persistence.entities.ArtistPayoutEntity

@Dao
interface PayoutDao {
    @Query("""
        UPDATE ArtistPayoutEntity
        SET payoutAmount = payoutAmount + :amount
        WHERE artistAddress = :artistAddress AND payoutId = :payoutId
    """)
    suspend fun addFundsToArtistUpdate(
        payoutId: String,
        artistAddress: String,
        amount: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPayout(payout: ArtistPayoutEntity): Long

    @Transaction
    suspend fun addFundsToArtist(payoutId: String, artistAddress: String, amount: Long) {
        val updatedRows = addFundsToArtistUpdate(payoutId, artistAddress, amount)
        if (updatedRows == 0) {
            insertPayout(
                ArtistPayoutEntity(
                    payoutId = payoutId,
                    artistAddress = artistAddress,
                    payoutAmount = amount
                )
            )
        }
    }

    @Query("SELECT * FROM ArtistPayoutEntity WHERE payoutId = :payoutId")
    suspend fun getArtistPayoutsForPayoutId(payoutId: String): List<ArtistPayoutEntity>
}
