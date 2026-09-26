package com.campusmaps.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.media.MediaPlayer
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.campusmaps.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

enum class TtsStatus(val label: String) {
    STARTING("TTS: starting"),
    READY("TTS: ready"),
    UNAVAILABLE("TTS: unavailable"),
}

// Reads instructions out loud. With ELEVENLABS_API_KEY in local.properties it uses the ElevenLabs voice
// "Sarah" (soft, reassuring), for directions only; each sentence is cached on the phone after the first
// fetch, so repeats cost no network and no characters. No key, no network or a failed fetch: the phone's
// own text to speech engine, as before. Settings shows that engine's status ("TTS: ready"). Glasses mode
// watches isSpeaking so the camera and the audio never overlap; it goes true as soon as speak() is
// called, so a fetch counts as speaking too.
class Speaker(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cacheDir = File(context.applicationContext.cacheDir, "voice")
    private var player: MediaPlayer? = null
    private var generation = 0 // Bumped by speak() and stop(); a stale fetch never plays
    @Volatile private var currentUtterance: String? = null

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
                        if (utteranceId == currentUtterance) _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == currentUtterance) _isSpeaking.value = false
                    }

                    @Deprecated("Required override on older APIs")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == currentUtterance) _isSpeaking.value = false
                    }
                })
            } else {
                _status.value = TtsStatus.UNAVAILABLE
            }
        }
    }

    // Speaks right away, replacing anything that was being said.
    fun speak(text: String) {
        val gen = ++generation
        stopPlayback()
        if (API_KEY.isBlank()) {
            speakWithPhone(text)
            return
        }
        _isSpeaking.value = true
        scope.launch {
            val clip = withContext(Dispatchers.IO) { runCatching { clipFor(text) } }
                .onFailure { Log.w(TAG, "ElevenLabs failed, phone voice instead: ${it.message}") }
                .getOrNull()
            if (gen != generation) return@launch
            if (clip == null || !play(clip, gen)) speakWithPhone(text)
        }
    }

    fun stop() {
        generation++
        stopPlayback()
        _isSpeaking.value = false
    }

    private fun speakWithPhone(text: String) {
        if (_status.value != TtsStatus.READY) {
            _isSpeaking.value = false
            return
        }
        val id = UUID.randomUUID().toString()
        currentUtterance = id
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun stopPlayback() {
        currentUtterance = null
        tts?.stop()
        player?.release()
        player = null
    }

    // The cached clip for [text], fetched once. Throws on a network or API error.
    private fun clipFor(text: String): File {
        val key = MessageDigest.getInstance("SHA-1")
            .digest("$VOICE_ID|$MODEL|$VOICE_SETTINGS|$text".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = File(cacheDir, "$key.mp3")
        if (file.length() > 0) return file
        cacheDir.mkdirs()
        val conn = URL("https://api.elevenlabs.io/v1/text-to-speech/$VOICE_ID?output_format=mp3_44100_128")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 2_000
            conn.readTimeout = 3_000
            conn.doOutput = true
            conn.setRequestProperty("xi-api-key", API_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "audio/mpeg")
            val body = """{"text":${jsonString(text)},"model_id":"$MODEL","voice_settings":$VOICE_SETTINGS}"""
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode != 200) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("HTTP ${conn.responseCode} ${err.take(200)}")
            }
            val tmp = File(cacheDir, "$key.tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!tmp.renameTo(file)) error("cache write failed")
            return file
        } finally {
            conn.disconnect()
        }
    }

    // False if the clip could not be played (the caller then falls back to the phone voice).
    private fun play(clip: File, gen: Int): Boolean = runCatching {
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        mp.setDataSource(clip.path)
        mp.setOnCompletionListener {
            it.release()
            if (player === it) player = null
            if (gen == generation) _isSpeaking.value = false
        }
        mp.prepare()
        player = mp
        mp.start()
    }.onFailure {
        Log.w(TAG, "clip playback failed: ${it.message}")
        clip.delete()
    }.isSuccess

    private companion object {
        const val TAG = "Speaker"
        val API_KEY: String = BuildConfig.ELEVENLABS_API_KEY
        const val VOICE_ID = "EXAVITQu4vr4xnSDxMaL" // Sarah: warm, reassuring, American
        const val MODEL = "eleven_flash_v2_5" // Lowest latency, half the characters of the multilingual model
        const val VOICE_SETTINGS =
            """{"stability":0.65,"similarity_boost":0.75,"style":0,"use_speaker_boost":true,"speed":0.95}"""

        fun jsonString(s: String): String = buildString {
            append('"')
            for (c in s) when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
            append('"')
        }
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
