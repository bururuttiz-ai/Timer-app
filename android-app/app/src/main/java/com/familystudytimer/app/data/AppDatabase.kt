package com.familystudytimer.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChildEntity::class,
        WeeklyGoalEntity::class,
        DailyRecordEntity::class,
        StudyStateEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun childDao(): ChildDao
    abstract fun weeklyGoalDao(): WeeklyGoalDao
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun studyStateDao(): StudyStateDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "study_timer.db",
                )
                    // 開発中のスキーマ変更のたびに手動アンインストールしなくて済むよう、
                    // マイグレーションは書かずに再作成する（家庭内利用アプリのため許容）
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
