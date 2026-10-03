package com.haru.haru_stay.service.manager

import android.content.Context
import android.location.Location
import com.haru.haru_stay.data.dao.LogDao
import com.haru.haru_stay.data.entity.VisitLog
import com.haru.haru_stay.service.collector.AdmResult

class LogSaveManager(
    private val context: Context,
    private val logDao: LogDao // DB에 접근하기 위한 DAO
) {
    /**
     * 수집된 모든 데이터 패키지를 받아 VisitLog 엔티티를 생성하고 DB에 안전하게 저장합니다.
     */
    suspend fun saveLogData(
        locationData: Location?,
        cellData: String?,
        wifiData: String?,
        admResult: AdmResult?
    ) {
        // 💡 오차 범위(accuracy)를 소수점 없이 정수 미터 형태의 Double로 변환
        val gpsRange = locationData?.accuracy?.let { kotlin.math.round(it).toInt().toDouble() } ?: 0.0
        val visitLog = VisitLog(
            logAdmCode = admResult?.admCode ?: "0000000000",        // 10자리 행정동 코드
            logAdmName = admResult?.admName ?: "동정보없음",       // AdmCollector에서 완성된 "명륜동 (원주시)" 주소 포맷
            logBusStop = admResult?.stopName ?: "",                // 가장 가까운 버스정류장 이름 (NODE_NM)
            logCellKey = cellData ?: "",
            logWifiMac = wifiData ?: "",
            logGpsLat = locationData?.latitude ?: 0.0,
            logGpsLon = locationData?.longitude ?: 0.0,
            logGpsRange = gpsRange,                                // 💡 가공된 정수형 오차 범위 대입
            logStats = "pending",
            logStartTime = System.currentTimeMillis(),
            logEndTime = 0L,
            logProcessedAt = 0L,
            logMemoId = 0L,
            logBusDistance = admResult?.busDistance?: 0L,
            logField2 = "cmppending",       // log압축용 상태값 cmppending cmpfirst cmpignored cmplast
            logField3 = "",       // 예비3 기본값
            logField4 = ""        // 예비4 기본값
        )

        // DAO를 통해 DB 트랜잭션 저장 실행
        logDao.insert(visitLog)

        // 화면 켜짐 상태 체크 후 방아쇠 발사
        // 휴대폰의 화면이 켜져있다면 가공을 시작
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (powerManager.isInteractive) {
            com.haru.haru_stay.service.유틸.TriggerLogToSpot.runIfNeeded(context)
        }
    }
}