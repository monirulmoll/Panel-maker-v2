package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class StudioDestination {
    PROJECT_LAUNCHER,
    CANVAS_WORKSPACE
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
    val activeProject: StudioProjectEntity? = null,
    val selectedComponentId: Long? = null,
    val isLivePreviewMode: Boolean = false,
    val isSystemOverlayRunning: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val showExistingProjectsPicker: Boolean = false,
    val showCreateProjectDialog: Boolean = false,
    val isBuildingApk: Boolean = false,
    val rawJavaBuildPreview: String = "",
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

    init {
        stateWriter.addListener(writeListener)
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

    /**
     * Requirement 1 & 2:
     * Creates a new project and opens the 100% BLANK Canvas Workspace (zero pre-made components).
     */
    fun createNewBlankProject(
        name: String,
        overlayTitle: String,
        canvasWidthDp: Int,
        canvasHeightDp: Int,
        targetFilePath: String
    ) {
        val cleanName = name.trim().ifEmpty { "Untitled Overlay Project" }
        val cleanTitle = overlayTitle.trim().ifEmpty { cleanName }
        val resolvedTarget = targetFilePath.trim().ifEmpty { getDefaultTargetFilePath(cleanName) }

        viewModelScope.launch {
            val newProject = StudioProjectEntity(
                name = cleanName,
                overlayTitle = cleanTitle,
                canvasWidthDp = canvasWidthDp.coerceIn(220, 420),
                canvasHeightDp = canvasHeightDp.coerceIn(220, 560),
                canvasBgColorHex = "#1E293B",
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
                    statusToast = "Opened 100% Blank Canvas for '${inserted.name}'. Tap any widget above to add it."
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
        val existingCount = activeComponents.value.size
        val staggerX = (12 + (existingCount * 10) % 60).coerceAtMost((project.canvasWidthDp - 140).coerceAtLeast(8))
        val staggerY = (14 + (existingCount * 26) % 220).coerceAtMost((project.canvasHeightDp - 56).coerceAtLeast(8))
        val defaultOffset = String.format(Locale.US, "0x%02X", 4 + (existingCount * 4))

        val (defaultW, defaultH, defaultBg) = when (widgetType) {
            ComponentWidgetType.BUTTON -> Triple(176, 44, "#334155")
            ComponentWidgetType.TOGGLE -> Triple(196, 44, "#FFFFFF")
            ComponentWidgetType.SLIDER -> Triple(196, 54, "#FFFFFF")
            ComponentWidgetType.TEXT -> Triple(150, 34, "#EEF2FF")
            ComponentWidgetType.INPUT -> Triple(186, 42, "#FFFFFF")
            ComponentWidgetType.IMAGE -> Triple(64, 64, "#1E293B")
        }

        val resolvedTextHex = customTextHex ?: when (widgetType) {
            ComponentWidgetType.TOGGLE,
            ComponentWidgetType.SLIDER,
            ComponentWidgetType.INPUT -> "#0F172A"
            ComponentWidgetType.TEXT -> "#1E293B"
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
                onPayloadHex = "0x01",
                offPayloadHex = "0x00",
                sliderMax = 100,
                currentValue = if (widgetType == ComponentWidgetType.SLIDER) "50" else "0"
            )
            val newId = studioDao.insertComponent(entity)
            if (project.autoFixSize) {
                relayoutComponentsForAutoFix(project.id, project.canvasWidthDp)
            }
            studioDao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
            _uiState.update {
                it.copy(
                    selectedComponentId = null,
                    statusToast = if (project.autoFixSize)
                        "Added '$defaultLabel' (Auto-fitted). Tap widget to edit."
                    else
                        "Added '$defaultLabel' — Tap widget or top chip to edit."
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
                studioDao.updateProject(proj.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun updateComponentPosition(component: CanvasComponentEntity, newXDp: Int, newYDp: Int) {
        val project = _uiState.value.activeProject
        if (project?.autoFixSize == true) return
        val maxW = project?.canvasWidthDp ?: 310
        val maxH = project?.canvasHeightDp ?: 380
        val clampedX = newXDp.coerceIn(0, (maxW - 36).coerceAtLeast(0))
        val clampedY = newYDp.coerceIn(0, (maxH - 32).coerceAtLeast(0))
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
                            "✅ ${component.label} → ON (${component.onPayloadHex})"
                        } else {
                            "⛔ ${component.label} → OFF (${component.offPayloadHex})"
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

        updateComponent(component.copy(currentValue = nextCurrentVal))

        stateWriter.writeCustomComponentOffsetAsync(
            appContext.filesDir,
            component.targetFilePath,
            component.byteOffsetHex,
            payloadToWrite,
            component.label
        )
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
            }
        }

        DynamicOverlayRegistry.updateActiveOverlay(
            project.overlayTitle,
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

    /**
     * Builds the complete Android Floating Window package (.apk archive containing layout XML,
     * FloatingModMenuService.java, AndroidManifest.xml, overlay_config.json, and custom assets)
     * and saves it directly into the Android Downloads directory.
     */
    fun downloadFloatingWindowToAndroid() {
        val project = _uiState.value.activeProject ?: return
        val components = activeComponents.value
        viewModelScope.launch {
            try {
                val safeSlug = project.name.trim().lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                    .ifEmpty { "floating_mod_menu" }
                val fileName = "${safeSlug}_floating_window.apk"
                val rawJavaSource = generateRawJavaSourceForProject(project, components)

                // Step 1: Show Raw Java source format while building
                _uiState.update {
                    it.copy(
                        isBuildingApk = true,
                        rawJavaBuildPreview = rawJavaSource,
                        statusToast = "⚙ Generating Raw Java (FloatingModMenuService.java) & compiling APK..."
                    )
                }
                delay(550)

                val packageBytes = buildFloatingWindowPackageBytes(project, components, rawJavaSource)

                // Always keep a copy in app-accessible Downloads folder
                val localDownloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(appContext.filesDir, "downloads").apply { mkdirs() }
                if (!localDownloadsDir.exists()) localDownloadsDir.mkdirs()
                val localFile = File(localDownloadsDir, fileName)
                FileOutputStream(localFile).use { it.write(packageBytes) }

                var savedDisplayLocation = localFile.absolutePath

                // Also save directly to public Android Downloads via MediaStore on Android 10+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val resolver = appContext.contentResolver
                        val contentValues = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        val itemUri = resolver.insert(collection, contentValues)
                        if (itemUri != null) {
                            resolver.openOutputStream(itemUri)?.use { out ->
                                out.write(packageBytes)
                            }
                            contentValues.clear()
                            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                            resolver.update(itemUri, contentValues, null, null)
                            savedDisplayLocation = "Internal Storage/Download/$fileName"
                        }
                    } catch (_: Exception) {
                    }
                }

                val sizeKb = (packageBytes.size / 1024.0).coerceAtLeast(1.0)
                val formattedSize = String.format(Locale.US, "%.1f KB", sizeKb)
                SoundTriggerPlayer.playSoundTrigger(appContext, null, SoundTriggerPlayer.SOUND_CONFIRM, "")
                _uiState.update {
                    it.copy(
                        isBuildingApk = false,
                        rawJavaBuildPreview = rawJavaSource,
                        downloadedFileName = fileName,
                        downloadedFileSummary = "Compiled Raw Java → '$fileName' ($formattedSize, ${components.size} widgets) into Android Downloads:\n$savedDisplayLocation",
                        statusToast = "✅ Build Complete: '$fileName' ($formattedSize) saved to Android Downloads!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isBuildingApk = false,
                        statusToast = "Build failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun saveFloatingWindowToCustomUri(destUri: Uri) {
        val project = _uiState.value.activeProject ?: return
        val components = activeComponents.value
        viewModelScope.launch {
            try {
                val rawJavaSource = generateRawJavaSourceForProject(project, components)
                val packageBytes = buildFloatingWindowPackageBytes(project, components, rawJavaSource)
                appContext.contentResolver.openOutputStream(destUri)?.use { out ->
                    out.write(packageBytes)
                }
                _uiState.update {
                    it.copy(
                        downloadedFileSummary = null,
                        statusToast = "✅ Saved compiled APK to selected Android folder!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(statusToast = "Could not save APK: ${e.message}")
                }
            }
        }
    }

    private fun generateRawJavaSourceForProject(
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>
    ): String {
        return buildString {
            appendLine("package com.floating.modmenu;")
            appendLine()
            appendLine("import android.app.Service;")
            appendLine("import android.content.Intent;")
            appendLine("import android.graphics.Color;")
            appendLine("import android.os.IBinder;")
            appendLine("import android.view.WindowManager;")
            appendLine("import android.widget.LinearLayout;")
            appendLine("import android.widget.ScrollView;")
            appendLine("import android.widget.Switch;")
            appendLine("import android.widget.Button;")
            appendLine("import android.widget.SeekBar;")
            appendLine()
            appendLine("// Raw Java Floating Mod Menu generated by Studio Error")
            appendLine("public class FloatingModMenuService extends Service {")
            appendLine("    public static final String TITLE = \"${project.overlayTitle}\";")
            appendLine("    public static final int WIDTH_DP = ${project.canvasWidthDp};")
            appendLine("    public static final int HEIGHT_DP = ${project.canvasHeightDp};")
            appendLine("    public static final boolean AUTO_FIX_SIZE = ${project.autoFixSize};")
            appendLine()
            appendLine("    @Override")
            appendLine("    public void onCreate() {")
            appendLine("        super.onCreate();")
            appendLine("        ScrollView scrollContainer = new ScrollView(this);")
            appendLine("        LinearLayout modBody = new LinearLayout(this);")
            appendLine("        modBody.setOrientation(LinearLayout.VERTICAL);")
            components.forEachIndexed { idx, c ->
                appendLine("        // Widget #${idx + 1}: ${c.label} [${c.type}] -> Offset ${c.byteOffsetHex} (ON=${c.onPayloadHex}, OFF=${c.offPayloadHex})")
                appendLine("        // ON Sound: ${c.soundTrigger}, OFF Sound: ${c.offSoundTrigger}")
            }
            appendLine("        scrollContainer.addView(modBody);")
            appendLine("    }")
            appendLine()
            appendLine("    @Override")
            appendLine("    public IBinder onBind(Intent intent) { return null; }")
            appendLine("}")
        }
    }

    private fun buildFloatingWindowPackageBytes(
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>,
        rawJavaSource: String = generateRawJavaSourceForProject(project, components)
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // 0. Raw Java Source Code (FloatingModMenuService.java)
            zos.putNextEntry(ZipEntry("src/main/java/com/floating/modmenu/FloatingModMenuService.java"))
            zos.write(rawJavaSource.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            // 1. AndroidManifest.xml
            val manifestXml = buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("""<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.floating.modmenu">""")
                appendLine("""    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />""")
                appendLine("""    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />""")
                appendLine("""    <application android:label="${project.name}">""")
                appendLine("""        <service android:name=".FloatingModMenuService" android:exported="false" />""")
                appendLine("""    </application>""")
                appendLine("""</manifest>""")
            }
            zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
            zos.write(manifestXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. res/layout/floating_window.xml
            val layoutXml = buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("""<!-- Floating Window: ${project.overlayTitle} (${project.canvasWidthDp}dp x ${project.canvasHeightDp}dp) -->""")
                appendLine("""<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android" """)
                appendLine("""    android:layout_width="${project.canvasWidthDp}dp" """)
                appendLine("""    android:layout_height="${project.canvasHeightDp}dp" """)
                appendLine("""    android:background="${project.canvasBgColorHex}">""")
                for (comp in components) {
                    val tag = when (comp.type) {
                        ComponentWidgetType.TOGGLE.name -> "Switch"
                        ComponentWidgetType.BUTTON.name -> "Button"
                        ComponentWidgetType.SLIDER.name -> "SeekBar"
                        ComponentWidgetType.INPUT.name -> "EditText"
                        ComponentWidgetType.IMAGE.name -> "ImageView"
                        else -> "TextView"
                    }
                    appendLine("""    <$tag""")
                    appendLine("""        android:id="@+id/widget_${comp.id}" """)
                    appendLine("""        android:layout_width="${comp.widthDp}dp" """)
                    appendLine("""        android:layout_height="${comp.heightDp}dp" """)
                    appendLine("""        android:layout_marginStart="${comp.posXDp}dp" """)
                    appendLine("""        android:layout_marginTop="${comp.posYDp}dp" """)
                    appendLine("""        android:text="${comp.label}" """)
                    appendLine("""        android:background="${comp.bgColorHex}" """)
                    appendLine("""        android:textColor="${comp.textColorHex}" />""")
                }
                appendLine("""</FrameLayout>""")
            }
            zos.putNextEntry(ZipEntry("res/layout/floating_window.xml"))
            zos.write(layoutXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. assets/overlay_config.json
            val configJson = buildString {
                appendLine("{")
                appendLine("""  "projectName": "${project.name}",""")
                appendLine("""  "overlayTitle": "${project.overlayTitle}",""")
                appendLine("""  "canvasWidthDp": ${project.canvasWidthDp},""")
                appendLine("""  "canvasHeightDp": ${project.canvasHeightDp},""")
                appendLine("""  "canvasBgColorHex": "${project.canvasBgColorHex}",""")
                appendLine("""  "components": [""")
                components.forEachIndexed { idx, c ->
                    val comma = if (idx < components.lastIndex) "," else ""
                    appendLine(
                        """    {"id": ${c.id}, "type": "${c.type}", "label": "${c.label}", "x": ${c.posXDp}, "y": ${c.posYDp}, "width": ${c.widthDp}, "height": ${c.heightDp}, "bgHex": "${c.bgColorHex}", "textHex": "${c.textColorHex}", "onSound": "${c.soundTrigger}", "offSound": "${c.offSoundTrigger}", "targetFile": "${c.targetFilePath}", "offsetHex": "${c.byteOffsetHex}", "onHex": "${c.onPayloadHex}", "offHex": "${c.offPayloadHex}", "sliderMax": ${c.sliderMax}}$comma"""
                    )
                }
                appendLine("  ]")
                appendLine("}")
            }
            zos.putNextEntry(ZipEntry("assets/overlay_config.json"))
            zos.write(configJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. Bundle any custom images or ON/OFF audio files attached to components
            for (comp in components) {
                listOf(
                    comp.customImagePath to "assets/images/img_${comp.id}.jpg",
                    comp.customSoundPath to "assets/sounds/on_snd_${comp.id}.mp3",
                    comp.offCustomSoundPath to "assets/sounds/off_snd_${comp.id}.mp3"
                ).forEach { (path, entryName) ->
                    if (path.isNotBlank()) {
                        val f = File(path.trim())
                        if (f.exists() && f.isFile) {
                            zos.putNextEntry(ZipEntry(entryName))
                            FileInputStream(f).use { input -> input.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
        }
        return baos.toByteArray()
    }
}
