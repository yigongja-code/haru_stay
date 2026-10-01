package com.haru.haru_stay.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.haru.haru_stay.data.entity.Sum_db

@Dao
interface SumDao {

    @Insert
    suspend fun insertSum(sum: Sum_db)

    @Query("SELECT * FROM Sum_db")
    suspend fun getAllSum(): List<Sum_db>

    @Insert
    suspend fun insertSumList(
        sumList: List<Sum_db>
    )
    @Update
    suspend fun updateSum(
        sum: Sum_db
    )

    @Query("""
    SELECT *
    FROM sum_db
    WHERE sumKey = :sumKey
    LIMIT 1
""")
    suspend fun sumKey로가져옴(
        sumKey: String
    ): Sum_db?

    @Query("DELETE FROM sum_db")
    suspend fun 전체SumDB삭제()

    @Query("""
    DELETE FROM sum_db
    WHERE sumPeriodDays = :periodDays
      AND sumAdmCode != '9999999999'
""")
    suspend fun 해당기간SumDB삭제(
        periodDays: Int
    )

}