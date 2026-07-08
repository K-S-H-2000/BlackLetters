package com.tukorea.blackletters.app

import android.app.Application
import com.kakao.sdk.common.KakaoSdk

class BlackLettersApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 카카오 SDK 초기화
        KakaoSdk.init(this, "98c4ae0b804536a389cf66503f1176fd")
    }
}
