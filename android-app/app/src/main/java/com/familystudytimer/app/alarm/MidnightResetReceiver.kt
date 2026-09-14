package com.familystudytimer.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familystudytimer.app.StudyTimerApp
import com.familystudytimer.app.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 深夜0時に1日の区切りを処理し、翌日0時分のアラームを再予約する。 */
class MidnightResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as StudyTimerApp
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.rolloverAllChildren()
            } finally {
                app.alarmScheduler.scheduleMidnightRollover(TimeUtils.nextMidnightEpochMillis())
                pendingResult.finish()
            }
        }
    }
}
