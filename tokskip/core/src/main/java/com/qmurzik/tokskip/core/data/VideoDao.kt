package com.qmurzik.tokskip.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<VideoEntry>>

    @Query("SELECT * FROM videos ORDER BY updatedAt DESC")
    suspend fun all(): List<VideoEntry>

    @Query("SELECT * FROM videos WHERE videoId IN (:ids)")
    suspend fun byIds(ids: List<String>): List<VideoEntry>

    @Query("SELECT * FROM videos WHERE videoId = :id")
    suspend fun byId(id: String): VideoEntry?

    @Upsert
    suspend fun upsert(entry: VideoEntry)

    @Upsert
    suspend fun upsertAll(entries: List<VideoEntry>)

    @Query("DELETE FROM videos WHERE videoId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM videos")
    suspend fun clear()
}
