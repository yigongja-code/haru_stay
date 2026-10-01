package com.haru.haru_stay.service.유틸

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.haru.haru_stay.R
import com.haru.haru_stay.data.database.AppDatabase
import com.haru.haru_stay.service.collector.AdmResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object 알림유틸 {

    private const val CHANNEL_ID = "HA_RU SPOT v1"
    private const val NOTIFICATION_ID = 1
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    //위치수집 알림 - 기준 m - 500m = 500L
    private const val NEW_LOCATION_DISTANCE_METERS = 500L

    /**
     * admResult를 받아 정류장과 행정동 조합 문구를 직접 조립하고
     * 과거 Spot 데이터를 조회하여 알림을 갱신합니다.
     */
    fun updateWithAdmResult(context: Context, admResult: AdmResult?) {
        val admName = admResult?.admName?.ifBlank { "" } ?: ""
        val busStop = admResult?.stopName?.ifBlank { "" } ?: ""
        val admCode = admResult?.admCode?.ifBlank { "" } ?: ""
        val distance = admResult?.busDistance ?: 0L


        val messageTitle = when {
            busStop.isNotBlank() && admName.isNotBlank() -> "$busStop 인근 / $admName"
            busStop.isNotBlank() -> "$busStop 인근"
            admName.isNotBlank() -> admName
            else -> "위치 수집 완료 (정보 없음)"
        }

        notificationScope.launch {
            try {
                if (distance > NEW_LOCATION_DISTANCE_METERS) {
                    showNewLocationNotification(context, messageTitle) // 👈 messageTitle 전달!

                } else {
                    showNormalLocationNotification(
                        context,
                        admCode,
                        messageTitle
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showNotification(
                    context = context,
                    messageTitle = messageTitle,
                    messageText = "위치 수집 완료"
                )
            }
        }
    }

    private fun showNotification(
        context: Context,
        messageTitle: String,
        messageText: String,
        action: String = ""
    )  {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "하루 위치 수집 서비스",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "백그라운드에서 위치 정보를 수집하는 서비스입니다."
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP

                if (action.isNotBlank()) {
                    this.action = action
                }

                Log.e(
                    "NewLocation",
                    "알림 Intent action = ${this.action}"
                )
            }
            ?: Intent()
        Log.e(
            "NewLocation",
            "PendingIntent 직전 action = ${intent.action}"
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(messageTitle)
            .setContentText(messageText)
            .setSmallIcon(R.drawable.outline_pin_history_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("M월d일", Locale.KOREAN)
        return sdf.format(Date(timestamp))
    }

    fun 오늘00시(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    // 💡 190일(반년) 전 자정 타임스탬프 계산 함수 추가
    fun getHalfYearAgoTimestamp(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -190)
        }.timeInMillis
    }

    //알람에 위치등록 관련
    //알람에 위치등록 관련
    private fun showNewLocationNotification(context: Context, messageTitle: String) {
        val line1 = "⚠\uFE0F새위치 등록필요[알림터치] - 현위치 신뢰도 낮음"
        val line2 = "참조 좌표와 너무 멀리 떨어져 있습니다."

        val messageText = "$line1\n$line2"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "하루 위치 수집 서비스",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "백그라운드에서 위치 정보를 수집하는 서비스입니다."
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                action = "REGISTER_NEW_LOCATION"
            }
            ?: Intent()

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 💡 [핵심] 긴 텍스트와 2줄 표현을 완벽하게 보장하는 BigTextStyle 적용!
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(messageText)
            .setSummaryText("신뢰도 낮음") // 선택사항: 상단에 작게 뜨는 부가 설명

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(messageTitle)
            .setContentText(messageText) // 기본 텍스트
            .setStyle(bigTextStyle)     // 👈 여기에 스타일을 꽂아주면 생략 기호(...) 없이 두 줄로 완벽 출력!
            .setSmallIcon(R.drawable.outline_pin_history_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    //기본 알람
    private suspend fun showNormalLocationNotification(
        context: Context,
        admCode: String,
        messageTitle: String
    ) {
        val database = AppDatabase.Companion.getDatabase(context)
        val spotDao = database.spotDao()

        val todayStartTimestamp = 오늘00시()
        val halfYearAgo = getHalfYearAgoTimestamp()



        // 💡 [단순화 포인트] 체류 시간 계산용 쿼리 대신, '오늘 이전'의 가장 최근 스팟만 단건으로 깔끔하게 조회!
        val pastSpot = spotDao.오늘을제외한100일간의최근1개spotDB가져옴(
            admCode,
            halfYearAgo,
            todayStartTimestamp
        )

        // 1줄: 오늘을 제외한 최근 방문 날짜 텍스트 조립
        // 1줄: '오늘 00시'와 '과거 스팟의 00시'를 기준으로 정확한 일수 차이 계산
        // 1줄: '오늘 00시'와 '과거 스팟의 00시'를 기준으로 일수 차이를 구하고,
        // 표기는 월, 일에다가 정확한 시간까지 상세히 노출!
        val visitDateText = pastSpot?.let { spot ->
            val spotCalendar = Calendar.getInstance().apply {
                timeInMillis = spot.spStartTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val spotDayZero = spotCalendar.timeInMillis

            val diffMillis = todayStartTimestamp - spotDayZero
            val daysAgo = diffMillis / (1000L * 60 * 60 * 24)

            // 💡 날짜뿐만 아니라 시·분까지 함께 포맷팅 (예: 9월 9일 오후 3시 20분)
            val dateString = SimpleDateFormat(
                "M월 d일 ah:m",
                Locale.KOREAN
            ).format(Date(spot.spStartTime))
            val correctedDays = if (daysAgo <= 0L) 1L else daysAgo

            "이전방문 : ${correctedDays}일전 ($dateString)"
        } ?: "최근 반년내 방문 기록 없음"

        // 2줄: 백그라운드 수집 상태 안내 문구
        val stayText = "백그라운드에서 안전하게 위치를 수집 합니다."

        // 최종 2줄 알림 내용 결합
        val messageText = "$visitDateText\n$stayText"

        showNotification(context = context, messageTitle = messageTitle, messageText = messageText)

    }
}