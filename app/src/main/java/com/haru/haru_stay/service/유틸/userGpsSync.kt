package com.haru.haru_stay.service.유틸

import android.content.Context
import android.util.Log
import com.haru.haru_stay.data.database.AppDatabase
import com.haru.haru_stay.data.entity.VisitLog
import com.haru.haru_stay.service.collector.AdmCollector
import com.haru.haru_stay.service.collector.AdmResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

object userGpsSync {

    private const val TAG = "userGpsSync"

    /**
     * 전달받은 좌표 주변의 기존 logDB를 다시 계산한다.
     *
     * 처리 흐름:
     * 1. 전달받은 좌표 주변 약 2km 범위의 기존 로그 조회
     * 2. 각 로그의 GPS 좌표를 findCurrentLocationAdm()에 전달
     * 3. busDB에서 가장 가까운 버스정류장 정보 반환
     * 4. 기존 Log 객체를 copy()하여 갱신 리스트에 추가
     * 5. 모든 계산이 끝난 뒤 한 번에 logDB 저장
     *
     * 유저 스팟 정보는 사용하지 않는다.
     * 유저 스팟이 이미 삭제된 상태에서도 정상적으로 재계산해야 한다.
     */
    suspend fun refineWithUserSpots(
        context: Context,
        targetLat: Double,
        targetLon: Double
    ) {
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)

            Log.d(
                TAG,
                "💡 userGpsSync 진입 - targetLat=$targetLat, targetLon=$targetLon"
            )

            // findCurrentLocationAdm()에서 사용하는 busStopDao와
            // 동일한 DB를 사용해야 한다.
            //
            // 유저 스팟 목록은 조회하지 않는다.
            // 삭제된 유저 스팟이 없어도 재계산해야 하기 때문이다.

            // ±0.02도 범위
            // 위도 기준 약 2.2km 정도이며, 실제로는 사각형 검색 범위다.
            val delta = 0.02

            val minLat = targetLat - delta
            val maxLat = targetLat + delta
            val minLon = targetLon - delta
            val maxLon = targetLon + delta

            // 기존 logDB에서 대상 로그 조회
            val targetLogs = db.logDao().getLogsInBoundingBox(
                minLat = minLat,
                maxLat = maxLat,
                minLon = minLon,
                maxLon = maxLon
            )

            if (targetLogs.isEmpty()) {
                Log.d(TAG, "💡 주변에 재계산할 로그가 없습니다.")
                return@withContext
            }

            Log.d(
                TAG,
                "💡 재계산 대상 로그 수: ${targetLogs.size}"
            )

            // 계산 결과를 임시 리스트에 모은다.
            // 계산 중에는 DB를 갱신하지 않는다.
            val updatedLogs = mutableListOf<VisitLog>()
            val admCollector = AdmCollector(context)

            for (log in targetLogs) {
                try {
                    // 기존 로그의 GPS 좌표를 기준으로 busDB 검색
                    val admResult = admCollector.findCurrentLocationAdm(
                        log.logGpsLat,
                        log.logGpsLon
                    ) ?: continue

                    if (admResult == null) {
                        Log.w(
                            TAG,
                            "⚠️ 버스정류장을 찾지 못해 건너뜀: logId=${log.logId}"
                        )
                        continue
                    }

                    // AdmResult는 새 로그를 생성하지 않고 값만 반환한다.
                    // 여기서는 기존 logDB 행을 copy()하여 갱신한다.
                    val updatedLog = log.copy(
                        logBusStop = admResult.stopName,
                        logAdmCode = admResult.admCode,
                        logAdmName = admResult.admName,
                        logBusDistance = admResult.busDistance
                    )

                    updatedLogs.add(updatedLog)

                    Log.d(
                        TAG,
                        "✨ 재계산 완료: " +
                                "logId=${log.logId}, " +
                                "정류장=${admResult.stopName}, " +
                                "행정동=${admResult.admName}, " +
                                "거리=${admResult.busDistance}m"
                    )

                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "❌ 로그 재계산 실패: logId=${log.logId}",
                        e
                    )
                }
            }

            if (updatedLogs.isEmpty()) {
                Log.d(TAG, "💡 갱신할 로그가 없습니다.")
                return@withContext
            }

            // 모든 계산이 끝난 뒤 한 번에 저장
            db.logDao().updateLogs(updatedLogs)

            Log.d(
                TAG,
                "✨ userGpsSync 재계산 및 일괄 저장 완료: ${updatedLogs.size}개"
            )
        }
    }

    /**
     * busDB의 가장 가까운 버스정류장을 검색하는 함수.
     *
     * 이 함수는 새 logDB를 생성하지 않고 AdmResult 값만 반환해야 한다.
     *
     * findCurrentLocationAdm()가 다른 유틸 객체에 있다면
     * 아래 호출부에 해당 객체명을 붙여야 한다.
     */
    private suspend fun findCurrentLocationAdm(
        context: Context,
        currentLat: Double,
        currentLon: Double
    ): AdmResult? {
        val collector = AdmCollector(context)

        return collector.findCurrentLocationAdm(
            currentLat,
            currentLon
        )
    }

    /**
     * 하버사인 공식.
     * 두 GPS 좌표 사이의 실제 거리를 미터 단위로 반환한다.
     */
    fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6371e3

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a =
            sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) *
                    cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)

        val c = 2 * atan2(
            sqrt(a),
            sqrt(1 - a)
        )

        return earthRadius * c
    }
}