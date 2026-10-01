package com.haru.haru_stay.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.haru.haru_stay.data.entity.Memo
import androidx.room.OnConflictStrategy


@Dao
interface MemoDao {
    // 💡 REPLACE를 붙여주면: 기존 ID가 겹치면 덮어쓰기(수정), 없으면 새로 저장(삽입)!
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun 메모저장id반환(memo: Memo): Long // 👈 나중에 Spot에 spMemoId를 꽂아줘야 하니 Long 리턴값도 챙겨주세요!

    // 💡 [추가] ID로 메모 행을 삭제하는 쿼리
    @Query("DELETE FROM memo_db WHERE memo_id = :memoId")
    suspend fun deleteMemoById(memoId: Long)

    @Query("SELECT * FROM memo_db")
    suspend fun getAllMemoList(): List<Memo>

    @Query("SELECT * FROM memo_db WHERE memo_id = :memoId LIMIT 1")
    suspend fun spMemoId로memo불러오기(memoId: Long): Memo?


    // 💡 [추가] memo_Field1 값(시작 시간 문자열들)과 일치하는 메모들을 한 번에 가져오는 쿼리
    @Query("SELECT * FROM memo_db WHERE memo_Field1 IN (:field1List)")
    suspend fun getMemosByField1In(field1List: List<String>): List<Memo>

    // 💡 [추가] 테이블의 모든 메모 데이터를 싹 비우는 전체 삭제 쿼리
    @Query("DELETE FROM memo_db")
    suspend fun deleteAllMemos()

    @Query("DELETE FROM memo_db") // 👈 형님의 실제 테이블 이름에 맞춤
    suspend fun deleteAllMemo()

    // 백업 데이터 복원용
    @Insert
    suspend fun insertMemo(memo: Memo)


}