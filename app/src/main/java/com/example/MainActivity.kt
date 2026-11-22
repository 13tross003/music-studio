package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.audio.StudioAudioEngine
import com.example.audio.VoiceBoothController
import com.example.data.ai.StudioAiOrchestrator
import com.example.data.firebase.configureAppCheck
import com.example.data.model.UiState
import com.example.data.repository.StudioRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.console.ConsoleFloorTab
import com.example.ui.studio.StudioBoothTab
import com.example.ui.studio.StudioViewModel
import com.example.ui.theme.HydrogenStudioTheme
import com.example.ui.theme.ProducerCyan
import com.example.ui.theme.ProducerCyanSurface
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioConsoleBg
import com.example.ui.theme.StudioVoid
import com.example.ui.vault.LyricsVaultTab
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class StudioTab {
    BOOTH,
    CONSOLE,
    VAULT
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {

    private var hasMicPermission by mutableStateOf(false)
    private var micPermissionDeniedPermanently by mutableStateOf(false)
    private var showMicRationaleDialog by mutableStateOf(false)
    private var pendingMicAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure Firebase App Check with launch intent BEFORE UI or ViewModels
        configureAppCheck(this, intent)

        hasMicPermission = checkMicrophonePermission()
        enableEdgeToEdge()
        setContent {
            val micPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasMicPermission = isGranted
                if (isGranted) {
                    micPermissionDeniedPermanently = false
                    pendingMicAction?.invoke()
                } else {
                    val canShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    )
                    micPermissionDeniedPermanently = !canShowRationale
                    Log.w("StudioMicPermission", "Microphone permission denied (permanent=$micPermissionDeniedPermanently)")
                }
                pendingMicAction = null
            }

            HydrogenStudioTheme {
                HydrogenRootNavigation(
                    hasMicPermission = hasMicPermission,
                    micPermissionDeniedPermanently = micPermissionDeniedPermanently,
                    onRequestMicPermission = { onGranted ->
                        requestMicrophonePermission(
                            launchPrompt = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onGranted = onGranted
                        )
                    },
                    onOpenAppSettings = { openAppSettings() }
                )

                if (showMicRationaleDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showMicRationaleDialog = false
                            pendingMicAction = null
                        },
                        containerColor = StudioConsoleBg,
                        title = { Text("Enable Voice Booth Microphone") },
                        text = {
                            Text(
                                "Hydrogen Studio uses your microphone for live voice collaboration, humming melodies, and interrupting the Sound Engineer or AI Lyricist mid-stream."
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showMicRationaleDialog = false
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier.testTag("confirm_mic_rationale_button")
                            ) {
                                Text("Allow Microphone")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showMicRationaleDialog = false
                                    pendingMicAction = null
                                }
                            ) {
                                Text("Not Now")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = checkMicrophonePermission()
        hasMicPermission = granted
        if (granted) {
            micPermissionDeniedPermanently = false
        }
    }

    private fun checkMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestMicrophonePermission(
        launchPrompt: () -> Unit,
        onGranted: (() -> Unit)? = null
    ) {
        if (checkMicrophonePermission()) {
            hasMicPermission = true
            micPermissionDeniedPermanently = false
            onGranted?.invoke()
            return
        }

        pendingMicAction = onGranted
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
            showMicRationaleDialog = true
        } else {
            launchPrompt()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}

@Composable
private fun HydrogenRootNavigation(
    hasMicPermission: Boolean,
    micPermissionDeniedPermanently: Boolean,
    onRequestMicPermission: ((() -> Unit)?) -> Unit,
    onOpenAppSettings: () -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow updates automatically */ })
    } else {
        key(user.uid) {
            HydrogenStudioWorkspace(
                currentUserId = user.uid,
                userEmail = user.email.orEmpty(),
                hasMicPermission = hasMicPermission,
                micPermissionDeniedPermanently = micPermissionDeniedPermanently,
                onRequestMicPermission = onRequestMicPermission,
                onOpenAppSettings = onOpenAppSettings
            )
        }
    }
}

@Composable
private fun HydrogenStudioWorkspace(
    currentUserId: String,
    userEmail: String,
    hasMicPermission: Boolean,
    micPermissionDeniedPermanently: Boolean,
    onRequestMicPermission: ((() -> Unit)?) -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val audioEngine = StudioAudioEngine(app)
                val voiceBooth = VoiceBoothController(app, audioEngine)
                val repository = StudioRepository(app)
                val orchestrator = StudioAiOrchestrator()
                StudioViewModel(
                    repository = repository,
                    aiOrchestrator = orchestrator,
                    audioEngine = audioEngine,
                    voiceBooth = voiceBooth,
                    currentUserId = currentUserId
                )
            }
        }
    )

    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val selectedSessionId by viewModel.selectedSessionId.collectAsStateWithLifecycle()
    val activeSession = (sessionsState as? UiState.Success)?.data
        ?.firstOrNull { it.id == selectedSessionId }

    var selectedTab by rememberSaveable { mutableStateOf(StudioTab.BOOTH) }
    var promptedMicOnEntry by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hasMicPermission) {
        if (!hasMicPermission && !micPermissionDeniedPermanently && !promptedMicOnEntry) {
            promptedMicOnEntry = true
            onRequestMicPermission {
                viewModel.voiceBooth.setVoiceBoothEnabled(true)
            }
        }
    }

    val signOutAction: () -> Unit = {
        viewModel.audioEngine.release()
        viewModel.voiceBooth.stopAllSpeechAndListening()
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("StudioAuth", "Failed clearing credential state: ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp

        if (isExpanded) {
            Scaffold(
                containerColor = StudioVoid,
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = StudioConsoleBg
                    ) {
                        NavigationRailItem(
                            selected = selectedTab != StudioTab.VAULT,
                            onClick = { selectedTab = StudioTab.BOOTH },
                            icon = { Icon(Icons.Rounded.Forum, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_booth)) }
                        )
                        NavigationRailItem(
                            selected = selectedTab == StudioTab.VAULT,
                            onClick = { selectedTab = StudioTab.VAULT },
                            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_vault)) }
                        )
                    }
                    VerticalDivider(color = StudioBorder)

                    if (selectedTab == StudioTab.VAULT) {
                        LyricsVaultTab(
                            viewModel = viewModel,
                            userEmail = userEmail,
                            onSignOut = signOutAction,
                            onSessionSelectedNavigateToBooth = { selectedTab = StudioTab.BOOTH },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Side-by-side Booth + Console Floor on tablets/foldables
                        StudioBoothTab(
                            viewModel = viewModel,
                            activeSession = activeSession,
                            hasMicPermission = hasMicPermission,
                            micPermissionDeniedPermanently = micPermissionDeniedPermanently,
                            onRequestMicPermission = onRequestMicPermission,
                            onOpenAppSettings = onOpenAppSettings,
                            modifier = Modifier
                                .weight(1.15f)
                                .fillMaxHeight()
                        )
                        VerticalDivider(color = StudioBorder)
                        ConsoleFloorTab(
                            viewModel = viewModel,
                            modifier = Modifier
                                .width(400.dp)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = StudioVoid,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(
                        containerColor = StudioConsoleBg,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == StudioTab.BOOTH,
                            onClick = { selectedTab = StudioTab.BOOTH },
                            icon = { Icon(Icons.Rounded.Forum, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_booth)) },
                            modifier = Modifier.testTag("nav_tab_booth"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ProducerCyan,
                                selectedTextColor = ProducerCyan,
                                indicatorColor = ProducerCyanSurface
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == StudioTab.CONSOLE,
                            onClick = { selectedTab = StudioTab.CONSOLE },
                            icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_console)) },
                            modifier = Modifier.testTag("nav_tab_console"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ProducerCyan,
                                selectedTextColor = ProducerCyan,
                                indicatorColor = ProducerCyanSurface
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == StudioTab.VAULT,
                            onClick = { selectedTab = StudioTab.VAULT },
                            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                            label = { Text(stringResource(R.string.nav_vault)) },
                            modifier = Modifier.testTag("nav_tab_vault"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ProducerCyan,
                                selectedTextColor = ProducerCyan,
                                indicatorColor = ProducerCyanSurface
                            )
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        StudioTab.BOOTH -> StudioBoothTab(
                            viewModel = viewModel,
                            activeSession = activeSession,
                            hasMicPermission = hasMicPermission,
                            micPermissionDeniedPermanently = micPermissionDeniedPermanently,
                            onRequestMicPermission = onRequestMicPermission,
                            onOpenAppSettings = onOpenAppSettings
                        )
                        StudioTab.CONSOLE -> ConsoleFloorTab(
                            viewModel = viewModel
                        )
                        StudioTab.VAULT -> LyricsVaultTab(
                            viewModel = viewModel,
                            userEmail = userEmail,
                            onSignOut = signOutAction,
                            onSessionSelectedNavigateToBooth = { selectedTab = StudioTab.BOOTH }
                        )
                    }
                }
            }
        }
    }
}
