package com.haru.haru_stay.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.haru.haru_stay.data.entity.Cache

@Dao
interface CacheDao {
    @Insert
    suspend fun insert(cache: Cache)

    @Query("SELECT * FROM cache_db")
    suspend fun getAllCache(): List<Cache>
}