package com.example.haru_spot.service.유틸

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object GpsMapUtil {
    fun openKakaoMap(context: Context, latitude: Double, longitude: Double, placeName: String) {
        if (latitude == 0.0 && longitude == 0.0) {
            Toast.makeText(context, "유효한 좌표 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 장소 이름이 비어있으면 기본값 세팅 후 카카오맵 URL 생성
        val encodedName = if (placeName.isBlank()) "수집 위치" else placeName
        val mapUrl = "https://map.kakao.com/link/map/$encodedName,$latitude,$longitude"

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl))

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "브라우저를 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}