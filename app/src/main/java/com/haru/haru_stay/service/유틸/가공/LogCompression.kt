package com.haru.haru_stay.service.유틸.가공

import android.content.Context
import android.util.Log
import com.haru.haru_stay.data.database.AppDatabase
import com.haru.haru_stay.data.entity.Fav
import com.haru.haru_stay.data.entity.VisitLog

class LogCompression(
    private val context: Context
) {

    /**
     * =========================================================
     * 3차 로그 압축
     * =========================================================
     *
     * 목적
     * - 로그 용량 감소가 1차 목적이 아님
     * - 확실한 장소(Wi-Fi)를 기준으로
     *   GPS 미세 튐이 이동경로에 표시되는 것을 줄이는 것이 목적
     *
     * 원칙
     * - log_db 원본 데이터는 수정하지 않는다.
     * - 로그 삭제도 하지 않는다.
     * - logStartTime / logEndTime / GPS / Wi-Fi 등 원본값은 유지한다.
     * - logField2 상태값만 사용한다.
     *
     * 상태값
     * - cmppending - 기본값
     * - cmpfirst   - 압축 첫번째
     * - cmpignored - 압축된 값
     * - cmplast    - 압축 마지막
     * - cmpnone    - 압축과 상관없는 단독 로그
     * - cmpconnect - 로그 마지막 연결자
     *
     * 이동경로 표시 시
     * - cmpignored만 제외
     * - 나머지 상태값은 모두 표시
     *
     * 아직 실제 DB 연결은 하지 않는다.
     * List를 받아서 가공 결과 List를 반환하는 형태로 먼저 구현한다.
     */
    private  val TAG = "logcmp"


    suspend fun compressLogs(): List<VisitLog> {

        val db = AppDatabase.getDatabase(context)

        val logDao = db.logDao()
        val favoriteDao = db.favoriteDao()

         // 3차 압축 대상 로그 조회
        val logs = logDao.getLogsForCompression()

        // 즐겨찾기 전체 조회
        val favorites = favoriteDao.getAllFavList()

        val result = logs.toMutableList()

        // 압축 대상 로그가 없으면 종료
        if (result.size <= 1) {
            return result
        }

// 변경된 로그만 마지막에 한 번 저장하기 위한 목록
        val changedLogs = linkedMapOf<Long, VisitLog>()

        fun setState(index: Int, state: String) {
            val updated = result[index].copy(
                logField2 = state
            )

            result[index] = updated
            changedLogs[updated.logId] = updated
        }


// =====================================================
// 1차 : Wi-Fi 기준 압축
// =====================================================
        for (i in result.indices) {

            val current = result[i]

            // =====================================================
            // 마지막 로그
            //
            // 다음 로그가 없으므로 여기서 더 이상 판정하지 않는다.
            // 마지막 로그는 다음 실행과 연결하기 위해 cmpconnect로 남긴다.
            // =====================================================
            if (i == result.lastIndex) {
                setState(i, "cmpconnect")
                break
            }

            val next = result[i + 1]

            // =====================================================
            // 현재 로그가 즐겨찾기가 아닐때
            // =====================================================
            if (!current.logBusStop.startsWith("⭐")) {
                setState(i, "cmpnone")
                /*Log.e(
                    TAG,
                    "❌ 즐겨찾기 가드 컷: id=${current.logId}, busStop=${current.logBusStop}"
                )*/
                continue
            }

            // =====================================================
            // 즐겨찾기 로그지만 Wi-Fi가 없으면 압축하지 않는다.
            // =====================================================
            if (current.logWifiMac.isNullOrEmpty()) {
                setState(i, "cmpnone")
                Log.e(
                    TAG,
                    "❌ 즐겨찾기 가드 와이파이 컷: id=${current.logId}, busStop=${current.logBusStop}"
                )
                continue
            }

            when (current.logField2) {

                // =================================================
                // 새로운 압축 후보
                // =================================================
                "cmpnone", "cmppending" -> {

                    // =====================================================
                    // 현재 로그와 일치하는 즐겨찾기를 메모리에서 찾는다.
                    //
                    // 위의 조건문을 통과했으므로
                    // 현재 로그는 즐겨찾기 + Wi-Fi 존재가 이미 확인된 상태다.
                    // =====================================================
                    val favorite = favorites.first {
                        it.favBusStop == current.logBusStop
                            .removePrefix("⭐")
                            .trim()
                    }

                    // =====================================================
                    // 즐겨찾기의 Wi-Fi와 현재 로그의 Wi-Fi가 같고
                    // 다음 로그의 Wi-Fi도 현재 로그와 같으면
                    // 새로운 압축을 시작한다.
                    //
                    // 현재 로그 → 압축 첫번째
                    // 다음 로그 → 현재 시점에서는 압축 마지막
                    //
                    // 다음 반복에서 다음 로그를 다시 확인하면서
                    // 압축이 계속되면 cmplast를 뒤로 이동시킨다.
                    // =====================================================
                    val currentWifiMac = current.logWifiMac.take(17)
                    val favoriteWifiMac = favorite.favWifiMac.take(17)
                    val nextWifiMac = next.logWifiMac.orEmpty().take(17)
                    Log.e(
                        TAG,
                        "즐겨찾기 if전 : id=${current.logId}, busStop=${current.logBusStop}"
                    )
                    Log.e(
                        TAG,
                        "fav=$favoriteWifiMac / current=$currentWifiMac / next=$nextWifiMac"
                    )

                    if (favoriteWifiMac == currentWifiMac &&
                        currentWifiMac == nextWifiMac
                    ) {
                        setState(i, "cmpfirst")
                        setState(i + 1, "cmplast")

                        continue
                    }
                    Log.e(
                        TAG,
                        "❌ 즐겨찾기 if끝  컷: id=${current.logId}, busStop=${current.logBusStop}"
                    )
                    // 압축 시작 조건 불충족
                    setState(i, "cmpnone")

                    // 다음 로그는 변경하지 않는다.
                    // 다음 반복에서 다시 판정한다.
                    continue
                }


                // =================================================
                // 현재 압축의 꼬리
                // =================================================
                "cmplast", "cmpconnect" -> {

                    val currentWifiMac = current.logWifiMac.take(17)
                    val nextWifiMac = next.logWifiMac.orEmpty().take(17)

                    if (currentWifiMac == nextWifiMac) {

                        setState(i, "cmpignored")
                        setState(i + 1, "cmplast")

                        continue
                    }

                    // 연결자가 이전 실행의 마지막 값이었다면
                    // 이번 실행에서는 현재 압축의 마지막 값으로 확정한다.
                    if (current.logField2 == "cmpconnect") {
                        setState(i, "cmplast")
                    }

                    continue
                }
            }
        }


// =====================================================
// 2차 : 날짜 변경 지점 정리
//
// 날짜가 바뀌는 순간에는 압축을 끊는다.
// 이동경로도 날짜별로 따로 보기 때문에
// 서로 다른 날짜의 로그가 하나의 압축으로 이어지면 안 된다.
// =====================================================
        val calendar = java.util.Calendar.getInstance()

        fun isSameDate(time1: Long, time2: Long): Boolean {

            calendar.timeInMillis = time1
            val year1 = calendar.get(java.util.Calendar.YEAR)
            val day1 = calendar.get(java.util.Calendar.DAY_OF_YEAR)

            calendar.timeInMillis = time2
            val year2 = calendar.get(java.util.Calendar.YEAR)
            val day2 = calendar.get(java.util.Calendar.DAY_OF_YEAR)

            return year1 == year2 && day1 == day2
        }

        for (i in 0 until result.lastIndex) {

            val current = result[i]
            val next = result[i + 1]

            // 날짜가 같으면 아무것도 하지 않는다.
            if (isSameDate(
                    current.logStartTime,
                    next.logStartTime
                )
            ) {
                continue
            }

            // =====================================================
            // 날짜 변경 지점
            // =====================================================
            when (current.logField2) {

                "cmpnone" -> {
                    // 그대로 유지
                }

                "cmpfirst" -> {

                    // 현재 날짜의 압축을 끊는다.
                    setState(i, "cmpnone")

                    // 다음 로그가 연결자라면 그대로 둔다.
                    // 그 외에는 새로운 날짜의 압축 첫 로그로 만든다.
                    if (next.logField2 != "cmpconnect") {
                        setState(i + 1, "cmpfirst")
                    }
                }

                "cmpignored" -> {

                    // 이전 날짜의 압축 마지막으로 확정
                    setState(i, "cmplast")

                    when (next.logField2) {

                        "cmpconnect" -> {
                            // 연결자는 그대로 유지
                        }

                        "cmplast" -> {
                            // 다음 날짜의 시작점이 될 수 없으므로
                            // 압축과 관계없는 로그로 변경
                            setState(i + 1, "cmpnone")
                        }

                        else -> {
                            // 다음 날짜에서 새로운 압축 시작
                            setState(i + 1, "cmpfirst")
                        }
                    }
                }

                "cmplast" -> {
                    // 그대로 유지
                }
            }
        }


// =====================================================
// 3차 : 변경된 상태값만 DB 저장
// =====================================================
        if (changedLogs.isNotEmpty()) {
            logDao.updateLogs(changedLogs.values.toList())
        }

        return result

    }
}
