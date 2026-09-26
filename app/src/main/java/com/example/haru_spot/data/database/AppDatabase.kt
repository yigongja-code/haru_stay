package com.example.haru_spot.data.database

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.haru_spot.data.dao.*
import com.example.haru_spot.data.entity.*
import com.example.haru_spot.service.유틸.가공.LogStatsMaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader


@Database(
    entities = [
        Fav::class,
        VisitLog::class,
        Spot::class,
        Cache::class,
        Memo::class,
        BusStopEntity::class,
        Sum_db::class

    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao
    abstract fun logDao(): LogDao
    abstract fun spotDao(): SpotDao
    abstract fun cacheDao(): CacheDao
    abstract fun memoDao(): MemoDao
    abstract fun busStopDao(): BusStopDao
    abstract fun sumDao(): SumDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 🚀 [핵심] 앱 전체에서 공유되는 전역 준비 완료 플래그!
        @Volatile
        var isDataInitialized = false
            private set // 외부에서는 읽을 수만 있게 안전장치

        // 🚀 1. getDatabase에서 onComplete 콜백까지 받을 수 있도록 파라미터 확장!
        fun getDatabase(
            context: Context,
            onProgress: ((Int) -> Unit)? = null,
            onComplete: (() -> Unit)? = null
        ): AppDatabase {
            val appContext = context.applicationContext
            Log.e("AppDatabase", "🔥🔥🔥 [Room getdatabase 진입바로후]  🔥🔥🔥")


            // 1. 이미 메모리에 올라와 있을 때 (재방문/재실행 등)
            // 1. 이미 메모리에 올라와 있을 때 (재방문/재실행 등)
            /*INSTANCE?.let {
                isDataInitialized = true // 👈 전역 플래그만 켜고
                return it                // 👈 여기서 즉시 리턴! (앱에 자료 새로 안 올릴 때)
            }*/

            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "haru_spot_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance

                // 🎯 DB가 생성된 직후 백그라운드에서 실행
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val logStatsMaker = LogStatsMaker(instance.logDao(), instance.spotDao())
                        logStatsMaker.processPendingLogs()
                        Log.e("AppDatabase", "🔥🔥🔥 [AppDatabase] 1차 및 2차 연쇄 가공 완료! 🔥🔥🔥")

                        val count = instance.busStopDao().getCount()
                        Log.e("AppDatabase", "🔍 현재 DB 정류장 개수 확인: $count")
                        if (count == 0) {
                            Log.e("AppDatabase", "🔥 데이터가 없으므로 CSV 프리로드를 시작합니다!")
                            preloadBusStops(
                                appContext,
                                instance,
                                onProgress = { currentCount ->
                                    Log.e("AppDatabase", "🔄 현재 버스정류장 프리로드 진행 중... ${currentCount}건 완료")
                                    onProgress?.invoke(currentCount)
                                    isDataInitialized = false
                                },
                                onComplete = {
                                    Log.e("AppDatabase", "🎉 버스정류장 프리로드 최종 완료!")
                                    isDataInitialized = true
                                    onComplete?.invoke() // 👈 프리로드 끝났다고 신호 쏘기!
                                }
                            )
                        } else {
                            // 데이터가 이미 있어서 프리로드가 필요 없을 때도 완료 신호는 쏴줘야 로딩바가 꺼짐!
                            isDataInitialized = true
                            onComplete?.invoke()
                        }
                    } catch (e: Exception) {
                        isDataInitialized = true // 👈 [여기 추가] 기존 데이터 쓸 때도 깃발 켜기!
                        Log.e("AppDatabase", "❌ 초기화 작업 중 오류 발생: ${e.message}")
                        onComplete?.invoke() // 예외가 나더라도 로딩바가 영원히 돌지 않게 끄기
                    }
                }

                instance
            }
        }

        // 🚀 2. preloadBusStops에 onComplete 콜백 파라미터 추가
        private suspend fun preloadBusStops(
            context: Context,
            database: AppDatabase,
            onProgress: (Int) -> Unit,
            onComplete: () -> Unit
        ) {
            try {
                val dao = database.busStopDao()

                context.assets.open("mergegps2026.csv").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->

                        val header = reader.readLine()
                        if (header == null) {
                            onComplete()
                            return
                        }

                        val batchList = mutableListOf<BusStopEntity>()
                        var line: String? = reader.readLine()
                        var count = 0

                        while (line != null) {
                            val tokens = line.split(",")
                            if (tokens.size >= 5) {
                                try {
                                    val name = tokens[0].trim()
                                    val lat = tokens.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 0.0
                                    val lon = tokens.getOrNull(2)?.trim()?.toDoubleOrNull() ?: 0.0
                                    val admCode = tokens.getOrNull(3)?.trim() ?: ""

                                    val cityStr = tokens.getOrNull(4)?.trim() ?: ""
                                    val admStr = tokens.getOrNull(5)?.trim() ?: ""

                                    val combinedAdmName = if (admStr.isNotBlank() && cityStr.isNotBlank()) {
                                        "$admStr($cityStr)"
                                    } else {
                                        admStr.ifBlank { cityStr }
                                    }

                                    batchList.add(
                                        BusStopEntity(
                                            stopName = name,
                                            lat = lat,
                                            lon = lon,
                                            admCode = admCode,
                                            admName = combinedAdmName
                                        )
                                    )

                                    if (batchList.size >= 1000) {
                                        dao.insertAll(batchList)
                                        count += batchList.size
                                        batchList.clear()
                                        onProgress(count)
                                    }
                                } catch (e: Exception) {
                                    // 개별 행 파싱 오류 무시
                                }
                            }
                            line = reader.readLine()
                        }

                        if (batchList.isNotEmpty()) {
                            dao.insertAll(batchList)
                            count += batchList.size
                            onProgress(count)
                        }

                        Log.e("AppDatabase", "🚀 BusStop CSV Preload Success! 총 ${count}건 적재 완료!")
                        onComplete() // 🚀 3. 정상 완료 시 신호 발사!
                    }
                }
            } catch (e: Exception) {
                Log.e("AppDatabase", "❌ BusStop CSV Preload Failed: ${e.message}")
                onComplete() // 에러 나도 꺼주기
            }
        }
    }
}