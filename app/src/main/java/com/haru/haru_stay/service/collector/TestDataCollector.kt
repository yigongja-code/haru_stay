package com.haru.haru_stay.service.collector

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import com.haru.haru_stay.data.database.AppDatabase
import com.haru.haru_stay.data.entity.VisitLog

object TestDataCollector {

    private const val TAG = "TestDataCollector"

    /**
     * 🚀 10일치 가상 데이터 수집 함수 (15분 고정 간격, 0~5km 랜덤 이동 및 체류 반영 구조)
     */
    suspend fun generatePastThreeDaysTestData(context: Context): Int = withContext(Dispatchers.IO) {
        val admCollector = AdmCollector(context)
        val logDao = AppDatabase.getDatabase(context).logDao()

        Log.d(TAG, "🚀 [TestDataCollector] 가상 데이터 생성 시작...")

        // 1. 기존 데이터 삭제
        try {
            logDao.deleteAllLogs()
            Log.d(TAG, "🧹 기존 log_db 데이터 전체 삭제 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 기존 데이터 삭제 실패: ${e.message}", e)
            return@withContext 0
        }

        // 2. 좌표 설정 - 국회의사당
        val baseLat = 37.5311
        val baseLon = 126.9148
        val random = Random()

        // 3. 시작 시간 설정 (예: 5일 전)
        val startCalendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -30)  // 뒤에 숫자가 데이터 생성일
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var a = startCalendar.timeInMillis

        // 💡 4. 종료 시간을 현재 시간에서 하루 전(-1일)으로 안전하게 격리 (실제 수집기 충돌 방지)
        val endCalendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1) //몇일전에 데이터를 마감할건지 0=현재
        }
        val targetEndTimeMillis = endCalendar.timeInMillis

        var successCount = 0
        var totalLoops = 0
        val maxLoopLimit = 15000 //총수집할 에이터(무한루프방지)
                                //1달2880 3달8640 6달17280 12달 35040

        var currentLat = baseLat
        var currentLon = baseLon
        var moveCounter = 0 // 💡 300번마다 초기화하기 위한 카운터 변수 추가 대략 3일3시간

        while (a < targetEndTimeMillis && totalLoops < maxLoopLimit) {
            totalLoops++
            moveCounter++

            // 💡 500번 이동할 때마다  중심 좌표로 강제 귀환!
            if (moveCounter >= 500) {
                currentLat = baseLat
                currentLon = baseLon
                moveCounter = 0 // 카운터 리셋
            }

            // 💡 1. 무조건 15분 간격 고정
            val startTimeMillis = a
            a += (15 * 60 * 1007L)
            val endTimeMillis = a

            // 💡 2. % 확률로 1m~100m (동네 밀착 체류), 20% 확률로 2.5km~3.5km (다른 동네로 이동)
            val distanceKm = if (random.nextDouble() < 0.9) {       //0.9 = 90% 확률
                0.050 + (random.nextDouble() * 0.099) // 0.001km(1m) ~ 0.1km(100m)
            } else {
                1.5 + (random.nextDouble() * 3.5)     // 1.5km ~ 5.0km 사이의 랜덤 3km 이동
            }

            val angle = random.nextDouble() * 2 * Math.PI // 0~360도 랜덤 방향
            val latOffset = (distanceKm * cos(angle)) / 111.0
            val lonOffset = (distanceKm * sin(angle)) / (111.0 * cos(Math.toRadians(currentLat)))

            currentLat += latOffset
            currentLon += lonOffset

            val formattedLat = String.format(Locale.getDefault(), "%.4f", currentLat).toDouble()
            val formattedLon = String.format(Locale.getDefault(), "%.4f", currentLon).toDouble()

            // 3. 행정구역 매칭 확인
            val admResult = admCollector.findCurrentLocationAdm(formattedLat, formattedLon)
            if (admResult == null) {
                continue
            }

            val admCode = admResult.admCode ?: "32020360"
            val cellKeyPrefix = if (admCode.length >= 7) admCode.substring(0, 7) else admCode
            val stopName = admResult.stopName ?: "인근 정류장 없음"

            // 4. DB 저장
            val visitLog = VisitLog(
                logAdmCode = admCode,
                logAdmName = admResult.admName ?: "알 수 없음",
                logBusStop = stopName,
                logCellKey = "LTE_$cellKeyPrefix",
                logWifiMac = "bb:aa:32:02:03:06(WIFI_$stopName)",
                logGpsLat = formattedLat,
                logGpsLon = formattedLon,
                logGpsRange = 0.0,
                logStats = "pending",
                logStartTime = startTimeMillis,
                logEndTime = endTimeMillis,
                logProcessedAt = 0L,
                logMemoId = 0L,
                logBusDistance = admResult.busDistance,
                logField2 = "cmppending",
                logField3 = "",
                logField4 = ""

            )


            try {
                logDao.insert(visitLog)
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "❌ DB Insert 실패: ${e.message}", e)
            }
        }

        Log.d(TAG, "🎉 가상 데이터 ${successCount}건 생성 완료!")
        // 💡 가상 데이터 생성이 끝나는 바로 이 지점에 방아쇠를 당겨서 자동 가공 시작!
        /*try {
            Log.d(TAG, "🚀 [자동 방아쇠] 가상 데이터 생성 완료 직후 1·2차 가공 파이프라인 자동 실행!")
            // DB 인스턴스를 가져와서 방아쇠를 당깁니다.
            val database = AppDatabase.getDatabase(context)
            com.example.haru_spot.service.engine.TriggerLogToSpot.runIfNeeded(database) {
                Log.d(TAG, "✨ [자동 방아쇠 완료] 가상 데이터 생성부터 스팟 가공까지 모든 프로세스 종료!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 자동 가공 방아쇠 당기기 실패: ${e.message}", e)
        }*/


        return@withContext successCount
    }
}