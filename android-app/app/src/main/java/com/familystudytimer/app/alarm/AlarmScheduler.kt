package com.familystudytimer.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

const val EXTRA_CHILD_ID = "child_id"

/** ReminderAlarmReceiver 用のリクエストコード帯（子どもID分だけ予約）。MidnightResetReceiver とは重複しない値を使う。 */
private const val REMINDER_REQUEST_CODE_BASE = 1000
private const val MIDNIGHT_REQUEST_CODE = 9000

class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** この端末で「時刻ぴったり」にアラームを予約できるかどうか。Android 12+ は専用許可が必要。 */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

    fun scheduleReminder(childId: Long, triggerAtMillis: Long) {
        val pendingIntent = reminderPendingIntent(childId)
        setExactSafely(triggerAtMillis, pendingIntent)
    }

    fun cancelReminder(childId: Long) {
        alarmManager.cancel(reminderPendingIntent(childId))
    }

    fun scheduleMidnightRollover(triggerAtMillis: Long) {
        val intent = Intent(context, MidnightResetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, MIDNIGHT_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        setExactSafely(triggerAtMillis, pendingIntent)
    }

    private fun setExactSafely(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            // 正確なアラーム許可が無い場合はOS側の判断による遅延を許容してでも鳴らす
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun reminderPendingIntent(childId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_CHILD_ID, childId)
        }
        return PendingIntent.getBroadcast(
            context, REMINDER_REQUEST_CODE_BASE + childId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
