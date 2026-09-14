package com.familystudytimer.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 子ども1人分のプロフィール。studyAlarmIntervalMinutes は未達成時に繰り返すアラーム間隔A。 */
@Entity(tableName = "children")
data class ChildEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val alarmIntervalMinutes: Int = 15,
)

/**
 * 曜日ごとの目標学習時間。dayOfWeek は [java.time.DayOfWeek.getValue] 準拠 (月=1〜日=7)。
 * (childId, dayOfWeek) で一意。
 */
@Entity(tableName = "weekly_goals", primaryKeys = ["childId", "dayOfWeek"])
data class WeeklyGoalEntity(
    val childId: Long,
    val dayOfWeek: Int,
    val targetMinutes: Int,
)

/**
 * 子ども1人・1日分の学習記録。studiedSeconds は勉強ボタンで確定した累計時間（進行中のセッション分は含まない）。
 * date は "yyyy-MM-dd" 形式のローカル日付文字列。
 */
@Entity(tableName = "daily_records", primaryKeys = ["childId", "date"])
data class DailyRecordEntity(
    val childId: Long,
    val date: String,
    val goalMinutes: Int,
    val studiedSeconds: Long = 0,
    val achieved: Boolean = false,
    val achievedAtEpochMillis: Long? = null,
)

/**
 * 子どもごとの「今まさに勉強中か」を表す実行時状態。studyStartEpochMillis が null なら勉強していない。
 * バックグラウンドに回っても再開時にこのタイムスタンプから経過時間を計算し直すだけなので、
 * 常駐サービスなしで正しくカウントを継続できる。
 */
@Entity(tableName = "study_state")
data class StudyStateEntity(
    @PrimaryKey val childId: Long,
    val studyStartEpochMillis: Long? = null,
)
