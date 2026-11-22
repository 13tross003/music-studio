package com.example.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.RenderedStem
import com.example.data.model.SpeakerRole
import com.example.data.model.StudioMessage
import com.example.data.model.StudioSession
import com.example.data.model.TargetAgent
import com.example.data.model.UiState
import com.example.ui.theme.EngineerAmber
import com.example.ui.theme.EngineerAmberSurface
import com.example.ui.theme.LyricistViolet
import com.example.ui.theme.LyricistVioletSurface
import com.example.ui.theme.MeterGreen
import com.example.ui.theme.MeterPeakRed
import com.example.ui.theme.ProducerCyan
import com.example.ui.theme.ProducerCyanSurface
import com.example.ui.theme.SampleGold
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioConsoleBg
import com.example.ui.theme.StudioRackSurface
import com.example.ui.theme.StudioVoid

@Composable
fun StudioBoothTab(
    viewModel: StudioViewModel,
    activeSession: StudioSession?,
    hasMicPermission: Boolean,
    micPermissionDeniedPermanently: Boolean,
    onRequestMicPermission: ((() -> Unit)?) -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val targetAgent by viewModel.targetAgent.collectAsStateWithLifecycle()
    val isThinking by viewModel.isAgentThinking.collectAsStateWithLifecycle()
    val thinkingRole by viewModel.activeThinkingRole.collectAsStateWithLifecycle()
    val streamingLyricist by viewModel.streamingLyricistText.collectAsStateWithLifecycle()
    val stemsByMsg by viewModel.renderedStemsByMessage.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    val dspParams by viewModel.audioEngine.dspParams.collectAsStateWithLifecycle()
    val isLivePlaying by viewModel.audioEngine.isLivePlaying.collectAsStateWithLifecycle()
    val playingStemPath by viewModel.audioEngine.playingStemPath.collectAsStateWithLifecycle()
    val vuLevel by viewModel.audioEngine.vuLevel.collectAsStateWithLifecycle()

    val voiceBoothOn by viewModel.voiceBooth.voiceBoothEnabled.collectAsStateWithLifecycle()
    val isListening by viewModel.voiceBooth.isListening.collectAsStateWithLifecycle()
    val speakingAgent by viewModel.voiceBooth.activeSpeakingAgent.collectAsStateWithLifecycle()
    val micRms by viewModel.voiceBooth.micRmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.voiceBooth.partialTranscript.collectAsStateWithLifecycle()
    val voiceStatus by viewModel.voiceBooth.voiceStatusNote.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }

    val listState = rememberLazyListState()
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size, streamingLyricist) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioVoid)
    ) {
        // Console Header Strip with Live DSP & Voice Booth Controls
        StudioBoothHeader(
            sessionTitle = activeSession?.title ?: "Midnight Session",
            bpm = dspParams.bpm,
            musicalKey = dspParams.musicalKey,
            grooveLabel = dspParams.grooveStyle.label,
            voiceBoothOn = voiceBoothOn,
            speakingAgent = speakingAgent,
            isLivePlaying = isLivePlaying,
            vuLevel = maxOf(vuLevel, micRms),
            onToggleVoiceBooth = {
                if (!voiceBoothOn) {
                    onRequestMicPermission {
                        viewModel.voiceBooth.setVoiceBoothEnabled(true)
                    }
                } else {
                    viewModel.voiceBooth.setVoiceBoothEnabled(false)
                }
            },
            onToggleLiveGroove = { viewModel.audioEngine.toggleLiveGroove() },
            onInterrupt = {
                viewModel.voiceBooth.interruptMidStream(startMicAfter = false)
            }
        )

        AnimatedVisibility(visible = !hasMicPermission) {
            Surface(
                color = ProducerCyanSurface,
                border = BorderStroke(1.dp, ProducerCyan.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (micPermissionDeniedPermanently)
                            "Microphone permission is off. Open Settings to enable live voice collaboration."
                        else
                            "Allow microphone access to speak, hum melodies, and interrupt agents live.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Button(
                        onClick = {
                            if (micPermissionDeniedPermanently) {
                                onOpenAppSettings()
                            } else {
                                onRequestMicPermission {
                                    viewModel.voiceBooth.setVoiceBoothEnabled(true)
                                }
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("grant_mic_permission_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ProducerCyan,
                            contentColor = StudioVoid
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (micPermissionDeniedPermanently) "Settings" else "Enable Mic",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = statusBanner != null) {
            Surface(
                color = StudioCardElevated,
                border = BorderStroke(1.dp, EngineerAmber.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusBanner.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    AssistChip(
                        onClick = { viewModel.clearStatusBanner() },
                        label = { Text("Dismiss") }
                    )
                }
            }
        }

        // Unified 3-Participant Studio Chat Thread
        Box(modifier = Modifier.weight(1f)) {
            when (val state = messagesState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProducerCyan)
                    }
                }
                is UiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                is UiState.Success -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("studio_chat_list"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (messages.size <= 1) {
                            item(key = "spark_starters") {
                                SparkStarterSection(
                                    onSparkClick = { spark -> viewModel.sendSpark(spark) }
                                )
                            }
                        }

                        items(messages, key = { it.id }) { msg ->
                            LaunchedEffect(msg.id, msg.stemPackIncluded) {
                                if (msg.stemPackIncluded) {
                                    viewModel.ensureStemsRenderedForMessage(msg)
                                }
                            }
                            StudioMessageBubble(
                                message = msg,
                                stems = stemsByMsg[msg.id].orEmpty(),
                                playingStemPath = playingStemPath,
                                onPlayStem = { stem -> viewModel.audioEngine.playStem(stem.file) },
                                onShareStem = { stem ->
                                    viewModel.audioEngine.createShareStemIntent(stem.file)?.let { intent ->
                                        context.startActivity(
                                            android.content.Intent.createChooser(intent, "Export ${stem.stemType.label}")
                                        )
                                    }
                                },
                                onSaveStem = { stem -> viewModel.saveStemToMusic(stem) }
                            )
                        }

                        if (isThinking) {
                            item(key = "thinking_indicator") {
                                AgentThinkingCard(
                                    role = thinkingRole ?: SpeakerRole.ENGINEER,
                                    streamingText = streamingLyricist
                                )
                            }
                        }
                    }
                }
            }
        }

        // Voice Status + Target Agent Chips + Composer
        StudioComposerDock(
            inputText = inputText,
            onInputChange = { inputText = it },
            targetAgent = targetAgent,
            onTargetSelect = { viewModel.setTargetAgent(it) },
            isListening = isListening,
            isThinking = isThinking,
            partialTranscript = partialTranscript,
            voiceStatus = voiceStatus,
            onMicClick = {
                onRequestMicPermission {
                    if (isListening) {
                        viewModel.voiceBooth.stopListening()
                    } else {
                        viewModel.voiceBooth.startListening { spoken ->
                            viewModel.sendSpark(spoken)
                        }
                    }
                }
            },
            onSendClick = {
                val toSend = inputText
                inputText = ""
                viewModel.sendSpark(toSend)
            }
        )
    }
}

@Composable
private fun StudioBoothHeader(
    sessionTitle: String,
    bpm: Int,
    musicalKey: String,
    grooveLabel: String,
    voiceBoothOn: Boolean,
    speakingAgent: SpeakerRole?,
    isLivePlaying: Boolean,
    vuLevel: Float,
    onToggleVoiceBooth: () -> Unit,
    onToggleLiveGroove: () -> Unit,
    onInterrupt: () -> Unit
) {
    Surface(
        color = StudioConsoleBg,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sessionTitle,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$bpm BPM  •  $musicalKey  •  $grooveLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = EngineerAmber
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (speakingAgent != null || isLivePlaying) {
                        Surface(
                            onClick = onInterrupt,
                            shape = RoundedCornerShape(10.dp),
                            color = MeterPeakRed.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MeterPeakRed),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("interrupt_ai_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeOff,
                                    contentDescription = stringResource(R.string.cd_interrupt_ai),
                                    tint = MeterPeakRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Cut In",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MeterPeakRed
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onToggleLiveGroove,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("header_audition_button"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isLivePlaying) MeterGreen.copy(alpha = 0.2f) else StudioRackSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (isLivePlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = if (isLivePlaying) "Stop groove" else "Play groove",
                            tint = if (isLivePlaying) MeterGreen else ProducerCyan
                        )
                    }

                    IconButton(
                        onClick = onToggleVoiceBooth,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("voice_booth_toggle"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (voiceBoothOn) ProducerCyan.copy(alpha = 0.22f) else StudioRackSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (voiceBoothOn) Icons.Rounded.RecordVoiceOver else Icons.Rounded.Hearing,
                            contentDescription = if (voiceBoothOn) "Mute Voice Booth" else "Enable Voice Booth",
                            tint = if (voiceBoothOn) ProducerCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Signal Meter Bar
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            ) {
                drawRoundRect(
                    color = StudioRackSurface,
                    cornerRadius = CornerRadius(4f, 4f)
                )
                val fillW = size.width * vuLevel.coerceIn(0f, 1f)
                if (fillW > 1f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(MeterGreen, EngineerAmber, MeterPeakRed)),
                        size = Size(fillW, size.height),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SparkStarterSection(onSparkClick: (String) -> Unit) {
    val sparks = listOf(
        "92 BPM midnight Kid Cudi hum over a warm Rhodes and heavy 808 sub pocket",
        "Flip a dusty 70s soul drum break at 88 BPM with sharp consonants on the snare",
        "138 BPM stadium anthem — surgical HPF, booming sub-bass, and an unforgettable hook",
        "Raw late-night neo-soul confession in F# minor — let the vocals breathe in the mid pocket"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "DROP THE SPARK",
            style = MaterialTheme.typography.labelSmall,
            color = ProducerCyan
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sparks.forEachIndexed { idx, spark ->
                Surface(
                    onClick = { onSparkClick(spark) },
                    shape = RoundedCornerShape(14.dp),
                    color = StudioRackSurface,
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("spark_starter_$idx")
                ) {
                    Text(
                        text = spark,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioMessageBubble(
    message: StudioMessage,
    stems: List<RenderedStem>,
    playingStemPath: String?,
    onPlayStem: (RenderedStem) -> Unit,
    onShareStem: (RenderedStem) -> Unit,
    onSaveStem: (RenderedStem) -> Unit
) {
    val role = SpeakerRole.fromWire(message.speaker)
    val isProducer = role == SpeakerRole.PRODUCER

    val accent = when (role) {
        SpeakerRole.PRODUCER -> ProducerCyan
        SpeakerRole.ENGINEER -> EngineerAmber
        SpeakerRole.LYRICIST -> LyricistViolet
        SpeakerRole.SYSTEM -> MeterGreen
    }
    val surfaceBg = when (role) {
        SpeakerRole.PRODUCER -> ProducerCyanSurface
        SpeakerRole.ENGINEER -> EngineerAmberSurface.copy(alpha = 0.72f)
        SpeakerRole.LYRICIST -> LyricistVioletSurface.copy(alpha = 0.78f)
        SpeakerRole.SYSTEM -> StudioRackSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isProducer) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = surfaceBg,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isProducer) 16.dp else 4.dp,
                bottomEnd = if (isProducer) 4.dp else 16.dp
            ),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Speaker Header with Avatar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (role == SpeakerRole.ENGINEER || role == SpeakerRole.LYRICIST) {
                        Image(
                            painter = painterResource(
                                id = if (role == SpeakerRole.ENGINEER) R.drawable.img_engineer_avatar
                                else R.drawable.img_lyricist_avatar
                            ),
                            contentDescription = stringResource(
                                if (role == SpeakerRole.ENGINEER) R.string.cd_engineer_avatar
                                else R.string.cd_lyricist_avatar
                            ),
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        text = role.displayName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Mandatory Sample Transparency Protocol Callout
                if (message.sampleCredit.isNotBlank()) {
                    Surface(
                        color = SampleGold.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, SampleGold.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = null,
                                tint = SampleGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "SAMPLE TRANSPARENCY CREDIT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SampleGold
                                )
                                Text(
                                    text = message.sampleCredit,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Inline Multi-Stem WAV Bounce Card
                if (message.stemPackIncluded) {
                    InlineStemPackCard(
                        bpm = message.stemBpm.toInt(),
                        musicalKey = message.stemKey,
                        stems = stems,
                        playingStemPath = playingStemPath,
                        onPlayStem = onPlayStem,
                        onShareStem = onShareStem,
                        onSaveStem = onSaveStem
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineStemPackCard(
    bpm: Int,
    musicalKey: String,
    stems: List<RenderedStem>,
    playingStemPath: String?,
    onPlayStem: (RenderedStem) -> Unit,
    onShareStem: (RenderedStem) -> Unit,
    onSaveStem: (RenderedStem) -> Unit
) {
    Surface(
        color = StudioConsoleBg,
        border = BorderStroke(1.dp, StudioBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MeterGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "MULTI-STEM WAV BOUNCE • $bpm BPM • $musicalKey",
                    style = MaterialTheme.typography.labelSmall,
                    color = MeterGreen
                )
            }

            if (stems.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ProducerCyan)
                    Text(
                        text = "Rendering 44.1kHz stereo DSP stems...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                stems.forEach { stem ->
                    val isPlaying = playingStemPath == stem.file.absolutePath
                    Surface(
                        color = StudioRackSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isPlaying) MeterGreen else StudioBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onPlayStem(stem) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("play_stem_${stem.stemType.fileKey}")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop ${stem.stemType.label}" else "Play ${stem.stemType.label}",
                                    tint = if (isPlaying) MeterGreen else ProducerCyan
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stem.stemType.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${"%.1f".format(stem.durationSec)}s • 44.1kHz WAV",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onSaveStem(stem) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "Save ${stem.stemType.label} to Music",
                                    tint = EngineerAmber
                                )
                            }
                            IconButton(
                                onClick = { onShareStem(stem) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Share ${stem.stemType.label} WAV",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentThinkingCard(role: SpeakerRole, streamingText: String) {
    val accent = if (role == SpeakerRole.ENGINEER) EngineerAmber else LyricistViolet
    Surface(
        color = StudioRackSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = accent
                )
                Text(
                    text = if (role == SpeakerRole.ENGINEER)
                        "SOUND ENGINEER SCULPTING FREQUENCIES..."
                    else
                        "AI LYRICIST LOCKING INTO THE POCKET...",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
            }
            if (streamingText.isNotBlank()) {
                Text(
                    text = streamingText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun StudioComposerDock(
    inputText: String,
    onInputChange: (String) -> Unit,
    targetAgent: TargetAgent,
    onTargetSelect: (TargetAgent) -> Unit,
    isListening: Boolean,
    isThinking: Boolean,
    partialTranscript: String,
    voiceStatus: String,
    onMicClick: () -> Unit,
    onSendClick: () -> Unit
) {
    Surface(
        color = StudioConsoleBg,
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    TargetAgent.entries.forEach { target ->
                        val selected = target == targetAgent
                        FilterChip(
                            selected = selected,
                            onClick = { onTargetSelect(target) },
                            label = {
                                Text(
                                    text = target.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ProducerCyanSurface,
                                selectedLabelColor = ProducerCyan
                            )
                        )
                    }
                }
            }

            Text(
                text = if (partialTranscript.isNotBlank()) "\"$partialTranscript\"" else voiceStatus,
                style = MaterialTheme.typography.labelSmall,
                color = if (isListening) MeterGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onMicClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("booth_mic_button"),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isListening) MeterPeakRed.copy(alpha = 0.22f) else StudioRackSurface
                    )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        contentDescription = stringResource(R.string.cd_mic_capture),
                        tint = if (isListening) MeterPeakRed else ProducerCyan
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("studio_prompt_input"),
                    placeholder = {
                        Text(
                            text = "Drop a beat cue, sample flip, or lyric idea...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    maxLines = 3,
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank() && !isThinking) onSendClick()
                    }),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ProducerCyan,
                        unfocusedBorderColor = StudioBorder,
                        focusedContainerColor = StudioVoid,
                        unfocusedContainerColor = StudioVoid
                    )
                )

                Button(
                    onClick = onSendClick,
                    enabled = inputText.isNotBlank() && !isThinking,
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("send_spark_button"),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProducerCyan,
                        contentColor = StudioVoid
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.cd_send_spark)
                    )
                }
            }
        }
    }
}
