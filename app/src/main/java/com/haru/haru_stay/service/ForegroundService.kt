package com.haru.haru_stay.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.haru.haru_stay.R
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import android.content.IntentFilter


fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun androidx.fragment.app.Fragment.toast(message: String) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}


class ForegroundService : Service() {

    private val CHANNEL_ID = "HaruLocationChannel_v2"

    // 포그라운드 서비스 생명주기에 맞춘 코루틴 스코프
    // 화면 켜짐시 가공시작용
    private val serviceScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var screenOnReceiver: ScreenOnReceiver? = null

    // 현재 서비스 인스턴스가 이미 Foreground 상태로 시작되었는지 확인
    // ACTION_START가 다시 들어와도 기존 알림을 덮어쓰지 않기 위한 변수
    private var isForegroundStarted = false


    override fun onBind(intent: Intent?): IBinder? = null


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.e("FGS_TRACE", "🔥 ForegroundService.onStartCommand 호출")
        Log.e("FGS_TRACE", "🔥 action = ${intent?.action}")
        Log.e("FGS_TRACE", "🔥 startId = $startId")



        when (intent?.action) {

            "ACTION_START" -> {

                // 서비스가 새로 시작된 경우에만
                // Foreground 알림을 처음 생성합니다.
                //
                // 이미 실행 중인 서비스에 ACTION_START가 다시 들어오면
                // 기존 위치 알림 내용을 그대로 유지합니다.
                if (!isForegroundStarted) {
                    startForegroundServiceWithNotification()
                    Toast.makeText(
                        this,
                        "포그라운드 위치 수집 시작",
                        Toast.LENGTH_SHORT
                    ).show()
                    isForegroundStarted = true
                }

                // WorkManager는 기존 작업이 있으면 UPDATE 정책으로 갱신합니다.
                startLocationWorkManager()
                startProcessingWorkManager()
            }

            "ACTION_STOP" -> {

                // 필요하면 아래 주석을 해제해서 두 작업을 함께 취소할 수 있습니다.
                // WorkManager.getInstance(this)
                //     .cancelUniqueWork("LocationCollectWork")
                //
                // WorkManager.getInstance(this)
                //     .cancelUniqueWork("SpotProcessingWork")

                Toast.makeText(
                    this,
                    "포그라운드 종료",
                    Toast.LENGTH_SHORT
                ).show()


                stopForeground(true)
                // 실제 Foreground 상태가 종료되었으므로 상태값도 변경
                isForegroundStarted = false
                stopSelf()
            }
            "ACTION_TOGGLE" -> {

                if (isForegroundStarted) {

                    // 현재 기록 중 → 기록 중지
                    val stopIntent = Intent(this, ForegroundService::class.java).apply {
                        action = "ACTION_STOP"
                    }

                    onStartCommand(stopIntent, 0, startId)

                } else {

                    // 현재 기록 중지 → 기록 시작
                    val startIntent = Intent(this, ForegroundService::class.java).apply {
                        action = "ACTION_START"
                    }

                    onStartCommand(startIntent, 0, startId)
                }
            }
            "ACTION_STATUS" -> {

                val statusIntent =
                    Intent("HARU_FGS_STATUS").apply {
                        setPackage(packageName)
                        putExtra(
                            "isForegroundStarted",
                            isForegroundStarted
                        )
                    }

                sendBroadcast(statusIntent)
            }
        }

        return START_STICKY
    }


    override fun onCreate() {
        super.onCreate()

        screenOnReceiver = ScreenOnReceiver()

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOnReceiver,filter,Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOnReceiver,filter)
        }
    }


    override fun onDestroy() {

        // 서비스가 실제로 종료되었으므로
        // 다음에 새 서비스 인스턴스가 만들어지면
        // Foreground 알림을 다시 만들어야 합니다.
        isForegroundStarted = false

        screenOnReceiver?.let {
            unregisterReceiver(it)
        }

        screenOnReceiver = null

        serviceScope.cancel()

        super.onDestroy()
    }


    private fun startForegroundServiceWithNotification() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "HA_RU SPOT v1",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description =
                    "백그라운드에서 위치 정보를 수집하는 서비스입니다."

                setSound(null, null)
                enableVibration(false)
            }

            val manager = getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }


        val notification =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("haru_spot")
                .setContentText("수집을 기다리고 있습니다.")
                .setSmallIcon(R.drawable.outline_pin_history_24)
                .setOngoing(true)
                .build()


        startForeground(1, notification)


    }


    private fun startLocationWorkManager() {

        val sharedPreferences =
            getSharedPreferences(
                "app_prefs",
                Context.MODE_PRIVATE
            )

        val savedMinutes =
            sharedPreferences.getInt(
                "collect_interval_minutes",
                10
            )

        val intervalMinutes =
            if (savedMinutes < 15) {
                15L
            } else {
                savedMinutes.toLong()
            }


        val request =
            PeriodicWorkRequestBuilder<LogCollectWorker>(
                intervalMinutes,
                TimeUnit.MINUTES
            )
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    5,
                    TimeUnit.MINUTES
                )
                .build()


        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "LocationCollectWork",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )


        Log.d(
            "ForegroundService",
            "위치 수집 Worker 등록 완료: ${intervalMinutes}분 주기"
        )

    }


    /**
     * 충전 중에만 가공 Worker가 실행됩니다.
     * 평소에는 WorkManager가 조건이 만족될 때까지 대기합니다.
     */
    private fun startProcessingWorkManager() {

        val constraints =
            Constraints.Builder()
                .setRequiresCharging(true)
                .build()


        val request =
            PeriodicWorkRequestBuilder<SpotProcessingWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()


        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "SpotProcessingWork",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )


        Log.d(
            "ForegroundService",
            "가공 Worker 등록 완료: 충전 중에만 실행"
        )
    }
}