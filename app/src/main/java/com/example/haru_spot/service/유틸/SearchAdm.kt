package com.example.haru_spot.service.유틸

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.haru_spot.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SearchAdm {

    // ============================================================
    // 검색창 상태 유지
    // ============================================================

    private var currentActivity: WeakReference<AppCompatActivity>? = null

    // 실제 검색창
    private var currentPanel: WeakReference<View>? = null

    // 어두운 배경
    private var currentDim: WeakReference<View>? = null


    // ============================================================
    // View 쪽으로 전달할 콜백
    // ============================================================

    // 검색 결과에서 시간을 선택했을 때
    private var currentOnTimeSelected: ((Long) -> Unit)? = null

    // 검색된 epoch 리스트
    private var currentOnSearchMatched: ((List<Long>) -> Unit)? = null

    // 검색 버튼을 눌렀을 때 keyword 전달
    private var currentOnKeywordSubmitted: ((String) -> Unit)? = null


    // ============================================================
    // 뒤로가기 Callback
    //
    // 중요:
    // 하나만 유지한다.
    // 검색창이 보일 때만 enabled = true
    // 검색창이 숨겨지면 enabled = false
    // ============================================================

    private var backPressedCallback: OnBackPressedCallback? = null


    // ============================================================
    // 검색창 열기
    // ============================================================

    fun showSearchDialog(
        context: Context,
        onTimeSelected: (Long) -> Unit,
        onSearchMatched: (List<Long>) -> Unit,
        onKeywordSubmitted: (String) -> Unit
    ) {

        val activity = context as? AppCompatActivity ?: return


        // --------------------------------------------------------
        // 현재 콜백 갱신
        // --------------------------------------------------------

        currentOnTimeSelected = onTimeSelected
        currentOnSearchMatched = onSearchMatched
        currentOnKeywordSubmitted = onKeywordSubmitted

        currentActivity = WeakReference(activity)


        // ========================================================
        // 이미 검색창이 만들어져 있다면 재사용
        // ========================================================

        val panel = currentPanel?.get()
        val dim = currentDim?.get()

        if (panel != null && panel.parent != null) {

            // 검색창 다시 표시
            dim?.visibility = View.VISIBLE
            panel.visibility = View.VISIBLE

            dim?.bringToFront()
            panel.bringToFront()

            // ----------------------------------------------------
            // 기존 BackCallback 다시 활성화
            // ----------------------------------------------------

            backPressedCallback?.isEnabled = true

            return
        }


        // ========================================================
        // 처음 열었을 때만 UI 생성
        // ========================================================

        activity.lifecycleScope.launch {

            if (activity.isFinishing || activity.isDestroyed) {
                return@launch
            }


            // ====================================================
            // 기존 callback이 혹시 남아 있다면 제거
            //
            // 중복 callback 방지
            // ====================================================

            backPressedCallback?.remove()
            backPressedCallback = null


            // ====================================================
            // 뒤로가기 callback 생성
            //
            // 이 callback이 활성화되어 있는 동안에는
            // Activity의 기본 뒤로가기가 실행되지 않는다.
            // ====================================================

            val newBackCallback = object : OnBackPressedCallback(true) {

                override fun handleOnBackPressed() {

                    // 검색창이 열려 있으면
                    // 프로그램 종료가 아니라 검색창만 닫는다.
                    hideDialog()
                }
            }

            backPressedCallback = newBackCallback

            activity.onBackPressedDispatcher.addCallback(
                activity,
                newBackCallback
            )


            // ====================================================
            // 화면 크기
            // ====================================================

            val displayMetrics = context.resources.displayMetrics

            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val targetWidth = (screenWidth * 0.85f).toInt()
            val targetHeight = (screenHeight * 0.8f).toInt()


            // ====================================================
            // 1. 딤 배경
            // ====================================================

            val dimBackgroundView = FrameLayout(context).apply {

                setBackgroundColor(
                    Color.parseColor("#80000000")
                )

                isClickable = true
                isFocusable = true
            }


            // ====================================================
            // 2. 검색창 메인 패널
            // ====================================================

            val panelView = LinearLayout(context).apply {

                orientation = LinearLayout.VERTICAL

                setBackgroundColor(Color.WHITE)

                elevation = 20f

                setPadding(
                    40,
                    40,
                    40,
                    40
                )

                isFocusable = true
                isFocusableInTouchMode = true

                requestFocus()
            }


            // ====================================================
            // 3. 검색창 ScrollView
            // ====================================================

            val panelScrollView = ScrollView(context).apply {

                addView(panelView)
            }


            // ====================================================
            // 4. 검색 입력 + 검색 버튼
            // ====================================================

            val firstRowLayout = LinearLayout(context).apply {

                orientation = LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    0,
                    0,
                    30
                )
            }


            // ----------------------------------------------------
            // 검색 입력창
            // ----------------------------------------------------

            val searchEditText = EditText(context).apply {

                hint = "예) 서울, 강남, 역삼 "

                textSize = 16f

                setTextColor(Color.BLACK)

                setSingleLine(true)

                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
                )
            }


            // ----------------------------------------------------
            // 검색 버튼
            // ----------------------------------------------------

            val searchButton = Button(context).apply {

                text = "검색"

                textSize = 16f

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {

                    leftMargin = 20
                }
            }


            firstRowLayout.addView(searchEditText)
            firstRowLayout.addView(searchButton)

            panelView.addView(firstRowLayout)


            // ====================================================
            // 5. 검색 결과 컨테이너
            // ====================================================

            val resultContainer = LinearLayout(context).apply {

                orientation = LinearLayout.VERTICAL
            }


            val resultScrollView = ScrollView(context).apply {

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1.0f
                )

                addView(resultContainer)
            }


            panelView.addView(resultScrollView)


            // ====================================================
            // 6. 검색 버튼 클릭
            // ====================================================

            searchButton.setOnClickListener {

                val keyword =
                    searchEditText.text.toString().trim()


                // ------------------------------------------------
                // 빈 검색어
                // ------------------------------------------------

                if (keyword.isEmpty()) {

                    Toast.makeText(
                        context,
                        "검색어를 입력해주세요",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }


                // ------------------------------------------------
                // ⭐ View 쪽으로 keyword 전달
                // ------------------------------------------------

                currentOnKeywordSubmitted?.invoke(keyword)


                // ------------------------------------------------
                // 키패드 숨기기
                // ------------------------------------------------

                val imm =
                    context.getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    ) as android.view.inputmethod.InputMethodManager

                imm.hideSoftInputFromWindow(
                    searchEditText.windowToken,
                    0
                )


                // ------------------------------------------------
                // DB 검색
                // ------------------------------------------------

                searchAdmName(
                    activity,
                    context,
                    keyword,
                    resultContainer
                )
            }


            // ====================================================
            // 7. DecorView에 추가
            // ====================================================

            val decorView =
                activity.window.decorView as? FrameLayout
                    ?: return@launch


            // ----------------------------------------------------
            // 딤 배경
            // ----------------------------------------------------

            decorView.addView(
                dimBackgroundView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )


            // ----------------------------------------------------
            // 검색창 위치
            // ----------------------------------------------------

            val panelParams =
                FrameLayout.LayoutParams(
                    targetWidth,
                    targetHeight
                ).apply {

                    gravity = Gravity.CENTER
                }


            decorView.addView(
                panelScrollView,
                panelParams
            )


            // ====================================================
            // 상태 저장
            // ====================================================

            currentDim =
                WeakReference(dimBackgroundView)

            currentPanel =
                WeakReference(panelScrollView)


            // ====================================================
            // 딤 배경 클릭
            // ====================================================

            dimBackgroundView.setOnClickListener {

                hideDialog()
            }
        }
    }


    // ============================================================
    // 검색창 숨기기
    //
    // ⭐ 삭제하지 않는다.
    // ⭐ 검색어 / 검색 결과 상태 그대로 유지.
    // ============================================================

    private fun hideDialog() {

        currentDim?.get()?.visibility = View.GONE

        currentPanel?.get()?.visibility = View.GONE

        // --------------------------------------------------------
        // 여기서만 BackCallback 비활성화
        //
        // 이후 Activity의 기본 뒤로가기가 동작한다.
        // --------------------------------------------------------

        backPressedCallback?.isEnabled = false
    }


    // ============================================================
    // 검색창 완전 삭제
    //
    // 화면을 완전히 없애야 할 때만 호출
    // ============================================================

    fun clearSearchDialog() {

        val activity =
            currentActivity?.get()

        val decorView =
            activity?.window?.decorView as? FrameLayout


        // --------------------------------------------------------
        // View 제거
        // --------------------------------------------------------

        currentDim?.get()?.let {

            decorView?.removeView(it)
        }


        currentPanel?.get()?.let {

            decorView?.removeView(it)
        }


        // --------------------------------------------------------
        // ⭐ BackCallback 완전 제거
        //
        // 단순 disable이 아니라 dispatcher에서 제거한다.
        // --------------------------------------------------------

        backPressedCallback?.remove()

        backPressedCallback = null


        // --------------------------------------------------------
        // 상태 초기화
        // --------------------------------------------------------

        currentDim = null

        currentPanel = null

        currentOnTimeSelected = null

        currentOnSearchMatched = null

        currentOnKeywordSubmitted = null

        currentActivity = null
    }


    // ============================================================
    // DB 검색
    // ============================================================

    private fun searchAdmName(
        activity: AppCompatActivity,
        context: Context,
        keyword: String,
        resultContainer: LinearLayout
    ) {

        activity.lifecycleScope.launch(Dispatchers.IO) {

            val db =
                AppDatabase.getDatabase(context)


            val spotList =
                db.spotDao().searchSpotsByName(
                    "%$keyword%"
                )


            withContext(Dispatchers.Main) {

                resultContainer.removeAllViews()


                // =================================================
                // 검색 결과 없음
                // =================================================

                if (spotList.isEmpty()) {

                    val emptyText =
                        TextView(context).apply {

                            text = "검색된 방문 기록이 없습니다."

                            textSize = 15f

                            setPadding(
                                20,
                                20,
                                20,
                                20
                            )

                            setTextColor(Color.GRAY)
                        }


                    resultContainer.addView(emptyText)

                    return@withContext
                }


                // =================================================
                // 검색된 epoch 리스트
                // =================================================

                val matchedEpochs =
                    spotList.map {
                        it.spStartTime
                    }


                // -------------------------------------------------
                // ⭐ Timeline/View로 전달
                // -------------------------------------------------

                currentOnSearchMatched?.invoke(
                    matchedEpochs
                )


                // =================================================
                // 검색 초기화 아이템
                // =================================================

                val resetItemLayout =
                    LinearLayout(context).apply {

                        orientation =
                            LinearLayout.VERTICAL

                        setBackgroundColor(
                            Color.parseColor("#FFF3E0")
                        )

                        setPadding(
                            30,
                            25,
                            30,
                            25
                        )

                        layoutParams =
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {

                                setMargins(
                                    0,
                                    0,
                                    0,
                                    15
                                )
                            }


                        // -----------------------------------------
                        // 검색 초기화
                        // -----------------------------------------

                        setOnClickListener {

                            resultContainer.removeAllViews()

                            currentOnSearchMatched?.invoke(
                                emptyList()
                            )
                            // 2. 뷰 쪽으로 빈 에폭 리스트 전달 (카드 색상 원복)
                            currentOnSearchMatched?.invoke(
                                emptyList()
                            )
                            // 3. 🚨 [추가] 메인 화면의 검색어(keyword)도 빈 값으로 초기화 전달!
                            currentOnKeywordSubmitted?.invoke("")

                            Toast.makeText(
                                context,
                                "검색이 초기화되었습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }


                // =================================================
                // 초기화 제목
                // =================================================

                val resetText =
                    TextView(context).apply {

                        text = "🔄 검색 초기화"

                        textSize = 15f

                        setTextColor(
                            Color.parseColor("#D32F2F")
                        )
                    }


                // =================================================
                // 초기화 설명
                // =================================================

                val resetSubText =
                    TextView(context).apply {

                        text =
                            "터치하시면 검색이 초기화 됩니다."

                        textSize = 12f

                        setTextColor(
                            Color.DKGRAY
                        )

                        setPadding(
                            0,
                            4,
                            0,
                            0
                        )
                    }


                resetItemLayout.addView(resetText)

                resetItemLayout.addView(resetSubText)


                // -------------------------------------------------
                // 최상단에 추가
                // -------------------------------------------------

                resultContainer.addView(
                    resetItemLayout
                )


                // =================================================
                // 검색 결과 카드
                // =================================================

                for (item in spotList) {

                    val itemLayout =
                        LinearLayout(context).apply {

                            orientation =
                                LinearLayout.VERTICAL

                            setBackgroundColor(
                                Color.parseColor("#F9F9F9")
                            )

                            setPadding(
                                30,
                                25,
                                30,
                                25
                            )

                            layoutParams =
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {

                                    setMargins(
                                        0,
                                        0,
                                        0,
                                        15
                                    )
                                }


                            // -------------------------------------
                            // 카드 클릭
                            // -------------------------------------

                            setOnClickListener {

                                val targetStartTime =
                                    item.spStartTime


                                // View로 시간 전달
                                currentOnTimeSelected?.invoke(
                                    targetStartTime
                                )


                                // 검색창 숨김
                                hideDialog()
                            }
                        }


                    // =================================================
                    // 장소 이름
                    // =================================================

                    val nameTextView =
                        TextView(context).apply {

                            text =
                                "◼ ${item.spAdmName}"

                            textSize = 16f

                            setTextColor(
                                Color.BLACK
                            )
                        }


                    // =================================================
                    // 시작 시간
                    // =================================================

                    val timeTextView =
                        TextView(context).apply {

                            val sdf =
                                SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm",
                                    Locale.getDefault()
                                )


                            text =
                                "시작 시간: ${
                                    sdf.format(
                                        Date(item.spStartTime)
                                    )
                                }"


                            textSize = 14f

                            setTextColor(
                                Color.DKGRAY
                            )

                            setPadding(
                                0,
                                5,
                                0,
                                0
                            )
                        }


                    itemLayout.addView(
                        nameTextView
                    )

                    itemLayout.addView(
                        timeTextView
                    )


                    resultContainer.addView(
                        itemLayout
                    )
                }
            }
        }
    }
    //검색초기화 , 뷰에서 검색을 종료했을때 호출
    fun clearSearchState() {
        val panel = currentPanel?.get() ?: return

        // 패널 내부의 뷰들을 찾아서 초기화
        // (주의: SearchAdm 내부에서 searchEditText와 resultContainer를 멤버 변수나 지역 변수로 접근할 수 있게 구조를 잡아두거나,
        // 아래처럼 안전하게 상태 콜백을 활용할 수 있습니다)

        currentOnKeywordSubmitted?.invoke("")
        currentOnSearchMatched?.invoke(emptyList())
    }
        // 💡 [핵심] 검색창을 완전히 종료하고 메모리 참조를 날려버리는 함수
        fun destroyDialog() {
            try {
                // 1. 패널과 딤 배경을 부모 뷰(윈도우 등)에서 완전히 제거
                val panel = currentPanel?.get()
                val dim = currentDim?.get()

                (panel?.parent as? android.view.ViewGroup)?.removeView(panel)
                (dim?.parent as? android.view.ViewGroup)?.removeView(dim)

                // 2. 콜백 및 참조 레퍼런스 깔끔하게 해제
                currentPanel?.clear()
                currentDim?.clear()
                currentPanel = null
                currentDim = null

                // 3. 뒤로가기 콜백도 제거
                backPressedCallback?.remove()
                backPressedCallback = null

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


