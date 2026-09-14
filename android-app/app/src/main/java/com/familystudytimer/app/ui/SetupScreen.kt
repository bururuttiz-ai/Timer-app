package com.familystudytimer.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SetupScreen(onCreate: (String, String) -> Unit) {
    var name1 by remember { mutableStateOf("") }
    var name2 by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("さいしょに、なまえを入力してね", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name1,
                onValueChange = { name1 = it },
                label = { Text("1人目の名前") },
            )
            OutlinedTextField(
                value = name2,
                onValueChange = { name2 = it },
                label = { Text("2人目の名前") },
            )

            Button(onClick = { onCreate(name1, name2) }) {
                Text("はじめる")
            }
        }
    }
}
