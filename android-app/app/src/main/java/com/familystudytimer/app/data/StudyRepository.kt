package com.familystudytimer.app.data

import com.familystudytimer.app.alarm.AlarmScheduler
import com.familystudytimer.app.util.TimeUtils
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

data class PauseResult(
    val record: DailyRecordEntity,
    val achievedJustNow: Boolean,
)

/**
 * 学習タイマーの中心となるビジネスロジック。
 *
 * 経過時間はすべて「開始時刻（絶対タイムスタンプ）」から都度計算するため、アプリがバックグラウンドや
 * 停止状態でも、次に開いたときに正しい経過時間へ復元できる（常駐サービス不要）。
 */
class StudyRepository(
    private val db: AppDatabase,
    private val alarmScheduler: AlarmScheduler,
) {
    private val zone = ZoneId.systemDefault()

    fun observeChildren(): Flow<List<ChildEntity>> = db.childDao().observeAll()

    suspend fun getChildren(): List<ChildEntity> = db.childDao().getAll()

    suspend fun createChild(name: String, sortOrder: Int): Long {
        val id = db.childDao().upsert(ChildEntity(name = name, sortOrder = sortOrder))
        db.studyStateDao().upsert(StudyStateEntity(childId = id, studyStartEpochMillis = null))
        return id
    }

    suspend fun renameChild(childId: Long, newName: String) {
        val child = db.childDao().getById(childId) ?: return
        db.childDao().update(child.copy(name = newName))
    }

    suspend fun setAlarmIntervalMinutes(childId: Long, minutes: Int) {
        val child = db.childDao().getById(childId) ?: return
        db.childDao().update(child.copy(alarmIntervalMinutes = minutes))
    }

    fun observeWeeklyGoals(childId: Long): Flow<List<WeeklyGoalEntity>> =
        db.weeklyGoalDao().observeForChild(childId)

    suspend fun setWeeklyGoal(childId: Long, dayOfWeek: Int, targetMinutes: Int) {
        db.weeklyGoalDao().upsert(WeeklyGoalEntity(childId, dayOfWeek, targetMinutes))
    }

    fun observeTodayRecord(childId: Long): Flow<DailyRecordEntity?> =
        db.dailyRecordDao().observe(childId, TimeUtils.todayKey())

    fun observeHistory(childId: Long): Flow<List<DailyRecordEntity>> =
        db.dailyRecordDao().observeHistory(childId)

    suspend fun getHistory(childId: Long): List<DailyRecordEntity> =
        db.dailyRecordDao().getHistory(childId)

    suspend fun isStudying(childId: Long): Boolean =
        db.studyStateDao().get(childId)?.studyStartEpochMillis != null

    /** 現在の表示用累計秒数（確定分 + 勉強中ならその場の経過分）。 */
    suspend fun currentDisplaySeconds(childId: Long): Long {
        val record = getOrCreateTodayRecord(childId)
        val state = db.studyStateDao().get(childId)
        val running = state?.studyStartEpochMillis?.let { start ->
            val todayStartMillis = TimeUtils.today().atStartOfDay(zone).toInstant().toEpochMilli()
            val effectiveStart = maxOf(start, todayStartMillis)
            (TimeUtils.nowEpochMillis() - effectiveStart) / 1000
        } ?: 0L
        return record.studiedSeconds + running
    }

    suspend fun getOrCreateTodayRecord(childId: Long): DailyRecordEntity =
        getOrCreateRecordForDate(childId, TimeUtils.today())

    private suspend fun getOrCreateRecordForDate(childId: Long, date: LocalDate): DailyRecordEntity {
        val key = TimeUtils.dateKey(date)
        db.dailyRecordDao().get(childId, key)?.let { return it }
        val goal = db.weeklyGoalDao().get(childId, date.dayOfWeek.value)?.targetMinutes ?: 0
        val record = DailyRecordEntity(childId = childId, date = key, goalMinutes = goal)
        db.dailyRecordDao().upsert(record)
        return record
    }

    suspend fun startStudy(childId: Long) {
        val state = db.studyStateDao().get(childId)
        if (state?.studyStartEpochMillis != null) return // 既に勉強中
        getOrCreateTodayRecord(childId)
        db.studyStateDao().upsert(StudyStateEntity(childId, TimeUtils.nowEpochMillis()))
        alarmScheduler.cancelReminder(childId)
    }

    suspend fun pauseStudy(childId: Long): PauseResult? {
        val state = db.studyStateDao().get(childId) ?: return null
        val startMillis = state.studyStartEpochMillis ?: return null
        val now = TimeUtils.nowEpochMillis()

        val beforeAchieved = getOrCreateTodayRecord(childId).achieved

        accumulateElapsed(childId, startMillis, now)
        db.studyStateDao().upsert(StudyStateEntity(childId, studyStartEpochMillis = null))

        val today = getOrCreateTodayRecord(childId)
        scheduleOrCancelReminder(childId, today)
        return PauseResult(today, achievedJustNow = today.achieved && !beforeAchieved)
    }

    /** 手動調整（原型アプリの「手動調整」相当）。studyStartEpochMillis はいじらない。 */
    suspend fun manualAdjustMinutes(childId: Long, deltaMinutes: Int) {
        val today = getOrCreateTodayRecord(childId)
        val newSeconds = (today.studiedSeconds + deltaMinutes * 60L).coerceAtLeast(0)
        val nowAchieved = today.goalMinutes > 0 && newSeconds >= today.goalMinutes * 60L
        val updated = today.copy(
            studiedSeconds = newSeconds,
            achieved = today.achieved || nowAchieved,
            achievedAtEpochMillis = if (!today.achieved && nowAchieved) TimeUtils.nowEpochMillis() else today.achievedAtEpochMillis,
        )
        db.dailyRecordDao().upsert(updated)
        scheduleOrCancelReminder(childId, updated)
    }

    /** 勉強中でなく未達成なら次のアラームを予約、達成済みなら予約中のアラームを消す。 */
    private suspend fun scheduleOrCancelReminder(childId: Long, record: DailyRecordEntity) {
        val studying = isStudying(childId)
        if (!studying && !record.achieved && record.goalMinutes > 0) {
            val child = db.childDao().getById(childId) ?: return
            val next = TimeUtils.nowEpochMillis() + child.alarmIntervalMinutes * 60_000L
            alarmScheduler.scheduleReminder(childId, next)
        } else {
            alarmScheduler.cancelReminder(childId)
        }
    }

    /**
     * [startMillis, endMillis) の経過時間を、日付をまたぐ場合は日ごとに分割して各日の記録へ加算する。
     * 通常は1日分だが、深夜0時をまたいだセッションや長期間の異常状態でも正しく積算されるようにする。
     */
    private suspend fun accumulateElapsed(childId: Long, startMillis: Long, endMillis: Long) {
        var cursor = startMillis
        var guard = 0
        while (cursor < endMillis && guard < 400) {
            guard++
            val cursorDate = TimeUtils.epochMillisToLocalDate(cursor)
            val nextMidnight = cursorDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segmentEnd = minOf(endMillis, nextMidnight)
            val seconds = (segmentEnd - cursor) / 1000
            if (seconds > 0) addSecondsToDate(childId, cursorDate, seconds)
            cursor = segmentEnd
        }
    }

    private suspend fun addSecondsToDate(childId: Long, date: LocalDate, seconds: Long) {
        val record = getOrCreateRecordForDate(childId, date)
        val newSeconds = record.studiedSeconds + seconds
        val nowAchieved = !record.achieved && record.goalMinutes > 0 && newSeconds >= record.goalMinutes * 60L
        db.dailyRecordDao().upsert(
            record.copy(
                studiedSeconds = newSeconds,
                achieved = record.achieved || nowAchieved,
                achievedAtEpochMillis = if (nowAchieved) TimeUtils.nowEpochMillis() else record.achievedAtEpochMillis,
            ),
        )
    }

    /**
     * 端末再起動直後の復旧処理。AlarmManager の予約は再起動で消えるため、勉強中でない・未達成の
     * 子どもについてリマインダーを再予約する。勉強中セッションの開始時刻はそのまま保持するので、
     * 経過時間の計算自体は再起動の影響を受けない。
     */
    suspend fun rescheduleAfterBoot() {
        for (child in getChildren()) {
            val today = getOrCreateTodayRecord(child.id)
            scheduleOrCancelReminder(child.id, today)
        }
    }

    /**
     * 深夜0時の切り替え処理。勉強中の子は「今日」分を確定させてセッションを0時起点で継続、
     * 勉強中でない子は今日の記録を先に作っておき、未達成ならアラームを予約する。
     */
    suspend fun rolloverAllChildren() {
        val now = TimeUtils.nowEpochMillis()
        for (child in getChildren()) {
            val state = db.studyStateDao().get(child.id)
            val startMillis = state?.studyStartEpochMillis
            if (startMillis != null) {
                accumulateElapsed(child.id, startMillis, now)
                db.studyStateDao().upsert(StudyStateEntity(child.id, studyStartEpochMillis = now))
                alarmScheduler.cancelReminder(child.id) // 勉強中なので鳴らさない
            } else {
                val today = getOrCreateTodayRecord(child.id)
                scheduleOrCancelReminder(child.id, today)
            }
        }
    }
}
