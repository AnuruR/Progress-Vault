package com.anuru.progressvault.data.repository

import com.anuru.progressvault.data.db.dao.SyncDao
import com.anuru.progressvault.data.db.entity.SyncSource
import com.anuru.progressvault.data.db.entity.SyncStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncStatusRepository @Inject constructor(private val syncDao: SyncDao) {
    fun observeAll(): Flow<Map<SyncSource, SyncStateEntity?>> = combine(
        syncDao.observeSyncState(SyncSource.LEETCODE),
        syncDao.observeSyncState(SyncSource.GITHUB),
    ) { lc, gh -> mapOf(SyncSource.LEETCODE to lc, SyncSource.GITHUB to gh) }
}
