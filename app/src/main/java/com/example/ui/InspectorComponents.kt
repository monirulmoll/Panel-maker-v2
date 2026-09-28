package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import com.example.engine.LocalConfigStateWriter
import com.example.engine.SoundTriggerPlayer
import java.util.Locale

/**
 * REQUIREMENT 3: SKETCHWARE-STYLE BOTTOM PROPERTY DOCK & INSPECTOR
 * Matches the uploaded screenshot:
 * 1. Bright Blue Top Bar (#0288D1) with "[Ab] widget_id ▼", instant ON/OFF state toggle pill, and Save/Delete icons.
 * 2. "Basic | Recent | Event" category strip.
 * 3. Horizontal scrollable square white property cards ("inject", "convert", "width", "height", "on/off", "bg color", "image", "sound") + floating "... See All" card.
 * 4. Interactive property form for editing dimensions, hex colors/images, sound triggers, and file byte offsets.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PropertyInspectorBottomDock(
    component: CanvasComponentEntity,
    hasStoragePermission: Boolean = true,
    isAutoFixSize: Boolean = false,
    onToggleAutoFixSize: () -> Unit = {},
    onOpenEditCode: () -> Unit = {},
    onUpdateComponent: (CanvasComponentEntity) -> Unit,
    onSaveDesign: (CanvasComponentEntity) -> Unit = { onUpdateComponent(it) },
    onPickImageUri: (android.net.Uri) -> Unit,
    onPickSoundUri: (android.net.Uri, Boolean) -> Unit = { _, _ -> },
    onDuplicateComponent: () -> Unit,
    onDeleteComponent: () -> Unit,
    onTestTriggerWrite: (CanvasComponentEntity) -> Unit,
    onCloseDock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    var currentType by remember(component.id) { mutableStateOf(component.type) }
    var labelText by remember(component.id) { mutableStateOf(component.label) }
    var widthInput by remember(component.id) { mutableStateOf(component.widthDp.toString()) }
    var heightInput by remember(component.id) { mutableStateOf(component.heightDp.toString()) }
    var posXInput by remember(component.id) { mutableStateOf(component.posXDp.toString()) }
    var posYInput by remember(component.id) { mutableStateOf(component.posYDp.toString()) }

    var bgColorInput by remember(component.id) { mutableStateOf(component.bgColorHex) }
    var textColorInput by remember(component.id) { mutableStateOf(component.textColorHex) }
    var imagePathInput by remember(component.id) { mutableStateOf(component.customImagePath) }

    var soundTrigger by remember(component.id) { mutableStateOf(component.soundTrigger) }
    var customSoundPath by remember(component.id) { mutableStateOf(component.customSoundPath) }
    var offSoundTrigger by remember(component.id) { mutableStateOf(component.offSoundTrigger) }
    var offCustomSoundPath by remember(component.id) { mutableStateOf(component.offCustomSoundPath) }

    var targetFileInput by remember(component.id) { mutableStateOf(component.targetFilePath) }
    var offsetHexInput by remember(component.id) { mutableStateOf(component.byteOffsetHex) }
    var onPayloadInput by remember(component.id) { mutableStateOf(component.onPayloadHex) }
    var offPayloadInput by remember(component.id) { mutableStateOf(component.offPayloadHex) }
    var sliderMaxInput by remember(component.id) { mutableStateOf(component.sliderMax.toString()) }
    var linkUrlInput by remember(component.id) { mutableStateOf(component.linkUrl) }

    val isCurrentlyOn = component.currentValue == "1" || component.currentValue.equals("true", ignoreCase = true)

    // Sync external drag/resize/picker updates without wiping user typing
    LaunchedEffect(component.widthDp, component.heightDp) {
        if (widthInput.toIntOrNull() != component.widthDp) {
            widthInput = component.widthDp.toString()
        }
        if (heightInput.toIntOrNull() != component.heightDp) {
            heightInput = component.heightDp.toString()
        }
    }
    LaunchedEffect(component.posXDp, component.posYDp) {
        if (posXInput.toIntOrNull() != component.posXDp) {
            posXInput = component.posXDp.toString()
        }
        if (posYInput.toIntOrNull() != component.posYDp) {
            posYInput = component.posYDp.toString()
        }
    }
    LaunchedEffect(component.customImagePath) {
        if (component.customImagePath.isNotBlank() && imagePathInput != component.customImagePath) {
            imagePathInput = component.customImagePath
        }
    }
    LaunchedEffect(component.customSoundPath, component.soundTrigger) {
        customSoundPath = component.customSoundPath
        soundTrigger = component.soundTrigger
    }
    LaunchedEffect(component.offCustomSoundPath, component.offSoundTrigger) {
        offCustomSoundPath = component.offCustomSoundPath
        offSoundTrigger = component.offSoundTrigger
    }

    fun buildEditedComponent(
        typeOverride: String = currentType,
        labelOverride: String = labelText,
        widthOverride: Int = widthInput.toIntOrNull()?.coerceIn(40, 380) ?: component.widthDp,
        heightOverride: Int = heightInput.toIntOrNull()?.coerceIn(28, 320) ?: component.heightDp,
        posXOverride: Int = posXInput.toIntOrNull()?.coerceAtLeast(0) ?: component.posXDp,
        posYOverride: Int = posYInput.toIntOrNull() ?: component.posYDp,
        bgHexOverride: String = bgColorInput,
        textHexOverride: String = textColorInput,
        imageOverride: String = imagePathInput,
        onSoundTriggerOverride: String = soundTrigger,
        onSoundPathOverride: String = customSoundPath,
        offSoundTriggerOverride: String = offSoundTrigger,
        offSoundPathOverride: String = offCustomSoundPath,
        targetPathOverride: String = targetFileInput,
        offsetHexOverride: String = offsetHexInput,
        onPayloadOverride: String = onPayloadInput,
        offPayloadOverride: String = offPayloadInput,
        sliderMaxOverride: Int = sliderMaxInput.toIntOrNull()?.coerceIn(1, 100000) ?: component.sliderMax,
        linkUrlOverride: String = linkUrlInput
    ): CanvasComponentEntity {
        return component.copy(
            type = typeOverride,
            label = labelOverride,
            widthDp = widthOverride,
            heightDp = heightOverride,
            posXDp = posXOverride,
            posYDp = posYOverride,
            bgColorHex = bgHexOverride,
            textColorHex = textHexOverride,
            customImagePath = imageOverride,
            soundTrigger = onSoundTriggerOverride,
            customSoundPath = onSoundPathOverride,
            offSoundTrigger = offSoundTriggerOverride,
            offCustomSoundPath = offSoundPathOverride,
            targetFilePath = targetPathOverride,
            byteOffsetHex = offsetHexOverride,
            onPayloadHex = onPayloadOverride,
            offPayloadHex = offPayloadOverride,
            sliderMax = sliderMaxOverride,
            linkUrl = linkUrlOverride
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUpdateComponent(buildEditedComponent())
            onPickImageUri(uri)
        }
    }

    val onSoundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onUpdateComponent(buildEditedComponent())
            onPickSoundUri(uri, false)
        }
    }

    val offSoundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onUpdateComponent(buildEditedComponent())
            onPickSoundUri(uri, true)
        }
    }

    val targetFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val resolvedPath = resolvePickedTargetFilePath(context, uri, component.id)
            if (resolvedPath.isNotBlank()) {
                targetFileInput = resolvedPath
                onUpdateComponent(buildEditedComponent(targetPathOverride = resolvedPath))
            }
        }
    }

    val sketchWidgetId = remember(component.id, currentType) {
        val prefix = when (currentType) {
            ComponentWidgetType.TEXT.name -> "textview"
            ComponentWidgetType.TOGGLE.name -> "switch"
            ComponentWidgetType.BUTTON.name -> "button"
            ComponentWidgetType.SLIDER.name -> "seekbar"
            ComponentWidgetType.INPUT.name -> "edittext"
            ComponentWidgetType.IMAGE.name -> "imageview"
            ComponentWidgetType.LINK.name -> "linkbutton"
            else -> "view"
        }
        "${prefix}${component.id}"
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .testTag("property_inspector_dock"),
        color = Color(0xFFEEEEEE),
        tonalElevation = 10.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
        ) {
            // 1. COMPACT SKETCHWARE BRIGHT BLUE TOP BAR (#0288D1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0288D1))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: [Ab] textview1 ▼
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = "Ab",
                            color = Color(0xFF1E293B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$sketchWidgetId ($labelText)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Center: Crystal-Clear ON / OFF State Toggle Pill right in the Blue Bar!
                Surface(
                    onClick = {
                        val latest = buildEditedComponent()
                        onUpdateComponent(latest)
                        onTestTriggerWrite(latest)
                    },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isCurrentlyOn) Color(0xFF00C853) else Color(0xFFEF4444),
                    border = BorderStroke(1.dp, Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Toggle ON/OFF",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (isCurrentlyOn) "ON" else "OFF",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Right: Duplicate/Delete/Close icons (All changes auto-save in real time)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            onUpdateComponent(buildEditedComponent())
                            onDuplicateComponent()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("inspector_duplicate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate Component",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onDeleteComponent,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("inspector_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Component",
                            tint = Color(0xFFFECACA),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            onUpdateComponent(buildEditedComponent())
                            onCloseDock()
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("inspector_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Inspector",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 2. COMPACT PROPERTY ACTION CARDS ROW ("convert", "edit code", "auto fix", "path", "original", "change", "width", "height", "bg color", "on sound", "off sound")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEEEEEE))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(end = 60.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SketchwarePropertySquareCard(
                        title = "convert",
                        icon = Icons.Default.SyncAlt,
                        iconTint = Color(0xFFF59E0B),
                        isSelected = false,
                        onClick = {
                            // Cycles widget between TOGGLE, BUTTON, SLIDER, INPUT, LINK, TEXT, IMAGE
                            val order = listOf(
                                ComponentWidgetType.TOGGLE.name,
                                ComponentWidgetType.BUTTON.name,
                                ComponentWidgetType.SLIDER.name,
                                ComponentWidgetType.INPUT.name,
                                ComponentWidgetType.LINK.name,
                                ComponentWidgetType.TEXT.name,
                                ComponentWidgetType.IMAGE.name
                            )
                            val nextIdx = (order.indexOf(currentType) + 1) % order.size
                            val nextType = order[nextIdx]
                            currentType = nextType
                            val updated = buildEditedComponent(typeOverride = nextType)
                            onUpdateComponent(updated)
                        }
                    )
                    SketchwarePropertySquareCard(
                        title = "edit code",
                        icon = Icons.Default.Code,
                        iconTint = Color(0xFF00C853),
                        isSelected = false,
                        onClick = {
                            onUpdateComponent(buildEditedComponent())
                            onOpenEditCode()
                        }
                    )
                    SketchwarePropertySquareCard(
                        title = "auto fix",
                        icon = Icons.Default.SwapHoriz,
                        iconTint = if (isAutoFixSize) Color(0xFF00C853) else Color(0xFF0288D1),
                        isSelected = isAutoFixSize,
                        onClick = {
                            onUpdateComponent(buildEditedComponent())
                            onToggleAutoFixSize()
                        }
                    )
                    SketchwarePropertySquareCard(
                        title = "widget code",
                        icon = Icons.Default.Code,
                        iconTint = Color(0xFF0288D1),
                        isSelected = selectedTab == 3,
                        onClick = { selectedTab = 3 }
                    )
                    SketchwarePropertySquareCard(
                        title = "path",
                        icon = Icons.Default.Code,
                        iconTint = Color(0xFF0288D1),
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    SketchwarePropertySquareCard(
                        title = "original",
                        icon = Icons.Default.SwapHoriz,
                        iconTint = Color(0xFFEF4444),
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    SketchwarePropertySquareCard(
                        title = "change",
                        icon = Icons.Default.PlayArrow,
                        iconTint = Color(0xFF00C853),
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    SketchwarePropertySquareCard(
                        title = "width",
                        icon = Icons.Default.SwapHoriz,
                        iconTint = Color(0xFF0288D1),
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    SketchwarePropertySquareCard(
                        title = "height",
                        icon = Icons.Default.Height,
                        iconTint = Color(0xFF0288D1),
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    SketchwarePropertySquareCard(
                        title = if (isCurrentlyOn) "ON" else "OFF",
                        icon = Icons.Default.PowerSettingsNew,
                        iconTint = if (isCurrentlyOn) Color(0xFF00C853) else Color(0xFFEF4444),
                        isSelected = isCurrentlyOn,
                        onClick = {
                            val latest = buildEditedComponent()
                            onUpdateComponent(latest)
                            onTestTriggerWrite(latest)
                        }
                    )
                    SketchwarePropertySquareCard(
                        title = "text",
                        icon = Icons.Default.TextFields,
                        iconTint = Color(0xFF334155),
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    SketchwarePropertySquareCard(
                        title = "bg color",
                        icon = Icons.Default.Palette,
                        iconTint = Color(0xFF10B981),
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    SketchwarePropertySquareCard(
                        title = "image",
                        icon = Icons.Default.Image,
                        iconTint = Color(0xFF8B5CF6),
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                    SketchwarePropertySquareCard(
                        title = "on sound",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        iconTint = Color(0xFF00C853),
                        isSelected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                    SketchwarePropertySquareCard(
                        title = "off sound",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        iconTint = Color(0xFFEF4444),
                        isSelected = selectedTab == 2,
                        onClick = { selectedTab = 2 }
                    )
                }

                // Compact "... See All" Card pinned on the right
                Surface(
                    onClick = { selectedTab = (selectedTab + 1) % 4 },
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.96f),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(54.dp)
                        .height(38.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(2.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "See All Properties",
                            tint = Color(0xFF0288D1),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "See All",
                            color = Color(0xFFF59E0B),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 3. ULTRA-SLIM 4-TAB BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(BorderStroke(0.5.dp, Color(0xFFCBD5E1)))
            ) {
                listOf("Path & Change", "Color & Image", "Sound Trigger", "Widget Code").forEachIndexed { idx, title ->
                    val isSel = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = idx }
                            .background(if (isSel) Color(0xFFE0F2FE) else Color.White)
                            .padding(vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 10.sp,
                            color = if (isSel) Color(0xFF0288D1) else Color(0xFF64748B),
                            fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (selectedTab) {
                    // TAB 0: PATH + ORIGINAL + CHANGE (PYTHON / SCRIPT / FILE PATCHING), LINK URL, LABEL & DIMENSIONS
                    0 -> {
                        // Widget Design Type Selector Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Design Type:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                            listOf(
                                ComponentWidgetType.TOGGLE.name to "Switch",
                                ComponentWidgetType.BUTTON.name to "Button",
                                ComponentWidgetType.SLIDER.name to "Slide Bar",
                                ComponentWidgetType.INPUT.name to "Value Input",
                                ComponentWidgetType.LINK.name to "Open Link",
                                ComponentWidgetType.TEXT.name to "TextView",
                                ComponentWidgetType.IMAGE.name to "ImageView"
                            ).forEach { (typeKey, displayLabel) ->
                                FilterChip(
                                    selected = currentType == typeKey,
                                    onClick = {
                                        currentType = typeKey
                                        val updated = buildEditedComponent(typeOverride = typeKey)
                                        onUpdateComponent(updated)
                                    },
                                    label = { Text(displayLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = labelText,
                            onValueChange = {
                                labelText = it
                                onUpdateComponent(buildEditedComponent(labelOverride = it))
                            },
                            label = { Text("Component Text / Label") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inspector_label_input")
                        )

                        if (currentType == ComponentWidgetType.LINK.name) {
                            Surface(
                                color = Color(0xFFF0F9FF),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.5.dp, Color(0xFF0288D1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "🌐 Link Opening Widget Setup (Opens URL on Click):",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0369A1)
                                    )

                                    OutlinedTextField(
                                        value = linkUrlInput,
                                        onValueChange = {
                                            linkUrlInput = it
                                            onUpdateComponent(buildEditedComponent(linkUrlOverride = it))
                                        },
                                        label = { Text("Link URL (https://...)") },
                                        placeholder = { Text("https://t.me/your_channel") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("inspector_link_url_input")
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf(
                                            "Telegram" to "https://t.me/",
                                            "YouTube" to "https://youtube.com/",
                                            "GitHub" to "https://github.com/",
                                            "Website" to "https://google.com"
                                        ).forEach { (chipTitle, presetUrl) ->
                                            AssistChip(
                                                onClick = {
                                                    linkUrlInput = presetUrl
                                                    onUpdateComponent(buildEditedComponent(linkUrlOverride = presetUrl))
                                                },
                                                label = { Text(chipTitle, fontSize = 10.sp) },
                                                colors = AssistChipDefaults.assistChipColors(
                                                    containerColor = Color.White
                                                )
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            onUpdateComponent(buildEditedComponent())
                                            val raw = linkUrlInput.trim().ifEmpty { "https://google.com" }
                                            val formatted = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
                                            try {
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(formatted)
                                                ).apply {
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("inspector_test_link_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Test Open Link",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Test Open Link Now ↗", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // LIVE PYTHON / SCRIPT / FILE PATCHER: PATH + ORIGINAL + CHANGE
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.5.dp, Color(0xFF0288D1)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = when (currentType) {
                                            ComponentWidgetType.SLIDER.name ->
                                                "🐍 Python / File Live Slider Patch (Path • Original • Change):"
                                            ComponentWidgetType.INPUT.name ->
                                                "🐍 Python / File Live Value Patch (Path • Original • Change):"
                                            else ->
                                                "🐍 Python / File ON-OFF Patch (Path • Original • Change):"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A)
                                    )

                                    // 1. PATH INPUT + PICK FILE BUTTON + STORAGE PERMISSION GRANT
                                    val hasInspectorStoragePerm = hasStoragePermission && LocalConfigStateWriter.hasStoragePermissionGranted(context)
                                    if (!hasInspectorStoragePerm) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFEF3C7), RoundedCornerShape(6.dp))
                                                .border(BorderStroke(1.dp, Color(0xFFD97706)), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "⚠ Storage Permission required for /storage/emulated/0/...",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF92400E),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(
                                                onClick = { LocalConfigStateWriter.requestStoragePermission(context) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .height(28.dp)
                                                    .testTag("inspector_grant_storage_permission_button")
                                            ) {
                                                Text("Allow Storage", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = targetFileInput,
                                            onValueChange = {
                                                targetFileInput = it
                                                onUpdateComponent(buildEditedComponent(targetPathOverride = it))
                                            },
                                            label = { Text("Path (Python .py / File Path)") },
                                            placeholder = { Text("/sdcard/script.py") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("inspector_target_path_input")
                                        )

                                        Button(
                                            onClick = { targetFilePickerLauncher.launch("*/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                            modifier = Modifier.testTag("inspector_pick_target_file_button")
                                        ) {
                                            Text("Pick File", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // 2. ORIGINAL & CHANGE INPUTS
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = offPayloadInput,
                                            onValueChange = {
                                                offPayloadInput = it
                                                onUpdateComponent(buildEditedComponent(offPayloadOverride = it))
                                            },
                                            label = {
                                                Text(
                                                    when (currentType) {
                                                        ComponentWidgetType.SLIDER.name -> "Original (e.g. speed = 10)"
                                                        ComponentWidgetType.INPUT.name -> "Original (e.g. key = \"old\")"
                                                        else -> "Original (OFF / Original Area)"
                                                    }
                                                )
                                            },
                                            placeholder = {
                                                Text(
                                                    when (currentType) {
                                                        ComponentWidgetType.SLIDER.name -> "speed = 10"
                                                        else -> "aimbot = False"
                                                    }
                                                )
                                            },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("inspector_original_value_input")
                                        )

                                        OutlinedTextField(
                                            value = onPayloadInput,
                                            onValueChange = {
                                                onPayloadInput = it
                                                onUpdateComponent(buildEditedComponent(onPayloadOverride = it))
                                            },
                                            label = {
                                                Text(
                                                    when (currentType) {
                                                        ComponentWidgetType.SLIDER.name -> "Change (e.g. speed = {value})"
                                                        ComponentWidgetType.INPUT.name -> "Change (e.g. key = \"{value}\")"
                                                        else -> "Change (ON / Changed Area)"
                                                    }
                                                )
                                            },
                                            placeholder = {
                                                Text(
                                                    when (currentType) {
                                                        ComponentWidgetType.SLIDER.name -> "speed = {value}"
                                                        else -> "aimbot = True"
                                                    }
                                                )
                                            },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("inspector_change_value_input")
                                        )
                                    }

                                    if (currentType == ComponentWidgetType.SLIDER.name) {
                                        OutlinedTextField(
                                            value = sliderMaxInput,
                                            onValueChange = {
                                                sliderMaxInput = it
                                                val parsedMax = it.toIntOrNull()
                                                if (parsedMax != null && parsedMax >= 1) {
                                                    onUpdateComponent(
                                                        buildEditedComponent(sliderMaxOverride = parsedMax.coerceIn(1, 100000))
                                                    )
                                                }
                                            },
                                            label = { Text("Slider Max Value (0 - Max)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("inspector_slider_max_input")
                                        )
                                    }

                                    val needsAllFilesAccess = remember(targetFileInput, component.currentValue) {
                                        val clean = targetFileInput.trim()
                                        val isExtPath = clean.startsWith("/storage/") || clean.startsWith("/sdcard/")
                                        isExtPath &&
                                            !com.example.engine.LocalConfigStateWriter.hasStoragePermissionGranted(context)
                                    }

                                    if (needsAllFilesAccess) {
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = android.content.Intent(
                                                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                        android.net.Uri.parse("package:${context.packageName}")
                                                    ).apply {
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {
                                                    try {
                                                        val fallback = android.content.Intent(
                                                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                                        ).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(fallback)
                                                    } catch (_: Exception) {
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("inspector_grant_all_files_button")
                                        ) {
                                            Text(
                                                text = "🔓 Allow All Files Access (Required to Modify /storage/emulated/0/...)",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    var liveFilePreviewText by remember(targetFileInput, component.currentValue) {
                                        mutableStateOf(
                                            com.example.engine.LocalConfigStateWriter.getInstance()
                                                .readTargetFilePreview(context.filesDir, targetFileInput)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val latest = buildEditedComponent()
                                            onUpdateComponent(latest)
                                            onTestTriggerWrite(latest)
                                            liveFilePreviewText = com.example.engine.LocalConfigStateWriter.getInstance()
                                                .readTargetFilePreview(context.filesDir, latest.targetFilePath)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isCurrentlyOn) Color(0xFF00C853) else Color(0xFF1E293B)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("inspector_test_patch_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PowerSettingsNew,
                                            contentDescription = "Modify File Original to Change",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isCurrentlyOn)
                                                "Modify File Now: Revert to ORIGINAL (${offPayloadInput.ifBlank { "Off" }})"
                                            else
                                                "Modify File Now: Apply CHANGE (${onPayloadInput.ifBlank { "On" }})",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Text(
                                        text = "📄 File Text Preview: $liveFilePreviewText",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A),
                                        maxLines = 2,
                                        modifier = Modifier.testTag("inspector_live_file_preview_text")
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = widthInput,
                                onValueChange = {
                                    widthInput = it
                                    val parsed = it.toIntOrNull()
                                    if (parsed != null) {
                                        onUpdateComponent(buildEditedComponent(widthOverride = parsed.coerceIn(40, 380)))
                                    }
                                },
                                label = { Text("Width (dp)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inspector_width_input")
                            )

                            OutlinedTextField(
                                value = heightInput,
                                onValueChange = {
                                    heightInput = it
                                    val parsed = it.toIntOrNull()
                                    if (parsed != null) {
                                        onUpdateComponent(buildEditedComponent(heightOverride = parsed.coerceIn(28, 320)))
                                    }
                                },
                                label = { Text("Height (dp)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inspector_height_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = posXInput,
                                onValueChange = {
                                    posXInput = it
                                    val parsed = it.toIntOrNull()
                                    if (parsed != null) {
                                        onUpdateComponent(buildEditedComponent(posXOverride = parsed.coerceAtLeast(0)))
                                    }
                                },
                                label = { Text("X Position (dp)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            OutlinedTextField(
                                value = posYInput,
                                onValueChange = {
                                    posYInput = it
                                    val parsed = it.toIntOrNull()
                                    if (parsed != null) {
                                        onUpdateComponent(buildEditedComponent(posYOverride = parsed))
                                    }
                                },
                                label = { Text("Y Position (dp)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // TAB 1: BACKGROUND COLOR OR CUSTOM IMAGE PATH
                    1 -> {
                        Text(
                            text = "Quick Color Swatches:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        val swatches = listOf(
                            "#FFFFFF", "#2563EB", "#0288D1", "#10B981", "#F59E0B",
                            "#EF4444", "#8B5CF6", "#0F172A", "#FDE047"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            swatches.forEach { hex ->
                                val parsedColor = parseComposeColor(hex, Color(0xFF2563EB))
                                val isSelected = bgColorInput.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(parsedColor)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color(0xFF0288D1) else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            bgColorInput = hex
                                            // Automatically set a contrasting text color if needed
                                            val autoTextHex = if (hex == "#FFFFFF" || hex == "#FDE047") "#0F172A" else "#FFFFFF"
                                            textColorInput = autoTextHex
                                            onUpdateComponent(
                                                buildEditedComponent(
                                                    bgHexOverride = hex,
                                                    textHexOverride = autoTextHex
                                                )
                                            )
                                        }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = bgColorInput,
                                onValueChange = {
                                    bgColorInput = it
                                    onUpdateComponent(buildEditedComponent(bgHexOverride = it))
                                },
                                label = { Text("Background Hex") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inspector_bg_color_input")
                            )

                            OutlinedTextField(
                                value = textColorInput,
                                onValueChange = {
                                    textColorInput = it
                                    onUpdateComponent(buildEditedComponent(textHexOverride = it))
                                },
                                label = { Text("Text Hex") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = imagePathInput,
                                onValueChange = {
                                    imagePathInput = it
                                    onUpdateComponent(buildEditedComponent(imageOverride = it))
                                },
                                label = { Text("Custom Background / Icon Image Path") },
                                placeholder = { Text("/storage/.../custom_bg.png") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inspector_image_path_input")
                            )

                            Button(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Pick Image",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick", fontSize = 12.sp)
                            }
                        }
                    }

                    // TAB 2: CUSTOM ON SOUND & OFF SOUND (TRIGGER OFF) CONFIGURATIONS
                    2 -> {
                        val soundOptions = listOf(
                            SoundTriggerPlayer.SOUND_NONE to "Mute (None)",
                            SoundTriggerPlayer.SOUND_CLICK to "System Click",
                            SoundTriggerPlayer.SOUND_BEEP to "Digital Beep",
                            SoundTriggerPlayer.SOUND_CONFIRM to "Confirm Tone",
                            SoundTriggerPlayer.SOUND_POP to "Switch Pop",
                            SoundTriggerPlayer.SOUND_ALERT to "Alert Pulse",
                            SoundTriggerPlayer.SOUND_CUSTOM_FILE to "Custom Audio File"
                        )

                        // --- SECTION 1: SOUND WHEN ON (ACTIVE) ---
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF00C853)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFF00C853),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "WHEN ON",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "Sound Play When Widget / Button Turns ON:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF065F46)
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    soundOptions.forEach { (code, label) ->
                                        FilterChip(
                                            selected = soundTrigger == code,
                                            onClick = {
                                                soundTrigger = code
                                                onUpdateComponent(buildEditedComponent(onSoundTriggerOverride = code))
                                                SoundTriggerPlayer.playSoundTrigger(context, null, code, customSoundPath)
                                            },
                                            label = { Text(label, fontSize = 11.sp) }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customSoundPath,
                                        onValueChange = {
                                            customSoundPath = it
                                            val resolvedTrigger = if (it.isNotBlank()) SoundTriggerPlayer.SOUND_CUSTOM_FILE else soundTrigger
                                            soundTrigger = resolvedTrigger
                                            onUpdateComponent(
                                                buildEditedComponent(
                                                    onSoundPathOverride = it,
                                                    onSoundTriggerOverride = resolvedTrigger
                                                )
                                            )
                                        },
                                        label = { Text("ON Sound File Path (.mp3 / .wav / .ogg)") },
                                        placeholder = { Text("/sdcard/Sounds/on_fx.wav") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("inspector_sound_path_input")
                                    )

                                    Button(
                                        onClick = { onSoundPickerLauncher.launch("audio/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C853)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                        modifier = Modifier.testTag("pick_on_sound_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Pick ON Audio",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add ON Audio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        SoundTriggerPlayer.playSoundTrigger(context, null, soundTrigger, customSoundPath)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("test_on_sound_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Test ON Sound Trigger",
                                        tint = Color(0xFF00C853),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Test ON Sound Trigger", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // --- SECTION 2: SOUND WHEN OFF (TRIGGER OFF) ---
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = Color(0xFFEF4444),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "WHEN OFF",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "Sound Play When Widget / Button Turns OFF (Trigger OFF):",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF991B1B)
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    soundOptions.forEach { (code, label) ->
                                        FilterChip(
                                            selected = offSoundTrigger == code,
                                            onClick = {
                                                offSoundTrigger = code
                                                onUpdateComponent(buildEditedComponent(offSoundTriggerOverride = code))
                                                SoundTriggerPlayer.playSoundTrigger(context, null, code, offCustomSoundPath)
                                            },
                                            label = { Text(label, fontSize = 11.sp) }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = offCustomSoundPath,
                                        onValueChange = {
                                            offCustomSoundPath = it
                                            val resolvedTrigger = if (it.isNotBlank()) SoundTriggerPlayer.SOUND_CUSTOM_FILE else offSoundTrigger
                                            offSoundTrigger = resolvedTrigger
                                            onUpdateComponent(
                                                buildEditedComponent(
                                                    offSoundPathOverride = it,
                                                    offSoundTriggerOverride = resolvedTrigger
                                                )
                                            )
                                        },
                                        label = { Text("OFF Sound File Path (.mp3 / .wav / .ogg)") },
                                        placeholder = { Text("/sdcard/Sounds/off_fx.wav") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("inspector_off_sound_path_input")
                                    )

                                    Button(
                                        onClick = { offSoundPickerLauncher.launch("audio/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                                        modifier = Modifier.testTag("pick_off_sound_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Pick OFF Audio",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add OFF Audio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        SoundTriggerPlayer.playSoundTrigger(context, null, offSoundTrigger, offCustomSoundPath)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("test_off_sound_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Test OFF Sound Trigger",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Test OFF Sound Trigger", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // TAB 3: KOTLIN WIDGET CODE PREVIEW & FULL MULTI-FILE EDITOR LAUNCHER
                    3 -> {
                        val widgetKotlinSnippet = remember(
                            component.id,
                            currentType,
                            labelText,
                            posXInput,
                            posYInput,
                            widthInput,
                            heightInput,
                            bgColorInput,
                            textColorInput,
                            sliderMaxInput,
                            component.currentValue
                        ) {
                            buildString {
                                appendLine("VisualWidgetSpec(")
                                appendLine("    id = ${component.id}L,")
                                appendLine("    type = \"$currentType\",")
                                appendLine("    label = \"$labelText\",")
                                appendLine("    posXDp = $posXInput, posYDp = $posYInput,")
                                appendLine("    widthDp = $widthInput, heightDp = $heightInput,")
                                appendLine("    bgColorHex = \"$bgColorInput\", textColorHex = \"$textColorInput\",")
                                appendLine("    sliderMax = $sliderMaxInput, currentValue = \"${component.currentValue}\"")
                                append(")")
                            }
                        }

                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Kotlin Widget Code (CanvasWorkspaceComponents.kt):",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = widgetKotlinSnippet,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onUpdateComponent(buildEditedComponent())
                                onOpenEditCode()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inspector_open_edit_code_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0288D1)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Open Full Edit Code",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Edit Code (All 6 Kotlin APK Files) & Compile",
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

@Composable
private fun SketchwarePropertySquareCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) Color(0xFFE0F2FE) else Color.White,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isSelected) Color(0xFF0288D1) else Color(0xFFCBD5E1)
        ),
        shadowElevation = 1.dp,
        modifier = Modifier
            .height(38.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = title.lowercase(Locale.US),
                color = Color(0xFF1E293B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

fun parseComposeColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val clean = if (hex.trim().startsWith("#")) hex.trim() else "#${hex.trim()}"
        Color(android.graphics.Color.parseColor(clean))
    } catch (_: Exception) {
        fallback
    }
}

private fun resolvePickedTargetFilePath(
    context: android.content.Context,
    uri: android.net.Uri,
    componentId: Long
): String {
    try {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            val filePath = uri.path
            if (!filePath.isNullOrBlank()) {
                return filePath
            }
        }
        val docId = android.provider.DocumentsContract.getDocumentId(uri)
        if (docId.startsWith("primary:")) {
            val rel = docId.removePrefix("primary:")
            val candidate = java.io.File("/storage/emulated/0", rel)
            return candidate.absolutePath
        }
        if (docId.startsWith("raw:")) {
            return docId.removePrefix("raw:")
        }
        if (docId.startsWith("home:")) {
            val rel = docId.removePrefix("home:")
            return java.io.File("/storage/emulated/0/Documents", rel).absolutePath
        }
        if (docId.contains(":")) {
            val afterColon = docId.substringAfter(":")
            val extCandidate = java.io.File("/storage/emulated/0", afterColon)
            if (extCandidate.exists()) {
                return extCandidate.absolutePath
            }
        }
    } catch (_: Exception) {
    }

    try {
        val rawPath = android.net.Uri.decode(uri.path ?: "")
        if (rawPath.contains("/storage/emulated/0/")) {
            return "/storage/emulated/0/" + rawPath.substringAfter("/storage/emulated/0/")
        }
        if (rawPath.contains("/sdcard/")) {
            return "/storage/emulated/0/" + rawPath.substringAfter("/sdcard/")
        }
        if (rawPath.contains("primary:")) {
            return "/storage/emulated/0/" + rawPath.substringAfter("primary:")
        }
    } catch (_: Exception) {
    }

    try {
        context.contentResolver.query(uri, arrayOf("_data", "_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dataIdx = cursor.getColumnIndex("_data")
                if (dataIdx >= 0) {
                    val dataPath = cursor.getString(dataIdx)
                    if (!dataPath.isNullOrBlank()) {
                        return dataPath
                    }
                }
                val nameIdx = cursor.getColumnIndex("_display_name")
                if (nameIdx >= 0) {
                    val displayName = cursor.getString(nameIdx)
                    if (!displayName.isNullOrBlank()) {
                        val resolved = com.example.engine.LocalConfigStateWriter.getInstance()
                            .resolveTargetFile(context.filesDir, displayName)
                        if (resolved.exists()) {
                            return resolved.absolutePath
                        }
                    }
                }
            }
        }
    } catch (_: Exception) {
    }

    return try {
        val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() } ?: "script_${componentId}.py"
        val resolvedExt = com.example.engine.LocalConfigStateWriter.getInstance()
            .resolveTargetFile(context.filesDir, fileName)
        if (resolvedExt.exists() && resolvedExt.absolutePath.startsWith("/storage/")) {
            return resolvedExt.absolutePath
        }
        val targetDir = java.io.File(context.filesDir, "target_scripts").apply { mkdirs() }
        val destFile = java.io.File(targetDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            java.io.FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        destFile.absolutePath
    } catch (_: Exception) {
        uri.path ?: "/storage/emulated/0/script.py"
    }
}
