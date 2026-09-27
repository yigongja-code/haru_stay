package com.example.haru_spot.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.haru_spot.R
import com.example.haru_spot.data.dao.LogDao
import com.example.haru_spot.ui.adapter.toTimelineItems
import com.example.haru_spot.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.haru_spot.data.dao.SpotDao
import com.example.haru_spot.data.dao.SumDao
import com.example.haru_spot.data.database.AppDatabase
import com.example.haru_spot.data.entity.BusStopEntity
import com.example.haru_spot.data.entity.Fav
import com.example.haru_spot.data.entity.Spot
import com.example.haru_spot.service.유틸.GpsMapUtil
import com.example.haru_spot.service.유틸.SearchAdm
import com.example.haru_spot.service.유틸.TriggerLogToSpot
import com.example.haru_spot.ui.adapter.extractPopupBusItems
import com.example.haru_spot.data.entity.Memo
import com.example.haru_spot.service.유틸.userGpsSync
import com.example.haru_spot.ui.adapter.PopupBusItem
import com.example.haru_spot.util.SumUtil
import com.example.haru_spot.data.entity.VisitLog


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // 📌 2층 팝업 및 뒤로 가기 관리 변수
    private var backPressedCallback: OnBackPressedCallback? = null
    private var mainBackPressedCallback: OnBackPressedCallback? = null

    // 현재 선택된 날짜를 관리하는 캘린더 객체
    private val calendar = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val spotDao: SpotDao by lazy {
        AppDatabase.getDatabase(requireContext()).spotDao()
    }

    private val logDao: LogDao by lazy {
        AppDatabase.getDatabase(requireContext()).logDao()
    }

    private val sumDao: SumDao by lazy {
        AppDatabase.getDatabase(requireContext()).sumDao()
    }

    //배경 깜박임 애니
    //이동경로 리스트에서 미선택시 이동경로-선택하여 좌표확인및 위치등록 깜박임
    val blinkAnimation = android.view.animation.AlphaAnimation(1.0f, 0.2f).apply {
        duration = 150 // 0.15초 간격으로 빠르게 번쩍번쩍
        repeatMode = android.view.animation.Animation.REVERSE
        repeatCount = 6 // 총 6번 반복 (약 1.8초 동안 강렬하게 어필)
    }

    // 📌 날짜를 안전하게 이동하고 UI와 데이터를 갱신하는 공통 함수
    private fun moveDate(days: Int) {
        // 💡 만약 미래로 가려는 경우라면? (가려는 날짜가 오늘보다 뒤쪽일 때)
        val targetCal = calendar.clone() as Calendar
        targetCal.add(Calendar.DATE, days)
        val today = Calendar.getInstance()

        if (targetCal.get(Calendar.YEAR) > today.get(Calendar.YEAR) ||
            (targetCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && targetCal.get(Calendar.DAY_OF_YEAR) > today.get(Calendar.DAY_OF_YEAR))) {
            return // 🛑 미래 진입 차단!
        }

        calendar.add(Calendar.DATE, days)
        updateDateText()
        loadSpotsForSelectedDate()
    }

    //가공과 뷰의 타이밍이 엇갈리면 다시 주석처리
    override fun onResume() {
        super.onResume()

        //TriggerLogToSpot.runIfNeeded(this)
        //TriggerLogToSpot.runIfNeeded(requireContext())
        //val db = AppDatabase.getDatabase(requireContext())

        // 다른 곳 다녀왔으면 묻고 따지지 않고 깔끔하게 처형!
        // 검색모드 초기화
        SearchAdm.destroyDialog()
        //화면띄우기
        //loadSpotsForSelectedDate()

        // 1. 가공 시작 전 로딩바 켜기
        //눈물의 방아쇠 또 봉인
        // 1. 가공 시작 전 로딩바 켜기
        binding.loadingProgressBar.visibility = View.VISIBLE

        // 2. 방아쇠 실행
        // 가공이 끝난 뒤 뷰 DB가 갱신되므로 그때 화면을 다시 읽는다.
        TriggerLogToSpot.runIfNeeded(requireContext()) {
            requireActivity().runOnUiThread {
                // 가공 완료 후 화면 갱신
                loadSpotsForSelectedDate()
                // 로딩바 종료

                binding.loadingProgressBar.visibility = View.GONE

            }
        }
        /*
        // [케이스 B] 이미 가공이 끝났거나 돌고 있어서 새로 안 돌았을 때
        if (!isStarted) {
            // 💡 핵심: 가공이 안 일어났더라도 화면에 돌아왔으니 현재 선택된 날짜의 데이터를 다시 불러와서 그려줍니다!
            loadSpotsForSelectedDate()
            binding.loadingProgressBar.visibility = View.GONE
        }*/
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //화면띄우기
        loadSpotsForSelectedDate()

        /* 방아쇠 봉인
        val db = AppDatabase.getDatabase(requireContext())

        // 1. 가공 시작 전 로딩바 켜기 방아쇠
        binding.loadingProgressBar.visibility = View.VISIBLE

        // 2. 방아쇠를 당기고 실행 여부를 리턴 받음
        val isStarted = TriggerLogToSpot.runIfNeeded(db) {
            // [케이스 A] 정상적으로 가공 파이프라인이 다 돌고 끝났을 때
            requireActivity().runOnUiThread {
                loadSpotsForSelectedDate()
                binding.loadingProgressBar.visibility = View.GONE
            }
        }

        // [케이스 B] 이미 돌고 있거나 해서 가공이 아예 안 일어났을 때 (즉시 컷)
        if (!isStarted) {
            binding.loadingProgressBar.visibility = View.GONE
        }*/

        // 1. 초기 날짜 텍스트 세팅
        updateDateText()

        // 2. 날짜 텍스트 클릭 시 -> 캘린더 다이얼로그 띄우기
        binding.tvCurrentDate.setOnClickListener {
            showDatePickerDialog()
        }

        // 3. 어제(◀) 버튼 클릭 시 하루 전으로 이동
        binding.btnPrevDay.setOnClickListener {
            moveDate(-1)
        }

        // 4. 내일(▶) 버튼 클릭 시 미래로 이동
        binding.btnNextDay.setOnClickListener {
            moveDate(1)
        }

        // 5. 좌우 스와이프 시 1일씩 변경
        binding.homeTimeAxisView.setOnDateSwipeListener { isNext ->
            if (isNext) {
                moveDate(1)
            } else {
                moveDate(-1)
            }
        }

        // 6. 최초 진입 시 오늘 날짜 기준 데이터 로드
        //loadSpotsForSelectedDate()

        // 7. 타임라인 뷰에서 카드를 터치했을 때 2층 팝업 띄우기 연결
        binding.homeTimeAxisView.setOnCardClickListener { clickedItem ->
            showDetailPopup(clickedItem)
        }

        // 📌 검색 리스트 클릭 시 날짜를 맞추고 해당 시간으로 점프하는 리스너 연동
        binding.homeTimeAxisView.setOnDateChangeListener { targetDateMillis ->
            // 1. Fragment의 기준 캘린더 날짜를 검색된 날짜로 변경
            calendar.timeInMillis = targetDateMillis

            // 2. 상단 날짜 텍스트 갱신 (예: 2026-06-07)
            updateDateText()

            // 3. 해당 날짜의 DB 데이터를 새로 로드! (다 불러온 뒤에 뷰어가 알아서 그려줍니다)
            loadSpotsForSelectedDate()
        }

        // 📌 1층 메인 화면 전용 '뒤로 가기' (예/아니오 종료 다이얼로그)
         mainBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(requireContext(),R.style.CustomAlertDialogStyle)
                    .setTitle("앱 종료")
                    .setMessage("하루(Haru) 앱을 종료하시겠습니까?")
                    .setPositiveButton("예") { _, _ ->
                        requireActivity().finish()
                    }
                    .setNegativeButton("아니오", null)
                    .show()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, mainBackPressedCallback!!)


    }

    // 📌 2층 상세 팝업을 띄우고 애니메이션을 실행하는 함수
    // 📌 2층 상세 팝업을 띄우는 함수 (기존 정상 동작하던 깔끔한 스케일로 원복)
    // 📌 2층 상세 팝업을 띄우고 데이터를 매핑하는 함수
    // 📌 2층 상세 팝업을 띄우고 데이터 바인딩을 수행하는 함수
    // 📌 UserLocationInputHelper 구조를 기초로 리뉴얼된 2층 상세 팝업 함수
    // 📌 UserLocationInputHelper 구조를 기초로 리뉴얼된 2층 상세 팝업 함수 (선택 연동 기능 포함)
    // 📌 [좌표확인] 버튼 연동 및 선택 경로 관리 기능이 포함된 2층 상세 팝업 함수
    // 📌 [좌표확인] 버튼 및 선택 경로 관리 기능이 완벽하게 반영된 2층 상세 팝업 함수
    // 📌 기존 변수명 및 데이터 구조와 완벽히 맞춘 2줄 리스트 & 연한 회색 배경 팝업 함수
    // 📌 [2줄 리스트 & 연한 회색 배경 컨테이너]가 적용된 2층 상세 팝업 함수
    private fun showDetailPopup(item: com.example.haru_spot.ui.adapter.HomeTimelineItem) {
        val activity = requireActivity() as? androidx.appcompat.app.AppCompatActivity ?: return
        val decorView = activity.window.decorView as? android.widget.FrameLayout ?: return

        // 1. 데이터 가공 (타이틀, 시간, 체류 시간 등)
        val firstBusStop = if (!item.busStop.isNullOrBlank()) {
            item.busStop.split("|").firstOrNull() ?: ""
        } else {
            ""
        }

        val titleText = if (firstBusStop.isNotBlank()) {
            "${item.adm} $firstBusStop 인근"
        } else {
            item.adm
        }

        val stayMinutes = item.spotTimeMinutes
        val stayDurationStr = if (stayMinutes <= 0L) {
            "최신 위치 / 이동 중"
        } else {
            val days = stayMinutes / (24 * 60)
            val hours = (stayMinutes % (24 * 60)) / 60
            val minutes = stayMinutes % 60
            val sb = java.lang.StringBuilder()
            if (days > 0) sb.append("${days}일 ")
            if (hours > 0) sb.append("${hours}시간 ")
            if (minutes > 0 || sb.isEmpty()) sb.append("${minutes}분")
            sb.toString().trim()
        }

        // 2. 화면 크기 계산
        val displayMetrics = context?.resources?.displayMetrics ?: return
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val targetWidth = (screenWidth * 0.88f).toInt()
        val targetHeight = (screenHeight * 0.82f).toInt()

        val layerParams = android.widget.FrameLayout.LayoutParams(
            targetWidth,
            targetHeight
        ).apply {
            gravity = android.view.Gravity.CENTER
        }

        var dimBackgroundRef: android.widget.FrameLayout? = null
        var panelScrollViewRef: android.widget.ScrollView? = null
        var isDialogOpen = true

        // 💡 현재 사용자가 선택한 정류장/경로 정보 (초기에는 미선택 상태로 비워둠)
        var selectedPopupItem: com.example.haru_spot.ui.adapter.PopupBusItem? = null





        // 6. 메인 패널 (ScrollView 감싸기)
        val panelView = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
            elevation = 20f
            setPadding(40, 40, 40, 40)
            isFocusable = true
            isFocusableInTouchMode = true
        }

        // --- 컴포넌트 1: 타이틀 ---
        val tvTitle = android.widget.TextView(requireContext()).apply {
            text = item.popTitle.ifBlank { titleText }
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.BLACK)
            setPadding(0, 0, 0, 12)
        }
        panelView.addView(tvTitle)

        // --- 컴포넌트 2: 시간 ---
        val tvTimeRange = android.widget.TextView(requireContext()).apply {
            text = "시간: ${item.timeRangeStr}"
            textSize = 14f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 0, 0, 6)
        }
        panelView.addView(tvTimeRange)

        // --- 컴포넌트 3: 체류 시간 ---
        val tvStayDuration = android.widget.TextView(requireContext()).apply {
            text = "체류 시간: $stayDurationStr"
            textSize = 14f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 0, 0, 20)
        }
        panelView.addView(tvStayDuration)

        // --- 컴포넌트 4: 선택된 이동경로 박스 (하단 리스트와 동일한 2줄 구조로 변경) ---
        // 메모입력 박스로 환골탈퇴
        //메모 저장은 팝업이 닫힐때나 포커스 해제될때 저장ㅅ
        val memoBoxCard = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.card1))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        }

        val tvMemoLine1 = android.widget.TextView(requireContext()).apply {
            text = "메모"
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#222222"))
        }

        // 💡 라인 2: 입력 락을 풀고 직접 글을 적을 수 있는 EditText로 변신!
        val tvMemoLine2 = android.widget.EditText(requireContext()).apply {
            // TODO: 추후 DB에서 기존 메모 불러와서 세팅
            hint = "이곳에 메모를 남겨보세요 (최대 4줄)"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#222222"))
            setHintTextColor(android.graphics.Color.parseColor("#AAAAAA"))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(0, 4, 0, 0)

            // 멀티라인 허용하되 최대 4줄 고정
            isSingleLine = false
            maxLines = 4
            minLines = 4 // 공간이 찌그러지지 않게 최소 4줄 높이 확보

            // 💡 [핵심] 엔터(\n) 개수가 3개(총 4줄) 이상일 때 추가 엔터를 원천 차단하는 필터 적용!
            filters = arrayOf(android.text.InputFilter { source, start, end, dest, dstart, dend ->
                val tentativeText = dest.toString().substring(0, dstart) +
                        source.subSequence(start, end) +
                        dest.toString().substring(dend)

                // 줄바꿈(\n) 개수 계산
                val lineCount = tentativeText.count { it == '\n' } + 1

                // 만약 4줄을 초과하려고 하면 방금 입력한 내용을 무효화("") 처리
                if (lineCount > 4) {
                    // 사용자에게 넌지시 알려주려면 토스트를 띄워도 좋지만, 조용히 막는 게 깔끔합니다.
                    ""
                } else {
                    null // 허용
                }
            })
        }

        memoBoxCard.addView(tvMemoLine1)
        memoBoxCard.addView(tvMemoLine2)
        panelView.addView(memoBoxCard)

        // --- 컴포넌트 5: [좌표확인] [위치등록] 버튼 2개 레이아웃 ---
        val buttonRowLayout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
        }

        // 1. 💡 [변수 선언만 먼저 위에서 해둡니다]
        lateinit var tvListLabel: android.widget.TextView

        val btnCheckCoordinate = android.widget.Button(requireContext()).apply {
            text = "좌표확인"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            ).apply {
                rightMargin = 10
            }
            setOnClickListener {
                val currentSelected = selectedPopupItem
                if (currentSelected == null) {
                    android.widget.Toast.makeText(requireContext(), "먼저 아래 리스트에서 경로를 선택해주세요!", android.widget.Toast.LENGTH_SHORT).show()

                    // 💡 [핵심] 배경색이 평소 색(#FFF3E0)에서 강렬한 빨간색(#FF5252)으로 왕복하도록 애니메이션 실행!
                    val colorAnim = android.animation.ValueAnimator.ofObject(
                        android.animation.ArgbEvaluator(),
                        android.graphics.Color.parseColor("#FFF3E0"), // 원래 포인트 배경색
                        android.graphics.Color.parseColor("#FF5252")  // 강렬한 경고 빨간색
                    ).apply {
                        duration = 150 // 깜박이는 속도 (0.15초)
                        repeatMode = android.animation.ValueAnimator.REVERSE
                        repeatCount = 5 // 총 5번 왕복 (번쩍번쩍)
                    }

                    // 애니메이션이 진행되는 동안 tvListLabel의 배경색을 실시간으로 변경
                    colorAnim.addUpdateListener { animator ->
                        val animatedColor = animator.animatedValue as Int
                        tvListLabel.setBackgroundColor(animatedColor)
                    }

                    colorAnim.start()
                    return@setOnClickListener
                }

                // 💡 외부 앱으로 나가기 직전에도 팝업의 뒤로가기 주도권(isEnabled)을 확실히 유지
                backPressedCallback?.isEnabled = true

                GpsMapUtil.openKakaoMap(
                    requireContext(),
                    currentSelected.latitude,
                    currentSelected.longitude,
                    currentSelected.displayText
                )
            }
        }

        val btnRegisterLocation = android.widget.Button(requireContext()).apply {
            text = "위치등록"
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            ).apply {
                leftMargin = 10
            }
            setOnClickListener {

                val currentSelected = selectedPopupItem

                if (currentSelected == null) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "먼저 아래 리스트에서 경로를 선택해주세요!",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }

                // 위치명 입력
                val editText = android.widget.EditText(requireContext(),).apply {
                    hint = "위치명을 입력하세요"
                    setSingleLine(true)
                    setPadding(40, 20, 40, 20)
                }

                android.app.AlertDialog.Builder(requireContext(),R.style.CustomAlertDialogStyle)
                    .setTitle("위치 등록")
                    .setMessage("등록할 위치의 이름을 입력해주세요.")
                    .setView(editText)
                    .setNegativeButton("취소", null)
                    .setPositiveButton("등록") { _, _ ->

                        val locationName = editText.text.toString().trim()

                        if (locationName.isBlank()) {
                            android.widget.Toast.makeText(
                                requireContext(),
                                "위치명을 입력해주세요.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()

                            return@setPositiveButton
                        }

                        viewLifecycleOwner.lifecycleScope.launch {
                            registerUserLocation(
                                requireContext(),
                                locationName,
                                currentSelected
                            )
                        }
                    }
                    .show()
            }
        }

        buttonRowLayout.addView(btnCheckCoordinate)
        buttonRowLayout.addView(btnRegisterLocation)
        panelView.addView(buttonRowLayout)

        // 3. 닫기 공통 로직
        // 3. 닫기 공통 로직
        // 3. 닫기 공통 로직
        val closeDialog: () -> Unit = {
            if (isDialogOpen) {
                isDialogOpen = false

                val currentMemoText = tvMemoLine2.text.toString().trim()

                lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(requireContext())
                    val memoDao = db.memoDao()
                    val spotDao = db.spotDao()

                    var targetMemoId = item.memo

                    if (currentMemoText.isNotEmpty()) {
                        // 💡 memo_Field1에 오직 시작 시간(item.startEpoch)만 문자열로 쏙 장착!
                        val memoEntity = Memo(
                            memo_id = if (targetMemoId > 0) targetMemoId else 0L,
                            memo_String = currentMemoText,
                            memo_Field1 = item.startEpoch.toString() // 👈 닻 역할은 이 타임스탬프 하나면 충분합니다!
                        )

                        val savedId = memoDao.메모저장id반환(memoEntity)
                        if (savedId > 0) {
                            targetMemoId = savedId
                        }
                    } else {
                        if (targetMemoId > 0) {
                            memoDao.deleteMemoById(targetMemoId)
                        }
                        targetMemoId = 0L
                    }

                    // 시작 시간으로 Spot 엔티티를 찾아와서 spMemoId 갱신
                    val targetSpot = spotDao.메모id저장대상(item.startEpoch)
                    if (targetSpot != null) {
                        val updatedSpot = targetSpot.copy(spMemoId = targetMemoId)
                        spotDao.updateSpot(updatedSpot)
                    }
                }

                backPressedCallback?.remove()
                backPressedCallback = null
                mainBackPressedCallback?.isEnabled = true
                binding.homeTimeAxisView.isEnabled = true

                dimBackgroundRef?.let { if (it.parent != null) decorView.removeView(it) }
                panelScrollViewRef?.let { if (it.parent != null) decorView.removeView(it) }

                loadSpotsForSelectedDate()
            }
        }

        // 4. Back 버튼 핸들링 연동
        // 4. Back 버튼 핸들링 연동 (외부 앱 다녀와도 주도권을 잃지 않도록 보장)
        mainBackPressedCallback?.isEnabled = false

        if (backPressedCallback == null) {
            backPressedCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isDialogOpen) {
                        closeDialog()
                    }
                }
            }
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback!!)
        } else {
            backPressedCallback?.isEnabled = true
        }
        // 5. 딤(Dim) 배경 생성
        val dimBackgroundView = android.widget.FrameLayout(requireContext()).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
            isClickable = true
            isFocusable = true
            setOnClickListener { closeDialog() }
        }
        dimBackgroundRef = dimBackgroundView
        decorView.addView(dimBackgroundView, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // --- 컴포넌트 6: 이동경로 리스트 영역 타이틀 ---
         tvListLabel = android.widget.TextView(requireContext()).apply {
            text = "  이동 경로-선택하여 좌표확인및 위치등록"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.BLACK)
            setBackgroundColor(android.graphics.Color.parseColor("#FFF3E0"))
            setPadding(0, 10, 0, 10)
        }
        panelView.addView(tvListLabel)

        // 7. 리스트 박스 전체 컨테이너
        val routeListContainer = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
        }
        panelView.addView(routeListContainer)

        // 8. ScrollView로 패널 감싸기
        val scrollView = android.widget.ScrollView(requireContext()).apply {
            isFillViewport = true
            addView(panelView, android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        panelScrollViewRef = scrollView
        decorView.addView(scrollView, layerParams)

        // 9. 비동기로 rawLogs 데이터 파싱 및 2줄 리스트 동적 구성 (기본 자동 선택 제거)
        viewLifecycleOwner.lifecycleScope.launch {
            val rawLogs = item.rawLogs.sortedBy { it.logStartTime }
            val popupBusItemList = extractPopupBusItems(rawLogs)

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (!isDialogOpen) return@withContext

                // 💡 첫 진입 시 임의 자동 선택을 없애고 빈 상태로 유지
                selectedPopupItem = null

                routeListContainer.removeAllViews()
                // 💡 팝업이 열릴 때 기존에 저장된 메모가 있다면 DB에서 긁어와서 뷰에 세팅!
                if (item.memo > 0) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(requireContext())
                        val memoDao = db.memoDao()
                        // MemoDao에 저장된 엔티티를 아이디로 가져오는 쿼리 함수 (예: getMemoById 등)
                        val savedMemo = memoDao.spMemoId로memo불러오기(item.memo) // 👈 쓰시는 DAO 함수명에 맞춰주세요!

                        if (savedMemo != null && !savedMemo.memo_String.isNullOrEmpty()) {
                            withContext(Dispatchers.Main) {
                                tvMemoLine2.setText(savedMemo.memo_String)
                            }
                        }
                    }
                    loadSpotsForSelectedDate()
                }



                val timeFormatter = java.text.SimpleDateFormat("a h:mm", java.util.Locale.KOREA)

                // logBusDistance = 50미터 기준으로 삼고 중복을 제거함(중복중 두번째의 값이 distance 기준값이됨
                data class DisplayRoute(
                    val log: VisitLog,
                    val duplicateCount: Int,
                    val endTime: Long
                )

                val displayLogs = mutableListOf<DisplayRoute>()
                var bucket = mutableListOf<VisitLog>()
                var referenceDistance = 0L

                for (log in rawLogs) {

                    if (bucket.isEmpty()) {
                        bucket.add(log)
                        continue
                    }

                    val last = bucket.last()

                    if (log.logBusStop == last.logBusStop) {

                        if (bucket.size == 1) {
                            referenceDistance = log.logBusDistance
                            bucket.add(log)
                            continue
                        }
                        // 여기서 몇m로 바꿀건지를 설정   50L = 50미터 20L = 20미터
                        // 테스트가 애매하면 설정으로 빼면됨
                        if (kotlin.math.abs(log.logBusDistance - referenceDistance) <= 20L) {
                            bucket.add(log)
                            continue
                        }
                    }

                    val displayLog = bucket.getOrNull(1) ?: bucket.first()

                    displayLogs.add(
                        DisplayRoute(
                            log = displayLog,
                            duplicateCount = bucket.size,
                            endTime = bucket.last().logStartTime
                        )
                    )

                    bucket = mutableListOf(log)
                }

                if (bucket.isNotEmpty()) {

                    val displayLog = bucket.getOrNull(1) ?: bucket.first()

                    displayLogs.add(
                        DisplayRoute(
                            log = displayLog,
                            duplicateCount = bucket.size,
                            endTime = bucket.last().logStartTime
                        )
                    )
                }

                //위의 for문에서 만들어진 리스트를 받아와 리스트 출력 (중복제거용)
                for (item in displayLogs) {
                    val log = item.log
                    val duplicateCount = item.duplicateCount
                    val endTime = item.endTime

                    val busStopText = log.logBusStop?.ifBlank { "위치 정보 없음" } ?: "위치 정보 없음"

                    // 시간 포맷팅
                    val rawTimeStr = timeFormatter.format(java.util.Date(log.logStartTime))
                    val formattedTimeStr = rawTimeStr.replace(Regex(" ([1-9]):")) { matchResult ->
                        " 0${matchResult.groupValues[1]}:"
                    }

                    // GPS 오차 값
                    val gpsRangeVal = log.logGpsRange.toInt()
                    val rangeStr = if (gpsRangeVal > 0) " | GPS 오차: ${gpsRangeVal}m" else ""

                    // 각 아이템 카드 레이아웃 (2줄 구조)
                    val itemCardLayout = android.widget.LinearLayout(requireContext()).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(24, 20, 24, 20)
                        setBackgroundColor(android.graphics.Color.parseColor("#F2F3F5"))
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = 14
                        }
                    }

                    // [첫 번째 줄]: logBusStop 필드 값 - 중복이 있으면 중복을 표시
                    val duplicateText = if (duplicateCount > 1) " X${duplicateCount}" else ""

                    val tvLine1 = android.widget.TextView(requireContext()).apply {
                        text = "◼ $busStopText 인근$duplicateText"
                        textSize = 15f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(android.graphics.Color.parseColor("#222222"))
                    }

                    // [두 번째 줄]: logStartTime 옆에 logGpsRange 배치
                    //중복리스트면면 뒤에 종료시간은 붙임
                    val endTimeStr = if (duplicateCount > 1) {
                        val endTimeRaw = timeFormatter.format(java.util.Date(endTime))

                        endTimeRaw.replace(Regex(" ([1-9]):")) { matchResult ->
                            " 0${matchResult.groupValues[1]}:"
                        }
                    } else {
                        ""
                    }

                    val tvLine2 = android.widget.TextView(requireContext()).apply {
                        text = if (duplicateCount > 1) {
                            "  🕒 $formattedTimeStr ~ $endTimeStr"
                        } else {
                            "  🕒 $formattedTimeStr$rangeStr"
                        }

                        textSize = 12f
                        setTextColor(android.graphics.Color.parseColor("#666666"))
                        setPadding(0, 4, 0, 0)
                    }

                    itemCardLayout.addView(tvLine1)
                    itemCardLayout.addView(tvLine2)

                    // 클릭 시 상단 대표 경로 2줄 박스도 함께 갱신되도록 연동
                    //선택하면 selectedPoupItem에 정보를 담음
                    itemCardLayout.setOnClickListener {
                        selectedPopupItem = com.example.haru_spot.ui.adapter.PopupBusItem(
                            displayText = "$formattedTimeStr - $busStopText 인근",
                            latitude = log.logGpsLat,
                            longitude = log.logGpsLon,
                            busAdmCode = log.logAdmCode,
                            busAdmName = log.logAdmName

                        )


                        // 💡 상단 선택 박스도 리스트 카드와 똑같은 2줄 형태로 갱신!
                        // 메모장으로 대체될 영역이라 주석처리
                        //tvSelectedLine1.text = "◼ $busStopText"
                        //tvSelectedLine2.text = "  🕒 $formattedTimeStr$rangeStr"

                        // 💡 [핵심 아이디어] 클릭될 때마다 컨테이너(routeListContainer) 안에 있는 모든 자식 뷰(카드들)의 색상을 일단 기본색으로 싹 리셋!
                        // 카드 백그라운드를 원래색상으로 초기화 - 이래야 강조색상이 하나만 표시됨
                        for (i in 0 until routeListContainer.childCount) {
                            val child = routeListContainer.getChildAt(i)
                            child.setBackgroundColor(android.graphics.Color.parseColor("#F2F3F5"))
                        }

                        // 💡 [핵심] 클릭된 해당 아이템 카드의 배경색을 상단 박스와 동일한 포인트 컬러로 변경!
                        itemCardLayout.setBackgroundColor(android.graphics.Color.parseColor("#FFF3E0"))
                        // 리스트 터치 시 상단으로 스크롤 이동
                        panelScrollViewRef?.smoothScrollTo(0, 0)
                    }

                    routeListContainer.addView(itemCardLayout)
                }
            }
        }
    }



    // 📌 2층 팝업을 닫는 함수
    private fun hideDetailPopup() {
        val popupView = binding.layoutDetailPopup
        val dimBackground = binding.viewDimBackground

        backPressedCallback?.isEnabled = false
        mainBackPressedCallback?.isEnabled = true

        dimBackground.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                dimBackground.visibility = View.GONE
            }
            .start()

        popupView.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                popupView.visibility = View.GONE
                binding.homeTimeAxisView.isEnabled = true
            }
            .start()
    }

    // 캘린더 날짜 텍스트 갱신 함수
    private fun updateDateText() {
        binding.tvCurrentDate.text = dateFormatter.format(calendar.time)
    }

    // 날짜를 직접 누를 때 뜨는 캘린더 다이얼로그 구현
    private fun showDatePickerDialog() {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        //💡 [핵심] 캘린더 다이얼로그에 우리가 만든 CustomAlertDialogStyle 테마를 입히는 방법!
        //테마가 맘에 안들면 따로 테마 만들어서 장착
        val themedContext = android.view.ContextThemeWrapper(requireContext(), R.style.CustomAlertDialogStyle)

        DatePickerDialog(themedContext, { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(selectedYear, selectedMonth, selectedDay)
            updateDateText()
            loadSpotsForSelectedDate()
        }, year, month, day).show()
    }



    // 📌 선택된 날짜에 맞는 DB 쿼리를 수행하고 타임라인에 꽂아주는 함수 (기존 핵심 로직 완전 복구)
    private fun loadSpotsForSelectedDate() {
        val currentDateStr = dateFormatter.format(calendar.time)

        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val selectedCal = calendar.clone() as Calendar
        selectedCal.set(Calendar.HOUR_OF_DAY, 0)
        selectedCal.set(Calendar.MINUTE, 0)
        selectedCal.set(Calendar.SECOND, 0)
        selectedCal.set(Calendar.MILLISECOND, 0)

        val isFuture = selectedCal.timeInMillis > todayCal.timeInMillis

        // 💡 핵심: 어떤 날짜로 이동하든, 데이터를 조회하기 전에 무조건 뷰부터 싹 비우고 시작!
        binding.homeTimeAxisView.setItems(emptyList())

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (isFuture) {
                    withContext(Dispatchers.Main) {
                        binding.homeTimeAxisView.visibility = View.GONE
                        binding.imgFuturePlaceholder.visibility = View.VISIBLE
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    binding.homeTimeAxisView.visibility = View.VISIBLE
                    binding.imgFuturePlaceholder.visibility = View.GONE
                }

                val startTimestamp = selectedCal.timeInMillis

                val endCal = calendar.clone() as Calendar
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                endCal.set(Calendar.MILLISECOND, 999)
                val endTimestamp = endCal.timeInMillis

                // =========================================================
                // ① Sum_db는 별도로 조회
                // =========================================================
                val sumList = withContext(Dispatchers.IO) {
                    sumDao.getAllSum()
                }

                // =========================================================
                // ② Spot 데이터 조회
                // =========================================================
                val spotList = withContext(Dispatchers.IO) {
                    val list = spotDao
                        .getSpotsOverlappingDate(
                            startTimestamp,
                            endTimestamp
                        )
                        .toMutableList()

                    val latestLog = logDao.getLatestLog()
                    val todayStr = dateFormatter.format(System.currentTimeMillis())

                    // 체류시간이 1일을 넘었고
                    // list[0].spSpotTime >= 1440L * 60 * 1000L
                    // 종료시간이 뷰어기준날자보다 큰것

                    // ⚠️ 기존 코드 그대로 유지
                    val startDateStr = dateFormatter.format(list[0].spStartTime)
                    val endDateStr = dateFormatter.format(list[0].spEndTime)

                    android.util.Log.d(
                        "SPOT_DEBUG",
                        "🔥 진입전! 시작시간: $startDateStr, 종료시간: $endDateStr, " +
                                "currentDateStr: $currentDateStr, 체류시간: ${list[0].spSpotTime}"
                    )

                    // 1일 이상 체류 && 시작날짜가 오늘 이전 && 종료날짜가 오늘 이후
                    if (
                        list[0].spSpotTime >= 1440L * 60 * 1000L &&
                        startDateStr < currentDateStr &&
                        endDateStr > currentDateStr
                    ) {

                        val lastKnownSpot = spotDao.getPreviousSpot(startTimestamp)

                        android.util.Log.d(
                            "SPOT_DEBUG",
                            "🔥 완벽한 기간 걸침 유령 데이터 진입 성공!"
                        )

                        val carriedSpot = Spot(
                            spAdmCode = lastKnownSpot?.spAdmCode ?: "00000",
                            spAdmName = lastKnownSpot?.spAdmName
                                ?: "이 날은 기록된 위치 데이터가 없습니다.",
                            spBusStop = lastKnownSpot?.spBusStop ?: "없음",
                            spCellKey = lastKnownSpot?.spCellKey ?: "",
                            spWifiMac = lastKnownSpot?.spWifiMac ?: "",
                            spGpsLat = lastKnownSpot?.spGpsLat ?: 0.0,
                            spGpsLon = lastKnownSpot?.spGpsLon ?: 0.0,
                            spStats = lastKnownSpot?.spStats ?: "",
                            spStartTime = startTimestamp,
                            spEndTime = endTimestamp,
                            spSpotTime = lastKnownSpot?.spSpotTime ?: 0L,
                            spProcessedAt = System.currentTimeMillis(),
                            spMemoId = lastKnownSpot?.spMemoId ?: 0L
                        )

                        list.add(carriedSpot)
                    }

                    // 현재날짜와 데이터의 날짜를 맞춤
                    if (currentDateStr == todayStr) {

                        // spotDB의 마지막을 불러와서..
                        val lastSpot = spotDao.getLastSpot()

                        // spotDB의 종료시간이 0 이면
                        if (lastSpot != null && lastSpot.spEndTime == 0L) {

                            // 다음 logDB가 있고,
                            // 그 시작 시간이 내 시작 시간보다 뒤에 있다면?
                            if (
                                latestLog != null &&
                                latestLog.logStartTime > lastSpot.spStartTime
                            ) {
                                // 다음 로그의 시작 시간을 내 종료 시간으로 넣음
                                lastSpot.spEndTime = latestLog.logStartTime
                            } else {
                                // 뒤에 데이터가 없다면 1시간 더해줌
                                lastSpot.spEndTime =
                                    lastSpot.spStartTime + (60 * 60 * 1000L)

                                lastSpot.spAdmName =
                                    "(🔹최근)${lastSpot.spAdmName}"
                            }

                            // 마지막 spotDB에 다른 데이터가 있는지 확인
                            val existing = list.find {
                                it.spStartTime == lastSpot.spStartTime
                            }

                            // 방어코드
                            if (existing != null) {
                                existing.spEndTime = lastSpot.spEndTime
                            } else {
                                list.add(lastSpot)
                            }

                            if (
                                latestLog != null &&
                                latestLog.logEndTime == 0L
                            ) {
                                val currentSpot = Spot(
                                    spAdmCode = latestLog.logAdmCode,
                                    spAdmName = "(🔹최근) ${latestLog.logAdmName} ",
                                    spBusStop = "${latestLog.logBusStop} ",
                                    spCellKey = latestLog.logCellKey,
                                    spWifiMac = latestLog.logWifiMac,
                                    spGpsLat = latestLog.logGpsLat,
                                    spGpsLon = latestLog.logGpsLon,
                                    spStats = latestLog.logStats,
                                    spStartTime = latestLog.logStartTime,
                                    spEndTime =
                                        latestLog.logStartTime +
                                                (60 * 60 * 1000L),
                                    spSpotTime = 0L,
                                    spProcessedAt = System.currentTimeMillis(),
                                    spMemoId = 0L
                                )

                                list.add(currentSpot)
                            }
                        }
                    }

                    list
                }

                // =========================================================
                // ③ rawLogs는 기존 그대로   //spot의 시작과 끝시간 안에 있는 로그를 넣음
                // =========================================================
                val rawLogs = withContext(Dispatchers.IO) {
                    logDao.getLogsBetween(
                        startTimestamp,
                        endTimestamp
                    )
                }

                // =========================================================
                // ④ Spot + rawLogs + Sum_db를 여기서 묶어서 전달
                // =========================================================
                val timelineItems = spotList.flatMap {
                    it.toTimelineItems(
                        currentDateStr,
                        rawLogs,
                        sumList
                    )
                }
                android.util.Log.e(
                    "Statistics",
                    "🔥 loadSpotsForSelectedDate의 sumList 반포 = ${
                        sumList.filter { it.sumAdmCode == "1165010700" }
                    }"
                )

                binding.homeTimeAxisView.post {
                    binding.homeTimeAxisView.scrollToNoon()
                }

                binding.homeTimeAxisView.setItems(timelineItems)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private suspend fun registerUserLocation(
        context: Context,
        locationName: String,
        selected: PopupBusItem
    ) {
        val lat = selected.latitude
        val lon = selected.longitude
        val admCode = selected.busAdmCode
        val admCodeNm = selected.busAdmName

        try {
            val db = AppDatabase.getDatabase(context)

            // ------------------------------------------------
            // BusStop 저장
            // ------------------------------------------------
            val newBusStop = BusStopEntity(
                stopName = locationName,
                lat = lat,
                lon = lon,
                admCode = admCode,
                admName = admCodeNm
            )

            val generatedBusStopId = db.busStopDao().insert(newBusStop)

            // ------------------------------------------------
            // Fav 저장
            // ------------------------------------------------
            val newFav = Fav(
                favAdmCode = admCode,
                favAdmName = admCodeNm,
                favBusStop = locationName,
                favCellKey = "",
                favWifiMac = "",
                favGpsLat = lat,
                favGpsLon = lon,
                favInDate = System.currentTimeMillis(),
                favGeoFnc = 300L,
                favStats = "user",
                favLinkedBusStopId = generatedBusStopId,
                favField2 = "",
                favField3 = "",
                favField4 = "",
                favField5 = ""
            )

            db.favoriteDao().insert(newFav)

            // ------------------------------------------------
            // 사용자 위치 동기화
            // ------------------------------------------------
            userGpsSync.refineWithUserSpots(
                context,
                lat,
                lon
            )

            // ------------------------------------------------
            // UI
            // ------------------------------------------------


        } catch (e: Exception) {
            e.printStackTrace()

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "저장 중 오류가 발생했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /*private fun showRouteList(
        rawLogs: List<VisitLog>,
        onItemSelected: (PopupBusItem) -> Unit
    ) {
        val timeFormatter = java.text.SimpleDateFormat("a h:mm", java.util.Locale.KOREA)

        for (log in rawLogs) {
            val busStopText = log.logBusStop?.ifBlank { "위치 정보 없음" } ?: "위치 정보 없음"
            val rawTimeStr = timeFormatter.format(java.util.Date(log.logStartTime))
            val formattedTimeStr = rawTimeStr.replace(Regex(" ([1-9]):")) { " 0${it.groupValues[1]}:" }
            val gpsRangeVal = log.logGpsRange.toInt()
            val rangeStr = if (gpsRangeVal > 0) " | GPS 오차: ${gpsRangeVal}m" else ""

            val itemCardLayout = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(24, 20, 24, 20)
                setBackgroundColor(android.graphics.Color.parseColor("#F2F3F5"))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 14 }
            }

            val tvLine1 = android.widget.TextView(requireContext()).apply {
                text = "◼ $busStopText 인근"
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.parseColor("#222222"))
            }

            val tvLine2 = android.widget.TextView(requireContext()).apply {
                text = "  🕒 $formattedTimeStr$rangeStr"
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#666666"))
                setPadding(0, 4, 0, 0)
            }

            itemCardLayout.addView(tvLine1)
            itemCardLayout.addView(tvLine2)

            itemCardLayout.setOnClickListener {
                selectedPopupItem = com.example.haru_spot.ui.adapter.PopupBusItem(
                    displayText = "$formattedTimeStr - $busStopText 인근",
                    latitude = log.logGpsLat,
                    longitude = log.logGpsLon,
                    busAdmCode = log.logAdmCode,
                    busAdmName = log.logAdmName
                )

                for (i in 0 until routeListContainer.childCount) {
                    val child = routeListContainer.getChildAt(i)
                    child.setBackgroundColor(android.graphics.Color.parseColor("#F2F3F5"))
                }

                itemCardLayout.setBackgroundColor(android.graphics.Color.parseColor("#FFF3E0"))
                panelScrollViewRef?.smoothScrollTo(0, 0)
            }

            routeListContainer.addView(itemCardLayout)
        }
    }*/



}