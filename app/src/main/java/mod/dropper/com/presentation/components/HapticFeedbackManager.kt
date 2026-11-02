package mod.dropper.com.presentation.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

enum class HapticStyle {
    /** Subtle mechanical tick for switches, filter chips, tabs, and dropdown items. */
    TICK,
    /** Crisp tactile click for standard buttons, cards, and action icons. */
    CLICK,
    /** Positive double-pulse confirmation for installs, saving target configs, and completions. */
    CONFIRM,
    /** Stronger tactile thud for destructive actions (delete, reset, disable all). */
    HEAVY
}

/**
 * Provides native Android View haptic feedback with automatic fallback to
 * micro-pulse vibration-haptic simulation when native haptics are unavailable,
 * unsupported by the hardware actuator, or muted in system touch settings.
 */
class DropperHaptics(
    private val context: Context?,
    private val view: View?,
    val enabled: Boolean = true
) : HapticFeedback {

    companion object {
        /**
         * Returns the simulated (timingsMs, amplitudes) waveform for devices that fall back
         * to vibration-haptic simulation. Amplitudes are in [0..255].
         */
        fun simulatedWaveformFor(style: HapticStyle): Pair<LongArray, IntArray> {
            return when (style) {
                HapticStyle.TICK -> longArrayOf(0L, 10L) to intArrayOf(0, 75)
                HapticStyle.CLICK -> longArrayOf(0L, 15L) to intArrayOf(0, 130)
                HapticStyle.CONFIRM -> longArrayOf(0L, 14L, 45L, 22L) to intArrayOf(0, 115, 0, 210)
                HapticStyle.HEAVY -> longArrayOf(0L, 26L, 35L, 18L) to intArrayOf(0, 230, 0, 140)
            }
        }
    }

    fun tick() = perform(HapticStyle.TICK)

    fun click() = perform(HapticStyle.CLICK)

    fun confirm() = perform(HapticStyle.CONFIRM)

    fun heavy() = perform(HapticStyle.HEAVY)

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        when (hapticFeedbackType) {
            HapticFeedbackType.LongPress -> heavy()
            HapticFeedbackType.TextHandleMove -> tick()
            else -> click()
        }
    }

    fun perform(style: HapticStyle) {
        if (!enabled) return

        val systemHapticEnabled = isSystemHapticFeedbackEnabled()
        val nativeConstant = mapToViewConstant(style)

        // Attempt native View haptic feedback first if system touch haptics are enabled
        val targetView = view
        val nativePerformed = if (systemHapticEnabled && targetView != null) {
            runCatching {
                @Suppress("DEPRECATION")
                targetView.performHapticFeedback(
                    nativeConstant,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            }.getOrDefault(false)
        } else {
            false
        }

        // Trigger vibration-haptic simulation if native haptics did not fire,
        // if system touch haptics are disabled, or if rich multi-pulse feedback is requested
        // on hardware without native primitive support.
        val needsSimulation = !nativePerformed ||
            !systemHapticEnabled ||
            ((style == HapticStyle.CONFIRM || style == HapticStyle.HEAVY) && !supportsRichPrimitives())

        if (needsSimulation) {
            performSimulatedHapticVibration(style)
        }
    }

    private fun mapToViewConstant(style: HapticStyle): Int {
        return when (style) {
            HapticStyle.TICK -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.TEXT_HANDLE_MOVE
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
            }
            HapticStyle.CLICK -> HapticFeedbackConstants.KEYBOARD_TAP
            HapticStyle.CONFIRM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.CONFIRM
                } else {
                    HapticFeedbackConstants.VIRTUAL_KEY
                }
            }
            HapticStyle.HEAVY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    HapticFeedbackConstants.REJECT
                } else {
                    HapticFeedbackConstants.LONG_PRESS
                }
            }
        }
    }

    private fun isSystemHapticFeedbackEnabled(): Boolean {
        val ctx = context ?: return false
        return runCatching {
            @Suppress("DEPRECATION")
            Settings.System.getInt(
                ctx.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1
            ) != 0
        }.getOrDefault(true)
    }

    private fun getVibrator(): Vibrator? {
        val ctx = context ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    private fun supportsRichPrimitives(): Boolean {
        val vib = getVibrator() ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return runCatching {
                vib.areAllPrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_TICK
                )
            }.getOrDefault(false)
        }
        return false
    }

    private fun performSimulatedHapticVibration(style: HapticStyle) {
        val vibrator = getVibrator() ?: return
        runCatching {
            if (!vibrator.hasVibrator()) return

            val (timings, amplitudes) = simulatedWaveformFor(style)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    // On non-amplitude ERM motors, use ultra-short micro-pulse timings
                    VibrationEffect.createWaveform(timings, -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        }
    }
}

val LocalDropperHaptics = staticCompositionLocalOf {
    DropperHaptics(context = null, view = null, enabled = true)
}

@Composable
fun rememberDropperHaptics(enabled: Boolean): DropperHaptics {
    val context = LocalContext.current
    val view = LocalView.current
    return remember(context, view, enabled) {
        DropperHaptics(
            context = context,
            view = view,
            enabled = enabled
        )
    }
}
