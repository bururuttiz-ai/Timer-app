package com.familystudytimer.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familystudytimer.app.StudyTimerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 一定期間Aごとに鳴る「未達成リマインダー」。勉強ボタンが押されている間は
 * StudyRepository 側でこのアラームがキャンセルされているので、ここに届くのは
 * 「勉強していない・目標未達」の状態のときだけ（念のため受信時にも再確認する）。
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val childId = intent.getLongExtra(EXTRA_CHILD_ID, -1L)
        if (childId < 0) return

        val app = context.applicationContext as StudyTimerApp
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val studying = app.repository.isStudying(childId)
                val today = app.repository.getOrCreateTodayRecord(childId)
                if (!studying && !today.achieved && today.goalMinutes > 0) {
                    val child = app.repository.getChildren().firstOrNull { it.id == childId }
                    val remaining = ((today.goalMinutes * 60L - today.studiedSeconds) / 60).toInt().coerceAtLeast(1)
                    NotificationHelper.showReminder(context, childId, child?.name ?: "", remaining)
                    val next = System.currentTimeMillis() + (child?.alarmIntervalMinutes ?: 15) * 60_000L
                    app.alarmScheduler.scheduleReminder(childId, next)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
