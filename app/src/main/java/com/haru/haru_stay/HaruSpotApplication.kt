package com.haru.haru_stay

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

class HaruSpotApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        KakaoMapSdk.init(
            this,
            "640d1ebe23c32fed83876c00895b7aee"
        )
    }
}