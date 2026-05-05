package com.arda.stopmiddlingme.data.db.dao

import androidx.room.*
import com.arda.stopmiddlingme.data.db.entity.NetworkBaseline
import kotlinx.coroutines.flow.Flow

@Dao
interface BaselineDao {

    @Query("SELECT * FROM network_baseline WHERE ssid = :ssid")
    fun observe(ssid: String): Flow<NetworkBaseline?>

    @Query("SELECT * FROM network_baseline WHERE ssid = :ssid")
    suspend fun get(ssid: String): NetworkBaseline?

    @Query("SELECT * FROM network_baseline")
    fun observeAll(): Flow<List<NetworkBaseline>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(baseline: NetworkBaseline)

    @Update
    suspend fun update(baseline: NetworkBaseline)

    @Query("UPDATE network_baseline SET isTrusted = :trusted WHERE ssid = :ssid")
    suspend fun setTrusted(ssid: String, trusted: Boolean)

    @Query("DELETE FROM network_baseline WHERE ssid = :ssid")
    suspend fun delete(ssid: String)
}
