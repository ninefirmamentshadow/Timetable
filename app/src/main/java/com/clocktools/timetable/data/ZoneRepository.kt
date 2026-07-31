package com.clocktools.timetable.data

import kotlinx.coroutines.flow.Flow

class ZoneRepository(private val dao: ZoneDao) {
    fun observeAll(): Flow<List<Zone>> = dao.observeAll()

    suspend fun getById(id: Long): Zone? = dao.getById(id)

    suspend fun insert(zone: Zone): Long = dao.insert(zone)

    suspend fun update(zone: Zone) = dao.update(zone)

    suspend fun delete(zone: Zone) = dao.delete(zone)
}
