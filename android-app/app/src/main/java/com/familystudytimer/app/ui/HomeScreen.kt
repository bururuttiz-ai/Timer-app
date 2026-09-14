package com.familystudytimer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familystudytimer.app.util.SoundPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    children: List<ChildUiState>,
    onToggleStudy: (childId: Long) -> Unit,
    onManualAdjust: (childId: Long, deltaMinutes: Int) -> Unit,
    onOpenSettings: (childId: Long) -> Unit,
    onOpenHistory: (childId: Long) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("じしゅ勉タイマー") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(children, key = { it.child.id }) { state ->
                ChildCard(
                    state = state,
                    onToggleStudy = { onToggleStudy(state.child.id) },
                    onManualAdjust = { delta -> onManualAdjust(state.child.id, delta) },
                    onOpenSettings = { onOpenSettings(state.child.id) },
                    onOpenHistory = { onOpenHistory(state.child.id) },
                )
            }
        }
    }
}

@Composable
private fun ChildCard(
    state: ChildUiState,
    onToggleStudy: () -> Unit,
    onManualAdjust: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(state.child.name, style = MaterialTheme.typography.titleLarge)

            val studiedMinutes = state.displaySeconds / 60
            val goalMinutes = state.todayRecord.goalMinutes
            Text(
                if (goalMinutes > 0) "今日：${studiedMinutes}分 / 目標 ${goalMinutes}分" else "今日：${studiedMinutes}分（目標未設定）",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.todayRecord.achieved) {
                Text("目標達成しました！", color = MaterialTheme.colorScheme.primary)
            }

            Button(onClick = {
                if (state.isStudying) SoundPlayer.playFinishSound()
                onToggleStudy()
            }) {
                Text(if (state.isStudying) "勉強をやめる（記録する）" else "勉強をはじめる")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onManualAdjust(5) }) { Text("+5分") }
                OutlinedButton(onClick = { onManualAdjust(-5) }) { Text("-5分") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenSettings) { Text("目標・設定") }
                OutlinedButton(onClick = onOpenHistory) { Text("記録・CSV") }
            }
        }
    }
}
