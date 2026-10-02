package com.haru.haru_stay.ui.settingfragment

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.haru.haru_stay.R
import com.haru.haru_stay.data.database.AppDatabase
//import com.example.haru_spot.data.database.HaruDatabase
import com.haru.haru_stay.data.entity.Fav
import com.haru.haru_stay.data.entity.Memo
import com.haru.haru_stay.data.entity.VisitLog
//import com.example.haru_spot.ui.HaruDatabase
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import android.util.Log
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class fragment_data_management_setting : Fragment() {

    private val REQUEST_CODE_LOAD_DATA = 9999


    data class HaruDatabase(
        val logs: List<VisitLog>,
        val favorites: List<Fav>, // 형님의 실제 즐겨찾기 Entity명에 맞춤
        val memos: List<Memo>          // 형님의 실제 메모 Entity명에 맞춤
    )


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.fragment_setting_data_management,
            container,
            false
        )

        // 1. 상단 '설정으로 돌아가기'
        initBackButton(view)

        // 2. 데이터 관리 버튼 연결
        initDataManagementButtons(view)

        // 3. 데이터 저장 안내 메시지
        initDataStorageInfo(view)

        // 4. 데이터 불러오기 안내 메시지
        initDataLoadInfo(view)

        // 5. 데이터 초기화 안내 메시지
        initDataResetInfo(view)

        return view
    }


    /**
     * 상단 '설정으로 돌아가기'
     */
    private fun initBackButton(view: View) {

        val backButton =
            view.findViewById<MaterialCardView>(
                R.id.layout_back_to_setting
            )

        backButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }


    /**
     * 데이터 관리 버튼 연결
     *
     * 데이터 저장
     * 데이터 불러오기
     * 데이터 초기화
     */
    private fun initDataManagementButtons(view: View) {

        // 데이터 저장
        val saveButton =
            view.findViewById<Button>(R.id.btn_data_save)

        saveButton.setOnClickListener {
            saveAllDataToDownloadFolder()
        }


        // 데이터 불러오기
        val loadButton =
            view.findViewById<Button>(R.id.btn_data_load)

        loadButton.setOnClickListener {

            AlertDialog.Builder(
                requireContext(),
                R.style.CustomAlertDialogStyle
            )
                .setTitle("데이터 불러오기")
                .setMessage(
                    "기존 데이터를 모두 비우고 선택한 백업 파일의 데이터로 " +
                            "덮어씌우시겠습니까?\n\n" +
                            "다운로드 폴더의 Haru_Database.json을 선택하면 됩니다."
                )
                .setPositiveButton("예") { _, _ ->

                    val intent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/json"

                            // 파일 탐색기에서 'haru' 검색을 유도
                            putExtra(Intent.EXTRA_TITLE, "haru")
                        }

                    startActivityForResult(
                        intent,
                        REQUEST_CODE_LOAD_DATA
                    )
                }
                .setNegativeButton("아니오", null)
                .show()
        }


        // 데이터 초기화
        val resetButton =
            view.findViewById<Button>(R.id.btn_data_reset)

        resetButton.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }


    /**
     * 파일 선택 결과
     */
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode == REQUEST_CODE_LOAD_DATA &&
            resultCode == android.app.Activity.RESULT_OK
        ) {

            data?.data?.let { uri ->
                loadAllDataFromDownloadFolder(uri)
            }
        }
    }


    /**
     * 데이터 초기화 확인창
     *
     * 초기화 전에 현재 데이터를 자동으로 백업한다.
     *
     * 5초 동안 확인 버튼을 숨기고,
     * 사용자가 '초기화'를 정확하게 입력해야 실제 삭제가 진행된다.
     */
    private fun showDeleteConfirmDialog() {

        val editText =
            EditText(requireContext()).apply {
                hint = "초기화"
                setSingleLine(true)
                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT
                gravity = Gravity.CENTER
            }


        val dialog =
            AlertDialog.Builder(
                requireContext(),
                R.style.CustomAlertDialogStyle
            )
                .setTitle("데이터 초기화")
                .setMessage(
                    "저장된 위치 기록과 스팟 데이터, " +
                            "즐겨찾기 및 집계 데이터가 삭제됩니다.\n\n" +
                            "초기화 전에 현재 데이터를 자동으로 백업합니다.\n\n" +
                            "계속하려면 '초기화'를 입력하세요."
                )
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setView(editText)
                .setPositiveButton("확인", null)
                .setNegativeButton("취소") { dialog, _ ->
                    dialog.dismiss()
                }
                .create()


        dialog.setOnShowListener {

            val positiveButton =
                dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
                )

            // 처음에는 확인 버튼을 숨긴다.
            positiveButton.visibility =
                View.GONE


            // 5초 후 확인 버튼 표시
            android.os.Handler(
                android.os.Looper.getMainLooper()
            ).postDelayed({

                if (dialog.isShowing) {
                    positiveButton.visibility =
                        View.VISIBLE
                }

            }, 5000)


            // 확인 버튼 클릭
            positiveButton.setOnClickListener {

                if (editText.text.toString() != "초기화") {

                    Toast.makeText(
                        requireContext(),
                        "'초기화'를 정확히 입력해주세요.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener
                }


                viewLifecycleOwner.lifecycleScope.launch {

                    try {

                        val db =
                            AppDatabase.getDatabase(
                                requireContext()
                            )


                        // 1. 기존 로그 데이터 삭제
                        db.logDao().deleteAllLogs()


                        // 2. 스팟 데이터 삭제
                        db.spotDao().deleteAllSpots()


                        // 3. 즐겨찾기 데이터 삭제
                        db.favoriteDao().deleteAllFav()


                        // 4. 순위 데이터 삭제
                        db.sumDao().전체SumDB삭제()


                        Toast.makeText(
                            requireContext(),
                            "위치 데이터, 홈 데이터, 즐겨찾기 데이터가 초기화되었습니다.",
                            Toast.LENGTH_SHORT
                        ).show()


                        dialog.dismiss()

                    } catch (e: Exception) {

                        e.printStackTrace()

                        Toast.makeText(
                            requireContext(),
                            "초기화 실패: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }


        dialog.show()


        // 다이얼로그가 뜨자마자 백업 시작
        saveAllDataToDownloadFolder()
    }


    /**
     * 데이터 내보내기
     *
     * 앱에 저장된 주요 데이터를 JSON 파일로 백업한다.
     *
     * - 로그 / 즐겨찾기 / 메모 등을 저장
     * - 파일명: Haru_Database_날짜.json
     * - 다운로드 폴더에 저장
     * - 로그는 현재 가공 상태가 아닌 pending 상태로 저장하여
     *   가져오기 후 다시 처리할 수 있도록 한다.
     *
     * 주의:
     * - 앱 초기화 또는 휴대전화 변경 전에 백업 용도로 사용
     * - 내보낸 파일은 사용자가 별도로 보관해야 한다.
     */
    private fun saveAllDataToDownloadFolder() {

        viewLifecycleOwner.lifecycleScope.launch(
            Dispatchers.IO
        ) {

            try {

                val db =
                    AppDatabase.getDatabase(
                        requireContext()
                    )


                // 1. 로그 데이터 가져오기
                val saveLogs =
                    db.logDao().getAllLogsList()


                // 로그가 10개 이하라면
                // 백업하지 않는다.
                if (saveLogs.size <= 10) {
                    return@launch
                }


                // 2. 즐겨찾기 / 메모 데이터 가져오기
                val saveFav =
                    db.favoriteDao().getAllFavList()

                val saveMemo =
                    db.memoDao().getAllMemoList()


                // 3. 로그 가공 상태를 초기화해서 백업
                val resetLogs =
                    saveLogs.map { log ->

                        log.copy(
                            logStats = "pending",
                            logEndTime = 0L,
                            logProcessedAt = 0L
                        )
                    }


                val totalBasket =
                    HaruDatabase(
                        logs = resetLogs,
                        favorites = saveFav,
                        memos = saveMemo
                    )


                val gson = Gson()


                // 4. 다운로드 폴더
                val downloadDir =
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    )


                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }


                // 5. 현재 날짜 + 시간을 파일명에 사용
                val fileName =
                    SimpleDateFormat(
                        "yyyy-MM-dd_HHmmss",
                        Locale.getDefault()
                    ).format(Date())


                val databaseFile =
                    File(
                        downloadDir,
                        "Haru_Database_$fileName.json"
                    )


                // 6. 새 백업 파일 생성
                FileWriter(databaseFile).use { writer ->

                    writer.write(
                        gson.toJson(totalBasket)
                    )
                }


                Log.e(
                    "DataBackup",
                    "✅ 새 백업 생성 / file=${databaseFile.name} / size=${databaseFile.length()}"
                )


                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        requireContext(),
                        "데이터 내보내기 완료\n${databaseFile.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }


            } catch (e: Exception) {

                Log.e(
                    "DataBackup",
                    "❌ 저장 실패 / ${e.javaClass.name}",
                    e
                )


                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        requireContext(),
                        "저장 실패: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }


    /**
     * 데이터 불러오기
     *
     * 선택한 JSON 백업 파일을 읽어서
     * 기존 데이터를 삭제한 뒤 새 데이터로 복원한다.
     */
    private fun loadAllDataFromDownloadFolder(
        uri: android.net.Uri
    ) {

        viewLifecycleOwner.lifecycleScope.launch(
            Dispatchers.IO
        ) {

            try {

                val context =
                    requireContext()


                // 1. 선택한 JSON 파일 읽기
                val jsonString =
                    context.contentResolver
                        .openInputStream(uri)
                        ?.bufferedReader()
                        .use { it?.readText() }


                if (!jsonString.isNullOrEmpty()) {

                    val gson = Gson()


                    // 2. JSON → HaruDatabase
                    val backupData =
                        gson.fromJson(
                            jsonString,
                            HaruDatabase::class.java
                        )


                    val db =
                        AppDatabase.getDatabase(
                            context
                        )


                    // 3. 기존 데이터를 삭제하고
                    //    백업 데이터로 덮어쓰기

                    // 로그
                    if (!backupData.logs.isNullOrEmpty()) {

                        db.logDao().deleteAllLogs()

                        backupData.logs.forEach {
                            db.logDao().insert(it)
                        }
                    }


                    // 즐겨찾기
                    if (!backupData.favorites.isNullOrEmpty()) {

                        db.favoriteDao().deleteAllFav()

                        backupData.favorites.forEach {
                            db.favoriteDao().insert(it)
                        }
                    }


                    // 메모
                    // 메모
                    if (!backupData.memos.isNullOrEmpty()) {

                        db.memoDao().deleteAllMemo()

                        backupData.memos.forEach {
                            db.memoDao().insertMemo(it)
                        }
                    }

                    // 순위 데이터는 백업하지 않고
                    // 복원 후 다시 계산하도록 초기화
                    db.sumDao().전체SumDB삭제()


                    withContext(Dispatchers.Main) {

                        Toast.makeText(
                            context,
                            "데이터 불러오기 및 덮어쓰기 완료!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            } catch (e: Exception) {

                e.printStackTrace()

                withContext(Dispatchers.Main) {

                    Toast.makeText(
                        requireContext(),
                        "불러오기 실패 (잘못된 파일이거나 형식 오류): ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    /**
     * 데이터 저장 안내
     */
    private fun initDataStorageInfo(view: View) {

        val tvDataStorageInfo =
            view.findViewById<TextView>(R.id.tv_data_storage_info)

        tvDataStorageInfo.text = """
        • 앱에 저장된 위치 기록, 즐겨찾기, 메모 등을 파일로 저장합니다.
        • 휴대전화의 다운로드 폴더에  Haru_Database_날짜.json 으로 저장됩니다.
        • 휴대전화를 변경하거나 앱을 초기화하기 전에 중요한 데이터는 먼저 저장하기를 권장합니다.
    """.trimIndent()
    }
    /**
     * 데이터 불러오기 안내
     */
    private fun initDataLoadInfo(view: View) {

        val tvDataLoadInfo =
            view.findViewById<TextView>(R.id.tv_data_load_info)

        tvDataLoadInfo.text = """
        • 데이터 불러오기를 하면 기존에 있던 데이터를 삭제한 후 대체합니다.
        • 중요한 데이터가 있다면 불러오기 전에 현재 데이터를 먼저 저장 하기를 권장합니다.
    """.trimIndent()
    }

    /**
     * 데이터 초기화 안내
     */
    private fun initDataResetInfo(view: View) {

        val tvDataResetInfo =
            view.findViewById<TextView>(R.id.tv_data_reset_info)

        tvDataResetInfo.text = """
        • 앱에 저장된 모든 데이터를 삭제하고 초기 상태로 되돌립니다.
        • 삭제된 데이터는 복구할 수 없으므로 초기화하기 전에 중요한 데이터는 먼저 내보내기를 권장합니다.
        • 초기화 후에는 위치 기록, 즐겨찾기, 메모 등의 데이터가 모두 삭제됩니다.
    """.trimIndent()
    }
}

