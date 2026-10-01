package com.haru.haru_stay.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.haru.haru_stay.service.유틸.TriggerLogToSpot
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 충전 중일 때만 실행되는 가공 전용 Worker.
 *
 * 실제 가공은 TriggerLogToSpot이 담당합니다.
 * Worker는 WorkManager와 방아쇠 사이를 연결하는 역할만 합니다.
 */
class SpotProcessingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SpotProcessingWorker"
    }

    override suspend fun doWork(): Result {


        return try {
            val completed = suspendCancellableCoroutine<Boolean> { continuation ->
                val started = TriggerLogToSpot.runIfNeeded(applicationContext) {

                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }

                // 이미 가공 중인 경우도 기존 파이프라인 완료까지 기다립니다.
                if (!started) {

                }
            }

            if (completed) Result.success() else Result.retry()
        } catch (e: Exception) {

            Result.retry()
        }
    }
}