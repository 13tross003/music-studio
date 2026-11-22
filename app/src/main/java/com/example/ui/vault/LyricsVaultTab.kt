package com.example.ui.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.SpeakerRole
import com.example.data.model.StudioMessage
import com.example.data.model.StudioSession
import com.example.data.model.UiState
import com.example.ui.studio.StudioViewModel
import com.example.ui.theme.EngineerAmber
import com.example.ui.theme.LyricistViolet
import com.example.ui.theme.MeterGreen
import com.example.ui.theme.MeterPeakRed
import com.example.ui.theme.ProducerCyan
import com.example.ui.theme.ProducerCyanSurface
import com.example.ui.theme.SampleGold
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioConsoleBg
import com.example.ui.theme.StudioRackSurface
import com.example.ui.theme.StudioVoid

@Composable
fun LyricsVaultTab(
    viewModel: StudioViewModel,
    userEmail: String,
    onSignOut: () -> Unit,
    onSessionSelectedNavigateToBooth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var showNewSessionDialog by remember { mutableStateOf(false) }
    var newSessionTitle by remember { mutableStateOf("") }
    var copiedFeedback by remember { mutableStateOf(false) }

    val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val lyricistEntries = messages.filter { SpeakerRole.fromWire(it.speaker) == SpeakerRole.LYRICIST }
    val sampleCredits = messages.mapNotNull { msg ->
        msg.sampleCredit.takeIf { it.isNotBlank() }?.let { SpeakerRole.fromWire(msg.speaker) to it }
    }.distinctBy { it.second }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StudioVoid)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sessions Vault
        Surface(
            color = StudioRackSurface,
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = ProducerCyan)
                        Text("STUDIO SESSIONS VAULT", style = MaterialTheme.typography.labelSmall, color = ProducerCyan)
                    }
                    Button(
                        onClick = { showNewSessionDialog = true },
                        modifier = Modifier.testTag("new_session_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ProducerCyan, contentColor = StudioVoid),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Cut", fontWeight = FontWeight.Bold)
                    }
                }

                if (sessions.isEmpty()) {
                    Text(
                        text = "No studio sessions yet. Tap New Cut to open a fresh console.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sessions.forEach { session ->
                        SessionCard(
                            session = session,
                            isSelected = session.id == selectedSessionId,
                            canDelete = sessions.size > 1,
                            onSelect = {
                                viewModel.selectSession(session)
                                onSessionSelectedNavigateToBooth()
                            },
                            onDelete = { viewModel.deleteSession(session.id) }
                        )
                    }
                }
            }
        }

        // Sample Transparency Ledger
        Surface(
            color = StudioRackSurface,
            border = BorderStroke(1.dp, SampleGold.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Album, contentDescription = null, tint = SampleGold)
                    Text("SAMPLE TRANSPARENCY LEDGER", style = MaterialTheme.typography.labelSmall, color = SampleGold)
                }
                if (sampleCredits.isEmpty()) {
                    Text(
                        text = "No sample flips or interpolations logged in this cut yet. Ask the Engineer or Lyricist to flip a classic record to populate this ledger.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    sampleCredits.forEach { (role, credit) ->
                        Surface(
                            color = StudioConsoleBg,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SampleGold.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(
                                    text = "LOGGED BY ${role.displayName.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SampleGold
                                )
                                Text(
                                    text = credit,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Compiled AI Lyricist Sheet
        Surface(
            color = StudioRackSurface,
            border = BorderStroke(1.dp, LyricistViolet.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = LyricistViolet)
                        Text("COMPILED LYRIC SHEET", style = MaterialTheme.typography.labelSmall, color = LyricistViolet)
                    }
                    if (lyricistEntries.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val compiled = lyricistEntries.joinToString("\n\n---\n\n") { it.text }
                                clipboard.setText(AnnotatedString(compiled))
                                copiedFeedback = true
                            },
                            border = BorderStroke(1.dp, LyricistViolet)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = LyricistViolet, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (copiedFeedback) "Copied" else "Copy Pad", color = LyricistViolet)
                        }
                    }
                }
                if (lyricistEntries.isEmpty()) {
                    Text(
                        text = "Drop a spark in the Studio Booth and the AI Lyricist's verses, hooks, and pocket notes will compile here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    lyricistEntries.forEachIndexed { idx, entry ->
                        Surface(
                            color = StudioConsoleBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StudioBorder)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "TAKE #${idx + 1} • ${entry.stemBpm} BPM (${entry.stemKey})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LyricistViolet
                                )
                                Text(
                                    text = entry.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Producer Account Footer
        Surface(
            color = StudioConsoleBg,
            border = BorderStroke(1.dp, StudioBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SIGNED IN PRODUCER", style = MaterialTheme.typography.labelSmall, color = MeterGreen)
                    Text(
                        text = userEmail.ifBlank { "Google Producer Account" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("sign_out_button"),
                    border = BorderStroke(1.dp, MeterPeakRed.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = MeterPeakRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_sign_out), color = MeterPeakRed)
                }
            }
        }
    }

    if (showNewSessionDialog) {
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            containerColor = StudioRackSurface,
            title = { Text("Start New Studio Cut") },
            text = {
                OutlinedTextField(
                    value = newSessionTitle,
                    onValueChange = { newSessionTitle = it },
                    label = { Text("Track Title") },
                    placeholder = { Text("e.g., Late Night 808 Flip") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNewSession(newSessionTitle.ifBlank { "New Studio Cut" })
                        newSessionTitle = ""
                        showNewSessionDialog = false
                        onSessionSelectedNavigateToBooth()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ProducerCyan, contentColor = StudioVoid)
                ) {
                    Text("Open Console")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSessionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SessionCard(
    session: StudioSession,
    isSelected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onSelect,
        color = if (isSelected) ProducerCyanSurface else StudioConsoleBg,
        border = BorderStroke(1.dp, if (isSelected) ProducerCyan else StudioBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${session.bpm} BPM • ${session.musicalKey} • HPF ${session.hpfHz}Hz",
                    style = MaterialTheme.typography.labelSmall,
                    color = EngineerAmber
                )
            }
            if (canDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${session.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
