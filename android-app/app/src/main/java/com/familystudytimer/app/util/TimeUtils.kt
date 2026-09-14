package com.familystudytimer.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeUtils {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): LocalDate = LocalDate.now()

    fun todayKey(): String = today().format(dateFormatter)

    fun dateKey(date: LocalDate): String = date.format(dateFormatter)

    fun parseDateKey(key: String): LocalDate = LocalDate.parse(key, dateFormatter)

    /** 今日の次の深夜0時（ローカルタイムゾーン）の epoch millis を返す。 */
    fun nextMidnightEpochMillis(): Long {
        val tomorrow = today().plusDays(1).atStartOfDay()
        return tomorrow.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    fun nowEpochMillis(): Long = System.currentTimeMillis()

    fun epochMillisToLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}
