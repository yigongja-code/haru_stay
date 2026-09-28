plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)      // <--- 이 줄이 있는지 확인!
    alias(libs.plugins.google.devtools.ksp) // <--- 이 줄이 있는지 확인!

    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.example.haru_spot"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.haru_spot"
        minSdk = 31 //안드로이드 12이상만 구동
        targetSdk = 36

        versionCode = 2         //버전 올릴때마다 +1
        versionName = "0.1.1"   //사람들에게 보이는 버전
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
    }


    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.kakao.maps.open:android:2.15.2")


    // 💡 Room 버전을 2.6.1 안정 버전으로 변경 (PassthroughConnectionPool 충돌 방지)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-crashlytics")
}

/* ==========================================================
0.1.1(2) - 날짜
    데이터관리 초기화 관련 버튼에 순위DB 초기화 추가 9/25
    데이터 베이스 버튼을 설정으로 이동후 뷰데이터 랭킹데이터 테이블에 띄우기 작업 9/25
    프레그먼트 어디서든지 폰ui 뒤로가기시 홈으로 가기 9/25
    포그라운드 문제 해결 9/25
    데이터프레그먼트쪽 즉시수집에 포그라운드 실행 넣음 9/25
    깃 연결 완료 9/26
    sunDB 신규지역 체류계산 오류 수정(마지막로그를 미계산, 스팟체류를 그냥 넣음) 9/26
    설정에서 하위프래그 먼트에서 뒤로가기 누르면 홈프레그먼트로감 9/26
    홈프레그먼트에서 뒤로가기 누르면 종료 다른프레그먼트 뒤로가기는 홈으로 이동 9/27
    구글시트 연동 구현 9/28
    프래그먼트 xml 백그라운드 색상통일 9/27

============================================================== */

/* ========해야할일===================================================
이동경로프레그먼트 로딩 속도문제
            장거리 이동데이터에 의한 화살표 갯수 문제
            화면에 보이는 부분만 화살표로 작성

 ===================================================================*/