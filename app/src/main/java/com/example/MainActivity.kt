package com.example

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CanvasComponentEntity
import com.example.ui.CompiledStandaloneAppScreen
import com.example.ui.ComponentCountSummary
import com.example.ui.ComponentTrackerBanner
import com.example.ui.KotlinProjectCodeEngine
import com.example.ui.MainViewModel
import com.example.ui.PropertyInspectorBottomDock
import com.example.ui.SketchwarePaletteEntry
import com.example.ui.SketchwareStudioSplitWorkspace
import com.example.ui.StudioDestination
import com.example.ui.StudioEditCodeDialog
import com.example.ui.StudioProjectLauncherScreen
import com.example.ui.StudioUiState
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val projects by viewModel.allProjects.collectAsStateWithLifecycle()
                val components by viewModel.activeComponents.collectAsStateWithLifecycle()
                val bundledStandaloneComponents by viewModel.bundledStandaloneComponents.collectAsStateWithLifecycle()

                when (uiState.destination) {
                    StudioDestination.COMPILED_STANDALONE_APP -> {
                        val activeProj = uiState.activeProject
                        val standaloneItems = if (uiState.isBundledStandaloneApk) {
                            bundledStandaloneComponents
                        } else {
                            components
                        }
                        if (!uiState.isBundledStandaloneApk) {
                            BackHandler {
                                viewModel.closeCompiledAppPreview()
                            }
                        }
                        if (activeProj != null) {
                            CompiledStandaloneAppScreen(
                                project = activeProj,
                                initialComponents = standaloneItems,
                                compiledPackageName = uiState.compiledAppPackageName.ifBlank { contextPackageName() },
                                isStandaloneInstalledApk = uiState.isBundledStandaloneApk,
                                onBackToStudioEditor = if (uiState.isBundledStandaloneApk) null else {
                                    { viewModel.closeCompiledAppPreview() }
                                }
                            )
                        }
                    }

                    StudioDestination.PROJECT_LAUNCHER -> {
                        StudioProjectLauncherScreen(
                            uiState = uiState,
                            projects = projects,
                            defaultPathProvider = viewModel::getDefaultTargetFilePath,
                            onOpenCreateDialog = { viewModel.openCreateProjectDialog(true) },
                            onDismissCreateDialog = { viewModel.openCreateProjectDialog(false) },
                            onCreateProject = viewModel::createNewBlankProject,
                            onOpenExistingPicker = { viewModel.openExistingProjectsPicker(true) },
                            onDismissExistingPicker = { viewModel.openExistingProjectsPicker(false) },
                            onSelectProject = viewModel::openExistingProject,
                            onDeleteProject = viewModel::deleteProject,
                            onOpenEditProjectDialog = { proj -> viewModel.openEditProjectDialog(proj) },
                            onDismissEditProjectDialog = { viewModel.openEditProjectDialog(null) },
                            onSaveProjectConfiguration = viewModel::updateProjectNameAndLogo,
                            onImportLogoUri = viewModel::importProjectLogoUri
                        )
                    }

                    StudioDestination.CANVAS_WORKSPACE -> {
                        BackHandler {
                            if (uiState.showEditFloatingPanelDialog) {
                                viewModel.openEditFloatingPanelDialog(false)
                            } else if (uiState.showEditCodeDialog) {
                                viewModel.openEditCodeDialog(false)
                            } else if (uiState.selectedComponentId != null) {
                                viewModel.selectComponent(null)
                            } else {
                                viewModel.navigateBackToLauncher()
                            }
                        }

                        val trackerSummary = remember(components) {
                            viewModel.computeComponentTrackerSummary(components)
                        }

                        StudioCanvasBuilderScreen(
                            uiState = uiState,
                            components = components,
                            trackerSummary = trackerSummary,
                            onBackToLauncher = viewModel::navigateBackToLauncher,
                            onAddPaletteEntry = { entry ->
                                viewModel.addPaletteItemToCanvas(
                                    widgetType = entry.widgetType,
                                    customPrefix = entry.title,
                                    customWidthDp = entry.customWidthDp,
                                    customHeightDp = entry.customHeightDp,
                                    customBgHex = entry.customBgHex,
                                    customTextHex = entry.customTextHex
                                )
                            },
                            onSelectComponent = viewModel::selectComponent,
                            onUpdateComponent = viewModel::updateComponent,
                            onSaveProjectDesign = viewModel::saveCurrentProjectDesign,
                            onMoveComponent = viewModel::updateComponentPosition,
                            onResizeComponent = viewModel::resizeComponent,
                            onResizeCanvas = viewModel::resizeActiveProjectCanvas,
                            onToggleAutoFixSize = viewModel::toggleAutoFixSize,
                            onOpenEditFloatingPanel = { viewModel.openEditFloatingPanelDialog(true) },
                            onDismissEditFloatingPanel = { viewModel.openEditFloatingPanelDialog(false) },
                            onSaveFloatingPanelConfig = viewModel::updateFloatingPanelNameAndLogo,
                            onImportFloatingLogoUri = viewModel::importProjectLogoUri,
                            onOpenEditCode = { viewModel.openEditCodeDialog(true) },
                            onDismissEditCode = { viewModel.openEditCodeDialog(false) },
                            onCompileCodeToVisual = { edited ->
                                viewModel.applyEditedKotlinCodeToVisualScreen(edited, andCompileApk = false)
                            },
                            onCompileCodeToApk = { edited ->
                                viewModel.applyEditedKotlinCodeToVisualScreen(edited, andCompileApk = true)
                            },
                            onDuplicateComponent = viewModel::duplicateSelectedComponent,
                            onDeleteComponent = viewModel::deleteComponent,
                            onClearCanvas = viewModel::clearEntireCanvas,
                            onPickImageForComponent = viewModel::assignPickedImageToComponent,
                            onPickSoundForComponent = viewModel::assignPickedSoundToComponent,
                            onTriggerComponentLive = viewModel::triggerComponentAction,
                            onDownloadFloatingWindow = { viewModel.downloadFloatingWindowToAndroid(null) },
                            onInstallCompiledApk = { viewModel.installCompiledApk(this@MainActivity) },
                            onRunCompiledAppPreview = { viewModel.openCompiledAppPreview() },
                            onSaveFloatingWindowToUri = viewModel::saveFloatingWindowToCustomUri,
                            onDismissDownloadDialog = viewModel::dismissDownloadSummaryDialog,
                            onLaunchSystemOverlay = viewModel::launchSystemFloatingOverlay,
                            onStopSystemOverlay = viewModel::stopSystemFloatingOverlay
                        )
                    }
                }
            }
        }
    }

    private fun contextPackageName(): String = packageName

    override fun onResume() {
        super.onResume()
        viewModel.refreshOverlayPermission()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioCanvasBuilderScreen(
    uiState: StudioUiState,
    components: List<CanvasComponentEntity>,
    trackerSummary: ComponentCountSummary,
    onBackToLauncher: () -> Unit,
    onAddPaletteEntry: (SketchwarePaletteEntry) -> Unit,
    onSelectComponent: (Long?) -> Unit,
    onUpdateComponent: (CanvasComponentEntity) -> Unit,
    onSaveProjectDesign: (CanvasComponentEntity?) -> Unit = {},
    onMoveComponent: (CanvasComponentEntity, Int, Int) -> Unit,
    onResizeComponent: (CanvasComponentEntity, Int, Int) -> Unit,
    onResizeCanvas: (Int, Int) -> Unit,
    onToggleAutoFixSize: () -> Unit,
    onOpenEditFloatingPanel: () -> Unit = {},
    onDismissEditFloatingPanel: () -> Unit = {},
    onSaveFloatingPanelConfig: (String, String) -> Unit = { _, _ -> },
    onImportFloatingLogoUri: (Uri, (String) -> Unit) -> Unit = { _, _ -> },
    onOpenEditCode: () -> Unit = {},
    onDismissEditCode: () -> Unit = {},
    onCompileCodeToVisual: (Map<String, String>) -> Unit = {},
    onCompileCodeToApk: (Map<String, String>) -> Unit = {},
    onDuplicateComponent: (CanvasComponentEntity) -> Unit,
    onDeleteComponent: (Long) -> Unit,
    onClearCanvas: () -> Unit,
    onPickImageForComponent: (CanvasComponentEntity, Uri) -> Unit,
    onPickSoundForComponent: (CanvasComponentEntity, Uri, Boolean) -> Unit,
    onTriggerComponentLive: (CanvasComponentEntity, String?) -> Unit,
    onDownloadFloatingWindow: () -> Unit,
    onInstallCompiledApk: () -> Unit = {},
    onRunCompiledAppPreview: () -> Unit = {},
    onSaveFloatingWindowToUri: (Uri) -> Unit,
    onDismissDownloadDialog: () -> Unit,
    onLaunchSystemOverlay: () -> Unit,
    onStopSystemOverlay: () -> Unit
) {
    val context = LocalContext.current
    val project = uiState.activeProject ?: return
    val selectedComponent = remember(components, uiState.selectedComponentId) {
        components.find { it.id == uiState.selectedComponentId }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        if (uri != null) {
            onSaveFloatingWindowToUri(uri)
        }
    }

    if (uiState.showEditFloatingPanelDialog) {
        var editedPanelTitle by remember(project.id, project.overlayTitle) {
            mutableStateOf(project.overlayTitle)
        }
        var editedFloatingLogoPath by remember(project.id, project.floatingLogoPath) {
            mutableStateOf(project.floatingLogoPath)
        }

        val floatingLogoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                onImportFloatingLogoUri(uri) { savedPath ->
                    editedFloatingLogoPath = savedPath
                }
            }
        }

        val previewGoalBitmap = remember(editedFloatingLogoPath) {
            if (editedFloatingLogoPath.isNotBlank()) {
                val f = File(editedFloatingLogoPath)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
            } else null
        }

        AlertDialog(
            onDismissRequest = onDismissEditFloatingPanel,
            title = {
                Text(
                    text = "Edit Floating Panel Name & Goal Logo",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "When you click '✕' on the floating window, it transforms into a round ('Goal') floating bubble. Set a Goal Logo below, or leave the logo empty to display the Floating Window Name inside the round bubble.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF475569)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB))
                                .border(BorderStroke(2.dp, Color(0xFF0288D1)), CircleShape)
                                .clickable {
                                    floatingLogoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("floating_panel_goal_logo_preview"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewGoalBitmap != null) {
                                Image(
                                    bitmap = previewGoalBitmap,
                                    contentDescription = "Selected Floating Goal Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Text(
                                    text = editedPanelTitle.trim().ifEmpty {
                                        project.name.trim().ifEmpty { "Float" }
                                    },
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    floatingLogoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pick_floating_goal_logo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Choose Floating Goal Logo",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select Goal Logo", fontSize = 12.sp)
                            }

                            if (editedFloatingLogoPath.isNotBlank()) {
                                TextButton(
                                    onClick = { editedFloatingLogoPath = "" },
                                    modifier = Modifier.testTag("remove_floating_goal_logo_button")
                                ) {
                                    Text(
                                        text = "Remove Logo (Show Window Name)",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editedPanelTitle,
                        onValueChange = { editedPanelTitle = it },
                        label = { Text("Floating Panel Name (Window Title)") },
                        placeholder = { Text("Enter floating window name...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_floating_panel_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveFloatingPanelConfig(editedPanelTitle, editedFloatingLogoPath)
                    },
                    modifier = Modifier.testTag("save_floating_panel_config_button")
                ) {
                    Text("Save Floating Panel", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissEditFloatingPanel) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showEditCodeDialog) {
        val initialFiles = remember(project, components, uiState.customEditedKotlinFiles) {
            if (uiState.customEditedKotlinFiles.isNotEmpty()) {
                uiState.customEditedKotlinFiles
            } else {
                KotlinProjectCodeEngine.generateKotlinFilesForProject(project, components)
            }
        }
        StudioEditCodeDialog(
            project = project,
            components = components,
            initialFiles = initialFiles,
            onDismiss = onDismissEditCode,
            onCompileCodeToVisualScreen = onCompileCodeToVisual,
            onCompileCodeToApk = onCompileCodeToApk
        )
    }

    if (uiState.isBuildingApk) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "Compiling '${project.name}' APK...",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = uiState.buildProgressStepText.ifBlank {
                            "Compiling your visual screen widgets and signing standalone Android APK..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF0288D1),
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = uiState.rawJavaBuildPreview,
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (uiState.downloadedFileSummary != null) {
        AlertDialog(
            onDismissRequest = onDismissDownloadDialog,
            title = {
                Text(
                    text = "Compiled '${project.name}' APK Ready",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFF22C55E)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = uiState.downloadedFileSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF065F46),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    if (uiState.rawJavaBuildPreview.isNotBlank()) {
                        Text(
                            text = "Compiled Kotlin Widget Code (${components.size} widget(s)):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 130.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = uiState.rawJavaBuildPreview,
                                    color = Color(0xFF4ADE80),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onInstallCompiledApk,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("install_compiled_apk_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.InstallMobile,
                                contentDescription = "Install Compiled APK",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Install '${project.name}' APK", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onRunCompiledAppPreview,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_compiled_app_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run Compiled App",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Compiled '${project.name}' App", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                createDocumentLauncher.launch(uiState.downloadedFileName)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_compiled_apk_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save APK to Custom Folder",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save APK to Folder...", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissDownloadDialog) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFFE2E8F0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onBackToLauncher,
                        modifier = Modifier.testTag("back_to_launcher_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Project Launcher",
                            tint = Color.White
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${project.overlayTitle} • ${project.canvasWidthDp}×${project.canvasHeightDp} dp",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.88f)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { onSaveProjectDesign(selectedComponent) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF16A34A),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .testTag("save_project_design_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save Design",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Save",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onOpenEditCode,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0F172A),
                            contentColor = Color(0xFF38BDF8)
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .testTag("edit_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Edit Kotlin Widget Code",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Code",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDownloadFloatingWindow,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00C853),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .testTag("download_floating_window_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Build APK from Raw Java",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Build",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            if (uiState.isSystemOverlayRunning) onStopSystemOverlay()
                            else onLaunchSystemOverlay()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isSystemOverlayRunning)
                                Color(0xFFEF4444)
                            else Color(0xFF1E293B)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .testTag("run_floating_overlay_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isSystemOverlayRunning) Icons.Default.Stop else Icons.Default.Layers,
                            contentDescription = "Launch Floating Overlay",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (uiState.isSystemOverlayRunning) "Stop" else "Float",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0288D1)
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedComponent != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                if (selectedComponent != null) {
                    PropertyInspectorBottomDock(
                        component = selectedComponent,
                        isAutoFixSize = project.autoFixSize,
                        onToggleAutoFixSize = onToggleAutoFixSize,
                        onOpenEditCode = onOpenEditCode,
                        onUpdateComponent = onUpdateComponent,
                        onSaveDesign = { edited -> onSaveProjectDesign(edited) },
                        onPickImageUri = { uri -> onPickImageForComponent(selectedComponent, uri) },
                        onPickSoundUri = { uri, isOff -> onPickSoundForComponent(selectedComponent, uri, isOff) },
                        onDuplicateComponent = { onDuplicateComponent(selectedComponent) },
                        onDeleteComponent = { onDeleteComponent(selectedComponent.id) },
                        onTestTriggerWrite = { editedComp -> onTriggerComponentLive(editedComp, null) },
                        onCloseDock = { onSelectComponent(null) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top bar showing only user-added widgets; clicking any widget opens its Edit Mode
            ComponentTrackerBanner(
                summary = trackerSummary,
                components = components,
                selectedComponentId = uiState.selectedComponentId,
                onSelectComponentForEdit = { compId ->
                    onSelectComponent(compId)
                }
            )

            // SKETCHWARE SPLIT IDE WORKSPACE (Left Vertical Palette + Right Android Phone Frame)
            SketchwareStudioSplitWorkspace(
                project = project,
                components = components,
                selectedComponentId = uiState.selectedComponentId,
                isLivePreviewMode = uiState.isLivePreviewMode,
                statusToast = uiState.statusToast,
                onAddPaletteEntry = onAddPaletteEntry,
                onSelectComponent = onSelectComponent,
                onMoveComponent = onMoveComponent,
                onResizeComponent = onResizeComponent,
                onResizeCanvas = onResizeCanvas,
                onToggleAutoFixSize = onToggleAutoFixSize,
                onOpenEditFloatingPanel = onOpenEditFloatingPanel,
                onSaveDesign = { onSaveProjectDesign(selectedComponent) },
                onTriggerComponentLive = onTriggerComponentLive,
                onClearCanvas = onClearCanvas,
                modifier = Modifier.weight(1f)
            )

            if (!uiState.hasOverlayPermission && selectedComponent == null) {
                Surface(
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Grant Overlay Permission to float your custom window over other Android apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF475569),
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Overlay Permission",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
