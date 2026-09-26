package com.example.haru_spot.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BatteryReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BatterySignal"
    }

    override fun onReceive(context: Context, intent: Intent) {

        Log.e(
            TAG,
            "🔥 충전 신호 수신됨 → action=${intent.action}"
        )

        when (intent.action) {

            Intent.ACTION_POWER_CONNECTED -> {
                Log.e(TAG, "🔌🔌🔌 충전기 연결 신호 들어옴!")
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.e(TAG, "🔋🔋🔋 충전기 해제 신호 들어옴!")
            }

            else -> {
                Log.e(TAG, "❓ 예상하지 못한 전원 신호: ${intent.action}")
            }
        }
    }

}