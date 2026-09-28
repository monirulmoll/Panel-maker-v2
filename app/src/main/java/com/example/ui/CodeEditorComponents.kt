package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import com.example.data.StudioProjectEntity

data class KotlinProjectFileTab(
    val relativePath: String,
    val shortName: String,
    val description: String
)

data class ParsedKotlinVisualState(
    val updatedProject: StudioProjectEntity,
    val updatedComponents: List<CanvasComponentEntity>
)

/**
 * Generates and parses the multi-file Kotlin source code structure matching the Studio Error APK
 * architecture (`MainActivity.kt`, `CanvasWorkspaceComponents.kt`, `InspectorComponents.kt`,
 * `MainViewModel.kt`, `LauncherScreen.kt`, `FloatingOverlayService.kt`).
 *
 * All generated Kotlin code is built directly from the user's visual screen widgets, and any edits
 * to the Kotlin widget code can be compiled right back into the visual screen and signed APK.
 */
object KotlinProjectCodeEngine {

    val FILE_TABS: List<KotlinProjectFileTab> = listOf(
        KotlinProjectFileTab(
            relativePath = "src/main/java/com/example/ui/CanvasWorkspaceComponents.kt",
            shortName = "CanvasWorkspaceComponents.kt",
            description = "Visual Screen Widget Layout & Compose Canvas Code"
        ),
        KotlinProjectFileTab(
            relativePath = "src/main/java/com/example/MainActivity.kt",
            shortName = "MainActivity.kt",
            description = "Main Activity & Studio Workspace Entry Point"
        ),
        KotlinProjectFileTab(
            relativePath = "src/main/java/com/example/ui/MainViewModel.kt",
            shortName = "MainViewModel.kt",
            description = "Project & Widget State Holder (ViewModel)"
        ),
        KotlinProjectFileTab(
            relativePath = "src/main/java/com/example/ui/InspectorComponents.kt",
            shortName = "InspectorComponents.kt",
            description = "Bottom Property Inspector & Widget Config Bindings"
        ),
        KotlinProjectFileTab(
            relativePath = "src/main/java/com/example/ui/LauncherScreen.kt",
            shortName = "LauncherScreen.kt",
            description = "Project Launcher & Config Setup Screen"
        ),
        KotlinProjectFileTab(
            relativePath = "src/main/java/com/example/service/FloatingOverlayService.kt",
            shortName = "FloatingOverlayService.kt",
            description = "System Floating Window Service Rendering Visual Screen Widgets"
        )
    )

    @JvmStatic
    fun generateKotlinFilesForProject(
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>
    ): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        map["src/main/java/com/example/ui/CanvasWorkspaceComponents.kt"] =
            generateCanvasWorkspaceKotlin(project, components)
        map["src/main/java/com/example/MainActivity.kt"] =
            generateMainActivityKotlin(project, components)
        map["src/main/java/com/example/ui/MainViewModel.kt"] =
            generateMainViewModelKotlin(project, components)
        map["src/main/java/com/example/ui/InspectorComponents.kt"] =
            generateInspectorComponentsKotlin(project, components)
        map["src/main/java/com/example/ui/LauncherScreen.kt"] =
            generateLauncherScreenKotlin(project)
        map["src/main/java/com/example/service/FloatingOverlayService.kt"] =
            generateFloatingOverlayServiceKotlin(project, components)
        return map
    }

    private fun generateCanvasWorkspaceKotlin(
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>
    ): String {
        return buildString {
            appendLine("package com.example.ui")
            appendLine()
            appendLine("import androidx.compose.foundation.background")
            appendLine("import androidx.compose.foundation.layout.*")
            appendLine("import androidx.compose.material3.*")
            appendLine("import androidx.compose.runtime.*")
            appendLine("import androidx.compose.ui.Alignment")
            appendLine("import androidx.compose.ui.Modifier")
            appendLine("import androidx.compose.ui.graphics.Color")
            appendLine("import androidx.compose.ui.unit.dp")
            appendLine()
            appendLine("// =====================================================================")
            appendLine("// VISUAL SCREEN CONFIGURATION (Edit values below & tap Compile Code)")
            appendLine("// =====================================================================")
            appendLine("object VisualScreenConfig {")
            appendLine("    const val PROJECT_NAME = \"${escapeKotlin(project.name)}\"")
            appendLine("    const val PACKAGE_ID = \"${escapeKotlin(project.packageName)}\"")
            appendLine("    const val WORKSPACE_PROJECT_NAME = \"${escapeKotlin(project.projectName)}\"")
            appendLine("    const val VERSION_CODE = ${project.versionCode}")
            appendLine("    const val VERSION_NAME = \"${escapeKotlin(project.versionName)}\"")
            appendLine("    const val MIN_SDK = ${project.minSdk}")
            appendLine("    const val TARGET_SDK = ${project.targetSdk}")
            appendLine("    const val OVERLAY_TITLE = \"${escapeKotlin(project.overlayTitle)}\"")
            appendLine("    const val CANVAS_WIDTH_DP = ${project.canvasWidthDp}")
            appendLine("    const val CANVAS_HEIGHT_DP = ${project.canvasHeightDp}")
            appendLine("    const val CANVAS_BG_HEX = \"${escapeKotlin(project.canvasBgColorHex)}\"")
            appendLine("    const val AUTO_FIX_SIZE = ${project.autoFixSize}")
            appendLine("}")
            appendLine()
            appendLine("data class VisualWidgetSpec(")
            appendLine("    val id: Long,")
            appendLine("    val type: String,")
            appendLine("    val label: String,")
            appendLine("    val posXDp: Int,")
            appendLine("    val posYDp: Int,")
            appendLine("    val widthDp: Int,")
            appendLine("    val heightDp: Int,")
            appendLine("    val bgColorHex: String,")
            appendLine("    val textColorHex: String,")
            appendLine("    val sliderMax: Int = 100,")
            appendLine("    val currentValue: String = \"0\"")
            appendLine(")")
            appendLine()
            appendLine("// =====================================================================")
            appendLine("// REAL WIDGETS ON VISUAL SCREEN (${components.size} configured widget(s))")
            appendLine("// You can edit label, posXDp, posYDp, widthDp, heightDp, bgColorHex,")
            appendLine("// textColorHex, or add new VisualWidgetSpec(...) blocks below!")
            appendLine("// =====================================================================")
            appendLine("val VISUAL_SCREEN_WIDGETS: List<VisualWidgetSpec> = listOf(")
            components.forEachIndexed { index, c ->
                val comma = if (index < components.lastIndex) "," else ""
                appendLine("    VisualWidgetSpec(")
                appendLine("        id = ${c.id}L,")
                appendLine("        type = \"${escapeKotlin(c.type)}\",")
                appendLine("        label = \"${escapeKotlin(c.label)}\",")
                appendLine("        posXDp = ${c.posXDp},")
                appendLine("        posYDp = ${c.posYDp},")
                appendLine("        widthDp = ${c.widthDp},")
                appendLine("        heightDp = ${c.heightDp},")
                appendLine("        bgColorHex = \"${escapeKotlin(c.bgColorHex)}\",")
                appendLine("        textColorHex = \"${escapeKotlin(c.textColorHex)}\",")
                appendLine("        sliderMax = ${c.sliderMax},")
                appendLine("        currentValue = \"${escapeKotlin(c.currentValue)}\"")
                appendLine("    )$comma")
            }
            appendLine(")")
            appendLine()
            appendLine("@Composable")
            appendLine("fun GeneratedVisualScreenCanvas() {")
            appendLine("    Box(")
            appendLine("        modifier = Modifier")
            appendLine("            .width(VisualScreenConfig.CANVAS_WIDTH_DP.dp)")
            appendLine("            .height(VisualScreenConfig.CANVAS_HEIGHT_DP.dp)")
            appendLine("            .background(Color(android.graphics.Color.parseColor(VisualScreenConfig.CANVAS_BG_HEX)))")
            appendLine("    ) {")
            components.forEachIndexed { idx, c ->
                appendLine("        // Widget #${idx + 1}: ${c.label} (${c.type}) at (${c.posXDp}dp, ${c.posYDp}dp) size ${c.widthDp}x${c.heightDp}dp")
                when (c.type) {
                    ComponentWidgetType.TOGGLE.name -> {
                        appendLine("        Row(")
                        appendLine("            modifier = Modifier")
                        appendLine("                .offset(x = ${c.posXDp}.dp, y = ${c.posYDp}.dp)")
                        appendLine("                .size(width = ${c.widthDp}.dp, height = ${c.heightDp}.dp),")
                        appendLine("            verticalAlignment = Alignment.CenterVertically")
                        appendLine("        ) {")
                        appendLine("            Text(text = \"${escapeKotlin(c.label)}\", modifier = Modifier.weight(1f))")
                        appendLine("            Switch(checked = ${c.currentValue == "1"}, onCheckedChange = { /* toggle state */ })")
                        appendLine("        }")
                    }
                    ComponentWidgetType.SLIDER.name -> {
                        appendLine("        Column(")
                        appendLine("            modifier = Modifier")
                        appendLine("                .offset(x = ${c.posXDp}.dp, y = ${c.posYDp}.dp)")
                        appendLine("                .size(width = ${c.widthDp}.dp, height = ${c.heightDp}.dp)")
                        appendLine("        ) {")
                        appendLine("            Text(text = \"${escapeKotlin(c.label)}: ${c.currentValue}/${c.sliderMax}\")")
                        appendLine("            Slider(value = ${c.currentValue.toFloatOrNull() ?: 0f}f, onValueChange = {}, valueRange = 0f..${c.sliderMax}f)")
                        appendLine("        }")
                    }
                    else -> {
                        appendLine("        Button(")
                        appendLine("            onClick = { /* trigger widget ${c.id} */ },")
                        appendLine("            modifier = Modifier")
                        appendLine("                .offset(x = ${c.posXDp}.dp, y = ${c.posYDp}.dp)")
                        appendLine("                .size(width = ${c.widthDp}.dp, height = ${c.heightDp}.dp)")
                        appendLine("        ) {")
                        appendLine("            Text(text = \"${escapeKotlin(c.label)}\")")
                        appendLine("        }")
                    }
                }
            }
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun generateMainActivityKotlin(
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>
    ): String {
        return buildString {
            appendLine("package com.example")
            appendLine()
            appendLine("import android.content.Intent")
            appendLine("import android.os.Build")
            appendLine("import android.os.Bundle")
            appendLine("import android.provider.Settings")
            appendLine("import androidx.activity.ComponentActivity")
            appendLine("import androidx.activity.compose.setContent")
            appendLine("import androidx.compose.foundation.layout.*")
            appendLine("import androidx.compose.material3.*")
            appendLine("import androidx.compose.ui.Alignment")
            appendLine("import androidx.compose.ui.Modifier")
            appendLine("import androidx.compose.ui.unit.dp")
            appendLine("import com.example.service.FloatingOverlayService")
            appendLine("import com.example.ui.GeneratedVisualScreenCanvas")
            appendLine("import com.example.ui.VisualScreenConfig")
            appendLine()
            appendLine("/**")
            appendLine(" * Studio Error — MainActivity.kt")
            appendLine(" * Hosts the visual screen for '${escapeKotlin(project.name)}' (${components.size} widgets)")
            appendLine(" * and launches the system Floating Overlay Window.")
            appendLine(" */")
            appendLine("class MainActivity : ComponentActivity() {")
            appendLine("    override fun onCreate(savedInstanceState: Bundle?) {")
            appendLine("        super.onCreate(savedInstanceState)")
            appendLine("        setContent {")
            appendLine("            MaterialTheme {")
            appendLine("                Scaffold(")
            appendLine("                    topBar = {")
            appendLine("                        TopAppBar(")
            appendLine("                            title = { Text(VisualScreenConfig.PROJECT_NAME) },")
            appendLine("                            actions = {")
            appendLine("                                Button(onClick = { startFloatingWindow() }) {")
            appendLine("                                    Text(\"Float (${components.size})\")")
            appendLine("                                }")
            appendLine("                            }")
            appendLine("                        )")
            appendLine("                    }")
            appendLine("                ) { innerPadding ->")
            appendLine("                    Box(")
            appendLine("                        modifier = Modifier")
            appendLine("                            .fillMaxSize()")
            appendLine("                            .padding(innerPadding),")
            appendLine("                        contentAlignment = Alignment.Center")
            appendLine("                    ) {")
            appendLine("                        GeneratedVisualScreenCanvas()")
            appendLine("                    }")
            appendLine("                }")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    private fun startFloatingWindow() {")
            appendLine("        if (Settings.canDrawOverlays(this)) {")
            appendLine("            val intent = Intent(this, FloatingOverlayService::class.java)")
            appendLine("            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {")
            appendLine("                startForegroundService(intent)")
            appendLine("            } else {")
            appendLine("                startService(intent)")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun generateMainViewModelKotlin(
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>
    ): String {
        return buildString {
            appendLine("package com.example.ui")
            appendLine()
            appendLine("import androidx.lifecycle.ViewModel")
            appendLine("import kotlinx.coroutines.flow.MutableStateFlow")
            appendLine("import kotlinx.coroutines.flow.StateFlow")
            appendLine("import kotlinx.coroutines.flow.asStateFlow")
            appendLine()
            appendLine("/**")
            appendLine(" * Studio Error — MainViewModel.kt")
            appendLine(" * Manages reactive state for '${escapeKotlin(project.overlayTitle)}' and its ${components.size} widget(s).")
            appendLine(" */")
            appendLine("class MainViewModel : ViewModel() {")
            appendLine("    private val _widgets = MutableStateFlow(VISUAL_SCREEN_WIDGETS)")
            appendLine("    val widgets: StateFlow<List<VisualWidgetSpec>> = _widgets.asStateFlow()")
            appendLine()
            appendLine("    fun updateWidgetValue(widgetId: Long, newValue: String) {")
            appendLine("        _widgets.value = _widgets.value.map { spec ->")
            appendLine("            if (spec.id == widgetId) spec.copy(currentValue = newValue) else spec")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun generateInspectorComponentsKotlin(
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>
    ): String {
        return buildString {
            appendLine("package com.example.ui")
            appendLine()
            appendLine("import androidx.compose.runtime.mutableStateMapOf")
            appendLine()
            appendLine("/**")
            appendLine(" * Studio Error — InspectorComponents.kt")
            appendLine(" * Binds each widget's configured UI properties and interactive state for '${escapeKotlin(project.name)}'.")
            appendLine(" */")
            appendLine("object InspectorBindings {")
            appendLine("    val activeWidgetStates = mutableStateMapOf<Long, Boolean>()")
            appendLine()
            components.forEachIndexed { idx, c ->
                appendLine("    // Widget #${idx + 1}: ${c.label} (${c.type}) -> size=${c.widthDp}x${c.heightDp}dp, bg=${c.bgColorHex}")
            }
            appendLine("    fun applyWidgetState(spec: VisualWidgetSpec, isEnabled: Boolean) {")
            appendLine("        activeWidgetStates[spec.id] = isEnabled")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun generateLauncherScreenKotlin(project: StudioProjectEntity): String {
        return buildString {
            appendLine("package com.example.ui")
            appendLine()
            appendLine("import androidx.compose.foundation.layout.*")
            appendLine("import androidx.compose.material3.*")
            appendLine("import androidx.compose.runtime.Composable")
            appendLine("import androidx.compose.ui.Modifier")
            appendLine("import androidx.compose.ui.unit.dp")
            appendLine()
            appendLine("/**")
            appendLine(" * Studio Error — LauncherScreen.kt")
            appendLine(" * Project Launcher metadata for '${escapeKotlin(project.name)}'.")
            appendLine(" */")
            appendLine("@Composable")
            appendLine("fun ProjectLauncherCard(onOpenWorkspace: () -> Unit) {")
            appendLine("    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {")
            appendLine("        Column(modifier = Modifier.padding(16.dp)) {")
            appendLine("            Text(text = VisualScreenConfig.PROJECT_NAME, style = MaterialTheme.typography.titleMedium)")
            appendLine("            Text(text = \"${escapeKotlin(project.overlayTitle)} • ${project.canvasWidthDp}x${project.canvasHeightDp} dp\")")
            appendLine("            Spacer(modifier = Modifier.height(8.dp))")
            appendLine("            Button(onClick = onOpenWorkspace) {")
            appendLine("                Text(\"Open Visual Workspace\")")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun generateFloatingOverlayServiceKotlin(
        project: StudioProjectEntity,
        components: List<CanvasComponentEntity>
    ): String {
        return buildString {
            appendLine("package com.example.service")
            appendLine()
            appendLine("import android.app.Service")
            appendLine("import android.content.Intent")
            appendLine("import android.graphics.Color")
            appendLine("import android.graphics.PixelFormat")
            appendLine("import android.os.Build")
            appendLine("import android.os.IBinder")
            appendLine("import android.view.Gravity")
            appendLine("import android.view.WindowManager")
            appendLine("import android.widget.*")
            appendLine("import com.example.ui.InspectorBindings")
            appendLine("import com.example.ui.VISUAL_SCREEN_WIDGETS")
            appendLine("import com.example.ui.VisualScreenConfig")
            appendLine()
            appendLine("/**")
            appendLine(" * Studio Error — FloatingOverlayService.kt")
            appendLine(" * Builds the floating WindowManager overlay matching the visual screen (${components.size} widgets).")
            appendLine(" */")
            appendLine("class FloatingOverlayService : Service() {")
            appendLine("    private var windowManager: WindowManager? = null")
            appendLine("    private var rootContainer: LinearLayout? = null")
            appendLine()
            appendLine("    override fun onCreate() {")
            appendLine("        super.onCreate()")
            appendLine("        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager")
            appendLine("        showOverlayWindow()")
            appendLine("    }")
            appendLine()
            appendLine("    private fun showOverlayWindow() {")
            appendLine("        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)")
            appendLine("            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY")
            appendLine("        else WindowManager.LayoutParams.TYPE_PHONE")
            appendLine()
            appendLine("        val params = WindowManager.LayoutParams(")
            appendLine("            WindowManager.LayoutParams.WRAP_CONTENT,")
            appendLine("            WindowManager.LayoutParams.WRAP_CONTENT,")
            appendLine("            overlayType,")
            appendLine("            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,")
            appendLine("            PixelFormat.TRANSLUCENT")
            appendLine("        ).apply {")
            appendLine("            gravity = Gravity.TOP or Gravity.START")
            appendLine("            x = 32")
            appendLine("            y = 160")
            appendLine("        }")
            appendLine()
            appendLine("        val container = LinearLayout(this).apply {")
            appendLine("            orientation = LinearLayout.VERTICAL")
            appendLine("            setBackgroundColor(Color.parseColor(VisualScreenConfig.CANVAS_BG_HEX))")
            appendLine("        }")
            appendLine()
            appendLine("        VISUAL_SCREEN_WIDGETS.forEach { spec ->")
            appendLine("            when (spec.type) {")
            appendLine("                \"TOGGLE\" -> {")
            appendLine("                    val sw = Switch(this).apply {")
            appendLine("                        text = spec.label")
            appendLine("                        isChecked = spec.currentValue == \"1\"")
            appendLine("                        setOnCheckedChangeListener { _, checked ->")
            appendLine("                            InspectorBindings.applyWidgetState(spec, checked)")
            appendLine("                        }")
            appendLine("                    }")
            appendLine("                    container.addView(sw)")
            appendLine("                }")
            appendLine("                \"SLIDER\" -> {")
            appendLine("                    val sb = SeekBar(this).apply {")
            appendLine("                        max = spec.sliderMax")
            appendLine("                    }")
            appendLine("                    container.addView(sb)")
            appendLine("                }")
            appendLine("                else -> {")
            appendLine("                    val btn = Button(this).apply {")
            appendLine("                        text = spec.label")
            appendLine("                        setOnClickListener {")
            appendLine("                            InspectorBindings.applyWidgetState(spec, true)")
            appendLine("                        }")
            appendLine("                    }")
            appendLine("                    container.addView(btn)")
            appendLine("                }")
            appendLine("            }")
            appendLine("        }")
            appendLine()
            appendLine("        rootContainer = container")
            appendLine("        windowManager?.addView(container, params)")
            appendLine("    }")
            appendLine()
            appendLine("    override fun onDestroy() {")
            appendLine("        rootContainer?.let { windowManager?.removeView(it) }")
            appendLine("        rootContainer = null")
            appendLine("        super.onDestroy()")
            appendLine("    }")
            appendLine()
            appendLine("    override fun onBind(intent: Intent?): IBinder? = null")
            appendLine("}")
        }
    }

    /**
     * Parses the user's edited Kotlin source code from `CanvasWorkspaceComponents.kt`
     * back into a `StudioProjectEntity` and `List<CanvasComponentEntity>` so that
     * compiling the edited Kotlin code updates the visual screen and the compiled APK to match!
     */
    fun parseEditedKotlinToVisualState(
        originalProject: StudioProjectEntity,
        originalComponents: List<CanvasComponentEntity>,
        editedFiles: Map<String, String>
    ): ParsedKotlinVisualState {
        val canvasKt = editedFiles["src/main/java/com/example/ui/CanvasWorkspaceComponents.kt"]
            ?: return ParsedKotlinVisualState(originalProject, originalComponents)

        val projectName = extractStringConst(canvasKt, "PROJECT_NAME") ?: originalProject.name
        val packageId = extractStringConst(canvasKt, "PACKAGE_ID") ?: originalProject.packageName
        val workspaceProjName = extractStringConst(canvasKt, "WORKSPACE_PROJECT_NAME") ?: originalProject.projectName
        val versionCode = extractIntConst(canvasKt, "VERSION_CODE")?.coerceAtLeast(1) ?: originalProject.versionCode
        val versionName = extractStringConst(canvasKt, "VERSION_NAME") ?: originalProject.versionName
        val minSdk = extractIntConst(canvasKt, "MIN_SDK")?.coerceIn(21, 36) ?: originalProject.minSdk
        val targetSdk = extractIntConst(canvasKt, "TARGET_SDK")?.coerceIn(minSdk, 36) ?: originalProject.targetSdk
        val overlayTitle = extractStringConst(canvasKt, "OVERLAY_TITLE") ?: originalProject.overlayTitle
        val canvasWidth = extractIntConst(canvasKt, "CANVAS_WIDTH_DP")?.coerceIn(180, 420) ?: originalProject.canvasWidthDp
        val canvasHeight = extractIntConst(canvasKt, "CANVAS_HEIGHT_DP")?.coerceIn(160, 640) ?: originalProject.canvasHeightDp
        val canvasBgHex = extractStringConst(canvasKt, "CANVAS_BG_HEX") ?: originalProject.canvasBgColorHex
        val autoFix = extractBooleanConst(canvasKt, "AUTO_FIX_SIZE") ?: originalProject.autoFixSize

        val updatedProject = originalProject.copy(
            name = projectName,
            packageName = packageId,
            projectName = workspaceProjName,
            versionCode = versionCode,
            versionName = versionName,
            minSdk = minSdk,
            targetSdk = targetSdk,
            overlayTitle = overlayTitle,
            canvasWidthDp = canvasWidth,
            canvasHeightDp = canvasHeight,
            canvasBgColorHex = canvasBgHex,
            autoFixSize = autoFix
        )

        val blockRegex = Regex("""VisualWidgetSpec\s*\((.*?)\)""", setOf(RegexOption.DOT_MATCHES_ALL))
        val matches = blockRegex.findAll(canvasKt).toList()
        if (matches.isEmpty()) {
            // Check if list was explicitly emptied (`listOf()`)
            val emptyListRegex = Regex("""VISUAL_SCREEN_WIDGETS\s*:\s*List<VisualWidgetSpec>\s*=\s*listOf\(\s*\)""")
            return if (emptyListRegex.containsMatchIn(canvasKt)) {
                ParsedKotlinVisualState(updatedProject, emptyList())
            } else {
                ParsedKotlinVisualState(updatedProject, originalComponents)
            }
        }

        val byId = originalComponents.associateBy { it.id }
        val parsedComponents = matches.mapIndexed { index, matchResult ->
            val body = matchResult.groupValues[1]
            val rawId = extractLongProp(body, "id") ?: 0L
            val existing = byId[rawId]

            val type = extractStringProp(body, "type")?.uppercase()
                ?.takeIf { it in setOf("BUTTON", "TOGGLE", "SLIDER", "TEXT", "INPUT", "IMAGE") }
                ?: existing?.type ?: "BUTTON"
            val label = extractStringProp(body, "label") ?: existing?.label ?: "$type #${index + 1}"
            val posX = extractIntProp(body, "posXDp") ?: existing?.posXDp ?: 10
            val posY = extractIntProp(body, "posYDp") ?: existing?.posYDp ?: (10 + index * 48)
            val width = (extractIntProp(body, "widthDp") ?: existing?.widthDp ?: 176).coerceIn(40, 380)
            val height = (extractIntProp(body, "heightDp") ?: existing?.heightDp ?: 44).coerceIn(28, 320)
            val bgHex = extractStringProp(body, "bgColorHex") ?: existing?.bgColorHex ?: "#334155"
            val textHex = extractStringProp(body, "textColorHex") ?: existing?.textColorHex ?: "#FFFFFF"
            val offsetHex = extractStringProp(body, "byteOffsetHex") ?: existing?.byteOffsetHex ?: "0x04"
            val onHex = extractStringProp(body, "onPayloadHex") ?: existing?.onPayloadHex ?: "0x01"
            val offHex = extractStringProp(body, "offPayloadHex") ?: existing?.offPayloadHex ?: "0x00"
            val sliderMax = (extractIntProp(body, "sliderMax") ?: existing?.sliderMax ?: 100).coerceIn(1, 10000)
            val currentVal = extractStringProp(body, "currentValue") ?: existing?.currentValue ?: "0"

            if (existing != null) {
                existing.copy(
                    type = type,
                    label = label,
                    posXDp = posX,
                    posYDp = posY,
                    widthDp = width,
                    heightDp = height,
                    bgColorHex = bgHex,
                    textColorHex = textHex,
                    byteOffsetHex = offsetHex,
                    onPayloadHex = onHex,
                    offPayloadHex = offHex,
                    sliderMax = sliderMax,
                    currentValue = currentVal
                )
            } else {
                CanvasComponentEntity(
                    id = 0L,
                    projectId = originalProject.id,
                    type = type,
                    label = label,
                    posXDp = posX,
                    posYDp = posY,
                    widthDp = width,
                    heightDp = height,
                    bgColorHex = bgHex,
                    textColorHex = textHex,
                    targetFilePath = originalProject.defaultTargetFilePath,
                    byteOffsetHex = offsetHex,
                    onPayloadHex = onHex,
                    offPayloadHex = offHex,
                    sliderMax = sliderMax,
                    currentValue = currentVal
                )
            }
        }

        return ParsedKotlinVisualState(updatedProject, parsedComponents)
    }

    private fun extractStringConst(src: String, name: String): String? {
        val m = Regex("""const\s+val\s+$name\s*=\s*"([^"]*)"""").find(src)
        return m?.groupValues?.get(1)
    }

    private fun extractIntConst(src: String, name: String): Int? {
        val m = Regex("""const\s+val\s+$name\s*=\s*(-?\d+)""").find(src)
        return m?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractBooleanConst(src: String, name: String): Boolean? {
        val m = Regex("""const\s+val\s+$name\s*=\s*(true|false)""").find(src)
        return m?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }

    private fun extractStringProp(body: String, prop: String): String? {
        val m = Regex("""$prop\s*=\s*"([^"]*)"""").find(body)
        return m?.groupValues?.get(1)
    }

    private fun extractIntProp(body: String, prop: String): Int? {
        val m = Regex("""$prop\s*=\s*(-?\d+)""").find(body)
        return m?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractLongProp(body: String, prop: String): Long? {
        val m = Regex("""$prop\s*=\s*(\d+)L?""").find(body)
        return m?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun escapeKotlin(raw: String): String {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}

@Composable
fun StudioEditCodeDialog(
    project: StudioProjectEntity,
    components: List<CanvasComponentEntity>,
    initialFiles: Map<String, String>,
    onDismiss: () -> Unit,
    onCompileCodeToVisualScreen: (Map<String, String>) -> Unit,
    onCompileCodeToApk: (Map<String, String>) -> Unit
) {
    val editableFiles = remember(project, components) {
        mutableStateMapOf<String, String>().apply {
            putAll(initialFiles)
        }
    }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val activeTab = KotlinProjectCodeEngine.FILE_TABS[selectedTabIndex]
    val currentCode = editableFiles[activeTab.relativePath].orEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("edit_code_dialog"),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.5.dp, Color(0xFF0288D1))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Top IDE Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Edit Code",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Edit Code — ${project.name} (Kotlin APK Structure)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${KotlinProjectCodeEngine.FILE_TABS.size} Kotlin files • Synced with Visual Screen (${components.size} widgets)",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val fresh = KotlinProjectCodeEngine.generateKotlinFilesForProject(project, components)
                            editableFiles.clear()
                            editableFiles.putAll(fresh)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Code from Visual Screen",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("close_edit_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Edit Code",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. Multi-File Kotlin Structure Tabs (Same structure as Studio Error APK)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KotlinProjectCodeEngine.FILE_TABS.forEachIndexed { index, tab ->
                        val isSelected = index == selectedTabIndex
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFF0288D1) else Color(0xFF1E293B),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .clickable { selectedTabIndex = index }
                                .testTag("kotlin_tab_${tab.shortName}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = tab.shortName,
                                    tint = if (isSelected) Color.White else Color(0xFF38BDF8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = tab.shortName,
                                    color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // 3. Active File Path + Quick Widget Code Snippet Inserters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = activeTab.relativePath,
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )

                    if (selectedTabIndex == 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SnippetActionButton(
                                label = "+ Switch",
                                onClick = {
                                    editableFiles[activeTab.relativePath] = appendWidgetSpecToKotlin(
                                        currentCode = currentCode,
                                        type = "TOGGLE",
                                        label = "Switch #${components.size + 1}",
                                        bgHex = "#FFFFFF",
                                        textHex = "#0F172A"
                                    )
                                }
                            )
                            SnippetActionButton(
                                label = "+ Button",
                                onClick = {
                                    editableFiles[activeTab.relativePath] = appendWidgetSpecToKotlin(
                                        currentCode = currentCode,
                                        type = "BUTTON",
                                        label = "Button #${components.size + 1}",
                                        bgHex = "#2563EB",
                                        textHex = "#FFFFFF"
                                    )
                                }
                            )
                            SnippetActionButton(
                                label = "+ Slider",
                                onClick = {
                                    editableFiles[activeTab.relativePath] = appendWidgetSpecToKotlin(
                                        currentCode = currentCode,
                                        type = "SLIDER",
                                        label = "SeekBar #${components.size + 1}",
                                        bgHex = "#FFFFFF",
                                        textHex = "#0F172A"
                                    )
                                }
                            )
                            SnippetActionButton(
                                label = "+ Text",
                                onClick = {
                                    editableFiles[activeTab.relativePath] = appendWidgetSpecToKotlin(
                                        currentCode = currentCode,
                                        type = "TEXT",
                                        label = "TextView #${components.size + 1}",
                                        bgHex = "#EEF2FF",
                                        textHex = "#1E293B"
                                    )
                                }
                            )
                        }
                    }
                }

                // 4. Main Editable Kotlin Source Code Area with Line Numbers
                val verticalScroll = rememberScrollState()
                val horizontalScroll = rememberScrollState()
                val lineCount = remember(currentCode) {
                    currentCode.lines().size.coerceAtLeast(1)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0B1120))
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScroll)
                    ) {
                        // Line numbers gutter
                        Column(
                            modifier = Modifier
                                .background(Color(0xFF0F172A))
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            for (lineNum in 1..lineCount) {
                                Text(
                                    text = lineNum.toString(),
                                    color = Color(0xFF475569),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Editable Kotlin source field
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(horizontalScroll)
                                .padding(8.dp)
                        ) {
                            BasicTextField(
                                value = currentCode,
                                onValueChange = { updatedText ->
                                    editableFiles[activeTab.relativePath] = updatedText
                                },
                                textStyle = TextStyle(
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                cursorBrush = SolidColor(Color(0xFF38BDF8)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_code_text_field")
                            )
                        }
                    }
                }

                // 5. Bottom Compile & Sync Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onCompileCodeToVisualScreen(editableFiles.toMap())
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0288D1),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compile_code_to_visual_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Apply Code to Visual Screen",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Apply to Visual Screen",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = {
                            onCompileCodeToApk(editableFiles.toMap())
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00C853),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compile_code_to_apk_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Compile Code to Signed APK",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Compile Code → APK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SnippetActionButton(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = Color(0xFF0288D1).copy(alpha = 0.25f),
        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = label,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                color = Color(0xFF38BDF8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun appendWidgetSpecToKotlin(
    currentCode: String,
    type: String,
    label: String,
    bgHex: String,
    textHex: String
): String {
    val listStartMarker = "val VISUAL_SCREEN_WIDGETS: List<VisualWidgetSpec> = listOf("
    val startIdx = currentCode.indexOf(listStartMarker)
    if (startIdx == -1) return currentCode
    val closeIdx = currentCode.indexOf("\n)", startIndex = startIdx)
    if (closeIdx == -1) return currentCode

    val between = currentCode.substring(startIdx + listStartMarker.length, closeIdx).trim()
    val prefixComma = if (between.isEmpty()) "" else ",\n"
    val newSpecBlock = buildString {
        append(prefixComma)
        appendLine("    VisualWidgetSpec(")
        appendLine("        id = 0L,")
        appendLine("        type = \"$type\",")
        appendLine("        label = \"$label\",")
        appendLine("        posXDp = 12,")
        appendLine("        posYDp = 12,")
        appendLine("        widthDp = 186,")
        appendLine("        heightDp = 44,")
        appendLine("        bgColorHex = \"$bgHex\",")
        appendLine("        textColorHex = \"$textHex\",")
        appendLine("        sliderMax = 100,")
        appendLine("        currentValue = \"0\"")
        append("    )")
    }
    return currentCode.substring(0, closeIdx) + newSpecBlock + currentCode.substring(closeIdx)
}
