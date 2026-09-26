package com.example.haru_spot.util

import android.content.Context
import android.util.Log
import com.example.haru_spot.data.database.AppDatabase
import com.example.haru_spot.data.entity.Spot
import com.example.haru_spot.data.entity.Sum_db
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SumUtil {

    companion object {

        // ==========================================================
        // 관리행 코드
        // ==========================================================

        private const val MANAGEMENT_CODE = "9999999999"


        // ==========================================================
        // 통계 계산
        //
        // 최초 / 전체 재계산
        //      → 최근 N일 전체 계산
        //
        // 이후
        //      → 마지막 처리 이후 종료된 Spot 확인
        //      → 영향받은 행정동만 다시 계산
        // ==========================================================

        suspend fun calculateSum(
            context: Context,
            periodDays: Int = 90
        ) {

            val database =
                AppDatabase.getDatabase(context)

            val spotDao =
                database.spotDao()

            val sumDao =
                database.sumDao()

            // 🔥 테스트용: 기존 Sum DB 전체 삭제
            // sumDao.전체SumDB삭제()


            // ======================================================
            // 계산 시작 시각
            //
            // 이 시간을 이번 처리의 기준점으로 고정한다.
            //
            // 처리 도중 새로 종료되는 Spot은
            // 다음 계산에서 처리된다.
            // ======================================================

            val processingStart =
                System.currentTimeMillis()


            // ======================================================
            // 기간 계산
            // ======================================================

            val periodMillis =
                periodDays.toLong() *
                        24L *
                        60L *
                        60L *
                        1000L

            val startTime =
                processingStart - periodMillis


            // ======================================================
            // 관리행 Key
            //
            // 예:
            // 90일  → 9999999999090
            // 180일 → 9999999999180
            // ======================================================

            val periodText =
                periodDays
                    .toString()
                    .padStart(3, '0')

            val managementKey =
                "$MANAGEMENT_CODE$periodText"


            // ======================================================
            // 관리행 조회
            // ======================================================

            val managementRow =
                withContext(Dispatchers.IO) {
                    sumDao.sumKey로가져옴(
                        managementKey
                    )
                }


            // ======================================================
            // 관리행이 없으면
            //
            // 아직 한 번도 전체 계산을 하지 않은 상태
            // ======================================================

            if (managementRow == null) {

                Log.e(
                    "SumUtil",
                    "📊 관리행 없음 → 전체 계산 시작"
                )

                전체계산(
                    spotDao = spotDao,
                    sumDao = sumDao,
                    periodDays = periodDays,
                    startTime = startTime,
                    processingStart = processingStart,
                    managementKey = managementKey
                )

                return
            }


            // ======================================================
            // 관리행이 존재함
            //
            // sumLastTime = 마지막 통계 처리 커서
            // ======================================================

            val lastProcessedTime =
                managementRow.sumLastTime


            // ======================================================
            // 관리행의 커서가 이상하면
            //
            // 0 이하라면 정상적인 증분 계산을 할 수 없으므로
            // 전체 계산으로 복구한다.
            // ======================================================

            if (lastProcessedTime <= 0L) {

                Log.e(
                    "SumUtil",
                    "📊 관리행 커서 없음 → 전체 계산"
                )

                전체계산(
                    spotDao = spotDao,
                    sumDao = sumDao,
                    periodDays = periodDays,
                    startTime = startTime,
                    processingStart = processingStart,
                    managementKey = managementKey
                )

                return
            }


            // ======================================================
            // 증분 계산
            //
            // 마지막 처리 이후 종료된 Spot만 가져온다.
            //
            // lastProcessedTime < spEndTime <= processingStart
            //
            // processingStart 이후 종료된 Spot은
            // 다음 실행에서 처리된다.
            // ======================================================

            val newFinishedSpots =
                withContext(Dispatchers.IO) {

                    spotDao.마지막처리이후종료된Spot가져옴(
                        lastProcessedTime =
                            lastProcessedTime,

                        processingTime =
                            processingStart
                    )
                }


            // ======================================================
            // 새로 종료된 Spot이 하나도 없음
            //
            // 그래도 관리행 커서는 앞으로 이동시킨다.
            //
            // 그렇지 않으면 다음 실행에서도
            // 같은 시간 구간을 계속 검사하게 된다.
            // ======================================================

            if (newFinishedSpots.isEmpty()) {

                val updatedManagement =
                    managementRow.copy(

                        sumLastTime =
                            processingStart
                    )

                withContext(Dispatchers.IO) {
                    sumDao.updateSum(
                        updatedManagement
                    )
                }

                Log.e(
                    "SumUtil",
                    "📊 증분 대상 없음"
                )

                Log.e(
                    "SumUtil",
                    "📊 관리행 커서 갱신 = $processingStart"
                )

                return
            }


            // ======================================================
            // 영향받은 행정동 추출
            //
            // 같은 행정동에서 Spot이 여러 개 생겨도
            // 한 번만 재계산한다.
            // ======================================================

            val affectedAdmCodes =
                newFinishedSpots
                    .map {
                        it.spAdmCode
                    }
                    .filter {
                        it.isNotBlank()
                    }
                    .filter {
                        it != MANAGEMENT_CODE
                    }
                    .toSet()


            Log.e(
                "SumUtil",
                "📊 새 종료 Spot = ${newFinishedSpots.size}건"
            )

            Log.e(
                "SumUtil",
                "📊 영향 행정동 = ${affectedAdmCodes.size}개"
            )


            // ======================================================
            // 영향받은 행정동만 재계산
            //
            // Rank는 여기서 다시 계산하지 않는다.
            // 기존 Rank를 그대로 유지한다.
            // ======================================================

            var updateCount = 0
            var insertCount = 0


            withContext(Dispatchers.IO) {

                for (admCode in affectedAdmCodes) {

                    val spots =
                        spotDao.행정동기간내SpotDB가져옴(
                            admCode =
                                admCode,

                            startTime =
                                startTime
                        )


                    // ----------------------------------------------
                    // 최근 N일 데이터가 하나도 없다면
                    //
                    // 일반적으로 발생하지 않지만
                    // 현재 Sum 행이 존재하면 삭제하지 않는다.
                    //
                    // 정확한 만료 제거는 전체 재계산에서 처리한다.
                    // ----------------------------------------------

                    if (spots.isEmpty()) {
                        continue
                    }


                    // ----------------------------------------------
                    // 행정동 집계
                    // ----------------------------------------------

                    val result =
                        계산행정동(
                            spots = spots,
                            now = processingStart
                        )


                    // ----------------------------------------------
                    // 기존 Sum Key
                    // ----------------------------------------------

                    val sumKey =
                        "$admCode$periodText"


                    // ----------------------------------------------
                    // 기존 Sum 조회
                    // ----------------------------------------------

                    val oldSum =
                        sumDao.sumKey로가져옴(
                            sumKey
                        )


                    if (oldSum != null) {

                        // ------------------------------------------
                        // 증분 계산
                        //
                        // Rank는 기존 값 유지
                        // ------------------------------------------

                        val updatedSum =
                            oldSum.copy(

                                sumAdmCode =
                                    admCode,

                                sumAdmName =
                                    result.admName,

                                sumVisitCount =
                                    result.visitCount,

                                sumStayMinutes =
                                    result.stayMinutes,

                                sumLastTime =
                                    result.lastVisitTime,

                                sumPeriodDays =
                                    periodDays

                                // sumVisitRank
                                // sumStayRank
                                //
                                // 기존 값 그대로 유지
                            )

                        sumDao.updateSum(
                            updatedSum
                        )

                        updateCount++

                    } else {

                        // ------------------------------------------
                        // 기존 Sum이 없으면 신규 생성
                        //
                        // 아직 Rank를 계산하지 않았으므로
                        // Rank = 0
                        // ------------------------------------------

                        val newSum =
                            Sum_db(

                                sumKey =
                                    sumKey,

                                sumAdmCode =
                                    admCode,

                                sumAdmName =
                                    result.admName,

                                sumVisitRank =
                                    0,

                                sumVisitCount =
                                    result.visitCount,

                                sumStayRank =
                                    0,

                                sumStayMinutes =
                                    result.stayMinutes,

                                sumLastTime =
                                    result.lastVisitTime,

                                sumPeriodDays =
                                    periodDays
                            )

                        sumDao.insertSum(
                            newSum
                        )

                        insertCount++
                    }
                }
            }


            // ======================================================
            // 모든 증분 처리 성공
            //
            // 마지막에 관리행 커서를 이동한다.
            // ======================================================

            val updatedManagement =
                managementRow.copy(

                    sumLastTime =
                        processingStart
                )

            withContext(Dispatchers.IO) {
                sumDao.updateSum(
                    updatedManagement
                )
            }


            // ======================================================
            // 결과 로그
            // ======================================================

            Log.e(
                "SumUtil",
                "📊 증분 계산 완료"
            )

            Log.e(
                "SumUtil",
                "📊 UPDATE = $updateCount"
            )

            Log.e(
                "SumUtil",
                "📊 INSERT = $insertCount"
            )

            Log.e(
                "SumUtil",
                "📊 처리 Spot = ${newFinishedSpots.size}"
            )

            Log.e(
                "SumUtil",
                "📊 영향 행정동 = ${affectedAdmCodes.size}"
            )

            Log.e(
                "SumUtil",
                "📊 관리행 커서 = $processingStart"
            )
        }


        // ==========================================================
        // 전체 계산
        // ==========================================================

        private suspend fun 전체계산(
            spotDao: com.example.haru_spot.data.dao.SpotDao,
            sumDao: com.example.haru_spot.data.dao.SumDao,
            periodDays: Int,
            startTime: Long,
            processingStart: Long,
            managementKey: String
        ) {

            val periodText =
                periodDays
                    .toString()
                    .padStart(3, '0')


            // ======================================================
            // 1. 해당 기간의 일반 Sum만 삭제
            //
            // ★ 관리행 9999999999는 삭제하지 않는다.
            // ======================================================

            withContext(Dispatchers.IO) {

                sumDao.해당기간SumDB삭제(
                    periodDays
                )
            }


            // ======================================================
            // 2. 최근 N일 Spot 전체 조회
            // ======================================================

            val spots =
                withContext(Dispatchers.IO) {

                    spotDao.기간내SpotDB가져옴(
                        startTime
                    )
                }


            Log.e(
                "SumUtil",
                "📊 전체 계산 Spot = ${spots.size}건"
            )


            // ======================================================
            // 3. 집계 Map
            // ======================================================

            val visitCountMap =
                mutableMapOf<String, Int>()

            val stayMinutesMap =
                mutableMapOf<String, Long>()

            val lastVisitMap =
                mutableMapOf<String, Long>()

            val admNameMap =
                mutableMapOf<String, String>()


            // ======================================================
            // 4. Spot 전체 순회
            // ======================================================

            for (spot in spots) {

                val admCode =
                    spot.spAdmCode

                if (admCode.isBlank()) {
                    continue
                }

                if (admCode == MANAGEMENT_CODE) {
                    continue
                }


                // 행정동 이름

                admNameMap[admCode] =
                    spot.spAdmName ?: ""


                // 방문 횟수

                visitCountMap[admCode] =
                    (visitCountMap[admCode] ?: 0) + 1


                // 마지막 방문시간

                val oldLastVisit =
                    lastVisitMap[admCode]

                if (
                    oldLastVisit == null ||
                    spot.spStartTime > oldLastVisit
                ) {

                    lastVisitMap[admCode] =
                        spot.spStartTime
                }


                // 체류시간

                /*val endTime =
                    spot.spEndTime
                        ?: processingStart */

                val stayMillis  = spot.spSpotTime

                val safeStayMillis =
                    if (stayMillis > 0L) {
                        stayMillis
                    } else {
                        0L
                    }

                val stayMinutes =
                    safeStayMillis /
                            (60L * 1000L)

                Log.e(
                    "SumUtil",
                    "🔥 ${spot.spAdmName} / spSpotTime=${spot.spSpotTime} / " +
                            "분=${spot.spSpotTime / 60000L}"
                )

                stayMinutesMap[admCode] =
                    (stayMinutesMap[admCode] ?: 0L) +
                            stayMinutes
            }


            // ======================================================
            // 5. 방문 Rank
            // ======================================================

            val visitRankMap =
                mutableMapOf<String, Int>()

            visitCountMap
                .entries
                .sortedByDescending {
                    it.value
                }
                .forEachIndexed {
                        index,
                        entry ->

                    visitRankMap[entry.key] =
                        index + 1
                }


            // ======================================================
            // 6. 체류 Rank
            // ======================================================

            val stayRankMap =
                mutableMapOf<String, Int>()

            stayMinutesMap
                .entries
                .sortedByDescending {
                    it.value
                }
                .forEachIndexed {
                        index,
                        entry ->

                    stayRankMap[entry.key] =
                        index + 1
                }


            // ======================================================
            // 7. 행정동 코드
            // ======================================================

            val admCodes =
                (
                        visitCountMap.keys +
                                stayMinutesMap.keys +
                                lastVisitMap.keys
                        ).toSet()


            // ======================================================
            // 8. Sum 저장
            // ======================================================

            var insertCount = 0


            withContext(Dispatchers.IO) {

                for (admCode in admCodes) {

                    val visitCount =
                        visitCountMap[admCode] ?: 0

                    val stayMinutes =
                        stayMinutesMap[admCode] ?: 0L

                    val lastVisitTime =
                        lastVisitMap[admCode] ?: 0L

                    val visitRank =
                        visitRankMap[admCode] ?: 0

                    val stayRank =
                        stayRankMap[admCode] ?: 0

                    val admName =
                        admNameMap[admCode] ?: ""


                    val sumKey =
                        "$admCode$periodText"


                    val newSum =
                        Sum_db(

                            sumKey =
                                sumKey,

                            sumAdmCode =
                                admCode,

                            sumAdmName =
                                admName,

                            sumVisitRank =
                                visitRank,

                            sumVisitCount =
                                visitCount,

                            sumStayRank =
                                stayRank,

                            sumStayMinutes =
                                stayMinutes,

                            sumLastTime =
                                lastVisitTime,

                            sumPeriodDays =
                                periodDays
                        )


                    sumDao.insertSum(
                        newSum
                    )

                    insertCount++
                }
            }


            // ======================================================
            // 9. 관리행 생성 / 갱신
            //
            // 관리행의 sumLastTime은
            // "마지막 통계 처리 커서"
            // ======================================================

            val oldManagement =
                sumDao.sumKey로가져옴(
                    managementKey
                )


            val managementSum =
                Sum_db(

                    // 기존 행이 있다면
                    // sumId는 copy/update를 통해 Room이 관리한다.
                    //
                    // 신규 생성 시에는 0
                    sumKey =
                        managementKey,

                    sumAdmCode =
                        MANAGEMENT_CODE,

                    sumAdmName =
                        "관리행",

                    sumVisitRank =
                        0,

                    sumVisitCount =
                        0,

                    sumStayRank =
                        0,

                    sumStayMinutes =
                        0L,

                    sumLastTime =
                        processingStart,

                    sumPeriodDays =
                        periodDays
                )


            if (oldManagement != null) {

                val updatedManagement =
                    oldManagement.copy(

                        sumAdmCode =
                            MANAGEMENT_CODE,

                        sumAdmName =
                            "관리행",

                        sumVisitRank =
                            0,

                        sumVisitCount =
                            0,

                        sumStayRank =
                            0,

                        sumStayMinutes =
                            0L,

                        sumLastTime =
                            processingStart,

                        sumPeriodDays =
                            periodDays
                    )

                sumDao.updateSum(
                    updatedManagement
                )

            } else {

                sumDao.insertSum(
                    managementSum
                )
            }


            // ======================================================
            // 10. 결과 로그
            // ======================================================

            val finalCount =
                sumDao.getAllSum().size


            Log.e(
                "SumUtil",
                "📊 전체 계산 완료"
            )

            Log.e(
                "SumUtil",
                "📊 INSERT = $insertCount"
            )

            Log.e(
                "SumUtil",
                "📊 현재 전체 Sum DB = ${finalCount}건"
            )

            Log.e(
                "SumUtil",
                "📊 기간 = ${periodDays}일"
            )

            Log.e(
                "SumUtil",
                "📊 관리행 커서 = $processingStart"
            )

        }


        // ==========================================================
        // 행정동 하나의 Spot들을 집계
        //
        // 증분 계산에서 사용
        // ==========================================================

        private fun 계산행정동(
            spots: List<Spot>,
            now: Long
        ): 지역집계결과 {

            var visitCount =
                0

            var stayMinutes =
                0L

            var lastVisitTime =
                0L

            var admName =
                ""


            for (spot in spots) {

                val admCode =
                    spot.spAdmCode

                if (admCode.isBlank()) {
                    continue
                }


                if (admName.isBlank()) {
                    admName =
                        spot.spAdmName ?: ""
                }


                // 방문 횟수

                visitCount++


                // 마지막 방문시간

                if (
                    spot.spStartTime >
                    lastVisitTime
                ) {

                    lastVisitTime =
                        spot.spStartTime
                }


                // 체류시간
                // 체류시간

                val stayMillis =
                    spot.spSpotTime

                val safeStayMillis =
                    if (stayMillis > 0L) {
                        stayMillis
                    } else {
                        0L
                    }

                stayMinutes +=
                    safeStayMillis /
                            (60L * 1000L)

               /* val endTime =
                    spot.spEndTime
                        ?: now

                val stayMillis =
                    endTime -
                            spot.spStartTime

                val safeStayMillis =
                    if (stayMillis > 0L) {
                        stayMillis
                    } else {
                        0L
                    }

                stayMinutes +=
                    safeStayMillis /
                            (60L * 1000L)*/
            }


            return 지역집계결과(

                admName =
                    admName,

                visitCount =
                    visitCount,

                stayMinutes =
                    stayMinutes,

                lastVisitTime =
                    lastVisitTime
            )
        }


        // ==========================================================
        // 행정동 집계 결과
        // ==========================================================

        private data class 지역집계결과(

            val admName: String,

            val visitCount: Int,

            val stayMinutes: Long,

            val lastVisitTime: Long
        )
    }
}

