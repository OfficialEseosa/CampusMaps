package com.campusmaps.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class TtsStatus(val label: String) {
    STARTING("TTS: starting"),
    READY("TTS: ready"),
    UNAVAILABLE("TTS: unavailable"),
}

// Reads instructions out loud with the phone's text to speech engine.
// Settings shows its status ("TTS: ready"). Glasses mode watches isSpeaking so the
// camera and the audio never overlap.
class Speaker(context: Context) {

    private val _status = MutableStateFlow(TtsStatus.STARTING)
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { result ->
            val engine = tts
            if (result == TextToSpeech.SUCCESS && engine != null) {
                val language = engine.setLanguage(Locale.US)
                _status.value = if (language >= TextToSpeech.LANG_AVAILABLE) TtsStatus.READY else TtsStatus.UNAVAILABLE
                engine.setSpeechRate(0.95f) // Calm, not rushed
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Required override on older APIs")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
            } else {
                _status.value = TtsStatus.UNAVAILABLE
            }
        }
    }

    // Speaks right away, replacing anything that was being said.
    fun speak(text: String) {
        if (_status.value != TtsStatus.READY) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }
}
