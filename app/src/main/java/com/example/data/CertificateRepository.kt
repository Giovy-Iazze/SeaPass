package com.example.data

import kotlinx.coroutines.flow.Flow

class CertificateRepository(private val certificateDao: CertificateDao) {
    val allCertificates: Flow<List<Certificate>> = certificateDao.getAllCertificates()

    suspend fun getCertificateById(id: Int): Certificate? {
        return certificateDao.getCertificateById(id)
    }

    suspend fun insert(certificate: Certificate): Long {
        return certificateDao.insertCertificate(certificate)
    }

    suspend fun delete(certificate: Certificate) {
        certificateDao.deleteCertificate(certificate)
    }
}
