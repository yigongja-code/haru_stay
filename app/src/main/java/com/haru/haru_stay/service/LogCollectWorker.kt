package com.haru.haru_stay.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.haru.haru_stay.data.database.AppDatabase
//import com.example.haru_spot.service.LogSaveManager
import com.haru.haru_stay.service.collector.AdmCollector
import com.haru.haru_stay.service.collector.AdmResult
import com.haru.haru_stay.service.collector.CellIdCollector
import com.haru.haru_stay.service.collector.LocationCollector
import com.haru.haru_stay.service.collector.WifiCollector
import com.haru.haru_stay.service.manager.LogSaveManager
import com.haru.haru_stay.service.유틸.알림유틸
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogCollectWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {


            val context = applicationContext
            val locationData = LocationCollector(context).fetchBestLocation()

            // 💡 핵심 방어선: 위치 좌표를 끝내 못 건졌다면(null) 여기서 즉시 재시도 진입!
            if (locationData == null) {

                return@withContext Result.retry()
            }

            val cellData = CellIdCollector(context).fetchCellId()
            val wifiData = WifiCollector(context).fetchConnectedWifi()

            var admResult: AdmResult? = null
            val lat = locationData.latitude
            val lon = locationData.longitude
            val admCollector = AdmCollector(context)
            admResult = admCollector.findCurrentLocationAdm(lat, lon)

            val logDao = AppDatabase.getDatabase(context).logDao()
            val saveManager = LogSaveManager(context, logDao)

            saveManager.saveLogData(locationData, cellData, wifiData, admResult)
            알림유틸.updateWithAdmResult(context, admResult)


            Result.success()
        } catch (e: Exception) {

            Result.retry() // 실패 시 재시도
        }
    }
}