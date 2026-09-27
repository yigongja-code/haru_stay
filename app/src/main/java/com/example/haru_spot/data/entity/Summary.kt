package com.example.haru_spot.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity data class Sum_db(
    @PrimaryKey(autoGenerate = true) val sumId: Long = 0,
    //식별자 행정동코드(10자리)+기간(3자리) 1111111111090 (코드10자리+090일)
    val sumKey: String,             //!!!!!관리행으로 사용시 앞에 9999999999 + 기간(3자리)붙여서 키를 만듬
    val sumAdmCode: String,         //법정동 코드  !!!!! 관리행 사용시 999999999  <- 코드처럼 9 열개
    val sumAdmName: String,         //법정동 주소  !!!!! 관리행 사용시 관리행 입력

    val sumLastTime: Long,          //마지막 방문  !!!!! 관리행 사용시 오늘날자를 입력해서 중복전채실행 방지 현재시간(millis)  저장

    val sumVisitRank: Int,          //방문 랭킹
    val sumVisitCount: Int,         //방문 횟수

    val sumStayRank: Int,           //체류 랭킹
    val sumStayMinutes: Long,       //체류 시간 !!!!! 관리행 구글시트(업데이트/공지)를 불러오는 식별자로 사용 1일? *sumutil에서 최초 식별

    val sumPeriodDays : Int         //몇일짜리 데이터인지 예 30일 90일 180일  !!!!!! 관리행 사용시 범위값 똑같이 저장
)