package com.example.haru_spot.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.haru_spot.data.entity.Cache

@Dao
interface CacheDao {
    @Insert
    suspend fun insert(cache: Cache)

    @Query("SELECT * FROM cache_db")
    suspend fun getAllCache(): List<Cache>
}