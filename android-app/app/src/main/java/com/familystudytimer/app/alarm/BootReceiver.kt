package com.familystudytimer.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familystudytimer.app.StudyTimerApp
import com.familystudytimer.app.util.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as StudyTimerApp
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.rescheduleAfterBoot()
            } finally {
                app.alarmScheduler.scheduleMidnightRollover(TimeUtils.nextMidnightEpochMillis())
                pendingResult.finish()
            }
        }
    }
}
