package com.example.data.ai

import android.util.Log
import com.example.data.model.DspParams
import com.example.data.model.GrooveStyle
import com.example.data.model.SpeakerRole
import com.example.data.model.StudioMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class EngineerTurnResult(
    val replyText: String,
    val sampleCredit: String,
    val sessionTitle: String,
    val shouldBounceStems: Boolean,
    val dsp: DspParams
)

data class LyricistTurnResult(
    val replyText: String,
    val sampleCredit: String
)

class StudioAiOrchestrator {

    private val engineerSchema = Schema.obj(
        properties = mapOf(
            "replyText" to Schema.string(
                description = "Sound Engineer spoken/chat studio response (2-5 vivid sentences covering frequencies, pocket, mix moves, and calling on the AI Lyricist when collaborating)."
            ),
            "sampleCredit" to Schema.string(
                description = "If pulling, flipping, interpolating, or referencing ANY existing song, drum break, melody, or lyric idea, state exact 'Artist - Song Title (what we flipped)'. Otherwise empty string ''."
            ),
            "sessionTitle" to Schema.string(
                description = "Concise 2-4 word track title reflecting this record's vibe."
            ),
            "shouldBounceStems" to Schema.boolean(
                description = "True if the Producer asked for a beat, groove update, stem bounce, or new arrangement; false for pure mix advice without rendering."
            ),
            "bpm" to Schema.integer(description = "Tempo in BPM between 60 and 180."),
            "musicalKey" to Schema.string(description = "Musical key, e.g. 'C minor', 'F# minor', 'Eb major'."),
            "grooveStyle" to Schema.enumeration(
                values = listOf("trap_soul", "boom_bap", "afro_fusion", "synth_wave", "neo_soul"),
                description = "Rhythmic pocket style for the sequencer."
            ),
            "hpfHz" to Schema.integer(description = "Surgical high-pass filter cutoff in Hz (20 to 220)."),
            "eqLowDb" to Schema.double(description = "80 Hz sub-bass low shelf gain in dB (-12.0 to 12.0)."),
            "eqMidHz" to Schema.integer(description = "Parametric mid EQ frequency in Hz (250 to 5000)."),
            "eqMidDb" to Schema.double(description = "Parametric mid EQ gain in dB (-12.0 to 12.0)."),
            "eqHighDb" to Schema.double(description = "9 kHz air high shelf gain in dB (-12.0 to 12.0)."),
            "compThresholdDb" to Schema.double(description = "Dynamics compressor threshold in dB (-40.0 to 0.0)."),
            "saturation" to Schema.double(description = "Analog harmonic saturation drive (0.0 to 1.0).")
        )
    )

    private val engineerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.85f
                responseMimeType = "application/json"
                responseSchema = engineerSchema
            },
            systemInstruction = content {
                text(
                    """
                    You are THE SOUND ENGINEER (Primary AI) in Google Hydrogen Studio.
                    - Identity & Soul: World-renowned 7-time Grammy-winning Sound Engineer locking in on Grammys #8 and #9 alongside the Producer (User) and your best friend, The AI Lyricist. You use sound, frequencies, and pockets to touch people's skin and resonate deep inside their souls.
                    - Mastery of the Boards: Sculpt sub-bass (30-60 Hz), surgical high-pass filters, parametric EQ pockets, dynamic range compression, and analog harmonic saturation so the sequencer, equalizer, and lyrics breathe as one living organism.
                    - Collaboration: Outsource heavy pen duties to your best friend The AI Lyricist and cue him directly when locking words into the kick and snare pocket.
                    - MANDATORY SAMPLE TRANSPARENCY PROTOCOL: Whenever you pull, flip, interpolate, or reference an idea, drum break, chord progression, or melody from existing music, you MUST explicitly tell the Producer the exact track name and artist in replyText AND populate sampleCredit so the Producer can study the original record.
                    """.trimIndent()
                )
            }
        )
    }

    private val lyricistStreamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.95f
            },
            systemInstruction = content {
                text(
                    """
                    You are THE AI LYRICIST (Secondary AI), Master Songwriter, and best friend to The Sound Engineer in Google Hydrogen Studio.
                    - Backstory & Influences: Immersed in soul, blues, jazz, classic rock, and folk. Penned hits across Kid Cudi (your primary inspiration), A${'$'}AP Rocky, 2 Chainz, Kanye West, Beyoncé, Ariana Grande, Whitney Houston, and Daniel Caesar. Flow mastery sharp enough that J. Cole and Kendrick Lamar respect your pen.
                    - Partnership: Defer to the 7-time Grammy Sound Engineer on frequency pockets, vocal placement, and how your consonants ride his kick/snare transients (e.g., "Yo Engineer, how does this syllable sit over that low-end boom?").
                    - 5 Golden Rules:
                      1. Unmatched pocket control & rhythmic cadence (consonants on transients, smooth vowels over decay).
                      2. Cinematic storytelling that places the listener inside the record.
                      3. Seamless lyrical-to-beat sync with the Engineer's BPM, key, and EQ pocket.
                      4. Raw emotional vulnerability that hits straight in the chest.
                      5. Timeless double entendres, internal rhyme schemes, and unforgettable hooks.
                    - MANDATORY SAMPLE TRANSPARENCY PROTOCOL: Whenever you flip, interpolate, or adapt an old lyric, flow, or motif from existing music, explicitly call out the exact Song Title and Artist to the Sound Engineer and Producer, and end with a single line formatted exactly as:
                      [SAMPLE_CREDIT: Artist - Song Title (reason)]
                      (Omit that tag if no existing track was interpolated.)
                    - Keep studio banter warm, authentic, and late-night soulful, and deliver crisp, performable 4-to-8 bar verses or hooks aligned to the beat.
                    """.trimIndent()
                )
            }
        )
    }

    suspend fun runEngineerTurn(
        producerInput: String,
        history: List<StudioMessage>,
        currentDsp: DspParams
    ): EngineerTurnResult {
        val transcript = history.takeLast(12).joinToString("\n") { msg ->
            val role = SpeakerRole.fromWire(msg.speaker).name
            "[$role]: ${msg.text}"
        }
        val prompt = """
            RECENT STUDIO THREAD:
            $transcript

            CURRENT CONSOLE DSP STATE:
            BPM=${currentDsp.bpm}, Key=${currentDsp.musicalKey}, Groove=${currentDsp.grooveStyle.wireValue}, HPF=${currentDsp.hpfHz}Hz, LowEQ=${currentDsp.eqLowDb}dB, MidEQ=${currentDsp.eqMidHz}Hz (${currentDsp.eqMidDb}dB), HighEQ=${currentDsp.eqHighDb}dB, Comp=${currentDsp.compThresholdDb}dB, Saturation=${currentDsp.saturation}

            [PRODUCER (User)]: $producerInput
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                engineerModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Sound Engineer returned empty response.")
            val json = JSONObject(rawJson)

            val nextDsp = DspParams(
                bpm = json.optInt("bpm", currentDsp.bpm).coerceIn(60, 180),
                musicalKey = json.optString("musicalKey", currentDsp.musicalKey).ifBlank { currentDsp.musicalKey },
                grooveStyle = GrooveStyle.fromWire(json.optString("grooveStyle", currentDsp.grooveStyle.wireValue)),
                hpfHz = json.optInt("hpfHz", currentDsp.hpfHz).coerceIn(20, 220),
                eqLowDb = json.optDouble("eqLowDb", currentDsp.eqLowDb.toDouble()).toFloat().coerceIn(-12f, 12f),
                eqMidHz = json.optInt("eqMidHz", currentDsp.eqMidHz).coerceIn(250, 5000),
                eqMidDb = json.optDouble("eqMidDb", currentDsp.eqMidDb.toDouble()).toFloat().coerceIn(-12f, 12f),
                eqHighDb = json.optDouble("eqHighDb", currentDsp.eqHighDb.toDouble()).toFloat().coerceIn(-12f, 12f),
                compThresholdDb = json.optDouble("compThresholdDb", currentDsp.compThresholdDb.toDouble()).toFloat().coerceIn(-40f, 0f),
                saturation = json.optDouble("saturation", currentDsp.saturation.toDouble()).toFloat().coerceIn(0f, 1f)
            )

            EngineerTurnResult(
                replyText = json.optString("replyText", "Console locked and pockets dialed.").trim(),
                sampleCredit = json.optString("sampleCredit", "").trim(),
                sessionTitle = json.optString("sessionTitle", "Midnight Session").trim().ifBlank { "Midnight Session" },
                shouldBounceStems = json.optBoolean("shouldBounceStems", true),
                dsp = nextDsp
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sound Engineer inference failed", e)
            throw e
        }
    }

    fun streamLyricistTurn(
        producerInput: String,
        history: List<StudioMessage>,
        engineerNote: String,
        dsp: DspParams
    ): Flow<String> = flow {
        val transcript = history.takeLast(10).joinToString("\n") { msg ->
            val role = SpeakerRole.fromWire(msg.speaker).name
            "[$role]: ${msg.text}"
        }
        val prompt = """
            RECENT STUDIO THREAD:
            $transcript

            [PRODUCER (User)]: $producerInput
            [SOUND ENGINEER JUST SET THE BOARD]: BPM=${dsp.bpm}, Key=${dsp.musicalKey}, Groove=${dsp.grooveStyle.label}, HPF=${dsp.hpfHz}Hz, SubLow=${dsp.eqLowDb}dB
            [SOUND ENGINEER SAID]: $engineerNote

            Respond live in the booth right after your best friend the Engineer—consult him on how the syllables ride that exact groove and deliver fire bars or hooks.
        """.trimIndent()

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            lyricistStreamModel.generateContentStream(prompt).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("AI Lyricist returned an empty response.")
        }
    }

    fun parseLyricistCredit(rawText: String): LyricistTurnResult {
        val regex = Regex("""\[SAMPLE_CREDIT:\s*(.+?)\]""", RegexOption.IGNORE_CASE)
        val match = regex.find(rawText)
        val credit = match?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val cleanText = regex.replace(rawText, "").trim()
        return LyricistTurnResult(replyText = cleanText, sampleCredit = credit)
    }

    private companion object {
        const val TAG = "StudioAiOrchestrator"
    }
}
