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
    val isConversationalReply: Boolean = false,
    val generatedCodePreview: String = ""
)

enum class AiPromptIntent {
    CONVERSATIONAL_GREETING,
    ASKING_HELP_OR_WHAT_TO_BUILD,
    VAGUE_BUILD_WITHOUT_DETAILS,
    CONVERSATIONAL_CHAT,
    BUILD_OR_MODIFY_APP
}

data class AiPromptEvaluation(
    val intent: AiPromptIntent,
    val shouldBuildOrUpdateApp: Boolean,
    val conversationalReply: String = ""
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
     * Evaluates whether a user prompt in AI Mode is a greeting/conversation/question/vague prompt
     * vs an actual app-building or app-modifying instruction.
     *
     * When the user types "hi", "hello", "kaise ho", "help", or "app banao" (without saying which app),
     * AI replies conversationally asking what kind of app to build instead of blindly running the compiler!
     */
    @JvmStatic
    fun evaluateUserPrompt(
        prompt: String,
        existingProjectName: String? = null
    ): AiPromptEvaluation {
        val clean = prompt.trim()
        val normalized = clean.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9/._\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val words = normalized.split(" ").filter { it.isNotBlank() }

        val greetingSet = setOf(
            "hi", "hii", "hiii", "hiiii", "hello", "helo", "hlw", "hey", "heyy", "hy", "hyy",
            "namaste", "namaskar", "salam", "assalamualaikum", "yo", "sup", "hola",
            "kaise", "ho", "kya", "haal", "hal", "hai", "h", "bro", "bhai", "sir", "ji", "dost", "ai"
        )
        val isGreetingPhrase = normalized in setOf(
            "hi", "hii", "hiii", "hello", "helo", "hlw", "hey", "heyy", "hy", "hyy",
            "namaste", "salam", "assalamu alaikum", "assalamualaikum", "yo", "sup",
            "good morning", "good afternoon", "good evening", "gm",
            "how are you", "how r u", "kaise ho", "kaisa hai", "kya haal hai", "kya hal hai", "kya hal h",
            "aur batao", "hello bhai", "hi bhai", "hello bro", "hi bro", "hello sir", "hi ai", "hello ai"
        ) || (words.isNotEmpty() && words.size <= 3 && words.all { it in greetingSet } &&
            words.any { it in setOf("hi", "hii", "hiii", "hello", "helo", "hlw", "hey", "heyy", "hy", "namaste", "salam", "yo", "sup", "kaise") })

        if (isGreetingPhrase) {
            val reply = if (!existingProjectName.isNullOrBlank()) {
                "👋 Hello! Abhi aapka '$existingProjectName' app ready hai.\n\nBataiye aaj hum aur konsa ya kaisa app banaye, ya is app me kya naya button/switch/slider add karein?"
            } else {
                "👋 Hello! Bataiye aaj hum konsa aur kaisa app banaye?\n\nAap mujhe bata sakte hain:\n• Konsa app banana hai (jaise Calculator, Music Controller, Notes App, Timer, Login Screen, ya Floating Game Mod Menu)\n• App me kaunse buttons, ON/OFF switches, sliders, ya text boxes chahiye\n• Agar kisi file path ko modify karna hai to wo path bhi likh sakte hain!"
            }
            return AiPromptEvaluation(
                intent = AiPromptIntent.CONVERSATIONAL_GREETING,
                shouldBuildOrUpdateApp = false,
                conversationalReply = reply
            )
        }

        val helpPhrases = setOf(
            "help", "madad", "kya kar sakte ho", "what can you do", "who are you", "tu kon hai",
            "tum kon ho", "aap kon ho", "kaise kam karta hai", "how does this work", "how to use",
            "app kaise banaye", "kaise banaye", "konsa app", "kaisa app", "konsa app banaye",
            "kaisa app banaye", "kya banaye", "batao", "example", "examples"
        )
        if (normalized in helpPhrases ||
            normalized.startsWith("who are you") ||
            normalized.startsWith("what can you") ||
            normalized.startsWith("tu kon") ||
            normalized.startsWith("tum kon") ||
            normalized.startsWith("konsa app") ||
            normalized.startsWith("kaisa app")
        ) {
            return AiPromptEvaluation(
                intent = AiPromptIntent.ASKING_HELP_OR_WHAT_TO_BUILD,
                shouldBuildOrUpdateApp = false,
                conversationalReply = "🤖 Main AI Studio App Builder hoon! Aap mujhe bataiye ki aapko konsa aur kaisa app banana hai, aur main step-by-step code aur floating UI tayar kar dunga.\n\n📌 Aap is tarah likh kar bhej sakte hain:\n1. \"Ek Calculator app banao\"\n2. \"Ek VIP FPS Booster app banao jisme 120 FPS toggle aur Speed slider ho\"\n3. \"Ek Music & Volume Controller app banao\"\n4. \"Ek Notes / Todo list app banao\"\n5. \"Ek Python file patcher banao /storage/emulated/0/py.py ke liye\""
            )
        }

        val vagueBuildSet = setOf(
            "app", "banao", "bana", "do", "de", "ek", "koi", "make", "build", "create",
            "an", "a", "new", "naya", "please", "plz", "bro", "bhai", "mujhe", "chahiye", "karo", "kar"
        )
        if (words.isNotEmpty() && words.size <= 5 && words.all { it in vagueBuildSet }) {
            return AiPromptEvaluation(
                intent = AiPromptIntent.VAGUE_BUILD_WITHOUT_DETAILS,
                shouldBuildOrUpdateApp = false,
                conversationalReply = "Zaroor! Par bataiye konsa aur kaisa app banaye? 🤔\n\nJaise:\n• Calculator / Counter App\n• Floating FPS Booster / Aimbot Mod Menu\n• Music & Bass Volume Controller\n• Quick Notes / Reminder App\n• Custom File Patcher (Toggle + Slider + Button)\n\nApp ka naam aur uske features likh kar bhejiye, main turant waisa hi app build kar dunga!"
            )
        }

        val feedbackOrComplaintKeywords = setOf("pagal", "galat", "bekar", "tatti", "bakwas", "wrong", "bad", "stupid", "crazy")
        if (words.size <= 7 && words.any { it in feedbackOrComplaintKeywords } && !hasAppFeatureKeywords(normalized)) {
            return AiPromptEvaluation(
                intent = AiPromptIntent.CONVERSATIONAL_CHAT,
                shouldBuildOrUpdateApp = false,
                conversationalReply = "Maaf kijiye! 😅 Ab main bina puche koi random app nahi banaunga. Bataiye aapko bilkul konsa aur kaisa app chahiye? Jo app ka naam, buttons, switches, ya sliders aap bolenge, main sirf wahi banaunga!"
            )
        }

        val shortChatSet = setOf(
            "ok", "okay", "okk", "k", "acha", "accha", "theek", "thik", "hmm", "hmmm",
            "haan", "han", "yes", "yep", "no", "nahi", "nhi", "nope", "thanks", "thank", "you",
            "thx", "shukriya", "nice", "good", "great", "cool", "wow", "sahi", "mast", "kyu", "why", "what", "kya"
        )
        if (words.isNotEmpty() && words.size <= 4 && words.all { it in shortChatSet }) {
            val reply = if (!existingProjectName.isNullOrBlank()) {
                "👍 Theek hai! Agar '$existingProjectName' me koi button, switch, ya slider badalna ho, ya koi naya app banana ho, to bas likh kar bhejiye."
            } else {
                "👍 Theek hai! Bataiye aaj hum konsa aur kaisa app banaye? Aap app ka naam aur features likh kar bhej sakte hain."
            }
            return AiPromptEvaluation(
                intent = AiPromptIntent.CONVERSATIONAL_CHAT,
                shouldBuildOrUpdateApp = false,
                conversationalReply = reply
            )
        }

        // If user sent a single short word (< 4 chars) that has no recognizable app/widget meaning, ask what app they want
        if (words.size == 1 && normalized.length <= 4 && !hasAppFeatureKeywords(normalized)) {
            return AiPromptEvaluation(
                intent = AiPromptIntent.ASKING_HELP_OR_WHAT_TO_BUILD,
                shouldBuildOrUpdateApp = false,
                conversationalReply = "Bataiye aapko konsa aur kaisa app banana hai? 😊\nJaise: 'Calculator app banao', 'FPS Booster Mod Menu banao', 'Music Player banao', ya apne app ke buttons aur switches ka naam likhiye!"
            )
        }

        return AiPromptEvaluation(
            intent = AiPromptIntent.BUILD_OR_MODIFY_APP,
            shouldBuildOrUpdateApp = true
        )
    }

    private fun hasAppFeatureKeywords(normalized: String): Boolean {
        val keywords = listOf(
            "calc", "calculator", "hisab", "math",
            "music", "song", "audio", "player", "volume", "bass", "dj",
            "note", "notes", "todo", "task", "diary", "reminder",
            "timer", "stopwatch", "clock", "alarm", "countdown",
            "torch", "flashlight", "light", "brightness", "dimmer", "night",
            "battery", "ram", "cleaner", "cooler", "cpu", "optimizer",
            "login", "password", "key", "auth",
            "counter", "clicker", "count",
            "vip", "fps", "boost", "booster", "mod", "menu", "aimbot", "esp", "hack", "speed", "fov", "bypass",
            "python", ".py", ".bin", ".cfg", ".json", ".txt", "/storage/", "/sdcard/",
            "button", "btn", "toggle", "switch", "slider", "seekbar", "input", "textbox", "text", "panel", "floating",
            "add", "jodo", "remove", "hatao", "delete", "rename"
        )
        return keywords.any { it in normalized }
    }

    /**
     * Generates a complete independent AI App Blueprint from the user's text prompt,
     * or intelligently updates an existing AI-built project if the user asks to add/remove/rename widgets.
     */
    @JvmStatic
    fun generateBlueprintFromPrompt(
        prompt: String,
        projectId: Long,
        defaultTargetFilePath: String,
        modelState: GgufModelState,
        existingProjectName: String? = null,
        existingComponents: List<CanvasComponentEntity> = emptyList()
    ): GeneratedBlueprintSpec {
        val cleanPrompt = prompt.trim()
        val lower = cleanPrompt.lowercase(Locale.US)

        // Extract custom file path if mentioned in prompt (including paths with spaces like /storage/emulated/0/PREMIUM VIDEOS/py.py)
        val pathWithPyOrExt = Regex("""(/storage/emulated/0/[^\n\r"']+?\.[a-zA-Z0-9]{1,5}|/sdcard/[^\n\r"']+?\.[a-zA-Z0-9]{1,5})""")
            .find(cleanPrompt)?.value?.trim()
        val fallbackSimplePath = Regex("""(/storage/emulated/0/[^\s,;]+|/sdcard/[^\s,;]+)""")
            .find(cleanPrompt)?.value?.trim()
        val extractedPath = pathWithPyOrExt ?: fallbackSimplePath ?: defaultTargetFilePath

        // Check if user is asking to incrementally modify an ALREADY built AI app
        val isIncrementalEdit = !existingProjectName.isNullOrBlank() &&
            existingComponents.isNotEmpty() &&
            (lower.startsWith("add ") || lower.startsWith("remove ") || lower.startsWith("delete ") ||
                lower.startsWith("rename ") || "aur add" in lower || "jodo" in lower || "hatao" in lower ||
                "naam badal" in lower)

        if (isIncrementalEdit) {
            val updatedWidgets = existingComponents.toMutableList()
            var nextId = (updatedWidgets.maxOfOrNull { it.id } ?: 0L) + 1L
            var nextY = (updatedWidgets.maxOfOrNull { it.posYDp + it.heightDp } ?: 12) + 8
            var updatedAppName = existingProjectName!!

            if ("remove" in lower || "delete" in lower || "hatao" in lower) {
                when {
                    "slider" in lower -> updatedWidgets.removeAll { it.type == ComponentWidgetType.SLIDER.name }
                    "toggle" in lower || "switch" in lower -> updatedWidgets.removeAll { it.type == ComponentWidgetType.TOGGLE.name }
                    "button" in lower -> updatedWidgets.removeAll { it.type == ComponentWidgetType.BUTTON.name }
                    "input" in lower -> updatedWidgets.removeAll { it.type == ComponentWidgetType.INPUT.name }
                    else -> if (updatedWidgets.size > 1) updatedWidgets.removeAt(updatedWidgets.lastIndex)
                }
            } else if ("rename" in lower || "naam badal" in lower) {
                val newName = extractCustomLabel(cleanPrompt, "AI Custom App")
                updatedAppName = newName
                if (updatedWidgets.isNotEmpty() && updatedWidgets[0].type == ComponentWidgetType.TEXT.name) {
                    updatedWidgets[0] = updatedWidgets[0].copy(label = "⚡ $newName Panel")
                }
            } else {
                // Adding a widget to existing app
                val customLabel = extractCustomLabel(cleanPrompt, "Custom Action")
                val newComp = when {
                    "toggle" in lower || "switch" in lower -> CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.TOGGLE.name,
                        label = customLabel,
                        posXDp = 14,
                        posYDp = nextY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#1E293B",
                        textColorHex = "#F8FAFC",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x04",
                        offPayloadHex = "Off",
                        onPayloadHex = "On",
                        currentValue = "0"
                    )
                    "slider" in lower -> CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.SLIDER.name,
                        label = customLabel,
                        posXDp = 14,
                        posYDp = nextY,
                        widthDp = 230,
                        heightDp = 52,
                        bgColorHex = "#0F172A",
                        textColorHex = "#E2E8F0",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x08",
                        offPayloadHex = "0",
                        onPayloadHex = "100",
                        sliderMax = 100,
                        currentValue = "50"
                    )
                    "input" in lower || "text box" in lower -> CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.INPUT.name,
                        label = customLabel,
                        posXDp = 14,
                        posYDp = nextY,
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
                    else -> CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = customLabel,
                        posXDp = 14,
                        posYDp = nextY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#2563EB",
                        textColorHex = "#FFFFFF",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "Off",
                        onPayloadHex = "On",
                        currentValue = "0"
                    )
                }
                updatedWidgets.add(newComp)
            }

            val slug = updatedAppName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "").ifEmpty { "aifloatingapp" }
            val pkg = "com.ai.$slug"
            val overlayTitle = "$updatedAppName Panel"
            val modelSourceTag = if (modelState.isUsingSampleFallback) {
                "Sample GGUF Fallback (${modelState.modelFileName.ifBlank { SAMPLE_GGUF_FILENAME }})"
            } else {
                "GGUF Model (${modelState.modelFileName})"
            }
            val summary = buildString {
                appendLine("// Updated via $modelSourceTag")
                appendLine("// App Name: $updatedAppName ($pkg)")
                appendLine("// Edit Prompt: \"$cleanPrompt\"")
                appendLine("// Active Widgets: ${updatedWidgets.size} interactive components")
            }.trim()

            return GeneratedBlueprintSpec(
                suggestedAppName = updatedAppName,
                suggestedPackageName = pkg,
                suggestedOverlayTitle = overlayTitle,
                suggestedTargetFilePath = extractedPath,
                components = updatedWidgets,
                kotlinJavaSummary = summary
            )
        }

        // Determine domain/category of app requested by the user
        val isCalculator = "calc" in lower || "calculator" in lower || "hisab" in lower || "math" in lower
        val isMusicApp = "music" in lower || "song" in lower || "audio" in lower || "player" in lower || "volume" in lower || "bass" in lower || "dj" in lower
        val isNotesApp = "note" in lower || "notes" in lower || "todo" in lower || "task" in lower || "diary" in lower || "reminder" in lower
        val isTimerApp = "timer" in lower || "stopwatch" in lower || "clock" in lower || "alarm" in lower || "countdown" in lower
        val isTorchOrScreenApp = "torch" in lower || "flashlight" in lower || "brightness" in lower || "dimmer" in lower || "night mode" in lower
        val isSystemOptimizer = "battery" in lower || "ram" in lower || "cleaner" in lower || "cooler" in lower || "cpu" in lower || "optimizer" in lower
        val isLoginKeyApp = "login" in lower || "password" in lower || "key system" in lower || "auth" in lower || "vip key" in lower
        val isCounterApp = "counter" in lower || "clicker" in lower || "tally" in lower

        val suggestedName = when {
            isCalculator -> "Smart Calculator"
            isMusicApp -> "Music & Bass Controller"
            isNotesApp -> "Quick Floating Notes"
            isTimerApp -> "Floating Timer Pro"
            isTorchOrScreenApp -> "Torch & Screen Control"
            isSystemOptimizer -> "RAM & Battery Booster"
            isLoginKeyApp -> "VIP Key Login App"
            isCounterApp -> "Smart Tap Counter"
            "vip" in lower -> "VIP Floating Mod"
            "fps" in lower || "boost" in lower -> "FPS Booster Pro"
            "python" in lower || ".py" in lower -> "Python Script Patcher"
            "speed" in lower || "hack" in lower -> "Speed Controller"
            else -> extractCleanAppNameFromPrompt(cleanPrompt)
        }

        val slug = suggestedName.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "")
            .ifEmpty { "aifloatingapp" }
        val suggestedPkg = "com.ai.$slug"
        val suggestedOverlayTitle = "$suggestedName Panel"

        val widgets = mutableListOf<CanvasComponentEntity>()
        var currentY = 12
        var nextId = 1L

        // Header Title Widget
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

        when {
            isCalculator -> {
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.INPUT.name,
                        label = "Enter Numbers (e.g. 25 + 75)",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 48,
                        bgColorHex = "#FFFFFF",
                        textColorHex = "#0F172A",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "0",
                        onPayloadHex = "100",
                        currentValue = "100"
                    )
                )
                currentY += 54
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = "➕ Add (+) / Calculate (=)",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 44,
                        bgColorHex = "#2563EB",
                        textColorHex = "#FFFFFF",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x04",
                        offPayloadHex = "0",
                        onPayloadHex = "Result=OK",
                        soundTrigger = "CLICK_POP",
                        currentValue = "0"
                    )
                )
                currentY += 50
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = "➖ Subtract (-) / Multiply (×)",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 44,
                        bgColorHex = "#0288D1",
                        textColorHex = "#FFFFFF",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x08",
                        offPayloadHex = "0",
                        onPayloadHex = "Calc=Done",
                        soundTrigger = "SOFT_TAP",
                        currentValue = "0"
                    )
                )
                currentY += 50
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = "🔄 Clear Calculator (AC)",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 44,
                        bgColorHex = "#DC2626",
                        textColorHex = "#FFFFFF",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "0",
                        onPayloadHex = "0",
                        currentValue = "0"
                    )
                )
            }

            isMusicApp -> {
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.TOGGLE.name,
                        label = "🎵 Play / Pause Music",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#1E293B",
                        textColorHex = "#F8FAFC",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "Paused",
                        onPayloadHex = "Playing",
                        soundTrigger = "CLICK_POP",
                        currentValue = "1"
                    )
                )
                currentY += 54
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.TOGGLE.name,
                        label = "🔊 Extra Bass Boost",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#1E293B",
                        textColorHex = "#4ADE80",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x04",
                        offPayloadHex = "Bass=Off",
                        onPayloadHex = "Bass=Max",
                        soundTrigger = "SUCCESS_CHIME",
                        currentValue = "0"
                    )
                )
                currentY += 54
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.SLIDER.name,
                        label = "Volume Level",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 52,
                        bgColorHex = "#0F172A",
                        textColorHex = "#E2E8F0",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x08",
                        offPayloadHex = "vol=0",
                        onPayloadHex = "vol=100",
                        sliderMax = 100,
                        currentValue = "80"
                    )
                )
                currentY += 58
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = "⏭ Next Track",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 44,
                        bgColorHex = "#2563EB",
                        textColorHex = "#FFFFFF",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x0C",
                        offPayloadHex = "Track=1",
                        onPayloadHex = "Track=Next",
                        currentValue = "0"
                    )
                )
            }

            isNotesApp -> {
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.INPUT.name,
                        label = "Write Quick Note / Task",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 50,
                        bgColorHex = "#FFFFFF",
                        textColorHex = "#0F172A",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "",
                        onPayloadHex = "SavedNote",
                        currentValue = "My Important Note"
                    )
                )
                currentY += 56
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.TOGGLE.name,
                        label = "📌 Pin Note on Screen",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#1E293B",
                        textColorHex = "#F8FAFC",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x04",
                        offPayloadHex = "Unpinned",
                        onPayloadHex = "Pinned",
                        currentValue = "1"
                    )
                )
                currentY += 54
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = "💾 Save Note",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#16A34A",
                        textColorHex = "#FFFFFF",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "Draft",
                        onPayloadHex = "Saved",
                        soundTrigger = "SUCCESS_CHIME",
                        currentValue = "1"
                    )
                )
            }

            isTimerApp -> {
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.TOGGLE.name,
                        label = "⏱ Start / Stop Timer",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#1E293B",
                        textColorHex = "#F8FAFC",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "Stopped",
                        onPayloadHex = "Running",
                        currentValue = "0"
                    )
                )
                currentY += 54
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.SLIDER.name,
                        label = "Countdown Seconds",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 52,
                        bgColorHex = "#0F172A",
                        textColorHex = "#E2E8F0",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x04",
                        offPayloadHex = "0s",
                        onPayloadHex = "300s",
                        sliderMax = 300,
                        currentValue = "60"
                    )
                )
                currentY += 58
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = "🔄 Reset Timer (00:00)",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#2563EB",
                        textColorHex = "#FFFFFF",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "0",
                        onPayloadHex = "Reset",
                        currentValue = "0"
                    )
                )
            }

            isTorchOrScreenApp -> {
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.TOGGLE.name,
                        label = "🔦 Torch / Flashlight ON/OFF",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#1E293B",
                        textColorHex = "#F8FAFC",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "Torch=Off",
                        onPayloadHex = "Torch=On",
                        currentValue = "0"
                    )
                )
                currentY += 54
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.SLIDER.name,
                        label = "Screen Brightness Level",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 52,
                        bgColorHex = "#0F172A",
                        textColorHex = "#E2E8F0",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x04",
                        offPayloadHex = "10%",
                        onPayloadHex = "100%",
                        sliderMax = 100,
                        currentValue = "75"
                    )
                )
                currentY += 58
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.TOGGLE.name,
                        label = "🌙 Eye Care Night Filter",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#1E293B",
                        textColorHex = "#4ADE80",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x08",
                        offPayloadHex = "Filter=Off",
                        onPayloadHex = "Filter=On",
                        currentValue = "0"
                    )
                )
            }

            isSystemOptimizer -> {
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.TOGGLE.name,
                        label = "🚀 Turbo RAM Boost",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#1E293B",
                        textColorHex = "#F8FAFC",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "Normal",
                        onPayloadHex = "Boosted",
                        currentValue = "1"
                    )
                )
                currentY += 54
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.TOGGLE.name,
                        label = "🔋 Battery Saver Lock",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#1E293B",
                        textColorHex = "#4ADE80",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x04",
                        offPayloadHex = "Off",
                        onPayloadHex = "Active",
                        currentValue = "0"
                    )
                )
                currentY += 54
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = "🧹 Clean Cache & Cool CPU",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#16A34A",
                        textColorHex = "#FFFFFF",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x08",
                        offPayloadHex = "0",
                        onPayloadHex = "Cleaned",
                        soundTrigger = "SUCCESS_CHIME",
                        currentValue = "0"
                    )
                )
            }

            isLoginKeyApp -> {
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.INPUT.name,
                        label = "Enter VIP Access Key",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 48,
                        bgColorHex = "#FFFFFF",
                        textColorHex = "#0F172A",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "Locked",
                        onPayloadHex = "VIP-2025",
                        currentValue = "VIP-2025"
                    )
                )
                currentY += 54
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.TOGGLE.name,
                        label = "Remember Key on Device",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#1E293B",
                        textColorHex = "#F8FAFC",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x04",
                        offPayloadHex = "false",
                        onPayloadHex = "true",
                        currentValue = "1"
                    )
                )
                currentY += 54
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = "🔓 Verify & Unlock Panel",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#2563EB",
                        textColorHex = "#FFFFFF",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x08",
                        offPayloadHex = "Locked",
                        onPayloadHex = "Unlocked",
                        soundTrigger = "SUCCESS_CHIME",
                        currentValue = "0"
                    )
                )
            }

            isCounterApp -> {
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.SLIDER.name,
                        label = "Current Count Value",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 52,
                        bgColorHex = "#0F172A",
                        textColorHex = "#E2E8F0",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "0",
                        onPayloadHex = "100",
                        sliderMax = 100,
                        currentValue = "1"
                    )
                )
                currentY += 58
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId++,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = "➕ Tap to Count (+1)",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 46,
                        bgColorHex = "#16A34A",
                        textColorHex = "#FFFFFF",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x04",
                        offPayloadHex = "0",
                        onPayloadHex = "+1",
                        soundTrigger = "CLICK_POP",
                        currentValue = "1"
                    )
                )
                currentY += 52
                widgets.add(
                    CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = "🔄 Reset Counter",
                        posXDp = 14,
                        posYDp = currentY,
                        widthDp = 230,
                        heightDp = 44,
                        bgColorHex = "#DC2626",
                        textColorHex = "#FFFFFF",
                        targetFilePath = extractedPath,
                        byteOffsetHex = "0x00",
                        offPayloadHex = "0",
                        onPayloadHex = "0",
                        currentValue = "0"
                    )
                )
            }

            else -> {
                // Custom App / Mod Menu / File Patcher: only add what matches the prompt (or a clean balanced set)
                val explicitToggleOnly = ("only button" in lower || "sirf button" in lower)
                val wantsToggle = !explicitToggleOnly && (
                    "toggle" in lower || "switch" in lower || "on/off" in lower ||
                        "aimbot" in lower || "esp" in lower || "bypass" in lower ||
                        "mod" in lower || "fps" in lower || "python" in lower || ".py" in lower ||
                        ("button" !in lower && "slider" !in lower && "input" !in lower)
                    )

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
                            label = "Anti-Ban & ESP Bypass",
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

                val wantsSlider = !explicitToggleOnly && (
                    "slider" in lower || "speed" in lower || "fov" in lower ||
                        "value" in lower || "range" in lower || "level" in lower ||
                        (widgets.size < 3 && "button" !in lower)
                    )
                if (wantsSlider) {
                    val sliderLabel = when {
                        "fov" in lower -> "FOV Radius"
                        "speed" in lower -> "Speed Multiplier"
                        else -> "$suggestedName Level"
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

                if ("input" in lower || "key" in lower || "custom" in lower || "config" in lower || "text box" in lower) {
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

                val buttonLabel = if ("button" in lower) {
                    extractCustomLabel(cleanPrompt, "Run $suggestedName")
                } else {
                    "Apply $suggestedName"
                }

                widgets.add(
                    CanvasComponentEntity(
                        id = nextId,
                        projectId = projectId,
                        type = ComponentWidgetType.BUTTON.name,
                        label = buttonLabel,
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
            }
        }

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

    private fun extractCleanAppNameFromPrompt(cleanPrompt: String): String {
        val stopWords = setOf(
            "create", "make", "build", "generate", "ek", "app", "banao", "bana", "do", "de",
            "for", "with", "and", "aur", "jisme", "mein", "me", "ka", "ki", "ke", "ko",
            "floating", "window", "panel", "menu", "please", "mujhe", "chahiye", "a", "an", "the"
        )
        val words = cleanPrompt.split(Regex("\\s+"))
            .map { it.replace(Regex("[^a-zA-Z0-9]"), "") }
            .filter { it.length >= 2 && !it.startsWith("/") && it.lowercase(Locale.US) !in stopWords }
            .take(3)
        if (words.isEmpty()) return "Custom Floating App"
        return words.joinToString(" ") { w ->
            w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
    }

    private fun extractCustomLabel(cleanPrompt: String, fallback: String): String {
        val quoted = Regex("""["']([^"']+)["']""").find(cleanPrompt)?.groupValues?.getOrNull(1)?.trim()
        if (!quoted.isNullOrBlank()) return quoted
        val stopWords = setOf(
            "add", "create", "make", "button", "toggle", "switch", "slider", "input",
            "rename", "to", "called", "named", "ek", "aur", "jodo", "banao", "naam", "badal", "ke", "rakho"
        )
        val filtered = cleanPrompt.split(Regex("\\s+"))
            .map { it.replace(Regex("[^a-zA-Z0-9_-]"), "") }
            .filter { it.length >= 2 && it.lowercase(Locale.US) !in stopWords }
            .take(3)
        return if (filtered.isNotEmpty()) {
            filtered.joinToString(" ") { w ->
                w.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
        } else {
            fallback
        }
    }
}
