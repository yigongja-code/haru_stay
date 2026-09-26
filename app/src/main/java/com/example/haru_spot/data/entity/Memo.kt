package com.example.haru_spot.data.entity

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.haru_spot.data.entity.Memo

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