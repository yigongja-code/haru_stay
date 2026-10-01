package com.haru.haru_stay.ui.settingfragment

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.haru.haru_stay.R
import com.haru.haru_stay.service.ForegroundService
import com.google.android.material.card.MaterialCardView
import com.haru.haru_stay.service.유틸.AppSettings

class fragment_collect : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(
            R.layout.fragment_setting_collect,
            container,
            false
        )

        // 1. 상단 '설정으로 돌아가기' 초기화
        initBackButton(view)

        // 2. 수집 주기 설정 및 WorkManager 연동 로직 초기화 (함수로 분리!)
        initCollectIntervalSettings(view)

        // 3. 짧은 체류시간 설정 초기화
        initShortSpotTimeSettings(view)

        return view
    }

    /**
     * 상단 돌아가기 버튼 리스너 설정
     */
    private fun initBackButton(view: View) {
        val backButton = view.findViewById<MaterialCardView>(R.id.layout_back_to_setting)
        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * 💡 수집 주기 설정 관련 로직 (입력, 유효성 검사, 저장, 포그라운드/워크매니저 갱신)
     * 이 영역을 함수로 깔끔하게 빼두었으니 다음 작업하실 때 혼선 없이 쾌적하게 하실 수 있습니다!
     */
    private fun initCollectIntervalSettings(view: View) {

        val editInterval =
            view.findViewById<EditText>(R.id.edit_collect_interval)

        val saveButton =
            view.findViewById<Button>(R.id.btn_save_interval)

        // 저장된 수집 간격 불러오기
        editInterval.setText(
            AppSettings.getCollectInterval(requireContext()).toString()
        )

        saveButton.setOnClickListener {

            val intervalStr = editInterval.text.toString().trim()

            // 확인 누를 때 키패드 숨기기 및 포커스 해제
            val imm = requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            imm.hideSoftInputFromWindow(editInterval.windowToken, 0)
            editInterval.clearFocus()

            if (intervalStr.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "수집 간격을 입력해주세요!",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            var intervalValue = intervalStr.toIntOrNull() ?: 15

            // 14 이하로 입력한 경우 15분으로 자동 보정
            if (intervalValue < 15) {
                intervalValue = 15
                editInterval.setText("15")

                Toast.makeText(
                    requireContext(),
                    "최소 수집 간격은 15분 이상이어야 합니다. (15분으로 설정됨)",
                    Toast.LENGTH_SHORT
                ).show()
            }

            // AppSettings에 수집 주기 저장
            AppSettings.setCollectInterval(
                requireContext(),
                intervalValue
            )

            // ForegroundService 호출을 통한 WorkManager 즉시 갱신 반영
            val serviceIntent =
                Intent(requireContext(), ForegroundService::class.java).apply {
                    action = "ACTION_START"
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(serviceIntent)
            } else {
                requireContext().startService(serviceIntent)
            }

            Toast.makeText(
                requireContext(),
                "수집 간격이 ${intervalValue}분으로 설정 및 즉시 반영되었습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    /**
     * 짧은 체류시간 설정
     * 설정한 시간보다 짧은 Spot은 의미없는 체류로 판단하여 정리
     */
    private fun initShortSpotTimeSettings(view: View) {

        val editShortSpotTime =
            view.findViewById<EditText>(R.id.edit_short_spot_time)

        val saveButton =
            view.findViewById<Button>(R.id.btn_save_short_spot_time)

        // 저장된 설정값 불러오기
        val shortSpotMinutes =
            AppSettings.getShortSpotTime(requireContext())

        editShortSpotTime.setText(shortSpotMinutes.toString())

        saveButton.setOnClickListener {

            val input = editShortSpotTime.text.toString().trim()

            // 키패드 숨기기 및 포커스 해제
            val imm = requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            imm.hideSoftInputFromWindow(editShortSpotTime.windowToken, 0)
            editShortSpotTime.clearFocus()

            if (input.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "체류시간을 입력해주세요!",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val minutes = input.toIntOrNull()

            if (minutes == null || minutes <= 0) {
                Toast.makeText(
                    requireContext(),
                    "올바른 시간을 입력해주세요.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // AppSettings에 짧은 체류시간 저장
            AppSettings.setShortSpotTime(
                requireContext(),
                minutes
            )

            Toast.makeText(
                requireContext(),
                "짧은 체류시간이 ${minutes}분으로 설정되었습니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}