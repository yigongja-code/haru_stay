package com.haru.haru_stay.service.유틸

import android.content.Context
import android.util.Log
import com.haru.haru_stay.data.database.AppDatabase
import com.haru.haru_stay.service.유틸.가공.LogStatsMaker
import com.haru.haru_stay.service.유틸.가공.LogToSpotMaker
import com.haru.haru_stay.util.SumUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object TriggerLogToSpot {
    //  import com.example.haru_spot.service.engine.TriggerLogToSpot
    //  호출 TriggerLogToSpot.runIfNeeded(this)
    // TriggerLogToSpot.runIfNeeded(requireContext())

    private const val TAG = "TriggerLogToSpot"

    // 중복 실행 방지
    private val isProcessing = AtomicBoolean(false)
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    /**
     * 수집 DB를 내부에서 직접 가져와 가공 파이프라인을 실행합니다.
     *
     * 흐름:
     * 수집 DB(AppDatabase)
     *      ↓
     * 1단계 LogStatsMaker
     *      ↓
     * 2단계 LogToSpotMaker
     *      ↓
     * 완료 콜백
     *
     * 기존처럼 호출하는 쪽에서 db를 미리 생성해서 넘길 필요가 없습니다.
     *
     * 반환값:
     * true  = 이번 호출에서 실제 파이프라인 실행 시작
     * false = 이미 실행 중이라 이번 호출은 무시
     */
    fun runIfNeeded(
        context: Context,
        onComplete: (() -> Unit)? = null
    ): Boolean {
        Log.e(TAG, "⚠️⚠️가공 진입⚠️⚠️ ")
        onComplete?.let {
            synchronized(pendingCallbacks) {
                pendingCallbacks.add(it)
            }
        }
        Log.e("FGS_TRACE", "🔥🔥 TriggerLogToSpot.runIfNeeded() 진입")
        Throwable().stackTrace.forEach {
            Log.e("FGS_TRACE", "→ $it")
        }

        // 이미 실행 중
        // 새 가공은 시작하지 않음
        // 대신 callback만 pendingCallbacks에 저장
        if (!isProcessing.compareAndSet(false, true)) {
            Log.e(TAG, "⚠️ 이미 가공 중 → 완료 콜백만 대기")
            return false
        }

        // Context는 Activity/Fragment보다 ApplicationContext를 사용
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ------------------------------------------------
                // 0단계. DB 직접 확보
                // ------------------------------------------------
                Log.e(TAG, "🔌 [0단계] AppDatabase 직접 확보 시작")

                val db = AppDatabase.getDatabase(appContext)

                Log.e(TAG, "🔌 [0단계 완료] AppDatabase 확보 완료")

                // ------------------------------------------------
                // 1단계. 로그 통계 가공
                // ------------------------------------------------
                Log.e(TAG, "🚀 [1단계] 1차 가공 시작")

                val logStatsMaker = LogStatsMaker(
                    db.logDao(),
                    db.spotDao()
                )

                logStatsMaker.processPendingLogs()

                Log.e(TAG, "✨ [1단계 완료] 1차 가공 끝! 이제 2단계 방아쇠 당김")

                // ------------------------------------------------
                // 2단계. 스팟 생성 및 정리
                // ------------------------------------------------
                val logToSpotMaker = LogToSpotMaker(
                    context = appContext,
                    db.logDao(),
                    db.spotDao(),
                    db.memoDao()
                )

                logToSpotMaker.createSpotsAndCleanUp()

                Log.e(TAG, "✨ [2단계 완료] 2차 스팟 생성 및 정리까지 완벽하게 끝!")

                SumUtil.calculateSum(appContext)
                Log.e(TAG, "📊 [3단계 완료] 통계 가공 끝!")




                // 가공이 끝났으면 포그라운드 수집 서비스도 다시 활성화
                //여기에 포그라운드 붙이면 앱초기실행시 홈프레그먼트의 온리섬이 먼저 실행되면서 크래시 일으킴
               /* val serviceIntent = Intent(
                    appContext,
                    ForegroundService::class.java
                ).apply {
                    action = "ACTION_START"
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(serviceIntent)
                } else {
                    appContext.startService(serviceIntent)
                }

                Log.e(TAG, "📡 가공 완료 → 포그라운드 서비스 재시작")*/

                notifyComplete()





            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "❌ 파이프라인 실행 중 오류 발생: ${e.message}",
                    e
                )
                notifyComplete()



            } finally {
                // 성공/실패와 관계없이 다음 실행을 허용
                isProcessing.set(false)
                Log.e(TAG, "🏁 파이프라인 실행 상태 해제")
            }
        }

        return true
    }

    private fun notifyComplete() {
        val callbacks = synchronized(pendingCallbacks) {
            val copy = pendingCallbacks.toList()
            pendingCallbacks.clear()
            copy
        }

        CoroutineScope(Dispatchers.Main).launch {
            callbacks.forEach { it() }
        }
    }
    /**
     * 현재 파이프라인이 실행 중인지 확인
     */
    fun isProcessing(): Boolean {
        return isProcessing.get()
    }
}
