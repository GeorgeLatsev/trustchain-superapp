package nl.tudelft.trustchain.musicdao.core.cache

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import nl.tudelft.trustchain.musicdao.core.cache.entities.AlbumEntity
import nl.tudelft.trustchain.musicdao.core.cache.entities.ContributionEntity

@Dao
interface CacheDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(infos: AlbumEntity)

    @Query("DELETE FROM AlbumEntity WHERE id IS :id")
    suspend fun delete(id: String)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(entity: AlbumEntity)

    @Query("SELECT * FROM AlbumEntity")
    suspend fun getAll(): List<AlbumEntity>

    @Query("SELECT * FROM AlbumEntity")
    fun getAllLiveData(): LiveData<List<AlbumEntity>>

    @Query("SELECT * FROM AlbumEntity WHERE id is :id")
    suspend fun get(id: String): AlbumEntity

    @Query("SELECT * FROM AlbumEntity WHERE id is :id")
    fun getLiveData(id: String): LiveData<AlbumEntity>

    @Query("SELECT * FROM AlbumEntity WHERE infoHash is :infoHash")
    suspend fun getFromInfoHash(infoHash: String): List<AlbumEntity>

    @Query("SELECT * FROM AlbumEntity WHERE publisher is :publicKey")
    suspend fun getFromArtist(publicKey: String): List<AlbumEntity>

    @Query("SELECT * FROM AlbumEntity WHERE artist LIKE '%' || :keyword || '%' OR title LIKE '%' || :keyword || '%'")
    suspend fun localSearch(keyword: String): List<AlbumEntity>


    @Query("SELECT * FROM ContributionEntity")
    fun getAllContributions(): Flow<List<ContributionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(infos: ContributionEntity)

    @Query("UPDATE ContributionEntity SET satisfied = 1 WHERE id IN (:transactionIds)")
    suspend fun markContributionsAsSatisfied(transactionIds: List<String>)
}
