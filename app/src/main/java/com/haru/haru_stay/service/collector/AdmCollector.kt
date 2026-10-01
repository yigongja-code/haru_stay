package com.haru.haru_stay.service.collector

import android.content.Context
import android.util.Log
import com.haru.haru_stay.data.dao.BusStopDao
import com.haru.haru_stay.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AdmResult(
    val admCode: String,     // 10자리 행정동 코드
    val admName: String,   // 시·군·구 + 동 주소 (예: 종로구 사직동)
    val stopName: String,    // 가장 가까운 버스정류장 이름
    val city: String,        // 폐지
    val adm: String,         // 폐지
    val centerLat: Double,
    val centerLon: Double,
    val busDistance: Long // 👈 정류장과의 거리 추가!
)

class AdmCollector(private val context: Context) {


    companion object {
        private const val TAG = "AdmCollector"
    }
    // 💡 클래스가 생성될 때 가장 먼저 진입하는지 확인용
    init {
        Log.d(TAG, "🚀 AdmCollector 클래스 초기화 진입 완료")
    }

    private val busStopDao: BusStopDao by lazy {
        AppDatabase.getDatabase(context).busStopDao()
    }

    // 💡 20MB CSV 파싱 로직, 한 줄씩 읽는 반복문, insertAll 호출 따위는
    // 앱을 즉사시키는 간첩이므로 코드 상에서 완전히 흔적도 없이 지워버려야 합니다!

    suspend fun findCurrentLocationAdm(currentLat: Double, currentLon: Double): AdmResult? {
        return withContext(Dispatchers.IO) {
            try {
                //먼저 favDB의 즐겨찾기 데이터인지 확인
                checkFavPriority(currentLat, currentLon)?.let { matchedFavResult ->
                    Log.d(TAG, "🎯 [Fav 우선권 발동] 버정 검색 스킵 완료: ${matchedFavResult.admName}")
                    return@withContext matchedFavResult
                }

                val nearest = busStopDao.findNearestBusStop(currentLat, currentLon) ?: return@withContext null

                // 💡 1. 현재 위치와 찾아낸 정류장 간의 실제 거리 계산 (단위: 미터)
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    currentLat, currentLon,
                    nearest.lat, nearest.lon,
                    results
                )
                val distanceInMeters = results[0] // 두 지점 사이의 거리 (미터)


                // 💡 2. 거리가 1km(1500m)를 넘어가면 정류장 이름은 "알 수 없음"으로 처리
                val finalStopName = if (distanceInMeters <= 1500f) {
                    nearest.stopName
                } else {
                    Log.w(TAG, "⚠️ 가장 가까운 정류장이 너무 멀리 있음 (${distanceInMeters}m). 정류장 이름 마스킹")
                    "알 수 없음" // 👈 널이 아니라 "알 수 없음" 문자열 대입!
                }

                // 💡 쪼개고 자시고 할 것 없이 DB에 이미 조합되어 들어간 값을 그대로 통째로 패스!
                AdmResult(
                    admCode = nearest.admCode,
                    admName = nearest.admName, // 👈 DB에 저장된  형태 그대로 전달
                    stopName = finalStopName,    // 👈 거리에 따라 정류장 이름 또는 "알 수 없음" 입력
                    city = "", // 불필요하므로 비움
                    adm = "",  // 불필요하므로 비움
                    centerLat = nearest.lat,
                    centerLon = nearest.lon,
                    busDistance = distanceInMeters.toLong() // 👈 미터 단위 Long형으로 전달
                )
            } catch (e: Exception) {
                Log.e(TAG, "logAdmCode_Crash: findCurrentLocationAdm failed - ${e.message}", e)
                null
            }
        }
    }
    private suspend fun checkFavPriority(currentLat: Double, currentLon: Double): AdmResult? {
        return try {
            val db = AppDatabase.getDatabase(context)

            // 1. 가장 가까운 거점 가져오기 (없으면 끝)
            val nearestFav = db.favoriteDao().findNearestFav(currentLat, currentLon) ?: return null

            // 💡 2. 지오펜스 값이 없거나 0 이하면, 확장성이고 뭐고 그냥 곧바로 리턴(거점 무시하고 버정 로직으로 토스)
            if (nearestFav.favGeoFnc <= 0L) return null

            // 3. 유효한 지오펜스 값이 있을 때만 거리 계산 진입
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                currentLat, currentLon,
                nearestFav.favGpsLat, nearestFav.favGpsLon,
                results
            )
            val distance = results[0]
            val radius = nearestFav.favGeoFnc.toFloat()

            if (distance <= radius) {
                Log.d(TAG, "✨ 거점 매칭 성공! (${nearestFav.favAdmName}) - 거리: ${distance}m (기준: ${radius}m)")
                return AdmResult(
                    admCode = nearestFav.favAdmCode,
                    admName = nearestFav.favAdmName,
                    stopName = "⭐ ${nearestFav.favBusStop.ifEmpty { "내 거점" }}",
                    city = "",
                    adm = "",
                    centerLat = nearestFav.favGpsLat,
                    centerLon = nearestFav.favGpsLon,
                    busDistance = distance.toLong()
                )
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "checkFavPriority failed - ${e.message}", e)
            null
        }
    }
}
