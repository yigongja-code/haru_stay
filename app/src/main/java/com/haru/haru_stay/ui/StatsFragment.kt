package com.haru.haru_stay.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.haru.haru_stay.R
import com.haru.haru_stay.data.database.AppDatabase
import com.haru.haru_stay.data.entity.Sum_db
import com.haru.haru_stay.util.SumUtil
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatsFragment : Fragment() {

    private var tvStatTitle: TextView? = null
    private var tvStatMessage: TextView? = null
    private var layoutRankContainer: LinearLayout? = null
    private var layoutStayTimeRankContainer: LinearLayout? = null


    // ==========================================================
    // Sum DB에서 가져온 통계 캐시
    // ==========================================================

    private var cachedSumList: List<Sum_db> = emptyList()

    private var cachedVisitRanking: List<Sum_db> = emptyList()
    private var cachedStayRanking: List<Sum_db> = emptyList()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //뒤로가기 누르면 홈프레그먼트로 가기
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {
                    requireActivity()
                        .findViewById<BottomNavigationView>(R.id.bottom_navigation)
                        .selectedItemId = R.id.nav_bar
                }
            }
        )
    }

    // ==========================================================
    // View 생성
    // ==========================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view =
            inflater.inflate(
                R.layout.fragment_stats,
                container,
                false
            )

        tvStatTitle =
            view.findViewById(R.id.tvStatTitle)

        tvStatMessage =
            view.findViewById(R.id.tvStatMessage)

        layoutRankContainer =
            view.findViewById(R.id.layoutVisitRankContainer)

        layoutStayTimeRankContainer =
            view.findViewById(R.id.layoutStayTimeRankContainer)

        return view
    }


    // ==========================================================
    // 화면 진입
    //
    // Sum 계산이 끝난 다음 통계를 읽는다.
    // ==========================================================

    override fun onResume() {
        super.onResume()

        viewLifecycleOwner.lifecycleScope.launch {

            // --------------------------------------------------
            // 1. Sum DB 최신화
            // --------------------------------------------------

            SumUtil.calculateSum(
                requireContext(),
                periodDays = 90
            )


            // --------------------------------------------------
            // 2. Sum DB 읽기
            // --------------------------------------------------

            refreshAllData()
        }
    }


    // ==========================================================
    // 전체 통계 갱신
    // ==========================================================

    private suspend fun refreshAllData() {

        val context =
            requireContext()

        val database =
            AppDatabase.getDatabase(context)

        val sumDao =
            database.sumDao()


        // ======================================================
        // Sum DB 전체 읽기
        //
        // 현재 약 400여 행이므로 매우 가벼운 작업
        // ======================================================

        val allSum =
            withContext(Dispatchers.IO) {

                sumDao.getAllSum()
            }

        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val sums = db.sumDao().getAllSum()

            sums.forEach { sum ->
                Log.e(
                    "SumCheck",
                    "key=${sum.sumKey} / adm=${sum.sumAdmName} / " +
                            "visit=${sum.sumVisitCount} / stay=${sum.sumStayMinutes}분 / " +
                            "period=${sum.sumPeriodDays}"
                )
            }
        }


        // ======================================================
        // 90일 일반 Sum만 사용
        //
        // 관리행 9999999999 제외
        // ======================================================

        cachedSumList =
            allSum.filter {

                it.sumPeriodDays == 90 &&
                        it.sumAdmCode != "9999999999"
            }


        // ======================================================
        // 방문 Rank
        //
        // Sum DB에 이미 Rank가 계산되어 있음
        // ======================================================

        cachedVisitRanking =
            cachedSumList
                .filter {
                    it.sumVisitCount > 0
                }
                .sortedBy {
                    if (it.sumVisitRank > 0) {
                        it.sumVisitRank
                    } else {
                        Int.MAX_VALUE
                    }
                }


        // ======================================================
        // 체류 Rank
        // ======================================================

        cachedStayRanking =
            cachedSumList
                .filter {
                    it.sumStayMinutes > 0L
                }
                .sortedBy {
                    if (it.sumStayRank > 0) {
                        it.sumStayRank
                    } else {
                        Int.MAX_VALUE
                    }
                }


        // ======================================================
        // 화면 갱신
        // ======================================================

        withContext(Dispatchers.Main) {

            갱신방문랭킹()

            갱신체류랭킹()
        }


        // ======================================================
        // 첫 번째 카드
        //
        // 현재 위치 / 이전 방문 / 현재 체류
        // 이 부분은 아직 Spot DB를 사용한다.
        // ======================================================

        함수첫번째카드현재상태()
    }


    // ==========================================================
    // 첫 번째 카드
    //
    // 현재 위치와 실제 최근 Spot 상태는 Spot DB 사용
    //
    // 랭킹 부분만 Sum DB 사용
    // ==========================================================

    private fun 함수첫번째카드현재상태() {

        viewLifecycleOwner.lifecycleScope.launch {

            try {

                val context =
                    requireContext()

                val database =
                    AppDatabase.getDatabase(context)

                val spotDao =
                    database.spotDao()

                val logDao =
                    database.logDao()


                // ------------------------------------------------
                // 현재 행정동
                // ------------------------------------------------

                val 최근로그AdmCode =
                    withContext(Dispatchers.IO) {

                        logDao.최근의로그에서admcode가져옴()
                            ?: ""
                    }


                val todayStartTimestamp =
                    getTodayStartTimestamp()

                val threeMonthsAgo =
                    getThreeMonthsAgoTimestamp()


                val messageTitle =
                    "최근 방문 위치 통계"


                // ------------------------------------------------
                // 가장 최근 Spot
                //
                // 현재 체류 상태 확인 때문에 유지
                // ------------------------------------------------

                val 최근스팟 =
                    withContext(Dispatchers.IO) {

                        spotDao.가장최근spotDB를code로가져옴(
                            최근로그AdmCode
                        )
                    }


                if (최근스팟 == null) {

                    withContext(Dispatchers.Main) {

                        tvStatTitle?.text =
                            messageTitle

                        tvStatMessage?.text =
                            "최근 3달내 이지역을 방문한 기록이 없습니다.\n체류 시간 기록 없음"
                    }

                    return@launch
                }


                // ------------------------------------------------
                // 현재 위치 이름
                // ------------------------------------------------

                val 현재위치이름 =
                    최근스팟.spAdmName
                        ?: "현재 위치"


                val 현재시간 =
                    System.currentTimeMillis()

                val 한시간 =
                    60L * 60L * 1000L


                // ------------------------------------------------
                // 최근 Spot 상태
                // ------------------------------------------------

                val isWithinOneHour =
                    최근스팟.spEndTime != null &&
                            (현재시간 - 최근스팟.spEndTime) <= 한시간


                val isTodaySpot =
                    최근스팟.spEndTime != null &&
                            최근스팟.spStartTime >=
                            todayStartTimestamp


                // ------------------------------------------------
                // 오늘 방문이면 이전 방문을 Spot에서 찾음
                //
                // Sum의 sumLastTime은 현재 방문시간까지
                // 포함하므로 "이전 방문" 표시에는 기존 조회 유지
                // ------------------------------------------------

                val pastSpot =
                    if (isTodaySpot) {

                        withContext(Dispatchers.IO) {

                            spotDao.오늘을제외한100일간의최근1개spotDB가져옴(
                                최근로그AdmCode,
                                threeMonthsAgo,
                                todayStartTimestamp
                            )
                        }

                    } else {

                        null
                    }


                val targetSpotForVisit =
                    if (isTodaySpot) {
                        pastSpot
                    } else {
                        최근스팟
                    }


                // ------------------------------------------------
                // 이전 방문 문구
                // ------------------------------------------------

                val line1Text =
                    targetSpotForVisit?.let { spot ->

                        val spotCalendar =
                            Calendar.getInstance().apply {

                                timeInMillis =
                                    spot.spStartTime

                                set(
                                    Calendar.HOUR_OF_DAY,
                                    0
                                )

                                set(
                                    Calendar.MINUTE,
                                    0
                                )

                                set(
                                    Calendar.SECOND,
                                    0
                                )

                                set(
                                    Calendar.MILLISECOND,
                                    0
                                )
                            }


                        val spotDayZero =
                            spotCalendar.timeInMillis


                        val diffMillis =
                            todayStartTimestamp -
                                    spotDayZero


                        val daysAgo =
                            diffMillis /
                                    (1000L * 60L * 60L * 24L)


                        val dateString =
                            formatDate(
                                spot.spStartTime
                            )


                        val correctedDays =
                            if (daysAgo <= 0L) {
                                1L
                            } else {
                                daysAgo
                            }


                        "이전방문 : ${correctedDays}일전(${dateString})"

                    } ?: "최근 3달내 이지역을 방문한 기록이 없습니다."


                // ------------------------------------------------
                // 현재 체류 문구
                //
                // 기존 동작 유지
                // ------------------------------------------------

                val line2Text =
                    if (isWithinOneHour) {

                        val diffMillis =
                            현재시간 -
                                    최근스팟.spStartTime


                        val safeDiffMillis =
                            if (diffMillis < 0L) {
                                0L
                            } else {
                                diffMillis
                            }


                        val totalHours =
                            safeDiffMillis /
                                    (1000L * 60L * 60L)


                        val days =
                            totalHours / 24L


                        val hours =
                            totalHours % 24L


                        val minutes =
                            (safeDiffMillis /
                                    (1000L * 60L)) % 60L


                        when {

                            days > 0 ->
                                "현위치 ${days}일 ${hours}시간 체류중"

                            hours > 0 ->
                                "현위치 ${hours}시간 ${minutes}분 체류중"

                            else ->
                                "현위치 ${minutes}분 체류중"
                        }

                    } else {

                        "현위치 체류시간 계산 중"
                    }


                // =================================================
                // Sum DB에서 현재 지역 Rank 조회
                // =================================================

                val currentSum =
                    cachedSumList.firstOrNull {

                        it.sumAdmCode ==
                                최근로그AdmCode
                    }


                // ------------------------------------------------
                // 방문 Rank
                // ------------------------------------------------

                val visitRankText =
                    if (
                        currentSum != null &&
                        currentSum.sumVisitRank > 0 &&
                        currentSum.sumVisitCount >= 2
                    ) {
                        "${currentSum.sumVisitRank}위(${currentSum.sumVisitCount}회) : " +
                                "${currentSum.sumAdmName}"
                    } else {
                        "최대 방문 지역 랭킹 집계 외"
                    }


                // ------------------------------------------------
                // 체류 Rank
                // ------------------------------------------------

                val stayRankText =
                    if (currentSum != null &&
                        currentSum.sumStayRank > 0
                    ) {

                        "최대 체류 시간 랭킹 ${currentSum.sumStayRank}위"

                    } else {

                        "최대 체류 시간 랭킹 집계 외"
                    }


                // ------------------------------------------------
                // 최종 메시지
                // ------------------------------------------------

                val messageText =
                    "📍 [$현재위치이름]\n" +
                            "$line1Text\n" +
                            "$line2Text\n" +
                            "$visitRankText\n" +
                            "$stayRankText"



                withContext(Dispatchers.Main) {

                    tvStatTitle?.text =
                        messageTitle

                    tvStatMessage?.text =
                        messageText
                }

            } catch (e: Exception) {

                e.printStackTrace()
            }
        }
    }


    // ==========================================================
    // 방문 횟수 랭킹
    //
    // Spot DB 조회 없음
    // Sum DB 결과만 화면에 표시
    // ==========================================================

    private fun 갱신방문랭킹() {

        val context =
            context ?: return

        val container =
            layoutRankContainer
                ?: return


        container.removeAllViews()


        if (cachedVisitRanking.isEmpty()) {

            val emptyView =
                TextView(context).apply {

                    text =
                        "최근 90일간 방문 기록이 없습니다."

                    textSize =
                        14f

                    setTextColor(
                        android.graphics.Color.parseColor(
                            "#555555"
                        )
                    )

                    setPadding(
                        0,
                        8,
                        0,
                        8
                    )
                }


            container.addView(
                emptyView
            )

            return
        }


        // ======================================================
        // Top 5
        // ======================================================

        val top5 =
            cachedVisitRanking.take(5)


        // ======================================================
        // 기타
        //
        // Rank 자체가 아니라 전체 방문횟수의 합
        // ======================================================

        val otherCount =
            cachedVisitRanking
                .drop(5)
                .sumOf {
                    it.sumVisitCount
                }


        for ((index, item) in top5.withIndex()) {

            val rankNum =
                item.sumVisitRank
                    .takeIf {
                        it > 0
                    }
                    ?: (index + 1)


            val itemView =
                TextView(context).apply {

                    text =
                        "${rankNum}위(${item.sumVisitCount}회) : ${item.sumAdmName}"

                    textSize =
                        15f

                    setTextColor(
                        android.graphics.Color.parseColor(
                            "#222222"
                        )
                    )

                    setPadding(
                        0,
                        6,
                        0,
                        6
                    )
                }


            container.addView(
                itemView
            )
        }


        if (otherCount > 0) {

            val otherView =
                TextView(context).apply {

                    text =
                        "기타 (${otherCount}회 방문)"

                    textSize =
                        15f

                    setTextColor(
                        android.graphics.Color.parseColor(
                            "#777777"
                        )
                    )

                    setPadding(
                        0,
                        10,
                        0,
                        6
                    )
                }


            container.addView(
                otherView
            )
        }
    }


    // ==========================================================
    // 체류시간 랭킹
    //
    // Sum DB의 sumStayMinutes는 이미 "분"이다.
    // ==========================================================

    private fun 갱신체류랭킹() {

        val context =
            context ?: return

        val container =
            layoutStayTimeRankContainer
                ?: return


        container.removeAllViews()


        if (cachedStayRanking.isEmpty()) {

            val emptyView =
                TextView(context).apply {

                    text =
                        "체류 기록이 없습니다."

                    textSize =
                        14f

                    setTextColor(
                        android.graphics.Color.parseColor(
                            "#555555"
                        )
                    )

                    setPadding(
                        0,
                        8,
                        0,
                        8
                    )
                }


            container.addView(
                emptyView
            )

            return
        }


        // ======================================================
        // 전체 체류시간
        //
        // Sum DB는 분 단위
        // ======================================================

        val totalSumOfStayMinutes =
            cachedStayRanking.sumOf {

                it.sumStayMinutes
            }


        val top5 =
            cachedStayRanking.take(5)



        for ((index, item) in top5.withIndex()) {

            val rankNum =
                item.sumStayRank
                    .takeIf {
                        it > 0
                    }
                    ?: (index + 1)


            val totalMinutes =
                item.sumStayMinutes


            val hours =
                totalMinutes / 60L


            val minutes =
                totalMinutes % 60L


            val timeText =
                if (hours > 0L) {

                    "${hours}시간 ${minutes}분"

                } else {

                    "${minutes}분"
                }


            // --------------------------------------------------
            // 체류시간 비율
            // --------------------------------------------------

            val percentage =
                if (totalSumOfStayMinutes > 0L) {

                    (
                            item.sumStayMinutes.toDouble() /
                                    totalSumOfStayMinutes.toDouble()
                            ) * 100.0

                } else {

                    0.0
                }


            val percentText =
                String.format(
                    Locale.getDefault(),
                    "%.1f%%",
                    percentage
                )


            val itemView =
                TextView(context).apply {

                    text =
                        "${rankNum}위. ${item.sumAdmName}\n" +
                                "               전체 체류의  ${percentText} · ${timeText}"

                    textSize =
                        15f

                    setTextColor(
                        android.graphics.Color.parseColor(
                            "#222222"
                        )
                    )

                    setPadding(
                        0,
                        6,
                        0,
                        6
                    )
                }


            container.addView(
                itemView
            )
        }


    }


    // ==========================================================
    // 날짜 표시
    // ==========================================================

    private fun formatDate(
        timestamp: Long
    ): String {

        val sdf =
            SimpleDateFormat(
                "M월d일",
                Locale.KOREAN
            )

        return sdf.format(
            Date(timestamp)
        )
    }


    // ==========================================================
    // 오늘 00:00
    // ==========================================================

    private fun getTodayStartTimestamp(): Long {

        return Calendar
            .getInstance()
            .apply {

                set(
                    Calendar.HOUR_OF_DAY,
                    0
                )

                set(
                    Calendar.MINUTE,
                    0
                )

                set(
                    Calendar.SECOND,
                    0
                )

                set(
                    Calendar.MILLISECOND,
                    0
                )
            }
            .timeInMillis
    }


    // ==========================================================
    // 최근 90일 시작
    // ==========================================================

    private fun getThreeMonthsAgoTimestamp(): Long {

        return Calendar
            .getInstance()
            .apply {

                set(
                    Calendar.HOUR_OF_DAY,
                    0
                )

                set(
                    Calendar.MINUTE,
                    0
                )

                set(
                    Calendar.SECOND,
                    0
                )

                set(
                    Calendar.MILLISECOND,
                    0
                )

                add(
                    Calendar.DAY_OF_YEAR,
                    -90
                )
            }
            .timeInMillis
    }
}

