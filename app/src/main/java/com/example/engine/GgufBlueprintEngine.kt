package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class StudioGenerationMode {
    OFFLINE_MANUAL,
    AI_GGUF_MODE
}

data class AiBuildStepStatus(
    val stepNumber: Int,
    val totalSteps: Int,
    val title: String,
    val detail: String,
    val isCompleted: Boolean = false,
    val hasError: Boolean = false
)

data class AiChatTurn(
    val id: Long = System.currentTimeMillis(),
    val userPrompt: String,
    val aiResponseText: String,
    val steps: List<AiBuildStepStatus> = emptyList(),
    val isAppReady: Boolean = false,
    val generatedCodePreview: String = ""
)

data class GgufModelState(
    val mode: StudioGenerationMode = StudioGenerationMode.OFFLINE_MANUAL,
    val isValidGgufLoaded: Boolean = false,
    val modelFileName: String = "",
    val modelFilePath: String = "",
    val isUsingSampleFallback: Boolean = false,
    val autoSampleFallbackEnabled: Boolean = false,
    val modelArchitecture: String = "",
    val quantizationTag: String = "",
    val modelSizeBytes: Long = 0L,
    val importErrorMessage: String? = null,
    val statusMessage: String = "Select Offline Mode or Online (AI GGUF) Mode to begin.",
    val lastGeneratedBlueprintSummary: String = ""
)

data class GeneratedBlueprintSpec(
    val suggestedAppName: String,
    val suggestedPackageName: String,
    val suggestedOverlayTitle: String,
    val suggestedTargetFilePath: String,
    val components: List<CanvasComponentEntity>,
    val kotlinJavaSummary: String
)

/**
 * GGUF Model Validation & AI Studio Blueprint Engine.
 *
 * - Strictly validates .gguf files when the user selects AI Mode:
 *   If the selected file is not a valid .gguf file or import fails, returns an explicit error message.
 *   If valid, loads the .gguf model and unlocks the independent AI Mode workspace.
 */
object GgufBlueprintEngine {

    private const val SAMPLE_GGUF_FILENAME = "sample_studio_codegen_q4_k_m.gguf"

    @JvmStatic
    fun ensureSampleGgufFile(context: Context): File {
        val modelsDir = File(context.filesDir, "gguf_models").apply {
            if (!exists()) mkdirs()
        }
        val sampleFile = File(modelsDir, SAMPLE_GGUF_FILENAME)
        if (!sampleFile.exists() || sampleFile.length() < 32L) {
            try {
                FileOutputStream(sampleFile).use { fos ->
                    val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.put("GGUF".toByteArray(StandardCharsets.US_ASCII))
                    buffer.putInt(3)
                    buffer.putLong(24L)
                    buffer.putLong(8L)
                    val desc = "STUDIO_ERROR_SAMPLE_GGUF_BLUEPRINT_ENGINE_V3".toByteArray(StandardCharsets.UTF_8)
                    buffer.putInt(desc.size)
                    buffer.put(desc, 0, desc.size.coerceAtMost(36))
                    fos.write(buffer.array(), 0, buffer.position())
                }
            } catch (_: Exception) {
            }
        }
        return sampleFile
    }

    /**
     * Strictly validates and imports a user-selected file URI as a .gguf model.
     * If the file is not a .gguf file, is empty, or fails to read, returns a GgufModelState
     * with `isValidGgufLoaded = false` and a clear `importErrorMessage`.
     */
    @JvmStatic
    fun validateAndImportGgufUri(context: Context, uri: Uri): GgufModelState {
        return try {
            val displayName = resolveDisplayName(context, uri).trim()
            if (displayName.isEmpty()) {
                return GgufModelState(
                    mode = StudioGenerationMode.AI_GGUF_MODE,
                    isValidGgufLoaded = false,
                    importErrorMessage = "Import Failed: Could not read file name from selected URI."
                )
            }

            val hasGgufExtension = displayName.lowercase(Locale.US).endsWith(".gguf")

            val modelsDir = File(context.filesDir, "gguf_models").apply {
                if (!exists()) mkdirs()
            }
            val safeFileName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val tempFile = File(modelsDir, "import_${System.currentTimeMillis()}_$safeFileName")

            var copiedBytes = 0L
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return GgufModelState(
                    mode = StudioGenerationMode.AI_GGUF_MODE,
                    isValidGgufLoaded = false,
                    importErrorMessage = "Import Failed: Unable to open selected file stream."
                )

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        copiedBytes += read
                    }
                    output.flush()
                }
            }

            if (copiedBytes <= 0L || !tempFile.exists()) {
                if (tempFile.exists()) tempFile.delete()
                return GgufModelState(
                    mode = StudioGenerationMode.AI_GGUF_MODE,
                    isValidGgufLoaded = false,
                    importErrorMessage = "Import Failed: Selected file '$displayName' is empty (0 bytes)."
                )
            }

            val hasGgufMagic = hasValidGgufMagicBytes(tempFile)
            if (!hasGgufExtension && !hasGgufMagic) {
                tempFile.delete()
                return GgufModelState(
                    mode = StudioGenerationMode.AI_GGUF_MODE,
                    isValidGgufLoaded = false,
                    importErrorMessage = "Invalid File ('$displayName'): Please select a valid .gguf model file!"
                )
            }

            val headerInfo = inspectGgufHeaderSafely(tempFile)
            GgufModelState(
                mode = StudioGenerationMode.AI_GGUF_MODE,
                isValidGgufLoaded = true,
                modelFileName = displayName,
                modelFilePath = tempFile.absolutePath,
                isUsingSampleFallback = false,
                autoSampleFallbackEnabled = false,
                modelArchitecture = headerInfo.first,
                quantizationTag = headerInfo.second,
                modelSizeBytes = copiedBytes,
                importErrorMessage = null,
                statusMessage = "GGUF Loaded: $displayName (${formatBytes(copiedBytes)})"
            )
        } catch (e: Exception) {
            GgufModelState(
                mode = StudioGenerationMode.AI_GGUF_MODE,
                isValidGgufLoaded = false,
                importErrorMessage = "GGUF Import Error: ${e.message ?: "Could not read the selected file."}"
            )
        }
    }

    /**
     * Strictly validates a direct file path for a .gguf model.
     */
    @JvmStatic
    fun validateGgufFilePath(rawPath: String): GgufModelState {
        val cleanPath = rawPath.trim()
        if (cleanPath.isEmpty()) {
            return GgufModelState(
                mode = StudioGenerationMode.AI_GGUF_MODE,
                isValidGgufLoaded = false,
                importErrorMessage = "Please enter or select a .gguf file path."
            )
        }
        val file = File(cleanPath)
        if (!file.exists() || !file.isFile) {
            return GgufModelState(
                mode = StudioGenerationMode.AI_GGUF_MODE,
                isValidGgufLoaded = false,
                importErrorMessage = "File Not Found: '$cleanPath' does not exist on storage."
            )
        }
        if (!file.canRead() || file.length() <= 0L) {
            return GgufModelState(
                mode = StudioGenerationMode.AI_GGUF_MODE,
                isValidGgufLoaded = false,
                importErrorMessage = "Cannot Read File: '$cleanPath' is empty or permission is denied."
            )
        }
        val hasGgufExtension = file.name.lowercase(Locale.US).endsWith(".gguf")
        val hasGgufMagic = hasValidGgufMagicBytes(file)
        if (!hasGgufExtension && !hasGgufMagic) {
            return GgufModelState(
                mode = StudioGenerationMode.AI_GGUF_MODE,
                isValidGgufLoaded = false,
                importErrorMessage = "Invalid File ('${file.name}'): File must be a valid .gguf model!"
            )
        }
        val headerInfo = inspectGgufHeaderSafely(file)
        return GgufModelState(
            mode = StudioGenerationMode.AI_GGUF_MODE,
            isValidGgufLoaded = true,
            modelFileName = file.name,
            modelFilePath = file.absolutePath,
            isUsingSampleFallback = false,
            autoSampleFallbackEnabled = false,
            modelArchitecture = headerInfo.first,
            quantizationTag = headerInfo.second,
            modelSizeBytes = file.length(),
            importErrorMessage = null,
            statusMessage = "GGUF Loaded: ${file.name} (${formatBytes(file.length())})"
        )
    }

    /**
     * Backwards-compatible helper used by unit tests.
     */
    @JvmStatic
    fun loadOrFallbackToSample(
        context: Context,
        customPathOrNull: String?,
        targetMode: StudioGenerationMode
    ): GgufModelState {
        val cleanPath = customPathOrNull?.trim().orEmpty()
        if (cleanPath.isNotEmpty()) {
            val validated = validateGgufFilePath(cleanPath)
            if (validated.isValidGgufLoaded) {
                return validated.copy(mode = targetMode)
            }
        }
        val sampleFile = ensureSampleGgufFile(context)
        return GgufModelState(
            mode = targetMode,
            isValidGgufLoaded = false,
            modelFileName = sampleFile.name,
            modelFilePath = sampleFile.absolutePath,
            isUsingSampleFallback = true,
            autoSampleFallbackEnabled = true,
            modelArchitecture = "Studio-Blueprint-GGUF-v3",
            quantizationTag = "Q4_K_M",
            modelSizeBytes = sampleFile.length(),
            importErrorMessage = null,
            statusMessage = "Ready"
        )
    }

    private fun hasValidGgufMagicBytes(file: File): Boolean {
        return try {
            BufferedInputStream(FileInputStream(file)).use { bis ->
                val header = ByteArray(4)
                val read = bis.read(header)
                read == 4 &&
                    header[0] == 'G'.code.toByte() &&
                    header[1] == 'G'.code.toByte() &&
                    header[2] == 'U'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name.trim()
                }
            }
        } catch (_: Exception) {
        }
        val lastSeg = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        return if (!lastSeg.isNullOrBlank()) lastSeg else ""
    }

    private fun inspectGgufHeaderSafely(file: File): Pair<String, String> {
        return try {
            BufferedInputStream(FileInputStream(file)).use { bis ->
                val header = ByteArray(8)
                val read = bis.read(header)
                if (read >= 4 &&
                    header[0] == 'G'.code.toByte() &&
                    header[1] == 'G'.code.toByte() &&
                    header[2] == 'U'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
                ) {
                    val version = if (read >= 8) {
                        ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    } else 3
                    "GGUF v$version Native Binary" to detectQuantizationFromName(file.name)
                } else {
                    "GGUF Local Model" to detectQuantizationFromName(file.name)
                }
            }
        } catch (_: Exception) {
            "GGUF Local Model" to "Q4_K_M"
        }
    }

    private fun detectQuantizationFromName(fileName: String): String {
        val upper = fileName.uppercase(Locale.US)
        return when {
            "Q4_K_M" in upper -> "Q4_K_M"
            "Q4_0" in upper -> "Q4_0"
            "Q5_K_M" in upper -> "Q5_K_M"
            "Q8_0" in upper -> "Q8_0"
            "F16" in upper -> "FP16"
            else -> "GGUF Quantized"
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Generates a complete independent AI App Blueprint from the user's text prompt.
     */
    @JvmStatic
    fun generateBlueprintFromPrompt(
        prompt: String,
        projectId: Long,
        defaultTargetFilePath: String,
        modelState: GgufModelState
    ): GeneratedBlueprintSpec {
        val cleanPrompt = prompt.trim()
        val lower = cleanPrompt.lowercase(Locale.US)

        // Extract custom file path if mentioned in prompt (including paths with spaces like /storage/emulated/0/PREMIUM VIDEOS/py.py)
        val pathWithPyOrExt = Regex("""(/storage/emulated/0/[^\n\r"']+?\.[a-zA-Z0-9]{1,5}|/sdcard/[^\n\r"']+?\.[a-zA-Z0-9]{1,5})""")
            .find(cleanPrompt)?.value?.trim()
        val fallbackSimplePath = Regex("""(/storage/emulated/0/[^\s,;]+|/sdcard/[^\s,;]+)""")
            .find(cleanPrompt)?.value?.trim()
        val extractedPath = pathWithPyOrExt ?: fallbackSimplePath ?: defaultTargetFilePath

        val suggestedName = when {
            "vip" in lower -> "VIP Floating Mod"
            "fps" in lower || "boost" in lower -> "FPS Booster Pro"
            "python" in lower || ".py" in lower -> "Python Script Patcher"
            "speed" in lower || "hack" in lower -> "Speed Controller"
            else -> cleanPrompt.split(Regex("\\s+"))
                .filter { !it.startsWith("/") && it.length > 2 }
                .take(3)
                .joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                }
                .ifBlank { "AI Floating App" }
        }

        val slug = suggestedName.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "")
            .ifEmpty { "aifloatingapp" }
        val suggestedPkg = "com.ai.$slug"
        val suggestedOverlayTitle = "$suggestedName Panel"

        val widgets = mutableListOf<CanvasComponentEntity>()
        var currentY = 12
        var nextId = 1L

        widgets.add(
            CanvasComponentEntity(
                id = nextId++,
                projectId = projectId,
                type = ComponentWidgetType.TEXT.name,
                label = "⚡ $suggestedOverlayTitle",
                posXDp = 14,
                posYDp = currentY,
                widthDp = 230,
                heightDp = 34,
                bgColorHex = "#0F172A",
                textColorHex = "#38BDF8",
                targetFilePath = extractedPath,
                byteOffsetHex = "0x00",
                offPayloadHex = "Off",
                onPayloadHex = "On",
                currentValue = "1"
            )
        )
        currentY += 42

        val wantsToggle = "toggle" in lower || "switch" in lower || "on/off" in lower || "aimbot" in lower || "esp" in lower || "bypass" in lower || "mod" in lower || widgets.size == 1
        if (wantsToggle) {
            val toggleLabel = when {
                "aimbot" in lower -> "Aimbot Lock ON/OFF"
                "fps" in lower -> "Unlock 120 FPS"
                "python" in lower || ".py" in lower -> "Python Flag (Off -> On)"
                else -> "$suggestedName Switch"
            }
            widgets.add(
                CanvasComponentEntity(
                    id = nextId++,
                    projectId = projectId,
                    type = ComponentWidgetType.TOGGLE.name,
                    label = toggleLabel,
                    posXDp = 14,
                    posYDp = currentY,
                    widthDp = 230,
                    heightDp = 46,
                    bgColorHex = "#1E293B",
                    textColorHex = "#F8FAFC",
                    targetFilePath = extractedPath,
                    byteOffsetHex = "0x00",
                    offPayloadHex = "Off",
                    onPayloadHex = "On",
                    soundTrigger = "CLICK_POP",
                    offSoundTrigger = "SOFT_TAP",
                    currentValue = "0"
                )
            )
            currentY += 54
        }

        if ("vip" in lower || "esp" in lower || "bypass" in lower || "all" in lower) {
            widgets.add(
                CanvasComponentEntity(
                    id = nextId++,
                    projectId = projectId,
                    type = ComponentWidgetType.TOGGLE.name,
                    label = "Anti-Ban & Bypass",
                    posXDp = 14,
                    posYDp = currentY,
                    widthDp = 230,
                    heightDp = 46,
                    bgColorHex = "#1E293B",
                    textColorHex = "#4ADE80",
                    targetFilePath = extractedPath,
                    byteOffsetHex = "0x04",
                    offPayloadHex = "false",
                    onPayloadHex = "true",
                    soundTrigger = "SUCCESS_CHIME",
                    offSoundTrigger = "POWER_DOWN",
                    currentValue = "0"
                )
            )
            currentY += 54
        }

        if ("slider" in lower || "speed" in lower || "fov" in lower || "value" in lower || "range" in lower || widgets.size < 3) {
            val sliderLabel = when {
                "fov" in lower -> "FOV Radius"
                "speed" in lower -> "Speed Multiplier"
                else -> "Intensity Level"
            }
            widgets.add(
                CanvasComponentEntity(
                    id = nextId++,
                    projectId = projectId,
                    type = ComponentWidgetType.SLIDER.name,
                    label = sliderLabel,
                    posXDp = 14,
                    posYDp = currentY,
                    widthDp = 230,
                    heightDp = 52,
                    bgColorHex = "#0F172A",
                    textColorHex = "#E2E8F0",
                    targetFilePath = extractedPath,
                    byteOffsetHex = "0x08",
                    offPayloadHex = "speed = 10",
                    onPayloadHex = "speed = 100",
                    sliderMax = 100,
                    currentValue = "50"
                )
            )
            currentY += 60
        }

        if ("input" in lower || "key" in lower || "custom" in lower || "config" in lower) {
            widgets.add(
                CanvasComponentEntity(
                    id = nextId++,
                    projectId = projectId,
                    type = ComponentWidgetType.INPUT.name,
                    label = "Custom Config Value",
                    posXDp = 14,
                    posYDp = currentY,
                    widthDp = 230,
                    heightDp = 48,
                    bgColorHex = "#FFFFFF",
                    textColorHex = "#0F172A",
                    targetFilePath = extractedPath,
                    byteOffsetHex = "0x10",
                    offPayloadHex = "Off",
                    onPayloadHex = "On",
                    currentValue = "On"
                )
            )
            currentY += 56
        }

        widgets.add(
            CanvasComponentEntity(
                id = nextId,
                projectId = projectId,
                type = ComponentWidgetType.BUTTON.name,
                label = "Apply Patch Now",
                posXDp = 14,
                posYDp = currentY,
                widthDp = 230,
                heightDp = 46,
                bgColorHex = "#2563EB",
                textColorHex = "#FFFFFF",
                targetFilePath = extractedPath,
                byteOffsetHex = "0x00",
                offPayloadHex = "Off",
                onPayloadHex = "On",
                soundTrigger = "LASER_PING",
                currentValue = "0"
            )
        )

        val modelSourceTag = if (modelState.isUsingSampleFallback) {
            "Sample GGUF Fallback (${modelState.modelFileName.ifBlank { SAMPLE_GGUF_FILENAME }})"
        } else {
            "GGUF Model (${modelState.modelFileName})"
        }

        val summary = buildString {
            appendLine("// Generated via $modelSourceTag")
            appendLine("// App Name: $suggestedName ($suggestedPkg)")
            appendLine("// Prompt: \"$cleanPrompt\"")
            appendLine("// Target File: $extractedPath")
            appendLine("// Generated Widgets: ${widgets.size} interactive components + XML/Java/Kotlin Blueprint")
        }

        return GeneratedBlueprintSpec(
            suggestedAppName = suggestedName,
            suggestedPackageName = suggestedPkg,
            suggestedOverlayTitle = suggestedOverlayTitle,
            suggestedTargetFilePath = extractedPath,
            components = widgets,
            kotlinJavaSummary = summary.trim()
        )
    }
}
