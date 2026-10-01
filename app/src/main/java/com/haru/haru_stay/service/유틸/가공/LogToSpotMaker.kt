package com.haru.haru_stay.service.유틸.가공

import android.content.Context
import android.util.Log
//import androidx.core.content.ContentProviderCompat.requireContext
import com.haru.haru_stay.data.dao.LogDao
import com.haru.haru_stay.data.dao.SpotDao
import com.haru.haru_stay.data.entity.Spot
import com.haru.haru_stay.data.entity.VisitLog
import com.haru.haru_stay.data.dao.MemoDao
import com.haru.haru_stay.service.유틸.AppSettings


class LogToSpotMaker(
    private val context: Context,
    private val logDao: LogDao,
    private val spotDao: SpotDao,
    private val memoDao: MemoDao
) {

    companion object {
        private const val TAG = "LogToSpotMaker"
    }

    /**
     * 🎯 [2차 가공: Stage 2] 로그 덩어리들을 모아 하나의 Spot으로 승격시키는 엔진
     */
    suspend fun createSpotsAndCleanUp() {
        try {
            // 💡 간지 폭발하는 stage2Logs 변수명 채택!
            val stage2Logs = logDao.머지만가져오는2차가공용쿼리()

            val firstStartTime = stage2Logs.first().logStartTime
            val lastEndTime = stage2Logs.last().logStartTime

            Log.d(
                TAG,
                "⏱️ [Stage 2] firstStartTime=$firstStartTime / lastEndTime=$lastEndTime / " +
                        "차이=${(lastEndTime - firstStartTime) / 60000}분"
            )

            if (lastEndTime - firstStartTime <= 10 * 60 * 1000L) {
                Log.d(
                    TAG,
                    "⏸️ [Stage 2] 가공 대상 전체 시간이 10분 이내 → 이번 가공 보류"
                )
                return
            }

            // spotDB가 최초 가공상태인지 판별
            // 최고가공 = 가공초기화 상태 간주
            // 0 = 일반 가공
            // 1 = spotDB가 비어 있던 초기화 가공
            var memoStatus = 0

            try {
                // 1. 가공 시작 전에 기존 spotDB 상태 확인
                val existingSpotCount = spotDao.getSpotCount()

                if (existingSpotCount == 0) {
                    memoStatus = 1
                    Log.d(TAG,"📝 기존 spotDB가 비어 있음 → 초기화 가공으로 판단")
                }

            val spotList = mutableListOf<Spot>()
            val currentGroup = mutableListOf<VisitLog>()

            // 2. disconnected 상태 및 행정구역 변경 분기 기반 그룹화
            for (i in stage2Logs.indices) {
                val log = stage2Logs[i]

                /*if (log.logStats == "disconnected") {
                    if (currentGroup.isNotEmpty()) {
                        spotList.add(createSpotFromGroup(currentGroup))
                        currentGroup.clear()
                    }
                    spotList.add(createSpotFromGroup(listOf(log)))
                    continue
                }*/


                currentGroup.add(log) //👈 비교하기 전에 일단 쥐고 있음!

                if (i < stage2Logs.size - 1) {   //처음데이터 방어코드 그냥두면 이전데이터 찾는 순간 에러터짐
                    val nextLog = stage2Logs[i + 1]  // 현로그의 다음 로그- 비교용

                    // ⭐️ [핵심 방어선]: 행정구역(AdmCode)이 달라지거나, 시간 단절이 발생하면 무조건 스팟 컷!
                    val isAdmCodeChanged = log.logAdmCode != nextLog.logAdmCode
                    val is시작종료시간비교 = log.logEndTime != nextLog.logStartTime

                    if (isAdmCodeChanged || is시작종료시간비교) {
                        if (currentGroup.isNotEmpty()) {
                            spotList.add(createSpotFromGroup(currentGroup))
                            //spotDao.insertSpot(createSpotFromGroup(currentGroup))
                            currentGroup.clear()
                        }
                    }
                }
            }

            if (currentGroup.isNotEmpty()) {
                spotList.add(createSpotFromGroup(currentGroup))
                //spotDao.insertSpot(createSpotFromGroup(currentGroup))
            }

            // 4. 시간 연속성 판정 및 융합 처리
            if (spotList.isNotEmpty()) {
                val lastExistingSpot = spotDao.getLastSpot()

                if (lastExistingSpot != null) {
                    val firstNewSpot = spotList.first()
                    if (lastExistingSpot.spEndTime == firstNewSpot.spStartTime) {
                        val updatedSpot = lastExistingSpot.copy(
                            spEndTime = firstNewSpot.spEndTime,
                            spSpotTime = firstNewSpot.spEndTime - lastExistingSpot.spStartTime
                        )
                        spotDao.updateSpot(updatedSpot)
                        spotList.removeAt(0)
                    }
                }

                if (spotList.isNotEmpty()) {
                    // 💡 저장하기 전에 'ignored' 상태가 아닌 알맹이들만 골라냅니다!
                    val validSpotsToInsert = spotList.filter { spot ->
                        spot.spStats != "ignored" // 또는 원하시는 제외 조건
                    }

                    if (validSpotsToInsert.isNotEmpty()) {
                        spotDao.insertSpots(validSpotsToInsert)
                    }
                }

                // 6. 원본 로그들 도장 쾅!

                val currentTime = System.currentTimeMillis()

                //가공된 머지(merge) 데이터를 재가공하지 않게 현재 시간을 씌워줌
                //마지막 머지 데이터를 0으로 남겨서 다른 데이터의 연결자 역활을 시킴
                // ⚠️ 마지막 로그는 처리 완료 처리하지 않는다.
                //
                // 이 마지막 merge 로그는 현재 가공의 결과물이면서
                // 동시에 다음 2차 가공에서 새 로그와 연결하기 위한 브릿지다.
                //
                // 예:
                //   현재:  a9 → b1
                //   다음:  a9 → b1 → b2
                //
                // 따라서 마지막 로그까지 processedAt을 현재시간으로 변경하면
                // 다음 실행에서 브릿지가 사라져 이전 로그와 새 로그의 연결이 끊어진다.
                //
                // processedAt = 0 → 다음 2차 가공에서도 다시 가져와야 하는 브릿지
                // processedAt != 0 → 해당 로그는 이전 가공에서 처리 완료

                val processedLogIds = stage2Logs
                    .dropLast(1) //마지막 연결자 용으로 뺌
                    .map { it.logId }

                logDao.updateLogsProcessedAt(processedLogIds, currentTime)

            }
            Log.d(TAG, "🔥 [Stage 2] 총 ${stage2Logs.size}개의 stage2Logs 가공 및 도장 완료!")

        } catch (e: Exception) {
            Log.e(TAG, "❌ [Stage 2] 2차 가공 및 청소 중 오류 발생: ${e.message}")
            e.printStackTrace()
        }

            //설정에서 입력 받은 시간내의 스팟제거 로직
            val shortSpotMinutes =
                AppSettings.getShortSpotTime(context)

            val shortSpotLimitMillis =
                shortSpotMinutes * 60 * 1000L

            //입력받은 시간 이내의 스팟을 삭제 - 의미없는 시간의 스팟을 정리
            spotDao.deleteShortUnprocessedSpots(shortSpotLimitMillis)
            //의미없는 스팟을 정리후 재가공하지 않기위해 현재시간을 마킹
            spotDao.markSpotsProcessed(System.currentTimeMillis())

            //초기화시 메모를 연결함
            // 8. 초기화 가공이었다면 Spot 생성 후 메모 복구
            if (memoStatus == 1) {
                Log.d(TAG,"📝 초기화 가공 완료 → 메모 복구 시작")
                linkMemosToSpotsStandalone()
                memoStatus = 0
                Log.d(TAG,"✨ 초기화 가공 후 특수 메모 연동 완료")
            }

        } catch (e: Exception) {
            Log.e(TAG,"❌ [Stage 2] 2차 가공 및 청소 중 오류 발생: ${e.message}",e)
        }
    }


    /**
     * 💡 그룹 내 정류장과 모든 GPS 좌표들을 파이프(|)로 엮어서 하나의 Spot 객체로 변환하는 헬퍼 함수
     */
    private fun createSpotFromGroup(group: List<VisitLog>): Spot {
        val firstLog = group.first()
        val lastLog = group.last()

        val startTime = roundTo5Minutes(firstLog.logStartTime)

        val rawEndTime =
            if (lastLog.logEndTime != 0L) lastLog.logEndTime
            else lastLog.logStartTime

        val endTime = roundTo5Minutes(rawEndTime)

        val validLogs = group.filter {
            val stop = it.logBusStop
            !stop.isNullOrBlank() && !stop.startsWith("[⚠️disconnect]")
        }



        return Spot(
            spId = 0,
            spAdmCode = firstLog.logAdmCode ?: "",
            spAdmName = firstLog.logAdmName ?: "",
            //spBusStop = bestBusStop,       // ⭐️ 가장 많이 찍힌(오래 머문) 베스트 정류장!
            //spCellKey = bestCellKey,
            //spWifiMac = cleanWifi,
            //spGpsLat = firstLog.logGpsLat,
            //spGpsLon = firstLog.logGpsLon,
            //spStats = firstLog.logStats,
            spBusStop = "",                 // ⭐️ 마지막 데이터 가공할때 오류가 생김
            spCellKey = "",                 // 스팟전체를 가져와서 비교해야되는대 안됨
            spWifiMac = "",                 // 차라리 진짜 필요할때 해당시간대의 logDB를 가져와서 구하면됨
            spGpsLat = 0.0,
            spGpsLon = 0.0,
            spStats = "",
            spStartTime = startTime,
            spEndTime = endTime,
            spSpotTime = endTime - startTime,
            spProcessedAt = 0L,             //짧은 시간의 스팟을 정리한 스탬프
            spMemoId = 0
        )
    }
    /**
     * 💡 [메모 복구 함수] 가공된 스팟들의 시작 시간(spStartTime)을 기반으로
     * memo_db에 저장되어 있던 메모 ID를 찾아와 Spot의 spMemoId에 복구·연결해 주는 함수!
     */
    /**
     * 💡 [단독 처리 함수] 이미 DB에 저장된 스팟들의 시작 시간(spStartTime)을 기반으로
     * memo_db에서 일치하는 메모를 찾아 spMemoId를 일괄 복구·연결해 주는 단독 프로세스
     */
    /**
     * 🎯 [정석 단독 후속 처리]
     * 1. 메모를 전부 불러와서 리스트화한다.
     * 2. for문으로 메모 리스트를 하나씩 돌린다.
     * 3. memo_field1 값을 파싱하여 메모_시작시간으로 삼고, 해당 시간으로 스팟DB를 불러온다.
     * 4. 불러온 스팟의 spMemoId에 메모의 memo_id를 저장하고 DB를 업데이트한다.
     */
    /**
     * 🎯 [메모 후속 단독 처리]
     * 1. 메모를 불러와 리스트한다.
     * 2. for문으로 리스트를 하나씩 돌린다.
     *   a. 메모_시작시간 = memo_field1
     *   b. 메모_시작시간으로 스팟db를 불러온다..
     *   c. spotDB의 spMemoId에 memo_id를 저장한다.
     */
    suspend fun linkMemosToSpotsStandalone() {
        try {
            // 1. 메모를 불러와 리스트한다.
            val memos = memoDao.getAllMemoList()
            if (memos.isEmpty()) return

            // 💡 한 번에 모아서 일괄 업데이트할 스팟들을 담을 바구니
            val spotsToUpdate = mutableListOf<Spot>()

            // 2. for문으로 리스트를 하나씩 돌린다.
            for (memo in memos) {
                // a. 메모_시작시간 = memo_field1
                val memoStartTime = memo.memo_Field1.toLongOrNull()

                Log.d(TAG, "📝 [값 비교] memo_Field1 ${memo.memo_Field1} | 변환결과(Long): $memoStartTime")

                if (memoStartTime == null || memoStartTime <= 0L) continue

                // b. 메모_시작시간으로 스팟db를 불러온다.
                val targetSpot = spotDao.메모id저장대상(memoStartTime)

                // c. spotDB의 spMemoId에 memo_id를 저장 (메모리에 있는 바구니에 차곡차곡 모음)
                if (targetSpot != null) {
                    if (targetSpot.spMemoId != memo.memo_id) {
                        val updatedSpot = targetSpot.copy(spMemoId = memo.memo_id)
                        spotsToUpdate.add(updatedSpot)
                    }
                }
            }

            // 3. 💡 루프가 다 끝난 후, 모아둔 스팟들이 있다면 딱 한 번에 일괄 DB 업데이트!
            if (spotsToUpdate.isNotEmpty()) {
                spotDao.insertSpots(spotsToUpdate) // REPLACE 전략이므로 update 역할 수행
                Log.d(TAG, "✨ [메모 단독 연동 완료] 총 ${spotsToUpdate.size}개의 스팟이 한 번에 일괄 업데이트되었습니다!")
            }


        } catch (e: Exception) {
            Log.e(TAG, "❌ [메모 단독 연동 오류]: ${e.message}")
        }
    }

    //기본시간을 5분 단위로 맞추기 위해서 반올림
    private fun roundTo5Minutes(time: Long): Long {
        val fiveMinutes = 5 * 60 * 1000L
        return ((time + fiveMinutes / 2) / fiveMinutes) * fiveMinutes
    }
}
