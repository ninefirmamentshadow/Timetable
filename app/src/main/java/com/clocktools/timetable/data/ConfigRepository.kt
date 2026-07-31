package com.clocktools.timetable.data

import kotlinx.coroutines.flow.Flow

class ConfigRepository(private val dao: AppConfigDao) {
    fun observe(): Flow<AppConfig?> = dao.observe()

    suspend fun getOnce(): AppConfig? = dao.getOnce()

    suspend fun upsert(config: AppConfig) = dao.upsert(config)
}
