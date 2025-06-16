package nl.tudelft.trustchain.musicdao.core.node.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ArtistPayoutEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.ContributionEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutEntity
import nl.tudelft.trustchain.musicdao.core.node.persistence.entities.PayoutWithArtists

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

    @Query("SELECT * FROM ContributionEntity WHERE transactionHash = :txid AND status = 'UNVERIFIED'")
    fun getUnverifiedContributionsByTransactionId(txid: String): List<ContributionEntity>

    suspend fun getCurrentCollectingPayoutId(): String? {
        return getPayoutIdByStatus(PayoutEntity.PayoutStatus.COLLECTING)
    }

    suspend fun getCurrentAwaitingForConfirmationPayoutId(): String? {
        return getPayoutIdByStatus(PayoutEntity.PayoutStatus.AWAITING_FOR_CONFIRMATION)
    }

    @Query("SELECT id FROM PayoutEntity WHERE payoutStatus = :status LIMIT 1")
    suspend fun getPayoutIdByStatus(status: PayoutEntity.PayoutStatus): String?

    @Query("SELECT payoutStatus FROM PayoutEntity WHERE id = :payoutId LIMIT 1")
    suspend fun getPayoutStatus(payoutId: String): PayoutEntity.PayoutStatus?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun createPayout(payout: PayoutEntity)

    @Insert
    suspend fun insertContribution(contribution: ContributionEntity): Long

    @Query("SELECT * FROM ContributionEntity WHERE transactionHash = :transactionHash")
    suspend fun getContributionByTransactionHash(transactionHash: String): ContributionEntity?

    @Query("""
        UPDATE ContributionEntity
        SET status = 'VERIFIED', donationAmount = :amount, payoutId = :payoutId
        WHERE transactionHash = :transactionHash AND status = 'UNVERIFIED'
    """)
    suspend fun verifyContribution(
        transactionHash: String,
        payoutId: String,
        amount: Long,
    )

    @Transaction
    suspend fun verifyContributionAndDistributeFunds(
        transactionHash: String,
        totalAmount: Long
    ) {
        val contribution = getContributionByTransactionHash(transactionHash)
            ?: throw IllegalStateException("Contribution not found")

        var payoutId = getCurrentCollectingPayoutId();
        if (payoutId == null) {
            val payout = PayoutEntity()
            createPayout(payout)
            payoutId = payout.id
        }

        if (contribution.status != ContributionEntity.ContributionStatus.UNVERIFIED) {
            throw IllegalStateException("Contribution is not in UNVERIFIED status")
        }

        val updatedRows = verifyContribution(transactionHash, payoutId, totalAmount)
        if (updatedRows.equals(0)) {
            throw IllegalStateException("Failed to verify contribution")
        }

        for ((artistAddress, percentage) in contribution.artistSplits) {
            val amountForArtist = (totalAmount * percentage).toLong()
            addFundsToArtist(
                payoutId = payoutId,
                artistAddress = artistAddress,
                amount = amountForArtist
            )
        }
    }

    @Query("SELECT * FROM PayoutEntity ORDER BY createdAt DESC")
    fun getAllPayouts(): Flow<List<PayoutEntity>>

    @Query("SELECT * FROM PayoutEntity ORDER BY createdAt DESC")
    fun getAllPayoutsWithArtists(): Flow<List<PayoutWithArtists>>

    @Query("SELECT * FROM ContributionEntity WHERE payoutId = :payoutId")
    fun getContributionsByPayoutId(payoutId: String): Flow<List<ContributionEntity>>

    @Query("SELECT * FROM ArtistPayoutEntity WHERE payoutId = :payoutId")
    fun getArtistPayoutsByPayoutId(payoutId: String): Flow<List<ArtistPayoutEntity>>

    @Query("SELECT * FROM ContributionEntity WHERE status = 'UNVERIFIED'")
    fun getUnverifiedContributions(): Flow<List<ContributionEntity>>

    @Query("UPDATE PayoutEntity SET payoutStatus = :newStatus WHERE id = :payoutId")
    suspend fun updatePayoutStatus(payoutId: String, newStatus: PayoutEntity.PayoutStatus)

    @Query("SELECT * FROM PayoutEntity WHERE id = :payoutId")
    suspend fun getPayoutWithArtistsById(payoutId: String): PayoutWithArtists
}
