plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)      // <--- 이 줄이 있는지 확인!
    alias(libs.plugins.google.devtools.ksp) // <--- 이 줄이 있는지 확인!

    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.haru.haru_stay"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.haru.haru_stay"
        minSdk = 31 //안드로이드 12이상만 구동
        targetSdk = 36

        versionCode = 4         //버전 올릴때마다 +1
        versionName = "0.1.2"   //사람들에게 보이는 버전
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
/* ========수정완료===================================================
0.1.2(4) - 2026-10-3 시작
압축 연결부 00시이후 cmpfirst 생성이 안되는거 해결 10/4
2개의 로그가 들어왔을대만 3차가공 압축로직 활성화

이동프레그먼트 하단 리스트 선택식 지도위에점 다른표시



 ===================================================================*/


/* ========해야할일===================================================

버스정류장 데이터 사전 탑재
초기 설치 시 미리 준비한 버정 DB를 사용해 로딩 시간을 줄이고, 앱 업데이트 시 최신 데이터로 교체하도록 구현.

위젯 개발
즉시수집 =
정보는 현위치의  주소정도.

현위치 즐겨찾기는 입력텍스트창이 필요하므로 제외

 ===================================================================*/

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
    지도 이동경로 표시 개선 9/28
    홈프레그먼트 db가 없는대도 과거로 계속가던 문제 수정 9/28
    스파db보기에서 체류시간 x일xx시간xx분 표시  (시간만 있던 문제 해결) 9/28
    데이터관리 프래그먼트에서 SPOT DB타이틀을 홈정보 데이터로 변환 9/28
    설정 순서 변경 및 몇몇 문구수정 9/28
    홈프레그먼트에 즐겨찾기 아이콘변경 즉시수집 버튼 만들기 9/28
    즐겨찾기 -> 즐겨찾기 현위치 추가 두줄로 바꿈 9/28
    설정 시트에서 업데이트 버전과 현재버전 표시 9/28
    이동 프레그먼트에서 중복제거 100미터로 상승(구20미터) 9/28
    앱의 기본시간을 10분 기준으로 맞춤 9/29
    카드의 시작과 종료시간이 같을경우 종료시간을 +1분 9/29
    앱의 기본단위 시간을 10분->5분으로 수정 9/29
    데이터관리 프레그먼스 행정동이라는 타이틀을 법정동으로 바꿈 9/29
    이동 경로에 즐겨찾기(노란별) 표시를 하고 싶음 9/29
    상세팝업카드내 좌표확인 -> 지도확인 위치등록 ->즐겨찾기 추가 9/29

    즐겨찾기 수정에서 수정후 리스트가 적용안됨 새로고침 필요 9/29
0.1.2(3) - 2026-10-1 시작
데이터 저장관련 상세 안내 수정수정 10/1
의미없는 짧은 체류시간 삭제관련 수정 10/1
설정을 로컬에 저장하는 SharedPreferences 사용 10/1 쉐어드 프리퍼런스
이동경로시 초기 줌값 11로 설정 10/1
시간 정규화 취소 롤백 10/2
데이터 프레그 먼드에 스팟뷰어 가공시간 표시 10/2
로그압축 3차가공 만듬 10/3
============================================================== */

