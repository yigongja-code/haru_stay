package com.example.haru_spot.ui.settingfragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.haru_spot.R
import com.google.android.material.card.MaterialCardView

class fragment_copyright : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_setting_copyright,
            container,
            false
        )

        // 1. 상단 '설정으로 돌아가기'
        initBackButton(view)

        // 2. 데이터 출처 및 저작권 내용
        initCopyrightText(view)

        return view
    }

    /**
     * 상단 돌아가기 버튼
     */
    private fun initBackButton(view: View) {

        val backButton =
            view.findViewById<MaterialCardView>(R.id.layout_back_to_setting)

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * 데이터 출처 및 저작권 내용
     */
    private fun initCopyrightText(view: View) {

        val apartmentText =
            view.findViewById<TextView>(R.id.text_copyright_apartment)

        val busText =
            view.findViewById<TextView>(R.id.text_copyright_bus)

        val osmText =
            view.findViewById<TextView>(R.id.text_copyright_osm)


        apartmentText.text = """
            출처: 공간데이터마켓
데이터명: 아파트단지 기준정보
운영기관: LX한국국토정보공사

공간데이터마켓에서 제공하는 아파트단지 기준정보를 이용하였습니다.
        """.trimIndent()


        busText.text = """
            출처: 공공데이터포털
데이터명: 국토교통부_전국 버스정류장 위치정보
제공기관: 국토교통부

공공데이터포털에서 제공하는 버스정류장 위치정보를 이용하였습니다.
        """.trimIndent()


        osmText.text = """
            출처: OpenStreetMap
라이선스: Open Database License (ODbL)

OpenStreetMap의 데이터를 이용하여 장소 및 위치 정보를 구성하였습니다.

© OpenStreetMap contributors
        """.trimIndent()
    }
}