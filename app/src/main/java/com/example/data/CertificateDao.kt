package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CertificateDao {
    @Query("SELECT * FROM certificates ORDER BY expiryDate ASC")
    fun getAllCertificates(): Flow<List<Certificate>>

    @Query("SELECT * FROM certificates WHERE id = :id LIMIT 1")
    suspend fun getCertificateById(id: Int): Certificate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCertificate(certificate: Certificate): Long

    @Delete
    suspend fun deleteCertificate(certificate: Certificate)
}
