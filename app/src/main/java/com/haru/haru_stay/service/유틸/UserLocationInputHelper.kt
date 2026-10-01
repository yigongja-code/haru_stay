package com.haru.haru_stay.service.유틸

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.haru.haru_stay.R
import com.haru.haru_stay.data.database.AppDatabase
import com.haru.haru_stay.data.entity.BusStopEntity
import com.haru.haru_stay.data.entity.Fav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


object UserLocationInputHelper {

    fun saveUserLocation(context: Context) {
        val activity = context as? AppCompatActivity ?: return

        activity.lifecycleScope.launchWhenResumed {
            // ============================================================
            // 1. 좌표 수집
            // ============================================================
            val collector = com.haru.haru_stay.service.collector.LocationCollector(context)
            val loc = collector.fetchBestLocation()
            val lat = loc?.latitude ?: 0.0
            val lon = loc?.longitude ?: 0.0

            // ============================================================
            // 2. 주소와 코드 수집
            // ============================================================
            val admCollector = com.haru.haru_stay.service.collector.AdmCollector(context)
            val admResult = admCollector.findCurrentLocationAdm(lat, lon)
            var admCode = admResult?.admCode ?: ""
            var admCodeNm = admResult?.admName ?: "주소를 찾지 못했습니다"

            android.util.Log.d("LocationCheck", "lat: $lat, lon: $lon")
            android.util.Log.d("LocationCheck", "admResult: $admResult, " + "code: ${admResult?.admCode}, " + "name: ${admResult?.admName}")

            // ============================================================
            // 3. 화면 크기
            // ============================================================
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val targetWidth = (screenWidth * 0.85f).toInt()
            val targetHeight = (screenHeight * 0.8f).toInt()
            val layerParams = FrameLayout.LayoutParams(targetWidth, targetHeight).apply { gravity = Gravity.CENTER }

            // ============================================================
            // 4. 레이어 참조
            // ============================================================
            var dimBackgroundRef: FrameLayout? = null
            var panelViewRef: LinearLayout? = null
            var panelScrollViewRef: ScrollView? = null

            // ============================================================
            // 5. 창 상태
            // ============================================================
            var isDialogOpen = true

            // ============================================================
            // 6. BACK 처리
            //
            // 기존 setOnKeyListener 방식이 아니라
            // Activity의 OnBackPressedDispatcher에서 직접 처리한다.
            // ============================================================
            var backCallback: OnBackPressedCallback? = null

            // ============================================================
            // 7. 공통 창 닫기
            // ============================================================
            val closeDialog: () -> Unit = {
                if (isDialogOpen) {
                    isDialogOpen = false
                    backCallback?.isEnabled = false
                    val decorView = activity.window.decorView as? FrameLayout
                    if (decorView != null) {
                        dimBackgroundRef?.let { if (it.parent != null) { decorView.removeView(it) } }
                        panelScrollViewRef?.let { if (it.parent != null) {
                            decorView.removeView(it)
                        }
                        }
                    }
                }
            }

            // ============================================================
            // 8. BACK callback 등록
            // ============================================================
            backCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isDialogOpen) {
                        closeDialog()
                    } else {
                        isEnabled = false
                        activity.onBackPressedDispatcher.onBackPressed()
                    }
                }
            }

            activity.onBackPressedDispatcher.addCallback(
                activity,
                backCallback!!
            )

            // ============================================================
            // 9. 딤 배경
            // ============================================================
            val dimBackgroundView = FrameLayout(context).apply {
                setBackgroundColor(Color.parseColor("#80000000"))
                isClickable = true
                isFocusable = true
                // 바깥 영역 클릭 → 창 닫기
                setOnClickListener {
                    closeDialog()
                }
            }

            dimBackgroundRef = dimBackgroundView

            // ============================================================
            // 10. 메인 패널
            // ============================================================
            val panelView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                elevation = 20f
                setPadding(40, 40, 40, 40)
                // 입력 포커스는 기존처럼 유지
                isFocusable = true
                isFocusableInTouchMode = true
            }

            // 제목
            val titleView = TextView(context).apply {
                text = "현재위치 즐겨찾기 등록"
                setTextColor(Color.BLACK)
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
            }

            panelView.addView(
                titleView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 24
                }
            )

            panelViewRef = panelView

            // ============================================================
            // 11. 첫 번째 줄
            // 이름 입력 + 확인
            // ============================================================
            val firstRowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 30)
            }

            val firstEditText = EditText(context).apply {
                hint = "이름을 입력(집,학교,직장)"
                textSize = 16f
                setTextColor(Color.BLACK)
                setSingleLine(true)
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
                )
            }

            val firstConfirmButton = android.widget.Button(context).apply {
                text = "확인"
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = 20
                }
            }

            // ============================================================
            // 12. 이름 저장
            // ============================================================
            firstConfirmButton.setOnClickListener {
                val locationName = firstEditText.text.toString().trim()

                if (locationName.isEmpty()) {
                    Toast.makeText(
                        context,
                        "이름을 입력해주세요!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                activity.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val db = AppDatabase.getDatabase(context)

                        // ------------------------------------------------
                        // busDB 저장
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
                        // UI 닫기
                        // ------------------------------------------------
                        withContext(Dispatchers.Main) {
                            closeDialog()

                            Toast.makeText(
                                context,
                                "'$locationName'(이)가 안전하게 저장되었습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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
            }

            firstRowLayout.addView(firstEditText)
            firstRowLayout.addView(firstConfirmButton)
            panelView.addView(firstRowLayout)

            // ============================================================
            // 13. 두 번째 줄
            // 주소 + 주소수정
            // ============================================================
            val secondRowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val secondTextView = TextView(context).apply {
                text = admCodeNm
                textSize = 16f
                setTextColor(Color.BLACK)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
                )
            }

            val secondConfirmButton = android.widget.Button(context).apply {
                text = "주소수정"
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = 20
                }
            }

            // ============================================================
            // 14. 주소 수정
            // ============================================================
            secondConfirmButton.setOnClickListener {
                val currentText = secondTextView.text.toString().trim()

                val defaultDong = if (currentText.contains(" ")) {
                    currentText.split(" ").last()
                } else {
                    currentText
                }

                val dialogInput = EditText(context).apply {
                    //setText(defaultDong) 기본 입력어 삭제
                    setSelection(text.length)
                    setPadding(40, 30, 40, 30)
                    hint = "검색 입력 (예:서울, 강남, 역삼 )"
                }

                AlertDialog.Builder(context, R.style.CustomAlertDialogStyle)
                    .setTitle("읍면동 검색")
                    .setView(dialogInput)
                    .setPositiveButton("확인") { _, _ ->
                        val inputStr = dialogInput.text.toString().trim()

                        if (inputStr.isNotEmpty()) {
                            val searchKeyword = inputStr.split(" ").last()

                            fetchAndShowBusStops(
                                context,
                                panelView,
                                searchKeyword,
                                secondTextView
                            ) { selectedName, selectedCode ->
                                admCodeNm = selectedName
                                admCode = selectedCode

                                android.util.Log.d(
                                    "LocationCheck",
                                    "선택된 최종 주소: " + "$admCodeNm, 코드: $admCode"
                                )
                            }
                        }
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }

            secondRowLayout.addView(secondTextView)
            secondRowLayout.addView(secondConfirmButton)
            panelView.addView(secondRowLayout)

            // ============================================================
            // 15. 구분선
            // ============================================================
            val dividerView = View(context).apply {
                setBackgroundColor(Color.parseColor("#E0E0E0"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2
                ).apply {
                    setMargins(0, 10, 0, 20)
                }
            }

            panelView.addView(dividerView)

            // ============================================================
            // 16. ScrollView
            // ============================================================
            val panelScrollView = ScrollView(context).apply {
                addView(panelView)
            }

            panelScrollViewRef = panelScrollView

            // ============================================================
            // 17. DecorView에 추가
            // ============================================================
            val decorView = activity.window.decorView as? FrameLayout

            if (decorView != null) {
                val fullParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )

                // 딤 배경
                decorView.addView(dimBackgroundView, fullParams)

                // 실제 패널
                decorView.addView(panelScrollView, layerParams)
            }

            // ============================================================
            // 18. 패널에 포커스
            // ============================================================
            panelView.isFocusable = true
            panelView.isFocusableInTouchMode = true
            panelView.requestFocus()
        }
    }


    // ====================================================================
    // 주소 검색 결과 표시
    // ====================================================================

    fun fetchAndShowBusStops(
        context: Context,
        panelView: LinearLayout,
        keyword: String,
        secondTextView: TextView,
        onStopSelected: (String, String) -> Unit
    ) {
        val activity = context as? AppCompatActivity

        activity?.lifecycleScope?.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(context)
            val admList = database.busStopDao().searchDistinctAdmByKeyword(keyword)

            withContext(Dispatchers.Main) {
                // --------------------------------------------------------
                // 기존 검색 결과 제거
                // --------------------------------------------------------
                if (panelView.childCount > 3) {
                    panelView.removeViews(3, panelView.childCount - 3)
                }

                // --------------------------------------------------------
                // 검색 결과 컨테이너
                // --------------------------------------------------------
                val resultContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 20
                    }
                }

                // --------------------------------------------------------
                // 안내 문구
                // --------------------------------------------------------
                val guideTextView = TextView(context).apply {
                    text = "※ 읍,면,동으로 검색해주세요 (oo리 단위 검색 불가)"
                    textSize = 13f
                    setTextColor(Color.parseColor("#E65100"))
                    setPadding(30, 20, 30, 20)
                    setBackgroundColor(Color.parseColor("#FFF3E0"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 0, 0, 10)
                    }
                }

                resultContainer.addView(guideTextView)

                // --------------------------------------------------------
                // 검색 결과 없음
                // --------------------------------------------------------
                if (admList.isEmpty()) {
                    val emptyView = TextView(context).apply {
                        text = "검색된 결과가 없습니다. 다시 해주세요"
                        textSize = 14f
                        setTextColor(Color.GRAY)
                    }

                    resultContainer.addView(emptyView)
                } else {
                    // ----------------------------------------------------
                    // 검색 결과 표시
                    // ----------------------------------------------------
                    for (stop in admList) {
                        val itemTextView = TextView(context).apply {
                            //text = "◼ ${stop.admName} (${stop.admCode})"
                            //법정동 코드 제거
                            text = "◼ ${stop.admName}"
                            textSize = 15f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(Color.BLACK)
                            setPadding(30, 25, 30, 25)
                            setBackgroundColor(Color.parseColor("#F5F5F5"))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, 10)
                            }
                        }

                        // ------------------------------------------------
                        // 주소 선택
                        // ------------------------------------------------
                        itemTextView.setOnClickListener {
                            secondTextView.text = stop.admName
                            onStopSelected(stop.admName, stop.admCode)

                            // 검색 결과만 제거
                            // 메인 창은 그대로 유지
                            panelView.removeView(resultContainer)
                        }

                        resultContainer.addView(itemTextView)
                    }
                }

                // --------------------------------------------------------
                // 결과 추가
                // --------------------------------------------------------
                panelView.addView(resultContainer)
            }
        }
    }
}