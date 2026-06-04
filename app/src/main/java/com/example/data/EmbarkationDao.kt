package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EmbarkationDao {
    @Query("SELECT * FROM embarkations ORDER BY signOnDate DESC")
    fun getAllEmbarkations(): Flow<List<Embarkation>>

    @Query("SELECT * FROM embarkations WHERE id = :id LIMIT 1")
    suspend fun getEmbarkationById(id: Int): Embarkation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbarkation(embarkation: Embarkation): Long

    @Delete
    suspend fun deleteEmbarkation(embarkation: Embarkation)
}
