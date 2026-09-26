package com.example.haru_spot.ui.settingfragment

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.haru_spot.R
import com.example.haru_spot.data.database.AppDatabase
import com.example.haru_spot.data.entity.Fav
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.drawable.GradientDrawable
import android.widget.ScrollView
import com.example.haru_spot.service.유틸.userGpsSync

class fragment_userlocation : Fragment() {

    // XML에 있는 리사이클러뷰 자리를 동적 컨테이너로 활용
    private lateinit var listContainer: LinearLayout

    // 선택된 스팟의 ID와 이름을 저장할 변수
    private var selectedLinkId: Long = -1L
    private var selectedFavName: String = ""

    private var selectedLat: Double = 0.0  // 💡 추가
    private var selectedLon: Double = 0.0  // 💡 추가

    private var selectfavAdmCode: String = ""
    private var selectfavAdmName: String = ""
    // 💡 [추가] 텍스트 입력창 자체를 전역적으로 잡아둘 변수 선언!
    //private var editNameInput: EditText? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_setting_userlocation, container, false)

        // 1. 상단 '설정으로 돌아가기' 초기화
        initBackButton(view)

        // 2. 2박스 버튼([검색], [수정], [삭제]) 리스너 초기화
        initActionButtons(view)

        // 3. 3박스: favDB 사용자 위치 목록 로드 및 코드로 동적 뷰 생성 연동
        loadUserLocationList(view)

        return view
    }

    /**
     * 상단 '설정으로 돌아가기' 버튼 초기화
     */
    private fun initBackButton(view: View) {
        val backButton = view.findViewById<MaterialCardView>(R.id.layout_back_to_setting)
        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * 2박스 버튼 리스너 초기화 (터치로 선택된 아이템 연동)
     */
    private fun initActionButtons(view: View) {
        val btnSearch = view.findViewById<Button>(R.id.btn_user_search)
        val btnEdit = view.findViewById<Button>(R.id.btn_user_edit)
        val btnDelete = view.findViewById<Button>(R.id.btn_user_delete)

        btnSearch.setOnClickListener {
            Toast.makeText(requireContext(), "검색 기능 동작", Toast.LENGTH_SHORT).show()
            // 다이얼로그 내부에서 쓸 입력 텍스트뷰(EditText) 생성
            val inputEditText = android.widget.EditText(requireContext()).apply {
                hint = "검색할 스팟 이름이나 주소를 입력하세요"
                setPadding(50, 40, 50, 40)
                setTextColor(Color.BLACK)      // 입력 글자 색상
                setHintTextColor(Color.GRAY)   // 힌트 글자 색상
            }

            // 💡 이미 만들어져 있는 R.style.CustomAlertDialogStyle 테마를 적용!
            android.app.AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogStyle)
                .setTitle("사용자 스팟 검색")
                .setView(inputEditText)
                .setPositiveButton("검색") { _, _ ->
                    val keyword = inputEditText.text.toString().trim()
                    if (keyword.isEmpty()) {
                        Toast.makeText(requireContext(), "검색어를 입력해주세요!", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    // 입력한 검색어로 리스트 필터링 수행 함수 호출
                    filterUserLocationList(view, keyword)
                }
                .setNegativeButton("취소", null)
                .show()
        }

        btnDelete.setOnClickListener {
            if (selectedLinkId == -1L) {
                Toast.makeText(requireContext(), "삭제할 스팟을 리스트에서 먼저 터치해 주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 원본 기초 코드와 동일한 커스텀 테마 적용 AlertDialog
            android.app.AlertDialog.Builder(requireContext(), R.style.CustomAlertDialogStyle)
                .setTitle("데이터 삭제")
                .setMessage("정말 이 스팟을 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getDatabase(requireContext())

                        // 💡 원본 기초 코드처럼 두 DAO 모두 안전하게 ID 기준으로 삭제 처리
                        db.favoriteDao().deleteById(selectedLinkId)
                        db.busStopDao().deleteById(selectedLinkId)

                        // 2. 🚀 삭제된 좌표 기준으로 주변 위치/로그 싱크 재정비 엔진 가동!
                        userGpsSync.refineWithUserSpots(requireContext(), selectedLat, selectedLon)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "[$selectedFavName] 삭제 완료", Toast.LENGTH_SHORT).show()
                            selectedLinkId = -1L
                            selectedFavName = ""

                            // 삭제 후 상단 안내 문구 원복
                            val listContainerView = view.findViewById<LinearLayout>(R.id.recycler_user_locations)
                            val parentCardLayout = listContainerView.parent as? LinearLayout
                            val tvGuide = parentCardLayout?.getChildAt(0) as? TextView
                            tvGuide?.text = "◼ 아래 리스트에서 선택해주세요"

                            // 리스트 새로고침
                            loadUserLocationList(view)
                        }
                    }
                }
                .setNegativeButton("취소", null)
                .show()
        }

        //수정버튼
        btnEdit.setOnClickListener {
            if (selectedLinkId == -1L) {
                Toast.makeText(requireContext(), "수정할 스팟을 리스트에서 먼저 터치해 주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 💡 복잡한 다이얼로그와 주소 검색 처리는 아래 전용 함수로 통째로 토스!
            showEditSpotDialog(view)
        }

    }



    /**
     * 3박스: DB에서 데이터를 불러와 코드로 개별 행을 만들고 선택 시 상단 이동 및 배경색 변경 연동
     */
    /**
     * 3박스: DB에서 데이터를 불러와 코드로 개별 행을 만들고 선택 시 상단 이동 및 배경색 변경 연동
     */
    private fun loadUserLocationList(view: View) {

        val listContainer =
            view.findViewById<LinearLayout>(R.id.recycler_user_locations)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {

            val db = AppDatabase.getDatabase(requireContext())
            val userFavList: List<Fav> =
                db.favoriteDao().getUserFavs()

            withContext(Dispatchers.Main) {

                listContainer.removeAllViews()

                if (userFavList.isEmpty()) {
                    val emptyTv = TextView(requireContext()).apply {
                        text = "저장된 사용자 스팟이 없습니다."
                        setPadding(30, 30, 30, 30)
                        setTextColor(Color.GRAY)
                    }

                    listContainer.addView(emptyTv)
                    return@withContext
                }

                // 전체 아이템 레이아웃들을 담아두어 선택 시 배경색을 일괄 초기화하기 위한 리스트
                val allItemLayouts = mutableListOf<LinearLayout>()

                for (item in userFavList) {

                    val itemLayout = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL

                        setPadding(35, 12, 35, 12)

                        // 기본 비선택 상태 배경색 (#EAF4FF)
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#EAF4FF"))
                            cornerRadius = 24f
                            setStroke(2, Color.parseColor("#D0E3F0"))
                        }

                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, 12)
                        }

                        isClickable = true
                        isFocusable = true
                    }

                    val tvName = TextView(requireContext()).apply {
                        val spotName = item.favBusStop.ifEmpty { "이름 없는 스팟" }
                        text = "• $spotName"

                        textSize = 15f
                        setTextColor(Color.BLACK)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }

                    val tvAddress = TextView(requireContext()).apply {
                        text = item.favAdmName.ifEmpty {
                            "주소 정보 없음"
                        }

                        textSize = 12f
                        setTextColor(Color.parseColor("#8A8A8A"))

                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 4
                        }
                    }

                    itemLayout.addView(tvName)
                    itemLayout.addView(tvAddress)
                    allItemLayouts.add(itemLayout)

                    // 💡 개별 아이템 터치(클릭) 이벤트
                    itemLayout.setOnClickListener {
                        // favLinkedBusStopId와 bus_stops.id를 연결하는 ID
                        // 정확히는 bus_stop의 PK임
                        selectedLinkId = item.favLinkedBusStopId
                        selectedFavName = item.favBusStop
                        selectedLat = item.favGpsLat  // 💡 터치한 스팟의 위도 캡처!
                        selectedLon = item.favGpsLon  // 💡 터치한 스팟의 경도 캡처!
                        selectfavAdmCode = item.favAdmCode
                        selectfavAdmName = item.favAdmName

                        // 1. 3박스 안의 상단 안내 텍스트뷰를 찾아 선택된 이름으로 변경
                        val parentCardLayout = listContainer.parent as? LinearLayout
                        val tvGuide = parentCardLayout?.getChildAt(0) as? TextView

                        tvGuide?.text = "◼ 선택 : $selectedFavName"

                        // 💡 [핵심] 가로 폭을 MATCH_PARENT로 꽉 채우고, 리스트와 똑같은 둥근 박스 배경을 코드로 직접 적용!
                        tvGuide?.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, 15) // 아래쪽 여백 살짝 주기
                        }

                        tvGuide?.setPadding(35, 24, 35, 24) // 패딩을 줘서 박스 크기 키우기

                        // 💡 [핵심] 텍스트만 글자색 배경으로 잡히던 걸 리스트 카드처럼 둥글고 꽉 찬 박스 배경으로 적용!
                        tvGuide?.setPadding(30, 24, 30, 24) // 여백을 줘서 박스 느낌 살리기
                        tvGuide?.background = GradientDrawable().apply {
                            setColor(Color.parseColor("#FFF3E0")) // 요청하신 주황빛 배경
                            cornerRadius = 24f                    // 모서리 둥글게
                            setStroke(2, Color.parseColor("#FFB74D")) // 테두리 선 추가
                        }

                        // 2. 모든 아이템 배경을 기본 색상(#EAF4FF)으로 일괄 초기화
                        for (otherLayout in allItemLayouts) {
                            otherLayout.background = GradientDrawable().apply {
                                setColor(Color.parseColor("#EAF4FF"))
                                cornerRadius = 24f
                                setStroke(2, Color.parseColor("#D0E3F0"))
                            }
                        }

                        // 3. 방금 터치한 아이템만 요청하신 #FFF3E0 색상으로 변경
                        itemLayout.background = GradientDrawable().apply {
                            setColor(Color.parseColor("#FFF3E0"))
                            cornerRadius = 24f
                            setStroke(2, Color.parseColor("#FFB74D"))
                        }

                        // 4. 어떤 끄트머리를 눌러도 화면 최상단(NestedScrollView)으로 스르륵 이동
                        val rootScrollView = view.parent as? androidx.core.widget.NestedScrollView
                            ?: view as? androidx.core.widget.NestedScrollView

                        rootScrollView?.smoothScrollTo(0, 0)

                        Toast.makeText(
                            requireContext(),
                            "선택됨: $selectedFavName",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    listContainer.addView(itemLayout)
                }
            }
        }
    }

    /*========================================================
     * 💡 스팟 수정 및 주소 변경 다이얼로그를 띄우고 처리하는 전용 함수
     =========================================================*/
    private fun showEditSpotDialog(rootView: View) {
        val context = requireContext()

        // 1. 전체를 담을 수직 메인 레이아웃
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 30)
        }

        // ==========================================
        // 📌 [1번 줄] 선택한 이름 (EditText) + [이름수정] 버튼 가로 배치
        // ==========================================
        val nameRowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val editNameInput = android.widget.EditText(context).apply {
            hint = "변경할 이름을 입력하세요"
            setText(selectedFavName) // 기존 선택된 이름 기본 세팅
            textSize = 15f
            setPadding(30, 25, 30, 25)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            ).apply {
                rightMargin = 10
            }
        }

        val btnNameModify = Button(context).apply {
            text = "이름수정"
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                // 💡 [핵심] 이름수정 버튼 누르는 순간 키패드 강제 닫기!
                val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(editNameInput.windowToken, 0)

                val newName = editNameInput.text.toString().trim()
                if (newName.isEmpty()) {
                    Toast.makeText(context, "변경할 이름을 입력해주세요!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                /*viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context)
                    // TODO: 필요한 이름 변경 쿼리 연결
                    // db.favoriteDao().updateFavName(selectedLinkId, newName)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "이름이 수정되었습니다: $newName", Toast.LENGTH_SHORT).show()
                        selectedFavName = newName
                        loadUserLocationList(rootView)
                    }
                }*/
            }
        }

        nameRowLayout.addView(editNameInput)
        nameRowLayout.addView(btnNameModify)
        dialogLayout.addView(nameRowLayout)

        // ==========================================
        // 📌 [2번 줄] 선택한 주소 (TextView) + [주소수정] 버튼 가로 배치
        // ==========================================
        val addressRowLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 30)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val tvAddressDisplay = TextView(context).apply {
            // 주소가 있으면 보여주고 없으면 깔끔하게 빈칸 처리
            text = if (selectfavAdmName.isNotEmpty()) selectfavAdmName else ""
            textSize = 14f
            setTextColor(Color.DKGRAY)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            ).apply {
                rightMargin = 10
            }
        }

        val btnAddressModify = Button(context).apply {
            text = "주소수정"
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                // 💡 [주소수정 버튼] -> 주소 검색 리스트 창(또는 하단 리스트 전환) 연동 영역
                // 💡 복잡한 주소 검색 다이얼로그와 리스트 로직은 전용 함수로 토스!
                showAddressSearchDialog(context, tvAddressDisplay)
                Toast.makeText(context, "주소 검색 리스트 창 호출 영역", Toast.LENGTH_SHORT).show()
            }
        }

        addressRowLayout.addView(tvAddressDisplay)
        addressRowLayout.addView(btnAddressModify)
        dialogLayout.addView(addressRowLayout)

        // ==========================================
        // 📌 [최종 다이얼로그 빌드] [확인] [취소] 버튼 구조 (빈 이름 유효성 검사 적용)
        // ==========================================
        android.app.AlertDialog.Builder(context, R.style.CustomAlertDialogStyle)
            .setTitle("사용자 스팟 편집")
            .setView(dialogLayout)
            .setPositiveButton("확인", null) // 💡 빈 이름 방지 체크를 위해 null 지정 후 커스텀 오버라이드
            .setNegativeButton("취소", null)
            .create().apply {
                setOnShowListener {
                    val positiveButton = getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                    positiveButton.setOnClickListener {
                        val finalName = editNameInput.text.toString().trim()

                        // 1. 유효성 검사: 이름이 비어있으면 차단
                        if (finalName.isEmpty()) {
                            Toast.makeText(context, "변경할 이름을 입력해주세요!", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        // 2. 유효성 검사: 스팟이 선택되었는지 체크
                        if (selectedLinkId == -1L) {
                            Toast.makeText(context, "수정할 스팟을 먼저 선택해주세요!", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        // 3. 유효성 검사: 읍면동 주소가 선택되었는지 체크
                        if (selectfavAdmCode.isEmpty()) {
                            Toast.makeText(context, "변경할 읍면동 주소를 다시 확인해주세요!", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        // 🚀 DB 업데이트 코루틴 실행
                        requireActivity().lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val db = AppDatabase.getDatabase(context)

                                // Fav 테이블에서 연결된 버스 정류장/스팟 고유 ID(favLinkedBusStopId)를 추출
                                val favItem = db.favoriteDao().getFavById(selectedLinkId)
                                if (favItem != null) {
                                    val targetBusStopId = favItem.favLinkedBusStopId

                                    // A. BusStopEntity 갱신 (좌표는 기존 유지, 이름과 행정동 정보만 수정)
                                    db.busStopDao().updateBusStopInfo(
                                        id = targetBusStopId,
                                        name = finalName,
                                        code = selectfavAdmCode,
                                        addr = selectfavAdmName
                                    )

                                    // B. Fav 테이블 갱신 (이름과 행정동 정보 수정)
                                    db.favoriteDao().updateUserSpotInfo(
                                        id = targetBusStopId,
                                        name = finalName,
                                        addr = selectfavAdmName,
                                        code = selectfavAdmCode
                                    )

                                    // C. 사용자 위치/로그 싱크 엔진 연동 (기존 위도, 경도 활용)

                                    userGpsSync.refineWithUserSpots(context, selectedLat, selectedLon)


                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "수정 사항이 안전하게 적용되었습니다.", Toast.LENGTH_SHORT).show()

                                        // 상태 변수 초기화
                                        selectedLinkId = -1L
                                        selectedFavName = ""
                                        selectedLat = 0.0
                                        selectedLon = 0.0
                                        selectfavAdmCode = ""
                                        selectfavAdmName = ""

                                        // 리스트 갱신 및 다이얼로그 닫기
                                        // loadUserLocationList(rootView) // 필요시 호출
                                        dismiss()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "대상을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "저장 중 오류가 발생했습니다: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            .show()


    }
    /**
     * 검색 키워드(스팟 이름 또는 주소)가 포함된 항목만 필터링하여 리스트 뷰를 갱신하는 함수
     */
    /**
     * 검색 키워드(스팟 이름 또는 주소)가 포함된 항목만 필터링하여 리스트 뷰를 갱신하는 함수
     */
    private fun filterUserLocationList(view: View, keyword: String) {
        val listContainer = view.findViewById<LinearLayout>(R.id.recycler_user_locations)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(requireContext())
            val allUserFavs = db.favoriteDao().getUserFavs()

            // 💡 대소문자 구분 없이 이름(favBusStop)이나 주소(favAdmName)에 키워드가 포함된 항목 추출
            val filteredList = allUserFavs.filter {
                it.favBusStop.contains(keyword, ignoreCase = true) ||
                        it.favAdmName.contains(keyword, ignoreCase = true)
            }

            withContext(Dispatchers.Main) {
                listContainer.removeAllViews()

                if (filteredList.isEmpty()) {
                    val emptyTv = TextView(requireContext()).apply {
                        text = "'$keyword'에 해당하는 스팟이 없습니다."
                        setPadding(30, 30, 30, 30)
                        setTextColor(Color.GRAY)
                    }
                    listContainer.addView(emptyTv)
                    Toast.makeText(requireContext(), "검색 결과가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                Toast.makeText(requireContext(), "${filteredList.size}개의 스팟을 찾았습니다!", Toast.LENGTH_SHORT).show()

                // 전체 아이템 레이아웃들을 담아두어 선택 시 배경색 초기화에 활용
                val allItemLayouts = mutableListOf<LinearLayout>()

                for (item in filteredList) {
                    val itemLayout = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(35, 12, 35, 12)

                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#EAF4FF"))
                            cornerRadius = 24f
                            setStroke(2, Color.parseColor("#D0E3F0"))
                        }

                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, 12)
                        }

                        isClickable = true
                        isFocusable = true
                    }

                    val tvName = TextView(requireContext()).apply {
                        val spotName = item.favBusStop.ifEmpty { "이름 없는 스팟" }
                        text = "• $spotName"
                        textSize = 15f
                        setTextColor(Color.BLACK)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                    }

                    val tvAddress = TextView(requireContext()).apply {
                        text = item.favAdmName.ifEmpty { "주소 정보 없음" }
                        textSize = 12f
                        setTextColor(Color.parseColor("#8A8A8A"))

                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 4
                        }
                    }

                    itemLayout.addView(tvName)
                    itemLayout.addView(tvAddress)
                    allItemLayouts.add(itemLayout)

                    // 💡 검색된 항목을 터치했을 때의 선택 이벤트 연동
                    itemLayout.setOnClickListener {
                        selectedLinkId = item.favId
                        selectedFavName = item.favBusStop

                        // 1. 상단 3박스 안의 안내 텍스트뷰 찾아오기
                        val parentCardLayout = listContainer.parent as? LinearLayout
                        val tvGuide = parentCardLayout?.getChildAt(0) as? TextView

                        tvGuide?.text = "◼ 선택됨: $selectedFavName"

                        // 💡 [핵심] 가로 폭을 MATCH_PARENT로 꽉 채우고, 둥근 박스 배경 및 패딩/마진 적용!
                        tvGuide?.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 0, 0, 15)
                        }

                        tvGuide?.setPadding(35, 24, 35, 24)

                        tvGuide?.background = GradientDrawable().apply {
                            setColor(Color.parseColor("#FFF3E0")) // 주황빛 배경
                            cornerRadius = 24f                    // 모서리 둥글게
                            setStroke(2, Color.parseColor("#FFB74D")) // 테두리 선
                        }

                        // 2. 다른 아이템 배경 일괄 초기화
                        for (otherLayout in allItemLayouts) {
                            otherLayout.background = GradientDrawable().apply {
                                setColor(Color.parseColor("#EAF4FF"))
                                cornerRadius = 24f
                                setStroke(2, Color.parseColor("#D0E3F0"))
                            }
                        }

                        // 3. 방금 터치한 아이템만 주황색으로 강조
                        itemLayout.background = GradientDrawable().apply {
                            setColor(Color.parseColor("#FFF3E0"))
                            cornerRadius = 24f
                            setStroke(2, Color.parseColor("#FFB74D"))
                        }

                        // 4. 최상단으로 스크롤 이동
                        val rootScrollView = view.parent as? androidx.core.widget.NestedScrollView
                            ?: view as? androidx.core.widget.NestedScrollView
                        rootScrollView?.smoothScrollTo(0, 0)

                        Toast.makeText(requireContext(), "선택됨: $selectedFavName", Toast.LENGTH_SHORT).show()
                    }

                    listContainer.addView(itemLayout)
                }
            }
        }
    }
    /**
     * 💡 읍면동 주소 검색 및 결과 리스트를 띄우고 선택을 처리하는 전용 함수
     */
    private fun showAddressSearchDialog(context: Context, tvAddressDisplay: TextView) {
        // 1. 전체를 감쌀 수직 메인 레이아웃 (다이얼로그 내부)
        val searchLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
        }

        // ==========================================
        // 📌 [상단 1단] 텍스트 입력박스 + [확인] 버튼 가로 배치
        // ==========================================
        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 20)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val editKeywordInput = android.widget.EditText(context).apply {
            hint = "읍면동 입력 (예: 역삼동)"
            textSize = 15f
            setPadding(30, 25, 30, 25)
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            ).apply {
                rightMargin = 10
            }
        }

        val btnSearchConfirm = Button(context).apply {
            text = "확인"
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        inputRow.addView(editKeywordInput)
        inputRow.addView(btnSearchConfirm)
        searchLayout.addView(inputRow)

        // ==========================================
        // 📌 [하단 2단] 검색 결과 리스트가 꽂힐 스크롤 컨테이너
        // ==========================================
        val resultContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val resultScrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0, // 💡 weight를 주어 남은 공간을 유연하게 차지하게 하거나, 적당한 최대 높이감 부여
                1.0f
            ).apply {
                topMargin = 15
                // 필요시 최소/최대 높이 설정 가능
            }
            isFillViewport = true
            addView(resultContainer)
        }

        searchLayout.addView(resultScrollView)

        // ==========================================
        // 📌 다이얼로그 빌드 및 띄우기
        // ==========================================
        val subDialog = android.app.AlertDialog.Builder(context, R.style.CustomAlertDialogStyle)
            .setTitle("주소(읍면동) 검색")
            .setView(searchLayout)
            .setNegativeButton("취소", null)
            .create()

        // ==========================================
        // 📌 [확인] 버튼을 눌렀을 때 DB 검색 및 하단 리스트 생성 로직
        // ==========================================
        btnSearchConfirm.setOnClickListener {
            val keyword = editKeywordInput.text.toString().trim()
            if (keyword.isEmpty()) {
                Toast.makeText(context, "검색할 읍면동을 입력해주세요!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 키패드 숨기기
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(editKeywordInput.windowToken, 0)

            // DB 조회 코루틴 실행
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val database = AppDatabase.getDatabase(context)
                val admList = database.busStopDao().searchDistinctAdmByKeyword(keyword)

                withContext(Dispatchers.Main) {
                    resultContainer.removeAllViews() // 기존 결과 초기화

                    if (admList.isEmpty()) {
                        val emptyView = TextView(context).apply {
                            text = "검색된 결과가 없습니다."
                            textSize = 14f
                            setTextColor(Color.GRAY)
                            setPadding(20, 20, 20, 20)
                        }
                        resultContainer.addView(emptyView)
                    } else {
                        // 검색된 리스트 아이템 하나씩 동적 생성
                        for (stop in admList) {
                            val itemTextView = TextView(context).apply {
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

                            // 🚀 [핵심] 리스트 항목을 터치했을 때 값 반영 후 창 닫기
                            itemTextView.setOnClickListener {
                                selectfavAdmName = stop.admName
                                selectfavAdmCode = stop.admCode

                                // 메인 편집 다이얼로그의 주소 텍스트뷰에 즉시 반영
                                tvAddressDisplay.text = stop.admName

                                Toast.makeText(context, "선택됨: ${stop.admName}", Toast.LENGTH_SHORT).show()
                                subDialog.dismiss()
                            }

                            resultContainer.addView(itemTextView)
                        }
                    }
                }
            }
        }

        subDialog.show()
    }


}