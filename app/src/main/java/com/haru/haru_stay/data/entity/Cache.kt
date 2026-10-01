package com.haru.haru_stay.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 4. 캐싱데이터 (cache_db)
@Entity(tableName = "cache_db")
data class Cache(
    @PrimaryKey(autoGenerate = true) val chId: Long = 0,
    val chTime: Long,
    val chCellKey: String,
    val chWifiMac: String,
    val chAdmCode: String,
    val chAdmName: String,
    val chBusStop: String,
    val chGpsLat: Double,
    val chGpsLon: Double
)