package com.familystudytimer.app.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.familystudytimer.app.StudyTimerApp
import com.familystudytimer.app.ui.theme.StudyTimerTheme
import kotlinx.coroutines.launch

/**
 * 目覚まし時計のような「全画面アラーム」画面。画面ロック中でも表示され、
 * 音とバイブレーションをボタンが押されるまでループさせる。
 */
class AlarmRingActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        (getSystemService(KeyguardManager::class.java))?.requestDismissKeyguard(this, null)

        val childId = intent.getLongExtra(EXTRA_CHILD_ID, -1L)
        val childName = intent.getStringExtra(EXTRA_CHILD_NAME) ?: ""

        startAlarmSound()
        startVibration()

        setContent {
            StudyTimerTheme {
                AlarmRingScreen(
                    childName = childName,
                    onStopAndStudy = { stopAndStartStudy(childId) },
                )
            }
        }
    }

    private fun stopAndStartStudy(childId: Long) {
        stopAlarmSound()
        stopVibration()
        val app = application as StudyTimerApp
        if (childId >= 0) {
            lifecycleScope.launch { app.repository.startStudy(childId) }
        }
        getSystemService(NotificationManager::class.java)?.cancel(childId.toInt())
        finish()
    }

    override fun onDestroy() {
        stopAlarmSound()
        stopVibration()
        super.onDestroy()
    }

    private fun startAlarmSound() {
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmRingActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            // 端末によっては再生に失敗することがあるが、画面自体は表示され続けるので致命的ではない
        }
    }

    private fun stopAlarmSound() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    private fun startVibration() {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 500, 500)
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
        vibrator = v
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }
}

@Composable
private fun AlarmRingScreen(childName: String, onStopAndStudy: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("⏰", fontSize = 72.sp)
            Text(
                if (childName.isNotBlank()) "$childName さん\n勉強の時間だよ！" else "勉強の時間だよ！",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(
                onClick = onStopAndStudy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    "▶ 勉強をはじめる",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
