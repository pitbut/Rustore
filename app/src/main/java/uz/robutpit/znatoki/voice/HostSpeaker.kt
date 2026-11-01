package uz.robutpit.znatoki.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Озвучка ведущего и ботов локально через TextToSpeech (бесплатно, на устройстве).
 * Реплики ведущего во время обсуждения откладываются — нельзя перебивать команду.
 */
@Singleton
class HostSpeaker @Inject constructor(@ApplicationContext context: Context) {
    private var ready = false
    private val pending = ArrayDeque<Pair<String, String>>()
    private val held = ArrayDeque<String>()
    var enabled = true
    var volume = 0.9f
    var basePitch = 1f
    var baseRate = 1f
    var language: String = "ru"

    /** Пока true — голос ведущего не звучит (идёт обсуждение). */
    var discussionActive = false
        set(value) {
            field = value
            if (!value) while (held.isNotEmpty()) speakNow(held.removeFirst(), "host")
        }

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            applyLocale()
            while (pending.isNotEmpty()) pending.removeFirst().let { speakNow(it.first, it.second) }
        }
    }

    private fun applyLocale() {
        val loc = if (language == "uz") Locale.forLanguageTag("uz-UZ") else Locale.forLanguageTag("ru-RU")
        val r = tts.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.forLanguageTag("ru-RU"))
    }

    fun setLang(lang: String) { language = lang; if (ready) applyLocale() }

    fun say(text: String, voiceStyle: String) {
        if (!enabled || text.isBlank()) return
        if (voiceStyle == "host" && discussionActive) { held.addLast(text); if (held.size > 3) held.removeFirst(); return }
        if (!ready) { pending.addLast(text to voiceStyle); return }
        speakNow(text, voiceStyle)
    }

    private fun speakNow(text: String, style: String) {
        val (pitch, rate) = when (style) {
            "host" -> 0.92f to 0.97f
            "bot1" -> 0.75f to 1.05f
            "bot2" -> 1.3f to 1.08f
            "bot3" -> 0.95f to 1.15f
            "bot4" -> 1.45f to 1.0f
            "bot5" -> 0.85f to 0.92f
            else -> 1f to 1f
        }
        tts.setPitch(pitch * basePitch)
        tts.setSpeechRate(rate * baseRate)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        tts.speak(text, TextToSpeech.QUEUE_ADD, params, "u" + System.nanoTime())
    }

    fun stop() { held.clear(); pending.clear(); if (ready) tts.stop() }
}
