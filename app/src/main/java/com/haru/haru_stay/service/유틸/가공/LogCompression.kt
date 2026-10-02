package com.haru.haru_stay.service.유틸

import com.haru.haru_stay.data.entity.Fav
//import com.haru.haru_stay.data.entity.Favorite
import com.haru.haru_stay.data.entity.VisitLog

object LogCompression {

    /**
     * =========================================================
     * 3차 로그 압축
     * =========================================================
     *
     * 목적
     * - 로그 용량 감소가 1차 목적이 아님
     * - 확실한 장소(Wi-Fi)를 기준으로
     *   GPS 미세 튐이 이동경로에 표시되는 것을 줄이는 것이 목적
     *
     * 원칙
     * - log_db 원본 데이터는 수정하지 않는다.
     * - 로그 삭제도 하지 않는다.
     * - logStartTime / logEndTime / GPS / Wi-Fi 등 원본값은 유지한다.
     * - logField2 상태값만 사용한다.
     *
     * 상태값
     * - pending
     * - comfirst
     * - comignored
     * - comend
     *
     * 아직 실제 DB 연결은 하지 않는다.
     * List를 받아서 가공 결과 List를 반환하는 형태로 먼저 구현한다.
     */
    fun compressLogs(
        logs: List<VisitLog>,
        favorites: List<Fav>
    ): List<VisitLog> {

        // =====================================================
        // 앞으로 실제 압축 알고리즘이 들어갈 부분
        // =====================================================


        // 현재는 원본을 그대로 반환
        return logs
    }
}