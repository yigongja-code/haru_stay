package com.example.haru_spot.service.유틸

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.haru_spot.R
import com.example.haru_spot.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


object UserSpotEditHelper {


    // 💡 형님이 짚어주신 5가지 핵심 데이터를 쥐고 있을 상태 변수들!
    private var selectedBusFavId: Long = -1L       // 1. favLinkedBusStopId (고유 매핑 ID)
    private var selectedSpotName: String = ""    // 2. 정류장명 (스팟 이름)
    private var selectedLat: Double = 0.0        // 3. 위도
    private var selectedLon: Double = 0.0        // 4. 경도
    private var selectedAdmCode: String = ""     // 5. 행정동 코드
    private var selectedAdmName: String = ""     // 주소 표시용 텍스트

    fun editUserLocation(context: Context) {
        val activity = context as? AppCompatActivity ?: return

        activity.lifecycleScope.launchWhenResumed {
            selectedBusFavId = -1L
            selectedAdmCode = ""

            // ==========================================
            // 📌 2층 레이어 크기 및 배경 셋팅
            // ==========================================
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val targetWidth = (screenWidth * 0.85f).toInt()
            val targetHeight = (screenHeight * 0.8f).toInt()

            var dimBackgroundRef: FrameLayout? = null
            var panelScrollViewRef: ScrollView? = null

            val closeDialog: () -> Unit = {
                val decorView = activity.window.decorView as? FrameLayout
                if (decorView != null) {
                    dimBackgroundRef?.let { decorView.removeView(it) }
                    panelScrollViewRef?.let { decorView.removeView(it) }
                }
            }

            // 1층 터치 막아주는 딤 배경
            val dimBackgroundView = FrameLayout(context).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#80000000"))
                isClickable = true
                isFocusable = true
                setOnClickListener { closeDialog() }
            }
            dimBackgroundRef = dimBackgroundView

            // 스크롤이 가능한 컨테이너 판때기
            val panelView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(android.graphics.Color.WHITE)
                elevation = 20f
                setPadding(40, 40, 40, 40)
            }

            // 1. 스크롤 뷰는 포커스/키 리스너 다 떼고 깔끔하게 생성만 합니다.
            val panelScrollView = ScrollView(context).apply {
                addView(panelView)
            }
            panelScrollViewRef = panelScrollView

            // 2. ⭐️ [끝판왕 해결책] 포커스와 상관없이 이 2층 팝업이 떠 있는 동안 백 버튼을 누르면 무조건 닫히게 강제 채널 고정!
            val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // 검색 결과 리스트가 쭈루룩 펼쳐져 있다면, 백 버튼 누를 때 리스트부터 먼저 지우고 싶으시면 요렇게 처리 가능!
                    // 만약 그냥 팝업 전체를 닫고 싶다면 바로 closeDialog()를 호출하시면 됩니다.
                    closeDialog()
                }
            }
            activity.onBackPressedDispatcher.addCallback(activity, backCallback)

            // ==========================================
            // 📌 첫 번째 줄: 스팟 이름 입력 박스
            // ==========================================
            val firstEditText = EditText(context).apply {
                hint = "스팟 이름을 입력하세요 (예: 집, 직장)"
                textSize = 16f
                setTextColor(android.graphics.Color.BLACK)
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 20)
                }
            }
            panelView.addView(firstEditText)



                // ==========================================
            // 📌 두 번째 줄: 주소 표시 텍스트뷰 + 주소수정 버튼
            // ==========================================
            val secondRowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 20)
            }

            val secondTextView = TextView(context).apply {
                text = "읍면동 검색(oo리는 검색불가)"
                textSize = 15f
                setTextColor(android.graphics.Color.DKGRAY)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
                )
            }

            val addressEditButton = Button(context).apply {
                text = "주소수정"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = 15
                }
            }

            // ==========================================
            // 📌 리스트 컨테이너
            // ==========================================
            val listContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            addressEditButton.setOnClickListener {

                if (selectedBusFavId == -1L) {
                    Toast.makeText(context, "먼저 수정할 스팟을 리스트에서 선택해주세요!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 💡 기존 주소 텍스트에서 뒤쪽 핵심 키워드(읍/면/동 등)만 깔끔하게 발라냄
                val rawCurrentAddress = secondTextView.text.toString().trim()
                val tokens = rawCurrentAddress.split("\\s+".toRegex())
                val refinedAddress = if (tokens.isNotEmpty()) tokens.last() else rawCurrentAddress

                val dialogInput = EditText(context).apply {
                    setText(refinedAddress) // 정제된 주소 세팅
                    setSelection(text.length)
                    setPadding(40, 30, 40, 30)
                    hint = "읍면동 검색(oo리는 검색불가)"
                }

                //androidx.appcompat.app.AlertDialog.Builder(context,R.style.CustomAlertDialogStyle)
                AlertDialog.Builder(context, R.style.CustomAlertDialogStyle)
                    .setTitle("읍,면,동 검색(oo리 불가)")
                    .setView(dialogInput)
                    .setPositiveButton("확인") { _, _ ->
                        val inputStr = dialogInput.text.toString().trim()
                        if (inputStr.isNotEmpty()) {
                            val searchKeyword = inputStr.split("\\s+".toRegex()).last()

                            // 🚀 바로 이 지점에서 내부 DB 검색 및 리스트 갱신 함수 호출!
                            // (함수 내부에서 searchKeyword를 받아 DB를 긁고 listContainer에 뷰를 동적으로 뿌려줍니다)
                            // 💡 키패드를 깔끔하게 강제로 숨겨주는 유틸성 코드
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                            imm.hideSoftInputFromWindow(dialogInput.windowToken, 0)
                            searchAndDisplayAddresses(activity, searchKeyword, secondTextView, listContainer, panelScrollView)
                        } else {
                            Toast.makeText(context, "검색할 주소를 입력해주세요.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("취소", null)
                    .setOnDismissListener {
                        // ⭐️ [핵심 방어선] 다이얼로그나 검색 결과가 닫힐 때 2층 팝업이 다시 주도권을 꽉 쥠!
                        panelScrollView.isFocusableInTouchMode = true
                        panelScrollView.requestFocus()
                    }
                    .show()
            }

            secondRowLayout.addView(secondTextView)
            secondRowLayout.addView(addressEditButton)
            panelView.addView(secondRowLayout)



            // ==========================================
            // 📌 세 번째 줄: [수정] [삭제] [취소] 하단 액션 버튼
            // ==========================================
            val actionRowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 10, 0, 20)
            }

            val btnModify = Button(context).apply {
                text = "수정"
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    rightMargin = 8
                }
                //텍스트입력 확인 버튼이 없으니 수정이라는 단추를 누르는순간 DB에 엮어서 넣으면됨
                setOnClickListener {
                    handleModifyAction(activity, firstEditText, secondTextView, listContainer, panelScrollView)
                }
            }

            val btnDelete = Button(context).apply {
                text = "삭제"
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    leftMargin = 4
                    rightMargin = 4
                }
                setOnClickListener {
                    if (selectedBusFavId == -1L) {
                        Toast.makeText(context, "삭제할 스팟을 선택해주세요!", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }

                    AlertDialog.Builder(context,R.style.CustomAlertDialogStyle)
                        .setTitle("데이터 삭제")
                        .setMessage("정말 이 스팟을 삭제하시겠습니까?")
                        .setPositiveButton("삭제") { _, _ ->
                            activity.lifecycleScope.launch(Dispatchers.IO) {
                                val db = AppDatabase.getDatabase(context)
                                db.favoriteDao().deleteById(selectedBusFavId)
                                db.busStopDao().deleteById(selectedBusFavId)
                                // ==========================================
                                // 💡 [핵심 추가] DB 업데이트가 끝난 직후(메인으로 넘어가기 전 IO 스레드에서)!
                                // 수정된 최신 좌표(selectedLat, selectedLon) 기준으로 주변 로그 오버라이드 싱크 강제 구동!
                                // ==========================================
                                userGpsSync.refineWithUserSpots(activity, selectedLat, selectedLon)
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "삭제 처리 완료", Toast.LENGTH_SHORT).show()
                                    selectedBusFavId = -1L
                                    selectedAdmCode = ""
                                    firstEditText.setText("")
                                    secondTextView.text = "주소를 선택해주세요."

                                    loadUserFavList(activity, firstEditText, secondTextView, listContainer, panelScrollView)
                                }
                            }
                        }
                        .setNegativeButton("취소", null)
                        .show()
                }
            }

            val btnCancel = Button(context).apply {
                text = "취소"
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                    leftMargin = 8
                }
                setOnClickListener {
                    closeDialog()
                }
            }

            actionRowLayout.addView(btnModify)
            actionRowLayout.addView(btnDelete)
            actionRowLayout.addView(btnCancel)
            panelView.addView(actionRowLayout)

            // ==========================================
            // 📌 [구분선]
            // ==========================================
            val divider = View(context).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#CCCCCC"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2
                ).apply {
                    setMargins(0, 10, 0, 20)
                }
            }
            panelView.addView(divider)

            panelView.addView(listContainer)

            // 💡 [최초 호출] 창이 뜨자마자 리스트 로딩 실행
            loadUserFavList(activity, firstEditText, secondTextView, listContainer, panelScrollView)


            // ==========================================
            // 📌 뷰를 화면(DecorView)에 올리기
            // ==========================================
            val decorView = activity.window.decorView as? FrameLayout
            if (decorView != null) {
                val layerParams = FrameLayout.LayoutParams(targetWidth, targetHeight).apply {
                    gravity = Gravity.CENTER
                }
                decorView.addView(dimBackgroundView)
                decorView.addView(panelScrollView, layerParams)
            }
        }
    }

    /**
     * 💡 [수정 액션 전담] 이름, 주소, 행정동 코드를 포함해 DB에 통째로 반영하는 함수
     */
    /**
     * 💡 [수정 액션] 변수를 새로 쪼개지 않고, 쥐고 있던 상태 변수들을 그대로 쿼리에 투척!
     */
    private fun handleModifyAction(
        activity: AppCompatActivity,
        firstEditText: EditText,
        secondTextView: TextView,
        listContainer: LinearLayout,
        panelScrollView: ScrollView
    ) {
        // 사용자가 입력창에 최종적으로 적어둔 텍스트만 상태 변수에 살짝 갱신해 줍니다.
        selectedSpotName = firstEditText.text.toString().trim()
        //selectedadmname 는 주소 수정하는 과정에서 코드와 함께 저장
        // selectedAdmName = secondTextView.text.toString().trim()

        if (selectedSpotName.isEmpty()) {
            Toast.makeText(activity, "스팟 이름을 입력해주세요!", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedBusFavId == -1L) {
            Toast.makeText(activity, "목록에서 수정할 스팟을 선택해주세요!", Toast.LENGTH_SHORT).show()
            return
        }

        activity.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(activity)

            // 💡 2. 쥐고 있던 상태 변수들을 그대로 Dao에 전달! (ID 꼬일 일 절대 없음)
            // favDao 업데이트 메서드에 맞게 selectedBusFavId, selectedSpotName, selectedAdmName, selectedAdmCode, 위도, 경도를 쏴줍니다.
            // 1. 즐겨찾기(fav_db) 쪽 정보 갱신
            db.favoriteDao().updateUserSpotInfo(
                id = selectedBusFavId,
                name = selectedSpotName,
                addr = selectedAdmName,
                code = selectedAdmCode
            )

// 2. 원천 버스정류장(bus_stops) 쪽 정보 동기화 갱신
            db.busStopDao().updateBusStopInfo(
                id = selectedBusFavId,
                name = selectedSpotName,
                code = selectedAdmCode,
                addr = selectedAdmName
            )

            // 원천 데이터(bus_stops) 정류장명 동기화도 동일한 ID 사용
            db.busStopDao().updateBusStopText(selectedBusFavId, selectedSpotName)

            // ==========================================
            // 💡 [핵심 추가] DB 업데이트가 끝난 직후(메인으로 넘어가기 전 IO 스레드에서)!
            // 수정된 최신 좌표(selectedLat, selectedLon) 기준으로 주변 로그 오버라이드 싱크 강제 구동!
            // ==========================================
            userGpsSync.refineWithUserSpots(activity, selectedLat, selectedLon)

            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "수정 및 주소·코드 반영 완료!", Toast.LENGTH_SHORT).show()

                // 사용 후 상태 초기화
                selectedBusFavId = -1L
                selectedSpotName = ""
                selectedLat = 0.0
                selectedLon = 0.0
                selectedAdmCode = ""
                selectedAdmName = ""

                firstEditText.setText("")
                secondTextView.text = "주소를 선택해주세요."

                // 리스트 새로고침
                loadUserFavList(activity, firstEditText, secondTextView, listContainer, panelScrollView)
            }
        }
    }

    /**
     * 💡 [리스트 로딩 전담] 클릭 시 상단 반영 및 맨 윗부분(FOCUS_UP)으로 스크롤 이동 처리
     */
    /**
     * 💡 [리스트 로딩 전담] 5가지 데이터를 쏙 뽑아 변수에 저장하고 맨 위로 스크롤 튕기기
     */
    private fun loadUserFavList(
        activity: AppCompatActivity,
        firstEditText: EditText,
        secondTextView: TextView,
        listContainer: LinearLayout,
        panelScrollView: ScrollView
    ) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(activity)
            val userFavs = db.favoriteDao().getUserFavs()

            withContext(Dispatchers.Main) {
                listContainer.removeAllViews()

                if (userFavs.isEmpty()) {
                    val emptyTextView = TextView(activity).apply {
                        text = "등록된 사용자 스팟이 없습니다."
                        textSize = 14f
                        setTextColor(android.graphics.Color.GRAY)
                        gravity = Gravity.CENTER
                        setPadding(0, 30, 0, 30)
                    }
                    listContainer.addView(emptyTextView)
                } else {
                    for (fav in userFavs) {
                        val itemLayout = LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            setBackgroundColor(android.graphics.Color.parseColor("#F9F9F9"))
                            setPadding(30, 25, 30, 25)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, 15)
                            }
                        }

                        val nameTv = TextView(activity).apply {
                            text = "◼︎ ${fav.favBusStop}"
                            textSize = 16f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(android.graphics.Color.BLACK)
                        }

                        val addrTv = TextView(activity).apply {
                            text = fav.favAdmName
                            textSize = 14f
                            setTextColor(android.graphics.Color.DKGRAY)
                            setPadding(0, 5, 0, 0)
                        }

                        itemLayout.addView(nameTv)
                        itemLayout.addView(addrTv)

                        // 💡 리스트 클릭 시 5가지 핵심 데이터 변수에 장전 + 화면 맨 위로 이동!
                        itemLayout.setOnClickListener {
                            selectedBusFavId = fav.favLinkedBusStopId // 1. ID
                            selectedSpotName = fav.favBusStop        // 2. 정류장명
                            selectedLat = fav.favGpsLat               // 3. 위도
                            selectedLon = fav.favGpsLon                 // 4. 경도
                            selectedAdmCode = fav.favAdmCode         // 5. 행정동 코드
                            selectedAdmName = fav.favAdmName         // 주소 명칭

                            firstEditText.setText(selectedSpotName)
                            secondTextView.text = selectedAdmName

                            // 터치 시 스크롤 뷰 맨 위로 팍 올라가기
                            panelScrollView.post {
                                panelScrollView.fullScroll(View.FOCUS_UP)
                            }

                            Toast.makeText(activity, "'$selectedSpotName' 선택됨", Toast.LENGTH_SHORT).show()
                        }

                        listContainer.addView(itemLayout)
                    }
                }
            }
        }
    }
    /**
     * 주소 찾기
     * 💡 내부 DB에서 키워드로 주소/정류장 정보를 긁어와 listContainer에 동적으로 뷰를 뿌려주는 함수
     *
     */
    private fun searchAndDisplayAddresses(
        activity: AppCompatActivity,
        keyword: String,
        secondTextView: TextView,
        listContainer: LinearLayout,
        panelScrollView: ScrollView // ⭐️ 여기로 받아오기!
    ) {
        // 기존에 리스트에 나와 있던 이전 검색 결과들은 싹 지워줌 (초기화)
        listContainer.removeAllViews()

        // 🚀 [추가 포인트] 검색 결과 리스트 상단에 띄울 안내 텍스트뷰 생성!
        val noticeTextView = TextView(activity).apply {
            text = "※ 읍,면,동으로 검색해주세요 (oo리 단위 검색 불가)"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#E65100")) // 눈에 띄는 진한 주황/오렌지톤 포인트 컬러
            setPadding(30, 20, 30, 20)
            setBackgroundColor(android.graphics.Color.parseColor("#FFF3E0")) // 연한 배경색
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 10)
            }
        }
        // 가장 먼저 리스트 컨테이너의 맨 위에 꽂아줍니다!
        listContainer.addView(noticeTextView)

        activity.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(activity)

            // 💡 [내부 DB 조회] 예를 들어 busStopDao나 관련 주소 테이블에서 키워드로 검색
            // (사용하시는 DAO에 맞추어 쿼리 메서드명을 살짝 연동하시면 됩니다!)
            val matchedList = db.busStopDao().searchDistinctAdmByKeyword(keyword)

            withContext(Dispatchers.Main) {
                if (matchedList.isEmpty()) {
                    Toast.makeText(activity, "검색 결과가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                // 가져온 결과 리스트를 순회하며 동적으로 뷰(TextView 등)를 생성해서 listContainer에 추가
                for (item in matchedList) {
                    val itemView = TextView(activity).apply {
                        // 예시: 읍면동 이름과 코드를 보기 좋게 세팅
                        text = "◼${item.admName} (${item.admCode})"
                        textSize = 15f
                        setTextColor(android.graphics.Color.BLACK)
                        setPadding(30, 25, 30, 25)
                        setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))

                        // 레이아웃 Margins 부여
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, 10)
                        }
                    }

                    // 💡 리스트의 특정 항목을 콕 찍었을 때의 이벤트!
                    itemView.setOnClickListener {
                        // 1. 전역 변수에 최종 선택값 쏙 저장!
                        selectedAdmName = item.admName ?: ""
                        selectedAdmCode = item.admCode ?: ""

                        // 2. 화면에 보이는 주소 텍스트뷰 업데이트
                        secondTextView.text = selectedAdmName

                        // 3. 선택 완료되었으니 아래쪽 검색 결과 리스트 컨테이너는 다시 비워줌 (숨김 효과)
                        listContainer.removeAllViews()

                        // ⭐️ 아이템 선택 직후에도 포커스 뺏김 방어
                        panelScrollView.isFocusableInTouchMode = true
                        panelScrollView.requestFocus()

                        Toast.makeText(activity, "선택됨: $selectedAdmName", Toast.LENGTH_SHORT).show()
                    }


                    // 리스트 컨테이너에 동적으로 추가
                    listContainer.addView(itemView)
                }

                // ⭐️ 2. [정답 위치] for 문이 완전히 끝나고 리스트가 통째로 완성된 '바로 바깥(직후)'에 딱 한 번!
                panelScrollView.isFocusableInTouchMode = true
                panelScrollView.requestFocus()
            }
        }
    }
}