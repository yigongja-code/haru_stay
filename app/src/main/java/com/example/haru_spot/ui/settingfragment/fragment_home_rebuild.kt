package com.example.haru_spot.ui.settingfragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.haru_spot.R
import com.example.haru_spot.data.database.AppDatabase
import com.example.haru_spot.service.유틸.TriggerLogToSpot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class fragment_home_rebuild : Fragment() {


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_setting_home_rebuild,
            container,
            false
        )

        // 1. 상단 '설정으로 돌아가기'
        initBackButton(view)

        // 2. 홈 체류 카드 재생성
        initHomeRebuildButton(view)

        return view
    }

    /**
     * 상단 돌아가기 버튼
     */
    private fun initBackButton(view: View) {

        val backButton =
            view.findViewById<com.google.android.material.card.MaterialCardView>(
                R.id.layout_back_to_setting
            )

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    /**
     * 홈 체류 카드 재생성
     *
     * 기존 Spot과 순위 데이터를 삭제한 뒤
     * Log DB를 기준으로 처음부터 다시 가공한다.
     *
     * Log DB 자체는 삭제하거나 초기화하지 않는다.
     */
    private fun initHomeRebuildButton(view: View) {

        val rebuildButton =
            view.findViewById<Button>(R.id.btn_home_rebuild)

        rebuildButton.setOnClickListener {

            androidx.appcompat.app.AlertDialog.Builder(
                requireContext(),
                R.style.CustomAlertDialogStyle
            )
                .setTitle("홈 체류 카드 재생성")
                .setMessage(
                    "현재 저장된 로그 데이터를 기준으로 홈 체류 카드와 순위 데이터를 처음부터 다시 생성하시겠습니까?"
                )
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("예") { dialog, _ ->

                    viewLifecycleOwner.lifecycleScope.launch {

                        try {

                            val db =
                                AppDatabase.getDatabase(requireContext())

                            // 기존 Spot과 순위 데이터를 먼저 삭제한다.
                            // 가공 상태만 로우데이터 상태로 되돌린다.
                            withContext(Dispatchers.IO) {
                                db.spotDao().deleteAllSpots()
                                db.logDao().resetAllLogsToRaw()
                                db.sumDao().전체SumDB삭제()
                            }

                            // 기존 로그를 기준으로 처음부터 다시 가공한다.
                            TriggerLogToSpot.runIfNeeded(requireContext()) {

                                Toast.makeText(
                                    requireContext(),
                                    "홈 체류 카드 재생성이 완료되었습니다.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                        } catch (e: Exception) {

                            e.printStackTrace()

                            Toast.makeText(
                                requireContext(),
                                "재생성 실패: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    dialog.dismiss()
                }
                .setNegativeButton("아니오", null)
                .show()
        }
    }


}
