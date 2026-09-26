package com.campusmaps.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
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

// Where the spoken instructions will come out right now, for the S3 line under the connection pill:
// "glasses" (any Bluetooth output: A2DP, SCO or LE audio), "phone speaker", or "headphones".
// Read only; nothing is rerouted. Text to speech plays as media, so the media route is the one asked.
object SpeechOutput {
    fun label(context: Context): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "phone speaker"
        val type = if (Build.VERSION.SDK_INT >= 33) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            runCatching { am.getAudioDevicesForAttributes(attrs).firstOrNull()?.type }.getOrNull()
        } else {
            null
        } ?: am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.firstOrNull { isBluetooth(it) }
        return when {
            type == null -> "phone speaker"
            isBluetooth(type) -> "glasses"
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                type == AudioDeviceInfo.TYPE_USB_HEADSET -> "headphones"
            else -> "phone speaker"
        }
    }

    private fun isBluetooth(type: Int) = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        (Build.VERSION.SDK_INT >= 31 && (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
}
