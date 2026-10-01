package com.haru.haru_stay.service.유틸

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class Update_SheetUtil(
    private val context: Context
) {

    data class UpdateInfo(
        val pk: Int,
        val date: String,
        val versionCode: Int,
        val versionName: String,
        val title: String,
        val content: String,
        val url: String

    )

    companion object {
        private const val TAG = "Update_SheetUtil"

        // Google Apps Script 웹앱 주소
        private const val SHEET_URL =
            "https://script.google.com/macros/s/AKfycbyMlqqX5O5t258OeqT2lKF7CHygcFDTAtSboPomI1yxt7EHIvWxEyAcGX0dZE1adsg0/exec"

    }

    // ======================================================
    // 구글시트 마지막 데이터 불러오기
    //
    // 현재는 버전 비교나 팝업 처리를 하지 않는다.
    // 시트 데이터를 UpdateInfo에 담고 Logcat으로 확인한다.
    // ======================================================
    suspend fun sheetLoad(): UpdateInfo? {

        return withContext(Dispatchers.IO) {

            var connection: HttpURLConnection? = null

            try {

                Log.d(
                    TAG,
                    "구글시트 웹앱 호출 시작"
                )

                val url = URL(SHEET_URL)

                connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000

                val responseCode =
                    connection.responseCode

                Log.d(
                    TAG,
                    "HTTP 응답 코드 = $responseCode"
                )

                if (responseCode == HttpURLConnection.HTTP_OK) {

                    val response =
                        connection.inputStream
                            .bufferedReader()
                            .use { it.readText() }

                    // ======================================================
                    // JSON → UpdateInfo
                    // ======================================================

                    val json =
                        JSONObject(response)

                    val updateInfo =
                        UpdateInfo(
                            pk = json.getInt("pk"),
                            date = json.getString("date"),
                            versionCode = json.getInt("versionCode"),
                            versionName = json.getString("versionName"),
                            title = json.getString("title"),
                            content = json.getString("content"),
                            url = json.getString("url")
                        )

                    // ======================================================
                    // 현재 확인용
                    // ======================================================

                    Log.d(
                        TAG,
                        "UpdateInfo = $updateInfo"
                    )

                    updateInfo

                } else {

                    Log.e(
                        TAG,
                        "구글시트 호출 실패 = HTTP $responseCode"
                    )

                    null
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "구글시트 호출 오류",
                    e
                )

                null

            } finally {

                connection?.disconnect()
            }
        }
    }
}