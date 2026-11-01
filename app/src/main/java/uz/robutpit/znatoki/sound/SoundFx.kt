package uz.robutpit.znatoki.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class Sfx { TICK, GONG, SPIN_UP, WARNING, CORRECT, WRONG, CLICK }

/** Звуковые эффекты (SoundPool), фоновая музыка и вибрация. Всё отключается в настройках. */
@Singleton
class SoundFx @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
    private val ids = mutableMapOf<Sfx, Int>()
    private var music: MediaPlayer? = null

    var soundEnabled = true
    var vibrationEnabled = true
    var musicEnabled = true
        set(value) { field = value; if (!value) stopMusic() }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    init {
        scope.launch {
            val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
            val gens = mapOf(
                Sfx.TICK to SynthAudio::tick, Sfx.GONG to SynthAudio::gong, Sfx.SPIN_UP to SynthAudio::spinUp,
                Sfx.WARNING to SynthAudio::warning, Sfx.CORRECT to SynthAudio::correct, Sfx.WRONG to SynthAudio::wrong, Sfx.CLICK to SynthAudio::click,
            )
            for ((k, g) in gens) {
                val f = File(dir, "${k.name.lowercase()}_v1.wav")
                if (!f.exists()) SynthAudio.writeWav(g(), f)
                ids[k] = pool.load(f.path, 1)
            }
            val m = File(dir, "ambient_v1.wav")
            if (!m.exists()) SynthAudio.writeWav(SynthAudio.ambientLoop(), m)
        }
    }

    fun play(s: Sfx, volume: Float = 1f, rate: Float = 1f) {
        if (!soundEnabled) return
        val id = ids[s] ?: return
        pool.play(id, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun vibrate(ms: Long = 18, amplitude: Int = 80) {
        if (!vibrationEnabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(ms, amplitude.coerceIn(1, 255)))
    }

    fun startMusic() {
        if (!musicEnabled || music != null) return
        val f = File(context.cacheDir, "sfx/ambient_v1.wav")
        if (!f.exists()) return
        music = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(f.path); isLooping = true; setVolume(0.18f, 0.18f); prepare(); start()
            }
        }.getOrNull()
    }

    fun stopMusic() { music?.runCatching { stop(); release() }; music = null }
}
