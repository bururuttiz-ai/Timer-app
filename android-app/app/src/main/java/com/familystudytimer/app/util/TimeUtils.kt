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

    /** 0時からの分数（例: 930）を "HH:mm" 表示（例: "15:30"）に変換する。 */
    fun minutesToHHmm(minutesFromMidnight: Int): String {
        val h = (minutesFromMidnight / 60).coerceIn(0, 23)
        val m = (minutesFromMidnight % 60).coerceIn(0, 59)
        return "%02d:%02d".format(h, m)
    }

    /** "HH:mm" 表示を0時からの分数に変換する。パースできなければ null。 */
    fun hhmmToMinutes(text: String): Int? {
        val parts = text.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}
