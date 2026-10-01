package com.haru.haru_stay.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.haru.haru_stay.data.entity.Fav

@Dao
interface FavoriteDao {
    @Insert
    suspend fun insert(fav: Fav)

    @Query("SELECT * FROM fav_db")
    suspend fun getAllFavList(): List<Fav>

    @Query("SELECT * FROM fav_db WHERE favStats = 'user' ORDER BY favBusStop ASC")
    suspend fun getUserFavs(): List<Fav>

    // FavoriteDao.kt 예시
    @Query("DELETE FROM fav_db WHERE favLinkedBusStopId = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE fav_db SET favBusStop = :newText WHERE favLinkedBusStopId = :id")
    suspend fun updateBusStopText(id: Long, newText: String)

    @Query("SELECT * FROM fav_db WHERE favLinkedBusStopId = :favId LIMIT 1")
    suspend fun getFavById(favId: Long): Fav?

    @Query("""
    UPDATE fav_db 
    SET favBusStop = :name, 
        favAdmName = :addr, 
        favAdmCode = :code
    WHERE favLinkedBusStopId = :id
""")
    suspend fun updateUserSpotInfo(
        id: Long,
        name: String,
        addr: String,
        code: String
    )

    // 버정보다 우선되는 favDB에서 가까운 지점을 찾음
    @Query("""
    SELECT * FROM fav_db 
    WHERE favGpsLat BETWEEN (:currentLat - 0.02) AND (:currentLat + 0.02)
      AND favGpsLon BETWEEN (:currentLon - 0.02) AND (:currentLon + 0.02)
    ORDER BY ((favGpsLat - :currentLat) * (favGpsLat - :currentLat) + (favGpsLon - :currentLon) * (favGpsLon - :currentLon)) ASC
    LIMIT 1
""")
    suspend fun findNearestFav(currentLat: Double, currentLon: Double): Fav?

    @Query("DELETE FROM fav_db") // 👈 형님의 실제 테이블 이름에 맞춤 (보통 entity명이 테이블명)
    suspend fun deleteAllFav()


}