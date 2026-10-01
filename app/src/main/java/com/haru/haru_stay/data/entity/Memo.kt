package com.haru.haru_stay.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// 5. 메모데이터 (memo_db)
@Entity(tableName = "memo_db")
data class Memo(
    @PrimaryKey(autoGenerate = true) val memo_id: Long = 0L,
    val memo_String: String,
    val memo_Field1: String = "",  //spotDB의 시작시간을 String 형태로 저장(long로 바꾸면 데이터가 날아가님
    val memo_Field2: String = "",
    val memo_Field3: String = "",
    val memo_Field4: String = "",
    val memo_Field5: String = ""
    )