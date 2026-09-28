package com.example.haru_spot.ui.fragment

import android.app.DatePickerDialog
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.haru_spot.R
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import com.example.haru_spot.data.database.AppDatabase
import com.example.haru_spot.data.entity.VisitLog
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.MapGravity
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.TransformMethod
import com.kakao.vectormap.shape.MapPoints
import com.kakao.vectormap.shape.PolylineOptions
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior


class MoveFragment : Fragment(R.layout.fragment_move) {

    private lateinit var mapView: MapView

    // 현재 지도에서 보고 있는 날짜
    private var mapDate = LocalDate.now()

    // 지도에서 이동으로 인정할 최소 거리
    // 즐겨찾기 거리 기준과 동일하게 20m 사용
    private companion object {
        const val MAP_MOVE_DISTANCE = 20.0
    }

    //지도의 줌값
    private var mapZoom = 13
    private var previousMapZoom = 13


    private var currentMoveLogs = emptyList<VisitLog>()

    // 현재 준비된 카카오 지도
    private var kakaoMap: KakaoMap? = null

    // 바텀시트에 경로표시용
    private lateinit var pathRecyclerView: RecyclerView
    private lateinit var pathAdapter: MapPathAdapter

    // 바텀시트 위치 제어
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    // 위쪽 앵커 위치
    // 화면 기준 시트 상단 위치
    private var bottomSheetAnchors = emptyList<Int>()

    //바텀시트 중간값 크기 0.3은 30%크기
    private val bottomSheetCenter = 0.40f


    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        //뒤로가기 누르면 홈으로 가기
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

        mapView = view.findViewById(R.id.map_view)

        // ---------------------------------------------------------
        // 날짜 UI
        // ---------------------------------------------------------

        val btnPrevDate =
            view.findViewById<View>(R.id.btn_prev_date)

        val tvMapDate =
            view.findViewById<TextView>(R.id.tv_map_date)

        val btnNextDate =
            view.findViewById<View>(R.id.btn_next_date)

        // 현재 날짜 표시
        updateMapDate(
            tvMapDate,
            btnNextDate
        )

        // 이전 날짜
        btnPrevDate.setOnClickListener {

            mapDate = mapDate.minusDays(1)

            updateMapDate(
                tvMapDate,
                btnNextDate
            )

            // 선택된 날짜의 지도 데이터를 새로 읽는다.
            loadMapData()
        }

        // 다음 날짜
        // 오늘보다 미래로는 이동하지 않음
        btnNextDate.setOnClickListener {

            val today = LocalDate.now()

            if (mapDate.isBefore(today)) {

                mapDate = mapDate.plusDays(1)

                updateMapDate(
                    tvMapDate,
                    btnNextDate
                )

                // 선택된 날짜의 지도 데이터를 새로 읽는다.
                loadMapData()
            }
        }

        // 날짜를 직접 누르면 캘린더 표시
        tvMapDate.setOnClickListener {

            showDatePicker(
                tvMapDate,
                btnNextDate
            )
        }

        //리스트 뿌리기 관련
        pathRecyclerView =
            view.findViewById(R.id.recycler_map_path)

        pathAdapter =
            MapPathAdapter(
                onItemClick = { log ->

                    // 선택한 로그 위치
                    val position =
                        LatLng.from(
                            log.logGpsLat,
                            log.logGpsLon
                        )

                    // BottomSheet가 50% 상태일 때만
                    // 아래쪽 BottomSheet 영역을 지도에서 제외한다.
                    if (
                        bottomSheetBehavior.state ==
                        BottomSheetBehavior.STATE_HALF_EXPANDED
                    ) {

                        kakaoMap?.setPadding(
                            0,
                            0,
                            0,
                            (mapView.height * bottomSheetCenter).toInt()
                        )

                    } else {

                        // 50% 상태가 아니면
                        // 기존처럼 지도 정중앙으로 이동한다.
                        kakaoMap?.setPadding(
                            0,
                            0,
                            0,
                            0
                        )
                    }

                    kakaoMap?.moveCamera(
                        CameraUpdateFactory.newCenterPosition(
                            position
                        )
                    )
                }
            )

        pathRecyclerView.layoutManager =
            LinearLayoutManager(requireContext())

        pathRecyclerView.adapter =
            pathAdapter

        // ---------------------------------------------------------
        // Kakao 지도
        // ---------------------------------------------------------

        mapView.start(

            object : MapLifeCycleCallback() {

                override fun onMapDestroy() {
                    // 지도 정상 종료
                }

                override fun onMapError(
                    error: Exception
                ) {
                    error.printStackTrace()
                }
            },

            object : KakaoMapReadyCallback() {

                override fun onMapReady(
                    kakaoMap: KakaoMap
                ) {
                    // 지도 회전 제스처 차단
                    // 손가락으로 지도를 돌려도 방향이 회전하지 않도록 함
                    kakaoMap.setGestureEnable(GestureType.Rotate, false)

                    // 회전 + 확대/축소가 함께 되는 제스처 차단
                    // 두 손가락 제스처로 지도가 회전하지 않도록 함
                    kakaoMap.setGestureEnable(GestureType.RotateZoom, false)

                    // 지도 기울이기(Tilt) 제스처 차단
                    // 두 손가락으로 위아래로 밀어서 지도가 3D처럼 기울어지는 것을 방지
                    kakaoMap.setGestureEnable(GestureType.Tilt, false)

                    // 현재 지도 방향을 확인할 수 있도록 나침반 표시
                    kakaoMap.getCompass()?.show()
                    // 현재 준비된 지도 저장
                    this@MoveFragment.kakaoMap = kakaoMap

                    // 지도에 실제 거리를 확인할 수 있도록 축척 표시
                    kakaoMap.getScaleBar()?.apply {
                        show()
                        setAutoHide(false)
                        setPosition(
                            MapGravity.BOTTOM or MapGravity.CENTER_HORIZONTAL,
                            -400f,
                            180f
                        )
                    }

                    kakaoMap.setOnCameraMoveEndListener(
                        object : KakaoMap.OnCameraMoveEndListener {

                            override fun onCameraMoveEnd(
                                kakaoMap: KakaoMap,
                                cameraPosition: com.kakao.vectormap.camera.CameraPosition,
                                gestureType: GestureType
                            ) {

                                val currentZoom =
                                    cameraPosition.zoomLevel

                                if (currentZoom != mapZoom) {

                                    mapZoom = currentZoom

                                    showMoveLogsOnMap(
                                        currentMoveLogs,
                                        false
                                    )
                                }

                                android.util.Log.d(
                                    "HaruMap",
                                    "실시간 줌값 = $currentZoom"
                                )
                            }
                        }
                    )

                    // 현재 날짜의 데이터를 지도에 표시
                    loadMapData()
                }
            }
        )

        // 바텀시트
        val bottomSheet =
            view.findViewById<FrameLayout>(R.id.bottom_sheet)

        bottomSheetBehavior =
            BottomSheetBehavior.from(bottomSheet)

// XML에 설정해 둔 60dp 최소 표시 높이는 그대로 사용
        bottomSheetBehavior.state =
            BottomSheetBehavior.STATE_COLLAPSED

        bottomSheet.post {

            val screenHeight =
                (bottomSheet.parent as View).height

            // 화면의 10% 지점까지만 올라가도록 제한 바텀시트 90% 크기
            // 즉 BottomSheet 상단이 화면 최상단까지 올라가지 않는다.
            bottomSheetBehavior.isFitToContents = false
            bottomSheetBehavior.expandedOffset =
                (screenHeight * 0.10f).toInt()

            // 중간 위치는 화면 bottomSheetCenter 변수를 변경해서 크리조절
            bottomSheetBehavior.halfExpandedRatio = bottomSheetCenter
        }
        var bottomPadding = 0
        //드레그후 손을 놓았을때 붙이기
        bottomSheetBehavior.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {

                override fun onStateChanged(
                    bottomSheet: View,
                    newState: Int
                ) {

                    when (newState) {

                        // 90% 상태
                        BottomSheetBehavior.STATE_EXPANDED -> {

                            pathRecyclerView.setPadding(
                                0,
                                0,
                                0,
                                200
                            )
                        }

                        // bottomSheetCenter 상태 - 변경가능(bottomSheetCenter수정)
                        BottomSheetBehavior.STATE_HALF_EXPANDED -> {

                            bottomPadding =
                                (
                                        1000 -
                                                ((bottomSheetCenter - 0.5f) / 0.1f * 200)
                                        ).toInt()

                            pathRecyclerView.setPadding(
                                0,
                                0,
                                0,
                                bottomPadding
                            )
                        }

                        // 최소 상태
                        BottomSheetBehavior.STATE_COLLAPSED -> {

                            pathRecyclerView.setPadding(
                                0,
                                0,
                                0,
                                500
                            )
                        }
                    }
                }

                override fun onSlide(
                    bottomSheet: View,
                    slideOffset: Float
                ) {
                }
            }
        )
    }

    /**
     * 현재 선택된 날짜를 화면에 표시하고
     * 오늘 날짜라면 다음 날짜 버튼을 비활성화한다.
     */
    private fun updateMapDate(
        tvMapDate: TextView,
        btnNextDate: View
    ) {

        tvMapDate.text =
            "${mapDate.monthValue}월 ${mapDate.dayOfMonth}일"

        // 오늘이면 미래 날짜로 이동할 수 없음
        btnNextDate.isEnabled =
            mapDate.isBefore(LocalDate.now())
    }

    /**
     * 날짜 선택 캘린더를 표시한다.
     *
     * 오늘 이후 날짜는 선택할 수 없도록 maxDate를 오늘로 제한한다.
     */
    private fun showDatePicker(
        tvMapDate: TextView,
        btnNextDate: View
    ) {

        val today = LocalDate.now()

        val datePicker = DatePickerDialog(
            requireContext(),
            R.style.CustomAlertDialogStyle,

            { _, year, month, dayOfMonth ->

                // DatePicker의 month는 0부터 시작하므로 +1
                mapDate = LocalDate.of(
                    year,
                    month + 1,
                    dayOfMonth
                )

                updateMapDate(
                    tvMapDate,
                    btnNextDate
                )

                // 캘린더에서 선택한 날짜의 지도 데이터를 읽는다.
                loadMapData()
            },

            mapDate.year,
            mapDate.monthValue - 1,
            mapDate.dayOfMonth
        )

        // 미래 날짜 선택 금지
        datePicker.datePicker.maxDate =
            today
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()

        datePicker.show()
    }

    /**
     * 현재 mapDate에 해당하는 하루의 logDB를 가져온다.
     *
     * 처리 순서
     *
     * 1. 해당 날짜의 logDB 조회
     * 2. 시작시간 순으로 정렬된 상태로 받음
     * 3. 첫 번째 좌표를 기준점으로 사용
     * 4. 다음 좌표와 기준점 사이의 거리 계산
     * 5. 20m 이하이면 같은 위치로 보고 버림
     * 6. 20m를 초과하면 새로운 이동점으로 채택
     * 7. 채택된 좌표만 지도에 표시
     *
     * 원본 logDB는 변경하지 않는다.
     */
    private fun loadMapData() {

        // 현재 선택된 날짜
        val targetDate = mapDate

        val startTime = targetDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val endTime = targetDate
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        viewLifecycleOwner.lifecycleScope.launch {

            val moveLogs = withContext(Dispatchers.IO) {

                val db =
                    AppDatabase.getDatabase(requireContext())

                val logs =
                    db.logDao().getLogsByStartTime(
                        startTime = startTime,
                        endTime = endTime
                    )

                makeMoveLogs(logs)
            }

            // 현재 지도 데이터를 전역 변수에 저장
            currentMoveLogs = moveLogs

            // 바텀시트 경로 목록 갱신
            pathAdapter.submitList(currentMoveLogs)

            // 지도에 최종 이동점 표시
            showMoveLogsOnMap(currentMoveLogs)
        }
    }

    /**
     * 15분 간격으로 쌓인 logDB에서
     * 20m를 초과해서 이동한 좌표만 추린다.
     *
     * 복잡한 그룹화는 하지 않는다.
     *
     * 첫 번째 로그를 기준점으로 잡고
     * 다음 로그와의 거리를 비교한다.
     */
    private fun makeMoveLogs(
        logs: List<VisitLog>
    ): List<VisitLog> {

        if (logs.isEmpty()) {
            return emptyList()
        }


        val result = mutableListOf<VisitLog>()

        // 첫 번째 로그는 무조건 표시
        var referenceLog = logs.first()

        result.add(referenceLog)

        // 두 번째 로그부터 비교
        for (log in logs.drop(1)) {

            val distance =
                distanceBetween(
                    referenceLog,
                    log
                )

            // 20m를 초과하면 새로운 이동점
            if (distance > MAP_MOVE_DISTANCE) {

                result.add(log)

                // 새로 채택된 로그를 다음 기준점으로 사용
                referenceLog = log
            }
        }

        return result
    }

    /**
     * 두 VisitLog 좌표 사이의 실제 거리(m)
     */
    private fun distanceBetween(
        first: VisitLog,
        second: VisitLog
    ): Double {

        val result = FloatArray(1)

        Location.distanceBetween(
            first.logGpsLat,
            first.logGpsLon,
            second.logGpsLat,
            second.logGpsLon,
            result
        )

        return result[0].toDouble()
    }

    /**
     * 최종 이동점을 카카오 지도에 표시한다.
     *
     * 번호는 표시하지 않는다.
     * 이동점 위치만 작은 점으로 표시한다.
     *
     * 날짜를 변경하면 기존 점을 모두 지우고
     * 새 날짜의 점을 다시 표시한다.
     */
    private fun showMoveLogsOnMap(
        logs: List<VisitLog>,
        moveCamera: Boolean = true
    ) {

        val map = kakaoMap ?: return

        // 이전 날짜의 이동점 제거
        // 이전 날짜의 이동점 + 화살표 제거
        val layer =
            map.labelManager?.getLayer() ?: return

        layer.removeAll()

// 이전 날짜의 이동 경로 선 제거
        map.shapeManager
            ?.getLayer()
            ?.removeAll()

        if (logs.isEmpty()) {
            return
        }

        // ---------------------------------------------------------
        // 첫 번째 이동점으로 지도 중심 이동
        // ---------------------------------------------------------

        /*val firstLog = logs.first()

        val firstPosition =
            LatLng.from(
                firstLog.logGpsLat,
                firstLog.logGpsLon
            ) */

        if (moveCamera) {

            val firstLog = logs.first()

            val firstPosition =
                LatLng.from(
                    firstLog.logGpsLat,
                    firstLog.logGpsLon
                )

            map.moveCamera(
                CameraUpdateFactory.newCenterPosition(firstPosition)
            )
        }

        // 지도 확대/축소 정도
        //범위는 10~21  숫자가 적을수록 줌아웃 - 많이 바뀌니까 1씩 바꾸면서 확인
        //map.moveCamera(
        //    CameraUpdateFactory.zoomTo(13)
        //)

        // ---------------------------------------------------------
        // 이동점 표시용 스타일
        // ---------------------------------------------------------

        val styles =
            map.labelManager?.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from()
                        .setTextStyles(
                            20,
                            android.graphics.Color.BLACK
                        )
                )
            ) ?: return

        // ---------------------------------------------------------
        // 최종 이동점을 지도에 표시
        // ---------------------------------------------------------

        for (log in logs) {

            val position =
                LatLng.from(
                    log.logGpsLat,
                    log.logGpsLon
                )

            val options =
                LabelOptions
                    .from(position)
                    .setStyles(styles)
                    .setTexts(
                        LabelTextBuilder()
                            .setTexts("●")
                    )

            layer.addLabel(options)
        }


        // ---------------------------------------------------------
        // 이동 경로 선 표시
        // ---------------------------------------------------------
        //함수로 뺌
        kakaoLine(map, logs, mapZoom)

    }

    override fun onResume() {
        super.onResume()

        if (::mapView.isInitialized) {
            mapView.resume()
        }
    }

    override fun onPause() {

        if (::mapView.isInitialized) {
            mapView.pause()
        }

        super.onPause()
    }

    //점과 점사이 라인을 구성함(폴리라인)
    //화면에 보이는 부분만 화살표를 그려서 확대 배율시 화살표가 많이 생기지 않게함

    private fun kakaoLine(
        map: KakaoMap,
        logs: List<VisitLog>,
        zoom: Int
    ) {

        val arrowDistance =
            when {
                zoom == 6 -> 40000.0
                zoom == 7 -> 20000.0
                zoom == 8 -> 10000.0
                zoom == 9 -> 4000.0
                zoom == 10 -> 2000.0
                zoom == 11 -> 1000.0
                zoom == 12 -> 400.0
                zoom == 13 -> 200.0
                zoom == 14 -> 100.0
                else -> 50.0
            }

        if (logs.size < 2) return

        val labelLayer =
            map.labelManager?.getLayer() ?: return

        // ---------------------------------------------------------
        // 현재 화면 영역 확인
        // ---------------------------------------------------------
        val viewport = map.getViewport()




        // 화면 가장자리에서 화살표가 너무 딱 끊기지 않도록
        // 약간 여유를 둔다.
        // aa로 화면밖의 여유를 준다
        val aa = 3000

        val left = viewport.left - aa
        val top = viewport.top - aa
        val right = viewport.right + aa
        val bottom = viewport.bottom + aa

        // ---------------------------------------------------------
        // "▶" 표시용 스타일
        // ---------------------------------------------------------
        val arrowStyles =
            map.labelManager?.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from()
                        .setTextStyles(
                            12,
                            android.graphics.Color.RED
                        )
                )
            ) ?: return

        var arrowRemain = 0.0

        // ---------------------------------------------------------
        // 로그와 로그 사이를 하나씩 처리
        // ---------------------------------------------------------
        for (i in 0 until logs.lastIndex) {

            val start = logs[i]
            val end = logs[i + 1]

            val distance =
                distanceBetween(start, end)

            if (distance <= 0.0) continue

            // -----------------------------------------------------
            // 현재 선분의 화면 좌표
            // -----------------------------------------------------
            val startPoint =
                map.toScreenPoint(
                    LatLng.from(
                        start.logGpsLat,
                        start.logGpsLon
                    )
                )

            val endPoint =
                map.toScreenPoint(
                    LatLng.from(
                        end.logGpsLat,
                        end.logGpsLon
                    )
                )

            if (startPoint == null || endPoint == null) {
                arrowRemain =
                    (arrowRemain + distance) % arrowDistance
                continue
            }

            // -----------------------------------------------------
            // 화면 안에 들어오는 선분 구간을 계산한다.
            //
            // t = 0.0 → start
            // t = 1.0 → end
            // -----------------------------------------------------
            val visibleRange =
                clipLineToViewport(
                    startPoint.x.toDouble(),
                    startPoint.y.toDouble(),
                    endPoint.x.toDouble(),
                    endPoint.y.toDouble(),
                    left.toDouble(),
                    top.toDouble(),
                    right.toDouble(),
                    bottom.toDouble()
                )

            // 화면을 전혀 통과하지 않는 선분
            if (visibleRange == null) {

                arrowRemain =
                    (arrowRemain + distance) % arrowDistance

                continue
            }

            val visibleStartDistance =
                distance * visibleRange.first

            val visibleEndDistance =
                distance * visibleRange.second

            // -----------------------------------------------------
            // 로그 사이 거리가 화살표 간격보다 짧으면
            // 기존처럼 가운데에 화살표 하나 표시
            // -----------------------------------------------------
            if (distance < arrowDistance) {

                val arrowPosition = distance * 0.5

                if (
                    arrowPosition >= visibleStartDistance &&
                    arrowPosition <= visibleEndDistance
                ) {

                    val ratio =
                        arrowPosition / distance

                    addArrow(
                        labelLayer = labelLayer,
                        arrowStyles = arrowStyles,
                        start = start,
                        end = end,
                        ratio = ratio
                    )
                }

                arrowRemain =
                    (arrowRemain + distance) % arrowDistance

                continue
            }

            // -----------------------------------------------------
            // 화면 안에서 실제로 필요한 화살표 위치만 계산
            //
            // 기존처럼 100km 전체를 while 돌지 않는다.
            // -----------------------------------------------------
            var arrowPosition =
                arrowDistance - arrowRemain

            if (arrowPosition < visibleStartDistance) {

                val skipCount =
                    kotlin.math.ceil(
                        (visibleStartDistance - arrowPosition) /
                                arrowDistance
                    ).toLong()

                arrowPosition +=
                    skipCount * arrowDistance
            }

            // -----------------------------------------------------
            // 화면 안에 들어오는 화살표만 생성
            // -----------------------------------------------------
            while (arrowPosition <= visibleEndDistance) {

                val ratio =
                    arrowPosition / distance

                addArrow(
                    labelLayer = labelLayer,
                    arrowStyles = arrowStyles,
                    start = start,
                    end = end,
                    ratio = ratio
                )

                arrowPosition += arrowDistance
            }

            // -----------------------------------------------------
            // 다음 선분에서도 화살표 간격이 이어지도록 유지
            // -----------------------------------------------------
            arrowRemain =
                (arrowRemain + distance) % arrowDistance
        }


        // ---------------------------------------------------------
        // 전체 이동 경로는 기존처럼 Polyline 하나로 표시
        // ---------------------------------------------------------

        val shapeLayer =
            map.shapeManager
                ?.getLayer()
                ?: return

        val routePoints =
            MapPoints.fromLatLng(
                logs.map { log ->
                    LatLng.from(
                        log.logGpsLat,
                        log.logGpsLon
                    )
                }
            )

        val routeOptions =
            PolylineOptions.from(
                routePoints,
                2.5f,
                android.graphics.Color.RED
            )

        shapeLayer.addPolyline(routeOptions)
    }

    //화살표를 생성하는 보조함수
    private fun addArrow(
        labelLayer: com.kakao.vectormap.label.LabelLayer,
        arrowStyles: LabelStyles,
        start: VisitLog,
        end: VisitLog,
        ratio: Double
    ) {

        val arrowLat =
            start.logGpsLat +
                    (end.logGpsLat - start.logGpsLat) * ratio

        val arrowLon =
            start.logGpsLon +
                    (end.logGpsLon - start.logGpsLon) * ratio

        val arrow =
            labelLayer.addLabel(
                LabelOptions
                    .from(
                        LatLng.from(
                            arrowLat,
                            arrowLon
                        )
                    )
                    .setStyles(arrowStyles)
                    .setTexts(
                        LabelTextBuilder()
                            .setTexts("▶")
                    )
                    .setTransform(
                        TransformMethod.AbsoluteRotation
                    )
            )

        val angle =
            kotlin.math.atan2(
                end.logGpsLon - start.logGpsLon,
                end.logGpsLat - start.logGpsLat
            )

        arrow?.rotateTo(
            (angle - Math.PI / 2.0).toFloat()
        )
    }

    //화면과 선분이 만나는 구간 계산 함수
    private fun clipLineToViewport(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        left: Double,
        top: Double,
        right: Double,
        bottom: Double
    ): Pair<Double, Double>? {

        val dx = x2 - x1
        val dy = y2 - y1

        var tMin = 0.0
        var tMax = 1.0

        fun update(p: Double, q: Double): Boolean {

            if (p == 0.0) {
                return q >= 0.0
            }

            val r = q / p

            if (p < 0.0) {
                if (r > tMax) return false
                if (r > tMin) tMin = r
            } else {
                if (r < tMin) return false
                if (r < tMax) tMax = r
            }

            return true
        }

        if (!update(-dx, x1 - left)) return null
        if (!update(dx, right - x1)) return null
        if (!update(-dy, y1 - top)) return null
        if (!update(dy, bottom - y1)) return null

        return tMin to tMax
    }
    /*private fun kakaoLine(
        map: KakaoMap,
        logs: List<VisitLog>,
        zoom: Int
    ) {

        val arrowDistance =
            when {
                zoom == 6 -> 2000.0
                zoom == 7 -> 2000.0
                zoom == 8 -> 2000.0
                zoom == 9 -> 1000.0
                zoom == 10 -> 500.0
                zoom == 11 -> 400.0
                zoom == 12 -> 400.0
                zoom == 13 -> 200.0
                zoom == 14 -> 100.0
               // zoom == 15 -> 50.0
               // zoom == 16 -> 25.0
               // zoom >= 17 -> 10.0
                // 화살표 빈도수
                else -> 50.0
            }

        if (logs.size < 2) return

        val labelLayer =
            map.labelManager?.getLayer() ?: return

        // ---------------------------------------------------------
        // "▶" 표시용 스타일
        // ---------------------------------------------------------

        val arrowStyles =
            map.labelManager?.addLabelStyles(
                LabelStyles.from(
                    LabelStyle.from()
                        .setTextStyles(
                            12,
                            android.graphics.Color.RED
                        )
                )
            ) ?: return

        var arrowRemain = 0.0

        // ---------------------------------------------------------
        // 로그와 로그 사이를 하나씩 처리
        // ---------------------------------------------------------

        for (i in 0 until logs.lastIndex) {

            val start = logs[i]
            val end = logs[i + 1]

            val distance =
                distanceBetween(start, end)

            if (distance <= 0.0) continue

            // 줌 12 이하에서는 화살표를 그리지 않는다.
            //if (zoom <= 12) {
            //    continue
            //}

            // -----------------------------------------------------
            // 로그 사이 거리가 화살표 간격보다 짧으면
            // 가운데에 화살표 하나만 표시
            // -----------------------------------------------------

            if (distance < arrowDistance) {

                val ratio = 0.5

                val arrowLat =
                    start.logGpsLat +
                            (end.logGpsLat - start.logGpsLat) * ratio

                val arrowLon =
                    start.logGpsLon +
                            (end.logGpsLon - start.logGpsLon) * ratio

                val arrow =
                    labelLayer.addLabel(
                        LabelOptions
                            .from(
                                LatLng.from(
                                    arrowLat,
                                    arrowLon
                                )
                            )
                            .setStyles(arrowStyles)
                            .setTexts(
                                LabelTextBuilder()
                                    .setTexts("▶")
                            )
                            .setTransform(
                                TransformMethod.AbsoluteRotation
                            )
                    )

                // 진행 방향 계산
                val angle =
                    kotlin.math.atan2(
                        end.logGpsLon - start.logGpsLon,
                        end.logGpsLat - start.logGpsLat
                    )

                arrow?.rotateTo(
                    (angle - Math.PI / 2.0).toFloat()
                )

                // 이 짧은 선분의 실제 거리도 다음 계산에 반영
                arrowRemain =
                    (arrowRemain + distance) % arrowDistance

                continue
            }

            // -----------------------------------------------------
            // 진행방향 "▶" 표시
            // -----------------------------------------------------

            var arrowPosition =
                arrowDistance - arrowRemain

            while (arrowPosition < distance) {

                val ratio =
                    arrowPosition / distance

                val arrowLat =
                    start.logGpsLat +
                            (end.logGpsLat - start.logGpsLat) * ratio

                val arrowLon =
                    start.logGpsLon +
                            (end.logGpsLon - start.logGpsLon) * ratio

                val arrow =
                    labelLayer.addLabel(
                        LabelOptions
                            .from(
                                LatLng.from(
                                    arrowLat,
                                    arrowLon
                                )
                            )
                            .setStyles(arrowStyles)
                            .setTexts(
                                LabelTextBuilder()
                                    .setTexts("▶")
                            )
                            .setTransform(
                                TransformMethod.AbsoluteRotation
                            )
                    )

                // -------------------------------------------------
                // 현재 선분의 진행 방향 계산
                // -------------------------------------------------

                val angle =
                    kotlin.math.atan2(
                        end.logGpsLon - start.logGpsLon,
                        end.logGpsLat - start.logGpsLat
                    )

                // "▶" 문자의 기본 방향을 실제 이동 방향으로 회전
                arrow?.rotateTo(
                    (angle - Math.PI / 2.0).toFloat()
                )

                arrowPosition += arrowDistance
            }

            // -----------------------------------------------------
            // 다음 선분으로 넘어가도
            // 화살표 간격이 끊기지 않도록 남은 거리를 기억
            // -----------------------------------------------------

            arrowRemain =
                (arrowRemain + distance) % arrowDistance
        }

        // ---------------------------------------------------------
// 이동 경로 선 표시
// ---------------------------------------------------------
// 모든 이동 로그를 하나의 Polyline으로 연결한다.
//
// 기존에는
// 10m 선 → 12m 공백 → 10m 선 → 12m 공백...
// 방식으로 수백 개의 Polyline을 직접 만들어 점선을 표현했다.
//
// 이제는 전체 경로를 Polyline 하나로 그린다.
// 날짜 변경이나 줌 변경 때 생성/삭제해야 할 객체도 크게 줄어든다.
// ---------------------------------------------------------

        val shapeLayer =
            map.shapeManager
                ?.getLayer()
                ?: return

        val routePoints =
            MapPoints.fromLatLng(
                logs.map { log ->
                    LatLng.from(
                        log.logGpsLat,
                        log.logGpsLon
                    )
                }
            )

        val routeOptions =
            PolylineOptions.from(
                routePoints,
                2.5f,
                android.graphics.Color.RED
            )

        shapeLayer.addPolyline(routeOptions)

    } */

    private class MapPathAdapter(
        private val onItemClick: (VisitLog) -> Unit
    ) : RecyclerView.Adapter<MapPathAdapter.PathViewHolder>() {

        private var items = emptyList<VisitLog>()

        fun submitList(newItems: List<VisitLog>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): PathViewHolder {

            val view =
                LayoutInflater.from(parent.context)
                    .inflate(
                        R.layout.fragment_move_item_map_path,
                        parent,
                        false
                    )

            return PathViewHolder(view)
        }

        override fun onBindViewHolder(
            holder: PathViewHolder,
            position: Int
        ) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int {
            return items.size
        }

        inner class PathViewHolder(
            itemView: View
        ) : RecyclerView.ViewHolder(itemView) {

            private val tvAddress =
                itemView.findViewById<TextView>(
                    R.id.tv_path_address
                )

            private val tvTime =
                itemView.findViewById<TextView>(
                    R.id.tv_path_time
                )

            private val tvBusStop =
                itemView.findViewById<TextView>(
                    R.id.tv_path_bus_stop
                )

            fun bind(log: VisitLog) {

                tvAddress.text =
                    log.logAdmName

                tvTime.text =
                    java.text.SimpleDateFormat(
                        "HH:mm",
                        java.util.Locale.KOREA
                    ).format(
                        java.util.Date(log.logStartTime)
                    )

                // 버스정류장 필드는 실제 VisitLog 필드 확인 후 연결
                tvBusStop.text =
                    log.logBusStop

                itemView.setOnClickListener {
                    onItemClick(log)
                }
            }
        }
    }
}
