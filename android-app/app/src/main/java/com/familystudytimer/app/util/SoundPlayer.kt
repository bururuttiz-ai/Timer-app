package com.familystudytimer.app.util

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** 勉強ボタンを止めたときに鳴らす「おわり」の合図音。 */
object SoundPlayer {
    fun playFinishSound() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            Handler(Looper.getMainLooper()).postDelayed({ toneGenerator.release() }, 600)
        } catch (_: RuntimeException) {
            // 端末によってはトーン生成に失敗することがあるが、致命的ではないので無視する
        }
    }
}
