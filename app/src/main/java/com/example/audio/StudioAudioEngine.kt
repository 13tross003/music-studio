package com.example.audio

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.DspParams
import com.example.data.model.GrooveStyle
import com.example.data.model.RenderedStem
import com.example.data.model.StemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

class StudioAudioEngine(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _dspParams = MutableStateFlow(DspParams())
    val dspParams: StateFlow<DspParams> = _dspParams.asStateFlow()

    private val _isLivePlaying = MutableStateFlow(false)
    val isLivePlaying: StateFlow<Boolean> = _isLivePlaying.asStateFlow()

    private val _activeStep = MutableStateFlow(0)
    val activeStep: StateFlow<Int> = _activeStep.asStateFlow()

    private val _vuLevel = MutableStateFlow(0f)
    val vuLevel: StateFlow<Float> = _vuLevel.asStateFlow()

    private val _playingStemPath = MutableStateFlow<String?>(null)
    val playingStemPath: StateFlow<String?> = _playingStemPath.asStateFlow()

    // Interactive 16-step sequencer patterns (user can toggle pads live!)
    private val _kickSteps = MutableStateFlow(defaultKickPattern(GrooveStyle.TRAP_SOUL))
    val kickSteps: StateFlow<BooleanArray> = _kickSteps.asStateFlow()

    private val _snareSteps = MutableStateFlow(defaultSnarePattern(GrooveStyle.TRAP_SOUL))
    val snareSteps: StateFlow<BooleanArray> = _snareSteps.asStateFlow()

    private val _hatSteps = MutableStateFlow(defaultHatPattern(GrooveStyle.TRAP_SOUL))
    val hatSteps: StateFlow<BooleanArray> = _hatSteps.asStateFlow()

    private val _subSteps = MutableStateFlow(defaultSubPattern(GrooveStyle.TRAP_SOUL))
    val subSteps: StateFlow<BooleanArray> = _subSteps.asStateFlow()

    private var liveJob: Job? = null
    private var mediaPlayer: MediaPlayer? = null

    fun updateDsp(newParams: DspParams, resetSequencerIfGrooveChanged: Boolean = false) {
        val oldGroove = _dspParams.value.grooveStyle
        _dspParams.value = newParams
        if (resetSequencerIfGrooveChanged && oldGroove != newParams.grooveStyle) {
            applyGroovePreset(newParams.grooveStyle)
        }
    }

    fun applyGroovePreset(groove: GrooveStyle) {
        _kickSteps.value = defaultKickPattern(groove)
        _snareSteps.value = defaultSnarePattern(groove)
        _hatSteps.value = defaultHatPattern(groove)
        _subSteps.value = defaultSubPattern(groove)
    }

    fun toggleSequencerPad(trackIndex: Int, step: Int) {
        if (step !in 0 until 16) return
        when (trackIndex) {
            0 -> _kickSteps.value = _kickSteps.value.copyOf().also { it[step] = !it[step] }
            1 -> _snareSteps.value = _snareSteps.value.copyOf().also { it[step] = !it[step] }
            2 -> _hatSteps.value = _hatSteps.value.copyOf().also { it[step] = !it[step] }
            3 -> _subSteps.value = _subSteps.value.copyOf().also { it[step] = !it[step] }
        }
    }

    fun toggleLiveGroove() {
        if (_isLivePlaying.value) {
            pauseLiveGroove()
        } else {
            startLiveGroove()
        }
    }

    fun startLiveGroove() {
        if (liveJob?.isActive == true) return
        stopStem()
        _isLivePlaying.value = true
        liveJob = scope.launch {
            val sampleRate = SAMPLE_RATE
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val trackBuilder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            val audioTrack = trackBuilder.build()
            val dspChain = StereoDspChain(sampleRate)
            val voices = SynthVoices(sampleRate)
            val blockSamples = 512
            val pcmShort = ShortArray(blockSamples * 2)
            var sampleCounter = 0L
            var lastStep = -1

            try {
                audioTrack.play()
                while (isActive && _isLivePlaying.value) {
                    val params = _dspParams.value
                    dspChain.configure(params)

                    val samplesPerStep = ((sampleRate * 60.0) / params.bpm / 4.0).toInt().coerceAtLeast(1000)
                    val kicks = _kickSteps.value
                    val snares = _snareSteps.value
                    val hats = _hatSteps.value
                    val subs = _subSteps.value
                    val rootHz = keyToBassFreq(params.musicalKey)

                    var sumSq = 0.0
                    for (i in 0 until blockSamples) {
                        val step = ((sampleCounter / samplesPerStep) % 16).toInt()
                        if (step != lastStep) {
                            lastStep = step
                            _activeStep.value = step
                            if (kicks[step]) voices.triggerKick()
                            if (snares[step]) voices.triggerSnare(params.grooveStyle)
                            if (hats[step]) voices.triggerHat(step % 4 == 3)
                            if (subs[step]) voices.triggerSub(rootHz * bassIntervalRatio(step, params.grooveStyle))
                            if (step == 0 || step == 8) voices.triggerChord(rootHz * 2.0, step == 8, params.grooveStyle)
                        }

                        val (rawL, rawR) = voices.nextStereoSample()
                        val (procL, procR) = dspChain.processSample(rawL, rawR, params)

                        sumSq += procL * procL + procR * procR
                        pcmShort[i * 2] = (procL.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        pcmShort[i * 2 + 1] = (procR.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                        sampleCounter++
                    }

                    _vuLevel.value = sqrt(sumSq / (blockSamples * 2.0)).toFloat().coerceIn(0f, 1f)
                    audioTrack.write(pcmShort, 0, pcmShort.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Live AudioTrack error", e)
            } finally {
                _vuLevel.value = 0f
                _isLivePlaying.value = false
                runCatching {
                    audioTrack.pause()
                    audioTrack.flush()
                    audioTrack.release()
                }
            }
        }
    }

    fun pauseLiveGroove() {
        _isLivePlaying.value = false
        liveJob?.cancel()
        liveJob = null
        _vuLevel.value = 0f
    }

    suspend fun renderMultiStemPack(
        tagId: String,
        params: DspParams = _dspParams.value
    ): List<RenderedStem> = withContext(Dispatchers.IO) {
        val stemsDir = File(appContext.filesDir, "stems").apply { mkdirs() }
        val bars = 2
        val totalSteps = bars * 16
        val samplesPerStep = ((SAMPLE_RATE * 60.0) / params.bpm / 4.0).toInt()
        val totalFrames = totalSteps * samplesPerStep
        val durationSec = totalFrames.toFloat() / SAMPLE_RATE

        val kicks = _kickSteps.value
        val snares = _snareSteps.value
        val hats = _hatSteps.value
        val subs = _subSteps.value
        val rootHz = keyToBassFreq(params.musicalKey)

        StemType.entries.map { stemType ->
            val file = File(stemsDir, "${stemType.fileKey}_${params.bpm}bpm_${tagId.take(8)}.wav")
            val pcmBytes = renderStemPcm(
                stemType = stemType,
                params = params,
                totalFrames = totalFrames,
                samplesPerStep = samplesPerStep,
                kicks = kicks,
                snares = snares,
                hats = hats,
                subs = subs,
                rootHz = rootHz
            )
            writeWavFile(file, pcmBytes, SAMPLE_RATE, CHANNELS)
            RenderedStem(
                stemType = stemType,
                file = file,
                bpm = params.bpm,
                musicalKey = params.musicalKey,
                durationSec = durationSec
            )
        }
    }

    fun playStem(file: File) {
        if (!file.exists()) return
        if (_playingStemPath.value == file.absolutePath) {
            stopStem()
            return
        }
        pauseLiveGroove()
        stopStem()
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _playingStemPath.value = null
                    runCatching { it.release() }
                    mediaPlayer = null
                }
                prepare()
                start()
            }
            mediaPlayer = mp
            _playingStemPath.value = file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed playing stem ${file.name}", e)
            _playingStemPath.value = null
        }
    }

    fun stopStem() {
        mediaPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        mediaPlayer = null
        _playingStemPath.value = null
    }

    fun createShareStemIntent(file: File): Intent? {
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun saveStemToMusicFolder(file: File): Result<String> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext Result.failure(IllegalStateException("Stem file missing"))
        return@withContext try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Hydrogen Studio")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val resolver = appContext.contentResolver
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Could not create MediaStore entry")
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { inp -> inp.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("Saved to Music/Hydrogen Studio/${file.name}")
            } else {
                Result.success("Ready at ${file.name} (use Share to export)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving stem to Music library", e)
            Result.failure(e)
        }
    }

    fun release() {
        pauseLiveGroove()
        stopStem()
    }

    private fun renderStemPcm(
        stemType: StemType,
        params: DspParams,
        totalFrames: Int,
        samplesPerStep: Int,
        kicks: BooleanArray,
        snares: BooleanArray,
        hats: BooleanArray,
        subs: BooleanArray,
        rootHz: Double
    ): ByteArray {
        val dspChain = StereoDspChain(SAMPLE_RATE).apply { configure(params) }
        val voices = SynthVoices(SAMPLE_RATE)
        val out = ByteArrayOutputStream(totalFrames * 4)
        val frameBuf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

        var lastStep = -1
        for (frame in 0 until totalFrames) {
            val rawStep = frame / samplesPerStep
            val step = rawStep % 16
            if (rawStep != lastStep) {
                lastStep = rawStep
                if (stemType == StemType.DRUMS || stemType == StemType.MASTER_MIX) {
                    if (kicks[step]) voices.triggerKick()
                    if (snares[step]) voices.triggerSnare(params.grooveStyle)
                    if (hats[step]) voices.triggerHat(step % 4 == 3)
                }
                if (stemType == StemType.SUB_BASS || stemType == StemType.MASTER_MIX) {
                    if (subs[step]) voices.triggerSub(rootHz * bassIntervalRatio(step, params.grooveStyle))
                }
                if (stemType == StemType.MELODY || stemType == StemType.MASTER_MIX) {
                    if (step == 0 || step == 8) {
                        voices.triggerChord(rootHz * 2.0, step == 8, params.grooveStyle)
                    }
                }
            }

            val (rawL, rawR) = voices.nextStereoSample()
            val (procL, procR) = dspChain.processSample(rawL, rawR, params)
            val sL = (procL.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            val sR = (procR.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            frameBuf.clear()
            frameBuf.putShort(sL)
            frameBuf.putShort(sR)
            out.write(frameBuf.array())
        }
        return out.toByteArray()
    }

    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcmData.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16) // PCM chunk size
            putShort(1) // PCM format
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort((channels * 2).toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmData.size)
        }
        FileOutputStream(file).use { fos ->
            fos.write(header.array())
            fos.write(pcmData)
        }
    }

    private companion object {
        const val TAG = "StudioAudioEngine"
        const val SAMPLE_RATE = 44100
        const val CHANNELS = 2

        fun defaultKickPattern(g: GrooveStyle): BooleanArray = when (g) {
            GrooveStyle.BOOM_BAP -> booleanArrayOf(true,false,false,false, false,false,false,true, false,false,true,false, false,false,false,false)
            GrooveStyle.AFRO_FUSION -> booleanArrayOf(true,false,false,true, false,false,true,false, true,false,false,true, false,false,true,false)
            GrooveStyle.SYNTH_WAVE -> booleanArrayOf(true,false,false,false, true,false,false,false, true,false,false,false, true,false,false,false)
            GrooveStyle.NEO_SOUL -> booleanArrayOf(true,false,false,false, false,false,true,false, false,true,false,false, false,false,false,false)
            GrooveStyle.TRAP_SOUL -> booleanArrayOf(true,false,false,true, false,false,true,false, false,false,true,false, false,false,false,false)
        }

        fun defaultSnarePattern(g: GrooveStyle): BooleanArray = when (g) {
            GrooveStyle.TRAP_SOUL -> booleanArrayOf(false,false,false,false, false,false,false,false, true,false,false,false, false,false,false,false)
            else -> booleanArrayOf(false,false,false,false, true,false,false,false, false,false,false,false, true,false,false,false)
        }

        fun defaultHatPattern(g: GrooveStyle): BooleanArray = when (g) {
            GrooveStyle.TRAP_SOUL -> booleanArrayOf(true,false,true,true, true,false,true,true, true,false,true,true, true,true,true,true)
            GrooveStyle.SYNTH_WAVE -> booleanArrayOf(false,false,true,false, false,false,true,false, false,false,true,false, false,false,true,false)
            else -> booleanArrayOf(true,false,true,false, true,false,true,false, true,false,true,false, true,false,true,true)
        }

        fun defaultSubPattern(g: GrooveStyle): BooleanArray = when (g) {
            GrooveStyle.SYNTH_WAVE -> booleanArrayOf(true,false,true,false, true,false,true,false, true,false,true,false, true,false,true,false)
            else -> booleanArrayOf(true,false,false,false, false,false,true,false, false,false,true,false, false,false,false,false)
        }

        fun keyToBassFreq(key: String): Double {
            val lower = key.lowercase()
            return when {
                lower.startsWith("c#") || lower.startsWith("db") -> 69.30
                lower.startsWith("d#") || lower.startsWith("eb") -> 77.78
                lower.startsWith("d") -> 73.42
                lower.startsWith("e") -> 41.20
                lower.startsWith("f#") || lower.startsWith("gb") -> 46.25
                lower.startsWith("f") -> 43.65
                lower.startsWith("g#") || lower.startsWith("ab") -> 51.91
                lower.startsWith("g") -> 49.00
                lower.startsWith("a#") || lower.startsWith("bb") -> 58.27
                lower.startsWith("a") -> 55.00
                lower.startsWith("b") -> 61.74
                else -> 65.41 // C2
            }
        }

        fun bassIntervalRatio(step: Int, groove: GrooveStyle): Double = when {
            step >= 10 -> if (groove == GrooveStyle.BOOM_BAP) 0.74915 else 0.8909 // 5th or b7
            step >= 6 -> 0.7937 // minor 6th / b6 vibe
            else -> 1.0
        }
    }
}

private class SynthVoices(private val sr: Int) {
    private var kickEnv = 0.0
    private var kickPhase = 0.0

    private var snareEnv = 0.0
    private var snareTonePhase = 0.0

    private var hatEnv = 0.0

    private var subEnv = 0.0
    private var subPhase = 0.0
    private var subFreq = 65.41

    private var chordEnv = 0.0
    private var chordRoot = 130.81
    private val chordPhases = DoubleArray(4)

    private val rng = Random(42)

    fun triggerKick() {
        kickEnv = 1.0
        kickPhase = 0.0
    }

    fun triggerSnare(groove: GrooveStyle) {
        snareEnv = if (groove == GrooveStyle.TRAP_SOUL) 0.75 else 0.9
        snareTonePhase = 0.0
    }

    fun triggerHat(openHat: Boolean) {
        hatEnv = if (openHat) 0.35 else 0.22
    }

    fun triggerSub(freqHz: Double) {
        subFreq = freqHz
        subEnv = 1.0
    }

    fun triggerChord(rootHz: Double, secondHalf: Boolean, groove: GrooveStyle) {
        chordRoot = if (secondHalf) rootHz * 0.8909 else rootHz
        chordEnv = if (groove == GrooveStyle.SYNTH_WAVE) 0.7 else 0.55
    }

    fun nextStereoSample(): Pair<Float, Float> {
        var mixL = 0.0
        var mixR = 0.0

        // 1. 808 Kick
        if (kickEnv > 0.0005) {
            val freq = 44.0 + 115.0 * kickEnv.pow(3.2)
            kickPhase += 2.0 * PI * freq / sr
            val k = sin(kickPhase) * kickEnv * 0.85
            mixL += k
            mixR += k
            kickEnv *= 0.99935
        }

        // 2. Snare / Rim
        if (snareEnv > 0.0005) {
            snareTonePhase += 2.0 * PI * 195.0 / sr
            val noise = (rng.nextDouble() * 2.0 - 1.0)
            val body = sin(snareTonePhase) * 0.45
            val s = (noise * 0.65 + body) * snareEnv * 0.55
            mixL += s * 0.96
            mixR += s * 1.04
            snareEnv *= 0.9987
        }

        // 3. Hi-Hat
        if (hatEnv > 0.0005) {
            val noise = (rng.nextDouble() * 2.0 - 1.0)
            val h = noise * hatEnv * 0.25
            mixL += h * 1.12
            mixR += h * 0.88
            hatEnv *= 0.9965
        }

        // 4. Sub-Bass 808
        if (subEnv > 0.0005) {
            subPhase += 2.0 * PI * subFreq / sr
            val sub = (sin(subPhase) * 0.82 + sin(subPhase * 2.0) * 0.18) * subEnv * 0.58
            mixL += sub
            mixR += sub
            subEnv *= 0.99982
        }

        // 5. Rhodes / Warm Analog Pad (Cm9 chord intervals: 1, b3, 5, 9)
        if (chordEnv > 0.0005) {
            val ratios = doubleArrayOf(1.0, 1.1892, 1.4983, 2.2449)
            var cL = 0.0
            var cR = 0.0
            for (idx in ratios.indices) {
                chordPhases[idx] += 2.0 * PI * (chordRoot * ratios[idx]) / sr
                val osc = sin(chordPhases[idx]) + 0.25 * sin(chordPhases[idx] * 2.0)
                val pan = if (idx % 2 == 0) 0.85 else 1.15
                cL += osc * pan
                cR += osc * (2.0 - pan)
            }
            val scale = chordEnv * 0.085
            mixL += cL * scale
            mixR += cR * scale
            chordEnv *= 0.99988
        }

        return Pair(mixL.toFloat(), mixR.toFloat())
    }
}

private class StereoDspChain(private val sampleRate: Int) {
    private val hpf = StereoBiquad()
    private val lowShelf = StereoBiquad()
    private val midPeak = StereoBiquad()
    private val highShelf = StereoBiquad()
    private var compEnv = 0.0f
    private var lastParams: DspParams? = null

    fun configure(params: DspParams) {
        if (lastParams == params) return
        lastParams = params
        hpf.setHighPass(sampleRate.toDouble(), params.hpfHz.toDouble(), 0.707)
        lowShelf.setLowShelf(sampleRate.toDouble(), 80.0, params.eqLowDb.toDouble())
        midPeak.setPeaking(sampleRate.toDouble(), params.eqMidHz.toDouble(), 1.1, params.eqMidDb.toDouble())
        highShelf.setHighShelf(sampleRate.toDouble(), 9000.0, params.eqHighDb.toDouble())
    }

    fun processSample(inL: Float, inR: Float, params: DspParams): Pair<Float, Float> {
        var (l, r) = hpf.process(inL, inR)
        val low = lowShelf.process(l, r)
        val mid = midPeak.process(low.first, low.second)
        val high = highShelf.process(mid.first, mid.second)
        l = high.first
        r = high.second

        // Analog Harmonic Saturation
        val drive = 1.0f + params.saturation * 3.5f
        if (params.saturation > 0.01f) {
            l = tanh((l * drive).toDouble()).toFloat() / tanh(drive.toDouble()).toFloat()
            r = tanh((r * drive).toDouble()).toFloat() / tanh(drive.toDouble()).toFloat()
        }

        // Dynamic Range Compressor (4:1 ratio)
        val peak = max(abs(l), abs(r))
        val coeff = if (peak > compEnv) 0.01f else 0.0008f
        compEnv += coeff * (peak - compEnv)
        val threshLin = 10.0.pow(params.compThresholdDb / 20.0).toFloat().coerceAtLeast(0.01f)
        val gain = if (compEnv > threshLin) {
            val over = compEnv / threshLin
            (threshLin * over.pow(0.25f)) / compEnv
        } else 1.0f

        return Pair((l * gain * 0.9f).coerceIn(-1f, 1f), (r * gain * 0.9f).coerceIn(-1f, 1f))
    }
}

private class StereoBiquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
    private var a1 = 0.0; private var a2 = 0.0
    private var x1L = 0.0; private var x2L = 0.0; private var y1L = 0.0; private var y2L = 0.0
    private var x1R = 0.0; private var x2R = 0.0; private var y1R = 0.0; private var y2R = 0.0

    fun setHighPass(fs: Double, fc: Double, q: Double) {
        val w0 = 2.0 * PI * fc / fs
        val alpha = sin(w0) / (2.0 * q)
        val cosW0 = cos(w0)
        val a0 = 1.0 + alpha
        b0 = ((1.0 + cosW0) / 2.0) / a0
        b1 = (-(1.0 + cosW0)) / a0
        b2 = ((1.0 + cosW0) / 2.0) / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha) / a0
    }

    fun setLowShelf(fs: Double, fc: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / fs
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0
        b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)) / a0
        b2 = (a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0
        a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosW0)) / a0
        a2 = ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
    }

    fun setHighShelf(fs: Double, fc: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / fs
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt(2.0)
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha
        val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0
        b1 = (-2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)) / a0
        b2 = (a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0
        a1 = (2.0 * ((a - 1.0) - (a + 1.0) * cosW0)) / a0
        a2 = ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
    }

    fun setPeaking(fs: Double, fc: Double, q: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * fc / fs
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha / a
        b0 = (1.0 + alpha * a) / a0
        b1 = (-2.0 * cosW0) / a0
        b2 = (1.0 - alpha * a) / a0
        a1 = (-2.0 * cosW0) / a0
        a2 = (1.0 - alpha / a) / a0
    }

    fun process(inL: Float, inR: Float): Pair<Float, Float> {
        val xl = inL.toDouble()
        val yl = b0 * xl + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
        x2L = x1L; x1L = xl; y2L = y1L; y1L = yl

        val xr = inR.toDouble()
        val yr = b0 * xr + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
        x2R = x1R; x1R = xr; y2R = y1R; y1R = yr

        return Pair(yl.toFloat(), yr.toFloat())
    }
}
