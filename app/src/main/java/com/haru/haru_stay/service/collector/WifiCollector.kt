package com.haru.haru_stay.service.collector

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log

class WifiCollector(private val context: Context) {

    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @SuppressLint("MissingPermission")
    fun fetchConnectedWifi(): String? {
        try {
            // 안드로이드 10 이상에서는 연결된 네트워크의 와이파이 정보를 가져오기 위해 ConnectivityManager를 거치는 것이 안전합니다.
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiInfo = wifiManager.connectionInfo
                if (wifiInfo != null) {
                    val ssid = wifiInfo.ssid?.removeSurrounding("\"", "") // 앞뒤 쌍따옴표 제거
                    val bssid = wifiInfo.bssid // MAC 주소 역할 (접속한 공유기의 고유 BSSID)

                    // 유효한 와이파이 이름일 때만 반환 (연결 안 되어 있으면 "<unknown ssid>" 등이 나옴)
                    if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>") {
                        Log.d("WifiCollector", "🛜 현재 연결된 와이파이 포착 - SSID: $ssid, BSSID: $bssid")
                        return "$bssid ($ssid)"
                    }
                }
            } else {
                Log.d("WifiCollector", "⚠️ 현재 와이파이에 연결되어 있지 않습니다.")
            }
        } catch (e: Exception) {
            Log.e("WifiCollector", "❌ 와이파이 정보 수집 중 에러 발생: ${e.message}")
        }

        return null
    }
}