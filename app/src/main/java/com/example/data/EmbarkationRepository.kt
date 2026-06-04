package com.example.data

import kotlinx.coroutines.flow.Flow

class EmbarkationRepository(private val embarkationDao: EmbarkationDao) {
    val allEmbarkations: Flow<List<Embarkation>> = embarkationDao.getAllEmbarkations()

    suspend fun getEmbarkationById(id: Int): Embarkation? {
        return embarkationDao.getEmbarkationById(id)
    }

    suspend fun insert(embarkation: Embarkation): Long {
        return embarkationDao.insertEmbarkation(embarkation)
    }

    suspend fun delete(embarkation: Embarkation) {
        embarkationDao.deleteEmbarkation(embarkation)
    }
}
