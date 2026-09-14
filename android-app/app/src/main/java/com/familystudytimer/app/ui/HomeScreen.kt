package com.familystudytimer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familystudytimer.app.util.SoundPlayer
import com.familystudytimer.app.util.TimeUtils

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
        topBar = {
            TopAppBar(
                title = { Text("📚 じしゅ勉タイマー", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${if (state.isStudying) "✏️" else "🙂"} ${state.child.name}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            val goalMinutes = state.todayRecord.goalMinutes
            Text(
                TimeUtils.formatMinSec(state.displaySeconds),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
                color = if (state.isStudying) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            )
            Text(
                if (goalMinutes > 0) "今日の目標：${goalMinutes}分" else "今日の目標：未設定",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.todayRecord.achieved) {
                Text(
                    "🎉 目標達成！すごい！",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // メインボタン：勉強の開始/終了。他のボタンより大きく、色もはっきり分けて目立たせる
            Button(
                onClick = {
                    if (state.isStudying) SoundPlayer.playFinishSound()
                    onToggleStudy()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isStudying) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    contentColor = if (state.isStudying) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    if (state.isStudying) "⏸ 勉強をやめる" else "▶ 勉強をはじめる",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            // ここから下はあまり使わない操作。文字も小さめ・地味な色にして、メインボタンと視覚的に分離する
            Text(
                "手動調整",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onManualAdjust(5) }) { Text("＋5分") }
                TextButton(onClick = { onManualAdjust(-5) }) { Text("－5分") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenSettings) { Text("目標・設定") }
                TextButton(onClick = onOpenHistory) { Text("記録・CSV") }
            }
        }
    }
}
