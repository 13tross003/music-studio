package com.example.ui.studio

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.StudioAudioEngine
import com.example.audio.VoiceBoothController
import com.example.data.ai.StudioAiOrchestrator
import com.example.data.model.DspParams
import com.example.data.model.GrooveStyle
import com.example.data.model.RenderedStem
import com.example.data.model.SpeakerRole
import com.example.data.model.StudioMessage
import com.example.data.model.StudioSession
import com.example.data.model.TargetAgent
import com.example.data.model.UiState
import com.example.data.repository.StudioRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudioViewModel(
    private val repository: StudioRepository,
    private val aiOrchestrator: StudioAiOrchestrator,
    val audioEngine: StudioAudioEngine,
    val voiceBooth: VoiceBoothController,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedSessionId = MutableStateFlow<String?>(null)
    val selectedSessionId: StateFlow<String?> = _selectedSessionId.asStateFlow()

    private val _targetAgent = MutableStateFlow(TargetAgent.BOTH)
    val targetAgent: StateFlow<TargetAgent> = _targetAgent.asStateFlow()

    private val _isAgentThinking = MutableStateFlow(false)
    val isAgentThinking: StateFlow<Boolean> = _isAgentThinking.asStateFlow()

    private val _activeThinkingRole = MutableStateFlow<SpeakerRole?>(null)
    val activeThinkingRole: StateFlow<SpeakerRole?> = _activeThinkingRole.asStateFlow()

    private val _streamingLyricistText = MutableStateFlow("")
    val streamingLyricistText: StateFlow<String> = _streamingLyricistText.asStateFlow()

    private val _renderedStemsByMessage = MutableStateFlow<Map<String, List<RenderedStem>>>(emptyMap())
    val renderedStemsByMessage: StateFlow<Map<String, List<RenderedStem>>> = _renderedStemsByMessage.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var dspSaveJob: Job? = null
    private var initialSessionBootstrapped = false

    val sessionsState: StateFlow<UiState<List<StudioSession>>> = repository.observeSessions(currentUserId)
        .map<List<StudioSession>, UiState<List<StudioSession>>> { sessions ->
            if (sessions.isNotEmpty() && _selectedSessionId.value == null) {
                val first = sessions.first()
                _selectedSessionId.value = first.id
                audioEngine.updateDsp(first.toDspParams(), resetSequencerIfGrooveChanged = true)
            } else if (sessions.isEmpty() && !initialSessionBootstrapped) {
                initialSessionBootstrapped = true
                bootstrapDefaultSession()
            }
            UiState.Success(sessions)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing studio sessions", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load studio sessions"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messagesState: StateFlow<UiState<List<StudioMessage>>> = _selectedSessionId
        .flatMapLatest { sessionId ->
            if (sessionId.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMessages(currentUserId, sessionId)
                    .map<List<StudioMessage>, UiState<List<StudioMessage>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error observing studio messages", rootError)
                        emit(UiState.Error(rootError.message ?: "Failed to load studio messages"))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private fun bootstrapDefaultSession() {
        viewModelScope.launch {
            val welcome = "Boards are warm, monitors are calibrated, and the sub-bass room check is shaking the glass. Seven Grammys sitting behind us—and my best friend is already pacing by the booth mic with a fresh notebook. Drop The Spark, Producer: give us a mood, BPM, story, or classic record to flip for #8 and #9."
            repository.createSession(
                title = "Midnight Session 01",
                dsp = DspParams(),
                welcomeText = welcome
            ).onSuccess { newId ->
                _selectedSessionId.value = newId
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to initialize session"
            }
        }
    }

    fun createNewSession(title: String = "New Studio Cut") {
        viewModelScope.launch {
            audioEngine.pauseLiveGroove()
            audioEngine.stopStem()
            val freshDsp = DspParams()
            audioEngine.updateDsp(freshDsp, resetSequencerIfGrooveChanged = true)
            val welcome = "Fresh console loaded and faders zeroed. Tell us the tempo, pocket, or sample reference you want to chase tonight."
            repository.createSession(
                title = title,
                dsp = freshDsp,
                welcomeText = welcome
            ).onSuccess { newId ->
                _selectedSessionId.value = newId
                _statusBanner.value = "Opened fresh studio session"
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Could not create session"
            }
        }
    }

    fun selectSession(session: StudioSession) {
        audioEngine.pauseLiveGroove()
        audioEngine.stopStem()
        _selectedSessionId.value = session.id
        audioEngine.updateDsp(session.toDspParams(), resetSequencerIfGrooveChanged = true)
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (_selectedSessionId.value == sessionId) {
                _selectedSessionId.value = null
            }
            repository.deleteSession(sessionId).onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to delete session"
            }
        }
    }

    fun setTargetAgent(agent: TargetAgent) {
        _targetAgent.value = agent
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun updateDspFromConsole(newDsp: DspParams, grooveChanged: Boolean = false) {
        audioEngine.updateDsp(newDsp, resetSequencerIfGrooveChanged = grooveChanged)
        val sessionId = _selectedSessionId.value ?: return
        dspSaveJob?.cancel()
        dspSaveJob = viewModelScope.launch {
            delay(350L)
            repository.updateSessionDsp(sessionId = sessionId, dsp = newDsp)
        }
    }

    fun applyDspPreset(preset: EngineerPreset) {
        val current = audioEngine.dspParams.value
        val grooveChanged = current.grooveStyle != preset.dsp.grooveStyle
        updateDspFromConsole(preset.dsp, grooveChanged = grooveChanged)
        _statusBanner.value = "Applied ${preset.name} (${preset.subtitle})"
    }

    fun sendSparkFromSuggestion(suggestion: StudioSuggestion) {
        when (suggestion.targetRole) {
            SpeakerRole.ENGINEER -> setTargetAgent(TargetAgent.ENGINEER_ONLY)
            SpeakerRole.LYRICIST -> setTargetAgent(TargetAgent.LYRICIST_ONLY)
            else -> setTargetAgent(TargetAgent.BOTH)
        }
        sendSpark(suggestion.prompt)
    }

    fun sendSpark(rawInput: String) {
        val text = rawInput.trim()
        if (text.isBlank() || _isAgentThinking.value) return
        val sessionId = _selectedSessionId.value ?: return

        voiceBooth.interruptMidStream(startMicAfter = false)

        viewModelScope.launch {
            _isAgentThinking.value = true
            _statusBanner.value = null
            _streamingLyricistText.value = ""

            val currentMessages = (messagesState.value as? UiState.Success)?.data.orEmpty()
            val currentDsp = audioEngine.dspParams.value
            val mode = _targetAgent.value

            val producerWrite = repository.addMessage(
                sessionId = sessionId,
                speaker = SpeakerRole.PRODUCER,
                text = text,
                stemBpm = currentDsp.bpm,
                stemKey = currentDsp.musicalKey,
                stemGroove = currentDsp.grooveStyle
            )
            if (producerWrite.isFailure) {
                _statusBanner.value = producerWrite.exceptionOrNull()?.localizedMessage ?: "Failed to save message"
                _isAgentThinking.value = false
                return@launch
            }

            val spokenQueue = mutableListOf<Pair<SpeakerRole, String>>()
            var latestDsp = currentDsp
            var engineerReplyForLyricist = ""

            try {
                if (mode == TargetAgent.BOTH || mode == TargetAgent.ENGINEER_ONLY) {
                    _activeThinkingRole.value = SpeakerRole.ENGINEER
                    val engResult = aiOrchestrator.runEngineerTurn(
                        producerInput = text,
                        history = currentMessages,
                        currentDsp = currentDsp
                    )
                    latestDsp = engResult.dsp
                    engineerReplyForLyricist = engResult.replyText

                    audioEngine.updateDsp(latestDsp, resetSequencerIfGrooveChanged = true)
                    repository.updateSessionDsp(
                        sessionId = sessionId,
                        dsp = latestDsp,
                        title = engResult.sessionTitle,
                        lastSpark = text
                    )

                    val engMsgResult = repository.addMessage(
                        sessionId = sessionId,
                        speaker = SpeakerRole.ENGINEER,
                        text = engResult.replyText,
                        sampleCredit = engResult.sampleCredit,
                        stemPackIncluded = engResult.shouldBounceStems,
                        stemBpm = latestDsp.bpm,
                        stemKey = latestDsp.musicalKey,
                        stemGroove = latestDsp.grooveStyle
                    )

                    val engMsgId = engMsgResult.getOrNull()
                    if (engResult.shouldBounceStems && engMsgId != null) {
                        val stems = audioEngine.renderMultiStemPack(tagId = engMsgId, params = latestDsp)
                        _renderedStemsByMessage.value = _renderedStemsByMessage.value + (engMsgId to stems)
                    }
                    spokenQueue += SpeakerRole.ENGINEER to engResult.replyText
                }

                if (mode == TargetAgent.BOTH || mode == TargetAgent.LYRICIST_ONLY) {
                    _activeThinkingRole.value = SpeakerRole.LYRICIST
                    val rawLyricistBuffer = StringBuilder()
                    aiOrchestrator.streamLyricistTurn(
                        producerInput = text,
                        history = currentMessages,
                        engineerNote = engineerReplyForLyricist,
                        dsp = latestDsp
                    ).collect { partial ->
                        rawLyricistBuffer.clear()
                        rawLyricistBuffer.append(partial)
                        _streamingLyricistText.value = partial
                    }

                    val parsed = aiOrchestrator.parseLyricistCredit(rawLyricistBuffer.toString())
                    _streamingLyricistText.value = ""
                    repository.addMessage(
                        sessionId = sessionId,
                        speaker = SpeakerRole.LYRICIST,
                        text = parsed.replyText,
                        sampleCredit = parsed.sampleCredit,
                        stemPackIncluded = false,
                        stemBpm = latestDsp.bpm,
                        stemKey = latestDsp.musicalKey,
                        stemGroove = latestDsp.grooveStyle
                    )
                    spokenQueue += SpeakerRole.LYRICIST to parsed.replyText
                }

                if (spokenQueue.isNotEmpty()) {
                    voiceBooth.speakTurn(spokenQueue)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI studio turn timed out", e)
                _statusBanner.value = "Studio AI timed out after 60s. Check connection and try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Studio AI error", e)
                _statusBanner.value = e.localizedMessage ?: "Studio AI error occurred."
            } finally {
                _streamingLyricistText.value = ""
                _activeThinkingRole.value = null
                _isAgentThinking.value = false
            }
        }
    }

    fun bounceStemsNow() {
        val sessionId = _selectedSessionId.value ?: return
        val dsp = audioEngine.dspParams.value
        viewModelScope.launch {
            try {
                val msgRes = repository.addMessage(
                    sessionId = sessionId,
                    speaker = SpeakerRole.SYSTEM,
                    text = "Bounced 4 DSP-mastered 44.1kHz stereo WAV stems (${dsp.bpm} BPM • ${dsp.musicalKey} • ${dsp.grooveStyle.label}).",
                    stemPackIncluded = true,
                    stemBpm = dsp.bpm,
                    stemKey = dsp.musicalKey,
                    stemGroove = dsp.grooveStyle
                )
                val msgId = msgRes.getOrNull() ?: return@launch
                val stems = audioEngine.renderMultiStemPack(tagId = msgId, params = dsp)
                _renderedStemsByMessage.value = _renderedStemsByMessage.value + (msgId to stems)
                _statusBanner.value = "4 WAV stems rendered into chat thread"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed bouncing stems", e)
                _statusBanner.value = e.localizedMessage ?: "Failed rendering stems"
            }
        }
    }

    fun ensureStemsRenderedForMessage(message: StudioMessage) {
        if (!message.stemPackIncluded) return
        if (_renderedStemsByMessage.value.containsKey(message.id)) return
        viewModelScope.launch {
            val baseDsp = audioEngine.dspParams.value.copy(
                bpm = message.stemBpm.toInt().coerceIn(60, 180),
                musicalKey = message.stemKey.ifBlank { "C minor" },
                grooveStyle = GrooveStyle.fromWire(message.stemGroove)
            )
            val stems = audioEngine.renderMultiStemPack(tagId = message.id, params = baseDsp)
            _renderedStemsByMessage.value = _renderedStemsByMessage.value + (message.id to stems)
        }
    }

    fun saveStemToMusic(stem: RenderedStem) {
        viewModelScope.launch {
            audioEngine.saveStemToMusicFolder(stem.file)
                .onSuccess { msg -> _statusBanner.value = msg }
                .onFailure { err -> _statusBanner.value = err.localizedMessage ?: "Export failed" }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.release()
        voiceBooth.release()
    }

    private companion object {
        const val TAG = "StudioViewModel"
    }
}
