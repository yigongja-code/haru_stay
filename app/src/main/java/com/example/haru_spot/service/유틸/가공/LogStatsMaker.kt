package com.example.haru_spot.service.유틸.가공

import android.location.Location
import android.util.Log
import com.example.haru_spot.data.dao.LogDao
import com.example.haru_spot.data.dao.SpotDao
import com.example.haru_spot.data.entity.VisitLog

/**
 * 1차 가공 엔진: LogStatsMaker
 * - 무결성 검증(필수값 삭제) 및 시간/거리 기반 단절(Disconnected) 방어선 포함
 */
class LogStatsMaker(
    private val logDao: LogDao,
    private val spotDao: SpotDao
) {

    companion object {
        private const val TAG = "LogStatsMaker"

        // =================================================
        //       폰이 꺼지거나 앱이 단절된 상황 조건
        // ==============================================
        // 💡 1시간 (밀리초) 이상 공백 발생 시 단절 검사 시작
        private const val MAX_ALLOWABLE_GAP = 120 * 60 * 1000L

        // 💡 도즈 모드(감옥)와 진짜 이동(사고/단절)을 가르는 물리적 거리 임계값 (예: 2000미터)
        private const val MAX_ALLOWABLE_DISTANCE_METERS = 2000.0f
    }

    // 1차 가공 실행 함수
    // 1차 가공 실행 함수
    // 1차 가공 실행 함수
    suspend fun processPendingLogs() {
        // 💡 변경: 통합 쿼리로 미처리 로그를 불러와 stage1Logs에 담기
        //1~2차 가공을 마친 데이터들은 logProcessedAt = 현재시간
        // 그렇기에 logProcessedAt = 0 인것만 불러오고
        //수정이 필요한 데이터기에 toMutableList() 붙임
        val stage1Logs = logDao.getUnprocessedLogs().toMutableList()

        //가공할 값이 없으면 함수 종료
        if (stage1Logs.size <= 1) return

        // 🌉 [추가된 브릿지] 직전 마지막 로그(Tail) 끌어오기
        // 마지막에 프로세스 상태값을 0을 주기때문에 불필요
        // val lastProcessedLog = logDao.getLastProcessedLog()

        //반복문을 돌면서 데이터를 삭제하면 원보의 갯수가 줄어들기때문에
        //listIterator()를 사용해서 가이드 해줌
        val iterator = stage1Logs.listIterator()

        // 중간에 데이터가 삭제될수 있기 때문에 while사용
        // db를 수정하는게 적은 코드
        while (iterator.hasNext()) {
            val i = iterator.nextIndex()
            val current = iterator.next()

            // =================================================================
            // 🛡️ [1단계 방어선] 필수 값 누락 무결성 검증 (모가지 댕겅 🪓)
            // =================================================================
            if (current.logStartTime <= 0L ||
                current.logCellKey.isNullOrEmpty() ||
                current.logGpsLat == 0.0 ||
                current.logGpsLon == 0.0
            ) {
                Log.e(
                    TAG, "❌ [모가지 댕겅 처형] 무결성 위반으로 삭제! " +
                            "(ID: ${current.logId}, StartTime: ${current.logStartTime})"
                )
                //조건에 걸린놈 삭제
                logDao.deleteLogById(current.logId)

                //반복문에서도 다시 반복하지 않게 삭제
                iterator.remove()
                continue
            }

            // =================================================================
            // 🛡️ [2단계 방어선] 종료 시간 메우기 및 시간/거리 복합 단절 판정
            // 종료시간은 3단계에서 업뎃함
            // =================================================================

            //끝데이터가 아닐때 종료시간을 입력.. 끝이면 더이상 불러올 데이터가 없으니깐.
            //if (i < stage1Logs.size - 1) {
                //val next = stage1Logs[i + 1]

                //var updated = current


                //예외적상황 시작과 종료시간이 너무 길때
                //기타등등 앱이 기능을 못해서 수집이 중지된 상태 거르기.

                //시작과 종료 시간을 계산
                //val timeGap = next.logStartTime - current.logStartTime


                //임시 주석처리 단절 ㄴㄴ
                /*if (timeGap >= MAX_ALLOWABLE_GAP) {
                    //거리계산 미래의 데이터
                    val distance = calculateDistance(
                        current.logGpsLat, current.logGpsLon,
                        next.logGpsLat, next.logGpsLon
                    )
                    //미래의 데이터가 현 데이터의 변수로 지정한 한도가 넘으면 조건진입
                    //즉 다음 수집와 거리가 까까우면 단절된 신호라도 유효한 신호임
                    //휴대폰이 사고나 배터리 부족으로 앱이 종료되었을때 판정임..
                    //수집이 정지되었다가 재개될때 정지되기전 위치와 재개위치가 1키로 이내이면
                    //체류해 있었다로 판정,
                    //휴대폰이 딥도즈 상황이나 초절전 모드등의 상황
                    if (distance >= MAX_ALLOWABLE_DISTANCE_METERS) {
                         updated = updated.copy(
                            logEndTime = current.logStartTime + MAX_ALLOWABLE_GAP,
                            //logStats = "disconnected",
                            logBusStop = "[⚠️disconnect] ${current.logBusStop}".trim()
                        )
                        logDao.updateLog(updated)
                    }
                }*/

                //logDao.updateLog(updated)
              //  stage1Logs[i] = updated

                //
            //}
        }

        // =================================================================
        // 🔗 [3단계] merge / hold / ignored 상태값 마킹 루프 수행
        // =================================================================
        var prevLog: VisitLog? = null
        val ignoredBucket = mutableListOf<VisitLog>()



        for (log in stage1Logs) {

            if (prevLog == null) {
                prevLog = log
                continue
            }
            val isSameLocation = log.logAdmCode == prevLog.logAdmCode

            if (isSameLocation) {

                val updatedPrev = prevLog.copy(
                    logStats = "merge",
                    logEndTime = log.logStartTime
                )

                val updatedCurrent = log.copy(
                    logStats = "merge"
                )

                logDao.updateLog(updatedPrev)
                logDao.updateLog(updatedCurrent)

                Log.d(
                    "LogToSpotMaker1",
                    "같은 법정동 merge 확정: " +
                            "prev=${prevLog.logId}, current=${log.logId}, " +
                            "ignoredBucket=${ignoredBucket.size}"
                )

                // 앞뒤 merge가 확정된 순간 ignored 로그들을 일괄 퇴출
                if (ignoredBucket.isNotEmpty()) {
                    val processedAt = System.currentTimeMillis()

                    ignoredBucket.forEach { ignoredLog ->
                        logDao.updateLog(
                            ignoredLog.copy(
                                logProcessedAt = processedAt
                            )
                        )
                        Log.d(
                            "LogToSpotMaker1",
                            "ignored 마킹: " +
                                    "logId=${ignoredLog.logId}, " +
                                    "processedAt=$processedAt"
                        )
                    }
                    ignoredBucket.clear()
                }

                prevLog = updatedCurrent

            } else {

                when (prevLog.logStats) {

                    "merge" -> {
                        // 다른 코드가 나왔으므로 현재값은 보류
                        val updatedPrev = prevLog.copy(
                            logEndTime = log.logStartTime
                        )

                        val updatedCurrent = log.copy(
                            logStats = "hold"
                        )

                        logDao.updateLog(updatedPrev)
                        logDao.updateLog(updatedCurrent)

                        prevLog = updatedCurrent
                    }

                    "hold" -> {
                        // 이전 hold는 앞뒤 연결이 확인되지 않았으므로 ignored
                        val ignoredPrev = prevLog.copy(
                            logStats = "ignored",
                            logEndTime = log.logStartTime
                        )

                        val updatedCurrent = log.copy(
                            logStats = "hold"
                        )

                        logDao.updateLog(ignoredPrev)
                        logDao.updateLog(updatedCurrent)

                        ignoredBucket.add(ignoredPrev)

                        prevLog = updatedCurrent
                    }

                    "ignored"  -> {
                        // ignored는 연결자 후보로 계속 유지
                        val updatedPrev = prevLog.copy(
                            logEndTime = log.logStartTime
                        )

                        val updatedCurrent = log.copy(
                            logStats = "hold"
                        )



                        logDao.updateLog(updatedPrev)
                        logDao.updateLog(updatedCurrent)

                        ignoredBucket.add(updatedPrev)
                        Log.d(
                            "LogToSpotMaker1",
                            "ignored bucket 추가: " +
                                    "logId=${prevLog.logId}, " +
                                    "bucketSize=${ignoredBucket.size}"
                        )

                        prevLog = updatedCurrent
                    }
                    "pending"-> {  //앱 첫 데이터

                        val updatedPrev = prevLog.copy(
                            logEndTime = log.logStartTime,logStats = "ignored"
                        )

                        val updatedCurrent = log.copy(
                            logStats = "hold"
                        )

                        ignoredBucket.add(updatedPrev)

                        logDao.updateLog(updatedPrev)
                        logDao.updateLog(updatedCurrent)

                        prevLog = updatedCurrent

                    }

                    else -> {
                        Log.e(
                            TAG, "예상하지 못한 이전 상태: ${prevLog.logStats}, " +
                                    "logId=${prevLog.logId}"
                        )
                    }
                }
            }


            //워크매니저가 사고로 신호를 못줄때, 폰꺼짐, 고장, 앱 처형등등..
            /*val timeGap = log.logStartTime - prevLog.logStartTime
            if (timeGap >= MAX_ALLOWABLE_GAP) {
                //거리계산 미래의 데이터
                val distance = calculateDistance(
                    prevLog.logGpsLat, prevLog.logGpsLon,
                    log.logGpsLat, log.logGpsLon
                )
                if (distance >= MAX_ALLOWABLE_DISTANCE_METERS) {
                    updated = updated.copy(
                        logEndTime = current.logStartTime + MAX_ALLOWABLE_GAP,
                        logStats = "disconnected",
                        logBusStop = "[⚠️disconnect] ${current.logBusStop}".trim()
                    )
                }
            }*/
        }

        Log.d(TAG, "🎉 1차 가공 완료! (stage1Logs 처리 완료)")
    }

    /**
     * 두 GPS 좌표 간의 물리적 거리(미터)를 계산하는 유틸 메서드
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
}