package com.tukorea.blackletters

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tukorea.blackletters.ui.theme.BlackLettersTheme

import com.kakao.sdk.common.util.Utility

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 키해시 확인을 위한 로그 출력 (카카오 개발자 콘솔 등록용)
        val keyHash = Utility.getKeyHash(this)
        android.util.Log.d("KakaoKeyHash", "KeyHash: $keyHash")

        enableEdgeToEdge()
        setContent {
            BlackLettersTheme {
                // 현재 화면 상태를 관리 (로그인 전/후)
                var authToken by remember { mutableStateOf<String?>(null) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (authToken == null) {
                            LoginScreen(onLoginSuccess = { token -> 
                                authToken = token 
                            })
                        } else {
                            MainScreen(token = authToken!!)
                        }
                    }
                }
            }
        }
    }
}
