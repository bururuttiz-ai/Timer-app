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
import com.familystudytimer.app.ui.theme.StudyTimerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 反応がなくても鳴らし続けないための上限時間。 */
private const val RING_TIMEOUT_MILLIS = 60_000L

/**
 * 目覚まし時計のような「全画面アラーム」画面。画面ロック中でも表示され、
 * 音とバイブレーションを、ボタンが押されるか[RING_TIMEOUT_MILLIS]経つまでループさせる。
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

        // 反応がないまま鳴らし続けないよう、一定時間で自動的に止める（未達成なら次の間隔③でまた鳴る）
        lifecycleScope.launch {
            delay(RING_TIMEOUT_MILLIS)
            acknowledge(childId)
        }

        setContent {
            StudyTimerTheme {
                AlarmRingScreen(
                    childName = childName,
                    onAcknowledge = { acknowledge(childId) },
                )
            }
        }
    }

    /**
     * 「わかったよ」ボタン。アラーム音を止めるだけで、勉強を自動で開始はしない
     * （その場で勉強できない事情もあり得るため）。未達成のままなら次の間隔でまた鳴る。
     */
    private fun acknowledge(childId: Long) {
        stopAlarmSound()
        stopVibration()
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
private fun AlarmRingScreen(childName: String, onAcknowledge: () -> Unit) {
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
                onClick = onAcknowledge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    "わかったよ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
