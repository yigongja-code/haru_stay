package com.haru.haru_stay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.haru.haru_stay.service.유틸.TriggerLogToSpot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ScreenOnReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenOnReceiver"
    }

    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob()
    )

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action != Intent.ACTION_SCREEN_ON) return



        val appContext = context.applicationContext

        scope.launch {

            delay(30_000L)



            TriggerLogToSpot.runIfNeeded(appContext) {



            }
        }
    }
}