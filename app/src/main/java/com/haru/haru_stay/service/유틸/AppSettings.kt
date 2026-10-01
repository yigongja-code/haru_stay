package com.haru.haru_stay.service.유틸

import android.content.Context

object AppSettings {

    private const val PREF_NAME = "app_prefs"

    // 설정 키
    private const val KEY_COLLECT_INTERVAL = "collect_interval_minutes"
    private const val KEY_SHORT_SPOT_TIME = "short_spot_time_minutes"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)


    // 수집 간격
    fun getCollectInterval(context: Context): Int {
        return prefs(context).getInt(KEY_COLLECT_INTERVAL, 15)
    }

    fun setCollectInterval(context: Context, minutes: Int) {
        prefs(context).edit()
            .putInt(KEY_COLLECT_INTERVAL, minutes)
            .apply()
    }


    // 짧은 체류시간 스팟 컷
    fun getShortSpotTime(context: Context): Int {
        return prefs(context).getInt(KEY_SHORT_SPOT_TIME, 30)
    }

    fun setShortSpotTime(context: Context, minutes: Int) {
        prefs(context).edit()
            .putInt(KEY_SHORT_SPOT_TIME, minutes)
            .apply()
    }
}