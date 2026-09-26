package com.example.haru_spot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 3. 가공데이터 (spot_db)
@Entity(tableName = "spot_db")
data class Spot(
    @PrimaryKey(autoGenerate = true) val spId: Long = 0,
    val spAdmCode: String,      // 10자리 행정동 코드
    var spAdmName: String,      // 동 단위 상세 주소
    val spBusStop: String,      // 정류장명
    val spCellKey: String,      // 통신사 셀 키 (Cell ID)
    val spWifiMac: String,      //  와이파이 MAC 주소(와이파이이름)
    val spGpsLat: Double,       // 위도
    val spGpsLon: Double,       // 경도
    val spStats: String,        // 스팟 상태 정보
    val spStartTime: Long,      // 스팟 시작 시간 (Timestamp)
    var spEndTime: Long,        // 스팟 종료 시간 (Timestamp)
    val spSpotTime: Long,       // 스팟 체류 산정 시간
    val spProcessedAt: Long,    // 가공 처리 완료 시점의 타임스탬프 (기존 불필요 필드 재정의)
    val spMemoId: Long          // 연결된 메모 ID
)