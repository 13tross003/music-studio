package com.example.ui.console

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DspParams
import com.example.data.model.GrooveStyle
import com.example.ui.studio.StudioViewModel
import com.example.ui.theme.EngineerAmber
import com.example.ui.theme.LyricistViolet
import com.example.ui.theme.MeterGreen
import com.example.ui.theme.MeterPeakRed
import com.example.ui.theme.ProducerCyan
import com.example.ui.theme.ProducerCyanSurface
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioConsoleBg
import com.example.ui.theme.StudioRackSurface
import com.example.ui.theme.StudioVoid
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ConsoleFloorTab(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val dsp by viewModel.audioEngine.dspParams.collectAsStateWithLifecycle()
    val isLivePlaying by viewModel.audioEngine.isLivePlaying.collectAsStateWithLifecycle()
    val activeStep by viewModel.audioEngine.activeStep.collectAsStateWithLifecycle()
    val vuLevel by viewModel.audioEngine.vuLevel.collectAsStateWithLifecycle()
    val kickSteps by viewModel.audioEngine.kickSteps.collectAsStateWithLifecycle()
    val snareSteps by viewModel.audioEngine.snareSteps.collectAsStateWithLifecycle()
    val hatSteps by viewModel.audioEngine.hatSteps.collectAsStateWithLifecycle()
    val subSteps by viewModel.audioEngine.subSteps.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioVoid)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Holographic EQ & Live Output Visualizer
        Surface(
            color = StudioConsoleBg,
            border = BorderStroke(1.dp, StudioBorder),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SOUND ENGINEER DSP CONSOLE",
                            style = MaterialTheme.typography.labelSmall,
                            color = EngineerAmber
                        )
                        Text(
                            text = "${dsp.bpm} BPM  •  ${dsp.musicalKey}  •  ${dsp.grooveStyle.label}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (isLivePlaying) "LIVE AAudio" else "STANDBY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isLivePlaying) MeterGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DspCurveCanvas(dsp = dsp, vuLevel = vuLevel, isPlaying = isLivePlaying, activeStep = activeStep)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.audioEngine.toggleLiveGroove() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("console_audition_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLivePlaying) MeterPeakRed else MeterGreen,
                            contentColor = StudioVoid
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isLivePlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isLivePlaying) "Stop Groove" else "Audition Groove",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.bounceStemsNow() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("bounce_stems_button"),
                        border = BorderStroke(1.dp, ProducerCyan),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = ProducerCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("Bounce 4 WAVs", color = ProducerCyan)
                    }
                }
            }
        }

        // Groove Style & Musical Key Selector
        Surface(
            color = StudioRackSurface,
            border = BorderStroke(1.dp, StudioBorder),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("GROOVE POCKET & KEY", style = MaterialTheme.typography.labelSmall, color = ProducerCyan)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GrooveStyle.entries.forEach { style ->
                        FilterChip(
                            selected = dsp.grooveStyle == style,
                            onClick = {
                                viewModel.updateDspFromConsole(dsp.copy(grooveStyle = style), grooveChanged = true)
                            },
                            label = { Text(style.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ProducerCyanSurface,
                                selectedLabelColor = ProducerCyan
                            )
                        )
                    }
                }
                val keys = listOf("C minor", "F# minor", "Eb major", "G minor", "Bb minor", "A minor")
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    keys.forEach { k ->
                        FilterChip(
                            selected = dsp.musicalKey.equals(k, ignoreCase = true),
                            onClick = { viewModel.updateDspFromConsole(dsp.copy(musicalKey = k)) },
                            label = { Text(k) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EngineerAmber.copy(alpha = 0.2f),
                                selectedLabelColor = EngineerAmber
                            )
                        )
                    }
                }
            }
        }

        // 16-Step Sequencer Matrix
        Surface(
            color = StudioRackSurface,
            border = BorderStroke(1.dp, StudioBorder),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("16-STEP POCKET SEQUENCER", style = MaterialTheme.typography.labelSmall, color = EngineerAmber)
                SequencerRow("KICK", kickSteps, activeStep, isLivePlaying, EngineerAmber) { step ->
                    viewModel.audioEngine.toggleSequencerPad(0, step)
                }
                SequencerRow("SNARE", snareSteps, activeStep, isLivePlaying, ProducerCyan) { step ->
                    viewModel.audioEngine.toggleSequencerPad(1, step)
                }
                SequencerRow("HAT", hatSteps, activeStep, isLivePlaying, MeterGreen) { step ->
                    viewModel.audioEngine.toggleSequencerPad(2, step)
                }
                SequencerRow("SUB", subSteps, activeStep, isLivePlaying, LyricistViolet) { step ->
                    viewModel.audioEngine.toggleSequencerPad(3, step)
                }
            }
        }

        // Surgical DSP Chain Faders
        Surface(
            color = StudioRackSurface,
            border = BorderStroke(1.dp, StudioBorder),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("SURGICAL EQ, HPF & SATURATION CHAIN", style = MaterialTheme.typography.labelSmall, color = LyricistViolet)

                DspFader(
                    label = "Tempo (BPM)",
                    valueText = "${dsp.bpm} BPM",
                    value = dsp.bpm.toFloat(),
                    range = 60f..180f,
                    accent = ProducerCyan,
                    onValueChange = { viewModel.updateDspFromConsole(dsp.copy(bpm = it.toInt())) }
                )
                DspFader(
                    label = "Surgical High-Pass Filter",
                    valueText = "${dsp.hpfHz} Hz",
                    value = dsp.hpfHz.toFloat(),
                    range = 20f..220f,
                    accent = EngineerAmber,
                    onValueChange = { viewModel.updateDspFromConsole(dsp.copy(hpfHz = it.toInt())) }
                )
                DspFader(
                    label = "Sub-Bass Low Shelf (80 Hz)",
                    valueText = "${"%+.1f".format(dsp.eqLowDb)} dB",
                    value = dsp.eqLowDb,
                    range = -12f..12f,
                    accent = EngineerAmber,
                    onValueChange = { viewModel.updateDspFromConsole(dsp.copy(eqLowDb = it)) }
                )
                DspFader(
                    label = "Parametric Mid (${dsp.eqMidHz} Hz)",
                    valueText = "${"%+.1f".format(dsp.eqMidDb)} dB",
                    value = dsp.eqMidDb,
                    range = -12f..12f,
                    accent = ProducerCyan,
                    onValueChange = { viewModel.updateDspFromConsole(dsp.copy(eqMidDb = it)) }
                )
                DspFader(
                    label = "Air High Shelf (9 kHz)",
                    valueText = "${"%+.1f".format(dsp.eqHighDb)} dB",
                    value = dsp.eqHighDb,
                    range = -12f..12f,
                    accent = ProducerCyan,
                    onValueChange = { viewModel.updateDspFromConsole(dsp.copy(eqHighDb = it)) }
                )
                DspFader(
                    label = "Compressor Threshold (4:1)",
                    valueText = "${"%.0f".format(dsp.compThresholdDb)} dB",
                    value = dsp.compThresholdDb,
                    range = -40f..0f,
                    accent = LyricistViolet,
                    onValueChange = { viewModel.updateDspFromConsole(dsp.copy(compThresholdDb = it)) }
                )
                DspFader(
                    label = "Analog Harmonic Saturation",
                    valueText = "${"%.0f".format(dsp.saturation * 100)}%",
                    value = dsp.saturation,
                    range = 0f..1f,
                    accent = MeterPeakRed,
                    onValueChange = { viewModel.updateDspFromConsole(dsp.copy(saturation = it)) }
                )
            }
        }
    }
}

@Composable
private fun DspCurveCanvas(dsp: DspParams, vuLevel: Float, isPlaying: Boolean, activeStep: Int) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(StudioVoid, RoundedCornerShape(12.dp))
    ) {
        val midY = size.height * 0.55f
        val path = Path()
        val points = 64
        for (i in 0..points) {
            val frac = i / points.toFloat()
            val x = frac * size.width
            // Approximate visual EQ frequency response + HPF roll-off + harmonic wave
            val hpfCut = ((dsp.hpfHz - 20f) / 200f) * 0.22f
            val hpfDrop = if (frac < hpfCut) (hpfCut - frac) * 140f else 0f
            val lowBump = dsp.eqLowDb * 1.8f * kotlin.math.exp(-((frac - 0.14f) / 0.12f).let { it * it })
            val midBump = dsp.eqMidDb * 1.8f * kotlin.math.exp(-((frac - 0.48f) / 0.15f).let { it * it })
            val highBump = dsp.eqHighDb * 1.8f * kotlin.math.exp(-((frac - 0.86f) / 0.15f).let { it * it })
            val pulse = if (isPlaying) {
                sin((frac * 6.0 * PI) + activeStep * 0.8).toFloat() * vuLevel * 22f
            } else 0f
            val y = (midY + hpfDrop - lowBump - midBump - highBump + pulse).coerceIn(8f, size.height - 8f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            brush = Brush.horizontalGradient(listOf(EngineerAmber, ProducerCyan, LyricistViolet)),
            style = Stroke(width = 5f)
        )

        // Subtle VU bar strip along bottom
        val barCount = 24
        val barW = size.width / (barCount * 1.4f)
        val gap = size.width / barCount
        for (b in 0 until barCount) {
            val threshold = b / barCount.toFloat()
            val lit = vuLevel >= threshold * 0.85f
            val color = when {
                !lit -> StudioRackSurface
                threshold > 0.8f -> MeterPeakRed
                threshold > 0.55f -> EngineerAmber
                else -> MeterGreen
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(b * gap + 4f, size.height - 14f),
                size = Size(barW, 8f),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
    }
}

@Composable
private fun SequencerRow(
    label: String,
    steps: BooleanArray,
    activeStep: Int,
    isPlaying: Boolean,
    accent: Color,
    onToggle: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = accent)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (s in 0 until 16) {
                val active = steps.getOrElse(s) { false }
                val isCurrent = isPlaying && activeStep == s
                Surface(
                    onClick = { onToggle(s) },
                    modifier = Modifier
                        .size(width = 38.dp, height = 48.dp)
                        .minimumInteractiveComponentSize(),
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isCurrent && active -> accent
                        active -> accent.copy(alpha = 0.35f)
                        isCurrent -> StudioBorder
                        else -> StudioConsoleBg
                    },
                    border = BorderStroke(
                        width = if (isCurrent) 2.dp else 1.dp,
                        color = if (isCurrent) Color.White else if (active) accent else StudioBorder
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${s + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent && active) StudioVoid else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DspFader(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = valueText, style = MaterialTheme.typography.labelLarge, color = accent)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = StudioConsoleBg
            )
        )
    }
}
