package com.example.haru_spot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 2. 로그데이터(log_db)
// 💡 공간 조인 및 가공 스펙(행정동 코드, 상세 주소 등) 개편 반영
@Entity(tableName = "log_db")
data class VisitLog(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val logAdmCode: String,     // 10자리 행정동 코드 (예: 32020360)
    val logAdmName: String,     // 동 단위 상세 주소 (예: 명륜동(원주시))
    val logBusStop: String,     // 정류장명 (NODE_NM)
    val logCellKey: String,     // 통신사 셀 키 (Cell ID)
    val logWifiMac: String,     // 주변 와이파이 MAC 주소(와이파이이름)
    val logGpsLat: Double,      // 위도 (소수점 4자리 반올림)
    val logGpsLon: Double,      // 경도 (소수점 4자리 반올림)
    val logStats: String,       // 상태 정보 disconnected,merge,ignored,hold,pending
    val logStartTime: Long,     // 로그 기록 시작 시간 (Timestamp)
    val logEndTime: Long,       // 로그 기록 종료 시간 (Timestamp)
    val logProcessedAt: Long,   // 가공처리관련 시간값
    val logMemoId: Long,        // 연결된 메모 ID
    val logGpsRange: Double = 0.0,    // 💡 오차 범위 기본값
    val logBusDistance: Long,        // 정류장과 GPS와의 거리
    val logField2: String = "",       // 예비2 기본값
    val logField3: String = "",       // 예비3 기본값
    val logField4: String = ""        // 예비4 기본값
)
