package com.example.haru_spot.ui.settingfragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.haru_spot.BuildConfig
import com.example.haru_spot.MainActivity
import com.example.haru_spot.R
import com.example.haru_spot.service.유틸.Update_SheetUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class fragment_update_sheet : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_setting_update_sheet,
            container,
            false
        )

        // 설정으로 돌아가기
        val backButton =
            view.findViewById<MaterialCardView>(R.id.layout_back_to_setting)

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val mainActivity =
            requireActivity() as MainActivity

        val updateTitle =
            view.findViewById<TextView>(R.id.tv_update_title)

        val updateVersionDate =
            view.findViewById<TextView>(R.id.tv_update_version_date)

        val updateContent =
            view.findViewById<TextView>(R.id.tv_update_content)

        val updateButton =
            view.findViewById<MaterialButton>(R.id.btn_update)

        // ======================================================
        // MainActivity에 저장된 업데이트 정보가 있으면 사용
        // 없으면 구글시트에서 직접 불러온다.
        // ======================================================

        val updateInfo = mainActivity.updateInfo

        if (updateInfo != null) {

            showUpdateInfo(
                updateInfo,
                updateTitle,
                updateVersionDate,
                updateContent,
                updateButton
            )

        } else {

            lifecycleScope.launch {

                val result =
                    Update_SheetUtil(requireContext()).sheetLoad()

                if (result != null) {

                    mainActivity.updateInfo = result

                    showUpdateInfo(
                        result,
                        updateTitle,
                        updateVersionDate,
                        updateContent,
                        updateButton
                    )
                }
            }
        }

        // ======================================================
        // 업데이트 버튼 → 시트에 저장된 URL로 이동
        // ======================================================

        updateButton.setOnClickListener {

            val currentInfo =
                mainActivity.updateInfo

            if (currentInfo != null) {

                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(currentInfo.url)
                )

                startActivity(intent)
            }
        }

        return view
    }

    private fun showUpdateInfo(
        updateInfo: Update_SheetUtil.UpdateInfo,
        updateTitle: TextView,
        updateVersionDate: TextView,
        updateContent: TextView,
        updateButton: MaterialButton
    ) {

        updateTitle.text =
            updateInfo.title

        val displayDate =
            Instant.parse(updateInfo.date)
                .atZone(ZoneId.of("Asia/Seoul"))
                .format(
                    DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
                )

        updateVersionDate.text =
            "버전 ${updateInfo.versionName} · $displayDate"

        updateContent.text =
            updateInfo.content

        // 현재 앱보다 새로운 버전일 때만 업데이트 버튼 활성화
        if (updateInfo.versionCode > BuildConfig.VERSION_CODE) {
            updateButton.visibility = View.VISIBLE
        } else {
            updateButton.visibility = View.GONE
        }
    }
}