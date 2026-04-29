package com.example.pintxomatch.ui.common.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

internal fun triggerAchievementVibration(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return

    if (!vibrator.hasVibrator()) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0L, 45L, 35L, 70L),
                intArrayOf(0, 180, 0, 255),
                -1
            )
        )
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(longArrayOf(0L, 45L, 35L, 70L), -1)
    }
}
