package com.familystudytimer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familystudytimer.app.data.StudyRepository
import kotlinx.coroutines.launch

private val DAY_LABELS = listOf("月", "火", "水", "木", "金", "土", "日") // DayOfWeek.value 1..7 に対応

@Composable
fun SettingsScreen(childId: Long, repository: StudyRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var alarmInterval by remember { mutableStateOf("15") }
    val goalMinutesByDay = remember { mutableStateOf(MutableList(7) { "" }) }

    LaunchedEffect(childId) {
        val children = repository.getChildren()
        children.firstOrNull { it.id == childId }?.let {
            name = it.name
            alarmInterval = it.alarmIntervalMinutes.toString()
        }
    }

    LaunchedEffect(childId) {
        repository.observeWeeklyGoals(childId).collect { list ->
            val updated = MutableList(7) { "" }
            for (g in list) {
                if (g.dayOfWeek in 1..7) updated[g.dayOfWeek - 1] = g.targetMinutes.toString()
            }
            goalMinutesByDay.value = updated
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("目標・設定") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名前") })

            OutlinedTextField(
                value = alarmInterval,
                onValueChange = { alarmInterval = it.filter { c -> c.isDigit() } },
                label = { Text("アラーム間隔A（分）") },
            )

            Text("曜日ごとの目標学習時間（分）", style = MaterialTheme.typography.titleMedium)
            for (i in 0 until 7) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(DAY_LABELS[i], modifier = Modifier.padding(end = 8.dp))
                    OutlinedTextField(
                        value = goalMinutesByDay.value[i],
                        onValueChange = { v ->
                            val filtered = v.filter { c -> c.isDigit() }
                            val updated = goalMinutesByDay.value.toMutableList()
                            updated[i] = filtered
                            goalMinutesByDay.value = updated
                        },
                        label = { Text("分") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Button(onClick = {
                scope.launch {
                    repository.renameChild(childId, name)
                    repository.setAlarmIntervalMinutes(childId, alarmInterval.toIntOrNull() ?: 15)
                    for (i in 0 until 7) {
                        val minutes = goalMinutesByDay.value[i].toIntOrNull() ?: 0
                        repository.setWeeklyGoal(childId, i + 1, minutes)
                    }
                    onBack()
                }
            }) {
                Text("保存する")
            }
        }
    }
}
