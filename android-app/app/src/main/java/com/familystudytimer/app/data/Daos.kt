package com.familystudytimer.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildDao {
    @Query("SELECT * FROM children ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<ChildEntity>>

    @Query("SELECT * FROM children ORDER BY sortOrder ASC")
    suspend fun getAll(): List<ChildEntity>

    @Query("SELECT * FROM children WHERE id = :childId")
    suspend fun getById(childId: Long): ChildEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(child: ChildEntity): Long

    @Update
    suspend fun update(child: ChildEntity)
}

@Dao
interface WeeklyGoalDao {
    @Query("SELECT * FROM weekly_goals WHERE childId = :childId ORDER BY dayOfWeek ASC")
    fun observeForChild(childId: Long): Flow<List<WeeklyGoalEntity>>

    @Query("SELECT * FROM weekly_goals WHERE childId = :childId AND dayOfWeek = :dayOfWeek")
    suspend fun get(childId: Long, dayOfWeek: Int): WeeklyGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: WeeklyGoalEntity)
}

@Dao
interface DailyRecordDao {
    @Query("SELECT * FROM daily_records WHERE childId = :childId AND date = :date")
    suspend fun get(childId: Long, date: String): DailyRecordEntity?

    @Query("SELECT * FROM daily_records WHERE childId = :childId AND date = :date")
    fun observe(childId: Long, date: String): Flow<DailyRecordEntity?>

    @Query("SELECT * FROM daily_records WHERE childId = :childId ORDER BY date DESC")
    fun observeHistory(childId: Long): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_records WHERE childId = :childId ORDER BY date DESC")
    suspend fun getHistory(childId: Long): List<DailyRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DailyRecordEntity)
}

@Dao
interface StudyStateDao {
    @Query("SELECT * FROM study_state WHERE childId = :childId")
    suspend fun get(childId: Long): StudyStateEntity?

    @Query("SELECT * FROM study_state")
    fun observeAll(): Flow<List<StudyStateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: StudyStateEntity)
}
