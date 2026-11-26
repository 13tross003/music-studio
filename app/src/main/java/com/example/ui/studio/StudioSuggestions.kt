package com.example.ui.studio

import com.example.data.model.DspParams
import com.example.data.model.GrooveStyle
import com.example.data.model.SpeakerRole
import com.example.data.model.StudioMessage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EngineerAmber
import com.example.ui.theme.LyricistViolet
import com.example.ui.theme.MeterGreen
import com.example.ui.theme.ProducerCyan
import com.example.ui.theme.ProducerCyanSurface
import com.example.ui.theme.SampleGold
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioRackSurface
import com.example.ui.theme.StudioVoid

enum class SuggestionCategory(val label: String, val icon: String) {
    ALL("All Moves", "🔥"),
    MIX("Mix & Pocket", "🎛️"),
    LYRICS("Hooks & Pen", "✍️"),
    SAMPLE_FLIPS("Sample Flips", "📻"),
    ARRANGE("Arrangement", "🎼")
}

data class StudioSuggestion(
    val id: String,
    val title: String,
    val prompt: String,
    val category: SuggestionCategory,
    val targetRole: SpeakerRole = SpeakerRole.PRODUCER,
    val badge: String? = null
)

data class EngineerPreset(
    val id: String,
    val name: String,
    val subtitle: String,
    val description: String,
    val dsp: DspParams
)

enum class BlueprintSection(
    val sectionName: String,
    val barCount: String,
    val description: String,
    val defaultPrompt: String
) {
    INTRO(
        sectionName = "Intro",
        barCount = "4–8 Bars",
        description = "Atmospheric pads, low Kid Cudi hums, filtered drum loop setting the mood.",
        defaultPrompt = "Engineer & Lyricist: Build a 4-bar atmospheric Intro with filtered Rhodes chords, soft Kid Cudi hums, and a gradual low-end swell."
    ),
    VERSE_1(
        sectionName = "Verse 1",
        barCount = "16 Bars",
        description = "Intimate storytelling, locked cadence riding kick & snare transients.",
        defaultPrompt = "Lyricist: Write Verse 1 (16 bars). Ground it in vivid late-night storytelling with sharp consonants riding the Engineer's snare pocket."
    ),
    PRE_CHORUS(
        sectionName = "Pre-Chorus",
        barCount = "8 Bars",
        description = "Escalating syllable rhythm and harmonic tension building toward the drop.",
        defaultPrompt = "Lyricist & Engineer: Build an 8-bar Pre-Chorus that escalates melodic tension and strips the drums right before the hook hits."
    ),
    HOOK(
        sectionName = "Hook / Chorus",
        barCount = "8–16 Bars",
        description = "Unforgettable stadium melody, universal lyric hook, maximum emotional payoff.",
        defaultPrompt = "Lyricist: Drop a timeless, unforgettable Grammy hook. Singable, vulnerable, and explosive over the full 808 drop."
    ),
    VERSE_2(
        sectionName = "Verse 2",
        barCount = "16 Bars",
        description = "Deeper emotional perspective, double-time cadence switch-up on bars 5–8.",
        defaultPrompt = "Lyricist: Write Verse 2 (16 bars). Start introspective, then hit a rapid double-time flow on bars 5 through 8."
    ),
    BRIDGE(
        sectionName = "Bridge",
        barCount = "8 Bars",
        description = "Harmonic shift to a relative chord, stripped-back instrumentation, vocal breakthrough.",
        defaultPrompt = "Engineer & Lyricist: Craft an 8-bar Bridge. Engineer drops the drums; Lyricist sings a soaring, emotionally vulnerable vocal melody."
    ),
    OUTRO(
        sectionName = "Outro",
        barCount = "8–16 Bars",
        description = "Climactic vocal ad-libs, decaying sample loop, and sub-bass fadeout.",
        defaultPrompt = "Engineer & Lyricist: 16-bar Outro with layered vocal ad-libs and melodic hums fading out into a vinyl-filtered Rhodes reverb tail."
    )
}

object StudioSuggestionsEngine {

    val SIGNATURE_PRESETS = listOf(
        EngineerPreset(
            id = "grammy_808",
            name = "Grammy 808 Sub Boom",
            subtitle = "Trap Soul • 92 BPM",
            description = "30Hz HPF, +4.5dB low shelf sub, -1.5dB mid carve at 1.2kHz, 30% analog tape saturation.",
            dsp = DspParams(
                bpm = 92,
                musicalKey = "C minor",
                grooveStyle = GrooveStyle.TRAP_SOUL,
                hpfHz = 30,
                eqLowDb = 4.5f,
                eqMidHz = 1200,
                eqMidDb = -1.5f,
                eqHighDb = 2.0f,
                compThresholdDb = -16.0f,
                saturation = 0.30f
            )
        ),
        EngineerPreset(
            id = "vocal_pocket",
            name = "Crystal Vocal Air",
            subtitle = "Neo Soul • 96 BPM",
            description = "45Hz HPF, surgical -3.0dB dip at 1kHz for vocal clarity, +3.5dB 9kHz air shelf, smooth compression.",
            dsp = DspParams(
                bpm = 96,
                musicalKey = "F# minor",
                grooveStyle = GrooveStyle.NEO_SOUL,
                hpfHz = 45,
                eqLowDb = 2.0f,
                eqMidHz = 1000,
                eqMidDb = -3.0f,
                eqHighDb = 3.5f,
                compThresholdDb = -18.0f,
                saturation = 0.15f
            )
        ),
        EngineerPreset(
            id = "dusty_vinyl",
            name = "Dusty Vinyl Soul",
            subtitle = "Boom Bap • 88 BPM",
            description = "60Hz HPF roll-off, 50% analog harmonic warmth, warm boom bap swing with punchy snare transients.",
            dsp = DspParams(
                bpm = 88,
                musicalKey = "Eb major",
                grooveStyle = GrooveStyle.BOOM_BAP,
                hpfHz = 60,
                eqLowDb = 2.5f,
                eqMidHz = 2400,
                eqMidDb = 1.0f,
                eqHighDb = -1.0f,
                compThresholdDb = -14.0f,
                saturation = 0.50f
            )
        ),
        EngineerPreset(
            id = "stadium_punch",
            name = "Stadium Trap Punch",
            subtitle = "Trap Soul • 138 BPM",
            description = "Fast 138 BPM tempo, +5.0dB 808 sub, punchy 4:1 compressor at -12dB for festival systems.",
            dsp = DspParams(
                bpm = 138,
                musicalKey = "G minor",
                grooveStyle = GrooveStyle.TRAP_SOUL,
                hpfHz = 28,
                eqLowDb = 5.0f,
                eqMidHz = 800,
                eqMidDb = -2.0f,
                eqHighDb = 2.5f,
                compThresholdDb = -12.0f,
                saturation = 0.35f
            )
        ),
        EngineerPreset(
            id = "midnight_lofi",
            name = "Late Night Neo-Soul",
            subtitle = "Neo Soul • 82 BPM",
            description = "82 BPM, round low end, smooth mid presence, subtle harmonic bloom for intimate vocal confessions.",
            dsp = DspParams(
                bpm = 82,
                musicalKey = "Bb minor",
                grooveStyle = GrooveStyle.NEO_SOUL,
                hpfHz = 40,
                eqLowDb = 1.5f,
                eqMidHz = 1600,
                eqMidDb = 1.0f,
                eqHighDb = 1.0f,
                compThresholdDb = -20.0f,
                saturation = 0.20f
            )
        )
    )

    fun getContextualSuggestions(
        lastMessage: StudioMessage?,
        currentDsp: DspParams
    ): List<StudioSuggestion> {
        val list = mutableListOf<StudioSuggestion>()
        val lastRole = lastMessage?.let { SpeakerRole.fromWire(it.speaker) }

        when (lastRole) {
            SpeakerRole.ENGINEER -> {
                list += StudioSuggestion(
                    id = "eng_to_lyricist_hook",
                    title = "✍️ Lyricist: Write hook over that pocket",
                    prompt = "Lyricist, lock in on the Engineer's kick/snare transients and write an unforgettable hook.",
                    category = SuggestionCategory.NEXT_MOVES,
                    targetRole = SpeakerRole.LYRICIST,
                    badge = "Grammy Move"
                )
                list += StudioSuggestion(
                    id = "eng_hpf_tighten",
                    title = "🎛️ Carve mid pocket at 1.2kHz",
                    prompt = "Engineer, notch 1.2kHz by -2.5dB so the vocal cuts cleanly through the mix, and tighten the sub-bass.",
                    category = SuggestionCategory.MIX,
                    targetRole = SpeakerRole.ENGINEER
                )
                list += StudioSuggestion(
                    id = "eng_bounce_stems",
                    title = "🥁 Bounce 4-stem WAV pack",
                    prompt = "Engineer, bounce the current 4-stem WAV stems so we can audition the drums and 808 soloed.",
                    category = SuggestionCategory.NEXT_MOVES,
                    targetRole = SpeakerRole.ENGINEER,
                    badge = "WAV Stem"
                )
                list += StudioSuggestion(
                    id = "eng_flip_break",
                    title = "📻 Flip Skull Snaps 'It's a New Day'",
                    prompt = "Let's flip the drum break from Skull Snaps' 'It's a New Day' with warm vinyl saturation.",
                    category = SuggestionCategory.SAMPLE_FLIPS,
                    targetRole = SpeakerRole.ENGINEER
                )
            }
            SpeakerRole.LYRICIST -> {
                list += StudioSuggestion(
                    id = "lyr_cudi_hum",
                    title = "✨ Add Kid Cudi style hums",
                    prompt = "Lyricist, layer some low, resonant Kid Cudi style melodic hums behind that verse.",
                    category = SuggestionCategory.NEXT_MOVES,
                    targetRole = SpeakerRole.LYRICIST,
                    badge = "Vocal Layer"
                )
                list += StudioSuggestion(
                    id = "lyr_engineer_drop",
                    title = "🎚️ Engineer: Drop beat for bridge",
                    prompt = "Engineer, drop the drums completely for the bridge and let the Rhodes and sub-bass carry the emotion.",
                    category = SuggestionCategory.ARRANGE,
                    targetRole = SpeakerRole.ENGINEER
                )
                list += StudioSuggestion(
                    id = "lyr_next_verse",
                    title = "🎤 Write verse 2 with double entendres",
                    prompt = "Lyricist, follow that hook with Verse 2. Push the cadence double-time on bars 5 to 8.",
                    category = SuggestionCategory.LYRICS,
                    targetRole = SpeakerRole.LYRICIST
                )
                list += StudioSuggestion(
                    id = "lyr_sample_reference",
                    title = "📻 Interpolate Minnie Riperton",
                    prompt = "Can we interpolate the melody from Minnie Riperton's 'Lovin' You' into a minor key hook?",
                    category = SuggestionCategory.SAMPLE_FLIPS,
                    targetRole = SpeakerRole.LYRICIST
                )
            }
            else -> {
                list += StudioSuggestion(
                    id = "init_kid_cudi_808",
                    title = "🌌 92 BPM Kid Cudi hums & 808s",
                    prompt = "92 BPM late-night Kid Cudi hums over warm Rhodes chords and deep 808 sub bass.",
                    category = SuggestionCategory.NEXT_MOVES,
                    badge = "Popular"
                )
                list += StudioSuggestion(
                    id = "init_soul_flip",
                    title = "📻 Flip a dusty 70s soul record",
                    prompt = "Flip a dusty 70s soul drum break at 88 BPM with crisp vinyl warmth and a punchy snare pocket.",
                    category = SuggestionCategory.SAMPLE_FLIPS,
                    badge = "Sample"
                )
                list += StudioSuggestion(
                    id = "init_anthem_hook",
                    title = "🏆 138 BPM stadium trap anthem",
                    prompt = "138 BPM stadium trap anthem in C minor—heavy 808 glides, surgical HPF, and an explosive chorus.",
                    category = SuggestionCategory.NEXT_MOVES
                )
                list += StudioSuggestion(
                    id = "init_vulnerable_neo_soul",
                    title = "💜 F# minor neo-soul confession",
                    prompt = "Intimate neo-soul confession in F# minor at 84 BPM—let the vocal breathe in the mid-frequency pocket.",
                    category = SuggestionCategory.LYRICS
                )
            }
        }

        // Perennial categorized suggestions
        list += StudioSuggestion(
            id = "mix_808_boom",
            title = "🔊 Boost 808 sub & tape warmth",
            prompt = "Engineer, push the 808 sub shelf at 80Hz +4dB and drive analog saturation to 35% for tape warmth.",
            category = SuggestionCategory.MIX,
            targetRole = SpeakerRole.ENGINEER
        )
        list += StudioSuggestion(
            id = "mix_air_shelf",
            title = "✨ Add 9kHz air to the highs",
            prompt = "Engineer, open up the 9kHz air shelf +3dB and check the compressor threshold for vocal headroom.",
            category = SuggestionCategory.MIX,
            targetRole = SpeakerRole.ENGINEER
        )
        list += StudioSuggestion(
            id = "lyr_pre_chorus",
            title = "⚡ Build tension pre-chorus",
            prompt = "Lyricist, write an 8-bar pre-chorus that ratchets up the emotional tension right before the beat drops.",
            category = SuggestionCategory.LYRICS,
            targetRole = SpeakerRole.LYRICIST
        )
        list += StudioSuggestion(
            id = "lyr_stadium_call_response",
            title = "🗣️ Call-and-response anthem hook",
            prompt = "Lyricist, give us a stadium call-and-response hook that 50,000 people will scream back word for word.",
            category = SuggestionCategory.LYRICS,
            targetRole = SpeakerRole.LYRICIST
        )
        list += StudioSuggestion(
            id = "sample_isley_flip",
            title = "📻 Flip Isley Brothers 'Between The Sheets'",
            prompt = "Let's flip the iconic drum & bass groove from The Isley Brothers' 'Between The Sheets' at 86 BPM.",
            category = SuggestionCategory.SAMPLE_FLIPS,
            targetRole = SpeakerRole.ENGINEER
        )
        list += StudioSuggestion(
            id = "sample_marvin_gaye",
            title = "📻 Sample Marvin Gaye 'Sexual Healing'",
            prompt = "Interpolate the Roland TR-808 groove from Marvin Gaye's 'Sexual Healing' with a modern trap soul bounce.",
            category = SuggestionCategory.SAMPLE_FLIPS,
            targetRole = SpeakerRole.ENGINEER
        )
        list += StudioSuggestion(
            id = "arrange_strip_drop",
            title = "💥 Strip beat, then massive 808 drop",
            prompt = "Engineer & Lyricist, cut the drums for 4 bars of a cappella vocals, then hit with the hardest 808 drop on bar 1.",
            category = SuggestionCategory.ARRANGE
        )
        list += StudioSuggestion(
            id = "arrange_outro_fade",
            title = "🌅 Cinematic Rhodes & hums outro",
            prompt = "Craft a 16-bar cinematic outro with layered melodic hums fading out into filtered Rhodes chords.",
            category = SuggestionCategory.ARRANGE
        )

        return list.distinctBy { it.id }
    }

    fun getInlineMessageMoves(message: StudioMessage): List<StudioSuggestion> {
        val role = SpeakerRole.fromWire(message.speaker)
        return when (role) {
            SpeakerRole.ENGINEER -> listOf(
                StudioSuggestion(
                    id = "msg_${message.id}_hook",
                    title = "✍️ Write Hook Over Beat",
                    prompt = "Lyricist, write an unforgettable hook locked to the Engineer's kick/snare groove.",
                    category = SuggestionCategory.LYRICS,
                    targetRole = SpeakerRole.LYRICIST
                ),
                StudioSuggestion(
                    id = "msg_${message.id}_bounce",
                    title = "🥁 Bounce Fresh Stems",
                    prompt = "Engineer, bounce the updated 44.1kHz multi-stem WAV pack for this arrangement.",
                    category = SuggestionCategory.MIX,
                    targetRole = SpeakerRole.ENGINEER
                ),
                StudioSuggestion(
                    id = "msg_${message.id}_tighten",
                    title = "🎛️ Carve Mid Frequencies",
                    prompt = "Engineer, carve out the low mids at 800Hz by -2dB to clear space for the vocal.",
                    category = SuggestionCategory.MIX,
                    targetRole = SpeakerRole.ENGINEER
                )
            )
            SpeakerRole.LYRICIST -> listOf(
                StudioSuggestion(
                    id = "msg_${message.id}_hums",
                    title = "✨ Add Kid Cudi Hums",
                    prompt = "Lyricist, layer melodic Kid Cudi style hums behind that vocal take.",
                    category = SuggestionCategory.LYRICS,
                    targetRole = SpeakerRole.LYRICIST
                ),
                StudioSuggestion(
                    id = "msg_${message.id}_verse2",
                    title = "🎤 Write Next Verse",
                    prompt = "Lyricist, let's write Verse 2 keeping that same energy and cadence.",
                    category = SuggestionCategory.LYRICS,
                    targetRole = SpeakerRole.LYRICIST
                ),
                StudioSuggestion(
                    id = "msg_${message.id}_bridge",
                    title = "🌉 Build Tension Bridge",
                    prompt = "Engineer & Lyricist: Strip the beat down and write a vulnerable 8-bar bridge.",
                    category = SuggestionCategory.ARRANGE
                )
            )
            else -> emptyList()
        }
    }
}

@Composable
fun StudioSuggestionsDeck(
    lastMessage: StudioMessage?,
    currentDsp: DspParams,
    isThinking: Boolean,
    onSuggestionSelected: (StudioSuggestion, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by rememberSaveable { mutableStateOf(SuggestionCategory.ALL) }
    var isExpanded by rememberSaveable { mutableStateOf(true) }

    val allSuggestions = remember(lastMessage?.id, currentDsp) {
        StudioSuggestionsEngine.getContextualSuggestions(lastMessage, currentDsp)
    }

    val filteredSuggestions = remember(selectedCategory, allSuggestions) {
        if (selectedCategory == SuggestionCategory.ALL) allSuggestions
        else allSuggestions.filter { it.category == selectedCategory }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Deck Header with Category Chips & Expand/Collapse
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = ProducerCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "GRAMMY SUGGESTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = ProducerCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("toggle_suggestions_button")
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse Suggestions" else "Expand Suggestions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Category Filter Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionCategory.entries.forEach { cat ->
                        val isSelected = cat == selectedCategory
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    text = "${cat.icon} ${cat.label}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ProducerCyanSurface,
                                selectedLabelColor = ProducerCyan
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = StudioBorder,
                                selectedBorderColor = ProducerCyan
                            )
                        )
                    }
                }

                // Horizontal Suggestion Cards Carousel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredSuggestions.forEach { suggestion ->
                        val cardBorderColor = when (suggestion.targetRole) {
                            SpeakerRole.ENGINEER -> EngineerAmber
                            SpeakerRole.LYRICIST -> LyricistViolet
                            else -> ProducerCyan
                        }
                        Surface(
                            onClick = {
                                if (!isThinking) onSuggestionSelected(suggestion, true)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = StudioRackSurface,
                            border = BorderStroke(1.dp, cardBorderColor.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .widthIn(min = 180.dp, max = 260.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("suggestion_chip_${suggestion.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (suggestion.badge != null) {
                                        Surface(
                                            color = cardBorderColor.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = suggestion.badge.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = cardBorderColor,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = suggestion.category.label.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            onSuggestionSelected(suggestion, false)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Edit suggestion prompt",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = suggestion.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = suggestion.prompt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
