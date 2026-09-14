package com.familystudytimer.app

import android.app.Application
import com.familystudytimer.app.alarm.AlarmScheduler
import com.familystudytimer.app.alarm.NotificationHelper
import com.familystudytimer.app.data.AppDatabase
import com.familystudytimer.app.data.StudyRepository
import com.familystudytimer.app.util.TimeUtils

class StudyTimerApp : Application() {

    lateinit var repository: StudyRepository
        private set
    lateinit var alarmScheduler: AlarmScheduler
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.get(this)
        alarmScheduler = AlarmScheduler(this)
        repository = StudyRepository(db, alarmScheduler)
        NotificationHelper.createChannel(this)
        alarmScheduler.scheduleMidnightRollover(TimeUtils.nextMidnightEpochMillis())
    }
}
