package com.haru.haru_stay.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.haru.haru_stay.data.entity.VisitLog
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Insert
    suspend fun insert(log: VisitLog)

    //@Query("SELECT * FROM log_db ORDER BY logStartTime DESC")
    //suspend fun getAllLogs(): List<VisitLog>

    @Query("SELECT * FROM log_db ORDER BY logId DESC")
    fun getAllLogs(): Flow<List<VisitLog>>

    @Query("DELETE FROM log_db")
    suspend fun deleteAllLogs()
    /**
     * 🔥 [단독 로그 삭제 쿼리]
     * - 무결성이 깨졌거나 필수 값이 누락된 특정 로그 하나를 logId 기준으로 즉시 삭제합니다.
     * */
    @Query("DELETE FROM log_db WHERE logId = :logId")
    suspend fun deleteLogById(logId: Long)

    /**
     * 💡 [통합 미처리 로그 조회 쿼리]
     * - 2차 가공이 아직 이루어지지 않은(logProcessedAt = 0) 모든 로그를 시간순으로 조회합니다.
     */
    @Query("SELECT * FROM log_db WHERE logProcessedAt = 0 ORDER BY logStartTime ASC")
    suspend fun getUnprocessedLogs(): List<VisitLog>

    @Query("SELECT * FROM log_db WHERE logProcessedAt = 0 AND logStats = 'merge' ORDER BY logStartTime ASC")
    suspend fun 머지만가져오는2차가공용쿼리(): List<VisitLog>

    //로그의 마지막값을 확인해서 머지면 프로세스값을0을 줘서 이후 들어오는 수집데이터와 연결시킴
    //마지막값이 hold면 이전의 머지데이터는 끝났으므로 프로세스값을 주어서 가공종료 다시 값이 추가 되지 않게 막음
    @Query("SELECT logStats FROM log_db ORDER BY logId DESC LIMIT 1")
    suspend fun 이차가공_마지막로그상태확인용(): String?

    @Query("SELECT * FROM log_db WHERE logProcessedAt > 0 ORDER BY logEndTime DESC LIMIT 1")
    suspend fun getLastProcessedLog(): VisitLog?

    @Update
    suspend fun updateLog(log: VisitLog)

    @Query("UPDATE log_db SET logProcessedAt = :currentTime WHERE logId IN (:logIds)")
    suspend fun updateLogsProcessedAt(logIds: List<Long>, currentTime: Long = System.currentTimeMillis())

    // ... (기존 기타 쿼리 생략)
    /**
     * 🔥 [종료 시간 무결성 대상 조회 쿼리]
     * - 상태값(pending 등)을 따지며 복잡하게 조회하지 않고,
     * - 오직 종료 시간이 비어 있는(logEndTime이 0이거나 null인) 데이터들을
     * - 기록 시작 시간(logStartTime) 기준 과거부터 최신순(ASC)으로 가볍게 가져옵니다.
     */
    @Query("SELECT * FROM log_db WHERE logEndTime = 0 OR logEndTime IS NULL ORDER BY logStartTime ASC")
    suspend fun getLogsWithZeroOrNullEndTime(): List<VisitLog>

    @Query("SELECT * FROM log_db ORDER BY logId DESC")
    suspend fun getAllLogsList(): List<VisitLog>

    @Query("SELECT * FROM log_db ORDER BY logStartTime DESC LIMIT 1")
    suspend fun getLatestLog(): VisitLog?

    //logdb를 수집한 초기상태로 만들어주기
    // 💡 'pending'이 아닌(이미 가공된) 데이터들만 골라서 다시 pending 상태로 초기화!
    @Query("""
        UPDATE log_db 
        SET logEndTime = 0, 
            logStats = 'pending', 
            logProcessedAt = 0 
        WHERE logStats != 'pending'
    """)
    suspend fun resetAllLogsToRaw()

    //2층 확대 박스에 버스정류장등을 리스트화해서 뿌려줄 쿼리
    //spotdb의 시작시간과 종료시간을 받아와서 logdb의 시작시간  종료시간안의 값음 모두 가져옴
    @Query("SELECT * FROM log_db WHERE logStartTime >= :startTimestamp AND logEndTime <= :endTimestamp AND logEndTime >= 0 ORDER BY logStartTime ASC")
    suspend fun getLogsBetween(startTimestamp: Long, endTimestamp: Long): List<VisitLog>
    //10분 정규화로 이전dao주석
    /*@Query("""
    SELECT * FROM log_db
    WHERE logStartTime >= (:startTimestamp - 600000)
      AND logEndTime <= (:endTimestamp + 600000)
      AND logAdmCode = :admCode
      AND logEndTime >= 0
    ORDER BY logStartTime ASC
""")
    suspend fun getLogsBetween(
        startTimestamp: Long,
        endTimestamp: Long,
        admCode: String
    ): List<VisitLog> */

    @Query("SELECT * FROM log_db WHERE logGpsLat BETWEEN :minLat AND :maxLat AND logGpsLon BETWEEN :minLon AND :maxLon ")
    suspend fun getLogsInBoundingBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<VisitLog>

    /**
     * 🔥 [최신 로그의 행정동 코드 조회 쿼리]
     * - logId 기준(또는 logStartTime 기준)으로 가장 최근에 기록된 로그 1개를 찾아
     * - 그 안의 행정동 코드(logAdmCode) 문자열만 가볍게 반환합니다.
     */
    @Query("SELECT logAdmCode FROM log_db ORDER BY logId DESC LIMIT 1")
    suspend fun 최근의로그에서admcode가져옴(): String?

    @Update
    suspend fun updateLogs(logs: List<VisitLog>)

    // 즉시수집시 최근로그와 시작시같이 같은걸 방어
    @Query("SELECT logStartTime FROM log_db ORDER BY logStartTime DESC LIMIT 1")
    suspend fun 가장최근의로그시작시간가져옴(): Long?

    //오늘날자의 logDB 긁어오기
    @Query("""
    SELECT *
    FROM log_db
    WHERE logStartTime >= :startTime
      AND logStartTime < :endTime
    ORDER BY logStartTime ASC
""")
    suspend fun getLogsByStartTime(
        startTime: Long,
        endTime: Long
    ): List<VisitLog>

}