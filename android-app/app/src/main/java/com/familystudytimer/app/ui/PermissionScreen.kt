package com.familystudytimer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 通知・正確なアラームの許可を、意味を説明したうえでお願いする画面。
 * これらを許可してもらえないと、アプリを閉じている間はアラームが鳴らない。
 */
@Composable
fun PermissionScreen(
    needsNotificationPermission: Boolean,
    needsExactAlarmPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("さいごに、2つだけお願いがあります", style = MaterialTheme.typography.titleLarge)
            Text(
                "このアプリは、アプリを閉じていても『勉強の時間だよ』とお知らせ（通知）を鳴らします。" +
                    "そのために、スマホの設定で以下を許可してください。最初に1回だけでOKです。",
            )

            if (needsNotificationPermission) {
                Text("① 通知を許可する")
                Button(onClick = onRequestNotificationPermission) {
                    Text("通知を許可する")
                }
            }

            if (needsExactAlarmPermission) {
                Text("② 正確なアラームを許可する（時刻ぴったりに鳴らすために必要）")
                Button(onClick = onRequestExactAlarmPermission) {
                    Text("正確なアラームを許可する")
                }
            }

            Button(onClick = onContinue) {
                Text(if (needsNotificationPermission || needsExactAlarmPermission) "あとで設定する / 次へ" else "次へ")
            }
        }
    }
}
