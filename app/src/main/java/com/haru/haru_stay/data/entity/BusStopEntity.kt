package com.haru.haru_stay.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// 💡 국토교통부 버스정류장 데이터 + SHP 공간 조인 가공 스펙 반영 엔티티
@Entity(
    tableName = "bus_stops",
    indices = [
        Index(value = ["GPS_LATI"]), // 위도 인덱스 (위치 기반 검색 최적화)
        Index(value = ["GPS_LONG"])  // 경도 인덱스
    ]
)
data class BusStopEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "NODE_NM")
    val stopName: String,     // 정류장명 (NODE_NM)

    @ColumnInfo(name = "GPS_LATI")
    val lat: Double,          // 위도 (소수점 5자리 반올림 적용)

    @ColumnInfo(name = "GPS_LONG")
    val lon: Double,          // 경도 (소수점 5자리 반올림 적용)

    @ColumnInfo(name = "CITY_CD")
    val admCode: String,      // 행정동 코드 (10자리, 앞자리 0 보존을 위해 String 고정)

    @ColumnInfo(name = "CITY_NAME")
    val admName: String       // 동 단위 상세 주소 (예: 원주시 명륜동)


)