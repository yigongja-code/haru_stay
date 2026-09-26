package com.example.haru_spot.service.collector

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr // 5G용
import android.telephony.TelephonyManager
import android.util.Log

class CellIdCollector(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    @SuppressLint("MissingPermission")
    fun fetchCellId(): String? {
        try {
            // 단말기가 지원하는 주변 기지국 리스트 가져오기 (ACCESS_FINE_LOCATION 권한 필요)
            val cellInfoList = telephonyManager.allCellInfo

            if (cellInfoList.isNullOrEmpty()) {
                Log.d("CellIdCollector", "⚠️ 수집된 주변 기지국 정보가 없습니다.")
                return null
            }

            // 그중 현재 연결되어 있거나 유효한 메인 기지국 정보 추출
            for (cellInfo in cellInfoList) {
                if (cellInfo.isRegistered) {
                    when (cellInfo) {
                        is CellInfoLte -> {
                            val identity = cellInfo.cellIdentity
                            val ci = identity.ci // Cell ID
                            val tac = identity.tac // Tracking Area Code
                            Log.d("CellIdCollector", "📶 LTE 기지국 포착 - CI: $ci, TAC: $tac")
                            return "LTE_$ci"
                        }
                        is CellInfoGsm -> {
                            val identity = cellInfo.cellIdentity
                            val cid = identity.cid
                            Log.d("CellIdCollector", "📶 GSM 기지국 포착 - CID: $cid")
                            return "GSM_$cid"
                        }
                        is CellInfoNr -> {
                            // 5G NR 기지국 처리
                            val identity = cellInfo.cellIdentity
                            val nci = (identity as android.telephony.CellIdentityNr).nci
                            Log.d("CellIdCollector", "📶 5G 기지국 포착 - NCI: $nci")
                            return "5G_$nci"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CellIdCollector", "❌ 기지국 정보 수집 중 에러 발생: ${e.message}")
        }

        return null
    }
}