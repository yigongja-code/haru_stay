package com.example.haru_spot.service.유틸

import com.example.haru_spot.ui.adapter.HomeTimelineItem
import java.util.Calendar
import java.util.TimeZone

class LongStay {

    companion object {

        /**
         * 📌 오늘 날짜에 데이터가 없을 때, 이전부터 이어지고 있는 가장 가까운 긴 체류(LongStay) 스팟을 찾아
         * 오늘 하루를 꽉 채울 가상 타임라인 아이템으로 변환해 주는 브레인 함수
         *
         * @param targetDateEpoch 오늘 자정(00:00:00)의 밀리초 값
         * @param allItems DB에 저장된 전체 타임라인 아이템 목록 (또는 긴 체류 스팟 목록)
         */
        fun resolveOngoingLongStay(targetDateEpoch: Long, allItems: List<HomeTimelineItem>): HomeTimelineItem? {
            // 1. 만약 오늘 날짜에 이미 아이템이 존재한다면 굳이 가상 스팟을 만들 필요 없음 (필요에 따라 뷰어에서 체크 가능)

            // 2. 현재 타겟 날짜(오늘)보다 이전에 시작했거나 걸쳐있으면서,
            //    종료 에폭(endEpoch)이 아직 오늘 이후이거나 오늘 끝나는 '긴 체류' 스팟 색출
            val activeLongStayItem = allItems.find { item ->
                // 조건: 시작일은 오늘보다 같거나 이전이고, 종료일은 오늘보다 이후(또는 오늘 포함)인 경우
                item.startEpoch <= targetDateEpoch && item.endEpoch >= targetDateEpoch
            }

            if (activeLongStayItem == null) return null

            // 3. 찾았다면, 오늘 하루 종일(00:00 ~ 24:00)을 꽉 채우는 가상 아이템으로 껍데기를 씌워서 리턴
            // (시작/끝 시간을 오늘의 00:00과 24:00 밀리초로 세팅하거나, 뷰어가 그리기 좋게 포장)
            return activeLongStayItem.copy(
                startTime = "00:00",
                endTime = "24:00",
                timeRangeStr = "진행 중인 긴 체류 스팟 연장선"
            )
        }

        /**
         * Long 타입의 에폭 밀리초를 받아 하루 중 분(Minute) 좌표로 환산 (뷰어 렌더링용)
         */
        fun convertEpochToDayMinutes(epochMillis: Long): Float {
            val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = epochMillis
            }
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val second = calendar.get(Calendar.SECOND)

            return (hour * 60f) + minute + (second / 60f)
        }
    }
}