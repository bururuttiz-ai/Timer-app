package com.familystudytimer.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.familystudytimer.app.csv.CsvExporter
import com.familystudytimer.app.data.ChildEntity
import com.familystudytimer.app.data.DailyRecordEntity
import com.familystudytimer.app.data.StudyRepository

@Composable
fun HistoryScreen(childId: Long, repository: StudyRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    var child by remember { mutableStateOf<ChildEntity?>(null) }
    var records by remember { mutableStateOf<List<DailyRecordEntity>>(emptyList()) }

    LaunchedEffect(childId) {
        child = repository.getChildren().firstOrNull { it.id == childId }
        repository.observeHistory(childId).collect { records = it }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("記録・CSV") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = {
                val name = child?.name ?: "child"
                val intent = CsvExporter.buildShareIntent(context, name, records)
                context.startActivity(Intent.createChooser(intent, "学習記録を送る"))
            }) {
                Text("CSVを送る（メールなど）")
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records, key = { it.date }) { record ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(record.date)
                            val minutes = record.studiedSeconds / 60
                            Text("学習: ${minutes}分 / 目標: ${record.goalMinutes}分  ${if (record.achieved) "○達成" else "×未達成"}")
                        }
                    }
                }
            }
        }
    }
}
