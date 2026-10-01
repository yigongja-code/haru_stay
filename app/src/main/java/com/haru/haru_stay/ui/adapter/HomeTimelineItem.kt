package com.haru.haru_stay.ui.adapter

import com.haru.haru_stay.data.entity.Spot
import com.haru.haru_stay.data.entity.Sum_db
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.haru.haru_stay.data.entity.VisitLog
import kotlin.Int

data class HomeTimelineItem(
    val startTime: String,
    val endTime: String,
    val adm: String,          // 1층 메인 카드용 주소
    val popTitle: String,     // 📌 2층 팝업 전용 주소 (1층과 분리)
    val timeRangeStr: String,

    //2층 팝업 db조회용
    val startEpoch: Long,
    val endEpoch: Long,

    // ⭐️ [메인 카드용] 첫 번째로 짤라낸 깔끔한 대표 값 (버벅임 없음!)
    val busStop: String,
    val wifiMac: String,
    val cellKey: String,

    // ⭐️ [나중 새 창용] 파이프로 묶인 전체 원본 데이터 보존용
    val rawBusStop: String?,
    val rawWifiMac: String,
    val rawCellKey: String,

    val lat: Double,
    val lon: Double,
    val spotTimeMinutes: Long,
    val detailInfo: String,
    val stats: String,

    // 💡 [추가] 뷰어에 보낼 가공된 1층 리스트 텍스트 필드
    var busStopDetailText: String = "없음",

    //버스 리스트
    val rawLogs: List<VisitLog>,
    // 💡 [추가] 팝업에서 이 스팟의 기존 메모 ID를 바로 쓸 수 있게 필드 장착!
    val memo: Long,
    //랭킹관련 sum_db
    val sumVisitCount: Int = 0,
    val sumVisitRank: Int = 0,
    val sumStayMinutes: String = "-",
    val sumStayRank: Int = 0,
    val sumLastTime: String = "-",
    val sumDayAgo: Int = 0
)


    //2층뷰 데이터 클래스
data class PopupBusItem(
    val displayText: String, // "오전 07:30 - 강남역 인근"
    val latitude: Double,    // logGpsLat (개별 좌표)
    val longitude: Double,    // logGpsLon (개별 좌표)
    val busAdmCode: String = "",    // logAdmCode
    val busAdmName: String = ""// logAdmName
)

//화면에 개별 박스에 버스정류장을 띄울 정보 수집
private fun 많이등장한버정찾기(logs: List<VisitLog>): String? {
    val busStopCounts = linkedMapOf<String, Int>()

    android.util.Log.e(
        "BUS_STOP_DEBUG-12",
        "🚌 많이등장한버정찾기() 받은 logs=${logs.size}개"
    )


    for (log in logs) {

        android.util.Log.e(
            "BUS_STOP_DEBUG-12",
            "   ↳ logId=${log.logId} / " +
                    "AdmCode=${log.logAdmCode} / " +
                    "시작=${log.logStartTime} / " +
                    "종료=${log.logEndTime} / " +
                    "버정=${log.logBusStop}"
        )
        val busStop = log.logBusStop?.trim().orEmpty()

        // 대표 정류장 후보에서 의미 없는 값은 제외
        val isInvalidBusStop = busStop.isBlank() ||
                busStop == "알 수 없음" ||
                busStop.startsWith("[⚠️disconnect]")

        if (isInvalidBusStop) {
            continue
        }

        // 처음 나온 순서가 유지되도록 LinkedHashMap 사용
        busStopCounts[busStop] =
            (busStopCounts[busStop] ?: 0) + 1
    }

    if (busStopCounts.isEmpty()) {
        return null
    }

    val representative = busStopCounts.maxByOrNull { it.value }

    // 모든 정류장이 1번씩만 등장하면 대표값 없음
    return if (representative != null && representative.value >= 2) {
        representative.key
    } else {
        null
    }
}

// ==========================================
// 📌 [2층 팝업 전용] 데이터 구조 및 가공 함수 영역
// ==========================================

/**
 * 원본 로그 리스트를 받아 연속된 일반 정류장은 압축하고,
 * 빈값이나 "알 수 없는 장소", 디스커넥트 같은 특수 상태는 유실 없이 살려내며,
 * 각각의 개별 위도·경도를 꽉 쥐어 `PopupBusItem` 리스트로 반환하는 전용 함수
 */
fun extractPopupBusItems(logs: List<com.haru.haru_stay.data.entity.VisitLog>): List<PopupBusItem> {
    if (logs.isEmpty()) {
        return listOf(PopupBusItem("없음", 0.0, 0.0))
    }

    val combinedItems = mutableListOf<PopupBusItem>()
    var lastPureStop: String? = null
    val timeFormatter = SimpleDateFormat("a h:mm", Locale.KOREA)

    for (log in logs) {
        val currentBusStop = log.logBusStop ?: ""

        // 💡 [핵심 예외 처리] 빈값이거나 "알 수 없는 장소", 디스커넥트 같은 특수 상태는
        // 연속으로 들어와도 중복 체크로 묶어서 버리지 않고 각각 타임라인에 살려야 함!
        val isSpecialStop = currentBusStop.isBlank() ||
                currentBusStop == "알 수 없음" ||
                currentBusStop == "지정되지 않은 장소" ||
                currentBusStop.startsWith("[⚠️disconnect]")

        // 특수 정류장이 아닐 때만 기존 '연속 중복 체크'를 수행해서 압축!
        if (!isSpecialStop && currentBusStop == lastPureStop) {
            continue
        }

        // 정류장 이름이 너무 길면 잘라서 말줄임표(…) 처리
        val maxLength = 50
        val trimmedBusStop = if (currentBusStop.length > maxLength) {
            currentBusStop.take(maxLength) + "…"
        } else {
            currentBusStop
        }

        // 시간 포맷 변환 (1~9시 사이 한 자리 시간에 '0' 붙여서 두 자리로 맞춤)
        val rawTimeStr = timeFormatter.format(Date(log.logStartTime))
        val formattedTimeStr = rawTimeStr.replace(Regex(" ([1-9]):")) { matchResult ->
            " 0${matchResult.groupValues[1]}:"
        }

        // =================================================================
        // ⭐️ 모든 로그에 GPS 오차 정보를 일괄적으로 붙이기
        // =================================================================
        val errorMeters = log.logGpsRange.toInt()

        // 화면에 표시될 최종 문자열 조합
        val displayStr = "$formattedTimeStr - $trimmedBusStop 인근 (GPS오차 ${errorMeters}m)"


        // ⭐️ [핵심 포인트] 텍스트와 함께 '개별 로그의 고유 위도/경도'를 객체에 꾹 담습니다!
        combinedItems.add(
            PopupBusItem(
                displayText = displayStr,
                latitude = log.logGpsLat,
                longitude = log.logGpsLon,
                busAdmCode = log.logAdmCode,
                busAdmName = log.logAdmName
            )
        )

        // 마지막 정류장 갱신
        lastPureStop = currentBusStop
    }

    // 결과가 비어있다면 "없음" 아이템 리턴, 아니면 가공된 리스트 리턴
    return if (combinedItems.isNotEmpty()) {
        combinedItems
    } else {
        listOf(PopupBusItem("없음", 0.0, 0.0))
    }
}


// 💡 Spot을 변환할 때, 해당 Spot의 시간 범위에 맞는 1차 로그 리스트를 파라미터로 함께 받습니다.
fun Spot.toTimelineItems(
        selectedDateStr: String,
        rawLogs: List<VisitLog>,
        sumList: List<Sum_db>
        ): List<HomeTimelineItem> {

    // 💡 24시간제(HH:mm)를 오전/오후 12시간제(a h:mm)로 변경
    val formatter = SimpleDateFormat("a h:mm", Locale.KOREA)
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val displayDateFormatter = SimpleDateFormat("M월d일", Locale.getDefault())

    val startDateStr = dateFormatter.format(Date(this.spStartTime))
    val endDateStr = dateFormatter.format(Date(this.spEndTime))

    val startStr = formatter.format(Date(this.spStartTime))

    // 💡 함수 상단에서 종료 시간 원천 방어! (0이거나 비어있으면 현재 시간으로 세탁)
    val safeEndTime = if (this.spEndTime > 0L) {
        this.spEndTime
    } else {
        System.currentTimeMillis()
    }

    // 자정(오전 12:00)이면 카드 표시용으로 종료시간 1분 증가
    val midnightSafeEndTime = if (
        formatter.format(Date(safeEndTime)) == "오전 12:00"
    ) {
        safeEndTime + 60_000L
    } else {
        safeEndTime
    }

// 시작과 종료가 같으면 카드 표시용으로 종료시간 1분 추가
    val displayEndTime = if (this.spStartTime == midnightSafeEndTime) {
        midnightSafeEndTime + 60_000L
    } else {
        midnightSafeEndTime
    }

    val rawEndStr = formatter.format(Date(displayEndTime))

    val endStr = rawEndStr// 👈 롱값을 안전하게 문자열로 변환!

    val startDisplayDate = displayDateFormatter.format(Date(this.spStartTime))
    val endDisplayDate = displayDateFormatter.format(Date(this.spEndTime))

    //카드에 넣을 값을 완성함
    val originalFullRangeStr = if (startDateStr != endDateStr) {
        "$startStr ~  $endStr ($startDisplayDate~$endDisplayDate)"
    } else {
        "$startStr ~ $endStr ($startDisplayDate)"
    }

    // 1️⃣ 메인 카드용: 파이프(|)에서 첫 번째 대표 값만 쏙 쪼개기
    val firstBusStop = this.spBusStop.split("|").firstOrNull { it.isNotBlank() } ?: ""
    val firstWifi = this.spWifiMac.split("|").firstOrNull { it.isNotBlank() } ?: ""
    val firstCellKey = this.spCellKey.split("|").firstOrNull { it.isNotBlank() } ?: ""

    // 📌 1층 카드용 타이틀
    val titleText = if (this.spAdmName.isNotBlank()) this.spAdmName else (firstBusStop.ifBlank { "지정되지 않은 장소" })

    // 📌 2층 팝업 전용 타이틀
    val poptitleText = if (this.spAdmName.isNotBlank()) this.spAdmName else "지정되지 않은 장소"

    val subText = "WiFi: ${firstWifi.ifBlank { "없음" }} | 상태: ${this.spStats}"
    val minutes = this.spSpotTime / (1000 * 60)

    // 💡 [핵심] 1차 데이터(rawLogs) 중 현재 Spot의 시간 범위(spStartTime ~ spEndTime)에 포함되는 것만 필터링한 뒤 buslist에 전달



    //filter로 걸러내고 있음...
    //카드별(spot) 이동경로를 만들어줌.
    val tenMinutes = 10 * 60 * 1000L

    val spotLogs = rawLogs.filter {
        it.logStartTime in
                (this.spStartTime - tenMinutes) until
                (this.spEndTime + tenMinutes) &&
                it.logAdmCode == this.spAdmCode
    }



    val spotSumList = sumList.filter {
        it.sumAdmCode == this.spAdmCode
    }

    val spotSum = spotSumList.firstOrNull {
        it.sumPeriodDays == 90
    }

    val cardBusStopText = 많이등장한버정찾기(spotLogs)

    val items = mutableListOf<HomeTimelineItem>()

    // ==================== sum 정제  ========================
    //마지막 방문일을  보기쉽게 날자로 변환
    val sumLastTime = spotSum?.sumLastTime ?: 0L
    val sumLastTimeText = if (sumLastTime > 0L) {
        SimpleDateFormat("M월 d일", Locale.KOREA)
            .format(Date(sumLastTime))
    } else {
        " - "
    }
    // 체류시간을 보기 쉽게 변환
    val sumStayMinutes = spotSum?.sumStayMinutes ?: 0L


    android.util.Log.e(
        "Statistics",
        "📊 카드=${this.spAdmName} / " +
                "카드AdmCode=${this.spAdmCode} / " +
                "Sum=${spotSum?.sumAdmName} / " +
                "SumAdmCode=${spotSum?.sumAdmCode} / " +
                "SumKey=${spotSum?.sumKey} / " +
                "Visit=${spotSum?.sumVisitCount} / " +
                "Stay=${spotSum?.sumStayMinutes}"
    )

    /*android.util.Log.e(
        "Statistics",
        "📊 카드=${this.spAdmName} / 카드AdmCode=${this.spAdmCode}"
    )

    android.util.Log.e(
        "Statistics",
        "📊 매칭된 Sum=${spotSumList.map {
            "${it.sumAdmName}:${it.sumAdmCode}:${it.sumStayMinutes}"
        }}"
    )



    android.util.Log.e(
        "Statistics",
        "📊 최종=${spotSum?.sumAdmName} / ${spotSum?.sumKey} / ${spotSum?.sumStayMinutes}"
    )*/





    val sumStayMinutesText = when {
        sumStayMinutes < 60L -> {

            "1시간 이내"
        }

        sumStayMinutes <= 24 * 60L -> {
            // 30분 이상이면 다음 시간으로 반올림
            val hours = (sumStayMinutes + 30L) / 60L
            "약 ${hours}시간"
        }

        else -> {
            // 24시간 초과 → 일 + 시간
            val days = sumStayMinutes / (24 * 60L)
            val remainMinutes = sumStayMinutes % (24 * 60L)
            val hours = (remainMinutes + 30L) / 60L

            if (hours >= 24L) {
                "${days + 1}일"
            } else if (hours > 0L) {
                "${days}일 ${hours}시간"
            } else {
                "${days}일"
            }
        }
    }




    // 1. 공통으로 쓸 기본 베이스 아이템 미리 생성 (또는 빌더처럼 활용)
    val baseItem = HomeTimelineItem(
        startTime = "", // 아래 when문에서 copy로 갈아끼움   카드에 그리는 시간
        endTime = "",   // 아래 when문에서 copy로 갈아끼움   카드에 그리는 시간
        adm = titleText,                        //카드 타이틀? 주소?
        popTitle = poptitleText,                //타이틀에 들어가는 버정?
        timeRangeStr = originalFullRangeStr,    //표시될 시간 (시작시간+종료시간)
        busStop = firstBusStop,                 //버정 - 첫번째 버정? 타이틀용?
        wifiMac = firstWifi,                    //와이파이 - 동일 - 폐지
        cellKey = firstCellKey,                 //기지국   - 동일 - 폐지
        rawBusStop = cardBusStopText,           //카드에 들어갈 버정리스트 -> 9/17 이제 대표값을 만들어 넘김 많이 등장한 버정대표값으로 올림
        rawWifiMac = this.spWifiMac,            //와이파이 - 동일 - 폐지
        rawCellKey = this.spCellKey,            //기지국   -동일  = 폐지
        startEpoch = this.spStartTime,          //실제 spotDB의 시작시간
        endEpoch = this.spEndTime,              //실제 spotDB의 종료시간
        lat = this.spGpsLat,                    //좌표 - 폐지
        lon = this.spGpsLon,                    //좌표 - 폐지
        spotTimeMinutes = minutes,              // 체류시간?
        detailInfo = subText,                   // ? 상세안내? 뭘?
        stats = this.spStats,                   //스팟 상태값 - 안씀
        rawLogs = spotLogs,                     // 모르겠음
        memo = this.spMemoId,                    //메모
        //랭킹관련 sum_db
        sumVisitCount = spotSum?.sumVisitCount ?: 0,         //방문획수
        sumVisitRank = spotSum?.sumVisitRank ?: 0,          //방문랭킹
        sumStayMinutes = sumStayMinutesText,                    //체류시간
        sumStayRank = spotSum?.sumStayRank ?: 0,            //체류랭킹
        sumLastTime = sumLastTimeText,                       //마지막방문시간
        sumDayAgo = if (spotSum != null) {                  //마지막방문후 경과시간
            ((System.currentTimeMillis() - spotSum.sumLastTime) /
                    (24 * 60 * 60 * 1000L)).toInt()
        } else {
            0
        }
    )

// 2. when 문으로 날짜 분기 및 롱스테이/경계 조건 깔끔하게 정리
    when {
        // case A: 시작일과 종료일이 다르고, 종료일이 선택한 날과 같거나 이전일 때 (과거에서 이어져 넘어오는 구간)
        startDateStr != endDateStr && endDateStr <= selectedDateStr -> {
            items.add(baseItem.copy(
                startTime = "00:00",
                endTime = endStr
                // 필요하다면 여기서 LongStay 관련 에폭이나 텍스트를 살짝 커스텀 copy 가능!
            ))
        }

        // case B: 시작일과 종료일이 다르고, 시작일이 선택한 날과 같을 때 (오늘 시작해서 미래로 뻗 나가는 긴 체류 구간)
        startDateStr != endDateStr && startDateStr == selectedDateStr -> {
            items.add(baseItem.copy(
                startTime = startStr,
                endTime = "24:00" // 혹은 당일 자정 마감 처리
            ))
        }

        // case C: 당일치기 (시작일 == 종료일 == 선택한 날)
        startDateStr == endDateStr && startDateStr == selectedDateStr -> {
            items.add(baseItem.copy(
                startTime = startStr,
                endTime = endStr
            ))
        }



    }

    return items
}