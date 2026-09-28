package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.blueprint.ApkCompilationEngine
import com.example.data.AppDatabase
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import com.example.data.ConfigAuditRepository
import com.example.data.ConfigWriteAuditEntity
import com.example.data.StudioProjectEntity
import com.example.engine.ConfigParameterSpec
import com.example.engine.LocalConfigStateWriter
import com.example.engine.SoundTriggerPlayer
import com.example.service.DynamicOverlayRegistry
import com.example.service.FloatingDashboardService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

enum class StudioDestination {
    PROJECT_LAUNCHER,
    CANVAS_WORKSPACE,
    COMPILED_STANDALONE_APP
}

data class ComponentCountSummary(
    val totalCount: Int = 0,
    val buttonCount: Int = 0,
    val toggleCount: Int = 0,
    val sliderCount: Int = 0,
    val textCount: Int = 0,
    val inputCount: Int = 0,
    val imageCount: Int = 0
)

data class StudioUiState(
    val destination: StudioDestination = StudioDestination.PROJECT_LAUNCHER,
    val isBundledStandaloneApk: Boolean = false,
    val activeProject: StudioProjectEntity? = null,
    val selectedComponentId: Long? = null,
    val isLivePreviewMode: Boolean = false,
    val isSystemOverlayRunning: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val showExistingProjectsPicker: Boolean = false,
    val showCreateProjectDialog: Boolean = false,
    val editingProject: StudioProjectEntity? = null,
    val showEditFloatingPanelDialog: Boolean = false,
    val showEditCodeDialog: Boolean = false,
    val customEditedKotlinFiles: Map<String, String> = emptyMap(),
    val isBuildingApk: Boolean = false,
    val buildProgressStepText: String = "",
    val rawJavaBuildPreview: String = "",
    val compiledApkFilePath: String? = null,
    val compiledAppPackageName: String = "",
    val compiledAppName: String = "",
    val downloadedFileSummary: String? = null,
    val downloadedFileName: String = "floating_window.apk",
    val statusToast: String = "Welcome to Studio Error — Create or select a project to begin."
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val studioDao = db.studioDao()
    private val auditRepo = ConfigAuditRepository(db.configAuditDao())
    private val stateWriter = LocalConfigStateWriter.getInstance()

    private val _uiState = MutableStateFlow(
        StudioUiState(
            hasOverlayPermission = Settings.canDrawOverlays(appContext)
        )
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val activeProjectIdFlow = MutableStateFlow<Long?>(null)

    val allProjects: StateFlow<List<StudioProjectEntity>> = studioDao.observeAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeComponents: StateFlow<List<CanvasComponentEntity>> = activeProjectIdFlow
        .flatMapLatest { projectId ->
            if (projectId == null) flowOf(emptyList())
            else studioDao.observeComponentsForProject(projectId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAuditLogs: StateFlow<List<ConfigWriteAuditEntity>> = auditRepo.recentAudits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val writeListener = object : LocalConfigStateWriter.OnStateWriteListener {
        override fun onWriteSuccess(
            parameterKey: String,
            byteOffset: Int,
            previousValue: String,
            newValue: String,
            durationMicros: Long,
            updatedSnapshot: ConfigParameterSpec.StateSnapshot
        ) {
            val offsetHex = String.format(Locale.US, "0x%02X", byteOffset)
            val fileName = File(updatedSnapshot.targetFilePath).name
            val stateLabel = when {
                newValue.equals("0x00", ignoreCase = true) || newValue == "0" || newValue.equals("false", ignoreCase = true) -> "OFF (0)"
                newValue.equals("0x01", ignoreCase = true) || newValue == "1" || newValue.equals("true", ignoreCase = true) -> "ON (1)"
                else -> newValue
            }
            _uiState.update {
                it.copy(
                    statusToast = "$parameterKey → $stateLabel [Wrote $newValue @$offsetHex → $fileName]"
                )
            }
            viewModelScope.launch {
                auditRepo.recordWrite(
                    ConfigWriteAuditEntity(
                        parameterKey = parameterKey,
                        byteOffsetHex = offsetHex,
                        previousValue = previousValue,
                        newValue = newValue,
                        durationMicros = durationMicros,
                        crc32Hex = String.format(Locale.US, "0x%08X", updatedSnapshot.crc32Value),
                        targetFilePath = updatedSnapshot.targetFilePath
                    )
                )
            }
        }

        override fun onWriteError(parameterKey: String, errorMessage: String) {
            _uiState.update {
                it.copy(statusToast = "Write Error ($parameterKey): $errorMessage")
            }
        }
    }

    private val _bundledStandaloneComponents = MutableStateFlow<List<CanvasComponentEntity>>(emptyList())
    val bundledStandaloneComponents: StateFlow<List<CanvasComponentEntity>> = _bundledStandaloneComponents.asStateFlow()

    init {
        stateWriter.addListener(writeListener)
        if (DynamicOverlayRegistry.isBundledStandaloneApk(appContext)) {
            DynamicOverlayRegistry.loadFromBundledAssetsIfEmpty(appContext)
            val projName = DynamicOverlayRegistry.getActiveProjectName()
            val pkgName = DynamicOverlayRegistry.getActivePackageName()
            val standaloneProject = StudioProjectEntity(
                id = 1L,
                name = projName,
                packageName = pkgName,
                overlayTitle = DynamicOverlayRegistry.getActiveOverlayTitle(),
                appLogoPath = DynamicOverlayRegistry.getActiveAppLogoPath(),
                floatingLogoPath = DynamicOverlayRegistry.getActiveFloatingLogoPath(),
                canvasWidthDp = DynamicOverlayRegistry.getActiveCanvasWidthDp(),
                canvasHeightDp = DynamicOverlayRegistry.getActiveCanvasHeightDp(),
                canvasBgColorHex = DynamicOverlayRegistry.getActiveCanvasBgHex(),
                autoFixSize = DynamicOverlayRegistry.isActiveAutoFixSize(),
                defaultTargetFilePath = getDefaultTargetFilePath(projName)
            )
            val standaloneItems = DynamicOverlayRegistry.getActiveItems().map { spec ->
                CanvasComponentEntity(
                    id = spec.id,
                    projectId = 1L,
                    type = spec.type ?: ComponentWidgetType.BUTTON.name,
                    label = spec.label ?: "Widget",
                    posXDp = spec.posXDp,
                    posYDp = spec.posYDp,
                    widthDp = spec.widthDp,
                    heightDp = spec.heightDp,
                    bgColorHex = spec.bgColorHex ?: "#FFFFFF",
                    textColorHex = spec.textColorHex ?: "#0F172A",
                    customImagePath = spec.customImagePath ?: "",
                    soundTrigger = spec.soundTrigger ?: "NONE",
                    customSoundPath = spec.customSoundPath ?: "",
                    offSoundTrigger = spec.offSoundTrigger ?: "NONE",
                    offCustomSoundPath = spec.offCustomSoundPath ?: "",
                    targetFilePath = spec.targetFilePath ?: "",
                    byteOffsetHex = spec.byteOffsetHex ?: "0x04",
                    onPayloadHex = spec.onPayloadHex ?: "0x01",
                    offPayloadHex = spec.offPayloadHex ?: "0x00",
                    sliderMax = spec.sliderMax,
                    currentValue = spec.currentValue ?: "0",
                    linkUrl = spec.linkUrl ?: ""
                )
            }
            _bundledStandaloneComponents.value = standaloneItems
            _uiState.update {
                it.copy(
                    destination = StudioDestination.COMPILED_STANDALONE_APP,
                    isBundledStandaloneApk = true,
                    activeProject = standaloneProject,
                    compiledAppPackageName = pkgName,
                    compiledAppName = projName,
                    statusToast = "Running compiled app '$projName' (${standaloneItems.size} widgets)"
                )
            }
        }
    }

    override fun onCleared() {
        stateWriter.removeListener(writeListener)
        super.onCleared()
    }

    fun getDefaultTargetFilePath(projectName: String): String {
        val safeSlug = projectName.trim().lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "custom_overlay" }
        return File(appContext.filesDir, "${safeSlug}_state.bin").absolutePath
    }

    fun openCreateProjectDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateProjectDialog = show) }
    }

    fun openExistingProjectsPicker(show: Boolean) {
        _uiState.update { it.copy(showExistingProjectsPicker = show) }
    }

    fun openEditProjectDialog(project: StudioProjectEntity?) {
        _uiState.update { it.copy(editingProject = project) }
    }

    fun openEditFloatingPanelDialog(show: Boolean) {
        _uiState.update { it.copy(showEditFloatingPanelDialog = show) }
    }

    /**
     * Updates the active project's Floating Panel Name (overlayTitle) and Floating Goal Logo (floatingLogoPath)
     * directly from the Studio Workspace editor.
     */
    fun updateFloatingPanelNameAndLogo(
        newOverlayTitle: String,
        newFloatingLogoPath: String
    ) {
        val currentProject = _uiState.value.activeProject ?: return
        val cleanTitle = newOverlayTitle.trim()
        val cleanLogo = newFloatingLogoPath.trim()
        viewModelScope.launch {
            val updated = currentProject.copy(
                overlayTitle = cleanTitle,
                floatingLogoPath = cleanLogo,
                updatedAt = System.currentTimeMillis()
            )
            studioDao.updateProject(updated)
            _uiState.update { state ->
                state.copy(
                    activeProject = updated,
                    showEditFloatingPanelDialog = false,
                    statusToast = "Updated Floating Panel Name & Goal Logo."
                )
            }
        }
    }

    /**
     * Copies a user-picked App Logo image from the Android Photo Picker into local app storage
     * and invokes [onResult] with its absolute file path.
     */
    fun importProjectLogoUri(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val destPath = withContext(Dispatchers.IO) {
                    val logoDir = File(appContext.filesDir, "project_logos").apply { mkdirs() }
                    val destFile = File(logoDir, "app_logo_${System.currentTimeMillis()}.png")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    destFile.absolutePath
                }
                onResult(destPath)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusToast = "Could not load selected logo: ${e.message}")
                }
            }
        }
    }

    /**
     * Updates a saved project's complete Android Studio / Sketchware configuration
     * (App Name, Package ID, Project Name, App Logo, Version Code, Version Name, Min/Target SDK, Window Title)
     * from the Home Screen Pencil Icon dialog.
     */
    fun updateProjectNameAndLogo(
        project: StudioProjectEntity,
        newName: String,
        newPackageName: String,
        newProjectName: String,
        newOverlayTitle: String,
        newLogoPath: String,
        newVersionCode: Int = project.versionCode,
        newVersionName: String = project.versionName,
        newMinSdk: Int = project.minSdk,
        newTargetSdk: Int = project.targetSdk,
        newFloatingLogoPath: String = project.floatingLogoPath
    ) {
        val cleanName = newName.trim()
        val cleanPkg = newPackageName.trim().lowercase(Locale.US)
        val cleanProjName = newProjectName.trim()
        val cleanTitle = newOverlayTitle.trim()
        val cleanLogo = newLogoPath.trim()
        val cleanFloatLogo = newFloatingLogoPath.trim()
        val safeMinSdk = newMinSdk.coerceIn(21, 36)
        val safeTargetSdk = newTargetSdk.coerceIn(safeMinSdk, 36)
        val safeVerCode = newVersionCode.coerceAtLeast(1)
        val safeVerName = newVersionName.trim().ifEmpty { "1.0" }

        viewModelScope.launch {
            val updated = project.copy(
                name = cleanName,
                packageName = cleanPkg,
                projectName = cleanProjName,
                overlayTitle = cleanTitle,
                appLogoPath = cleanLogo,
                floatingLogoPath = cleanFloatLogo,
                versionCode = safeVerCode,
                versionName = safeVerName,
                minSdk = safeMinSdk,
                targetSdk = safeTargetSdk,
                updatedAt = System.currentTimeMillis()
            )
            studioDao.updateProject(updated)
            _uiState.update { state ->
                state.copy(
                    editingProject = null,
                    activeProject = if (state.activeProject?.id == updated.id) updated else state.activeProject,
                    statusToast = "Saved App Config (Name, Package ID, Logo, Version & SDK)."
                )
            }
        }
    }

    /**
     * Creates a new project with NO pre-filled App Name, NO pre-filled Logo, and a 100% empty background,
     * while supporting full Android Studio & Sketchware configuration (Package ID, Project Name, Version Code/Name, SDK).
     */
    fun createNewBlankProject(
        name: String,
        packageName: String,
        projectName: String,
        overlayTitle: String,
        canvasWidthDp: Int,
        canvasHeightDp: Int,
        targetFilePath: String,
        appLogoPath: String = "",
        versionCode: Int = 1,
        versionName: String = "1.0",
        minSdk: Int = 24,
        targetSdk: Int = 36
    ) {
        val cleanName = name.trim()
        val cleanPkg = packageName.trim().lowercase(Locale.US)
        val cleanProjName = projectName.trim()
        val cleanTitle = overlayTitle.trim()
        val cleanLogo = appLogoPath.trim()
        val safeMinSdk = minSdk.coerceIn(21, 36)
        val safeTargetSdk = targetSdk.coerceIn(safeMinSdk, 36)
        val safeVerCode = versionCode.coerceAtLeast(1)
        val safeVerName = versionName.trim().ifEmpty { "1.0" }
        val resolvedTarget = targetFilePath.trim().ifEmpty {
            getDefaultTargetFilePath(cleanName.ifEmpty { cleanProjName.ifEmpty { cleanPkg } })
        }

        viewModelScope.launch {
            val newProject = StudioProjectEntity(
                name = cleanName,
                packageName = cleanPkg,
                projectName = cleanProjName,
                overlayTitle = cleanTitle,
                appLogoPath = cleanLogo,
                versionCode = safeVerCode,
                versionName = safeVerName,
                minSdk = safeMinSdk,
                targetSdk = safeTargetSdk,
                canvasWidthDp = canvasWidthDp.coerceIn(220, 420),
                canvasHeightDp = canvasHeightDp.coerceIn(220, 560),
                canvasBgColorHex = "#FFFFFF",
                defaultTargetFilePath = resolvedTarget
            )
            val newId = studioDao.insertProject(newProject)
            val inserted = studioDao.getProjectById(newId) ?: newProject.copy(id = newId)
            activeProjectIdFlow.value = newId
            _uiState.update {
                it.copy(
                    destination = StudioDestination.CANVAS_WORKSPACE,
                    activeProject = inserted,
                    selectedComponentId = null,
                    isLivePreviewMode = false,
                    showCreateProjectDialog = false,
                    showExistingProjectsPicker = false,
                    statusToast = "100% Empty Workspace ready."
                )
            }
        }
    }

    /**
     * Requirement 1: Select Existing Project and load its saved canvas workspace.
     */
    fun openExistingProject(project: StudioProjectEntity) {
        activeProjectIdFlow.value = project.id
        _uiState.update {
            it.copy(
                destination = StudioDestination.CANVAS_WORKSPACE,
                activeProject = project,
                selectedComponentId = null,
                isLivePreviewMode = false,
                showExistingProjectsPicker = false,
                statusToast = "Loaded project '${project.name}'."
            )
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            studioDao.deleteAllComponentsForProject(projectId)
            studioDao.deleteProjectById(projectId)
            if (activeProjectIdFlow.value == projectId) {
                activeProjectIdFlow.value = null
                _uiState.update {
                    it.copy(
                        destination = StudioDestination.PROJECT_LAUNCHER,
                        activeProject = null,
                        selectedComponentId = null
                    )
                }
            }
        }
    }

    fun navigateBackToLauncher() {
        stopSystemFloatingOverlay()
        _uiState.update {
            it.copy(
                destination = StudioDestination.PROJECT_LAUNCHER,
                selectedComponentId = null,
                isLivePreviewMode = false
            )
        }
    }

    /**
     * Manually adds a single user-chosen widget to the canvas and selects it immediately
     * so the bottom Property Inspector Dock opens for customization.
     */
    fun addComponentToCanvas(widgetType: ComponentWidgetType) {
        addPaletteItemToCanvas(
            widgetType = widgetType,
            customPrefix = widgetType.displayName,
            customWidthDp = null,
            customHeightDp = null,
            customBgHex = null,
            customTextHex = null
        )
    }

    /**
     * Adds a specific Sketchware palette item (Layouts, AndroidX, or Widgets) onto the blank canvas.
     */
    fun addPaletteItemToCanvas(
        widgetType: ComponentWidgetType,
        customPrefix: String,
        customWidthDp: Int? = null,
        customHeightDp: Int? = null,
        customBgHex: String? = null,
        customTextHex: String? = null
    ) {
        val project = _uiState.value.activeProject ?: return
        val existingComponents = activeComponents.value
        val existingCount = existingComponents.size
        val staggerX = (10 + (existingCount * 6) % 24).coerceAtMost((project.canvasWidthDp - 180).coerceAtLeast(8))
        val staggerY = if (existingComponents.isEmpty()) {
            10
        } else {
            existingComponents.maxOf { it.posYDp + it.heightDp } + 8
        }
        val defaultOffset = String.format(Locale.US, "0x%02X", 4 + (existingCount * 4))

        val (defaultW, defaultH, defaultBg) = when (widgetType) {
            ComponentWidgetType.BUTTON -> Triple(176, 44, "#334155")
            ComponentWidgetType.TOGGLE -> Triple(196, 44, "#FFFFFF")
            ComponentWidgetType.SLIDER -> Triple(196, 54, "#FFFFFF")
            ComponentWidgetType.TEXT -> Triple(150, 34, "#EEF2FF")
            ComponentWidgetType.INPUT -> Triple(186, 42, "#FFFFFF")
            ComponentWidgetType.IMAGE -> Triple(64, 64, "#1E293B")
            ComponentWidgetType.LINK -> Triple(190, 42, "#0F172A")
        }

        val resolvedTextHex = customTextHex ?: when (widgetType) {
            ComponentWidgetType.TOGGLE,
            ComponentWidgetType.SLIDER,
            ComponentWidgetType.INPUT -> "#0F172A"
            ComponentWidgetType.TEXT -> "#1E293B"
            ComponentWidgetType.LINK -> "#38BDF8"
            else -> "#FFFFFF"
        }

        val defaultLabel = "$customPrefix #${existingCount + 1}"

        viewModelScope.launch {
            val entity = CanvasComponentEntity(
                projectId = project.id,
                type = widgetType.name,
                label = defaultLabel,
                posXDp = staggerX,
                posYDp = staggerY,
                widthDp = customWidthDp ?: defaultW,
                heightDp = customHeightDp ?: defaultH,
                bgColorHex = customBgHex ?: defaultBg,
                textColorHex = resolvedTextHex,
                customImagePath = "",
                soundTrigger = SoundTriggerPlayer.SOUND_CLICK,
                customSoundPath = "",
                offSoundTrigger = SoundTriggerPlayer.SOUND_POP,
                offCustomSoundPath = "",
                targetFilePath = project.defaultTargetFilePath.ifEmpty { getDefaultTargetFilePath(project.name) },
                byteOffsetHex = defaultOffset,
                onPayloadHex = "On",
                offPayloadHex = "Off",
                sliderMax = 100,
                currentValue = if (widgetType == ComponentWidgetType.SLIDER) "50" else "0",
                linkUrl = if (widgetType == ComponentWidgetType.LINK) "https://google.com" else ""
            )
            val newId = studioDao.insertComponent(entity)
            if (project.autoFixSize) {
                relayoutComponentsForAutoFix(project.id, project.canvasWidthDp)
            }
            studioDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
            _uiState.update {
                it.copy(
                    selectedComponentId = newId,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = if (project.autoFixSize)
                        "Added '$defaultLabel' (Auto-fitted). Edit below & tap Save."
                    else
                        "Added '$defaultLabel' — Customize below & tap Save."
                )
            }
        }
    }

    /**
     * Toggles Auto Fix Size ON or OFF.
     * When ON:
     * 1. Sets the Floating Window to screen-perfection proportions (260dp x 320dp).
     * 2. Stretches every widget horizontally to cover left-to-right with a clean 10dp side gap.
     * 3. Stacks widgets vertically one below another with an 8dp gap and enables vertical scrolling.
     * 4. Hides and disables all manual edge/corner crop handles.
     * When OFF:
     * Restores manual crop handles and free drag/resize on the Floating Window and widgets.
     */
    fun toggleAutoFixSize() {
        val project = _uiState.value.activeProject ?: return
        val nextAutoFix = !project.autoFixSize
        val updatedProject = if (nextAutoFix) {
            project.copy(
                autoFixSize = true,
                canvasWidthDp = 216,
                canvasHeightDp = 290,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            project.copy(
                autoFixSize = false,
                updatedAt = System.currentTimeMillis()
            )
        }

        _uiState.update {
            it.copy(
                activeProject = updatedProject,
                customEditedKotlinFiles = emptyMap(),
                statusToast = if (nextAutoFix)
                    "Auto Fix Size ON: Full-width stacked widgets + scroll active."
                else
                    "Auto Fix Size OFF: Manual crop handles & free resize restored."
            )
        }

        viewModelScope.launch {
            studioDao.updateProject(updatedProject)
            if (nextAutoFix) {
                relayoutComponentsForAutoFix(updatedProject.id, updatedProject.canvasWidthDp)
            }
        }
    }

    private suspend fun relayoutComponentsForAutoFix(projectId: Long, canvasWidthDp: Int) {
        val list = studioDao.getComponentsForProjectSync(projectId)
        val sideGapDp = 8
        val verticalGapDp = 6
        val fullItemWidthDp = (canvasWidthDp - (sideGapDp * 2)).coerceAtLeast(100)
        var currentYDp = 8

        for (comp in list) {
            val cleanHeightDp = when (comp.type) {
                ComponentWidgetType.SLIDER.name -> 54
                ComponentWidgetType.IMAGE.name -> 58
                ComponentWidgetType.TEXT.name -> 36
                else -> 42
            }
            val updatedComp = comp.copy(
                posXDp = sideGapDp,
                posYDp = currentYDp,
                widthDp = fullItemWidthDp,
                heightDp = cleanHeightDp
            )
            studioDao.updateComponent(updatedComp)
            currentYDp += cleanHeightDp + verticalGapDp
        }
    }

    /**
     * Resizes the Floating Mod Menu window body (width & height in dp) like an image crop box
     * when the user drags any corner or edge endpoint handle (disabled when Auto Fix Size is ON).
     */
    fun resizeActiveProjectCanvas(deltaWidthDp: Int, deltaHeightDp: Int) {
        val project = _uiState.value.activeProject ?: return
        if (project.autoFixSize) return
        val newW = (project.canvasWidthDp + deltaWidthDp).coerceIn(170, 420)
        val newH = (project.canvasHeightDp + deltaHeightDp).coerceIn(160, 620)
        if (newW == project.canvasWidthDp && newH == project.canvasHeightDp) return
        val updatedProject = project.copy(
            canvasWidthDp = newW,
            canvasHeightDp = newH,
            updatedAt = System.currentTimeMillis()
        )
        _uiState.update {
            it.copy(
                activeProject = updatedProject,
                customEditedKotlinFiles = emptyMap(),
                statusToast = "Floating Mod Menu Size: ${newW}dp × ${newH}dp"
            )
        }
        viewModelScope.launch {
            studioDao.updateProject(updatedProject)
        }
    }

    /**
     * Requirement 3: Interactive touch selection listener on canvas elements.
     * Selecting an element opens the Bottom Property Editing Dock.
     */
    fun selectComponent(componentId: Long?) {
        _uiState.update {
            it.copy(
                selectedComponentId = componentId,
                isLivePreviewMode = false
            )
        }
    }

    fun updateComponent(updated: CanvasComponentEntity) {
        viewModelScope.launch {
            studioDao.updateComponent(updated)
            _uiState.value.activeProject?.let { proj ->
                val updatedProj = proj.copy(updatedAt = System.currentTimeMillis())
                studioDao.updateProject(updatedProj)
            }
            // Clear any cached code snapshot so visual design changes are always authoritative on Build
            if (_uiState.value.customEditedKotlinFiles.isNotEmpty()) {
                _uiState.update { it.copy(customEditedKotlinFiles = emptyMap()) }
            }
        }
    }

    /**
     * Explicitly saves the current visual design (project dimensions, panel name/logo, and all widgets)
     * to Room Database and syncs the build blueprint so Building the APK always uses this exact design.
     */
    fun saveCurrentProjectDesign(editedComponent: CanvasComponentEntity? = null) {
        val currentProject = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            if (editedComponent != null) {
                studioDao.updateComponent(editedComponent)
            }
            val updatedProj = currentProject.copy(updatedAt = System.currentTimeMillis())
            studioDao.updateProject(updatedProj)
            val currentList = studioDao.getComponentsForProjectSync(updatedProj.id)
            for (comp in currentList) {
                studioDao.updateComponent(comp)
            }
            _uiState.update {
                it.copy(
                    activeProject = updatedProj,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = "✅ Design Saved! (${currentList.size} widget(s) locked in for Build)"
                )
            }
        }
    }

    fun updateComponentPosition(component: CanvasComponentEntity, newXDp: Int, newYDp: Int) {
        val project = _uiState.value.activeProject
        if (project?.autoFixSize == true) return
        val maxW = project?.canvasWidthDp ?: 310
        val clampedX = newXDp.coerceIn(0, (maxW - 36).coerceAtLeast(0))
        val clampedY = newYDp.coerceIn(-300, 2500)
        updateComponent(component.copy(posXDp = clampedX, posYDp = clampedY))
    }

    /**
     * Resizes a widget on the canvas (widthDp & heightDp) like an image crop box
     * when the user drags its edge or corner crop handles (disabled when Auto Fix Size is ON).
     */
    fun resizeComponent(component: CanvasComponentEntity, deltaWidthDp: Int, deltaHeightDp: Int) {
        val project = _uiState.value.activeProject
        if (project?.autoFixSize == true) return
        val maxW = (project?.canvasWidthDp ?: 380).coerceAtLeast(100)
        val maxH = (project?.canvasHeightDp ?: 500).coerceAtLeast(80)
        val newW = (component.widthDp + deltaWidthDp).coerceIn(44, maxW)
        val newH = (component.heightDp + deltaHeightDp).coerceIn(28, maxH)
        if (newW == component.widthDp && newH == component.heightDp) return
        val updated = component.copy(widthDp = newW, heightDp = newH)
        updateComponent(updated)
        _uiState.update {
            it.copy(
                selectedComponentId = component.id,
                statusToast = "Widget '${component.label}' Crop Size: ${newW}dp × ${newH}dp"
            )
        }
    }

    fun duplicateSelectedComponent(component: CanvasComponentEntity) {
        val project = _uiState.value.activeProject
        viewModelScope.launch {
            val copy = component.copy(
                id = 0,
                label = "${component.label} Copy",
                posXDp = (component.posXDp + 16).coerceAtMost(240),
                posYDp = (component.posYDp + 16).coerceAtMost(320)
            )
            val newId = studioDao.insertComponent(copy)
            if (project?.autoFixSize == true) {
                relayoutComponentsForAutoFix(project.id, project.canvasWidthDp)
            }
            _uiState.update {
                it.copy(
                    selectedComponentId = newId,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = "Duplicated '${component.label}'."
                )
            }
        }
    }

    fun deleteComponent(componentId: Long) {
        val project = _uiState.value.activeProject
        viewModelScope.launch {
            studioDao.deleteComponentById(componentId)
            if (project?.autoFixSize == true) {
                relayoutComponentsForAutoFix(project.id, project.canvasWidthDp)
            }
            _uiState.update { state ->
                state.copy(
                    selectedComponentId = if (state.selectedComponentId == componentId) null else state.selectedComponentId,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = "Component removed from canvas."
                )
            }
        }
    }

    fun clearEntireCanvas() {
        val project = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            studioDao.deleteAllComponentsForProject(project.id)
            _uiState.update {
                it.copy(
                    selectedComponentId = null,
                    customEditedKotlinFiles = emptyMap(),
                    statusToast = "Canvas cleared to 100% Blank state."
                )
            }
        }
    }

    /**
     * Copies a user-picked image from the Android Photo Picker into local app storage
     * and assigns its path to the selected component's customImagePath property.
     */
    fun assignPickedImageToComponent(component: CanvasComponentEntity, uri: Uri) {
        viewModelScope.launch {
            try {
                val imgDir = File(appContext.filesDir, "component_images").apply { mkdirs() }
                val destFile = File(imgDir, "img_${component.id}_${System.currentTimeMillis()}.jpg")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                updateComponent(component.copy(customImagePath = destFile.absolutePath))
                _uiState.update {
                    it.copy(statusToast = "Custom image bound to '${component.label}'.")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusToast = "Could not import image: ${e.message}")
                }
            }
        }
    }

    /**
     * Copies a user-picked audio file into local app storage and assigns it to either
     * the ON sound trigger or the OFF sound trigger of the component.
     */
    fun assignPickedSoundToComponent(component: CanvasComponentEntity, uri: Uri, isOffSound: Boolean) {
        viewModelScope.launch {
            try {
                val soundDir = File(appContext.filesDir, "component_sounds").apply { mkdirs() }
                val tag = if (isOffSound) "off" else "on"
                val destFile = File(soundDir, "snd_${tag}_${component.id}_${System.currentTimeMillis()}.mp3")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val updated = if (isOffSound) {
                    component.copy(
                        offSoundTrigger = SoundTriggerPlayer.SOUND_CUSTOM_FILE,
                        offCustomSoundPath = destFile.absolutePath
                    )
                } else {
                    component.copy(
                        soundTrigger = SoundTriggerPlayer.SOUND_CUSTOM_FILE,
                        customSoundPath = destFile.absolutePath
                    )
                }
                updateComponent(updated)
                _uiState.update {
                    it.copy(
                        statusToast = if (isOffSound)
                            "Custom OFF sound bound to '${component.label}'."
                        else
                            "Custom ON sound bound to '${component.label}'."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusToast = "Could not import audio: ${e.message}")
                }
            }
        }
    }

    /**
     * Executes a component's interactive action (ON/OFF sound trigger + background file byte-offset write)
     * during Live Test Mode or from the Inspector Test button.
     */
    fun triggerComponentAction(component: CanvasComponentEntity, newValueOverride: String? = null) {
        val payloadToWrite: String
        val nextCurrentVal: String
        val isTurningOn: Boolean

        when (component.type) {
            ComponentWidgetType.LINK.name -> {
                val rawUrl = component.linkUrl.trim().ifEmpty { component.onPayloadHex.trim() }
                if (rawUrl.isNotEmpty()) {
                    val formatted = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
                SoundTriggerPlayer.playSoundTrigger(
                    appContext,
                    null,
                    component.soundTrigger,
                    component.customSoundPath
                )
                _uiState.update {
                    it.copy(statusToast = "🌐 Opening Link: ${rawUrl.ifEmpty { "https://google.com" }}")
                }
                return
            }
            ComponentWidgetType.SLIDER.name -> {
                nextCurrentVal = newValueOverride ?: component.currentValue
                payloadToWrite = nextCurrentVal
                val numericVal = nextCurrentVal.toIntOrNull() ?: 0
                isTurningOn = numericVal > 0
            }
            ComponentWidgetType.INPUT.name -> {
                if (newValueOverride != null) {
                    nextCurrentVal = newValueOverride
                    payloadToWrite = newValueOverride
                    isTurningOn = newValueOverride.isNotBlank() && newValueOverride != "0" && !newValueOverride.equals("false", ignoreCase = true)
                } else {
                    val currentlyOn = component.currentValue == "1" || component.currentValue.equals("true", ignoreCase = true)
                    isTurningOn = !currentlyOn
                    nextCurrentVal = if (isTurningOn) "1" else "0"
                    payloadToWrite = if (isTurningOn) component.onPayloadHex else component.offPayloadHex
                }
            }
            else -> {
                val currentlyOn = component.currentValue == "1" || component.currentValue.equals("true", ignoreCase = true)
                val nextOn = if (newValueOverride != null) {
                    newValueOverride == "1" || newValueOverride.equals("true", ignoreCase = true)
                } else {
                    !currentlyOn
                }
                isTurningOn = nextOn
                nextCurrentVal = if (nextOn) "1" else "0"
                payloadToWrite = if (nextOn) component.onPayloadHex else component.offPayloadHex
                _uiState.update {
                    it.copy(
                        statusToast = if (nextOn) {
                            "✅ ${component.label} → CHANGE (${component.onPayloadHex})"
                        } else {
                            "⛔ ${component.label} → ORIGINAL (${component.offPayloadHex})"
                        }
                    )
                }
            }
        }

        if (isTurningOn) {
            SoundTriggerPlayer.playSoundTrigger(
                appContext,
                null,
                component.soundTrigger,
                component.customSoundPath
            )
        } else {
            SoundTriggerPlayer.playSoundTrigger(
                appContext,
                null,
                component.offSoundTrigger,
                component.offCustomSoundPath
            )
        }

        val updatedComp = component.copy(currentValue = nextCurrentVal)
        updateComponent(updatedComp)

        viewModelScope.launch(Dispatchers.IO) {
            val ok = stateWriter.applyWidgetPatchSync(
                appContext.filesDir,
                "widget_${component.id}",
                component.type,
                component.targetFilePath,
                component.byteOffsetHex,
                component.offPayloadHex,
                component.onPayloadHex,
                payloadToWrite,
                isTurningOn,
                component.label
            )
            val resolvedFile = stateWriter.resolveTargetFile(appContext.filesDir, component.targetFilePath)
            val preview = stateWriter.readTargetFilePreview(appContext.filesDir, component.targetFilePath)
            _uiState.update {
                it.copy(
                    statusToast = if (ok) {
                        "✅ Modified '${resolvedFile.name}' → $preview"
                    } else {
                        "⚠️ Cannot write '${resolvedFile.absolutePath}'. Tap 'Grant All Files Access' in Path settings!"
                    }
                )
            }
        }
    }

    fun toggleLivePreviewMode() {
        val nextMode = !_uiState.value.isLivePreviewMode
        _uiState.update {
            it.copy(
                isLivePreviewMode = nextMode,
                selectedComponentId = if (nextMode) null else it.selectedComponentId,
                statusToast = if (nextMode)
                    "Live Interactive Mode: Tap your components to fire sound triggers & file offset writes."
                else
                    "Edit Mode: Tap any component on the canvas to open the Bottom Property Dock."
            )
        }
    }

    fun refreshOverlayPermission() {
        _uiState.update {
            it.copy(
                hasOverlayPermission = Settings.canDrawOverlays(appContext),
                isSystemOverlayRunning = FloatingDashboardService.isRunning()
            )
        }
    }

    fun launchSystemFloatingOverlay() {
        val project = _uiState.value.activeProject ?: return
        val components = activeComponents.value
        val specs = components.map { comp ->
            DynamicOverlayRegistry.OverlayItemSpec().apply {
                id = comp.id
                type = comp.type
                label = comp.label
                posXDp = comp.posXDp
                posYDp = comp.posYDp
                widthDp = comp.widthDp
                heightDp = comp.heightDp
                bgColorHex = comp.bgColorHex
                textColorHex = comp.textColorHex
                customImagePath = comp.customImagePath
                soundTrigger = comp.soundTrigger
                customSoundPath = comp.customSoundPath
                offSoundTrigger = comp.offSoundTrigger
                offCustomSoundPath = comp.offCustomSoundPath
                targetFilePath = comp.targetFilePath
                byteOffsetHex = comp.byteOffsetHex
                onPayloadHex = comp.onPayloadHex
                offPayloadHex = comp.offPayloadHex
                sliderMax = comp.sliderMax
                currentValue = comp.currentValue
                linkUrl = comp.linkUrl
            }
        }

        DynamicOverlayRegistry.updateActiveOverlay(
            project.overlayTitle,
            project.floatingLogoPath,
            project.canvasWidthDp,
            project.canvasHeightDp,
            project.canvasBgColorHex,
            project.autoFixSize,
            specs
        )

        if (Settings.canDrawOverlays(appContext)) {
            try {
                val intent = Intent(appContext, FloatingDashboardService::class.java).apply {
                    action = FloatingDashboardService.ACTION_START_OVERLAY
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                _uiState.update {
                    it.copy(
                        isSystemOverlayRunning = true,
                        statusToast = "System Floating Overlay launched with ${specs.size} custom component(s)."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusToast = "Switched to In-App Interactive Preview Mode.")
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    isLivePreviewMode = false,
                    statusToast = "Please grant Overlay Permission to float window over other Android apps."
                )
            }
        }
    }

    fun stopSystemFloatingOverlay() {
        try {
            val intent = Intent(appContext, FloatingDashboardService::class.java).apply {
                action = FloatingDashboardService.ACTION_STOP_OVERLAY
            }
            appContext.stopService(intent)
        } catch (_: Exception) {
        }
        _uiState.update {
            it.copy(
                isSystemOverlayRunning = false,
                isLivePreviewMode = false,
                statusToast = "Floating overlay stopped. Tap any widget to edit."
            )
        }
    }

    fun computeComponentTrackerSummary(components: List<CanvasComponentEntity>): ComponentCountSummary {
        var buttons = 0
        var toggles = 0
        var sliders = 0
        var texts = 0
        var inputs = 0
        var images = 0
        for (c in components) {
            when (c.type) {
                ComponentWidgetType.BUTTON.name -> buttons++
                ComponentWidgetType.TOGGLE.name -> toggles++
                ComponentWidgetType.SLIDER.name -> sliders++
                ComponentWidgetType.TEXT.name -> texts++
                ComponentWidgetType.INPUT.name -> inputs++
                ComponentWidgetType.IMAGE.name -> images++
            }
        }
        return ComponentCountSummary(
            totalCount = components.size,
            buttonCount = buttons,
            toggleCount = toggles,
            sliderCount = sliders,
            textCount = texts,
            inputCount = inputs,
            imageCount = images
        )
    }

    fun dismissDownloadSummaryDialog() {
        _uiState.update { it.copy(downloadedFileSummary = null) }
    }

    fun openEditCodeDialog(show: Boolean) {
        if (show) {
            val project = _uiState.value.activeProject ?: return
            val generated = KotlinProjectCodeEngine.generateKotlinFilesForProject(project, activeComponents.value)
            _uiState.update {
                it.copy(
                    showEditCodeDialog = true,
                    customEditedKotlinFiles = generated,
                    statusToast = "Opened Edit Code (${generated.size} Kotlin files synced with Visual Screen)."
                )
            }
        } else {
            _uiState.update { it.copy(showEditCodeDialog = false) }
        }
    }

    /**
     * Parses the user's edited Kotlin files from the "Edit Code" window, updates the visual screen
     * project & widget database records so the visual screen immediately reflects the edited Kotlin code,
     * and optionally triggers full APK compilation & signing.
     */
    fun applyEditedKotlinCodeToVisualScreen(
        editedFiles: Map<String, String>,
        andCompileApk: Boolean = false
    ) {
        val project = _uiState.value.activeProject ?: return
        val currentComponents = activeComponents.value
        viewModelScope.launch {
            val parsed = KotlinProjectCodeEngine.parseEditedKotlinToVisualState(
                originalProject = project,
                originalComponents = currentComponents,
                editedFiles = editedFiles
            )
            studioDao.updateProject(parsed.updatedProject)
            studioDao.deleteAllComponentsForProject(project.id)
            for (comp in parsed.updatedComponents) {
                studioDao.insertComponent(comp.copy(id = 0L, projectId = project.id))
            }
            val reloadedComponents = studioDao.getComponentsForProjectSync(project.id)
            val refreshedKotlinFiles = KotlinProjectCodeEngine.generateKotlinFilesForProject(
                parsed.updatedProject,
                reloadedComponents
            ).toMutableMap().apply {
                // Preserve any custom edits in secondary Kotlin tabs while keeping CanvasWorkspaceComponents synced
                editedFiles.forEach { (k, v) ->
                    if (k != "src/main/java/com/example/ui/CanvasWorkspaceComponents.kt") {
                        put(k, v)
                    }
                }
            }

            _uiState.update {
                it.copy(
                    activeProject = parsed.updatedProject,
                    selectedComponentId = null,
                    showEditCodeDialog = false,
                    customEditedKotlinFiles = refreshedKotlinFiles,
                    statusToast = "✅ Compiled Kotlin Code → Visual Screen (${reloadedComponents.size} widgets synced)!"
                )
            }

            if (andCompileApk) {
                downloadFloatingWindowToAndroid(refreshedKotlinFiles)
            }
        }
    }

    /**
     * Compiles the app created by the user INSIDE Studio Error into a complete, standalone,
     * installable Android APK with its own unique package name, its own App Name (project.name),
     * 4-byte-aligned resources.arsc, 4096-byte-aligned native libraries, V1/V2/V3 signatures,
     * and bundled visual screen & floating window widgets.
     */
    fun downloadFloatingWindowToAndroid(customKotlinFiles: Map<String, String>? = null) {
        val initialProject = _uiState.value.activeProject ?: return
        viewModelScope.launch {
            try {
                // Always read the latest project and visual components from Room DB / active visual state.
                // Only parse customKotlinFiles if explicitly passed from the "Edit Code -> Compile" action;
                // never overwrite the user's visual design with an older cached code snapshot!
                var project = studioDao.getProjectById(initialProject.id) ?: initialProject
                var components = studioDao.getComponentsForProjectSync(project.id)
                    .ifEmpty { activeComponents.value }

                if (customKotlinFiles != null &&
                    customKotlinFiles.containsKey("src/main/java/com/example/ui/CanvasWorkspaceComponents.kt")
                ) {
                    val parsed = KotlinProjectCodeEngine.parseEditedKotlinToVisualState(
                        originalProject = project,
                        originalComponents = components,
                        editedFiles = customKotlinFiles
                    )
                    project = parsed.updatedProject
                    studioDao.updateProject(project)
                    studioDao.deleteAllComponentsForProject(project.id)
                    for (comp in parsed.updatedComponents) {
                        studioDao.insertComponent(comp.copy(id = 0L, projectId = project.id))
                    }
                    components = studioDao.getComponentsForProjectSync(project.id)
                    _uiState.update { it.copy(activeProject = project) }
                }

                // Also update DynamicOverlayRegistry immediately so in-app preview & floating service have the exact latest design
                val latestSpecs = components.map { comp ->
                    DynamicOverlayRegistry.OverlayItemSpec().apply {
                        id = comp.id
                        type = comp.type
                        label = comp.label
                        posXDp = comp.posXDp
                        posYDp = comp.posYDp
                        widthDp = comp.widthDp
                        heightDp = comp.heightDp
                        bgColorHex = comp.bgColorHex
                        textColorHex = comp.textColorHex
                        customImagePath = comp.customImagePath
                        soundTrigger = comp.soundTrigger
                        customSoundPath = comp.customSoundPath
                        offSoundTrigger = comp.offSoundTrigger
                        offCustomSoundPath = comp.offCustomSoundPath
                        targetFilePath = comp.targetFilePath
                        byteOffsetHex = comp.byteOffsetHex
                        onPayloadHex = comp.onPayloadHex
                        offPayloadHex = comp.offPayloadHex
                        sliderMax = comp.sliderMax
                        currentValue = comp.currentValue
                        linkUrl = comp.linkUrl
                    }
                }
                DynamicOverlayRegistry.updateActiveOverlay(
                    project.overlayTitle,
                    project.floatingLogoPath,
                    project.canvasWidthDp,
                    project.canvasHeightDp,
                    project.canvasBgColorHex,
                    project.autoFixSize,
                    latestSpecs
                )

                val safeSlug = project.name.trim().lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                    .ifEmpty { "compiled_app" }
                val fileName = "${safeSlug}.apk"
                val targetPkg = ApkCompilationEngine.getCompiledAppPackageName(project)

                val blueprintFiles = ApkCompilationEngine.generateProjectBlueprintFiles(project, components).toMutableMap()
                val activeCustomFiles = customKotlinFiles ?: _uiState.value.customEditedKotlinFiles
                if (activeCustomFiles.isNotEmpty()) {
                    blueprintFiles.putAll(activeCustomFiles)
                    // Ensure overlay_config.json and CanvasWorkspaceComponents.kt ALWAYS reflect the current visual widgets
                    blueprintFiles["src/main/java/com/example/ui/CanvasWorkspaceComponents.kt"] =
                        KotlinProjectCodeEngine.generateKotlinFilesForProject(project, components)[
                            "src/main/java/com/example/ui/CanvasWorkspaceComponents.kt"
                        ].orEmpty()
                }

                val rawJavaSource = buildString {
                    appendLine("// === COMPILED APP: ${project.name} ($targetPkg) ===")
                    appendLine("// === KOTLIN WIDGET SOURCE: CanvasWorkspaceComponents.kt ===")
                    appendLine(blueprintFiles["src/main/java/com/example/ui/CanvasWorkspaceComponents.kt"].orEmpty())
                    appendLine()
                    appendLine("// === KOTLIN SERVICE SOURCE: FloatingOverlayService.kt ===")
                    appendLine(blueprintFiles["src/main/java/com/example/service/FloatingOverlayService.kt"].orEmpty())
                }

                _uiState.update {
                    it.copy(
                        isBuildingApk = true,
                        buildProgressStepText = "Step 1/3: Compiling '${project.name}' Kotlin Widget Code (${components.size} widgets)...",
                        rawJavaBuildPreview = rawJavaSource,
                        statusToast = "⚙ Compiling '${project.name}' ($targetPkg) into standalone APK..."
                    )
                }
                delay(180)

                _uiState.update {
                    it.copy(
                        buildProgressStepText = "Step 2/3: Patching Binary AndroidManifest.xml (App: '${project.name}', ID: $targetPkg) & Aligning APK..."
                    )
                }
                delay(180)

                val localDownloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(appContext.filesDir, "downloads").apply { mkdirs() }
                if (!localDownloadsDir.exists()) localDownloadsDir.mkdirs()
                val localFile = File(localDownloadsDir, fileName)

                val compilationResult = withContext(Dispatchers.IO) {
                    ApkCompilationEngine.compileAndSignProjectApk(
                        appContext,
                        project,
                        components,
                        localFile,
                        blueprintFiles
                    )
                }

                _uiState.update {
                    it.copy(
                        buildProgressStepText = "Step 3/3: Signing V1 + V2 + V3 APK & Exporting '$fileName' to Android Downloads..."
                    )
                }
                delay(120)

                var savedDisplayLocation = compilationResult.signedApkFile.absolutePath

                // Save compiled & signed APK directly to public Android Downloads via MediaStore on Android 10+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    withContext(Dispatchers.IO) {
                        try {
                            val resolver = appContext.contentResolver
                            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                            try {
                                resolver.delete(
                                    collection,
                                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                                    arrayOf(fileName)
                                )
                            } catch (_: Exception) {
                            }

                            val contentValues = ContentValues().apply {
                                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                put(MediaStore.Downloads.IS_PENDING, 1)
                            }
                            val itemUri = resolver.insert(collection, contentValues)
                            if (itemUri != null) {
                                resolver.openOutputStream(itemUri)?.use { out ->
                                    FileInputStream(compilationResult.signedApkFile).use { input ->
                                        input.copyTo(out, bufferSize = 32768)
                                    }
                                }
                                contentValues.clear()
                                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                                resolver.update(itemUri, contentValues, null, null)
                                savedDisplayLocation = "Internal Storage/Download/$fileName"
                            }
                        } catch (_: Exception) {
                        }
                    }
                }

                val sizeBytes = compilationResult.apkSizeBytes
                val formattedSize = if (sizeBytes >= 1024L * 1024L) {
                    String.format(Locale.US, "%.2f MB", sizeBytes / (1024.0 * 1024.0))
                } else {
                    String.format(Locale.US, "%.1f KB", (sizeBytes / 1024.0).coerceAtLeast(1.0))
                }

                SoundTriggerPlayer.playSoundTrigger(appContext, null, SoundTriggerPlayer.SOUND_CONFIRM, "")
                _uiState.update {
                    it.copy(
                        isBuildingApk = false,
                        buildProgressStepText = "",
                        rawJavaBuildPreview = rawJavaSource,
                        compiledApkFilePath = compilationResult.signedApkFile.absolutePath,
                        compiledAppPackageName = compilationResult.compiledPackageName,
                        compiledAppName = compilationResult.compiledAppName,
                        downloadedFileName = fileName,
                        downloadedFileSummary = buildString {
                            appendLine("App Compiled: ${compilationResult.compiledAppName.ifBlank { "(No Name Set)" }}")
                            appendLine("Package ID: ${compilationResult.compiledPackageName}")
                            appendLine("Version: v${project.versionName} (Code ${project.versionCode}) • SDK ${project.minSdk}–${project.targetSdk}")
                            appendLine("Widgets Compiled: ${components.size} interactive widget(s)")
                            appendLine("APK Size: $formattedSize (Signed V1 + V2 + V3)")
                            append("Saved to: $savedDisplayLocation")
                        },
                        statusToast = "✅ Compiled '${compilationResult.compiledAppName}' APK ($formattedSize)!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBuildingApk = false,
                        buildProgressStepText = "",
                        statusToast = "Build failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Launches the Android system Package Installer for the newly compiled standalone APK
     * so the user can install the app they created inside Studio Error with one tap.
     */
    fun installCompiledApk(context: Context) {
        // Stop any running floating overlay first so Android PackageInstaller & Settings never block permission clicks!
        stopSystemFloatingOverlay()
        val apkPath = _uiState.value.compiledApkFilePath
        val apkFile = if (!apkPath.isNullOrBlank()) File(apkPath) else null
        if (apkFile == null || !apkFile.exists()) {
            _uiState.update { it.copy(statusToast = "Please tap Build first to compile your APK.") }
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                _uiState.update {
                    it.copy(statusToast = "Allow 'Install unknown apps' then tap Install Compiled APK again.")
                }
                return
            }

            val authority = "${context.packageName}.fileprovider"
            val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
            @Suppress("DEPRECATION")
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }
            context.startActivity(installIntent)
            _uiState.update {
                it.copy(
                    statusToast = "📲 Opening Android Installer for '${_uiState.value.compiledAppName}'..."
                )
            }
        } catch (e: Exception) {
            try {
                val authority = "${context.packageName}.fileprovider"
                val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Exception) {
                openCompiledAppPreview()
            }
        }
    }

    /**
     * Opens the compiled standalone app screen directly so the user can immediately run and test
     * the exact app they built inside Studio Error.
     */
    fun openCompiledAppPreview() {
        val project = _uiState.value.activeProject ?: return
        val pkg = _uiState.value.compiledAppPackageName.ifBlank {
            ApkCompilationEngine.getCompiledAppPackageName(project)
        }
        _uiState.update {
            it.copy(
                destination = StudioDestination.COMPILED_STANDALONE_APP,
                downloadedFileSummary = null,
                compiledAppPackageName = pkg,
                compiledAppName = project.name,
                statusToast = "▶ Running Compiled App '${project.name}' ($pkg)"
            )
        }
    }

    fun closeCompiledAppPreview() {
        if (_uiState.value.isBundledStandaloneApk) return
        _uiState.update {
            it.copy(
                destination = StudioDestination.CANVAS_WORKSPACE,
                statusToast = "Returned to Studio Error Editor for '${it.activeProject?.name ?: "Project"}'."
            )
        }
    }

    fun saveFloatingWindowToCustomUri(destUri: Uri) {
        val project = _uiState.value.activeProject ?: return
        val components = activeComponents.value
        viewModelScope.launch {
            try {
                val safeSlug = project.name.trim().lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                    .ifEmpty { "floating_mod_menu" }
                val tempOutFile = File(appContext.cacheDir, "${safeSlug}_custom_export.apk")
                withContext(Dispatchers.IO) {
                    val result = ApkCompilationEngine.compileAndSignProjectApk(
                        appContext,
                        project,
                        components,
                        tempOutFile
                    )
                    appContext.contentResolver.openOutputStream(destUri)?.use { out ->
                        FileInputStream(result.signedApkFile).use { input ->
                            input.copyTo(out, bufferSize = 32768)
                        }
                    }
                    if (tempOutFile.exists()) tempOutFile.delete()
                }
                _uiState.update {
                    it.copy(
                        downloadedFileSummary = null,
                        statusToast = "✅ Saved compiled & signed APK to selected Android folder!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusToast = "Could not save APK: ${e.message}")
                }
            }
        }
    }
}
