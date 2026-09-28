package com.example.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.blueprint.ApkCompilationEngine
import com.example.data.StudioProjectEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun generateSuggestedPackageId(appName: String, projectName: String): String {
    val base = appName.ifBlank { projectName }
        .trim()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "")
        .let { if (it.isNotEmpty() && !it[0].isLetter()) "app$it" else it }
        .ifEmpty { "newproject1" }
    return "com.my.$base"
}

/**
 * REQUIREMENT 1: PROJECT LAUNCHER (FIRST SCREEN OF STUDIO ERROR)
 * - Displays "Create New Project", "Select Existing Project", and the "Saved Projects" list.
 * - Includes full Android Studio & Sketchware project configuration fields:
 *   App Logo, Application Name, Package Name (Package ID / applicationId), Project Name,
 *   Version Code, Version Name, Minimum SDK, Target SDK, and Window Title.
 * - Each Saved Project item has a Pencil Icon (Edit) so the user can configure or change
 *   the App Logo, App Name, Package ID, Version, and SDK settings at any time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioProjectLauncherScreen(
    uiState: StudioUiState,
    projects: List<StudioProjectEntity>,
    defaultPathProvider: (String) -> String,
    onOpenCreateDialog: () -> Unit,
    onDismissCreateDialog: () -> Unit,
    onCreateProject: (
        name: String,
        packageName: String,
        projectName: String,
        overlayTitle: String,
        canvasWidthDp: Int,
        canvasHeightDp: Int,
        targetFilePath: String,
        appLogoPath: String,
        versionCode: Int,
        versionName: String,
        minSdk: Int,
        targetSdk: Int
    ) -> Unit,
    onOpenExistingPicker: () -> Unit,
    onDismissExistingPicker: () -> Unit,
    onSelectProject: (StudioProjectEntity) -> Unit,
    onDeleteProject: (Long) -> Unit,
    onOpenEditProjectDialog: (StudioProjectEntity) -> Unit,
    onDismissEditProjectDialog: () -> Unit,
    onSaveProjectConfiguration: (
        project: StudioProjectEntity,
        newName: String,
        newPackageName: String,
        newProjectName: String,
        newOverlayTitle: String,
        newLogoPath: String,
        newVersionCode: Int,
        newVersionName: String,
        newMinSdk: Int,
        newTargetSdk: Int
    ) -> Unit,
    onImportLogoUri: (android.net.Uri, (String) -> Unit) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = "Studio Error",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.launcher_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = stringResource(R.string.launcher_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("project_launcher_card"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Build Custom Android Apps",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Configure App Icon, Application Name, Package ID (com.company.app), Project Name, Version Code/Name, and SDK levels — just like Android Studio & Sketchware. Tap the pencil icon (✏️) on any saved project to edit its configuration.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // CHOICE 1: Create New Project
                            Button(
                                onClick = onOpenCreateDialog,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("create_new_project_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = stringResource(R.string.btn_create_new_project),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.btn_create_new_project),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // CHOICE 2: Select Existing Project
                            OutlinedButton(
                                onClick = onOpenExistingPicker,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("select_existing_project_button"),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = stringResource(R.string.btn_select_existing_project),
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${stringResource(R.string.btn_select_existing_project)} (${projects.size})",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                if (projects.isNotEmpty()) {
                    item {
                        Text(
                            text = "Saved Projects (${projects.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    items(projects, key = { it.id }) { project ->
                        val logoBitmap = remember(project.appLogoPath) {
                            if (project.appLogoPath.isNotBlank()) {
                                val file = File(project.appLogoPath)
                                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
                            } else null
                        }
                        val resolvedPkg = remember(project.packageName, project.name, project.id) {
                            project.packageName.ifBlank { ApkCompilationEngine.getCompiledAppPackageName(project) }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProject(project) }
                                .testTag("project_item_${project.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // App Logo Box (Empty when no logo has been set)
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Transparent)
                                        .border(
                                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onOpenEditProjectDialog(project) }
                                        .testTag("project_logo_box_${project.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (logoBitmap != null) {
                                        Image(
                                            bitmap = logoBitmap,
                                            contentDescription = "App Logo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = "No\nLogo",
                                            fontSize = 9.sp,
                                            lineHeight = 11.sp,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    if (project.name.isNotBlank()) {
                                        Text(
                                            text = project.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = "No App Name (Tap ✏️ to configure)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontStyle = FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Package ID: $resolvedPkg",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = buildString {
                                            if (project.projectName.isNotBlank()) {
                                                append("Project: ${project.projectName} • ")
                                            }
                                            append("v${project.versionName} (${project.versionCode}) • SDK ${project.minSdk}–${project.targetSdk}")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${project.canvasWidthDp}×${project.canvasHeightDp} dp • ${dateFormat.format(Date(project.updatedAt))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // PENCIL ICON: Edit App Logo, App Name, Package ID, Project Name, Version & SDK
                                IconButton(
                                    onClick = { onOpenEditProjectDialog(project) },
                                    modifier = Modifier.testTag("edit_project_${project.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit App Configuration (Logo, Name, Package ID, Version, SDK)",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteProject(project.id) },
                                    modifier = Modifier.testTag("delete_project_${project.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Project",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // PENCIL ICON DIALOG: Edit App Logo, App Name, Package ID, Project Name, Version Code/Name & SDK
    val editingProject = uiState.editingProject
    if (editingProject != null) {
        var editedName by remember(editingProject.id) { mutableStateOf(editingProject.name) }
        var editedPackageName by remember(editingProject.id) { mutableStateOf(editingProject.packageName) }
        var editedProjectName by remember(editingProject.id) { mutableStateOf(editingProject.projectName) }
        var editedOverlayTitle by remember(editingProject.id) { mutableStateOf(editingProject.overlayTitle) }
        var editedLogoPath by remember(editingProject.id) { mutableStateOf(editingProject.appLogoPath) }
        var editedVersionCode by remember(editingProject.id) { mutableStateOf(editingProject.versionCode.toString()) }
        var editedVersionName by remember(editingProject.id) { mutableStateOf(editingProject.versionName) }
        var editedMinSdk by remember(editingProject.id) { mutableStateOf(editingProject.minSdk.toString()) }
        var editedTargetSdk by remember(editingProject.id) { mutableStateOf(editingProject.targetSdk.toString()) }

        val logoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                onImportLogoUri(uri) { savedPath ->
                    editedLogoPath = savedPath
                }
            }
        }

        val previewBitmap = remember(editedLogoPath) {
            if (editedLogoPath.isNotBlank()) {
                val f = File(editedLogoPath)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
            } else null
        }

        AlertDialog(
            onDismissRequest = onDismissEditProjectDialog,
            title = { Text("Edit App & Project Settings", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. App Icon / Logo selector
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.Transparent)
                                .border(
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    logoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("edit_dialog_logo_preview"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap,
                                    contentDescription = "Selected App Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "No\nLogo",
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    logoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pick_app_logo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Choose Logo",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select App Logo")
                            }

                            if (editedLogoPath.isNotBlank()) {
                                TextButton(
                                    onClick = { editedLogoPath = "" },
                                    modifier = Modifier.testTag("clear_app_logo_button")
                                ) {
                                    Text("Remove Logo", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // 2. Application Name (App Name)
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Application Name (App Name)") },
                        placeholder = { Text("Enter application name...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_project_name_input")
                    )

                    // 3. Package Name / Package ID (applicationId) + Auto-generate button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = editedPackageName,
                            onValueChange = { editedPackageName = it },
                            label = { Text("Package Name / Package ID") },
                            placeholder = { Text("e.g. com.my.newproject") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("edit_project_package_input")
                        )
                        OutlinedButton(
                            onClick = {
                                editedPackageName = generateSuggestedPackageId(editedName, editedProjectName)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Auto ID", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 4. Project Name (Workspace Name)
                    OutlinedTextField(
                        value = editedProjectName,
                        onValueChange = { editedProjectName = it },
                        label = { Text("Project Name (Workspace)") },
                        placeholder = { Text("e.g. NewProject1") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_workspace_project_name_input")
                    )

                    // 5. Version Code & Version Name
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editedVersionCode,
                            onValueChange = { editedVersionCode = it },
                            label = { Text("Version Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("edit_version_code_input")
                        )
                        OutlinedTextField(
                            value = editedVersionName,
                            onValueChange = { editedVersionName = it },
                            label = { Text("Version Name") },
                            placeholder = { Text("1.0") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("edit_version_name_input")
                        )
                    }

                    // 6. Minimum SDK & Target SDK
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editedMinSdk,
                            onValueChange = { editedMinSdk = it },
                            label = { Text("Minimum SDK") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("edit_min_sdk_input")
                        )
                        OutlinedTextField(
                            value = editedTargetSdk,
                            onValueChange = { editedTargetSdk = it },
                            label = { Text("Target SDK") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("edit_target_sdk_input")
                        )
                    }

                    // 7. Optional Window Title
                    OutlinedTextField(
                        value = editedOverlayTitle,
                        onValueChange = { editedOverlayTitle = it },
                        label = { Text("Floating Window Title (Optional)") },
                        placeholder = { Text("Leave empty for blank header...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_project_overlay_title_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveProjectConfiguration(
                            editingProject,
                            editedName,
                            editedPackageName,
                            editedProjectName,
                            editedOverlayTitle,
                            editedLogoPath,
                            editedVersionCode.toIntOrNull() ?: 1,
                            editedVersionName.ifBlank { "1.0" },
                            editedMinSdk.toIntOrNull() ?: 24,
                            editedTargetSdk.toIntOrNull() ?: 36
                        )
                    },
                    modifier = Modifier.testTag("save_project_name_logo_button")
                ) {
                    Text("Save Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissEditProjectDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    // CREATE NEW PROJECT DIALOG: Full Android Studio & Sketchware Configuration (No pre-filled Name or Logo)
    if (uiState.showCreateProjectDialog) {
        var appName by remember { mutableStateOf("") }
        var packageName by remember { mutableStateOf("") }
        var workspaceProjectName by remember { mutableStateOf("") }
        var overlayTitle by remember { mutableStateOf("") }
        var appLogoPath by remember { mutableStateOf("") }
        var versionCode by remember { mutableStateOf("1") }
        var versionName by remember { mutableStateOf("1.0") }
        var minSdk by remember { mutableStateOf("24") }
        var targetSdk by remember { mutableStateOf("36") }
        var canvasWidth by remember { mutableStateOf("310") }
        var canvasHeight by remember { mutableStateOf("380") }
        var targetFilePath by remember { mutableStateOf(defaultPathProvider("")) }

        val createLogoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                onImportLogoUri(uri) { savedPath ->
                    appLogoPath = savedPath
                }
            }
        }

        val createLogoBitmap = remember(appLogoPath) {
            if (appLogoPath.isNotBlank()) {
                val f = File(appLogoPath)
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
            } else null
        }

        AlertDialog(
            onDismissRequest = onDismissCreateDialog,
            title = { Text("New Android Project (Studio & Sketchware)", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Configure App Icon, Application Name, Package ID (applicationId), Project Name, Version, and SDK. App Name, Logo, and Background start 100% empty.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1. App Icon / Logo Selector (starts empty)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    createLogoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (createLogoBitmap != null) {
                                Image(
                                    bitmap = createLogoBitmap,
                                    contentDescription = "App Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "No\nLogo",
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                createLogoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Pick App Logo",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (appLogoPath.isBlank()) "Select App Icon / Logo" else "Change App Logo")
                        }
                    }

                    // 2. Application Name
                    OutlinedTextField(
                        value = appName,
                        onValueChange = {
                            appName = it
                            targetFilePath = defaultPathProvider(it.ifBlank { workspaceProjectName })
                        },
                        label = { Text("Application Name (App Name)") },
                        placeholder = { Text("Enter Application Name...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_project_name_input")
                    )

                    // 3. Package Name / Package ID (applicationId)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it },
                            label = { Text("Package Name / Package ID") },
                            placeholder = { Text("e.g. com.my.newproject1") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("new_project_package_input")
                        )
                        OutlinedButton(
                            onClick = {
                                packageName = generateSuggestedPackageId(appName, workspaceProjectName)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Auto ID", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 4. Project Name (Workspace Name)
                    OutlinedTextField(
                        value = workspaceProjectName,
                        onValueChange = { workspaceProjectName = it },
                        label = { Text("Project Name (Workspace)") },
                        placeholder = { Text("e.g. NewProject1") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_workspace_project_name_input")
                    )

                    // 5. Version Code & Version Name
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = versionCode,
                            onValueChange = { versionCode = it },
                            label = { Text("Version Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = versionName,
                            onValueChange = { versionName = it },
                            label = { Text("Version Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 6. Minimum SDK & Target SDK
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minSdk,
                            onValueChange = { minSdk = it },
                            label = { Text("Minimum SDK") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = targetSdk,
                            onValueChange = { targetSdk = it },
                            label = { Text("Target SDK") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 7. Canvas Width & Height
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = canvasWidth,
                            onValueChange = { canvasWidth = it },
                            label = { Text("Width (dp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = canvasHeight,
                            onValueChange = { canvasHeight = it },
                            label = { Text("Height (dp)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 8. Optional Floating Window Title
                    OutlinedTextField(
                        value = overlayTitle,
                        onValueChange = { overlayTitle = it },
                        label = { Text("Window Title (Optional)") },
                        placeholder = { Text("Leave empty for blank header") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = targetFilePath,
                        onValueChange = { targetFilePath = it },
                        label = { Text("Default Target File Path") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateProject(
                            appName,
                            packageName,
                            workspaceProjectName,
                            overlayTitle,
                            canvasWidth.toIntOrNull() ?: 310,
                            canvasHeight.toIntOrNull() ?: 380,
                            targetFilePath,
                            appLogoPath,
                            versionCode.toIntOrNull() ?: 1,
                            versionName.ifBlank { "1.0" },
                            minSdk.toIntOrNull() ?: 24,
                            targetSdk.toIntOrNull() ?: 36
                        )
                    },
                    modifier = Modifier.testTag("confirm_create_project_button")
                ) {
                    Text("Create Project", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCreateDialog) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showExistingProjectsPicker) {
        AlertDialog(
            onDismissRequest = onDismissExistingPicker,
            title = { Text("Select Existing Project", fontWeight = FontWeight.Bold) },
            text = {
                if (projects.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "No existing projects found.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap 'Create New Project' to create your first blank floating canvas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        projects.forEach { proj ->
                            val resolvedPkg = proj.packageName.ifBlank {
                                ApkCompilationEngine.getCompiledAppPackageName(proj)
                            }
                            Surface(
                                onClick = { onSelectProject(proj) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = proj.name.ifBlank { "No App Name (Tap ✏️)" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "$resolvedPkg • v${proj.versionName} (${proj.versionCode})",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            onDismissExistingPicker()
                                            onOpenEditProjectDialog(proj)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit App Configuration",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (projects.isEmpty()) {
                    Button(
                        onClick = {
                            onDismissExistingPicker()
                            onOpenCreateDialog()
                        }
                    ) {
                        Text("Create New Project")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissExistingPicker) {
                    Text("Close")
                }
            }
        )
    }
}
