package com.example.haru_spot.ui

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.haru_spot.R
import com.example.haru_spot.data.database.AppDatabase
import com.example.haru_spot.data.entity.Fav
import com.example.haru_spot.data.entity.Memo
import com.example.haru_spot.data.entity.VisitLog
import com.example.haru_spot.service.ForegroundService
import com.example.haru_spot.service.LogCollectService
import com.example.haru_spot.service.collector.TestDataCollector
import com.example.haru_spot.service.유틸.TriggerLogToSpot
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import android.os.CountDownTimer
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import com.example.haru_spot.data.entity.BusStopEntity
import com.example.haru_spot.service.유틸.userGpsSync
import com.example.haru_spot.ui.adapter.PopupBusItem
import com.example.haru_spot.data.entity.Spot
import com.google.android.material.bottomnavigation.BottomNavigationView

//import android.view.View

data class HaruDatabase(
    val logs: List<VisitLog>,
    val favorites: List<Fav>, // 형님의 실제 즐겨찾기 Entity명에 맞춤
    val memos: List<Memo>          // 형님의 실제 메모 Entity명에 맞춤
)

class DatabaseFragment : Fragment() {

    private var currentIndex = 0
    private var logList: List<VisitLog> = listOf()
    private var spotCurrentIndex = 0
    private var spotList: List<Spot> = listOf()



    //private var isForegroundStarted = false

    // 💡 프래그먼트 전역에서 쓰일 뷰 변수들을 미리 선언
    private lateinit var tvPageInfo: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvBusStop: TextView
    private lateinit var tvAdmCode: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvCellTower: TextView
    private lateinit var tvWifi: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvGeofencing: TextView
    private lateinit var tvGpsRange: TextView
    private lateinit var tvStartTime: TextView // 👈 추가
    private lateinit var tvEndTime: TextView     // 👈 추가

    private lateinit var tvBusDistance: TextView     // 👈 추가
    private lateinit var tvAdmName: TextView     // 👈 추가

    private lateinit var tvTableTitle: TextView //수집데이터 타이틀 아이디

    private val REQUEST_CODE_LOAD_DATA = 9999

    //spotDB용 변수 선언
    private lateinit var tvSpotTableTitle: TextView
    private lateinit var tvSpAdmName: TextView
    private lateinit var tvSpAdmCode: TextView
    private lateinit var tvSpStartTime: TextView
    private lateinit var tvSpEndTime: TextView
    private lateinit var tvSpSpotTime: TextView
    private lateinit var tvSpMemoId: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //뒤로가기 눌렀음때 홈으로 가기
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

        // 나머지 초기화...
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_database, container, false)

        val btnPrev = view.findViewById<Button>(R.id.t_button_prev)
        val btnNext = view.findViewById<Button>(R.id.t_button_next)

        val btnImmediateCollect = view.findViewById<Button>(R.id.btnImmediateCollect)
        //버정리스트 재로딩
        val btnApplyCleanBusData = view.findViewById<Button>(R.id.btnApplyCleanBusData)
        // 🧪 가상 데이터 생성 테스트 단추
        val btnGenerateMockData = view.findViewById<Button>(R.id.btnGenerateMockData)

        // 개발자 메뉴.. 버정초기화. 테스트데이터 생성 여기서 숨김
        btnApplyCleanBusData.visibility = View.GONE
        btnGenerateMockData.visibility = View.GONE

        val btnUserLocationSave = view.findViewById<Button>(R.id.btnUserLocationSave)

        tvPageInfo = view.findViewById<TextView>(R.id.tvPageInfo)

        // 💡 9가지 상세 정보 표시용 뷰 매핑
        //logDB 수집데이터 뷰관련 설정
        tvStartTime = view.findViewById<TextView>(R.id.tvStartTime)
        tvEndTime = view.findViewById<TextView>(R.id.tvEndTime)
        tvBusStop = view.findViewById<TextView>(R.id.tvBusStop)           // 2. 정류장 부근
        tvAdmCode = view.findViewById<TextView>(R.id.tvAdmCode)           // 3. 행정동 코드
        tvLocation = view.findViewById<TextView>(R.id.tvLocation)         // 4. 위치
        tvCellTower = view.findViewById<TextView>(R.id.tvCellTower)     // 5. 기지국
        tvWifi = view.findViewById<TextView>(R.id.tvWifi)                 // 6. 와이파이
        tvStatus = view.findViewById<TextView>(R.id.tvStatus)             // 7. 상태
        tvGeofencing = view.findViewById<TextView>(R.id.tvGeofencing)     // 8. 지오펜싱
        tvGpsRange = view.findViewById<TextView>(R.id.tvGpsRange)           // 9. 와이파이오차값
        tvBusDistance = view.findViewById<TextView>(R.id.tvBusDistance)           // 9. 기준점거리
        tvAdmName = view.findViewById<TextView>(R.id.tvAdmName)           // 주소
        tvTableTitle = view.findViewById<TextView>(R.id.tvTableTitle)



        val btnCopyMapLink = view.findViewById<Button>(R.id.btnCopyMapLink)//카카오맵 좌표

        val tableLayout = view.findViewById<TableLayout>(R.id.tableLayout)

        // 1. 새로 만든 '삭제' 단추에 클릭 리스너 연결
        //logDB 수집데이터 개별삭제
        val btnDeleteCurrentLog = view.findViewById<Button>(R.id.btnDeleteCurrentLog)


        //spotDB용 연결
        tvSpotTableTitle = view.findViewById(R.id.tvSpotTableTitle)
        tvSpAdmName = view.findViewById(R.id.tvSpAdmName)
        tvSpAdmCode = view.findViewById(R.id.tvSpAdmCode)
        tvSpStartTime = view.findViewById(R.id.tvSpStartTime)
        tvSpEndTime = view.findViewById(R.id.tvSpEndTime)
        tvSpSpotTime = view.findViewById(R.id.tvSpSpotTime)
        tvSpMemoId = view.findViewById(R.id.tvSpMemoId)

        val btnSpotPrev = view.findViewById<Button>(R.id.t_sp_button_prev)
        val btnSpotNext = view.findViewById<Button>(R.id.t_sp_button_next)
        val btnSpotJumpCalendar = view.findViewById<Button>(R.id.tvSpPageInfo)





        //logDB 개별삭제버튼
        btnDeleteCurrentLog.setOnClickListener {
            if (logList.isNotEmpty() && currentIndex in logList.indices) {

                val targetLog = logList[currentIndex]

                // 💡 아직 가공되지 않은 로그는 삭제하면 안 됨.
                // logProcessedAt == 0L인 로그는 SPOT 연결에 필요한
                // 마지막 원본 로그일 수 있기 때문에 삭제하면 새로운 SPOT으로 분리될 수 있음.
                if (targetLog.logProcessedAt == 0L) {
                    Toast.makeText(
                        requireContext(),
                        "아직 가공되지 않은 데이터는 삭제할 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val deletedIndex = currentIndex

                androidx.appcompat.app.AlertDialog.Builder(
                    requireContext(),
                    R.style.CustomAlertDialogStyle
                )
                    .setTitle("선택된 수집 데이터 한개 삭제")
                    .setMessage(
                        "현재 보고 있는 ${currentIndex + 1}번째 수집데이터를 삭제하시겠습니까?\n" +
                                "삭제를 진행하면 데이터를 재설정 해야되기 때문에 시간이 오래 걸릴 수 있습니다.\n" +
                                "홈화면으로 진입하면 재설정을 시작합니다."
                    )
                    .setPositiveButton("삭제") { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            val db = AppDatabase.getDatabase(requireContext())

                            // 1. 단독 개별 로그 삭제
                            db.logDao().deleteLogById(targetLog.logId)

                            // 2. 1차 가공 결과물인 SPOT DB 전체 삭제
                            db.spotDao().deleteAllSpots()

                            // 3. 가공된 데이터들을 다시 원본 상태로 초기화
                            db.logDao().resetAllLogsToRaw()

                            // 4. 순위 데이터 초기화
                            db.sumDao().전체SumDB삭제()

                            // 💡 삭제 후 돌아갈 인덱스 보정
                            currentIndex = when {
                                deletedIndex > 0 -> deletedIndex - 1
                                else -> 0
                            }

                            Toast.makeText(
                                requireContext(),
                                "해당 로그가 삭제되었습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .setNegativeButton("취소", null)
                    .show()
            } else {
                Toast.makeText(
                    requireContext(),
                    "삭제할 기록이 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }


        // 📅 '날짜 이동' 버튼 (또는 tvPageInfo 버튼) 클릭 시 캘린더 다이얼로그 띄우기
        val btnJumpCalendar = view.findViewById<Button>(R.id.tvPageInfo) // 또는 새로 만드신 날짜 이동 버튼 아이디

        btnJumpCalendar.setOnClickListener {
            // 1. 현재 날짜를 가져오기 위한 캘린더 인스턴스
            val calendar = java.util.Calendar.getInstance()

            // 2. 캘린더 다이얼로그 띄우기
            android.app.DatePickerDialog(
                requireContext(),
                R.style.CustomAlertDialogStyle,
                { _, year, month, dayOfMonth ->
                    // 선택한 날짜를 "yyyy-MM-dd" 형식으로 포맷팅
                    val selectedDateStr = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)

                    // 아까 만든 jumpToDate 함수로 날짜 전달해서 이동!
                    jumpToDate(selectedDateStr)
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
                    ).show()
        }

        // 즉시수집
        btnImmediateCollect.setOnClickListener {

            viewLifecycleOwner.lifecycleScope.launch {

                val db = AppDatabase.getDatabase(requireContext())

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
                            requireContext(),
                            "약 ${remainSeconds}초 후 수집 가능합니다.",
                            Toast.LENGTH_SHORT
                        ).show()

                        return@launch
                    }
                }


                // 즉시수집 실행
                Toast.makeText(
                    requireContext(),
                    "즉시 수집 요청됨",
                    Toast.LENGTH_SHORT
                ).show()


                // 포그라운드 서비스 다시 깨우기
                requireContext().startForegroundService(
                    Intent(requireContext(), ForegroundService::class.java).apply {
                        action = "ACTION_START"
                    }
                )

                val intent =
                    Intent(requireContext(), LogCollectService::class.java).apply {
                        action = "ACTION_COLLECT"
                    }

                requireContext().startService(intent)
            }

        }


        //카카오맵 링크
        btnCopyMapLink.setOnClickListener {
            if (logList.isNotEmpty() && currentIndex in logList.indices) {
                val log = logList[currentIndex]
                val lat = log.logGpsLat
                val lon = log.logGpsLon

                if (lat != 0.0 || lon != 0.0) {
                    // 카카오맵 마커 지정 URL 포맷 (이름, 위도, 경도)
                    val placeName = log.logBusStop.ifEmpty { "수집 위치" }
                    val mapUrl = "https://map.kakao.com/link/map/$placeName,$lat,$lon"

                    // 💡 프래그먼트에서는 Intent와 Uri를 명시하고 startActivity 바로 호출 가능!
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(mapUrl))

                    try {
                        startActivity(intent) // 👈 context. 빼고 깔끔하게 호출!
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "브라우저를 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }

                } else {
                    Toast.makeText(requireContext(), "유효한 좌표 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "표시할 기록이 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        //즐겨찾기 저장
        btnUserLocationSave.setOnClickListener {

            if (logList.isNotEmpty() && currentIndex in logList.indices) {

                val targetLog = logList[currentIndex]

                val editText = android.widget.EditText(requireContext()).apply {
                    hint = "위치명을 입력하세요"
                    setSingleLine(true)
                    setPadding(40, 20, 40, 20)
                }

                android.app.AlertDialog.Builder(requireContext(),R.style.CustomAlertDialogStyle)
                    .setTitle("위치 저장")
                    .setMessage("등록할 위치의 이름을 입력해주세요.")
                    .setView(editText)
                    .setNegativeButton("취소", null)
                    .setPositiveButton("저장") { _, _ ->

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
                                targetLog
                            )
                        }
                    }
                    .show()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                AppDatabase.getDatabase(requireContext()).logDao().getAllLogs().collect { list ->
                    // 1. 기존 인덱스 위치를 일단 백업해 둠 (없으면 0)
                    val previousIndex = currentIndex

                    logList = list

                    if (logList.isNotEmpty()) {
                        // 2. 만약 삭제 등으로 인해 리스트 크기가 줄어서 이전 인덱스가 범위를 벗어나면 맨 끝으로 맞추고,
                        //    아니면 형님이 보고 있던 그 자리를 그대로 유지!
                        currentIndex = when {
                            previousIndex < logList.size -> previousIndex
                            else -> logList.size - 1
                        }

                        displayLogData(
                            currentIndex, tvPageInfo, tvStartTime, tvEndTime, tvBusStop, tvAdmCode,
                            tvLocation, tvCellTower, tvWifi, tvStatus, tvGeofencing, tvGpsRange, tvBusDistance, tvAdmName, logList
                        )
                    } else {
                        currentIndex = 0
                        // 데이터가 싹 비었을 때 빈 화면 처리
                        tvStartTime.text = "- ~ -"
                        tvEndTime.text = "- ~ -"
                        tvBusStop.text = ""
                        tvAdmCode.text = ""
                        tvLocation.text = ""
                        tvCellTower.text = ""
                        tvWifi.text = ""
                        tvStatus.text = ""
                        tvGeofencing.text = ""
                        tvGpsRange.text = ""
                        tvBusDistance.text = ""
                        tvAdmName.text = ""
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }



        btnPrev.setOnClickListener {
            if (logList.isNotEmpty() && currentIndex < logList.size - 1) {
                currentIndex++
                displayLogData(
                    currentIndex, tvPageInfo, tvStartTime, tvEndTime, tvBusStop, tvAdmCode,
                    tvLocation, tvCellTower, tvWifi, tvStatus, tvGeofencing, tvGpsRange, tvBusDistance, tvAdmName,logList
                )
            } else {
                Toast.makeText(requireContext(), "마지막 기록입니다.", Toast.LENGTH_SHORT).show()
            }
        }

        //다음버튼 미래로 갈때.. 다섯번 누르면 관리자기능 활성화
        var firstRecordClickCount = 0
        btnNext.setOnClickListener {
            if (logList.isNotEmpty() && currentIndex > 0) {
                currentIndex--
                displayLogData(
                    currentIndex, tvPageInfo, tvStartTime, tvEndTime, tvBusStop, tvAdmCode,
                    tvLocation, tvCellTower, tvWifi, tvStatus, tvGeofencing, tvGpsRange, tvBusDistance,tvAdmName, logList
                )
            } else {
                firstRecordClickCount++

                if (firstRecordClickCount >= 5) {
                    btnApplyCleanBusData.visibility = View.VISIBLE
                    btnGenerateMockData.visibility = View.VISIBLE

                    Toast.makeText(requireContext(), "관리 기능이 표시되었습니다.", Toast.LENGTH_SHORT).show()

                    firstRecordClickCount = 0
                } else {
                    Toast.makeText(
                        requireContext(),
                        "첫 번째 기록입니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        btnApplyCleanBusData.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext(),R.style.CustomAlertDialogStyle)
                .setTitle("⚠️ 버스 정류장 데이터 삭제")
                .setMessage("기존 버스 정류장 데이터를 싹 비우고 프로그램 재실행")
                .setPositiveButton("실행") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {

                            // 1. DB 인스턴스 획득 (형님 말씀하신 그 진입점!)
                            val db = AppDatabase.getDatabase(requireContext())

                            // 2. 💥 기존 버스 정류장 테이블 데이터 싹 폭파!
                            db.busStopDao().deleteAllBusStops()


                            val packageManager = requireContext().packageManager
                            val intent = packageManager.getLaunchIntentForPackage(requireContext().packageName)
                            val componentName = intent?.component
                            val mainIntent = Intent.makeRestartActivityTask(componentName)
                            requireContext().startActivity(mainIntent)
                            Runtime.getRuntime().exit(0)} catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(requireContext(), "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }

        // 🧪 가상 데이터 생성 테스트 단추
        //val btnGenerateMockData = view.findViewById<Button>(R.id.btnGenerateMockData)
        btnGenerateMockData.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogStyle)
                .setTitle("테스트 데이터 생성 및 백그라운드 정리")
                .setMessage("백그라운드 수집 서비스와 워크매니저를 종료하고, 기존 데이터를 싹 지운 뒤 가상 데이터를 생성하시겠습니까?")
                .setPositiveButton("예") { _, _ ->
                    // 1. 🛑 포그라운드 서비스 자동 종료 인텐트 날리기
                    val stopIntent = Intent(requireContext(), ForegroundService::class.java).apply {
                        action = "ACTION_STOP"
                    }
                    requireContext().startService(stopIntent)

                    // 2. 🛑 워크매니저(WorkManager) 전체 작업 자동 취소
                    try {
                        androidx.work.WorkManager.getInstance(requireContext()).cancelAllWork()
                        Log.d("MoveFragment", "🧹 워크매니저 모든 예약 작업 강제 취소 완료")
                    } catch (e: Exception) {
                        Log.e("MoveFragment", "❌ 워크매니저 취소 실패: ${e.message}")
                    }

                    // 3. 🚀 기존 데이터 싹 밀어버리고 가상 데이터 수집 로직 실행!
                    viewLifecycleOwner.lifecycleScope.launch {
                        val db = AppDatabase.getDatabase(requireContext())

                        // 📌 [핵심] 가상 데이터 들어가기 전 묵은 때(로그 + 스팟) 청소 세트!
                        db.logDao().deleteAllLogs()
                        db.spotDao().deleteAllSpots()
                        // 4. 순위데이터 초기화
                        db.sumDao().전체SumDB삭제()


                        // 🚀 가상 데이터 생성 함수 호출
                        TestDataCollector.generatePastThreeDaysTestData(requireContext())

                        Toast.makeText(requireContext(), "기존 데이터 청소 후 가상 데이터 재생성 완료!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("아니오", null)
                .show()
        }
        //가상 데이터 생성 테스트 단추끝


        //spotDB 작업준비
        viewLifecycleOwner.lifecycleScope.launch {

            try {

                val db = AppDatabase.getDatabase(requireContext())

                spotList = withContext(Dispatchers.IO) {
                    db.spotDao().getAllSpots()
                }

                if (spotList.isNotEmpty()) {

                    spotCurrentIndex = 0

                    displaySpotData(
                        spotCurrentIndex,
                        spotList
                    )

                } else {

                    spotCurrentIndex = 0

                    tvSpotTableTitle.text = "📍 SPOT DB  (0 / 0)"
                    tvSpAdmName.text = ""
                    tvSpAdmCode.text = ""
                    tvSpStartTime.text = ""
                    tvSpEndTime.text = ""
                    tvSpSpotTime.text = ""
                    tvSpMemoId.text = ""
                }

            } catch (e: Exception) {

                Log.e(
                    "DatabaseFragment",
                    "❌ SPOT DB 조회 실패: ${e.message}",
                    e
                )
            }
        }

        //spotDB 다음버튼
        btnSpotNext.setOnClickListener {

            if (spotList.isNotEmpty() && spotCurrentIndex > 0) {

                spotCurrentIndex--

                displaySpotData(
                    spotCurrentIndex,
                    spotList
                )

            } else {

                Toast.makeText(
                    requireContext(),
                    "첫 번째 SPOT입니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        //spotDB 이전 버튼
        btnSpotPrev.setOnClickListener {

            if (spotList.isNotEmpty() && spotCurrentIndex < spotList.size - 1) {

                spotCurrentIndex++

                displaySpotData(
                    spotCurrentIndex,
                    spotList
                )

            } else {

                Toast.makeText(
                    requireContext(),
                    "마지막 SPOT입니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        //spotDB 날짜이동버튼
        btnSpotJumpCalendar.setOnClickListener {

            val calendar = java.util.Calendar.getInstance()

            android.app.DatePickerDialog(
                requireContext(),
                R.style.CustomAlertDialogStyle,
                { _, year, month, dayOfMonth ->

                    val selectedDateStr =
                        String.format(
                            "%04d-%02d-%02d",
                            year,
                            month + 1,
                            dayOfMonth
                        )

                    jumpToSpotDate(selectedDateStr)

                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)

            ).show()
        }

        //포그라운드를 켜기위한 타이틀이벤트
        /*tvTableTitle.setOnClickListener {

            val intent = Intent(
                requireContext(),
                ForegroundService::class.java
            ).apply {
                action = "ACTION_TOGGLE"
            }

            requireContext().startService(intent)
        }*/



        return view

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_LOAD_DATA && resultCode == android.app.Activity.RESULT_OK) {
            data?.data?.let { uri ->
                // 유저가 파일을 선택했다면 불러오기 함수 실행!
                loadAllDataFromDownloadFolder(uri)
            }
        }
    }
    private fun displayLogData(
        index: Int,
        tvPageInfo: TextView,
        // tvTime: TextView,  시작과 종료로 리뉴얼
        tvStartTime: TextView, // 👈 변경
        tvEndTime: TextView,   // 👈 추가
        tvBusStop: TextView,
        tvAdmCode: TextView,
        tvLocation: TextView,
        tvCellTower: TextView,
        tvWifi: TextView,
        tvStatus: TextView,
        tvGeofencing: TextView,
        tvGpsRange: TextView, // 💡 파라미터명 변경
        tvBusDistance: TextView,
        tvAdmName : TextView,
        list: List<VisitLog>
    ) {
        if (list.isEmpty() || index !in list.indices) return

        val log = list[index]
            //tvPageInfo.text = "${index + 1} [삭제] ${list.size}"


        tvTableTitle.text =
            "\uD83D\uDCE1 이동경로 데이터  (${index + 1} / ${list.size})"


        // 1. 시간 (12시간제 오전/오후 및 한 자리 숫자 보정 적용)
        // 시간 분리
        val dateFormat = java.text.SimpleDateFormat("a h:mm:ss M월-d일", java.util.Locale.KOREA)
        //1-1 시작시간 포멧팅
        val rawStartTimeStr = dateFormat.format(java.util.Date(log.logStartTime))
        val startTimeStr = rawStartTimeStr.replace(Regex(" ([1-9]):")) { matchResult ->
            " 0${matchResult.groupValues[1]}:"
        }
        tvStartTime.text = startTimeStr
        // 1-2. 종료 시간 포맷팅
        tvEndTime.text = if (log.logEndTime == 0L) {
            "-" // 또는 "진행 중"이나 "가공 전"으로 바꾸셔도 됩니다!
        } else {
            val rawEndTimeStr = dateFormat.format(java.util.Date(log.logEndTime))
            rawEndTimeStr.replace(Regex(" ([1-9]):")) { matchResult ->
                " 0${matchResult.groupValues[1]}:"
            }
        }

        //tvTime.text = "$startTimeStr ~ $endTimeStr" 시작과 종료시간으로 분리

        // 2. 정류장 과 주소를 나눔
        val admName = log.logAdmName.ifBlank { "" }
        val busStop = log.logBusStop.ifBlank { "" }
        tvAdmName.text = admName
        tvBusStop.text = "$busStop 인근"


        // 3. 행정동 코드
        tvAdmCode.text = log.logAdmCode.ifBlank { "" }

        // 4. 위치
        tvLocation.text = if (log.logGpsLat != 0.0 || log.logGpsLon != 0.0) {
            "위도: ${log.logGpsLat}, 경도: ${log.logGpsLon}"
        } else {
            ""
        }

        // 5. 기지국
        tvCellTower.text = log.logCellKey.ifBlank { "" }

        // 6. 와이파이
        tvWifi.text = log.logWifiMac.ifBlank { "" }

        // 7. 상태
        tvStatus.text = log.logStats.ifBlank { "" }

        // 8. 가공후 상태메시지
        // 8. 가공후 상태메시지 (밀리초 타임스탬프를 보기 편한 날짜/시간으로 변환!)
        tvGeofencing.text = if (log.logProcessedAt != 0L) {
            val processedDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            processedDateFormat.format(java.util.Date(log.logProcessedAt))
        } else {
            ""
        }

        // 9. GPS 오차값 표시 (미터 단위 추가)
        tvGpsRange.text = "${log.logGpsRange} m"

        tvBusDistance.text = "${log.logBusDistance} m"
        //"@+id/tvBusDistanc"
    }

    // 🎯 선택한 날짜(YYYY-MM-DD)와 일치하는 첫 번째 로그를 찾아 점프하는 함수
    private fun jumpToDate(targetDate: String) {
        if (logList.isEmpty()) {
            Toast.makeText(requireContext(), "표시할 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // logList에서 해당 날짜와 일치하는 첫 번째 항목의 인덱스 탐색
        val targetIndex = logList.indexOfFirst { log ->
            val logDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date(log.logStartTime))
            logDateStr == targetDate
        }

        if (targetIndex != -1) {
            currentIndex = targetIndex
            displayLogData(
                currentIndex, tvPageInfo, tvStartTime, tvEndTime, tvBusStop, tvAdmCode,
                tvLocation, tvCellTower, tvWifi, tvStatus, tvGeofencing, tvGpsRange, tvBusDistance, tvAdmName,logList
            )
            Toast.makeText(requireContext(), "$targetDate 기록으로 이동했습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "해당 날짜에 기록된 데이터가 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadAllDataFromDownloadFolder(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext()
                // 1. 파일 피커로 선택된 유저의 JSON 파일을 읽어오기
                val jsonString = context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }

                if (!jsonString.isNullOrEmpty()) {
                    val gson = Gson()
                    // 2. 바구니 객체 규격(HaruDatabase)으로 역직렬화
                    val backupData = gson.fromJson(jsonString, HaruDatabase::class.java)

                    val db = AppDatabase.getDatabase(context)

                    // 3. 💡 데이터 뻥튀기 방지: 기존 데이터를 싹 비우고 새 데이터로 덮어씌우기

                    // (1) 로그 데이터 복원
                    if (!backupData.logs.isNullOrEmpty()) {
                        db.logDao().deleteAllLogs() // 기존 로그 싹 비우기
                        backupData.logs.forEach { db.logDao().insert(it) }
                    }

                    // (2) 즐겨찾기 데이터 복원
                    if (!backupData.favorites.isNullOrEmpty()) {
                        db.favoriteDao().deleteAllFav() // 👈 형님 즐겨찾기 DAO의 전체 삭제 함수명에 맞게 조정 필요
                        backupData.favorites.forEach { db.favoriteDao().insert(it) }
                    }

                    // (3) 메모 데이터 복원
                    if (!backupData.memos.isNullOrEmpty()) {
                        db.memoDao().deleteAllMemo() // 👈 형님 메모 DAO의 전체 삭제 함수명에 맞게 조정 필요
                        backupData.memos.forEach { db.memoDao().메모저장id반환(it) } // 또는 insert 함수
                    }
                    //순위데이터 초기화
                    db.sumDao().전체SumDB삭제()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "데이터 불러오기 및 덮어쓰기 완료!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "불러오기 실패 (잘못된 파일이거나 형식 오류): ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private suspend fun registerUserLocation(
        context: Context,
        locationName: String,
        selected: VisitLog
    ) {
        val lat = selected.logGpsLat
        val lon = selected.logGpsLon
        val admCode = selected.logAdmCode
        val admCodeNm = selected.logAdmName

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
    
    //spotDB용
    private fun displaySpotData(
        index: Int,
        list: List<Spot>
    ) {

        if (list.isEmpty() || index !in list.indices) {
            return
        }

        val spot = list[index]

        tvSpotTableTitle.text =
            "📍 SPOT DB  (${index + 1} / ${list.size})"

        // -----------------------------------------
        // 행정동
        // -----------------------------------------
        tvSpAdmName.text =
            spot.spAdmName.ifBlank { "" }

        // -----------------------------------------
        // 행정동 코드
        // -----------------------------------------
        tvSpAdmCode.text =
            spot.spAdmCode.ifBlank { "" }

        // -----------------------------------------
        // 시간 표시
        // -----------------------------------------
        val dateFormat =
            java.text.SimpleDateFormat(
                "a h:mm:ss M월-d일",
                java.util.Locale.KOREA
            )

        tvSpStartTime.text =
            dateFormat.format(
                java.util.Date(spot.spStartTime)
            )

        tvSpEndTime.text =
            if (spot.spEndTime == 0L) {
                "-"
            } else {
                dateFormat.format(
                    java.util.Date(spot.spEndTime)
                )
            }

        // -----------------------------------------
        // 체류시간
        // spSpotTime은 밀리초
        // -----------------------------------------
        val stayMillis =
            spot.spSpotTime.coerceAtLeast(0L)

        val totalMinutes =
            stayMillis / (60L * 1000L)

        val hours =
            totalMinutes / 60L

        val minutes =
            totalMinutes % 60L

        tvSpSpotTime.text =
            when {
                hours > 0L ->
                    "${hours}시간 ${minutes}분"

                minutes > 0L ->
                    "${minutes}분"

                else ->
                    "0분"
            }

        // -----------------------------------------
        // 연결된 메모 ID
        // -----------------------------------------
        tvSpMemoId.text =
            spot.spMemoId.toString()
    }

    //spot 날짜 이동 함수
    private fun jumpToSpotDate(targetDate: String) {

        if (spotList.isEmpty()) {

            Toast.makeText(
                requireContext(),
                "표시할 SPOT 데이터가 없습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val targetIndex =
            spotList.indexOfFirst { spot ->

                val spotDateStr =
                    java.text.SimpleDateFormat(
                        "yyyy-MM-dd",
                        java.util.Locale.getDefault()
                    ).format(
                        java.util.Date(spot.spStartTime)
                    )

                spotDateStr == targetDate
            }

        if (targetIndex != -1) {

            spotCurrentIndex = targetIndex

            displaySpotData(
                spotCurrentIndex,
                spotList
            )

            Toast.makeText(
                requireContext(),
                "$targetDate SPOT으로 이동했습니다.",
                Toast.LENGTH_SHORT
            ).show()

        } else {

            Toast.makeText(
                requireContext(),
                "해당 날짜에 SPOT 데이터가 없습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

}

