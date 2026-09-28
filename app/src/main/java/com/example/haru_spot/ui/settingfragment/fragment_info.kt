package com.example.haru_spot.ui.settingfragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.haru_spot.R
import com.google.android.material.card.MaterialCardView

class fragment_info : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_setting_info,
            container,
            false
        )



        // 설정으로 돌아가기
        val backButton =
            view.findViewById<MaterialCardView>(R.id.layout_back_to_setting)

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 1. Haru_Stay 소개
        val infoText = view.findViewById<TextView>(R.id.text_info_1)
        infoText.text = """
            하루 동안 어디서, 얼마나 머물렀는지를 자동으로 기록하는 일상 체류 기록 앱입니다.

            휴대폰 위치 정보를 바탕으로 이동을 읍·면·동 단위로 기록하고, 각 장소에서 머문 시간을 확인할 수 있습니다.

        """.trimIndent()


        // 2. 저전력 설계
        val infoTitle2 = view.findViewById<TextView>(R.id.title_info_2)
        val infoText2 = view.findViewById<TextView>(R.id.text_info_2)
        infoTitle2.text = "◼ 저전력 백그라운드"
        infoText2.text = """
            15분 주기로 위치 정보를 가볍게 수집합니다.

            실시간 위치 추적 대신 필요한 수준의 위치 정보를 활용하여 배터리 부담을 줄이는 방향으로 설계했습니다.

        """.trimIndent()


        // 3. 외부 서버 미사용
        val infoTitle3 = view.findViewById<TextView>(R.id.title_info_3)
        val infoText3 = view.findViewById<TextView>(R.id.text_info_3)
        infoTitle3.text = "◼ 오프라인·개인 보호"
        infoText3.text = """
            외부 서버로 개인 위치 데이터를 전송하거나 저장하지 않습니다.

            수집된 기록은 사용자 휴대폰에 보관됩니다.

        """.trimIndent()


        // 4. 위치 추가 및 검색
        val infoTitle4 = view.findViewById<TextView>(R.id.title_info_4)
        val infoText4 = view.findViewById<TextView>(R.id.text_info_4)
        infoTitle4.text = "◼ 수동 추가 & 검색 지원"
        infoText4.text = """
            자동 수집에서 누락된 장소는 사용자가 직접 추가할 수 있습니다.

            지난 기록은 검색을 통해 원하는 장소를 빠르게 찾아볼 수 있습니다.

        """.trimIndent()


        // 5. 사용하지 않는 항목은 깔끔하게 숨기기
        val infoTitle5 = view.findViewById<TextView>(R.id.title_info_5)
        val infoText5 = view.findViewById<TextView>(R.id.text_info_5)
        infoTitle5.text = "◼ 안전한 데이터 백업"
        infoText5.text = """
            하루의 기록을 JSON 파일 하나로 내보내고 다시 불러올 수 있습니다.

            기기를 변경할 때도 저장된 데이터를 간편하게 이동할 수 있습니다.

        """.trimIndent()
        return view
    }
}