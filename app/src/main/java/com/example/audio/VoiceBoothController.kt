package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.SpeakerRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt

class VoiceBoothController(
    private val appContext: Context,
    private val audioEngine: StudioAudioEngine
) {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _voiceBoothEnabled = MutableStateFlow(false)
    val voiceBoothEnabled: StateFlow<Boolean> = _voiceBoothEnabled.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _activeSpeakingAgent = MutableStateFlow<SpeakerRole?>(null)
    val activeSpeakingAgent: StateFlow<SpeakerRole?> = _activeSpeakingAgent.asStateFlow()

    private val _micRmsLevel = MutableStateFlow(0f)
    val micRmsLevel: StateFlow<Float> = _micRmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceStatusNote = MutableStateFlow("Booth ready • Tap mic to speak or toggle Voice Booth")
    val voiceStatusNote: StateFlow<String> = _voiceStatusNote.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var vadMonitorJob: Job? = null
    private var onFinalSpokenPrompt: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainScope.launch {
                            _activeSpeakingAgent.value = when {
                                utteranceId?.startsWith("ENGINEER") == true -> SpeakerRole.ENGINEER
                                utteranceId?.startsWith("LYRICIST") == true -> SpeakerRole.LYRICIST
                                else -> null
                            }
                            if (_voiceBoothEnabled.value) {
                                startBargeInMonitor()
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainScope.launch {
                            if (tts?.isSpeaking != true) {
                                _activeSpeakingAgent.value = null
                                stopBargeInMonitor()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainScope.launch {
                            _activeSpeakingAgent.value = null
                            stopBargeInMonitor()
                        }
                    }
                })
            }
        }
    }

    fun setVoiceBoothEnabled(enabled: Boolean) {
        _voiceBoothEnabled.value = enabled
        if (!enabled) {
            stopAllSpeechAndListening()
            _voiceStatusNote.value = "Voice Booth muted • Text studio active"
        } else {
            _voiceStatusNote.value = "Full-duplex Voice Booth active • Dual AI voices ready"
        }
    }

    fun speakTurn(lines: List<Pair<SpeakerRole, String>>) {
        if (!_voiceBoothEnabled.value || !ttsReady || lines.isEmpty()) return
        tts?.stop()
        lines.forEachIndexed { idx, (role, rawText) ->
            val cleanText = rawText.replace(Regex("[*_#`]+"), " ").take(900)
            if (cleanText.isBlank()) return@forEachIndexed
            if (role == SpeakerRole.ENGINEER) {
                tts?.setPitch(0.84f)
                tts?.setSpeechRate(0.96f)
            } else {
                tts?.setPitch(1.08f)
                tts?.setSpeechRate(1.04f)
            }
            val queueMode = if (idx == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(cleanText, queueMode, null, "${role.wireValue}_$idx")
        }
    }

    fun interruptMidStream(startMicAfter: Boolean = false, onTranscript: ((String) -> Unit)? = null) {
        tts?.stop()
        _activeSpeakingAgent.value = null
        stopBargeInMonitor()
        audioEngine.pauseLiveGroove()
        audioEngine.stopStem()
        _voiceStatusNote.value = "Producer cut in — booth listening..."
        if (startMicAfter && onTranscript != null) {
            startListening(onTranscript)
        }
    }

    fun startListening(onTranscript: (String) -> Unit) {
        // Barge-in immediately if AI is currently talking
        tts?.stop()
        _activeSpeakingAgent.value = null
        stopBargeInMonitor()
        onFinalSpokenPrompt = onTranscript

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _voiceStatusNote.value = "Speech recognizer unavailable on this device — type your spark below."
            return
        }

        mainScope.launch {
            runCatching {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _partialTranscript.value = ""
                        _voiceStatusNote.value = "Listening in booth... speak, hum, or direct"
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        val norm = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _micRmsLevel.value = norm
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _micRmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _micRmsLevel.value = 0f
                        _voiceStatusNote.value = "Mic ready • Tap mic to speak again"
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _micRmsLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull()?.trim().orEmpty()
                        _partialTranscript.value = ""
                        if (spoken.isNotBlank()) {
                            _voiceStatusNote.value = "Captured: \"$spoken\""
                            onFinalSpokenPrompt?.invoke(spoken)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull().orEmpty()
                        if (partial.isNotBlank()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                }
                recognizer.startListening(intent)
            }.onFailure { e ->
                Log.e(TAG, "SpeechRecognizer start failed", e)
                _isListening.value = false
                _voiceStatusNote.value = "Could not start mic: ${e.localizedMessage}"
            }
        }
    }

    fun stopListening() {
        _isListening.value = false
        _micRmsLevel.value = 0f
        _partialTranscript.value = ""
        runCatching { speechRecognizer?.stopListening() }
    }

    @SuppressLint("MissingPermission")
    private fun startBargeInMonitor() {
        if (vadMonitorJob?.isActive == true || _isListening.value) return
        vadMonitorJob = ioScope.launch {
            val sr = 16000
            val bufSize = AudioRecord.getMinBufferSize(
                sr,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1024)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sr,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    return@launch
                }
                recorder.startRecording()
                val buf = ShortArray(512)
                var hotFrames = 0
                while (isActive && _activeSpeakingAgent.value != null) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            val norm = buf[i] / 32768.0
                            sum += norm * norm
                        }
                        val rms = sqrt(sum / read).toFloat()
                        _micRmsLevel.value = (rms * 5f).coerceIn(0f, 1f)
                        if (rms > 0.14f) {
                            hotFrames++
                            if (hotFrames >= 3) {
                                mainScope.launch {
                                    interruptMidStream(startMicAfter = false)
                                }
                                break
                            }
                        } else {
                            hotFrames = 0
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Barge-in VAD monitor unavailable: ${e.message}")
            } finally {
                _micRmsLevel.value = 0f
                runCatching {
                    recorder?.stop()
                    recorder?.release()
                }
            }
        }
    }

    private fun stopBargeInMonitor() {
        vadMonitorJob?.cancel()
        vadMonitorJob = null
    }

    fun stopAllSpeechAndListening() {
        tts?.stop()
        _activeSpeakingAgent.value = null
        stopBargeInMonitor()
        stopListening()
    }

    fun release() {
        stopAllSpeechAndListening()
        runCatching {
            speechRecognizer?.destroy()
            tts?.shutdown()
        }
    }

    private companion object {
        const val TAG = "VoiceBoothController"
    }
}
