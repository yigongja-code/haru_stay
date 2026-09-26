package com.example.haru_spot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fav_db")
data class Fav(
    @PrimaryKey(autoGenerate = true) val favId: Long = 0,

    val favAdmCode: String,
    val favAdmName: String,
    val favBusStop: String,
    val favCellKey: String,
    val favWifiMac: String,
    val favGpsLat: Double,
    val favGpsLon: Double,
    val favInDate: Long,
    val favGeoFnc: Long,
    val favStats: String, //Favorite,user
    val favLinkedBusStopId: Long,
    val favField2: String,
    val favField3: String,
    val favField4: String,
    val favField5: String,

    )