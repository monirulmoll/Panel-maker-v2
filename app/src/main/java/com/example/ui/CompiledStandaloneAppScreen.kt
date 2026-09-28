package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import com.example.data.StudioProjectEntity
import com.example.engine.LocalConfigStateWriter
import com.example.engine.SoundTriggerPlayer
import com.example.service.DynamicOverlayRegistry
import com.example.service.FloatingDashboardService
import java.io.File
import kotlin.math.roundToInt

/**
 * Standalone Compiled App Screen.
 *
 * When the user creates an app through Studio Error and opens it:
 * 1. The main screen shows ONLY the START and STOP options on a clean empty background.
 * 2. Clicking START launches the Floating Panel (with custom Floating Panel Name, circular Logo,
 *    and user-built widgets).
 * 3. Clicking STOP stops and closes the Floating Panel completely.
 * 4. Clicking the '✕' button on the Floating Panel does NOT close everything — instead, it transforms
 *    the floating panel into a round ("goal" / गोल) floating bubble showing the custom Floating Logo
 *    (if set in Studio) or the Floating Window Name (if no logo is set). Tapping the round bubble
 *    expands the Floating Panel back!
 * 5. Includes built-in Android 13/14/15 "System Denied / Restricted Setting" unlock helper so the user
 *    can grant system-wide Overlay Permission without getting blocked.
 */
@Composable
fun CompiledStandaloneAppScreen(
    project: StudioProjectEntity,
    initialComponents: List<CanvasComponentEntity>,
    compiledPackageName: String,
    isStandaloneInstalledApk: Boolean,
    onBackToStudioEditor: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    if (!isStandaloneInstalledApk && onBackToStudioEditor != null) {
        BackHandler {
            onBackToStudioEditor()
        }
    }

    val prefs = remember(project.id, project.name) {
        context.getSharedPreferences(
            "compiled_app_state_${project.id}_${project.name.hashCode()}",
            Context.MODE_PRIVATE
        )
    }

    val liveComponents = remember(initialComponents) {
        mutableStateListOf<CanvasComponentEntity>().apply {
            initialComponents.forEach { comp ->
                val savedVal = prefs.getString("widget_val_${comp.id}", null) ?: comp.currentValue
                add(comp.copy(currentValue = savedVal))
            }
        }
    }

    var isFloatingActive by remember { mutableStateOf(FloatingDashboardService.isRunning()) }
    var isSystemServiceDispatched by remember { mutableStateOf(FloatingDashboardService.isRunning()) }
    var isMinimizedToGoalLogo by remember { mutableStateOf(false) }
    var hasSystemOverlayPerm by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var showOverlayPermHelper by remember { mutableStateOf(false) }

    var floatOffsetX by remember { mutableFloatStateOf(with(density) { 24.dp.toPx() }) }
    var floatOffsetY by remember { mutableFloatStateOf(with(density) { 84.dp.toPx() }) }

    fun pushSpecsToRegistry() {
        val specs = liveComponents.map { comp ->
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
    }

    fun startFloatingWindow() {
        pushSpecsToRegistry()
        hasSystemOverlayPerm = Settings.canDrawOverlays(context)
        isFloatingActive = true
        isMinimizedToGoalLogo = false

        if (hasSystemOverlayPerm) {
            showOverlayPermHelper = false
            try {
                val intent = Intent(context, FloatingDashboardService::class.java).apply {
                    action = FloatingDashboardService.ACTION_START_OVERLAY
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                isSystemServiceDispatched = true
            } catch (_: Exception) {
                isSystemServiceDispatched = false
            }
        } else {
            isSystemServiceDispatched = false
            showOverlayPermHelper = true
        }
    }

    fun stopFloatingWindow() {
        isFloatingActive = false
        isSystemServiceDispatched = false
        isMinimizedToGoalLogo = false
        showOverlayPermHelper = false
        try {
            val intent = Intent(context, FloatingDashboardService::class.java).apply {
                action = FloatingDashboardService.ACTION_STOP_OVERLAY
            }
            context.stopService(intent)
        } catch (_: Exception) {
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowGranted = Settings.canDrawOverlays(context)
                hasSystemOverlayPerm = nowGranted
                if (nowGranted && isFloatingActive) {
                    showOverlayPermHelper = false
                    pushSpecsToRegistry()
                    try {
                        val intent = Intent(context, FloatingDashboardService::class.java).apply {
                            action = FloatingDashboardService.ACTION_START_OVERLAY
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        isSystemServiceDispatched = true
                    } catch (_: Exception) {
                        isSystemServiceDispatched = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun handleWidgetInteraction(index: Int, overrideValue: String? = null) {
        if (index !in liveComponents.indices) return
        val comp = liveComponents[index]
        val nextVal: String
        val isTurningOn: Boolean
        val payloadToWrite: String

        when (comp.type) {
            ComponentWidgetType.LINK.name -> {
                SoundTriggerPlayer.playSoundTrigger(context, null, comp.soundTrigger, comp.customSoundPath)
                val rawUrl = comp.linkUrl.trim().ifEmpty { comp.onPayloadHex.trim() }
                if (rawUrl.isNotEmpty()) {
                    val formatted = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(browserIntent)
                    } catch (_: Exception) {
                    }
                }
                return
            }
            ComponentWidgetType.SLIDER.name -> {
                nextVal = overrideValue ?: comp.currentValue
                payloadToWrite = nextVal
                isTurningOn = (nextVal.toIntOrNull() ?: 0) > 0
            }
            ComponentWidgetType.INPUT.name -> {
                nextVal = overrideValue ?: comp.currentValue
                payloadToWrite = nextVal
                isTurningOn = nextVal.isNotBlank() && nextVal != "0" && !nextVal.equals("false", ignoreCase = true)
            }
            else -> {
                val currentlyOn = comp.currentValue == "1" || comp.currentValue.equals("true", ignoreCase = true)
                val nextOn = if (overrideValue != null) {
                    overrideValue == "1" || overrideValue.equals("true", ignoreCase = true)
                } else {
                    !currentlyOn
                }
                isTurningOn = nextOn
                nextVal = if (nextOn) "1" else "0"
                payloadToWrite = if (nextOn) comp.onPayloadHex else comp.offPayloadHex
                if (nextOn && comp.linkUrl.isNotBlank()) {
                    val rawUrl = comp.linkUrl.trim()
                    val formatted = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(formatted)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(browserIntent)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        if (isTurningOn) {
            SoundTriggerPlayer.playSoundTrigger(context, null, comp.soundTrigger, comp.customSoundPath)
        } else {
            SoundTriggerPlayer.playSoundTrigger(context, null, comp.offSoundTrigger, comp.offCustomSoundPath)
        }

        liveComponents[index] = comp.copy(currentValue = nextVal)
        prefs.edit().putString("widget_val_${comp.id}", nextVal).apply()

        LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(
            context.filesDir,
            "widget_${comp.id}",
            comp.type,
            comp.targetFilePath,
            comp.byteOffsetHex,
            comp.offPayloadHex,
            comp.onPayloadHex,
            payloadToWrite,
            isTurningOn,
            comp.label
        )
    }

    val canvasBgColor = remember(project.canvasBgColorHex) {
        try {
            val raw = project.canvasBgColorHex.trim()
            if (raw.isEmpty()) {
                Color.White
            } else {
                val formatted = if (raw.startsWith("#")) raw else "#$raw"
                Color(android.graphics.Color.parseColor(formatted))
            }
        } catch (_: Exception) {
            Color.White
        }
    }

    val floatingLogoBitmap = remember(project.floatingLogoPath) {
        if (project.floatingLogoPath.isNotBlank()) {
            val f = File(project.floatingLogoPath)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        } else null
    }

    val floatingDisplayTitle = remember(project.overlayTitle, project.name) {
        project.overlayTitle.trim().ifEmpty { project.name.trim() }
    }

    val minTopDp = remember(liveComponents.toList(), project.autoFixSize) {
        if (project.autoFixSize || liveComponents.isEmpty()) 0
        else liveComponents.minOf { it.posYDp }.coerceAtMost(0)
    }
    val topShiftDp = if (minTopDp < 0) (-minTopDp + 8) else 0
    val maxBottomDp = remember(liveComponents.toList(), topShiftDp) {
        if (liveComponents.isEmpty()) 0
        else liveComponents.maxOf { (it.posYDp + topShiftDp) + it.heightDp }
    }
    val canvasWidthDp = project.canvasWidthDp.coerceIn(180, 420)
    val canvasHeightDp = project.canvasHeightDp.coerceIn(160, 620)
    val contentHeightDp = maxOf(canvasHeightDp, maxBottomDp + 8)
    val scrollState = rememberScrollState()

    // Main Screen: Clean empty background showing ONLY START and STOP options,
    // plus the active Floating Panel (or collapsed round Goal Logo bubble) when START is active!
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(canvasBgColor)
            .systemBarsPadding()
            .testTag("compiled_standalone_app_screen")
    ) {
        // CENTER: ONLY START AND STOP OPTIONS
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { startFloatingWindow() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF16A34A),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                modifier = Modifier.testTag("standalone_start_floating_button")
            ) {
                Text(
                    text = "START",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Button(
                onClick = { stopFloatingWindow() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                modifier = Modifier.testTag("standalone_stop_floating_button")
            ) {
                Text(
                    text = "STOP",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // System Overlay Permission Helper (Fixes Android 13/14/15 "System Denied / Restricted Setting")
        if (showOverlayPermHelper && !hasSystemOverlayPerm) {
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("overlay_permission_helper_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Fix 'System Denied' Overlay Permission (Android 13–15)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "If Android says 'System denied' or 'Restricted setting':\n1. Tap 'Unlock Restricted Setting' → Tap ⋮ (top-right) → 'Allow restricted settings'.\n2. Then tap 'Grant Overlay Permission' to float over other apps.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Stop any running overlay first so Android Settings never blocks the click
                                try {
                                    context.stopService(Intent(context, FloatingDashboardService::class.java))
                                } catch (_: Exception) {
                                }
                                try {
                                    val appInfoIntent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(appInfoIntent)
                                } catch (_: Exception) {
                                }
                            },
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("unlock_restricted_settings_button")
                        ) {
                            Text(
                                text = "1. Unlock App Info",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                // Stop any running overlay first so Android Settings never says "obscuring permission request"
                                try {
                                    context.stopService(Intent(context, FloatingDashboardService::class.java))
                                } catch (_: Exception) {
                                }
                                try {
                                    val overlayIntent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(overlayIntent)
                                } catch (_: Exception) {
                                    try {
                                        val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(fallbackIntent)
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("grant_overlay_permission_button")
                        ) {
                            Text(
                                text = "2. Grant Overlay",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // ACTIVE FLOATING WINDOW OR COLLAPSED ROUND ("GOAL") LOGO BUBBLE
        // (Rendered in-app whenever FloatingDashboardService is not already drawing a system-level window)
        if (isFloatingActive && !isSystemServiceDispatched && !FloatingDashboardService.isRunning()) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(floatOffsetX.roundToInt(), floatOffsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            floatOffsetX = (floatOffsetX + dragAmount.x).coerceAtLeast(0f)
                            floatOffsetY = (floatOffsetY + dragAmount.y).coerceAtLeast(0f)
                        }
                    }
            ) {
                if (isMinimizedToGoalLogo) {
                    // ROUND ("GOAL" / गोल) FLOATING LOGO BUBBLE
                    // Shows the custom Floating Logo if set in Studio, or the Floating Window Name if no logo is set!
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB))
                            .border(BorderStroke(2.dp, Color.White), CircleShape)
                            .clickable {
                                isMinimizedToGoalLogo = false
                            }
                            .testTag("floating_goal_logo_bubble"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (floatingLogoBitmap != null) {
                            Image(
                                bitmap = floatingLogoBitmap,
                                contentDescription = "Floating Goal Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Text(
                                text = floatingDisplayTitle.ifEmpty { "Float" },
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
                } else {
                    // EXPANDED FLOATING PANEL WINDOW
                    Card(
                        modifier = Modifier
                            .width(canvasWidthDp.dp)
                            .testTag("compiled_app_visual_window"),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        border = BorderStroke(2.dp, Color(0xFF3B82F6))
                    ) {
                        Column {
                            // Floating Window Header Bar: Circular Logo + Floating Window Name + '✕' Minimize-to-Goal-Logo Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF2563EB))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (floatingLogoBitmap != null) {
                                        Image(
                                            bitmap = floatingLogoBitmap,
                                            contentDescription = "Floating Panel Logo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .border(BorderStroke(1.dp, Color.White), CircleShape)
                                        )
                                    }
                                    Text(
                                        text = floatingDisplayTitle,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Clicking '✕' changes the form into the round ("goal") logo instead of closing everything!
                                Text(
                                    text = "✕",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    modifier = Modifier
                                        .clickable {
                                            isMinimizedToGoalLogo = true
                                        }
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .testTag("floating_panel_close_to_goal_button")
                                )
                            }

                            // Floating Window Body with user-created widgets
                            Box(
                                modifier = Modifier
                                    .width(canvasWidthDp.dp)
                                    .height(canvasHeightDp.dp)
                                    .background(canvasBgColor)
                            ) {
                                if (liveComponents.isNotEmpty()) {
                                    if (project.autoFixSize) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(scrollState)
                                                .padding(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            liveComponents.forEachIndexed { idx, comp ->
                                                CompiledStandaloneWidgetView(
                                                    component = comp,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(comp.heightDp.coerceAtLeast(36).dp),
                                                    onTrigger = { overrideVal ->
                                                        handleWidgetInteraction(idx, overrideVal)
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(scrollState)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(canvasWidthDp.dp)
                                                    .height(contentHeightDp.dp)
                                            ) {
                                                liveComponents.forEachIndexed { idx, comp ->
                                                    CompiledStandaloneWidgetView(
                                                        component = comp,
                                                        modifier = Modifier
                                                            .offset(
                                                                x = comp.posXDp.coerceAtLeast(0).dp,
                                                                y = (comp.posYDp + topShiftDp).dp
                                                            )
                                                            .size(
                                                                width = comp.widthDp.coerceAtLeast(44).dp,
                                                                height = comp.heightDp.coerceAtLeast(28).dp
                                                            ),
                                                        onTrigger = { overrideVal ->
                                                            handleWidgetInteraction(idx, overrideVal)
                                                        }
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
        }
    }
}

@Composable
private fun CompiledStandaloneWidgetView(
    component: CanvasComponentEntity,
    modifier: Modifier = Modifier,
    onTrigger: (String?) -> Unit
) {
    val widgetType = remember(component.type) {
        try {
            ComponentWidgetType.valueOf(component.type)
        } catch (_: Exception) {
            ComponentWidgetType.BUTTON
        }
    }

    val bgColor = parseStandaloneColor(component.bgColorHex, Color.White)
    val textColor = parseStandaloneColor(component.textColorHex, Color(0xFF0F172A))
    val isActive = component.currentValue == "1" || component.currentValue.equals("true", ignoreCase = true)

    when (widgetType) {
        ComponentWidgetType.BUTTON -> {
            val buttonBg = if (isActive) Color(0xFF00C853) else bgColor
            val buttonTextCol = if (isActive) Color.White else textColor
            Surface(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTrigger(null) },
                shape = RoundedCornerShape(8.dp),
                color = buttonBg,
                border = BorderStroke(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) Color(0xFF16A34A) else Color(0xFF94A3B8)
                ),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = component.label,
                        color = buttonTextCol,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = if (isActive) Color(0xFF15803D) else Color(0xFF475569),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isActive) "ON" else "OFF",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        ComponentWidgetType.TOGGLE -> {
            Surface(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTrigger(if (isActive) "0" else "1") },
                shape = RoundedCornerShape(8.dp),
                color = if (isActive) Color(0xFFECFDF5) else bgColor,
                border = BorderStroke(
                    width = if (isActive) 2.dp else 1.dp,
                    color = if (isActive) Color(0xFF00C853) else Color(0xFF94A3B8)
                ),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = component.label,
                        color = if (isActive) Color(0xFF065F46) else textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isActive,
                        onCheckedChange = { checked ->
                            onTrigger(if (checked) "1" else "0")
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00C853),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF64748B)
                        )
                    )
                }
            }
        }

        ComponentWidgetType.SLIDER -> {
            val currentFloat = (component.currentValue.toFloatOrNull() ?: 50f)
                .coerceIn(0f, component.sliderMax.toFloat().coerceAtLeast(1f))
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(8.dp),
                color = bgColor,
                border = BorderStroke(1.dp, Color(0xFF94A3B8)),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = component.label,
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "${currentFloat.toInt()} / ${component.sliderMax}",
                            color = Color(0xFF0288D1),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    }
                    Slider(
                        value = currentFloat,
                        onValueChange = { v -> onTrigger(v.toInt().toString()) },
                        valueRange = 0f..component.sliderMax.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF0288D1),
                            activeTrackColor = Color(0xFF0288D1)
                        ),
                        modifier = Modifier.height(22.dp)
                    )
                }
            }
        }

        ComponentWidgetType.INPUT -> {
            var localText by remember(component.currentValue) { mutableStateOf(component.currentValue) }
            Surface(
                modifier = modifier,
                shape = RoundedCornerShape(6.dp),
                color = bgColor,
                border = BorderStroke(1.dp, Color(0xFF0288D1))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BasicTextField(
                        value = localText,
                        onValueChange = { newText ->
                            localText = newText
                            onTrigger(newText)
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (localText.isEmpty() || localText == "0") {
                                Text(
                                    text = component.label,
                                    color = textColor.copy(alpha = 0.55f),
                                    fontSize = 11.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }

        ComponentWidgetType.IMAGE -> {
            val customBitmap = remember(component.customImagePath) {
                if (component.customImagePath.isNotBlank()) {
                    val file = File(component.customImagePath)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
                } else null
            }
            Surface(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTrigger(null) },
                shape = RoundedCornerShape(8.dp),
                color = bgColor,
                border = BorderStroke(1.5.dp, Color(0xFF94A3B8))
            ) {
                if (customBitmap != null) {
                    Image(
                        bitmap = customBitmap,
                        contentDescription = component.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = component.label,
                            color = textColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        ComponentWidgetType.TEXT -> {
            Surface(
                modifier = modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onTrigger(null) },
                shape = RoundedCornerShape(6.dp),
                color = bgColor
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = component.label,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        ComponentWidgetType.LINK -> {
            Surface(
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onTrigger(null) },
                shape = RoundedCornerShape(8.dp),
                color = bgColor,
                border = BorderStroke(1.5.dp, Color(0xFF38BDF8)),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🌐 ${component.label}",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = Color(0xFF0288D1),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "OPEN ↗",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun parseStandaloneColor(hex: String, fallback: Color): Color {
    return try {
        val clean = if (hex.startsWith("#")) hex else "#$hex"
        Color(android.graphics.Color.parseColor(clean))
    } catch (_: Exception) {
        fallback
    }
}
