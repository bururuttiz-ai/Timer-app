package com.familystudytimer.app.csv

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.familystudytimer.app.data.DailyRecordEntity
import java.io.File

object CsvExporter {

    /**
     * 学習記録をCSVファイルにして、共有シート（メール添付など）を開くIntentを返す。
     * ヘッダー: 日付,目標(分),学習時間(分),学習時間(秒),達成
     */
    fun buildShareIntent(context: Context, childName: String, records: List<DailyRecordEntity>): Intent {
        val csvDir = File(context.cacheDir, "csv").apply { mkdirs() }
        val file = File(csvDir, "${childName}_学習記録.csv")

        file.bufferedWriter().use { writer ->
            writer.write("日付,目標(分),学習時間(分),学習時間(秒),達成\n")
            for (r in records.sortedBy { it.date }) {
                val minutes = r.studiedSeconds / 60
                writer.write("${r.date},${r.goalMinutes},$minutes,${r.studiedSeconds},${if (r.achieved) "○" else "×"}\n")
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "${childName}さんの学習記録")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
