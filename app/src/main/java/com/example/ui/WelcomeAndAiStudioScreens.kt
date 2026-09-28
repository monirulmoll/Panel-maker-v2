package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import com.example.data.StudioProjectEntity
import com.example.engine.AiBuildStepStatus
import com.example.engine.AiChatTurn
import com.example.engine.LocalConfigStateWriter

enum class AiWorkspaceTab {
    PREVIEW,
    TEST,
    DOWNLOAD
}

/**
 * FIRST SCREEN: WELCOME SCREEN
 * - Shows Welcome banner
 * - Shows Permission buttons ONLY if not yet granted (once granted, the button disappears immediately!)
 * - Lets user choose between:
 *   1. Offline Mode (Manual Studio Builder)
 *   2. Online / AI Mode (Requires valid .gguf file)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioWelcomeModeScreen(
    hasStoragePermission: Boolean,
    hasOverlayPermission: Boolean,
    onRefreshPermissions: () -> Unit,
    onSelectOfflineMode: () -> Unit,
    onSelectOnlineAiMode: () -> Unit
) {
    val context = LocalContext.current
    val storagePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
        if (!LocalConfigStateWriter.hasStoragePermissionGranted(context)) {
            LocalConfigStateWriter.requestStoragePermission(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0288D1),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = "Studio Error",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "STUDIO ERROR",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Welcome — Choose Your Workspace Mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .testTag("welcome_screen_root"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFE0F2FE),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Welcome Icon",
                            tint = Color(0xFF0288D1),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Text(
                    text = "Welcome to Studio Error",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Select Offline Manual Mode to build floating panels & projects manually, or select Online / AI Mode to load a .gguf model and generate apps from text prompts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF475569),
                    textAlign = TextAlign.Center
                )

                // PERMISSION CARD: ONLY VISIBLE IF ANY PERMISSION IS MISSING.
                // Each button disappears immediately once its permission is granted!
                if (!hasStoragePermission || !hasOverlayPermission) {
                    Surface(
                        color = Color(0xFFFFFBEB),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("welcome_permissions_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Required Permissions (Grant to hide):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF92400E)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!hasStoragePermission) {
                                    Button(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                                storagePermLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                    )
                                                )
                                            } else {
                                                LocalConfigStateWriter.requestStoragePermission(context)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFD97706),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .testTag("welcome_grant_storage_button")
                                    ) {
                                        Text(
                                            text = "🔓 Allow Storage",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!hasOverlayPermission) {
                                    Button(
                                        onClick = {
                                            LocalConfigStateWriter.requestOverlayPermission(context)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2563EB),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .testTag("welcome_grant_overlay_button")
                                    ) {
                                        Text(
                                            text = "🔓 Allow Overlay",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // OPTION 1: OFFLINE MODE (MANUAL STUDIO)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectOfflineMode() }
                        .testTag("welcome_select_offline_mode_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF0288D1))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFE0F2FE),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Offline Mode",
                                        tint = Color(0xFF0288D1),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Offline Mode (Manual Studio)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Create & edit projects, drag-and-drop canvas widgets, inspect properties, and build APKs manually.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                        Button(
                            onClick = onSelectOfflineMode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0288D1),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("welcome_select_offline_mode_button")
                        ) {
                            Text("Open Offline Mode", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // OPTION 2: ONLINE / AI MODE (GGUF MODEL STUDIO)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectOnlineAiMode() }
                        .testTag("welcome_select_online_ai_mode_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF4F46E5))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFEEF2FF),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "Online AI Mode",
                                        tint = Color(0xFF4F46E5),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Online / AI Mode (.GGUF Studio)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Import a valid .gguf model file to unlock the AI Studio builder with live step status, Preview, Float, Test & Download.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                        Button(
                            onClick = onSelectOnlineAiMode,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F46E5),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("welcome_select_online_ai_mode_button")
                        ) {
                            Text("Open Online / AI Mode (.GGUF)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * GGUF MODEL GATE SCREEN (SHOWN WHEN USER SELECTS ONLINE / AI MODE)
 * - Requires a valid .gguf file before opening AI Mode.
 * - Shows a clear Error Banner if the user picks a wrong file or if import fails.
 * - Includes an option in the UI to switch to Manual (Offline) Mode anytime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioAiGgufGateScreen(
    importErrorMessage: String?,
    hasStoragePermission: Boolean,
    hasOverlayPermission: Boolean,
    onRefreshPermissions: () -> Unit,
    onImportGgufUri: (android.net.Uri) -> Unit,
    onLoadGgufPath: (String) -> Unit,
    onSwitchToManualOfflineMode: () -> Unit
) {
    val context = LocalContext.current
    var manualGgufPath by remember { mutableStateOf("") }

    val ggufPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImportGgufUri(uri)
        }
    }

    val storagePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
        if (!LocalConfigStateWriter.hasStoragePermissionGranted(context)) {
            LocalConfigStateWriter.requestStoragePermission(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "GGUF Required",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "AI Mode — Select .GGUF Model",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Valid .gguf model required to enter AI Mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                },
                actions = {
                    Button(
                        onClick = onSwitchToManualOfflineMode,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF0288D1)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("gguf_gate_switch_to_manual_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Manual Mode",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manual Mode", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF4F46E5))
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(20.dp)
                    .testTag("gguf_gate_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = BorderStroke(1.5.dp, Color(0xFF6366F1))
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Import .GGUF Model File",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Please provide a valid .gguf model file to open AI Mode. If a wrong file is selected or import fails, an error will be displayed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF475569)
                    )

                    // PERMISSION BUTTONS: Disappear immediately once granted!
                    if (!hasStoragePermission || !hasOverlayPermission) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!hasStoragePermission) {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                            storagePermLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                )
                                            )
                                        } else {
                                            LocalConfigStateWriter.requestStoragePermission(context)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("gguf_gate_grant_storage_button")
                                ) {
                                    Text("🔓 Allow Storage", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (!hasOverlayPermission) {
                                Button(
                                    onClick = { LocalConfigStateWriter.requestOverlayPermission(context) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("gguf_gate_grant_overlay_button")
                                ) {
                                    Text("🔓 Allow Overlay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // ERROR BANNER WHEN WRONG FILE OR IMPORT FAILS
                    if (!importErrorMessage.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, Color(0xFFDC2626)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gguf_import_error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = importErrorMessage,
                                    color = Color(0xFF991B1B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { ggufPickerLauncher.launch("*/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F46E5),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("pick_gguf_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Pick .gguf File",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Select .GGUF File from Storage",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    OutlinedTextField(
                        value = manualGgufPath,
                        onValueChange = { manualGgufPath = it },
                        label = { Text("Or enter .gguf file path on storage") },
                        placeholder = { Text("/storage/emulated/0/Download/model.gguf") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_gguf_path_input")
                    )

                    OutlinedButton(
                        onClick = { onLoadGgufPath(manualGgufPath) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF4F46E5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("verify_gguf_path_button")
                    ) {
                        Text(
                            text = "Verify & Open AI Mode",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F46E5)
                        )
                    }

                    OutlinedButton(
                        onClick = onSwitchToManualOfflineMode,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF64748B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("gguf_card_back_to_manual_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Manual Mode",
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Switch to Offline Manual Mode",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                    }
                }
            }
        }
    }
}

/**
 * INDEPENDENT AI STUDIO WORKSPACE SCREEN (COMPLETELY SEPARATE FROM MANUAL MODE)
 * - Starts 100% empty: no pre-filled prompt text, no premade project file, no premade widgets.
 * - Shows Google AI Studio-style live step-by-step build status & error/diagnostic checks while building.
 * - Announces "App tayar ho gaya!" with AI output when complete.
 * - Provides dedicated Preview, Float, Test, and Download options strictly for the AI-generated app.
 * - Includes "Manual Mode" button in the top bar to switch to Offline Manual Mode anytime.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioAiWorkspaceScreen(
    modelFileName: String,
    modelArchitecture: String,
    hasStoragePermission: Boolean,
    hasOverlayPermission: Boolean,
    isAiBuilding: Boolean,
    liveBuildSteps: List<AiBuildStepStatus>,
    chatHistory: List<AiChatTurn>,
    aiBuiltProject: StudioProjectEntity?,
    aiBuiltComponents: List<CanvasComponentEntity>,
    isAiFloatingOverlayRunning: Boolean,
    aiCompiledApkSummary: String?,
    onRefreshPermissions: () -> Unit,
    onSendPromptToAi: (String) -> Unit,
    onToggleAiFloatOverlay: () -> Unit,
    onTriggerAiWidgetTest: (CanvasComponentEntity, String?) -> Unit,
    onDownloadAiApk: () -> Unit,
    onInstallAiApk: () -> Unit,
    onChangeGgufModel: () -> Unit,
    onSwitchToManualOfflineMode: () -> Unit
) {
    val context = LocalContext.current
    // Starts 100% empty — no pre-filled text!
    var userPromptInput by remember { mutableStateOf("") }
    var activeAiTab by remember { mutableStateOf(AiWorkspaceTab.PREVIEW) }
    val listState = rememberLazyListState()

    LaunchedEffect(chatHistory.size, isAiBuilding, aiBuiltProject) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val storagePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshPermissions()
        if (!LocalConfigStateWriter.hasStoragePermissionGranted(context)) {
            LocalConfigStateWriter.requestStoragePermission(context)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFF1F5F9),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Mode",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "AI Studio Mode",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "GGUF: $modelFileName ($modelArchitecture)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = onChangeGgufModel,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .height(34.dp)
                            .testTag("ai_workspace_change_gguf_button")
                    ) {
                        Text("GGUF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Button(
                        onClick = onSwitchToManualOfflineMode,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(34.dp)
                            .testTag("ai_workspace_switch_to_manual_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Manual Mode",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manual Mode", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF4F46E5))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            // PERMISSION BAR: Disappears completely once permissions are granted!
            if (!hasStoragePermission || !hasOverlayPermission) {
                Surface(
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_workspace_permissions_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Grant Permissions:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF92400E),
                            modifier = Modifier.weight(1f)
                        )
                        if (!hasStoragePermission) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                        storagePermLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                                            )
                                        )
                                    } else {
                                        LocalConfigStateWriter.requestStoragePermission(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("ai_workspace_grant_storage_button")
                            ) {
                                Text("Allow Storage", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (!hasOverlayPermission) {
                            Button(
                                onClick = { LocalConfigStateWriter.requestOverlayPermission(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .testTag("ai_workspace_grant_overlay_button")
                            ) {
                                Text("Allow Overlay", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // MAIN SCROLLABLE AI STREAM & INDEPENDENT PREVIEW / FLOAT / TEST / DOWNLOAD AREA
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (chatHistory.isEmpty() && !isAiBuilding && aiBuiltProject == null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_workspace_empty_state")
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Empty AI Workspace",
                                    tint = Color(0xFF4F46E5),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "AI Mode Ready — No Premade Project Loaded",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Send a text prompt below. AI will show live step-by-step build status and error diagnostics like Google AI Studio, then unlock Preview, Float, Test, and Download when your app is ready.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF475569),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // PREVIOUS & CURRENT AI CHAT TURNS
                items(chatHistory, key = { it.id }) { turn ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // User Prompt Bubble
                            Surface(
                                color = Color(0xFFEEF2FF),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "You: ${turn.userPrompt}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF312E81),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }

                            // Completed Steps Summary
                            if (turn.steps.isNotEmpty()) {
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "AI Studio Build Steps & Error Diagnostics:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F172A)
                                        )
                                        turn.steps.forEach { step ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (step.hasError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                                    contentDescription = "Step Status",
                                                    tint = if (step.hasError) Color(0xFFDC2626) else Color(0xFF16A34A),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "[${step.stepNumber}/${step.totalSteps}] ${step.title} — ${step.detail}",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF334155)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // AI Output Response (Conversational Reply OR "App tayar ho gaya!")
                            Surface(
                                color = if (turn.isConversationalReply) Color(0xFFF5F3FF) else Color(0xFFECFDF5),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (turn.isConversationalReply) Color(0xFF6366F1) else Color(0xFF10B981)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        if (turn.isConversationalReply) "ai_conversational_reply_banner"
                                        else "ai_ready_output_banner"
                                    )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = turn.aiResponseText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (turn.isConversationalReply) Color(0xFF312E81) else Color(0xFF065F46)
                                    )
                                    if (turn.generatedCodePreview.isNotBlank()) {
                                        val scratchFiles = turn.generatedScratchFiles
                                        var selectedFileKey by remember(turn.id) {
                                            mutableStateOf(scratchFiles.keys.firstOrNull() ?: "")
                                        }
                                        if (scratchFiles.isNotEmpty()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                scratchFiles.keys.forEach { filePath ->
                                                    val shortName = filePath.substringAfterLast('/')
                                                    val isSelected = selectedFileKey == filePath
                                                    Surface(
                                                        color = if (isSelected) Color(0xFF0288D1) else Color(0xFF1E293B),
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.clickable { selectedFileKey = filePath }
                                                    ) {
                                                        Text(
                                                            text = shortName,
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        val displayedCode = if (selectedFileKey.isNotBlank() && scratchFiles.containsKey(selectedFileKey)) {
                                            scratchFiles[selectedFileKey].orEmpty()
                                        } else {
                                            turn.generatedCodePreview
                                        }
                                        Surface(
                                            color = Color(0xFF0F172A),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 170.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                Text(
                                                    text = displayedCode,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFF4ADE80)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // GOOGLE AI STUDIO LIVE STEP-BY-STEP PROGRESS CARD WHILE BUILDING
                if (isAiBuilding) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            border = BorderStroke(1.5.dp, Color(0xFF6366F1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_live_build_status_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "AI Studio Building Your App...",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF38BDF8),
                                    trackColor = Color(0xFF1E293B)
                                )
                                liveBuildSteps.forEach { step ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (step.isCompleted) Icons.Default.CheckCircle else Icons.Default.Build,
                                            contentDescription = step.title,
                                            tint = if (step.isCompleted) Color(0xFF4ADE80) else Color(0xFF38BDF8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Step ${step.stepNumber}/${step.totalSteps}: ${step.title}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = step.detail,
                                                fontSize = 10.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // DEDICATED AI MODE PREVIEW / FLOAT / TEST / DOWNLOAD PANEL
                // (Only appears after AI generates an app from user prompt; 100% separate from Manual Mode!)
                if (aiBuiltProject != null && aiBuiltComponents.isNotEmpty() && !isAiBuilding) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF4F46E5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_dedicated_preview_float_test_download_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 4 DEDICATED AI ACTION BUTTONS: PREVIEW | FLOAT | TEST | DOWNLOAD
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // 1. PREVIEW BUTTON
                                    Button(
                                        onClick = { activeAiTab = AiWorkspaceTab.PREVIEW },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (activeAiTab == AiWorkspaceTab.PREVIEW) Color(0xFF4F46E5) else Color(0xFFE2E8F0),
                                            contentColor = if (activeAiTab == AiWorkspaceTab.PREVIEW) Color.White else Color(0xFF1E293B)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("ai_mode_preview_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Visibility,
                                            contentDescription = "Preview",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Preview", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }

                                    // 2. FLOAT BUTTON
                                    Button(
                                        onClick = onToggleAiFloatOverlay,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAiFloatingOverlayRunning) Color(0xFFDC2626) else Color(0xFF0288D1),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("ai_mode_float_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isAiFloatingOverlayRunning) Icons.Default.Stop else Icons.Default.Layers,
                                            contentDescription = "Float",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isAiFloatingOverlayRunning) "Stop Float" else "Float",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    // 3. TEST BUTTON
                                    Button(
                                        onClick = { activeAiTab = AiWorkspaceTab.TEST },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (activeAiTab == AiWorkspaceTab.TEST) Color(0xFF0D9488) else Color(0xFFE2E8F0),
                                            contentColor = if (activeAiTab == AiWorkspaceTab.TEST) Color.White else Color(0xFF1E293B)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("ai_mode_test_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Test",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Test", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }

                                    // 4. DOWNLOAD BUTTON
                                    Button(
                                        onClick = {
                                            activeAiTab = AiWorkspaceTab.DOWNLOAD
                                            onDownloadAiApk()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF16A34A),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .testTag("ai_mode_download_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Download",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Download", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }

                                // TAB CONTENT: SCREENSHOT-STYLE FLOATING PREVIEW / INTERACTIVE TEST / APK DOWNLOAD
                                when (activeAiTab) {
                                    AiWorkspaceTab.PREVIEW, AiWorkspaceTab.TEST -> {
                                        val isInteractiveTest = activeAiTab == AiWorkspaceTab.TEST
                                        Text(
                                            text = if (isInteractiveTest) {
                                                "🧪 AI Live Test Mode — Tap any widget below to test real-time file modification:"
                                            } else {
                                                "📱 AI App Visual Preview (${aiBuiltProject.overlayTitle} • Independent from Manual Mode):"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF334155)
                                        )

                                        // Screenshot-style phone frame & floating window preview
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
                                                .border(BorderStroke(2.dp, Color(0xFF334155)), RoundedCornerShape(16.dp))
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = Color(0xFF1E293B),
                                                border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                                                modifier = Modifier
                                                    .widthIn(min = 240.dp, max = 300.dp)
                                                    .testTag("ai_floating_window_preview_surface")
                                            ) {
                                                Column {
                                                    // Floating Window Title Header
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color(0xFF0288D1))
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = aiBuiltProject.overlayTitle,
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.ExtraBold
                                                        )
                                                        Text(
                                                            text = if (isInteractiveTest) "TEST ACTIVE" else "PREVIEW",
                                                            color = Color(0xFFE0F2FE),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }

                                                    // Widgets inside the AI-generated Floating Window
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        aiBuiltComponents.forEach { comp ->
                                                            val isOn = comp.currentValue == "1" || comp.currentValue.equals("true", ignoreCase = true)
                                                            when (comp.type) {
                                                                ComponentWidgetType.TEXT.name -> {
                                                                    Text(
                                                                        text = comp.label,
                                                                        color = Color(0xFF38BDF8),
                                                                        fontSize = 13.sp,
                                                                        fontWeight = FontWeight.ExtraBold
                                                                    )
                                                                }
                                                                ComponentWidgetType.TOGGLE.name -> {
                                                                    Surface(
                                                                        color = Color(0xFF0F172A),
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        border = BorderStroke(1.dp, if (isOn) Color(0xFF22C55E) else Color(0xFF475569)),
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clickable {
                                                                                onTriggerAiWidgetTest(comp, if (isOn) "0" else "1")
                                                                            }
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier
                                                                                .fillMaxWidth()
                                                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                                        ) {
                                                                            Column(modifier = Modifier.weight(1f)) {
                                                                                Text(
                                                                                    text = comp.label,
                                                                                    color = Color.White,
                                                                                    fontSize = 12.sp,
                                                                                    fontWeight = FontWeight.Bold
                                                                                )
                                                                                Text(
                                                                                    text = if (isOn) "State: ON (${comp.onPayloadHex})" else "State: OFF (${comp.offPayloadHex})",
                                                                                    color = if (isOn) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                                                                    fontSize = 10.sp
                                                                                )
                                                                            }
                                                                            Switch(
                                                                                checked = isOn,
                                                                                onCheckedChange = { checked ->
                                                                                    onTriggerAiWidgetTest(comp, if (checked) "1" else "0")
                                                                                },
                                                                                colors = SwitchDefaults.colors(
                                                                                    checkedThumbColor = Color.White,
                                                                                    checkedTrackColor = Color(0xFF16A34A)
                                                                                )
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                ComponentWidgetType.SLIDER.name -> {
                                                                    val sliderVal = (comp.currentValue.toFloatOrNull() ?: 50f)
                                                                        .coerceIn(0f, comp.sliderMax.toFloat().coerceAtLeast(1f))
                                                                    Surface(
                                                                        color = Color(0xFF0F172A),
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        border = BorderStroke(1.dp, Color(0xFF475569)),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Column(
                                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                                        ) {
                                                                            Row(
                                                                                modifier = Modifier.fillMaxWidth(),
                                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                                            ) {
                                                                                Text(
                                                                                    text = comp.label,
                                                                                    color = Color.White,
                                                                                    fontSize = 11.sp,
                                                                                    fontWeight = FontWeight.Bold
                                                                                )
                                                                                Text(
                                                                                    text = "${sliderVal.toInt()} / ${comp.sliderMax}",
                                                                                    color = Color(0xFF38BDF8),
                                                                                    fontSize = 11.sp,
                                                                                    fontWeight = FontWeight.ExtraBold
                                                                                )
                                                                            }
                                                                            Slider(
                                                                                value = sliderVal,
                                                                                onValueChange = { newV ->
                                                                                    onTriggerAiWidgetTest(comp, newV.toInt().toString())
                                                                                },
                                                                                valueRange = 0f..comp.sliderMax.toFloat().coerceAtLeast(1f),
                                                                                colors = SliderDefaults.colors(
                                                                                    thumbColor = Color(0xFF38BDF8),
                                                                                    activeTrackColor = Color(0xFF0288D1)
                                                                                )
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                ComponentWidgetType.INPUT.name -> {
                                                                    var localInputText by remember(comp.id, comp.currentValue) {
                                                                        mutableStateOf(comp.currentValue)
                                                                    }
                                                                    Surface(
                                                                        color = Color.White,
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Column(
                                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                                        ) {
                                                                            Text(
                                                                                text = comp.label,
                                                                                color = Color(0xFF0F172A),
                                                                                fontSize = 11.sp,
                                                                                fontWeight = FontWeight.Bold
                                                                            )
                                                                            OutlinedTextField(
                                                                                value = localInputText,
                                                                                onValueChange = { newVal ->
                                                                                    localInputText = newVal
                                                                                    onTriggerAiWidgetTest(comp, newVal)
                                                                                },
                                                                                singleLine = true,
                                                                                modifier = Modifier.fillMaxWidth()
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                                else -> {
                                                                    Button(
                                                                        onClick = { onTriggerAiWidgetTest(comp, "1") },
                                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    ) {
                                                                        Text(
                                                                            text = comp.label,
                                                                            color = Color.White,
                                                                            fontSize = 12.sp,
                                                                            fontWeight = FontWeight.Bold
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    AiWorkspaceTab.DOWNLOAD -> {
                                        Surface(
                                            color = Color(0xFFF0FDF4),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color(0xFF16A34A)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "📦 AI Compiled Standalone APK",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF065F46)
                                                )
                                                Text(
                                                    text = aiCompiledApkSummary ?: "Compiling signed standalone APK for '${aiBuiltProject.name}'...",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF065F46)
                                                )
                                                Button(
                                                    onClick = onInstallAiApk,
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("ai_mode_install_apk_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.InstallMobile,
                                                        contentDescription = "Install AI APK",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Install / Download '${aiBuiltProject.name}' APK",
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // BOTTOM PROMPT INPUT BAR (Starts 100% empty, no pre-filled text)
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = userPromptInput,
                        onValueChange = { userPromptInput = it },
                        placeholder = { Text("Type prompt to build floating app...") },
                        maxLines = 3,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_workspace_prompt_input")
                    )

                    Button(
                        onClick = {
                            val clean = userPromptInput.trim()
                            if (clean.isNotEmpty() && !isAiBuilding) {
                                onSendPromptToAi(clean)
                                userPromptInput = ""
                            }
                        },
                        enabled = userPromptInput.trim().isNotEmpty() && !isAiBuilding,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4F46E5),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("ai_workspace_send_prompt_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Prompt",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}
