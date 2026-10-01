package com.haru.haru_stay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.haru.haru_stay.data.database.AppDatabase
import com.haru.haru_stay.data.database.AppDatabase.Companion.isDataInitialized
import com.haru.haru_stay.databinding.ActivityMainBinding
import com.haru.haru_stay.service.ForegroundService
import com.haru.haru_stay.ui.DatabaseFragment
import com.haru.haru_stay.ui.HomeFragment
import com.haru.haru_stay.ui.SettingFragment
import com.haru.haru_stay.ui.StatsFragment
//import com.example.haru_spot.ui.MoveFragment
import com.haru.haru_stay.service.유틸.TriggerLogToSpot
import com.haru.haru_stay.service.유틸.Update_SheetUtil
import com.haru.haru_stay.service.유틸.UserLocationInputHelper
import com.haru.haru_stay.ui.fragment.MoveFragment
import com.haru.haru_stay.ui.settingfragment.fragment_update_sheet
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

//  호출 TriggerLogToSpot.runIfNeeded(this)

class MainActivity : AppCompatActivity() {

    // 업데이트 시트 마지막 로드 시간
    var lastUpdateCheckTime = 0L

    // 업데이트 시트에서 불러온 값
    var updateInfo: Update_SheetUtil.UpdateInfo? = null

    private lateinit var binding: ActivityMainBinding

    private var batteryDialog: android.app.AlertDialog? = null
    private var permissionDialog: android.app.AlertDialog? = null
    private var alarmDialog: android.app.AlertDialog? = null

    private var databaseReady = false

    // 권한 흐름은 한 번만 시작한다.
    private var permissionFlowStarted = false

    // 앱 설정 화면으로 이동했다가 돌아올 때만 onResume()에서 다음 단계를 이어간다.
    private var waitingForSettings = false

    // 마지막으로 설정 화면을 열었던 단계
    private enum class SettingsStep {
        FOREGROUND_LOCATION,
        NOTIFICATION,
        BACKGROUND_LOCATION,
        BATTERY
    }

    private var waitingSettingsStep: SettingsStep? = null

    // ------------------------------------------------------------
    // 1. Activity 생성
    // ------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.e("AppDatabase", "🚨온크리에이트 진입")
        Log.e(
            "ActivityLife",
            "🔥 onCreate instance=${System.identityHashCode(this)} savedState=${savedInstanceState != null}"
        )

        // 야간모드 설정은 현재 사용하지 않음
        // AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        //위치저장 알람용
        val registerNewLocation =
            intent?.action == "REGISTER_NEW_LOCATION"

        // --------------------------------------------------------
        // 2. DB 초기화
        // --------------------------------------------------------
        AppDatabase.getDatabase(
            context = this,
            onProgress = { currentCount ->
                runOnUiThread {
                    binding.layoutLoading.visibility = View.VISIBLE
                    binding.layoutLoading.isClickable = true
                    binding.layoutLoading.isFocusable = true

                    binding.bottomNavigation.visibility = View.GONE
                    binding.bottomNavigation.isEnabled = false
                    binding.bottomNavigation.isClickable = false
                    binding.bottomNavigation.isFocusable = false

                    val totalCount = 324564
                    val percent =
                        ((currentCount.toFloat() / totalCount) * 100)
                            .toInt()
                            .coerceAtMost(100)

                    binding.tvLoadingMessage.text =
                        "초기 설정을 진행하고 있습니다.\n($percent% 완료)"
                }
            },
            onComplete = {
                runOnUiThread {
                    databaseReady = true

                    binding.layoutLoading.visibility = View.GONE

                    binding.bottomNavigation.visibility = View.VISIBLE
                    binding.bottomNavigation.isEnabled = true
                    binding.bottomNavigation.isClickable = true
                    binding.bottomNavigation.isFocusable = true

                    Log.e("AppDatabase", "✅ DB 초기화 완료 → 권한 흐름 시작")

                    // 권한 요청의 최초 진입점은 여기 하나로 고정한다.
                    startPermissionFlow()
                }
            }
        )

        // --------------------------------------------------------
        // 3. 첫 화면
        // --------------------------------------------------------
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        // --------------------------------------------------------
        // 4. Bottom Navigation
        // --------------------------------------------------------
        binding.bottomNavigation.setOnItemSelectedListener { item ->

            if (binding.layoutLoading.visibility == View.VISIBLE) {
                return@setOnItemSelectedListener false
            }

            //테스트 프레그먼트에 비번넣기
            /*if (item.itemId == R.id.nav_test && !DeveloperMode.unlocked) {

                // 여기서 인증창 표시
                showDeveloperAuthDialog()

                return@setOnItemSelectedListener false
            }*/

            val fragment: Fragment = when (item.itemId) {
                R.id.nav_bar -> HomeFragment()
                R.id.nav_stats -> StatsFragment()
                R.id.nav_data_manage -> DatabaseFragment()
                R.id.nav_settings -> SettingFragment()
                R.id.nav_test -> MoveFragment()
                else -> HomeFragment()
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()

            true
        }

        Log.e(
            "NewLocation",
            "onCreate action = ${intent?.action}"
        )
        //위치추가 알림용
        handleNotificationIntent(intent)
        Log.e("AppDatabase", "🚨온크리에이트 종료")

        //binding = ActivityMainBinding.inflate(layoutInflater)
        //setContentView(binding.root)

        // ======================================================
        // 업데이트 카드 버튼
        // ======================================================

        // 무시 → 카드만 닫기
        binding.btnUpdateIgnore.setOnClickListener {

            binding.cardUpdate.visibility = View.GONE
        }

        // 이동 → 카드 닫고 업데이트 상세 화면으로 이동
        binding.btnUpdateMove.setOnClickListener {

            binding.cardUpdate.visibility = View.GONE

            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_container,
                    fragment_update_sheet()
                )
                .addToBackStack(null)
                .commit()
        }
    }
    //위치추가 알림용
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.e(
            "NewLocation",
            "onNewIntent action = ${intent.action}"
        )
        setIntent(intent)
        handleNotificationIntent(intent)
    }
    // ------------------------------------------------------------
    // 5. onResume
    //
    // 절대로 여기서 최초 권한 요청을 시작하지 않는다.
    // 외부 "앱 설정" 화면에서 돌아온 경우에만 다음 단계 확인.
    // ------------------------------------------------------------
    override fun onResume() {
        super.onResume()

        if (!isDataInitialized) {
            Log.e(
                "AppDatabase",
                "🚨 [방어벽 작동] 아직 데이터 로딩 중 → onResume 권한처리 차단"
            )
            return
        }

        //구글시트로 업데이트 확인
        checkUpdateSheet()

        Log.e(
            "AppDatabase",
            "🚨 [onResume] DB 준비완료 / waitingForSettings=$waitingForSettings"
        )

        if (!waitingForSettings) {
            return
        }

        // 설정 화면 복귀 1회만 처리
        waitingForSettings = false

        Log.e(
            "AppDatabase",
            "🔄 설정 화면 복귀 → ${waitingSettingsStep} 단계 재확인"
        )

        waitingSettingsStep = null
        resumePermissionFlowAfterSettings()
        TriggerLogToSpot.runIfNeeded(this)


        }

    // ------------------------------------------------------------
    // 6. 권한 요청 결과 - 포그라운드 위치
    // ------------------------------------------------------------
    private val foregroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        Log.e("PermissionFlow", "📍 포그라운드 위치 권한 결과")

        val fineGranted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

        val coarseGranted =
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        Log.e(
            "PermissionFlow",
            "📍 fine=$fineGranted / coarse=$coarseGranted"
        )

        if (fineGranted || coarseGranted) {
            continuePermissionFlow()
        } else {
            showForGroundLocationRationale()
        }
    }

    // ------------------------------------------------------------
    // 7. 권한 요청 결과 - 알림
    // ------------------------------------------------------------
    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->

        Log.e("PermissionFlow", "🔔 알림 권한 결과 = $granted")

        if (granted) {
            continuePermissionFlow()
        } else {
            showAlarmRationale()
        }
    }

    // ------------------------------------------------------------
    // 8. 최초 권한 흐름 시작
    // ------------------------------------------------------------
    private fun startPermissionFlow() {
        if (permissionFlowStarted) {
            Log.e("PermissionFlow", "⛔ 권한 흐름 이미 시작됨")
            return
        }

        if (!databaseReady || !isDataInitialized) {
            Log.e(
                "PermissionFlow",
                "⛔ DB가 아직 준비되지 않아 권한 흐름 시작 안 함"
            )
            return
        }

        permissionFlowStarted = true

        Log.e("PermissionFlow", "🚀 권한 흐름 최초 시작")

        continuePermissionFlow()
    }

    // ------------------------------------------------------------
    // 9. 현재 권한 상태에 따라 다음 단계로 이동
    //
    // 순서:
    // ① 포그라운드 위치
    // ② 알림
    // ③ 백그라운드 위치
    // ④ 배터리 최적화
    // ------------------------------------------------------------
    private fun continuePermissionFlow() {

        // ① 포그라운드 위치
        if (!hasForegroundLocationPermission()) {
            Log.e("PermissionFlow", "1️⃣ 포그라운드 위치 미승인 → 요청")

            foregroundLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        Log.e("PermissionFlow", "1️⃣ 포그라운드 위치 승인")

        // ② 알림
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission()
        ) {
            Log.e("PermissionFlow", "2️⃣ 알림 미승인 → 요청")

            notificationLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
            return
        }

        Log.e("PermissionFlow", "2️⃣ 알림 승인/불필요")

        // ③ 백그라운드 위치
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasBackgroundLocationPermission()
        ) {
            Log.e("PermissionFlow", "3️⃣ 백그라운드 위치 미승인 → 안내")

            showBackgroundLocationRationale()
            return
        }

        Log.e("PermissionFlow", "3️⃣ 백그라운드 위치 승인/불필요")

        // ④ 배터리
        requestBatteryOptimization()
    }

    // ------------------------------------------------------------
    // 10. 포그라운드 위치 권한 확인
    // ------------------------------------------------------------
    private fun hasForegroundLocationPermission(): Boolean {
        val fineGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    // ------------------------------------------------------------
    // 11. 알림 권한 확인
    // ------------------------------------------------------------
    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ------------------------------------------------------------
    // 12. 백그라운드 위치 권한 확인
    // ------------------------------------------------------------
    private fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ------------------------------------------------------------
    // 13. 포그라운드 위치 거절 안내
    // ------------------------------------------------------------
    private fun showForGroundLocationRationale() {

        Log.e("PermissionFlow", "📍 포그라운드 위치 거절 안내")

        if (hasForegroundLocationPermission()) {
            permissionDialog?.dismiss()
            permissionDialog = null
            continuePermissionFlow()
            return
        }

        if (permissionDialog?.isShowing == true) {
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_battery, null)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val ivIcon = dialogView.findViewById<android.widget.ImageView>(R.id.ivDialogIcon)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val btnAction = dialogView.findViewById<android.widget.Button>(R.id.btnDialogAction)

        tvTitle.text = "위치 권한 설정 안내"
        ivIcon.setImageResource(R.drawable.locationsetting)
        tvMessage.text =
            "자동 기록을 위해 위치 권한이 필요합니다.\n앱 설정에서 위치 권한을 허용해주세요."
        btnAction.text = "설정하러 가기"

        permissionDialog = android.app.AlertDialog.Builder(
            this,
            R.style.CustomAlertDialogStyle
        )
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnAction.setOnClickListener {
            permissionDialog?.dismiss()
            waitingForSettings = true
            waitingSettingsStep = SettingsStep.FOREGROUND_LOCATION
            openAppDetailsSettings()
        }

        permissionDialog?.show()
    }

    // ------------------------------------------------------------
    // 14. 백그라운드 위치 안내
    // ------------------------------------------------------------
    private fun showBackgroundLocationRationale() {

        Log.e("PermissionFlow", "📍 백그라운드 위치 설정 안내")

        if (hasBackgroundLocationPermission()) {
            permissionDialog?.dismiss()
            permissionDialog = null
            continuePermissionFlow()
            return
        }

        if (permissionDialog?.isShowing == true) {
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_battery, null)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val ivIcon = dialogView.findViewById<android.widget.ImageView>(R.id.ivDialogIcon)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val btnAction = dialogView.findViewById<android.widget.Button>(R.id.btnDialogAction)

        tvTitle.text = "백그라운드 위치 설정 안내"
        ivIcon.setImageResource(R.drawable.locationsetting)
        tvMessage.text =
            "자동 기록을 원활하게 하려면 위치 권한을 '항상 허용'으로 설정해주세요.\n설정 화면에서 변경할 수 있습니다."
        btnAction.text = "설정하러 가기"

        permissionDialog = android.app.AlertDialog.Builder(
            this,
            R.style.CustomAlertDialogStyle
        )
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnAction.setOnClickListener {
            permissionDialog?.dismiss()
            waitingForSettings = true
            waitingSettingsStep = SettingsStep.BACKGROUND_LOCATION
            openAppDetailsSettings()
        }

        permissionDialog?.show()
    }

    // ------------------------------------------------------------
    // 15. 알림 권한 거절 안내
    // ------------------------------------------------------------
    private fun showAlarmRationale() {

        Log.e("PermissionFlow", "🔔 알림 권한 거절 안내")

        if (hasNotificationPermission()) {
            alarmDialog?.dismiss()
            alarmDialog = null
            continuePermissionFlow()
            return
        }

        if (alarmDialog?.isShowing == true) {
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_battery, null)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val ivIcon = dialogView.findViewById<android.widget.ImageView>(R.id.ivDialogIcon)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val btnAction = dialogView.findViewById<android.widget.Button>(R.id.btnDialogAction)

        tvTitle.text = "알림 권한 설정 안내"
        ivIcon.setImageResource(R.drawable.alarmsetting)
        ivIcon.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        ivIcon.requestLayout()

        tvMessage.text =
            "백그라운드 서비스 상태를 실시간으로 확인하려면 알림 권한이 필요합니다.\n앱 설정에서 알림을 허용해주세요."
        btnAction.text = "설정하러 가기"

        alarmDialog = android.app.AlertDialog.Builder(
            this,
            R.style.CustomAlertDialogStyle
        )
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnAction.setOnClickListener {
            alarmDialog?.dismiss()
            waitingForSettings = true
            waitingSettingsStep = SettingsStep.NOTIFICATION
            openAppDetailsSettings()
        }

        alarmDialog?.show()
    }

    // ------------------------------------------------------------
    // 16. 배터리 최적화 제외
    //
    // Android 6.0(API 23) 이상에서만 의미가 있다.
    // 현재 상태는 PowerManager로 확인하고,
    // 버튼을 누르면 이 앱을 배터리 최적화 예외 목록에 직접
    // 추가하도록 시스템 화면을 요청한다.
    // ------------------------------------------------------------
    private fun requestBatteryOptimization() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.e("PermissionFlow", "4️⃣ Android 6.0 미만 → 배터리 단계 건너뜀")
            return
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager

        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            batteryDialog?.dismiss()
            batteryDialog = null
            Log.e("PermissionFlow", "4️⃣ 배터리 제한 없음 상태 ✅")
            return
        }

        if (batteryDialog?.isShowing == true) {
            return
        }

        Log.e("PermissionFlow", "4️⃣ 배터리 제한 없음 설정 필요")

        val dialogView = layoutInflater.inflate(R.layout.dialog_battery, null)

        val tvTitle =
            dialogView.findViewById<android.widget.TextView>(R.id.tvDialogTitle)
        val ivIcon =
            dialogView.findViewById<android.widget.ImageView>(R.id.ivDialogIcon)
        val tvMessage =
            dialogView.findViewById<android.widget.TextView>(R.id.tvDialogMessage)
        val btnAction =
            dialogView.findViewById<android.widget.Button>(R.id.btnDialogAction)

        tvTitle.text = "배터리 제한없음 설정안내"
        ivIcon.setImageResource(R.drawable.battery)
        tvMessage.text = "원활한 위치수집을 위해 배터리 최적화를 '제한 없음'으로 설정해주세요. 설정하지 않으면 백그라운드 위치 수집이 제한될 수 있습니다."
        btnAction.text = "설정하러 가기"

        batteryDialog = android.app.AlertDialog.Builder(
            this,
            R.style.CustomAlertDialogStyle
        )
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnAction.setOnClickListener {
            batteryDialog?.dismiss()

            waitingForSettings = true
            waitingSettingsStep = SettingsStep.BATTERY

            try {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data = Uri.parse("package:$packageName")
                }

                startActivity(intent)

                Log.e("PermissionFlow", "4️⃣ 배터리 제한 없음 요청 화면 진입")
            } catch (e: Exception) {
                Log.e(
                    "PermissionFlow",
                    "4️⃣ 직접 배터리 제외 요청 실패 → 배터리 최적화 목록으로 이동",
                    e
                )

                try {
                    startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                } catch (ignored: Exception) {
                    waitingForSettings = false
                    waitingSettingsStep = null

                    Toast.makeText(
                        this,
                        "배터리 설정 화면을 열 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        batteryDialog?.show()
    }

    // ------------------------------------------------------------
    // 17. 앱 상세 설정 화면
    // ------------------------------------------------------------
    private fun openAppDetailsSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("PermissionFlow", "앱 상세 설정 열기 실패", e)
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (ignored: Exception) {
                Toast.makeText(
                    this,
                    "설정 화면을 열 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                waitingForSettings = false
                waitingSettingsStep = null
            }
        }
    }

    // ------------------------------------------------------------
    // 18. 설정 화면에서 돌아온 뒤 현재 권한 상태만 재확인
    //
    // 여기서는 런타임 권한을 자동으로 다시 launch하지 않는다.
    // 아직 거부 상태라면 안내 다이얼로그를 보여주고,
    //
    // ------------------------------------------------------------
    private fun resumePermissionFlowAfterSettings() {
        Log.e("PermissionFlow", "➡️ 설정 복귀 → 현재 권한 상태 재확인")

        when {
            !hasForegroundLocationPermission() -> {
                Log.e("PermissionFlow", "➡️ 포그라운드 위치 아직 미승인")
                showForGroundLocationRationale()
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasNotificationPermission() -> {
                Log.e("PermissionFlow", "➡️ 알림 아직 미승인")
                showAlarmRationale()
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !hasBackgroundLocationPermission() -> {
                Log.e("PermissionFlow", "➡️ 백그라운드 위치 아직 미승인")
                showBackgroundLocationRationale()
            }

            else -> {
                Log.e("PermissionFlow", "➡️ 런타임 권한 완료 → 배터리 확인")
                requestBatteryOptimization()
                //포그라운드 시작
                startHaruService()
            }
        }
    }

    // ------------------------------------------------------------
    // 19. 서비스
    // ------------------------------------------------------------
    private fun startHaruService() {
        val serviceIntent = Intent(this, ForegroundService::class.java).apply {
            action = "ACTION_START"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    // ------------------------------------------------------------
    // 20. Toast 확장
    // ------------------------------------------------------------
    fun Context.toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    fun Fragment.toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    //위치추가용 알람용
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.action != "REGISTER_NEW_LOCATION") return
        Log.e("NewLocation", "받은 action = ${intent?.action}")

        // 먼저 액션을 제거해서 재처리 방지
        intent.action = null

        Log.e("NewLocation", "새 위치 등록 실행")

        UserLocationInputHelper.saveUserLocation(this)
    }

    //테스트프레그먼트에 비밀번호 1234 등록
    private fun showDeveloperAuthDialog() {

        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD

            hint = "개발자 비밀번호"
            setSingleLine(true)
        }

        val container = FrameLayout(this).apply {
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        /*AlertDialog.Builder(this, R.style.CustomAlertDialogStyle)
            .setTitle("개발자 메뉴")
            .setMessage("개발자 메뉴입니다.\n비밀번호를 입력하세요.")
            .setView(container)
            .setNegativeButton("취소", null)
            .setPositiveButton("확인", null)
            .create()
            .apply {

                setOnShowListener {

                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {

                        if (input.text.toString() == "1236") {

                            DeveloperMode.unlocked = true

                            dismiss()

                            // 테스트 메뉴 다시 선택
                            binding.bottomNavigation.selectedItemId = R.id.nav_test

                        } else {

                            input.error = "비밀번호가 올바르지 않습니다."
                            input.requestFocus()
                        }
                    }
                }

                show()
            }*/
    }

    private fun checkUpdateSheet() {

        lifecycleScope.launch {

            // ======================================================
            // 전역변수에 시트를 마지막으로 로드한 시간을 저장
            // 마지막 로드 후 1일이 지나지 않았으면 리턴
            // 시트에서 불러온 값은 전역변수에 저장
            // 현재 앱 버전과 시트 버전을 비교하여 업데이트 카드 표시 여부 결정
            // ======================================================

            val oneDayMillis = 24 * 60 * 60 * 1000L
            val currentTime = System.currentTimeMillis()

            // 테스트 중이라 주석처리 시간비교
            if (
                lastUpdateCheckTime != 0L &&
                currentTime - lastUpdateCheckTime < oneDayMillis
            ) {
                return@launch
            }

            val result =
                Update_SheetUtil(this@MainActivity).sheetLoad()

            // 시트 로딩 성공 시에만 시간과 값을 저장
            // 시트 로딩 성공 시에만 시간과 값을 저장
            if (result != null) {

                Log.d(
                    "Update_SheetUtil",
                    "📋 시트 결과 확인 = $result"
                )

                Log.d(
                    "Update_SheetUtil",
                    "📋 시트 versionCode = ${result.versionCode}"
                )

                Log.d(
                    "Update_SheetUtil",
                    "📋 현재 앱 versionCode = ${BuildConfig.VERSION_CODE}"
                )

                Log.d(
                    "Update_SheetUtil",
                    "📋 버전 비교 결과 = ${result.versionCode > BuildConfig.VERSION_CODE}"
                )

                updateInfo = result
                lastUpdateCheckTime = System.currentTimeMillis()


                //날짜 형식변환
                val displayDate =
                    Instant.parse(result.date)
                        .atZone(ZoneId.of("Asia/Seoul"))
                        .format(
                            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
                        )

                // 현재 앱 버전과 시트 버전 비교
                if (result.versionCode > BuildConfig.VERSION_CODE) {

                    Log.d(
                        "Update_SheetUtil",
                        "🚨 업데이트 카드 표시 조건 통과"
                    )

                    binding.tvUpdateVersion.text =
                        "버전  ${result.versionName}"


                    binding.tvUpdateDate.text =
                        "날짜  $displayDate"

                    binding.tvUpdateTitle.text =
                        result.title

                    binding.cardUpdate.visibility =
                        View.VISIBLE

                    Log.d(
                        "Update_SheetUtil",
                        "🚨 업데이트 카드 표시 완료"
                    )
                }
            }
        }
    }


}
