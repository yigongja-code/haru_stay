package com.haru.haru_stay.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.haru.haru_stay.data.entity.BusStopEntity

@Dao
interface BusStopDao {

    // 💡 최초 실행 시 20만 건의 버스정류장 데이터를 한 번에 밀어 넣는 벌크 인서트
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<BusStopEntity>)

    @Query("SELECT COUNT(*) FROM bus_stops")
    suspend fun getCount(): Int

    // 💡 Bounding Box(사각 영역) 1차 필터링 후 피타고라스 근사치로 가장 가까운 정류장 단 1개 고속 조회
    // 💡 Bounding Box(사각 영역) 1차 필터링 후 피타고라스 근사치로 가장 가까운 정류장 단 1개 고속 조회
    @Query("""
        SELECT * FROM bus_stops 
        WHERE GPS_LATI BETWEEN (:currentLat - 0.02) AND (:currentLat + 0.02)
          AND GPS_LONG BETWEEN (:currentLon - 0.02) AND (:currentLon + 0.02)
        ORDER BY ((GPS_LATI - :currentLat) * (GPS_LATI - :currentLat) + (GPS_LONG - :currentLon) * (GPS_LONG - :currentLon)) ASC
        LIMIT 1
    """)
    suspend fun findNearestBusStop(currentLat: Double, currentLon: Double): BusStopEntity?


    //=====================================================
    //사용자가 만드는 가상의 버정관련 쿼리
    //====================================================
    // 💡 만약 개수 조회 함수가 필요하다면 이렇게 정상적으로 함수명까지 완성해 줍니다!
    @Query("SELECT COUNT(*) FROM bus_stops")
    suspend fun getBusStopCount(): Int

    // 1. 가상의 버정 데이터를 원천 테이블에 꽂고 자동 생성된 PK(id)를 반환 받기
    @Insert
    suspend fun insert(busStop: BusStopEntity): Long

    // 2. 나중에 Fav에서 수정할 때, linkedBusStopId로 원천 버정 정보를 통째로 업데이트 칠 때 사용
    @Update
    suspend fun update(busStop: BusStopEntity)

    // 3. 특정 ID의 원천 버정 이름(정류장명)만 빠르게 바꿀 때
    @Query("UPDATE bus_stops SET NODE_NM = :newName WHERE id = :stopId")
    suspend fun updateStopName(stopId: Long, newName: String)

    // 4. 나중에 특정 ID로 원천 버정 정보를 조회해 올 때
    @Query("SELECT * FROM bus_stops WHERE id = :stopId")
    suspend fun getStopById(stopId: Long): BusStopEntity?

    @Query("DELETE FROM bus_stops WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE bus_stops SET NODE_NM = :newText WHERE id = :id")
    suspend fun updateBusStopText(id: Long, newText: String)

    // 💡 읍면동 이름(CITY_NAME)으로 버스 정류장 목록 조회
    // 💡 주소(CITY_NAME)와 코드(CITY_CD)를 쌍으로 중복 없이 가져오기
    /*@Query("""
        SELECT DISTINCT CITY_CD
        FROM bus_stops 
        WHERE CITY_NAME LIKE '%' || :keyword || '%'
    """)
    suspend fun searchDistinctAdmByKeyword(keyword: String): List<BusStopEntity>*/
    // 💡 키워드로 검색하고 CITY_CD 기준 중복을 쳐낸 뒤 엔티티 전체를 통째로 가져오기
    // 💡 키워드로 검색하고, 행정동 코드(CITY_CD) 기준으로 중복을 다 쳐낸 뒤
    //    그 행에 포함된 모든 필드를 List<BusStopEntity>에 담아오기
    @Query("""
        SELECT * FROM bus_stops 
        WHERE CITY_NAME LIKE '%' || :keyword || '%'
        GROUP BY CITY_CD
    """)
    suspend fun searchDistinctAdmByKeyword(keyword: String): List<BusStopEntity>

    @Query("""
    UPDATE bus_stops 
    SET NODE_NM = :name, 
        CITY_CD = :code, 
        CITY_NAME = :addr 
    WHERE id = :id
""")
    suspend fun updateBusStopInfo(
        id: Long,
        name: String,
        code: String,
        addr: String
    )

    // 전부삭제..
    @Query("DELETE FROM bus_stops")
    suspend fun deleteAllBusStops()



} // 👈 인터페이스 전체를 닫는 마지막 중괄호는 딱 여기 하나만 있어야 합니다!