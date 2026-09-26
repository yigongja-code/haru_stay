package com.example.haru_spot.service.collector

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.round
import kotlinx.coroutines.withTimeoutOrNull


class LocationCollector(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // 소수점 4자리(~11m 단위)로 좌표를 자르는 헬퍼 함수
    private fun truncateTo4Decimals(value: Double): Double {
        return round(value * 10000.0) / 10000.0
    }

    // 💡 0.0 유령 좌표 및 오차 범위(300m 초과)를 걸러내는 공통 방어 함수
    private fun isLocationValid(location: Location): Boolean {
        // 1. 위경도가 둘 다 0.0이면 꺼진 유령 좌표
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            Log.w("LocationCollector", "⚠️ 위치가 0.0으로 꺼져 있음!")
            return false
        }
        // 2. 오차 범위(Accuracy)가 300미터를 넘어가면 튀는 불량 좌표
        if (location.hasAccuracy() && location.accuracy > 300f) {
            Log.w("LocationCollector", "⚠️ 위치 오차 범위가 너무 넓음(${location.accuracy}m). 무시!")
            return false
        }
        return true
    }

    private fun formatLocation(location: Location): Location {
        return location.apply {
            latitude = truncateTo4Decimals(latitude)
            longitude = truncateTo4Decimals(longitude)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchBestLocation(): Location? {
        // 1단계: 'Last Known Location' 확인 (1분 이내 최신 값이면 공짜 수집)
        val lastLocation = getLatestKnownLocation()
        if (lastLocation != null) {
            val ageMillis = System.currentTimeMillis() - lastLocation.time
            // 💡 1분 이내이면서 유효한 좌표일 때만 통과
            if (ageMillis <= 60_000L && isLocationValid(lastLocation)) {
                Log.d("LocationCollector", "⭕ 1분 이내 최신 캐시 위치 수집 완료 (배터리 0%)")
                return formatLocation(lastLocation)
            } else {
                Log.d("LocationCollector", "⚠️ 캐시 좌표가 낡았거나 불량품(0점/오차 초과)! 2단계로 전환")
            }
        }

        // 2단계: 네트워크 정확도로 강제 갱신 시도
        Log.d("LocationCollector", "❌ 네트워크 정확도로 강제 갱신 시도")
        val networkLocation = requestCurrentNetworkLocation()
        if (networkLocation != null && isLocationValid(networkLocation)) {
            return formatLocation(networkLocation)
        }

        // 3단계: ❌ 네트워크마저 튕겼거나 불량이라면? 시골 방어선 끝판왕! GPS 센서 강제 가동!
        Log.d("LocationCollector", "🚨 네트워크마저 실패! 초정밀 GPS 센서(HIGH_ACCURACY) 강제 가동")
        return requestHighAccuracyGpsLocation()
    }

    @SuppressLint("MissingPermission")
    private fun getLatestKnownLocation(): Location? {
        val networkLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val passiveLoc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

        return listOfNotNull(networkLoc, passiveLoc).maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentNetworkLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            val tokenSource = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                tokenSource.token
            ).addOnSuccessListener { location ->
                if (location != null && isLocationValid(location)) {
                    Log.d("LocationCollector", "🎯 네트워크 정확도로 최신 좌표 갱신 성공")
                    continuation.resume(location)
                } else {
                    Log.d("LocationCollector", "⚠️ 네트워크 위치 결과물이 null이거나 오차 범위/0점 불량품입니다.")
                    continuation.resume(null)
                }
            }.addOnFailureListener { e ->
                Log.e("LocationCollector", "네트워크 위치 갱신 실패: ${e.toString()}")
                continuation.resume(null)
            }
        }

    // 💡 3단계: 시골 및 음영 지역 방어용 초정밀 GPS 강제 가동 함수
    // 💡 3단계: 시골 및 음영 지역 방어용 초정밀 GPS 강제 가동 함수 (30초 타임아웃 적용)
    @SuppressLint("MissingPermission")
    private suspend fun requestHighAccuracyGpsLocation(): Location? =
        withTimeoutOrNull(30_000L) { // ⏱️ 딱 30초만 기다리고 안 되면 포기!
            suspendCancellableCoroutine { continuation ->
                val tokenSource = CancellationTokenSource()

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY, // 위성 GPS 직접 갈구기!
                    tokenSource.token
                ).addOnSuccessListener { location ->
                    if (location != null && isLocationValid(location)) {
                        Log.d("LocationCollector", "🛰️ 초정밀 GPS 위성 좌표 획득 성공!")
                        continuation.resume(location)
                    } else {
                        Log.d("LocationCollector", "❌ 초정밀 GPS마저 유효한 좌표를 얻지 못했습니다 (null 또는 불량).")
                        continuation.resume(null)
                    }
                }.addOnFailureListener { e ->
                    Log.e("LocationCollector", "초정밀 GPS 갱신 실패: ${e.toString()}")
                    continuation.resume(null)
                }
            }
        } ?: run {
            // 💡 30초 동안 응답이 없어서 타임아웃에 걸렸을 때의 처리
            Log.w("LocationCollector", "⏰ 초정밀 GPS 가동 시간(30초) 초과로 강제 포기합니다.")
            null
        }
}