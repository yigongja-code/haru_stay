package com.haru.haru_stay.ui.view

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.haru.haru_stay.R
import com.haru.haru_stay.data.database.AppDatabase
import com.haru.haru_stay.service.ForegroundService
import com.haru.haru_stay.service.LogCollectService
import com.haru.haru_stay.ui.adapter.HomeTimelineItem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import com.haru.haru_stay.service.유틸.UserLocationInputHelper
import com.haru.haru_stay.service.유틸.SearchAdm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class HomeTimeAxisView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    //메모용 변수
    private val memoEmojiPaint = Paint().apply {
        textSize = 30f // 이모지 크기
        isAntiAlias = true
    }
    private val tickBarPaint = Paint().apply {
        color = 0xFF888888.toInt()
        strokeWidth = 4.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val tickPointPaint = Paint().apply {
        color = 0xFFCCCCCC.toInt()
        strokeWidth = 6.0f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val tickHourBarPaint = Paint().apply {
        color = 0xFF555555.toInt()
        strokeWidth = 4.5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val timeTextPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.gray_dark)
        textSize = 32f
        textAlign = Paint.Align.RIGHT
        isAntiAlias = true
    }


    //카드색상
    private val cardBgPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.card1) // 👈 테마 컬러로 교체!
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    //카드라인
    private val cardStrokePaint = Paint().apply {
        color = 0xFFB5B5B5.toInt()
        strokeWidth = 3.0f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    //카드색상
    private val 검색된카드색상 = Paint().apply {
        color = ContextCompat.getColor(context, R.color.검색된카드배경1)// 👈 테마 컬러로 교체!
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    //카드라인
    private val 검색된카드라인 = Paint().apply {
        color = 0xFFB5B5B5.toInt()
        strokeWidth = 3.0f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    // 🔍 [검색 모드 배너] 페인트 설정
    private val searchModeBgPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.검색된카드배경1)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val searchModeStrokePaint = Paint().apply {
        color = 0xFFB5B5B5.toInt()
        strokeWidth = 3.0f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val searchModeTextPaint = Paint().apply {
        color = 0xFF222222.toInt()
        textSize = 30f
        isAntiAlias = true
    }

    private val searchModeClosePaint = Paint().apply {
        color = 0xFFE53935.toInt() // X 표시는 눈에 띄게 포인트 레드 혹은 진한 회색
        textSize = 36f
        //isBoldText = true
        isAntiAlias = true
    }

    private val searchEpochs = mutableSetOf<Long>()

    // 🔍 외부(SearchAdm 등)에서 검색된 에폭 리스트를 던져줄 때 받는 함수
    fun setMatchedSearchEpochs(epochs: List<Long>) {
        searchEpochs.clear()
        searchEpochs.addAll(epochs)
        invalidate() // 💡 화면 새로고침!
    }


    private val cardTextTitlePaint = Paint().apply {
        color = 0xFF222222.toInt()
        textSize = 34f
        isAntiAlias = true
    }

    private val cardTextSubPaint = Paint().apply {
        color = 0xFF777777.toInt()
        textSize = 28f
        isAntiAlias = true
    }

    private var scaleFactorY = 1.0f
    private var currentTranslateY = 0.0f

    private val pixelsPerMinute = 3.0f
    private val rawDayHeight = 24 * 60 * pixelsPerMinute

    private val topPadding = 100f
    //날자 콘트롤 박스가 가리는 영역 밀어 커버
    private val bottomPadding = 300f
    private val baseCanvasHeight = rawDayHeight + topPadding + bottomPadding

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private var lastTouchX = 0.0f
    private var lastTouchY = 0.0f
    private var startTouchX = 0.0f
    private var startTouchY = 0.0f
    private var isDragging = false
    // 핀치 줌이 끝난 직후의 ACTION_UP을 일반 터치로 처리하지 않기 위한 플래그
    private var justFinishedScaling = false

    private var onDateSwipeListener: ((isNext: Boolean) -> Unit)? = null

    // 특정날짜를 받아오는 리스너
    private var onDateChangeListener: ((Long) -> Unit)? = null

    fun setOnDateChangeListener(listener: (Long) -> Unit) {
        onDateChangeListener = listener
    }

    //==============돋보기 아이콘 ======================
    // ==========================================
// 📌 [수정] 핀 아이콘 스타일 정의
// ==========================================
    // 📌 기존에 만드셨던 배경 페인트에 테두리(Stroke) 추가
    private val pinButtonBgPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt() // 버튼 배경 (하얀색)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pinButtonStrokePaint = Paint().apply {
        color = 0xFFCCCCCC.toInt() // 버튼 테두리 (은은한 회색)
        strokeWidth = 3.0f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val pinRedPaint = Paint().apply {
        color = 0xFFE53935.toInt() // 핀 머리/몸통 (강렬한 빨간색)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pinWhiteInnerPaint = Paint().apply {
        color = 0xFFFFFFFF.toInt() // 핀 가운데 하얀색 포인트 점
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    //사용자 위치 지정 끝

    private var isPinButtonAction = false // 👈 핀 버튼을 눌렀음을 기억하는 안전장치 플래그

    //카트 터치시 카드가 화면에 커짐
    private var onCardClickListener: ((HomeTimelineItem) -> Unit)? = null

    fun setOnCardClickListener(listener: (HomeTimelineItem) -> Unit) {
        onCardClickListener = listener
    }

    fun setOnDateSwipeListener(listener: (isNext: Boolean) -> Unit) {
        onDateSwipeListener = listener
    }

    private val timelineItems = mutableListOf<HomeTimelineItem>()

    init {
        isClickable = true
        isFocusable = true
        // 💡 [핵심 해결] 앱을 처음 켰을 때 12시(낮 12시 = 12시간 * 60분 = 720분) 지점이 보이도록 초기 스크롤 위치 강제 지정!
        // 12시의 분(minute) 위치 = 12 * 60 = 720분 * pixelsPerMinute
        val noonMinutes = 12 * 60f
        val noonY = noonMinutes * pixelsPerMinute + topPadding
        //프로그램 실행시 초기화면 12시 부근
        // 화면 높이가 아직 측정되기 전일 수 있으므로, 대략적인 화면 중앙이나 12시가 오프셋에 반영되도록 설정
        // (예: 12시 지점이 화면 상단에서 살짝 내려오도록 계산)
        currentTranslateY = -noonY + 300f // 300f는 적당한 여유 패딩
    }

    fun setItems(items: List<HomeTimelineItem>) {
        timelineItems.clear()
        timelineItems.addAll(items)
        invalidate()
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            isDragging = false
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val oldScaleY = scaleFactorY

            // 1. 새로운 스케일 계산 (범위 제한)
            scaleFactorY *= detector.scaleFactor
            scaleFactorY = max(0.4f, min(scaleFactorY, 6.0f))

            // 2. 💡 [핵심 보정] 두 손가락의 중심점(focusY)을 기준으로 화면이 튀지 않게 스무스하게 확대/축소
            val focusY = detector.focusY
            currentTranslateY = focusY - (focusY - currentTranslateY) * (scaleFactorY / oldScaleY)

            // 3. 스크롤 범위를 벗어나지 않도록 클램핑
            val scaledH = baseCanvasHeight * scaleFactorY
            val maxScrollY = max(0f, scaledH - height)
            currentTranslateY = max(-maxScrollY, min(currentTranslateY, 0f))

            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            // 💡 줌이 끝났을 때 좌표를 강제로 0으로 초기화하면 튀므로,
            // 여기서는 아무것도 건드리지 않고 플래그만 안전하게 관리합니다.
            isScaling = false
            isDragging = false

            // 💡 줌이 완전히 끝난 시각을 기록하여 0.5초 동안의 오작동 터치를 방어합니다.
            lastScaleEndTime = System.currentTimeMillis()
        }
    }

    // 💡 줌 제스처 상태를 명확히 추적할 플래그 추가
    private var isScaling = false
    private var lastScaleEndTime = 0L // 👈 추가

    private var isMultiTouchOccurred = false

    private var searchKeyword: String = ""

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // 화면에 다시 돌아왔을 때, 검색어가 비어있는데 잔재가 남아있다면 깔끔하게 청소!
        if (searchKeyword.isEmpty()) {
            SearchAdm.destroyDialog()
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        val pointerCount = event.pointerCount

        // 1. 두 손가락 이상이 닿았거나 줌 제스처가 진행 중이면 멀티터치 플래그 활성화
        if (pointerCount > 1 || scaleDetector.isInProgress) {
            isMultiTouchOccurred = true
            isDragging = false
        }

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                // 새로운 단일 터치가 시작될 때만 플래그 초기화
                isMultiTouchOccurred = false
                isScaling = false

                //==============================================
                // 🔍 [검색 모드 배너]가 켜져 있을 때 터치 영역 판정
                //==============================================
                // 1. [검색 모드 배너] 터치 판정
                if (searchKeyword.isNotEmpty()) {
                    val boxRight = width - 30f
                    val boxBottom = height - 770f
                    val boxLeft = boxRight - 150f
                    val boxTop = boxBottom - 130f

                    if (event.x in boxLeft..boxRight && event.y in boxTop..boxBottom) {
                        isPinButtonAction = true
                        searchKeyword = ""
                        searchEpochs.clear()
                        SearchAdm.destroyDialog()
                        invalidate()
                    }
                }

                // 2. [위치 검색 돋보기 버튼] 터치 판정
                val searchCenterX = width - 100f
                val searchCenterY = height - 660f
                val buttonSearchRadius = 75f
                val dxSearch = event.x - searchCenterX
                val dySearch = event.y - searchCenterY

                if (dxSearch * dxSearch + dySearch * dySearch <= buttonSearchRadius * buttonSearchRadius) {
                    isPinButtonAction = true
                    SearchAdm.showSearchDialog(
                        context,
                        onTimeSelected = { selectedStartTime -> scrollToTime(selectedStartTime) },
                        onSearchMatched = { epochs -> setMatchedSearchEpochs(epochs) },
                        onKeywordSubmitted = { keyword -> searchKeyword = keyword }
                    )
                }

                // 3. [즉시 수집 버튼] 터치 판정 (기존 핀 위치가 위로 올라간 영역)
                val collectCenterX = width - 100f
                val collectCenterY = height - 430f
                val collectTouchRadius = 75f
                val dxCollect = event.x - collectCenterX
                val dyCollect = event.y - collectCenterY

                if (dxCollect * dxCollect + dyCollect * dyCollect <= collectTouchRadius * collectTouchRadius) {
                    isPinButtonAction = true

                    CoroutineScope(Dispatchers.Main).launch {

                        val db = AppDatabase.getDatabase(context)

                        // 가장 최근 로그의 시작시간만 가져옴
                        val latestStartTime =
                            withContext(Dispatchers.IO) {
                                db.logDao().가장최근의로그시작시간가져옴()
                            }

                        val now = System.currentTimeMillis()

                        // 최근 로그가 있고, 시작 후 60초 이내라면 즉시수집 차단
                        if (latestStartTime != null) {

                            val nextCollectTime = latestStartTime + 60_000L

                            if (now < nextCollectTime) {

                                val remainSeconds =
                                    ((nextCollectTime - now) / 1_000L).coerceAtLeast(1L)

                                Toast.makeText(
                                    context,
                                    "약 ${remainSeconds}초 후 수집 가능합니다.",
                                    Toast.LENGTH_SHORT
                                ).show()

                                return@launch
                            }
                        }

                        // 즉시수집 실행
                        Toast.makeText(
                            context,
                            "즉시 수집 요청됨",
                            Toast.LENGTH_SHORT
                        ).show()

                        // 포그라운드 서비스 다시 깨우기
                        context.startForegroundService(
                            Intent(context, ForegroundService::class.java).apply {
                                action = "ACTION_START"
                            }
                        )

                        val intent =
                            Intent(context, LogCollectService::class.java).apply {
                                action = "ACTION_COLLECT"
                            }

                        context.startService(intent)
                    }

                    return true
                }

                // 4. [즐겨찾기 추가 버튼] 터치 판정 (최하단 별 버튼 영역)
                val starCenterX = width - 100f
                val starCenterY = height - 200f
                val starTouchRadius = 75f
                val dxStar = event.x - starCenterX
                val dyStar = event.y - starCenterY

                if (dxStar * dxStar + dyStar * dyStar <= starTouchRadius * starTouchRadius) {
                    isPinButtonAction = true
                    UserLocationInputHelper.saveUserLocation(context)
                }

                //return true




                // --- 👇 이하 기존에 있던 원래 드래그 시작 좌표 기록부 그대로 유지 ---
                startTouchX = event.x
                startTouchY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                // 💡 멀티터치 과정이었거나 줌 중이라면 드래그 이동 계산을 아예 수행하지 않음
                if (isMultiTouchOccurred || scaleDetector.isInProgress) return true

                val dx = abs(event.x - lastTouchX)
                val dy = abs(event.y - lastTouchY)
                if (dx > 5f || dy > 5f) {
                    isDragging = true
                }

                if (isDragging) {
                    val dyMove = event.y - lastTouchY
                    currentTranslateY += dyMove

                    val scaledH = baseCanvasHeight * scaleFactorY
                    val maxScrollY = max(0f, scaledH - height)
                    currentTranslateY = max(-maxScrollY, min(currentTranslateY, 0f))

                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // 💡 두 손가락 중 첫 번째 손가락이 떨어지는 순간 (아직 손가락이 하나 남아있음)
                // 이때 남은 손가락 좌표로 화면이 튀는 것을 막기 위해 멀티터치 상태를 유지합니다.
                isMultiTouchOccurred = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 💡 [핵심 방어] 줌 동작이 끝난 지 0.5초(500ms) 이내에 손을 떼서 발생한 UP 이벤트라면 터치(카드 클릭)로 새어나가지 않게 즉시 차단!
                if (System.currentTimeMillis() - lastScaleEndTime < 500L) {
                    isMultiTouchOccurred = false
                    isScaling = false
                    isDragging = false
                    lastTouchX = 0f
                    lastTouchY = 0f
                    startTouchX = 0f
                    return true
                }

                // 💡 [안전장치 추가] 만약 핀 버튼을 누른 거였다면, 손을 뗄 때 스와이프나 다른 동작이 절대 실행되지 않고 즉시 종료!
                if (isPinButtonAction) {
                    isPinButtonAction = false
                    isMultiTouchOccurred = false
                    isScaling = false
                    isDragging = false
                    return true
                }

                // 2. 순수 단일 터치 드래그였다면 좌우 스와이프 판단
                if (isDragging) {
                    val totalDx = event.x - startTouchX
                    val totalDy = event.y - startTouchY // 📌 세로 이동 거리 추가 측정

                    val swipeThreshold = 350f           // 기준 거리를 살짝 넉넉하게 (350f)

                    // 💡 핵심: 가로 이동이 임계각을 넘었고, 세로 흔들림보다 최소 2배 이상 확실하게 가로로 밀었을 때만 작동!
                    val isHorizontalSwipe = abs(totalDx) > swipeThreshold && abs(totalDx) > abs(totalDy) * 2.0f

                    if (isHorizontalSwipe) {
                        if (totalDx > 0) {
                            onDateSwipeListener?.invoke(false) // 전날로 (왼 -> 오)
                        } else {
                            onDateSwipeListener?.invoke(true)  // 다음날로 (오 -> 왼)
                        }
                    }
                } else {
                    // 3. 순수 단일 탭이었다면 카드 팝업 띄우기
                    val canvasY = (event.y - currentTranslateY) - topPadding
                    val currentPixelsPerMinute = pixelsPerMinute * scaleFactorY
                    val totalMinutes = canvasY / currentPixelsPerMinute

                    val clickedItem = timelineItems.find { item ->
                        val startMin = parseTimeToMinutes(item.startTime)
                        var endMin = parseTimeToMinutes(item.endTime)
                        if (endMin <= startMin) endMin += 24 * 60f

                        totalMinutes in startMin..endMin
                    }

                    if (clickedItem != null) {
                        onCardClickListener?.invoke(clickedItem)
                    }
                }

                // 상태 초기화
                isMultiTouchOccurred = false
                isScaling = false
                isDragging = false
                lastTouchX = 0f
                lastTouchY = 0f
                startTouchX = 0f
            }
        }
        return true
    }




    // 💡 새로운 날짜 데이터가 들어올 때 정오(12:00)가 화면 중앙에 오도록 스크롤을 맞춰주는 함수
    // 💡 정오(12시)를 화면 중앙에 맞추고, 하루 전체가 한눈에 보이도록 최소 비율로 축소하는 함수
    fun scrollToNoon() {
        // 1. 화면 전체가 다 보이도록 최소 축소 배율(0.5f) 강제 적용
        scaleFactorY = 0.4f

        val currentPixelsPerMinute = pixelsPerMinute * scaleFactorY

        // 정오(12시)까지의 총 분 = 12시간 * 60분 = 720분
        val noonMinutes = 12 * 60f
        val noonY = topPadding + (noonMinutes * currentPixelsPerMinute)

        // 정오 위치가 뷰의 정중앙에 오도록 translateY 계산 (화면 높이의 절반 - 정오의 Y 좌표)
        currentTranslateY = (height / 2f) - noonY

        // 스크롤 범위를 벗어나지 않도록 안전하게 클램핑
        val scaledH = baseCanvasHeight * scaleFactorY
        val maxScrollY = max(0f, scaledH - height)
        currentTranslateY = max(-maxScrollY, min(currentTranslateY, 0f))

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val currentPixelsPerMinute = pixelsPerMinute * scaleFactorY
        val hourHeight = 60f * currentPixelsPerMinute

        // ==========================================
        // 1. [좌측 시간 축 영역]
        // ==========================================
        canvas.save()
        canvas.translate(0f, currentTranslateY)

        for (hour in 0..24) {
            val hourY = topPadding + (hour * hourHeight)

            for (m in 0..60 step 30) {
                if (hour == 24 && m > 0) continue
                if (m == 60 && hour == 24) continue
                val y = hourY + (m * currentPixelsPerMinute)

                canvas.drawLine(140f, y, 160f, y, tickBarPaint)

                if (m == 0) {
                    canvas.drawLine(132f, y, 160f, y, tickHourBarPaint)
                }
            }

            for (m in 0..60 step 10) {
                if (hour == 24) continue
                if (m == 0 || m == 30 || m == 60) continue
                val y = hourY + (m * currentPixelsPerMinute)
                canvas.drawPoint(150f, y, tickPointPaint)
            }

            // 💡 [수정] 24시간(0시~24시) 기준으로 좌측 축에 12시간제(AM/PM) 텍스트를 정확하게 매핑
            val timeStr = when (hour) {
                0 -> "AM12"
                24 -> "PM24"
                in 1..9 -> "AM0${hour}"
                in 10..11 -> "AM${hour}"
                12 -> "PM12"
                in 13..21 -> "PM0${hour - 12}"
                else -> "PM${hour - 12}"
            }

            canvas.drawText(timeStr, 120f, hourY + 12f, timeTextPaint)
        }

        canvas.restore()

        // ==========================================
        // 2. [우측 박스 영역]
        // ==========================================
        canvas.save()
        canvas.translate(0f, currentTranslateY)



        // 2. 전달받은 타임라인 아이템들을 순회하며 카드(끝단 포함)를 그림
        for (item in timelineItems) {
            val startMinutes = parseTimeToMinutes(item.startTime)
            var endMinutes = parseTimeToMinutes(item.endTime)

            // 자정을 넘어가는 경우 처리 (예: 23:00 ~ 01:00)
            if (endMinutes <= startMinutes) {
                endMinutes += 24 * 60f
                //endMinutes = startMinutes + (15 * 60f)
            }


            val cardTopY = topPadding + (startMinutes * currentPixelsPerMinute)
            val cardBottomY = topPadding + (endMinutes * currentPixelsPerMinute)

            val cardLeft = 180f
            val cardRight = width - 40f
            val cardBottom = max(cardBottomY, cardTopY + 40f)

            if (cardRight > cardLeft + 50f) {
                val rect = RectF(cardLeft, cardTopY, cardRight, cardBottom)

                // onDraw 내부의 카드 그리는 곳: 검색된시작시간리스트(epochs)


// 💡 현재 그리고 있는 카드의 시작 에폭(item.startEpoch)이 검색된 리스트(searchEpochs)에 포함되어 있는가?
                //  제목을 조건으로
                if (searchKeyword.isNotEmpty() && item.adm.contains(searchKeyword)) {
                // 버스리스트를 조건으로
                //if (searchKeyword.isNotEmpty() && item.rawBusStop.contains(searchKeyword)) {
                        canvas.drawRoundRect(rect, 16f, 16f, 검색된카드색상)
                        canvas.drawRoundRect(rect, 16f, 16f, 검색된카드라인)
                    } else {
                        canvas.drawRoundRect(rect, 16f, 16f, cardBgPaint)
                        canvas.drawRoundRect(rect, 16f, 16f, cardStrokePaint)
                    }


                val boxHeight = cardBottom - cardTopY
                val availableWidth = cardRight - cardLeft - 48f

                //===================================
                //          메모icon배치
                //===================================

                // 💡 메모가 존재한다면 카드 우상단에 말풍선(💬) 콕 박기!
                if (item.memo > 0) {
                    val emojiX = cardRight - 140f // 카드 우측 끝에서 안쪽으로 살짝 이동
                    val emojiY = cardTopY + 30f  // 카드 상단에서 아래로 살짝 이동
                    canvas.drawText("\uD83D\uDCDDmemo", emojiX, emojiY, memoEmojiPaint)
                }


                // 📌 1. 와이파이 문자열 파싱 (괄호 안쪽만 쏙 빼내는 가장 깔끔한 로직)
                val rawWifi = item.wifiMac
                val cleanedWifi =
                    if (!rawWifi.isNullOrEmpty() && rawWifi.contains("(") && rawWifi.endsWith(")")) {
                        rawWifi.substringAfterLast("(").removeSuffix(")").trim()
                    } else if (rawWifi.isNullOrEmpty()) {
                        "No Signal"
                    } else {
                        rawWifi.trim()
                    }

                // 📌 2. 뷰에 뿌려줄 5가지 라인 텍스트 구성
                // 📌 2. 뷰에 뿌려줄 5가지 라인 텍스트 구성
                // 💡 spotTimeMinutes가 0 이하(최신 위치 등)면 장소 이름만, 아니면 체류 시간 포함해서 safeTitle에 대입!
                // 💡 이 부분에서 item.adm에 체류 시간을 붙여서 타이틀을 만들고 있죠!
                //?val formattedDuration = formatStayDuration(item.spotTimeMinutes)

                // 📌 2. 뷰에 뿌려줄 5가지 라인 텍스트 구성 (체류 시간 제거 버전)
                // 📌 2. 뷰에 뿌려줄 5가지 라인 텍스트 구성 (타이틀 오른쪽에 첫 번째 정류장 결합)
                // 📌 2. 뷰에 뿌려줄 5가지 라인 텍스트 구성 (타이틀 뒤에 괄호 없이 정류장명과 '인근' 붙이기)
                val firstBusStop = if (!item.busStop.isNullOrBlank()) {
                    item.busStop.split("|").firstOrNull() ?: ""
                } else {
                    ""
                }

                //주소+대표경로 합침
                val stopText = item.rawBusStop ?: "대표장소 없음"
                //주소와 대표이동 경로를 하나로 합침
                val rawTitleText = if (stopText.isNotBlank()) {
                    //"${item.adm} $stopText"
                    "${item.adm}"
                } else {
                    item.adm
                }

                val safeTitle = getEllipsizedText(
                    rawTitleText,
                    cardTextTitlePaint,
                    availableWidth
                )


                val safeTimeRange = getEllipsizedText(item.timeRangeStr, cardTextSubPaint, availableWidth)
                val safeBusCoord = getEllipsizedText(
                    "${item.busStop.ifBlank { "없음" }} - (${item.lat}, ${item.lon})",
                    cardTextSubPaint,
                    availableWidth
                )
                val safeNetwork = getEllipsizedText(
                    "${item.cellKey.ifBlank { "없음" }} - $cleanedWifi",
                    cardTextSubPaint,
                    availableWidth
                )
                val safeStatus = getEllipsizedText("상태: ${item.stats}", cardTextSubPaint, availableWidth)
                //======================================================
                // 💡 item.busStopDetailText를 줄바꿈(\n) 기준로 쪼개기


                // 📌 1. 칼각 X 좌표 기준선 설정
                //그래서 기본 칸 높이인 45px 기준을 뼈대로 삼고, 버스정류장 리스트처럼 데이터가 줄줄이 길게 붙을 때는
                // 그 50%의 여유 공간(또는 확장된 영역) 안에서 자연스럽게 아래로 뻗어 나가게 두면 화면이 터지지 않고 아주 안정적으로 정돈되겠네요!
                //글자 크기는 45px?
                val labelX = cardLeft + 24f
                val valueX = cardLeft + 150f // 값이 시작될 완벽한 세로 기준선!

// 📌 2. 항목별 레이블과 값 정의
                val stayLabel = "체류시간 :"
                val stayValue = getEllipsizedText(formatStayDuration(item.spotTimeMinutes), cardTextSubPaint, availableWidth - 150f)

                val timeLabel = "진행시간 :"
                val timeValue = getEllipsizedText(item.timeRangeStr, cardTextSubPaint, availableWidth - 150f)

                val busLabel = "대표장소 :"
                val busValue = getEllipsizedText(stopText, cardTextSubPaint, availableWidth - 150f)
                // 💡 기존의 "${item.busStop...} (${item.lat}, ${item.lon})" 부분을 통째로 빼고 busStopDetailText 대입!
                //val busValue = getEllipsizedText(item.rawBusStop.ifBlank { "없음" },cardTextSubPaint,availableWidth - 150f)
                //val stopText = item.rawBusStop ?: "대표장소 없음"
                val netLabel = "네트워크 :"
                val netValue = getEllipsizedText("${item.cellKey.ifBlank { "없음" }} - $cleanedWifi", cardTextSubPaint, availableWidth - 150f)

                val statLabel = "상    태  :"
                val statValue = getEllipsizedText(item.stats, cardTextSubPaint, availableWidth - 150f)

                val sumVisitCountText = "방문횟수 : ${item.sumVisitCount}"
                val sumVisitRankText = "방문랭킹 : ${item.sumVisitRank}"

                val sumStayMinutesText = "총 체류 : ${item.sumStayMinutes}"
                val sumStayRankText = "체류랭킹 : ${item.sumStayRank}"

                val sumLastTimeText = "마지막 방문 : ${item.sumLastTime}"
                val sumDayAgoText = "마지막 방문 : ${item.sumDayAgo}"

                // 📌 1. 우측 정렬용 Title Paint 설정 (기존 paint를 복사하거나 따로 정의)
                // 📌 1. 우측 정렬 및 흐린 회색 글자색이 적용된 Title Paint 설정
                val cardTextRightTitlePaint = Paint(cardTextTitlePaint).apply {
                    textAlign = Paint.Align.RIGHT
                    color = 0xFF999999.toInt() // 흐린 회색 (원하시는 밝기에 따라 888888 ~ AAAAAA 조절 가능)
                }
                val rightAlignX = cardRight - 24f

                val sumLastVisitText = when (item.sumDayAgo) {
                    0 -> "오늘 방문한 지역"
                    1 -> "마지막 방문은 어제, ${item.sumLastTime}입니다."
                    else -> "마지막 방문은 ${item.sumDayAgo}일 전, ${item.sumLastTime}입니다."
                }
                val sumVisitRankLabel = "방문 순위 :"
                val sumVisitRankValue =
                    if (item.sumVisitRank in 1..9) "${item.sumVisitRank}위" else "순위 외"

                val sumVisitCountLabel = "방문 횟수 :"
                val sumVisitCountValue = "${item.sumVisitCount}회"

                val sumStayRankLabel = "체류 순위 :"
                val sumStayRankValue =
                    if (item.sumStayRank in 1..9) "${item.sumStayRank}위" else "순위 외"

                val sumStayMinutesLabel = "체류 시간 :"
                val sumStayMinutesValue = "${item.sumStayMinutes}"


// 📌 3. 박스 높이에 따라 칼각 표 형태로 렌더링

                // 통계 제목
                //val statTitleY = cardTopY + 205f + aa

// 통계 본문
                //val statRow1Y = cardTopY + 250f + aa
                //val statRow2Y = cardTopY + 282f + aa
                //val statRow3Y = cardTopY + 314f + aa

                // 통게 : 왼쪽 영역 레이블
                val statLabelX = labelX

                // 통계 : 왼쪽 영역 값
                val statValueX = valueX + 10f

                // 통계 : 오른쪽 영역 레이블    cardRight : 카드의 오른쪽 외곽선임 그래서 - 해야 왼쪽으로 이동함
                val statRightLabelX = cardRight - 500f
                // 통계 : 오른쪽 영역 값
                val statRightValueX = cardRight - 350f

                // aa값 박스내 정보의 전체적인 위치보정 // 위쪽여백
                val aa = 50f
                val 한줄간격 = 30f
                // val 아래쪽여백 = 0f

                //when 조건을 줄수에 따라 계산
                fun 필요한높이(줄수: Int): Float {
                    return aa + (한줄간격 * 줄수)
                }

                when {
                    //정보를 꽉채운 카드의 크기
                    boxHeight > 필요한높이(9) + 한줄간격 + 50f -> {  //한줄에배정된간격 - 통계창 위에 한줄여백 추가 +  우하단 주소 여백
                        var lineY = cardTopY

                        //타이틀 : 주소
                        canvas.drawText(safeTitle, labelX, lineY + aa, cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //대표장소 : 버정
                        canvas.drawText(busLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(busValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //진행시간 : 시작시간 : 종료시간  (날짜)
                        canvas.drawText(timeLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(timeValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //체류시간
                        canvas.drawText(stayLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(stayValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += (한줄간격 * 2)  // 통계 위쪽에 공간을 더 벌림
                        //===================  줄바꿈  ====================

                        //통게타이틀
                        canvas.drawText("----- 최근 3개월 합계 -----",statValueX-50f,lineY + aa,cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        // 마지막방문은  xx일전 , 0월 0일입니다.
                        canvas.drawText(sumLastVisitText,statLabelX,lineY + aa,cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //방문횟수
                        canvas.drawText(sumVisitCountLabel,statLabelX,lineY + aa,cardTextSubPaint)
                        canvas.drawText(sumVisitCountValue,statValueX,lineY + aa,cardTextSubPaint)
                        //방문순위
                        canvas.drawText(sumVisitRankLabel,statRightLabelX,lineY + aa,cardTextSubPaint)
                        canvas.drawText(sumVisitRankValue,statRightValueX,lineY + aa,cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //체류시간
                        canvas.drawText(sumStayMinutesLabel,statLabelX,lineY + aa,cardTextSubPaint)
                        canvas.drawText(sumStayMinutesValue,statValueX,lineY + aa,cardTextSubPaint)
                        //체류순위
                        canvas.drawText(sumStayRankLabel,statRightLabelX,lineY + aa,cardTextSubPaint)
                        canvas.drawText(sumStayRankValue,statRightValueX,lineY + aa,cardTextSubPaint)
                        //===================  정보끝  ====================

                        //마지막 우하단 주소
                        canvas.drawText(safeTitle, rightAlignX, cardBottomY - 50f, cardTextRightTitlePaint)
                    }

                    boxHeight > 필요한높이(8) + 한줄간격 -> {  //통계창위 여백 한줄 +
                        var lineY = cardTopY

                        //타이틀 : 주소
                        canvas.drawText(safeTitle, labelX, lineY + aa, cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //대표장소 : 버정
                        canvas.drawText(busLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(busValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //진행시간 : 시작시간 : 종료시간  (날짜)
                        canvas.drawText(timeLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(timeValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //체류시간
                        canvas.drawText(stayLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(stayValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += (한줄간격 * 2)   // 통계 위쪽에 공간을 더 벌림
                        //===================  줄바꿈  ====================

                        //통게타이틀
                        canvas.drawText("----- 최근 3개월 합계 -----",statValueX-50f,lineY + aa,cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        // 마지막방문은  xx일전 , 0월 0일입니다.
                        canvas.drawText(sumLastVisitText,statLabelX,lineY + aa,cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //방문횟수
                        canvas.drawText(sumVisitCountLabel,statLabelX,lineY + aa,cardTextSubPaint)
                        canvas.drawText(sumVisitCountValue,statValueX,lineY + aa,cardTextSubPaint)
                        //방문순위
                        canvas.drawText(sumVisitRankLabel,statRightLabelX,lineY + aa,cardTextSubPaint)
                        canvas.drawText(sumVisitRankValue,statRightValueX,lineY + aa,cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //체류시간
                        canvas.drawText(sumStayMinutesLabel,statLabelX,lineY + aa,cardTextSubPaint)
                        canvas.drawText(sumStayMinutesValue,statValueX,lineY + aa,cardTextSubPaint)
                        //체류순위
                        canvas.drawText(sumStayRankLabel,statRightLabelX,lineY + aa,cardTextSubPaint)
                        canvas.drawText(sumStayRankValue,statRightValueX,lineY + aa,cardTextSubPaint)

                    }

                    boxHeight > 필요한높이(7) + 한줄간격 -> {  //통계창위 여백 한줄 +

                        var lineY = cardTopY

                        //타이틀 : 주소
                        canvas.drawText(safeTitle, labelX, lineY + aa, cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //대표장소 : 버정
                        canvas.drawText(busLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(busValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //진행시간 : 시작시간 : 종료시간  (날짜)
                        canvas.drawText(timeLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(timeValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //체류시간
                        canvas.drawText(stayLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(stayValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += (한줄간격 * 2)   // 통계 위쪽에 공간을 더 벌림
                        //===================  줄바꿈  ====================

                        //통게타이틀
                        canvas.drawText("----- 최근 3개월 합계 -----",statValueX-50f,lineY + aa,cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        // 마지막방문은  xx일전 , 0월 0일입니다.
                        canvas.drawText(sumLastVisitText,statLabelX,lineY + aa,cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //방문횟수
                        canvas.drawText(sumVisitCountLabel,statLabelX,lineY + aa,cardTextSubPaint)
                        canvas.drawText(sumVisitCountValue,statValueX,lineY + aa,cardTextSubPaint)
                        //방문순위
                        canvas.drawText(sumVisitRankLabel,statRightLabelX,lineY + aa,cardTextSubPaint)
                        canvas.drawText(sumVisitRankValue,statRightValueX,lineY + aa,cardTextSubPaint)

                    }

                    boxHeight > 필요한높이(6) + 한줄간격 -> {  //통계창위 여백 한줄 +

                        var lineY = cardTopY

                        //타이틀 : 주소
                        canvas.drawText(safeTitle, labelX, lineY + aa, cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //대표장소 : 버정
                        canvas.drawText(busLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(busValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //진행시간 : 시작시간 : 종료시간  (날짜)
                        canvas.drawText(timeLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(timeValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //체류시간
                        canvas.drawText(stayLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(stayValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += (한줄간격 * 2)   // 통계 위쪽에 공간을 더 벌림
                        //===================  줄바꿈  ====================

                        //통게타이틀
                        canvas.drawText("----- 최근 3개월 합계 -----",statValueX-50f,lineY + aa,cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        // 마지막방문은  xx일전 , 0월 0일입니다.
                        canvas.drawText(sumLastVisitText,statLabelX,lineY + aa,cardTextSubPaint)

                    }

                    boxHeight > 필요한높이(5) + 한줄간격 -> {  //통계창위 여백 한줄 +

                        var lineY = cardTopY

                        //타이틀 : 주소
                        canvas.drawText(safeTitle, labelX, lineY + aa, cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //대표장소 : 버정
                        canvas.drawText(busLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(busValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //진행시간 : 시작시간 : 종료시간  (날짜)
                        canvas.drawText(timeLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(timeValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //체류시간
                        canvas.drawText(stayLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(stayValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += (한줄간격 * 2)   // 통계 위쪽에 공간을 더 벌림
                        //===================  줄바꿈  ====================

                        //통게타이틀
                        canvas.drawText("----- 최근 3개월 합계 -----",statValueX-50f,lineY + aa,cardTextTitlePaint)

                    }
                    boxHeight > 필요한높이(4)   -> {  //통계창위 여백 한줄 없어짐

                        var lineY = cardTopY

                        //타이틀 : 주소
                        canvas.drawText(safeTitle, labelX, lineY + aa, cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //대표장소 : 버정
                        canvas.drawText(busLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(busValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //진행시간 : 시작시간 : 종료시간  (날짜)
                        canvas.drawText(timeLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(timeValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //체류시간
                        canvas.drawText(stayLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(stayValue, valueX, lineY + aa, cardTextSubPaint)


                    }
                    boxHeight > 필요한높이(3)   -> {  //통계창위 여백 한줄 없어짐

                        var lineY = cardTopY

                        //타이틀 : 주소
                        canvas.drawText(safeTitle, labelX, lineY + aa, cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //대표장소 : 버정
                        canvas.drawText(busLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(busValue, valueX, lineY + aa, cardTextSubPaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //진행시간 : 시작시간 : 종료시간  (날짜)
                        canvas.drawText(timeLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(timeValue, valueX, lineY + aa, cardTextSubPaint)

                    }
                    boxHeight > 필요한높이(2)   -> {  //통계창위 여백 한줄 없어짐

                        var lineY = cardTopY

                        //타이틀 : 주소
                        canvas.drawText(safeTitle, labelX, lineY + aa, cardTextTitlePaint)
                        lineY += 한줄간격
                        //===================  줄바꿈  ====================

                        //대표장소 : 버정
                        canvas.drawText(busLabel, labelX, lineY + aa, cardTextSubPaint)
                        canvas.drawText(busValue, valueX, lineY + aa, cardTextSubPaint)

                    }
                    boxHeight > 필요한높이(1)   -> {  //통계창위 여백 한줄 없어짐

                        var lineY = cardTopY

                        //타이틀 : 주소
                        canvas.drawText(safeTitle, labelX, lineY + aa, cardTextTitlePaint)

                    }
                    else -> {
                        var lineY = cardTopY

                        //타이틀 : 주소 y값 34는 타이틀 텍스트 크기
                        canvas.drawText(safeTitle, labelX, lineY + 34f , cardTextTitlePaint)
                    }
                }
            }
        }

        // ==========================================
        // 2. [우측 박스 영역 끝]
        // ==========================================


        canvas.restore() // (우측 박스 영역의 canvas.restore는 여기서 정상적으로 닫힙니다)
        // ==========================================
        // 🔍 1. [검색 모드 배너] 렌더링
        // ==========================================
        if (searchKeyword.isNotEmpty()) {
            val boxRight = width - 30f
            val boxBottom = height - 770f
            val boxLeft = boxRight - 140f
            val boxTop = boxBottom - 120f

            val searchModeRect = RectF(boxLeft, boxTop, boxRight, boxBottom)
            canvas.drawRoundRect(searchModeRect, 16f, 16f, searchModeBgPaint)
            canvas.drawRoundRect(searchModeRect, 16f, 16f, searchModeStrokePaint)

            val textPaintForBox = Paint(searchModeTextPaint).apply {
                textSize = 35f
                textAlign = Paint.Align.CENTER
            }

            val centerX = (boxLeft + boxRight) / 2f
            canvas.drawText("검 색", centerX, boxTop + 55f, textPaintForBox)
            canvas.drawText("모 드", centerX, boxTop + 100f, textPaintForBox)
            canvas.drawText("x", centerX + 50, boxTop + 30f, textPaintForBox)
        }

        // ==========================================
        // 🔍 2. [위치 검색 돋보기 버튼] (Y: height - 660f)
        // ==========================================
        val searchCenterX = width - 100f
        val searchCenterY = height - 660f
        val buttonSearchRadius = 75f

        canvas.drawCircle(searchCenterX, searchCenterY - 5f, buttonSearchRadius, pinButtonBgPaint)
        canvas.drawCircle(searchCenterX, searchCenterY - 5f, buttonSearchRadius, pinButtonStrokePaint)

        val searchStrokePaint = Paint().apply {
            color = 0xFF5A738E.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 8f
            isAntiAlias = true
        }
        val handlePaint = Paint().apply {
            color = 0xFF5A738E.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 15f
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
        }

        val searchRadius = 35f
        canvas.drawCircle(searchCenterX - 5f, searchCenterY - 15f, searchRadius, searchStrokePaint)

        val handlePath = android.graphics.Path().apply {
            val startAngle = 15f.toDouble()
            val startX = searchCenterX + searchRadius - 17f * Math.cos(Math.toRadians(startAngle)).toFloat()
            val startY = searchCenterY + searchRadius - 58f * Math.sin(Math.toRadians(startAngle)).toFloat()

            val endX = startX + 15f
            val endY = startY + 20f

            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        canvas.drawPath(handlePath, handlePaint)

        val searchPinTextPaint = Paint().apply {
            color = 0xFF555555.toInt()
            textSize = 35f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("위치 검색", searchCenterX, searchCenterY + 100f, searchPinTextPaint)

        // ==========================================
        // 📌 3. [즉시 수집 버튼] (기존 핀 아이콘 이관, Y: height - 430f)
        // ==========================================
        val collectCenterX = width - 100f
        val collectCenterY = height - 430f
        val collectRadius = 75f

        val collectColorPaint = Paint().apply {
            color = 0xFF5A738E.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        canvas.drawCircle(collectCenterX, collectCenterY - 5f, collectRadius, pinButtonBgPaint)
        canvas.drawCircle(collectCenterX, collectCenterY - 5f, collectRadius, pinButtonStrokePaint)

        val collectPath = android.graphics.Path().apply {
            val halfW = 38f
            val sharpH = 50f
            moveTo(collectCenterX - halfW, collectCenterY - 20f)
            lineTo(collectCenterX + halfW, collectCenterY - 20f)
            lineTo(collectCenterX, collectCenterY + sharpH)
            close()
        }
        canvas.drawPath(collectPath, collectColorPaint)

        val headR = 38f
        val headCY = collectCenterY - 20f
        canvas.drawCircle(collectCenterX, headCY, headR, collectColorPaint)

        val collectPlusPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 10f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        val pSize = 14f
        canvas.drawLine(collectCenterX - pSize, headCY, collectCenterX + pSize, headCY, collectPlusPaint)
        canvas.drawLine(collectCenterX, headCY - pSize, collectCenterX, headCY + pSize, collectPlusPaint)

        val collectTextPaint = Paint().apply {
            color = 0xFF555555.toInt()
            textSize = 35f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        canvas.drawText("현위치 기록", collectCenterX, collectCenterY + 100f, collectTextPaint)

        // ==========================================
        // ⭐ 4. [즐겨찾기 추가 버튼] (원 안에 큼직하게 꽉 차는 동글동글 라운드 별, Y: height - 200f)
        // ==========================================
        val starCenterX = width - 100f
        val starCenterY = height - 200f
        val starButtonRadius = 75f

        // 1. 버튼 배경 및 테두리 원
        canvas.drawCircle(starCenterX, starCenterY - 5f, starButtonRadius, pinButtonBgPaint)
        canvas.drawCircle(starCenterX, starCenterY - 5f, starButtonRadius, pinButtonStrokePaint)

        // 2. 버튼 크기에 맞게 시원하게 꽉 차도록 바깥쪽 반지름(outerRadius)을 58f로 확 키움!
        val starPath = android.graphics.Path().apply {
            val numPoints = 5
            val outerRadius = 58f
            val innerRadius = 27f
            val angleStep = Math.PI / numPoints

            val points = Array(10) { FloatArray(2) }

            // 별의 10개 꼭짓점 계산
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) outerRadius else innerRadius
                val angle = i * angleStep - Math.PI / 2

                points[i][0] =
                    starCenterX + (r * Math.cos(angle)).toFloat()

                points[i][1] =
                    (starCenterY - 5f) + (r * Math.sin(angle)).toFloat()
            }

            // 각 꼭짓점을 조금씩 잘라서 둥글게 연결
            for (i in 0 until 10) {

                val prev = points[(i - 1 + 10) % 10]
                val curr = points[i]
                val next = points[(i + 1) % 10]

                //val round = if (i % 2 == 0) 20f else 6f
                // 20f가 별 바깥 꼭지점의 둥근정도
                val round = if (i % 2 == 0) 20f else 6f


                val dx1 = prev[0] - curr[0]
                val dy1 = prev[1] - curr[1]
                val len1 = Math.sqrt((dx1 * dx1 + dy1 * dy1).toDouble()).toFloat()

                val dx2 = next[0] - curr[0]
                val dy2 = next[1] - curr[1]
                val len2 = Math.sqrt((dx2 * dx2 + dy2 * dy2).toDouble()).toFloat()

                val startX = curr[0] + dx1 / len1 * round
                val startY = curr[1] + dy1 / len1 * round

                val endX = curr[0] + dx2 / len2 * round
                val endY = curr[1] + dy2 / len2 * round

                if (i == 0) {
                    moveTo(startX, startY)
                } else {
                    lineTo(startX, startY)
                }

                quadTo(
                    curr[0],
                    curr[1],
                    endX,
                    endY
                )
            }

            close()
        }



        val starPaint = Paint().apply {
            color = Color.parseColor("#FFC107") // 따뜻한 골드 황금색
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawPath(starPath, starPaint)

        // 3. 하단 텍스트 레이블
        val starTextPaint = Paint().apply {
            color = 0xFF555555.toInt()
            textSize = 35f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        canvas.drawText("즐겨찾기", starCenterX, starCenterY + 100f, starTextPaint)
        canvas.drawText("현위치 추가", starCenterX, starCenterY + 140f, starTextPaint)

    } // 📌 onDraw 함수의 올바른 닫는 위치

    private fun parseTimeToMinutes(timeStr: String): Float {
        try {
            // 공백 제거 및 대소문자 통일
            val cleanStr = timeStr.trim()

            // 만약 기존 포맷("HH:mm" 형태)이 바로 들어온다면
            if (!cleanStr.contains("오전") && !cleanStr.contains("오후") && !cleanStr.contains("AM") && !cleanStr.contains("PM")) {
                val parts = cleanStr.split(":")
                if (parts.size >= 2) {
                    return parts[0].toFloat() * 60f + parts[1].toFloat()
                }
            }

            // 12시간제 ("오전 3:15" 또는 "오후 3:15" 등) 파싱 처리
            val isPM = cleanStr.contains("오후") || cleanStr.contains("PM", ignoreCase = true)
            val timeOnly = cleanStr.replace("오전", "").replace("오후", "").replace("AM", "", true).replace("PM", "", true).trim()

            val parts = timeOnly.split(":")
            if (parts.size >= 2) {
                var hour = parts[0].toFloat()
                val minute = parts[1].toFloat()

                // 오후이면서 12시가 아니면 12시간 더하기 (단, 오후 12시는 낮 12시이므로 그대로 12)
                if (isPM && hour < 12f) {
                    hour += 12f
                }
                // 오전이면서 12시(오전 12시 = 자정)면 0시로 변환
                else if (!isPM && hour == 12f) {
                    hour = 0f
                }

                return hour * 60f + minute
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 0f
    }

    private fun getEllipsizedText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated...") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return if (truncated.isNotEmpty()) "$truncated..." else "..."
    }
    // 분(Minutes)을 받아 '1일 24시간 60분' 체계의 문자열로 변환해 주는 함수
    private fun formatStayDuration(totalMinutes: Long): String {
        if (totalMinutes <= 0L) return "0분"

        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60

        val sb = StringBuilder()
        if (days > 0) sb.append("${days}일 ")
        if (hours > 0) sb.append("${hours}시간 ")
        if (minutes > 0 || sb.isEmpty()) sb.append("${minutes}분")

        return sb.toString().trim()
    }
    // 🚀 선택된 밀리초(Epoch Millisecond) 시간으로 타임라인 스크롤을 확 꽂아주는 함수
    fun scrollToTime(targetStartTime: Long) {
        // 1. 선택된 밀리초를 Date 객체로 변환하여 당일 '자정(0시 0분 0초)' 밀리초 구하기
        val 선택된시간 = java.util.Date(targetStartTime)
        // 1. 캘린더에 전체 시간 세팅
        val calendar = java.util.Calendar.getInstance().apply {
            time = 선택된시간
        }
        // 📌 [변수 1] 타임라인 날짜 (시·분·초를 0으로 밀어버린 '그날 자정'의 에폭 밀리초)
        val 타임라인날짜 = (calendar.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val 자정부터_흐른밀리초 = targetStartTime - 타임라인날짜
        val 타임라인시간분 = calendar.timeInMillis

        // ========================================================
        // 💡 1단계: 만약 검색된 날짜가 지금 화면의 날짜와 다르다면?
        // 외부(액티비티)에 "이 날짜 데이터로 화면 갈아껴줘!"라고 요청합니다.
        // ========================================================
        // (현재 화면의 날짜를 알 수 있다면 비교해서 다를 때만 호출하면 됩니다)
        onDateChangeListener?.invoke(타임라인날짜)
        // 좌표 점프 로직
        val currentPixelsPerMinute = pixelsPerMinute * scaleFactorY
        val targetY = topPadding + (타임라인시간분 * currentPixelsPerMinute)
        currentTranslateY = (height / 3f) - targetY

        val scaledH = baseCanvasHeight * scaleFactorY
        val maxScrollY = max(0f, scaledH - height)
        currentTranslateY = max(-maxScrollY, min(currentTranslateY, 0f))

        invalidate()

        // 2. 당일 자정부터 선택된 시간까지 몇 분(Minute)이 흘렀는지 계산
        //val elapsedMillis = targetStartTime - 타임라인시간
        //val elapsedMinutes = elapsedMillis / (1000f * 60f)

        // 3. 현재 줌 배율(scaleFactorY)이 반영된 분당 픽셀 높이 계산
        //val currentPixelsPerMinute = pixelsPerMinute * scaleFactorY

        // 4. 상단 패딩(topPadding)을 포함한 절대 Y 좌표 계산
        //val targetY = topPadding + (elapsedMinutes * currentPixelsPerMinute)

        // 5. 선택된 카드가 화면 중앙 부근(화면 높이의 1/3 지점)에 오도록 translateY 조정
        //currentTranslateY = (height / 3f) - targetY

        // 6. 스크롤 범위를 벗어나지 않도록 안전 클램핑 (Clamp)
        //val scaledH = baseCanvasHeight * scaleFactorY
        //val maxScrollY = max(0f, scaledH - height)
        //currentTranslateY = max(-maxScrollY, min(currentTranslateY, 0f))

        // 7. 화면 다시 그리기!
        invalidate()
    }
}