package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.io.File

enum class SpeakerRole(val wireValue: String, val displayName: String) {
    PRODUCER("PRODUCER", "Producer (You)"),
    ENGINEER("ENGINEER", "Sound Engineer • 7x Grammy"),
    LYRICIST("LYRICIST", "AI Lyricist • Master Pen"),
    SYSTEM("SYSTEM", "Production Floor");

    companion object {
        fun fromWire(raw: String): SpeakerRole =
            entries.firstOrNull { it.wireValue == raw } ?: SYSTEM
    }
}

enum class GrooveStyle(val wireValue: String, val label: String) {
    TRAP_SOUL("trap_soul", "Trap Soul"),
    BOOM_BAP("boom_bap", "Boom Bap"),
    AFRO_FUSION("afro_fusion", "Afro Fusion"),
    SYNTH_WAVE("synth_wave", "Synth Wave"),
    NEO_SOUL("neo_soul", "Neo Soul");

    companion object {
        fun fromWire(raw: String): GrooveStyle =
            entries.firstOrNull { it.wireValue == raw } ?: TRAP_SOUL
    }
}

enum class TargetAgent(val label: String) {
    BOTH("Both Agents"),
    ENGINEER_ONLY("@Engineer"),
    LYRICIST_ONLY("@Lyricist")
}

enum class StemType(val fileKey: String, val label: String) {
    DRUMS("drums", "Drums Stem"),
    SUB_BASS("sub_bass", "Sub-Bass 808"),
    MELODY("melody", "Melody / Rhodes"),
    MASTER_MIX("master_mix", "Master Mix")
}

data class DspParams(
    val bpm: Int = 92,
    val musicalKey: String = "C minor",
    val grooveStyle: GrooveStyle = GrooveStyle.TRAP_SOUL,
    val hpfHz: Int = 32,
    val eqLowDb: Float = 3.0f,
    val eqMidHz: Int = 1200,
    val eqMidDb: Float = -1.5f,
    val eqHighDb: Float = 2.0f,
    val compThresholdDb: Float = -16.0f,
    val saturation: Float = 0.25f
)

data class RenderedStem(
    val stemType: StemType,
    val file: File,
    val bpm: Int,
    val musicalKey: String,
    val durationSec: Float
)

data class StemPackResult(
    val messageId: String,
    val stems: List<RenderedStem>
)

@Keep
@IgnoreExtraProperties
data class StudioSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Midnight Session",
    val bpm: Long = 92L,
    val musicalKey: String = "C minor",
    val grooveStyle: String = GrooveStyle.TRAP_SOUL.wireValue,
    val hpfHz: Long = 32L,
    val eqLowDb: Double = 3.0,
    val eqMidHz: Long = 1200L,
    val eqMidDb: Double = -1.5,
    val eqHighDb: Double = 2.0,
    val compThresholdDb: Double = -16.0,
    val saturation: Double = 0.25,
    val lastSpark: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toDspParams(): DspParams = DspParams(
        bpm = bpm.toInt().coerceIn(60, 180),
        musicalKey = musicalKey.ifBlank { "C minor" },
        grooveStyle = GrooveStyle.fromWire(grooveStyle),
        hpfHz = hpfHz.toInt().coerceIn(20, 220),
        eqLowDb = eqLowDb.toFloat().coerceIn(-12f, 12f),
        eqMidHz = eqMidHz.toInt().coerceIn(250, 5000),
        eqMidDb = eqMidDb.toFloat().coerceIn(-12f, 12f),
        eqHighDb = eqHighDb.toFloat().coerceIn(-12f, 12f),
        compThresholdDb = compThresholdDb.toFloat().coerceIn(-40f, 0f),
        saturation = saturation.toFloat().coerceIn(0f, 1f)
    )
}

@Keep
@IgnoreExtraProperties
data class StudioMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val sessionId: String = "",
    val speaker: String = SpeakerRole.PRODUCER.wireValue,
    val text: String = "",
    val sampleCredit: String = "",
    val stemPackIncluded: Boolean = false,
    val stemBpm: Long = 92L,
    val stemKey: String = "C minor",
    val stemGroove: String = GrooveStyle.TRAP_SOUL.wireValue,
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
