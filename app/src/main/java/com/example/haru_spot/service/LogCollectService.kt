package com.example.haru_spot.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.haru_spot.service.collector.LocationCollector //위도 경도
import com.example.haru_spot.service.collector.CellIdCollector//기지국
import com.example.haru_spot.service.collector.WifiCollector //와이파이
import com.example.haru_spot.service.collector.AdmCollector //법정동코드, 읍면동(시군구)
import com.example.haru_spot.service.collector.AdmResult
import com.example.haru_spot.service.manager.LogSaveManager //수집한 정보를 저장
import com.example.haru_spot.data.database.AppDatabase
import com.example.haru_spot.service.유틸.알림유틸

class LogCollectService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int,): Int {
        if (intent?.action == "ACTION_COLLECT") {
            // 포그라운드 서비스로부터 수집 명령을 받았을 때 실행
            executeCollection()
        }
        return START_NOT_STICKY
    }

    private fun executeCollection() {
        serviceScope.launch {
            // TODO: 1. 각 수집기(Collector) 호출해서 데이터 가져오기
            //LocationCollecto = 위도 경도 수집 celldata = 기지국 wifidata = 와이파이이름 mac
            val locationData = LocationCollector(this@LogCollectService).fetchBestLocation()
            val cellData = CellIdCollector(this@LogCollectService).fetchCellId()
            val wifiData = WifiCollector(this@LogCollectService).fetchConnectedWifi()

            // 1. if문 바깥에서 admResult 변수를 먼저 선언해 줍니다 (타입은 AmdResult?)
            var admResult: AdmResult? = null

            //법정동 코드와 읍면동(시군구) 받아오기
            // 법정동 코드와 읍면동(시군구) 받아오기
            if (locationData != null) {
                val lat = locationData.latitude
                val lon = locationData.longitude

                // 2. AdmCollector 호출 (불필요한 람다식 제거)
                val admCollector = AdmCollector(this@LogCollectService)
                admResult = admCollector.findCurrentLocationAdm(lat, lon)
            }

            // TODO: 2. 데이터 가공 및 LogDao를 통해 DB에 저장하기
            // 3. LogSaveManager 호출 및 실제 데이터 인자 전달
            val logDao = AppDatabase.getDatabase(this@LogCollectService).logDao()
            val saveManager = LogSaveManager(this@LogCollectService, logDao)

            // 수집한 데이터들을 매니저의 saveLogData 함수로 고스란히 넘겨줍니다!
            saveManager.saveLogData(locationData, cellData, wifiData, admResult)
            알림유틸.updateWithAdmResult(this@LogCollectService, admResult)

            Log.d("LogCollectService", "LogSaveManager완료 알림,저장호출")

        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}