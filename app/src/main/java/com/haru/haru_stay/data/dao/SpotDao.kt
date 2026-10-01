package com.haru.haru_stay.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.haru.haru_stay.data.entity.Spot

@Dao
interface SpotDao {
    @Insert
    suspend fun insert(spot: Spot)

    @Query("SELECT * FROM spot_db ORDER BY spStartTime DESC")
    suspend fun getAllSpots(): List<Spot>

    // 💡 Spot 리스트를 한 번에(Bulk) DB에 안전하게 밀어 넣는 인서트 쿼리!
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpots(spots: List<Spot>)

    // 단건 추가용
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpot(spot: Spot)

    @Query("SELECT * FROM spot_db WHERE spStartTime <= :endOfDay AND spEndTime >= :startOfDay ORDER BY spStartTime ASC")
    suspend fun getSpotsOverlappingDate(startOfDay: Long, endOfDay: Long): List<Spot>

    @Insert
    suspend fun insertT(spot: Spot)
    // 또는 insertSpot(spot: Spot)

    //마지막 스팟 한개를 불러옴
    @Query("SELECT * FROM spot_db ORDER BY spEndTime DESC LIMIT 1")
    suspend fun getLastSpot(): Spot?


    // 💡 기존 Spot 엔티티의 변경된 필드(spEndTime, spSpotTime 등)를 반영하는 업데이터 쿼리!
    @Update
    suspend fun updateSpot(spot: Spot)


    // ==========================================
    // 🧹 [추가] 데이터 삭제용 쿼리 모음
    // ==========================================

    // 1. spot_db 테이블의 모든 데이터를 한 방에 삭제하는 쿼리
    @Query("DELETE FROM spot_db")
    suspend fun deleteAllSpots()

    // 2. 특정 Spot 엔티티 객체를 전달받아 삭제하는 메서드
    @Delete
    suspend fun deleteSpot(spot: Spot)

    // 💡 선택한 날짜(startOfDay) 이전에 존재했거나 걸쳐 있는 가장 마지막 스팟을 쏙 뽑아오는 전용 쿼리
    @Query("SELECT * FROM spot_db WHERE spStartTime < :start ORDER BY spStartTime DESC LIMIT 1")
    suspend fun getPreviousSpot(start: Long): Spot?

    @Query("SELECT * FROM spot_db WHERE spAdmName LIKE :keyword ORDER BY spStartTime DESC")
    suspend fun searchSpotsByName(keyword: String): List<Spot>

    @Query(
        """
    SELECT * FROM spot_db
    WHERE spAdmCode = :admCode
      AND spStartTime >= :threeMonthsAgo
      AND spStartTime < :todayStartTimestamp
    ORDER BY spStartTime DESC
    LIMIT 1
"""
    )
    suspend fun 오늘을제외한100일간의최근1개spotDB가져옴(
        admCode: String, // 파라미터 이름을 admName에서 admCode로 변경
        threeMonthsAgo: Long,
        todayStartTimestamp: Long
    ): Spot?
    
    data class SpotVisitCountResult(
        val spAdmCode: String,
        val spAdmName: String, // 화면 표출용 이름
        val visitCount: Int
    )

    @Query("""
    SELECT * FROM spot_db 
    WHERE spAdmName = :admName
    ORDER BY spStartTime DESC 
    LIMIT 1
""")
    suspend fun 가장최근spotDB가져옴(admName: String): Spot?



    @Query("""
    SELECT spAdmCode, MAX(spAdmName) as spAdmName, COUNT(*) as visitCount 
    FROM spot_db 
    WHERE spStartTime >= :threeMonthsAgo
      AND spAdmCode IS NOT NULL 
      AND spAdmCode != '' 
    GROUP BY spAdmCode 
    ORDER BY visitCount DESC
""")
    suspend fun getSpotVisitRankingByCode(threeMonthsAgo: Long): List<SpotVisitCountResult>


    data class SpotNameAndStats(
        val spAdmName: String,
        val spStartTime: Long,
        val spEndTime: Long,
        val spSpotTime: Long
    )
    @Query(
        """
    SELECT spAdmName, spStartTime, spEndTime, spSpotTime FROM spot_db
    WHERE spAdmCode = :admCode
      AND spStartTime >= :threeMonthsAgo
      AND spStartTime < :todayStartTimestamp
    ORDER BY spStartTime DESC
    LIMIT 1
"""
    )
    suspend fun 오늘을제외한100일간의최근1개spotName가져옴(
        admCode: String, // 👈 입력은 확실하게 코드로 받고!
        threeMonthsAgo: Long,
        todayStartTimestamp: Long
    ): SpotNameAndStats? // 👈 반환은 사람이 알아볼 수 있는 이름이 포함된 객체로!


    //code로 비교해서 필드전체를 가져옴
    @Query("""
    SELECT * FROM spot_db 
    WHERE spAdmCode = :admCode
    ORDER BY spStartTime DESC 
    LIMIT 1
""")
    suspend fun 가장최근spotDB를code로가져옴(admCode: String): Spot?

    // 💡 체류 시간 랭킹 전용 가벼운 DTO 선언
    data class SpotStayRankingDto(
        val spAdmCode: String,
        val spAdmName: String,
        val totalStayTime: Long,
        val visitCount: Int
    )

    @Query("""
    SELECT
        spAdmCode,
        MAX(spAdmName) AS spAdmName,
        SUM(spSpotTime) AS totalStayTime,
        COUNT(*) AS visitCount
    FROM spot_db
    WHERE spStartTime >= :threeMonthsAgo
    GROUP BY spAdmCode
    ORDER BY totalStayTime DESC
""")
    suspend fun 랭킹체류기간쿼리(
        threeMonthsAgo: Long
    ): List<SpotStayRankingDto>


    @Query("SELECT * FROM spot_db WHERE spStartTime = :start  LIMIT 1")
    suspend fun 메모id저장대상(start: Long): Spot?

    //spotDB가 존재하는지 확인 - DB초기화용
    @Query("SELECT COUNT(*) FROM spot_db")
    suspend fun getSpotCount(): Int

    //sumutil 랭킹계산
    @Query("""
    SELECT *
    FROM spot_db
    WHERE spStartTime >= :startTime
    ORDER BY spStartTime ASC
""")
    suspend fun 기간내SpotDB가져옴(
        startTime: Long
    ): List<Spot>

    @Query("""
    SELECT *
    FROM spot_db
    WHERE spEndTime > :lastProcessedTime
      AND spEndTime <= :processingTime
    ORDER BY spEndTime ASC
""")
    suspend fun 마지막처리이후종료된Spot가져옴(
        lastProcessedTime: Long,
        processingTime: Long
    ): List<Spot>

    @Query("""
    SELECT *
    FROM spot_db
    WHERE spAdmCode = :admCode
      AND spStartTime >= :startTime
    ORDER BY spStartTime ASC
""")
    suspend fun 행정동기간내SpotDB가져옴(
        admCode: String,
        startTime: Long
    ): List<Spot>

    //가장 오래된 spotDB의 시작시간을 가져옴 - 날짜이동시 무한정 과거로 가는거 방지용
    @Query("SELECT MIN(spStartTime) FROM spot_db")
    suspend fun getOldestSpotTime(): Long?

    //입력받은 시간 이내의 스팟을 삭제 - 의미없는 시간의 스팟을 정리
    @Query("""
    DELETE FROM spot_db
    WHERE spProcessedAt = 0
      AND spSpotTime < :shortSpotLimitMillis
      AND spId != (
          SELECT spId
          FROM spot_db
          ORDER BY spEndTime DESC
          LIMIT 1
      )
""")
    suspend fun deleteShortUnprocessedSpots(shortSpotLimitMillis: Long)

    //의미없는 스팟을 정리후 재가공하지 않기위해 현재시간을 마킹
    @Query("""
    UPDATE spot_db
    SET spProcessedAt = :processedAt
    WHERE spProcessedAt = 0
      AND spId != (
          SELECT spId
          FROM spot_db
          ORDER BY spEndTime DESC
          LIMIT 1
      )
""")
    suspend fun markSpotsProcessed(processedAt: Long)
}