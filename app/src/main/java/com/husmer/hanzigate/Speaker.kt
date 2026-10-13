package com.husmer.hanzigate

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.mutableStateOf
import java.util.Locale

/** Reads Chinese aloud with the phone's text-to-speech, if a Chinese voice is installed. */
class Speaker(context: Context) {
    val ready = mutableStateOf(false)
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val t = tts
            ready.value = status == TextToSpeech.SUCCESS && t != null &&
                t.setLanguage(Locale.SIMPLIFIED_CHINESE) >= TextToSpeech.LANG_AVAILABLE
        }
    }

    fun say(text: String) {
        if (ready.value) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
