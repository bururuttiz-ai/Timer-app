package com.familystudytimer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.familystudytimer.app.data.StudyRepository
import com.familystudytimer.app.util.TimeUtils
import kotlinx.coroutines.launch

private val DAY_LABELS = listOf("月", "火", "水", "木", "金", "土", "日") // DayOfWeek.value 1..7 に対応
private const val MINUTES_PER_DAY = 24 * 60
private const val STEP_MINUTES = 15

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(childId: Long, repository: StudyRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var alarmInterval by remember { mutableStateOf("15") }
    val startTimeByDay = remember { mutableStateOf(MutableList(7) { 0 }) }
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
            val updatedStart = MutableList(7) { 0 }
            val updatedGoal = MutableList(7) { "" }
            for (g in list) {
                if (g.dayOfWeek in 1..7) {
                    updatedStart[g.dayOfWeek - 1] = g.startTimeMinutes
                    updatedGoal[g.dayOfWeek - 1] = g.targetMinutes.toString()
                }
            }
            startTimeByDay.value = updatedStart
            goalMinutesByDay.value = updatedGoal
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("目標・設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名前") })

            OutlinedTextField(
                value = alarmInterval,
                onValueChange = { alarmInterval = it.filter { c -> c.isDigit() } },
                label = { Text("③ アラーム間隔（分）") },
            )

            Text("曜日ごとの設定：① 開始時刻 ／ ② 目標時間（分）", style = MaterialTheme.typography.titleMedium)
            Text(
                "①の時刻になっても勉強ボタンが押されていなければアラームが鳴り、以降は③の間隔で繰り返します。②の合計時間に達すると、その日はもう鳴りません。",
                style = MaterialTheme.typography.bodySmall,
            )
            for (i in 0 until 7) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(DAY_LABELS[i], modifier = Modifier.width(20.dp))
                    Text("①", modifier = Modifier.padding(end = 2.dp))
                    TimeStepper(
                        minutes = startTimeByDay.value[i],
                        onChange = { newMinutes ->
                            val updated = startTimeByDay.value.toMutableList()
                            updated[i] = newMinutes
                            startTimeByDay.value = updated
                        },
                    )
                    OutlinedTextField(
                        value = goalMinutesByDay.value[i],
                        onValueChange = { v ->
                            val filtered = v.filter { c -> c.isDigit() }
                            val updated = goalMinutesByDay.value.toMutableList()
                            updated[i] = filtered
                            goalMinutesByDay.value = updated
                        },
                        label = { Text("② 分") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Button(onClick = {
                scope.launch {
                    repository.renameChild(childId, name)
                    repository.setAlarmIntervalMinutes(childId, alarmInterval.toIntOrNull() ?: 15)
                    for (i in 0 until 7) {
                        val goalMinutes = goalMinutesByDay.value[i].toIntOrNull() ?: 0
                        repository.setWeeklyGoal(childId, i + 1, startTimeByDay.value[i], goalMinutes)
                    }
                    repository.refreshTodaySettings(childId)
                    onBack()
                }
            }) {
                Text("保存する")
            }
        }
    }
}

/** ①開始時刻の入力用。手打ちさせず、15分刻みで±ボタンにより24時間をループする。 */
@Composable
private fun TimeStepper(minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((minutes - STEP_MINUTES + MINUTES_PER_DAY) % MINUTES_PER_DAY) }) {
            Text("－")
        }
        Text(
            TimeUtils.minutesToHHmm(minutes),
            modifier = Modifier.width(52.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        IconButton(onClick = { onChange((minutes + STEP_MINUTES) % MINUTES_PER_DAY) }) {
            Text("＋")
        }
    }
}
