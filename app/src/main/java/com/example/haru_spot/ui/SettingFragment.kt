package com.example.haru_spot.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.haru_spot.R
import com.example.haru_spot.ui.settingfragment.fragment_collect
import com.example.haru_spot.ui.settingfragment.fragment_userlocation
import com.example.haru_spot.ui.settingfragment.fragment_copyright
import com.example.haru_spot.ui.settingfragment.fragment_data_management_setting
import com.example.haru_spot.ui.settingfragment.fragment_home_rebuild
import com.example.haru_spot.ui.settingfragment.fragment_info

class SettingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_setting, container, false)

        // 1💡 데이터 수집 설정
        val layoutCollectSetting = view.findViewById<View>(R.id.layout_move_to_collect_setting)

        layoutCollectSetting.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment_collect())
                .addToBackStack(null)
                .commit()
        }

        // 💡 홈 체류 카드 재생성 home_rebuild
        val layouthome_rebuild = view.findViewById<View>(R.id.layout_move_to_home_rebuild)

        layouthome_rebuild.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment_home_rebuild())
                .addToBackStack(null)
                .commit()
        }

        // 💡 데이터 관리 data_management_setting
        val layoutdata_management_setting = view.findViewById<View>(R.id.layout_move_to_data_management_setting)

        layoutdata_management_setting.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment_data_management_setting())
                .addToBackStack(null)
                .commit()
        }



        // 2💡 사용자 위치 편집 설정 화면으로 이동하는 카드 박스 클릭 리스너
        val layoutUserLocationSetting = view.findViewById<View>(R.id.layout_move_to_user_location_setting)

        layoutUserLocationSetting.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment_userlocation())
                .addToBackStack(null)
                .commit()
        }

        //3  저작권 관련 고지
        val layoutMoveToCopyrightSetting = view.findViewById<View>(R.id.layout_move_to_copyright_setting)

        layoutMoveToCopyrightSetting.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment_copyright())
                .addToBackStack(null)
                .commit()
        }
        // 4  앱 안내
        val layoutMoveToInfoSetting =
            view.findViewById<View>(R.id.layout_move_to_info_setting)

        layoutMoveToInfoSetting.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment_info())
                .addToBackStack(null)
                .commit()
        }




        return view
    }



}