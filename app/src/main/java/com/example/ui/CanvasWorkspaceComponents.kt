package com.example.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditAttributes
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import com.example.data.StudioProjectEntity
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

data class SketchwarePaletteEntry(
    val title: String,
    val icon: ImageVector,
    val iconTint: Color,
    val widgetType: ComponentWidgetType,
    val customWidthDp: Int? = null,
    val customHeightDp: Int? = null,
    val customBgHex: String? = null,
    val customTextHex: String? = null,
    val testTag: String? = null
)

/**
 * Top bar showing ONLY the widgets currently added by the user.
 * Tapping any widget chip immediately selects that exact widget and opens its Edit Mode inspector.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComponentTrackerBanner(
    summary: ComponentCountSummary,
    components: List<CanvasComponentEntity> = emptyList(),
    selectedComponentId: Long? = null,
    onSelectComponentForEdit: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("component_tracker_banner"),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = Color(0xFF0288D1),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = "Widgets (${summary.totalCount})",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("total_component_count_text")
                )
            }

            if (components.isEmpty()) {
                Text(
                    text = "No widgets added yet — tap left palette to add",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            } else {
                components.forEach { comp ->
                    val isSelected = comp.id == selectedComponentId
                    Surface(
                        onClick = { onSelectComponentForEdit(comp.id) },
                        color = if (isSelected) Color(0xFF0288D1) else Color(0xFFE0F2FE),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) Color(0xFF0369A1) else Color(0xFF7DD3FC)
                        ),
                        modifier = Modifier.testTag("top_widget_chip_${comp.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = comp.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF0369A1)
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

/**
 * Sketchware-style Left Vertical Sidebar Palette + Center Phone Device Mockup Workspace
 * matching the reference screenshot.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SketchwareStudioSplitWorkspace(
    project: StudioProjectEntity,
    components: List<CanvasComponentEntity>,
    selectedComponentId: Long?,
    isLivePreviewMode: Boolean,
    statusToast: String,
    onAddPaletteEntry: (SketchwarePaletteEntry) -> Unit,
    onSelectComponent: (Long?) -> Unit,
    onMoveComponent: (CanvasComponentEntity, Int, Int) -> Unit,
    onResizeComponent: (CanvasComponentEntity, Int, Int) -> Unit = { _, _, _ -> },
    onResizeCanvas: (Int, Int) -> Unit,
    onToggleAutoFixSize: () -> Unit = {},
    onOpenEditFloatingPanel: () -> Unit = {},
    onTriggerComponentLive: (CanvasComponentEntity, String?) -> Unit,
    onClearCanvas: () -> Unit,
    modifier: Modifier = Modifier
) {
    val layoutEntries = remember {
        listOf(
            SketchwarePaletteEntry("Linear(H)", Icons.Default.ViewColumn, Color(0xFF475569), ComponentWidgetType.TEXT, 196, 40, "#F8FAFC", "#334155"),
            SketchwarePaletteEntry("Linear(V)", Icons.Default.ViewStream, Color(0xFF475569), ComponentWidgetType.TEXT, 196, 80, "#F8FAFC", "#334155"),
            SketchwarePaletteEntry("Scroll(H)", Icons.Default.SwapHoriz, Color(0xFF475569), ComponentWidgetType.TEXT, 196, 44, "#F1F5F9", "#334155"),
            SketchwarePaletteEntry("Scroll(V)", Icons.Default.SwapVert, Color(0xFF475569), ComponentWidgetType.TEXT, 196, 84, "#F1F5F9", "#334155"),
            SketchwarePaletteEntry("RadioGroup", Icons.Default.RadioButtonChecked, Color(0xFF0288D1), ComponentWidgetType.TOGGLE, 196, 44, "#FFFFFF", "#0F172A")
        )
    }

    val androidXEntries = remember {
        listOf(
            SketchwarePaletteEntry("TabLayout", Icons.Default.Tab, Color(0xFF0288D1), ComponentWidgetType.BUTTON, 196, 48, "#0288D1", "#FFFFFF"),
            SketchwarePaletteEntry("BottomNavigationView", Icons.Default.ViewDay, Color(0xFFF59E0B), ComponentWidgetType.BUTTON, 196, 48, "#1E293B", "#FFFFFF"),
            SketchwarePaletteEntry("CollapsingToolbar", Icons.Default.ViewStream, Color(0xFF0288D1), ComponentWidgetType.TEXT, 196, 46, "#0288D1", "#FFFFFF"),
            SketchwarePaletteEntry("CardView", Icons.Default.CreditCard, Color(0xFF7C3AED), ComponentWidgetType.TEXT, 196, 54, "#FFFFFF", "#0F172A"),
            SketchwarePaletteEntry("TextInputLayout", Icons.Default.EditAttributes, Color(0xFFF59E0B), ComponentWidgetType.INPUT, 196, 44, "#FFFFFF", "#0F172A"),
            SketchwarePaletteEntry("SwipeRefreshLayout", Icons.Default.Refresh, Color(0xFF0288D1), ComponentWidgetType.TOGGLE, 196, 44, "#FFFFFF", "#0F172A")
        )
    }

    val widgetEntries = remember {
        listOf(
            SketchwarePaletteEntry("Switch", Icons.Default.CheckBox, Color(0xFF10B981), ComponentWidgetType.TOGGLE, 196, 44, "#FFFFFF", "#0F172A", "add_toggle_widget"),
            SketchwarePaletteEntry("Button", Icons.Default.SmartButton, Color(0xFF00C853), ComponentWidgetType.BUTTON, 180, 42, "#334155", "#FFFFFF", "add_button_widget"),
            SketchwarePaletteEntry("Slide Bar 0-100", Icons.Default.LinearScale, Color(0xFFEF4444), ComponentWidgetType.SLIDER, 196, 54, "#FFFFFF", "#0F172A", "add_slider_widget"),
            SketchwarePaletteEntry("Open Link", Icons.Default.Add, Color(0xFF0288D1), ComponentWidgetType.LINK, 190, 42, "#0F172A", "#38BDF8", "add_link_widget"),
            SketchwarePaletteEntry("TextView", Icons.Default.TextFields, Color(0xFF334155), ComponentWidgetType.TEXT, 170, 34, "#FDE047", "#0288D1", "add_text_widget"),
            SketchwarePaletteEntry("EditText", Icons.Default.EditAttributes, Color(0xFF475569), ComponentWidgetType.INPUT, 190, 42, "#FFFFFF", "#0F172A", "add_input_widget"),
            SketchwarePaletteEntry("ImageView", Icons.Default.Image, Color(0xFF8B5CF6), ComponentWidgetType.IMAGE, 64, 64, "#1E293B", "#FFFFFF", "add_image_widget")
        )
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFE2E8F0))
    ) {
        // LEFT COLUMN: SKETCHWARE VERTICAL COMPONENT PALETTE SIDEBAR
        Surface(
            modifier = Modifier
                .width(104.dp)
                .fillMaxHeight(),
            color = Color(0xFFF8FAFC),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Dual Palette Header Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(Color(0xFFE2E8F0))
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF0288D1)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Palette",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFFF1F5F9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Saved Widgets",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Scrollable Vertical Palette Items (Widgets, Layouts, AndroidX)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PaletteSectionHeader("Widgets")
                    widgetEntries.forEach { entry ->
                        SketchwarePaletteItemCard(
                            entry = entry,
                            onClick = { onAddPaletteEntry(entry) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    PaletteSectionHeader("Layouts")
                    layoutEntries.forEach { entry ->
                        SketchwarePaletteItemCard(
                            entry = entry,
                            onClick = { onAddPaletteEntry(entry) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    PaletteSectionHeader("AndroidX")
                    androidXEntries.forEach { entry ->
                        SketchwarePaletteItemCard(
                            entry = entry,
                            onClick = { onAddPaletteEntry(entry) }
                        )
                    }
                }

                // Bottom-Left Pinned Option: Auto Fix Size Toggle (Takes ZERO height from the phone canvas!)
                Surface(
                    onClick = onToggleAutoFixSize,
                    color = if (project.autoFixSize) Color(0xFF00C853) else Color(0xFF1E293B),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .testTag("bottom_auto_fix_size_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Auto Fix Size",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (project.autoFixSize) "Auto Size: ON" else "Auto Size: OFF",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // RIGHT AREA: REALISTIC ANDROID PHONE BEZEL + XML TOOLBAR + FLOATING CANVAS
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Black Phone Bezel Frame
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF111827),
                border = BorderStroke(2.dp, Color(0xFF334155)),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                ) {
                    // Slim Blue Sketchware XML File Bar (e.g., project_name.xml)
                    val xmlFileName = remember(project.name) {
                        project.name.trim().lowercase(Locale.US)
                            .replace(Regex("[^a-z0-9]+"), "_")
                            .trim('_')
                            .ifEmpty { "floating" } + ".xml"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0288D1))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$xmlFileName (${components.size})",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = onOpenEditFloatingPanel,
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.testTag("edit_floating_panel_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Floating Panel Name & Logo",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "Edit Panel Name & Logo",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (components.isNotEmpty()) {
                                Text(
                                    text = "Clear All",
                                    color = Color(0xFFFECACA),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { onClearCanvas() }
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                        .testTag("clear_canvas_button")
                                )
                            }
                        }
                    }

                    // White Phone Screen Interior holding the Floating Window Canvas (No outer competing scroll!)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .padding(6.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        InteractiveFloatingCanvasWorkspace(
                            project = project,
                            components = components,
                            selectedComponentId = selectedComponentId,
                            isLivePreviewMode = isLivePreviewMode,
                            onSelectComponent = onSelectComponent,
                            onMoveComponent = onMoveComponent,
                            onResizeComponent = onResizeComponent,
                            onResizeCanvas = onResizeCanvas,
                            onOpenEditFloatingPanel = onOpenEditFloatingPanel,
                            onTriggerComponentLive = onTriggerComponentLive
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PaletteSectionHeader(title: String) {
    Text(
        text = title,
        color = Color(0xFF10B981),
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun SketchwarePaletteItemCard(
    entry: SketchwarePaletteEntry,
    onClick: () -> Unit
) {
    val cardMod = if (entry.testTag != null) {
        Modifier
            .fillMaxWidth()
            .testTag(entry.testTag)
    } else {
        Modifier.fillMaxWidth()
    }

    Surface(
        onClick = onClick,
        modifier = cardMod,
        shape = RoundedCornerShape(4.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = entry.title,
                tint = entry.iconTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = entry.title,
                color = Color(0xFF1E293B),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                lineHeight = 12.sp,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * REQUIREMENT 2 & 3 + IMAGE-CROP STYLE FLOATING WINDOW RESIZE:
 * 100% Blank Canvas Workspace when a project opens (zero hardcoded controls).
 * Surrounded by Image-Crop style corner/edge drag handles so the user can smoothly
 * shrink or expand the Floating Mod Menu body from its endpoints.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InteractiveFloatingCanvasWorkspace(
    project: StudioProjectEntity,
    components: List<CanvasComponentEntity>,
    selectedComponentId: Long?,
    isLivePreviewMode: Boolean,
    onSelectComponent: (Long?) -> Unit,
    onMoveComponent: (CanvasComponentEntity, Int, Int) -> Unit,
    onResizeComponent: (CanvasComponentEntity, Int, Int) -> Unit = { _, _, _ -> },
    onResizeCanvas: (Int, Int) -> Unit,
    onOpenEditFloatingPanel: () -> Unit = {},
    onTriggerComponentLive: (CanvasComponentEntity, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val onResizeState by rememberUpdatedState(onResizeCanvas)
    var liveCanvasDeltaWdp by remember(project.canvasWidthDp) { mutableIntStateOf(0) }
    var liveCanvasDeltaHdp by remember(project.canvasHeightDp) { mutableIntStateOf(0) }
    var cropAccumW by remember { mutableFloatStateOf(0f) }
    var cropAccumH by remember { mutableFloatStateOf(0f) }
    var isPreviewMinimizedToGoalLogo by remember { mutableStateOf(false) }

    val floatingLogoBitmap = remember(project.floatingLogoPath) {
        if (project.floatingLogoPath.isNotBlank()) {
            val f = File(project.floatingLogoPath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } else null
    }

    val displayCanvasWidthDp = (project.canvasWidthDp + liveCanvasDeltaWdp).coerceIn(170, 420)
    val displayCanvasHeightDp = (project.canvasHeightDp + liveCanvasDeltaHdp).coerceIn(160, 620)

    val canvasBgColor = parseComposeColor(project.canvasBgColorHex, Color.White)
    val isAutoFixSize = project.autoFixSize

    // Visual Screen Overflow Detection:
    // 1. Scroll is strictly 0 (disabled) while all widgets fit inside 0 .. displayCanvasHeightDp.
    // 2. As soon as ANY widget goes outside the top (< 0) or bottom (> displayCanvasHeightDp) visual screen,
    //    scroll turns ON automatically for both bottom and top visual regions.
    val minWidgetTopDp = remember(components) {
        components.minOfOrNull { it.posYDp } ?: 0
    }
    val maxWidgetBottomDp = remember(components) {
        components.maxOfOrNull { it.posYDp + it.heightDp } ?: 0
    }
    val isVisualOverflowing = remember(components, minWidgetTopDp, maxWidgetBottomDp, displayCanvasHeightDp) {
        components.isNotEmpty() && (minWidgetTopDp < 0 || maxWidgetBottomDp > displayCanvasHeightDp)
    }
    val topOverflowShiftDp = remember(minWidgetTopDp) {
        if (minWidgetTopDp < 0) (-minWidgetTopDp + 8) else 0
    }
    val scrollableContentHeightDp = remember(isVisualOverflowing, maxWidgetBottomDp, topOverflowShiftDp, displayCanvasHeightDp) {
        if (isVisualOverflowing) {
            maxOf(displayCanvasHeightDp, maxWidgetBottomDp + topOverflowShiftDp + 8)
        } else {
            displayCanvasHeightDp
        }
    }

    val canvasScrollState = rememberScrollState()
    var prevComponentCount by remember { mutableIntStateOf(components.size) }

    LaunchedEffect(isVisualOverflowing) {
        if (!isVisualOverflowing && canvasScrollState.value != 0) {
            canvasScrollState.scrollTo(0)
        }
    }

    LaunchedEffect(components.size, isVisualOverflowing, scrollableContentHeightDp) {
        if (components.size > prevComponentCount && isVisualOverflowing) {
            val targetScroll = canvasScrollState.maxValue
            if (targetScroll > 0) {
                canvasScrollState.animateScrollTo(targetScroll)
            }
        }
        prevComponentCount = components.size
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        if (isPreviewMinimizedToGoalLogo) {
            // Live Preview of the Collapsed Round ("Goal" / गोल) Floating Logo Bubble in Studio!
            val bubbleLabel = project.overlayTitle.trim().ifEmpty { project.name.trim().ifEmpty { "Float" } }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2563EB))
                        .border(BorderStroke(2.dp, Color(0xFF0288D1)), CircleShape)
                        .clickable { isPreviewMinimizedToGoalLogo = false }
                        .testTag("studio_preview_goal_logo_bubble"),
                    contentAlignment = Alignment.Center
                ) {
                    if (floatingLogoBitmap != null) {
                        Image(
                            bitmap = floatingLogoBitmap,
                            contentDescription = "Goal Logo Bubble",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = bubbleLabel,
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
                Text(
                    text = "Tap round Goal Logo to expand panel back",
                    color = Color(0xFF475569),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { isPreviewMinimizedToGoalLogo = false }
                )
            }
        } else {
        // Outer Box holding the Floating Mod Menu Card + Image-Crop Endpoint Handles
        Box(
            modifier = Modifier
                .padding(end = 10.dp, bottom = 10.dp)
        ) {
            Card(
                modifier = Modifier
                    .width(displayCanvasWidthDp.dp)
                    .testTag("floating_canvas_window"),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = BorderStroke(
                    width = 2.dp,
                    color = if (isLivePreviewMode) Color(0xFF10B981) else Color(0xFF0288D1)
                )
            ) {
                Column {
                    // Floating Panel Header Bar: Shows circular Floating Goal Logo (if set), Floating Window Name,
                    // '✏️' Edit button to edit Floating Panel Name & Logo, and '✕' button to collapse into round Goal Logo!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2563EB))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenEditFloatingPanel() }
                                .testTag("canvas_header_edit_panel_button")
                        ) {
                            if (floatingLogoBitmap != null) {
                                Image(
                                    bitmap = floatingLogoBitmap,
                                    contentDescription = "Floating Panel Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .border(BorderStroke(1.dp, Color.White), CircleShape)
                                )
                            }
                            Text(
                                text = project.overlayTitle.ifBlank {
                                    project.name.ifBlank { "Set Floating Panel Name & Logo" }
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Floating Panel Name & Logo",
                                tint = Color(0xFFBAE6FD),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        Text(
                            text = "✕",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { isPreviewMinimizedToGoalLogo = true }
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                .testTag("canvas_header_minimize_goal_button")
                        )
                    }

                    // THE MOBILE CANVAS WORKSPACE (100% EMPTY BACKGROUND — ONLY USER-CREATED WIDGETS)
                    Box(
                        modifier = Modifier
                            .width(displayCanvasWidthDp.dp)
                            .height(displayCanvasHeightDp.dp)
                            .background(canvasBgColor)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                onSelectComponent(null)
                            }
                            .testTag("blank_canvas_surface")
                    ) {
                        if (components.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("empty_canvas_placeholder")
                            )
                        } else {
                            // Scroll is ZERO (enabled = false) until any widget goes outside the visual screen;
                            // as soon as a widget goes outside top or bottom, scroll turns ON (enabled = true)!
                            val scrollContainerModifier = if (isVisualOverflowing) {
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(state = canvasScrollState, enabled = true)
                            } else {
                                Modifier.fillMaxSize()
                            }
                            Box(
                                modifier = scrollContainerModifier
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(displayCanvasWidthDp.dp)
                                        .height(scrollableContentHeightDp.dp)
                                ) {
                                    components.forEach { comp ->
                                        CanvasElementView(
                                            component = comp,
                                            topOverflowShiftDp = topOverflowShiftDp,
                                            isVisualOverflowing = isVisualOverflowing,
                                            isSelected = comp.id == selectedComponentId,
                                            isLivePreviewMode = false,
                                            isAutoFixSize = isAutoFixSize,
                                            onTapElement = {
                                                onSelectComponent(comp.id)
                                            },
                                            onToggleOnOffDirect = {
                                                onSelectComponent(comp.id)
                                                onTriggerComponentLive(comp, null)
                                            },
                                            onDragDeltaDp = { dxDp, dyDp ->
                                                onMoveComponent(comp, comp.posXDp + dxDp, comp.posYDp + dyDp)
                                            },
                                            onResizeDeltaDp = { dwDp, dhDp ->
                                                onResizeComponent(comp, dwDp, dhDp)
                                            },
                                            onSliderValueChange = { newSliderVal ->
                                                onTriggerComponentLive(comp, newSliderVal.toString())
                                            }
                                        )
                                    }
                                }
                            }

                            // Top & Bottom Visual Overflow Scroll Indicators (Only visible when scroll is ON)
                            if (isVisualOverflowing) {
                                if (canvasScrollState.value > 0) {
                                    Surface(
                                        color = Color(0xFF0F172A).copy(alpha = 0.78f),
                                        shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp),
                                        modifier = Modifier.align(Alignment.TopCenter)
                                    ) {
                                        Text(
                                            text = "▲ Scroll Up",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                if (canvasScrollState.value < canvasScrollState.maxValue) {
                                    Surface(
                                        color = Color(0xFF0F172A).copy(alpha = 0.78f),
                                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    ) {
                                        Text(
                                            text = "▼ Scroll Down",
                                            color = Color(0xFF4ADE80),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Manual Crop Handles on Floating Window (Hidden & disabled when Auto Fix Size is ON)
            // Uses stationary pointerInput + commit on release so handles NEVER shake/oscillate!
            if (!isAutoFixSize) {
                // 1. RIGHT EDGE IMAGE-CROP RESIZE HANDLE (Width +/-)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 7.dp)
                        .width(16.dp)
                        .height(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0288D1))
                        .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                        .pointerInput(project.canvasWidthDp) {
                            detectDragGestures(
                                onDragStart = {
                                    cropAccumW = 0f
                                    liveCanvasDeltaWdp = 0
                                },
                                onDragEnd = {
                                    if (liveCanvasDeltaWdp != 0) {
                                        val finalW = liveCanvasDeltaWdp
                                        liveCanvasDeltaWdp = 0
                                        onResizeState(finalW, 0)
                                    }
                                },
                                onDragCancel = {
                                    liveCanvasDeltaWdp = 0
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    cropAccumW += dragAmount.x
                                    val stepWDp = (cropAccumW / density.density).roundToInt()
                                    if (stepWDp != 0) {
                                        cropAccumW = 0f
                                        onResizeState(stepWDp, 0)
                                    }
                                }
                            )
                        }
                        .testTag("crop_handle_right_edge"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⋮", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }

                // 2. BOTTOM EDGE IMAGE-CROP RESIZE HANDLE (Height +/-)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 7.dp)
                        .width(54.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0288D1))
                        .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
                        .pointerInput(project.canvasHeightDp) {
                            detectDragGestures(
                                onDragStart = {
                                    cropAccumH = 0f
                                    liveCanvasDeltaHdp = 0
                                },
                                onDragEnd = {
                                    if (liveCanvasDeltaHdp != 0) {
                                        val finalH = liveCanvasDeltaHdp
                                        liveCanvasDeltaHdp = 0
                                        onResizeState(0, finalH)
                                    }
                                },
                                onDragCancel = {
                                    liveCanvasDeltaHdp = 0
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    cropAccumH += dragAmount.y
                                    val stepHDp = (cropAccumH / density.density).roundToInt()
                                    if (stepHDp != 0) {
                                        cropAccumH = 0f
                                        onResizeState(0, stepHDp)
                                    }
                                }
                            )
                        }
                        .testTag("crop_handle_bottom_edge"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⋯", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }

                // 3. BOTTOM-RIGHT CORNER ENDPOINT CROP HANDLE (Width & Height simultaneously)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 8.dp, y = 8.dp)
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF00C853))
                        .border(2.dp, Color.White, RoundedCornerShape(6.dp))
                        .pointerInput(project.canvasWidthDp, project.canvasHeightDp) {
                            detectDragGestures(
                                onDragStart = {
                                    cropAccumW = 0f
                                    cropAccumH = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    cropAccumW += dragAmount.x
                                    cropAccumH += dragAmount.y
                                    val stepWDp = (cropAccumW / density.density).roundToInt()
                                    val stepHDp = (cropAccumH / density.density).roundToInt()
                                    if (stepWDp != 0 || stepHDp != 0) {
                                        cropAccumW = 0f
                                        cropAccumH = 0f
                                        onResizeState(stepWDp, stepHDp)
                                    }
                                }
                            )
                        }
                        .testTag("crop_handle_bottom_right_corner"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("↘", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        }
    }
    }
}

@Composable
private fun CanvasElementView(
    component: CanvasComponentEntity,
    topOverflowShiftDp: Int = 0,
    isVisualOverflowing: Boolean = false,
    isSelected: Boolean,
    isLivePreviewMode: Boolean,
    isAutoFixSize: Boolean = false,
    onTapElement: () -> Unit,
    onToggleOnOffDirect: () -> Unit,
    onDragDeltaDp: (Int, Int) -> Unit,
    onResizeDeltaDp: (Int, Int) -> Unit = { _, _ -> },
    onSliderValueChange: (Int) -> Unit
) {
    val density = LocalDensity.current
    val currentComp by rememberUpdatedState(component)
    val onDragState by rememberUpdatedState(onDragDeltaDp)
    val onResizeState by rememberUpdatedState(onResizeDeltaDp)
    val onTapState by rememberUpdatedState(onTapElement)
    val onToggleState by rememberUpdatedState(onToggleOnOffDirect)

    // Smooth, jitter-free drag state:
    // During finger drag, the pointerInput box stays fixed at (posXDp, posYDp) so its local coordinate
    // space NEVER shifts mid-gesture, while graphicsLayer translates the visual widget at 60fps.
    // On drag release, the exact dp delta is committed once to Room DB!
    var liveDragXPx by remember(component.id, component.posXDp, component.posYDp) { mutableFloatStateOf(0f) }
    var liveDragYPx by remember(component.id, component.posXDp, component.posYDp) { mutableFloatStateOf(0f) }
    var resizeAccumW by remember(component.id) { mutableFloatStateOf(0f) }
    var resizeAccumH by remember(component.id) { mutableFloatStateOf(0f) }

    val bgColor = parseComposeColor(component.bgColorHex, Color.White)
    val txtColor = parseComposeColor(component.textColorHex, Color(0xFF0F172A))

    val isCheckedOn = component.currentValue == "1" || component.currentValue.equals("true", ignoreCase = true)

    val customBitmap = remember(component.customImagePath) {
        if (component.customImagePath.isNotBlank()) {
            val file = File(component.customImagePath.trim())
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } else null
        } else null
    }

    val borderColor = when {
        isSelected -> Color(0xFF0288D1)
        component.type == ComponentWidgetType.TOGGLE.name && isCheckedOn -> Color(0xFF00C853)
        component.type == ComponentWidgetType.TOGGLE.name && !isCheckedOn -> Color(0xFF64748B)
        component.type == ComponentWidgetType.BUTTON.name && isCheckedOn -> Color(0xFF00C853)
        else -> Color(0xFF475569)
    }

    val canDragDirectly = !isLivePreviewMode && !isAutoFixSize && (!isVisualOverflowing || isSelected)
    val dragGestureModifier = if (canDragDirectly) {
        Modifier.pointerInput(component.id, isLivePreviewMode, isAutoFixSize, isVisualOverflowing, isSelected) {
            detectDragGestures(
                onDragStart = {
                    liveDragXPx = 0f
                    liveDragYPx = 0f
                    onTapState()
                },
                onDragEnd = {
                    val totalDxDp = (liveDragXPx / density.density).roundToInt()
                    val totalDyDp = (liveDragYPx / density.density).roundToInt()
                    liveDragXPx = 0f
                    liveDragYPx = 0f
                    if (totalDxDp != 0 || totalDyDp != 0) {
                        onDragState(totalDxDp, totalDyDp)
                    }
                },
                onDragCancel = {
                    liveDragXPx = 0f
                    liveDragYPx = 0f
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    liveDragXPx += dragAmount.x
                    liveDragYPx += dragAmount.y
                }
            )
        }
    } else {
        Modifier
    }

    // Outer container positioned at (posXDp, posYDp + topOverflowShiftDp)
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = with(density) { currentComp.posXDp.dp.roundToPx() },
                    y = with(density) { (currentComp.posYDp + topOverflowShiftDp).dp.roundToPx() }
                )
            }
            .size(width = component.widthDp.dp, height = component.heightDp.dp)
            .then(dragGestureModifier)
            .graphicsLayer {
                translationX = liveDragXPx
                translationY = liveDragYPx
            }
    ) {
        // Inner Widget Body
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .border(
                    width = if (isSelected) 2.5.dp else 1.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable { onTapState() }
                .testTag("canvas_element_${component.id}")
        ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap,
                contentDescription = component.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        when (component.type) {
            ComponentWidgetType.TOGGLE.name -> {
                // Classic Switch Toggle design (with STATE: ON / STATE: OFF removed)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isCheckedOn) Color(0xFFECFDF5) else Color.White)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = component.label,
                        color = Color(0xFF1E293B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clickable { onToggleState() }
                            .testTag("toggle_state_button_${component.id}")
                    ) {
                        Surface(
                            color = if (isCheckedOn) Color(0xFF00C853) else Color(0xFFEF4444),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isCheckedOn) "ON" else "OFF",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        // Switch Track + Thumb
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isCheckedOn) Color(0xFF00C853) else Color(0xFFCBD5E1))
                                .padding(2.dp),
                            contentAlignment = if (isCheckedOn) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                shadowElevation = 1.dp,
                                modifier = Modifier.size(16.dp)
                            ) {
                                if (isCheckedOn) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFF00C853),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ComponentWidgetType.SLIDER.name -> {
                // WIDGET 3: 0 to 100 Slide Bar Widget
                val maxRange = component.sliderMax.coerceAtLeast(1)
                val sliderVal = (component.currentValue.toFloatOrNull() ?: 50f).coerceIn(0f, maxRange.toFloat())
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = component.label,
                            color = Color(0xFF1E293B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            color = Color(0xFFEF4444),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${sliderVal.roundToInt()} / $maxRange",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "0",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = sliderVal,
                            onValueChange = { newVal ->
                                onSliderValueChange(newVal.roundToInt())
                            },
                            valueRange = 0f..maxRange.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFEF4444),
                                activeTrackColor = Color(0xFFEF4444),
                                inactiveTrackColor = Color(0xFFFECACA)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                        )
                        Text(
                            text = "$maxRange",
                            color = Color(0xFF64748B),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            ComponentWidgetType.INPUT.name -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = component.label.ifEmpty { "EditText" },
                        color = txtColor.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ComponentWidgetType.IMAGE.name -> {
                if (customBitmap == null) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = component.label,
                            tint = txtColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = component.label,
                            color = txtColor,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            ComponentWidgetType.TEXT.name -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = component.label,
                        color = txtColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ComponentWidgetType.LINK.name -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🌐 ${component.label}",
                        color = txtColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    )
                    Surface(
                        onClick = { onToggleState() },
                        color = Color(0xFF0288D1),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, Color.White)
                    ) {
                        Text(
                            text = "OPEN ↗",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            ComponentWidgetType.BUTTON.name -> {
                // Classic Button design (turns Green when ON, with STATE: ON / STATE: OFF removed)
                // Tapping the button selects it to open the bottom Edit Dock; tapping the ON/OFF badge toggles state + selects!
                val activeBtnBg = if (isCheckedOn) Color(0xFF00C853) else bgColor
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(activeBtnBg)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = component.label,
                        color = if (isCheckedOn) Color.White else txtColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp)
                    )

                    Surface(
                        onClick = { onToggleState() },
                        color = if (isCheckedOn) Color(0xFF047857) else Color(0xFFEF4444),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, Color.White)
                    ) {
                        Text(
                            text = if (isCheckedOn) "ON" else "OFF",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

            // Image-Crop L-Corner Brackets inside the widget when selected (Hidden when Auto Fix Size is ON)
            if (isSelected && !isLivePreviewMode && !isAutoFixSize) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bLen = 10.dp.toPx()
                    val bStroke = 2.5.dp.toPx()
                    val cColor = Color(0xFF0288D1)

                    drawLine(cColor, Offset(0f, 0f), Offset(bLen, 0f), bStroke)
                    drawLine(cColor, Offset(0f, 0f), Offset(0f, bLen), bStroke)

                    drawLine(cColor, Offset(size.width - bLen, 0f), Offset(size.width, 0f), bStroke)
                    drawLine(cColor, Offset(size.width, 0f), Offset(size.width, bLen), bStroke)

                    drawLine(cColor, Offset(0f, size.height - bLen), Offset(0f, size.height), bStroke)
                    drawLine(cColor, Offset(0f, size.height), Offset(bLen, size.height), bStroke)

                    drawLine(cColor, Offset(size.width - bLen, size.height), Offset(size.width, size.height), bStroke)
                    drawLine(cColor, Offset(size.width, size.height - bLen), Offset(size.width, size.height), bStroke)
                }
            }

            // Offset + Size badge in top-right corner when in Edit Mode
            if (!isLivePreviewMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            if (isSelected) Color(0xFF0288D1) else Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(bottomStart = 4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (isSelected) "${component.widthDp}×${component.heightDp}" else component.byteOffsetHex,
                        color = Color.White,
                        fontSize = 7.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // IMAGE-CROP STYLE WIDGET RESIZE HANDLES (Hidden & disabled when Auto Fix Size is ON)
        if (!isLivePreviewMode && !isAutoFixSize) {
            if (isSelected) {
                // 1. RIGHT EDGE WIDGET CROP HANDLE (Width +/-)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 6.dp)
                        .width(12.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0288D1))
                        .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                        .pointerInput(component.id, component.widthDp) {
                            detectDragGestures(
                                onDragStart = {
                                    resizeAccumW = 0f
                                    onTapState()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    resizeAccumW += dragAmount.x
                                    val stepWDp = (resizeAccumW / density.density).roundToInt()
                                    if (stepWDp != 0) {
                                        resizeAccumW = 0f
                                        onResizeState(stepWDp, 0)
                                    }
                                }
                            )
                        }
                        .testTag("widget_crop_right_${component.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⋮", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }

                // 2. BOTTOM EDGE WIDGET CROP HANDLE (Height +/-)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 6.dp)
                        .width(24.dp)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0288D1))
                        .border(1.dp, Color.White, RoundedCornerShape(6.dp))
                        .pointerInput(component.id, component.heightDp) {
                            detectDragGestures(
                                onDragStart = {
                                    resizeAccumH = 0f
                                    onTapState()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    resizeAccumH += dragAmount.y
                                    val stepHDp = (resizeAccumH / density.density).roundToInt()
                                    if (stepHDp != 0) {
                                        resizeAccumH = 0f
                                        onResizeState(0, stepHDp)
                                    }
                                }
                            )
                        }
                        .testTag("widget_crop_bottom_${component.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⋯", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            // 3. BOTTOM-RIGHT ENDPOINT CROP HANDLE (Always available on every widget for instant Width & Height resize!)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 5.dp, y = 5.dp)
                    .size(if (isSelected) 20.dp else 15.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isSelected) Color(0xFF00C853) else Color(0xFF0288D1))
                    .border(1.5.dp, Color.White, RoundedCornerShape(5.dp))
                    .pointerInput(component.id, component.widthDp, component.heightDp) {
                        detectDragGestures(
                            onDragStart = {
                                resizeAccumW = 0f
                                resizeAccumH = 0f
                                onTapState()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                resizeAccumW += dragAmount.x
                                resizeAccumH += dragAmount.y
                                val stepWDp = (resizeAccumW / density.density).roundToInt()
                                val stepHDp = (resizeAccumH / density.density).roundToInt()
                                if (stepWDp != 0 || stepHDp != 0) {
                                    resizeAccumW = 0f
                                    resizeAccumH = 0f
                                    onResizeState(stepWDp, stepHDp)
                                }
                            }
                        )
                    }
                    .testTag("widget_crop_corner_${component.id}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↘",
                    color = Color.White,
                    fontSize = if (isSelected) 10.sp else 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
