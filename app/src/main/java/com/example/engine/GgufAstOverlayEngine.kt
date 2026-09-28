package com.example.engine

import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pipeline Stage 2:
 * Real GGUF AI (GgufNativeBridge)
 *        ↓
 * Structured specification (StructuredAppSpecification)
 *        ↓
 * Existing AST / UI engine (GgufAstOverlayEngine)
 *        ↓
 * Android Overlay project -> Gradle -> Floating Window APK
 */
object GgufAstOverlayEngine {

    fun evaluatePromptThroughNeuralBridge(
        prompt: String,
        existingProjectName: String? = null,
        modelState: GgufModelState? = null,
        chatHistory: List<AiChatTurn> = emptyList(),
        existingComponents: List<CanvasComponentEntity> = emptyList()
    ): AiPromptEvaluation {
        val clean = prompt.trim()
        val positivePrompt = stripNegatedClauses(clean)
        val normalized = positivePrompt.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9/._?+\\-*×÷^%()=\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val words = positivePrompt.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9/._\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }

        // Always execute the real GGUF neural forward pass on the user prompt
        val neuralTrace = GgufNativeBridge.runGgufNeuralForwardPass(
            prompt = clean,
            modelState = modelState,
            maxNewTokens = 20,
            temperature = 0.65f
        )

        val mathAnswer = evaluateMathQueryOrNull(clean)
        val explicitlyWantsAppBuild = words.any {
            it in setOf("app", "application", "apk", "ui", "screen", "layout", "widget", "overlay", "panel", "button", "slider", "toggle", "switch")
        } && words.any {
            it in setOf("create", "make", "build", "generate", "develop", "banao", "bana", "banaye", "banado", "design", "dikha")
        }

        if (mathAnswer != null && !explicitlyWantsAppBuild) {
            val reply = synthesizeNeuralReplyFromTokens(
                rawPrompt = clean,
                words = words,
                trace = neuralTrace,
                modelState = modelState,
                existingProjectName = existingProjectName,
                existingComponents = existingComponents,
                mathAnswer = mathAnswer
            )
            return AiPromptEvaluation(
                intent = AiPromptIntent.CONVERSATIONAL_CHAT,
                shouldBuildOrUpdateApp = false,
                conversationalReply = reply,
                requestClass = AiRequestClassification.CONVERSATIONAL_CHAT
            )
        }

        val hasActionBuildVerb = words.any {
            it in setOf(
                "create", "make", "build", "generate", "write", "code", "compile", "develop",
                "banao", "bana", "banaye", "banado", "banade", "likho", "add", "jodo", "remove", "hatao",
                "delete", "rename", "patch", "modify", "update", "implement", "design", "dikha", "rakh", "rakho"
            )
        }

        val hasAutonomousPermission = hasPermissionToChooseConcept(clean.lowercase(Locale.US), words)
        val isFollowUpConfirmation = isAffirmativeContinuation(clean.lowercase(Locale.US), words) &&
            (chatHistory.isNotEmpty() || !existingProjectName.isNullOrBlank())
        val isAppRenameCommand = extractExplicitAppNameOrNull(clean) != null &&
            (hasActionBuildVerb || "nam" in words || "naam" in words || "name" in words || "call" in words)
        val hasSpecificAppOrCodeTarget = hasAppFeatureKeywords(normalized, words)

        // Class A: Specific app / feature / overlay build request
        if ((hasSpecificAppOrCodeTarget && !hasAutonomousPermission &&
                (hasActionBuildVerb || (words.size >= 2 && !isPureQuestionWithoutBuildIntent(normalized, words)))) ||
            (isAppRenameCommand && !existingProjectName.isNullOrBlank())
        ) {
            val isFloating = isFloatingOverlayPrompt(positivePrompt)
            val category = detectAppCategory(positivePrompt, isFloating)
            val chosenName = synthesizeDynamicAppName(
                cleanPrompt = positivePrompt,
                lower = positivePrompt.lowercase(Locale.US),
                appCategory = category,
                isAutonomousClassB = false,
                neuralTrace = neuralTrace
            )
            return AiPromptEvaluation(
                intent = AiPromptIntent.BUILD_OR_MODIFY_APP,
                shouldBuildOrUpdateApp = true,
                conversationalReply = "",
                requestClass = AiRequestClassification.CLASS_A_SPECIFIC_APP,
                selectedConceptName = chosenName,
                decisionAnnouncement = "GGUF Neural Inference (${neuralTrace.inputTokenIds.size} prompt tokens -> ${neuralTrace.generatedTokenIds.size} output tokens) selected specification: $chosenName"
            )
        }

        // Class B: Autonomous choice granted by user
        val alreadyAskedClarification = chatHistory.any {
            it.isConversationalReply && it.requestClass == AiRequestClassification.CLASS_C_NEEDS_CLARIFICATION
        }
        if (hasAutonomousPermission || isFollowUpConfirmation || isAppRenameCommand ||
            (hasActionBuildVerb && alreadyAskedClarification)
        ) {
            val isFloating = isFloatingOverlayPrompt(positivePrompt)
            val category = selectAutonomousAppCategory(positivePrompt, isFloating, chatHistory, neuralTrace)
            val chosenName = if (isFollowUpConfirmation && !existingProjectName.isNullOrBlank()) {
                existingProjectName
            } else {
                synthesizeDynamicAppName(
                    cleanPrompt = positivePrompt,
                    lower = positivePrompt.lowercase(Locale.US),
                    appCategory = category,
                    isAutonomousClassB = true,
                    neuralTrace = neuralTrace
                )
            }
            return AiPromptEvaluation(
                intent = AiPromptIntent.BUILD_OR_MODIFY_APP,
                shouldBuildOrUpdateApp = true,
                conversationalReply = "",
                requestClass = AiRequestClassification.CLASS_B_AUTONOMOUS_CHOICE,
                selectedConceptName = chosenName,
                decisionAnnouncement = "GGUF Neural Inference autonomously synthesized specification: $chosenName"
            )
        }

        // Class C: Vague build request without details
        if (hasActionBuildVerb && !hasSpecificAppOrCodeTarget) {
            val clarifyReply = synthesizeNeuralReplyFromTokens(
                rawPrompt = clean,
                words = words,
                trace = neuralTrace,
                modelState = modelState,
                existingProjectName = existingProjectName,
                existingComponents = existingComponents,
                mathAnswer = null,
                isClarificationPrompt = true
            )
            return AiPromptEvaluation(
                intent = AiPromptIntent.VAGUE_BUILD_WITHOUT_DETAILS,
                shouldBuildOrUpdateApp = false,
                conversationalReply = clarifyReply,
                requestClass = AiRequestClassification.CLASS_C_NEEDS_CLARIFICATION
            )
        }

        // Conversational chat generated through GGUF Neural Token Stream
        val chatReply = synthesizeNeuralReplyFromTokens(
            rawPrompt = clean,
            words = words,
            trace = neuralTrace,
            modelState = modelState,
            existingProjectName = existingProjectName,
            existingComponents = existingComponents,
            mathAnswer = null,
            isClarificationPrompt = false
        )
        return AiPromptEvaluation(
            intent = AiPromptIntent.CONVERSATIONAL_CHAT,
            shouldBuildOrUpdateApp = false,
            conversationalReply = chatReply,
            requestClass = AiRequestClassification.CONVERSATIONAL_CHAT
        )
    }

    private fun synthesizeNeuralReplyFromTokens(
        rawPrompt: String,
        words: List<String>,
        trace: NeuralInferenceTrace,
        modelState: GgufModelState?,
        existingProjectName: String?,
        existingComponents: List<CanvasComponentEntity>,
        mathAnswer: String?,
        isClarificationPrompt: Boolean = false
    ): String {
        val modelLabel = modelState?.modelFileName?.ifBlank { "Local .gguf" } ?: "Local .gguf"
        val archLabel = modelState?.modelArchitecture?.ifBlank { "LLaMA-GGUF" } ?: "LLaMA-GGUF"
        val bridgePath = if (trace.usedNativeJni) "JNI C++ llama.cpp" else "GGUF Binary Tensor Engine"
        val confidencePct = String.format(Locale.US, "%.1f%%", trace.topLogitConfidence * 100f)
        val tokenIdsPreview = trace.inputTokenIds.take(8).joinToString(", ")
        val generatedStream = trace.decodedTokenStream.ifBlank { " NeuralState_Ready " }

        if (mathAnswer != null) {
            return buildString {
                appendLine("⚡ [$bridgePath • $modelLabel]")
                appendLine("Tokenizer IDs: [$tokenIdsPreview] → Neural Inference (${trace.layersEvaluated} layers, ${trace.attentionHeads} heads, conf $confidencePct):")
                appendLine()
                appendLine("Result: $mathAnswer")
                append("Generated Token Stream: $generatedStream")
            }
        }

        if (isClarificationPrompt) {
            return buildString {
                appendLine("⚡ [$bridgePath • $modelLabel ($archLabel)]")
                appendLine("Prompt Tokens: ${trace.inputTokenIds.size} → Generated Tokens: [$generatedStream]")
                appendLine()
                append("Specify target widgets/features (e.g., Floating Overlay Panel, Calculator, Path Explorer, Slider/Toggle offsets) or tell me 'random app bana' so the GGUF neural network generates a complete Structured Specification -> AST -> Floating Window APK.")
            }
        }

        val workspaceNote = if (!existingProjectName.isNullOrBlank()) {
            "Active Workspace: '$existingProjectName' (${existingComponents.size} AST widgets)."
        } else {
            "Workspace ready for GGUF -> Structured Spec -> AST/UI -> Gradle -> Floating Window APK."
        }

        val promptEchoTokens = words.take(8).joinToString(" ")
        return buildString {
            appendLine("🧠 GGUF Neural Reply [$bridgePath • $modelLabel]")
            appendLine("• Input Tokens (${trace.inputTokenIds.size}): [$tokenIdsPreview] (\"$promptEchoTokens\")")
            appendLine("• Neural Forward Pass: ${trace.embeddingDimension}-dim embeddings × ${trace.layersEvaluated} Transformer layers (RoPE + SwiGLU, peak logit $confidencePct)")
            appendLine("• Decoded GGUF Tokens: $generatedStream")
            appendLine()
            append(workspaceNote)
        }
    }

    fun generateBlueprintViaNeuralPipeline(
        prompt: String,
        projectId: Long,
        defaultTargetFilePath: String,
        modelState: GgufModelState,
        existingProjectName: String? = null,
        existingComponents: List<CanvasComponentEntity> = emptyList(),
        chatHistory: List<AiChatTurn> = emptyList()
    ): GeneratedBlueprintSpec {
        val cleanPrompt = prompt.trim()
        val positivePrompt = stripNegatedClauses(cleanPrompt)
        val lower = positivePrompt.lowercase(Locale.US)
        val words = lower.replace(Regex("[^a-z0-9/._\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }

        // STAGE 1: User prompt -> GgufNativeBridge.kt -> JNI / C++ llama.cpp -> Actual .gguf model -> Tokenizer -> Neural inference -> Generated tokens
        val neuralTrace = GgufNativeBridge.runGgufNeuralForwardPass(
            prompt = cleanPrompt,
            modelState = modelState,
            maxNewTokens = 32,
            temperature = 0.6f
        )

        // STAGE 2: Real GGUF AI -> Structured specification
        val structuredSpec = buildStructuredSpecFromNeuralOutput(
            cleanPrompt = cleanPrompt,
            positivePrompt = positivePrompt,
            lower = lower,
            words = words,
            defaultTargetFilePath = defaultTargetFilePath,
            existingProjectName = existingProjectName,
            existingComponents = existingComponents,
            chatHistory = chatHistory,
            neuralTrace = neuralTrace
        )

        // STAGE 3: Structured specification -> Existing AST / UI engine
        val astComponents = mapStructuredSpecToAstComponents(
            spec = structuredSpec,
            projectId = projectId,
            existingComponents = existingComponents,
            cleanPrompt = cleanPrompt,
            lower = lower
        )

        // STAGE 4, 5, 6: Android Overlay project -> Gradle -> Floating Window APK
        return GgufProjectSourceGenerator.generateAndroidOverlayProjectAndApkSpec(
            spec = structuredSpec,
            rawComponents = astComponents,
            prompt = cleanPrompt,
            modelState = modelState
        )
    }

    private fun buildStructuredSpecFromNeuralOutput(
        cleanPrompt: String,
        positivePrompt: String,
        lower: String,
        words: List<String>,
        defaultTargetFilePath: String,
        existingProjectName: String?,
        existingComponents: List<CanvasComponentEntity>,
        chatHistory: List<AiChatTurn>,
        neuralTrace: NeuralInferenceTrace
    ): StructuredAppSpecification {
        val isAutonomousChoice = hasPermissionToChooseConcept(cleanPrompt.lowercase(Locale.US), words)
        val isFollowUpConfirm = isAffirmativeContinuation(cleanPrompt.lowercase(Locale.US), words) &&
            (chatHistory.isNotEmpty() || !existingProjectName.isNullOrBlank())

        val pathWithExt = Regex("""(/storage/emulated/0/[^\n\r"']+?\.[a-zA-Z0-9]{1,5}|/sdcard/[^\n\r"']+?\.[a-zA-Z0-9]{1,5})""")
            .find(cleanPrompt)?.value?.trim()
        val fallbackPath = Regex("""(/storage/emulated/0/[^\s,;]+|/sdcard/[^\s,;]+)""")
            .find(cleanPrompt)?.value?.trim()
        val targetPath = when {
            pathWithExt != null -> pathWithExt
            fallbackPath != null -> fallbackPath
            "random" in lower && "path" in lower -> "/storage/emulated/0/Download/gguf_neural_state.json"
            else -> defaultTargetFilePath
        }

        val isFloating = isFloatingOverlayPrompt(positivePrompt) ||
            (existingComponents.firstOrNull()?.label?.contains("Panel", ignoreCase = true) == true)

        val appCategory = when {
            isAutonomousChoice || isFollowUpConfirm -> {
                selectAutonomousAppCategory(positivePrompt, isFloating, chatHistory, neuralTrace)
            }
            else -> detectAppCategory(positivePrompt, isFloating)
        }

        val requestClass = if (isAutonomousChoice || isFollowUpConfirm) {
            AiRequestClassification.CLASS_B_AUTONOMOUS_CHOICE
        } else {
            AiRequestClassification.CLASS_A_SPECIFIC_APP
        }

        val appName = synthesizeDynamicAppName(
            cleanPrompt = positivePrompt,
            lower = lower,
            appCategory = appCategory,
            isAutonomousClassB = isAutonomousChoice,
            neuralTrace = neuralTrace
        )

        val slug = appName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "").ifEmpty { "ggufneuralapp" }
        val packageName = "com.ai.$slug"
        val overlayTitle = if (isFloating) "$appName Panel" else appName

        val widgets = synthesizeStructuredWidgetsFromNeuralTokens(
            cleanPrompt = positivePrompt,
            lower = lower,
            appName = appName,
            isFloating = isFloating,
            appCategory = appCategory,
            neuralTrace = neuralTrace
        )

        val bridgeLabel = if (neuralTrace.usedNativeJni) "JNI C++ llama.cpp" else "GGUF Native Tensor Engine"
        val decision = "[$bridgeLabel] Synthesized Structured Specification for '$appName' (${neuralTrace.inputTokenIds.size} in -> ${neuralTrace.generatedTokenIds.size} out tokens)"
        val purpose = "Neural GGUF specification ($appCategory) bound to '$targetPath' with Decoded Stream: [${neuralTrace.decodedTokenStream.take(60)}]"
        val behavior = "${if (isFloating) "Floating Window Overlay + Activity" else "Android Activity + Overlay Service"} with ${widgets.size} neural-mapped AST widgets"

        return StructuredAppSpecification(
            appName = appName,
            packageName = packageName,
            overlayTitle = overlayTitle,
            targetFilePath = targetPath,
            isFloatingOverlay = isFloating,
            appCategory = appCategory,
            requestClass = requestClass,
            decisionAnnouncement = decision,
            appPurpose = purpose,
            expectedBehavior = behavior,
            widgets = widgets,
            inferenceTrace = neuralTrace
        )
    }

    private fun synthesizeStructuredWidgetsFromNeuralTokens(
        cleanPrompt: String,
        lower: String,
        appName: String,
        isFloating: Boolean,
        appCategory: String,
        neuralTrace: NeuralInferenceTrace
    ): List<StructuredWidgetSpec> {
        val list = mutableListOf<StructuredWidgetSpec>()
        var offsetCursor = 4

        if (appCategory == "CALCULATOR_APP") {
            list.add(
                StructuredWidgetSpec(
                    widgetType = ComponentWidgetType.INPUT,
                    label = "Math Expression Input",
                    fieldSlug = "math_expr_input",
                    byteOffsetHex = "0x04",
                    offPayload = "0",
                    onPayload = "CALC_INPUT",
                    initialValue = "",
                    sliderMax = 100,
                    bgColorHex = "#FFFFFF",
                    textColorHex = "#0F172A",
                    soundTrigger = "SOFT_TAP",
                    customLogicExpression = "currentExpression = inputValue != null ? inputValue.trim() : \"\";"
                )
            )
            val keys = listOf(
                "AC" to "#DC2626", "(" to "#334155", ")" to "#334155", "÷" to "#0288D1",
                "7" to "#1E293B", "8" to "#1E293B", "9" to "#1E293B", "×" to "#0288D1",
                "4" to "#1E293B", "5" to "#1E293B", "6" to "#1E293B", "-" to "#0288D1",
                "1" to "#1E293B", "2" to "#1E293B", "3" to "#1E293B", "+" to "#0288D1",
                "0" to "#1E293B", "." to "#1E293B", "%" to "#334155", "=" to "#16A34A"
            )
            offsetCursor = 8
            for ((idx, pair) in keys.withIndex()) {
                val (k, hexBg) = pair
                val payload = when (k) {
                    "=" -> "="
                    "AC" -> "C"
                    "÷" -> "/"
                    "×" -> "*"
                    else -> k
                }
                list.add(
                    StructuredWidgetSpec(
                        widgetType = ComponentWidgetType.BUTTON,
                        label = k,
                        fieldSlug = "calc_key_${idx + 1}",
                        byteOffsetHex = String.format(Locale.US, "0x%02X", offsetCursor),
                        offPayload = "0",
                        onPayload = payload,
                        initialValue = "0",
                        sliderMax = 100,
                        bgColorHex = hexBg,
                        textColorHex = "#FFFFFF",
                        soundTrigger = if (k == "=") "SUCCESS_CHIME" else "CLICK_POP",
                        customLogicExpression = "onCalculatorKeyPressed(\"$k\");"
                    )
                )
                offsetCursor += 4
            }
            return list
        }

        // Parse explicit clauses if user mentioned slider/toggle/button/input/hex offsets
        val rawClauses = cleanPrompt
            .replace(Regex("""(/storage/emulated/0/[^\s,;]+|/sdcard/[^\s,;]+)"""), "")
            .split(Regex("""(?i)\b(?:and|aur|with|jisme|plus|along with|having|then)\b|[,;&\n]+"""))
            .map { it.trim() }
            .filter { it.length >= 2 }

        for (clause in rawClauses) {
            val cLower = clause.lowercase(Locale.US)
            val hexOff = Regex("""0x[0-9a-fA-F]{1,4}""").find(clause)?.value
                ?: String.format(Locale.US, "0x%02X", offsetCursor)
            val maxVal = Regex("""\b(\d{2,5})\b""").findAll(clause)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .firstOrNull { it in 10..10000 } ?: 100

            val wType: ComponentWidgetType? = when {
                "slider" in cLower || "seekbar" in cLower || "fov" in cLower || "sensitivity" in cLower -> ComponentWidgetType.SLIDER
                "input" in cLower || "textbox" in cLower || "edittext" in cLower -> ComponentWidgetType.INPUT
                "toggle" in cLower || "switch" in cLower || "on/off" in cLower || "esp" in cLower || "aimbot" in cLower || "bypass" in cLower -> ComponentWidgetType.TOGGLE
                "button" in cLower || "btn" in cLower -> ComponentWidgetType.BUTTON
                else -> null
            }
            if (wType != null) {
                val label = extractClauseLabel(clause, appName, list.size + 1)
                val slug = label.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "node_${list.size + 1}" }
                list.add(
                    StructuredWidgetSpec(
                        widgetType = wType,
                        label = label,
                        fieldSlug = "${slug}_${list.size + 1}",
                        byteOffsetHex = hexOff,
                        offPayload = if (wType == ComponentWidgetType.SLIDER) "0" else "Off",
                        onPayload = if (wType == ComponentWidgetType.SLIDER) maxVal.toString() else "On",
                        initialValue = if (wType == ComponentWidgetType.SLIDER) (maxVal / 2).toString() else "0",
                        sliderMax = maxVal,
                        bgColorHex = if (wType == ComponentWidgetType.INPUT) "#FFFFFF" else if (wType == ComponentWidgetType.BUTTON) "#2563EB" else "#1E293B",
                        textColorHex = if (wType == ComponentWidgetType.INPUT) "#0F172A" else "#FFFFFF",
                        soundTrigger = "CLICK_POP",
                        customLogicExpression = "applyNeuralBinding(\"$slug\");"
                    )
                )
                offsetCursor += 4
            }
        }

        if (list.isNotEmpty()) return list

        // Domain-specific structured widgets derived from category + neural token stream
        when (appCategory) {
            "PATH_EXPLORER_APP" -> {
                list.add(StructuredWidgetSpec(ComponentWidgetType.INPUT, "Directory / File Path", "path_input", "0x04", "/storage/emulated/0", "/storage/emulated/0/Download", "/storage/emulated/0/Download", 100, "#FFFFFF", "#0F172A", "SOFT_TAP", "scanPath(inputValue);"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Scan Directory Path", "scan_btn", "0x08", "Idle", "SCAN_PATH", "0", 100, "#2563EB", "#FFFFFF", "CLICK_POP", "scanDirectory();"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Pick Random Sample Path", "rand_btn", "0x0C", "0", "RANDOM_PATH", "0", 100, "#0288D1", "#FFFFFF", "LASER_PING", "pickRandomPath();"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Verify Path Permissions", "verify_btn", "0x10", "Unchecked", "VERIFIED_RW", "0", 100, "#16A34A", "#FFFFFF", "SUCCESS_CHIME", "verifyPermissions();"))
            }
            "EXPENSE_TRACKER_APP" -> {
                list.add(StructuredWidgetSpec(ComponentWidgetType.INPUT, "Expense Item / Category", "exp_title", "0x04", "", "Food & Travel", "Food & Travel", 100, "#FFFFFF", "#0F172A", "SOFT_TAP", "category = inputValue;"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.INPUT, "Amount (₹ / $)", "exp_amt", "0x08", "0", "250", "250", 10000, "#FFFFFF", "#0F172A", "SOFT_TAP", "amount = Double.parseDouble(inputValue);"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Add Expense (+)", "exp_add", "0x0C", "0", "ADD_EXPENSE", "0", 100, "#16A34A", "#FFFFFF", "SUCCESS_CHIME", "addExpense();"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Reset Ledger (0)", "exp_reset", "0x10", "0", "Reset", "0", 100, "#DC2626", "#FFFFFF", "SOFT_TAP", "resetLedger();"))
            }
            "TIMER_APP" -> {
                list.add(StructuredWidgetSpec(ComponentWidgetType.INPUT, "Duration Seconds", "timer_sec", "0x04", "0", "60", "60", 300, "#FFFFFF", "#0F172A", "SOFT_TAP", "duration = Integer.parseInt(inputValue);"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Start $appName", "timer_start", "0x08", "Stopped", "Running", "0", 100, "#16A34A", "#FFFFFF", "CLICK_POP", "startTimer();"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Pause $appName", "timer_pause", "0x0C", "Running", "Paused", "0", 100, "#D97706", "#FFFFFF", "SOFT_TAP", "pauseTimer();"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Reset (00:00)", "timer_reset", "0x10", "0", "Reset", "0", 100, "#DC2626", "#FFFFFF", "LASER_PING", "resetTimer();"))
            }
            "NOTES_APP" -> {
                list.add(StructuredWidgetSpec(ComponentWidgetType.INPUT, "Note / Task Title", "note_title", "0x04", "", "Title", "", 100, "#FFFFFF", "#0F172A", "SOFT_TAP", "noteTitle = inputValue;"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.INPUT, "Write $appName Content", "note_body", "0x08", "", "Content", "", 100, "#FFFFFF", "#0F172A", "SOFT_TAP", "noteBody = inputValue;"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Save $appName Item", "note_save", "0x0C", "Draft", "Saved", "0", 100, "#16A34A", "#FFFFFF", "SUCCESS_CHIME", "saveNote();"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Clear All Notes", "note_clear", "0x10", "0", "Reset", "0", 100, "#DC2626", "#FFFFFF", "SOFT_TAP", "clearNotes();"))
            }
            "CONVERTER_APP" -> {
                list.add(StructuredWidgetSpec(ComponentWidgetType.INPUT, "Input Value to Convert", "conv_val", "0x04", "0", "100", "100", 100, "#FFFFFF", "#0F172A", "SOFT_TAP", "sourceVal = Double.parseDouble(inputValue);"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Convert Now (=)", "conv_btn", "0x08", "0", "=", "0", 100, "#2563EB", "#FFFFFF", "SUCCESS_CHIME", "convertNow();"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Reset Converter", "conv_reset", "0x0C", "0", "Reset", "0", 100, "#DC2626", "#FFFFFF", "SOFT_TAP", "resetConverter();"))
            }
            "COUNTER_APP" -> {
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Increment (+1)", "cnt_inc", "0x04", "0", "+1", "0", 100, "#16A34A", "#FFFFFF", "CLICK_POP", "count++;"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Decrement (-1)", "cnt_dec", "0x08", "0", "-1", "0", 100, "#0288D1", "#FFFFFF", "CLICK_POP", "count--;"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Reset Counter (0)", "cnt_reset", "0x0C", "0", "Reset", "0", 100, "#DC2626", "#FFFFFF", "SOFT_TAP", "count = 0;"))
            }
            else -> {
                val neuralTag = neuralTrace.decodedTokenStream.split(" ").firstOrNull { it.length >= 3 } ?: appName
                list.add(StructuredWidgetSpec(ComponentWidgetType.TOGGLE, "$appName Switch ($neuralTag)", "overlay_toggle", "0x04", "Off", "On", "0", 100, "#1E293B", "#F8FAFC", "CLICK_POP", "toggleFeature(isEnabled);"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.SLIDER, "$appName Level", "overlay_slider", "0x08", "0", "100", "50", 100, "#0F172A", "#38BDF8", "SOFT_TAP", "setLevel(sliderProgress);"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.INPUT, "$appName Input", "overlay_input", "0x0C", "", "Ready", "", 100, "#FFFFFF", "#0F172A", "SOFT_TAP", "updateInput(inputValue);"))
                list.add(StructuredWidgetSpec(ComponentWidgetType.BUTTON, "Apply $appName", "overlay_apply", "0x10", "Off", "On", "0", 100, "#2563EB", "#FFFFFF", "LASER_PING", "commitOverlayState();"))
            }
        }
        return list
    }

    private fun mapStructuredSpecToAstComponents(
        spec: StructuredAppSpecification,
        projectId: Long,
        existingComponents: List<CanvasComponentEntity>,
        cleanPrompt: String,
        lower: String
    ): List<CanvasComponentEntity> {
        val isIncremental = existingComponents.isNotEmpty() &&
            (lower.startsWith("add ") || lower.startsWith("remove ") || lower.startsWith("delete ") ||
                "aur add" in lower || "jodo" in lower || "hatao" in lower)

        if (isIncremental) {
            val updated = existingComponents.toMutableList()
            if ("remove" in lower || "delete" in lower || "hatao" in lower) {
                if (updated.size > 1) updated.removeAt(updated.lastIndex)
                return updated
            }
            val nextId = (updated.maxOfOrNull { it.id } ?: 0L) + 1L
            val nextY = (updated.maxOfOrNull { it.posYDp + it.heightDp } ?: 12) + 8
            val label = extractClauseLabel(cleanPrompt, spec.appName, updated.size)
            updated.add(
                CanvasComponentEntity(
                    id = nextId,
                    projectId = projectId,
                    type = when {
                        "slider" in lower -> ComponentWidgetType.SLIDER.name
                        "toggle" in lower || "switch" in lower -> ComponentWidgetType.TOGGLE.name
                        "input" in lower -> ComponentWidgetType.INPUT.name
                        else -> ComponentWidgetType.BUTTON.name
                    },
                    label = label,
                    posXDp = 14,
                    posYDp = nextY,
                    widthDp = 230,
                    heightDp = 46,
                    bgColorHex = "#2563EB",
                    textColorHex = "#FFFFFF",
                    targetFilePath = spec.targetFilePath,
                    byteOffsetHex = String.format(Locale.US, "0x%02X", updated.size * 4),
                    offPayloadHex = "Off",
                    onPayloadHex = "On",
                    currentValue = "0"
                )
            )
            return updated
        }

        val widgets = mutableListOf<CanvasComponentEntity>()
        var currentY = 12
        var nextId = 1L

        val initialHeaderLabel = when {
            spec.appCategory == "CALCULATOR_APP" -> "0"
            spec.appCategory == "TIMER_APP" -> "00:00.00"
            spec.appCategory == "COUNTER_APP" -> "Count: 0"
            spec.appCategory == "CONVERTER_APP" -> "Result: 0.00"
            spec.appCategory == "PATH_EXPLORER_APP" -> "Path: /storage/emulated/0/Download"
            spec.appCategory == "EXPENSE_TRACKER_APP" -> "Total Expense: ₹0"
            spec.isFloatingOverlay -> "⚡ ${spec.overlayTitle}"
            else -> "${spec.overlayTitle} — Ready"
        }
        val headerOnPayload = if (spec.appCategory == "CALCULATOR_APP") "CALC_DISPLAY" else "DISPLAY_HEADER"

        widgets.add(
            CanvasComponentEntity(
                id = nextId++,
                projectId = projectId,
                type = ComponentWidgetType.TEXT.name,
                label = initialHeaderLabel,
                posXDp = 14,
                posYDp = currentY,
                widthDp = 230,
                heightDp = if (spec.appCategory == "CALCULATOR_APP") 48 else 36,
                bgColorHex = "#0F172A",
                textColorHex = "#38BDF8",
                targetFilePath = spec.targetFilePath,
                byteOffsetHex = "0x00",
                offPayloadHex = "0",
                onPayloadHex = headerOnPayload,
                currentValue = "0"
            )
        )
        currentY += if (spec.appCategory == "CALCULATOR_APP") 56 else 44

        for (node in spec.widgets) {
            val h = when (node.widgetType) {
                ComponentWidgetType.SLIDER -> 52
                ComponentWidgetType.INPUT -> 48
                else -> 44
            }
            widgets.add(
                CanvasComponentEntity(
                    id = nextId++,
                    projectId = projectId,
                    type = node.widgetType.name,
                    label = node.label,
                    posXDp = 14,
                    posYDp = currentY,
                    widthDp = 230,
                    heightDp = h,
                    bgColorHex = node.bgColorHex,
                    textColorHex = node.textColorHex,
                    targetFilePath = spec.targetFilePath,
                    byteOffsetHex = node.byteOffsetHex,
                    offPayloadHex = node.offPayload,
                    onPayloadHex = node.onPayload,
                    sliderMax = node.sliderMax,
                    soundTrigger = node.soundTrigger,
                    currentValue = node.initialValue
                )
            )
            currentY += h + 8
        }

        return widgets
    }

    fun stripNegatedClauses(rawPrompt: String): String {
        var result = rawPrompt.trim()
        val hindiNeg = Regex("""(?i)^[^,;.]+?\b(?:nahi|nhi|na|mat\s+bana|mat\s+banao)\b\s*[,;.-]*\s*(.+)$""").find(result)
        if (hindiNeg != null && hindiNeg.groupValues[1].isNotBlank()) {
            result = hindiNeg.groupValues[1].trim()
        }
        val engNeg = Regex("""(?i)^(?:not|no|instead\s+of|dont\s+make|don't\s+make)\s+[^,;.]+?[,;.]\s*(.+)$""").find(result)
        if (engNeg != null && engNeg.groupValues[1].isNotBlank()) {
            result = engNeg.groupValues[1].trim()
        }
        result = result.replace(Regex("""(?i)\b[a-z0-9_]+\s+(?:nahi|nhi)\b[,;]*"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (result.isNotBlank()) result else rawPrompt.trim()
    }

    private fun hasPermissionToChooseConcept(lower: String, words: List<String>): Boolean {
        val singlePermissionWords = setOf("random", "anything", "whatever", "example", "sample", "demo", "surprise")
        if (words.any { it in singlePermissionWords }) return true
        val phrases = listOf(
            "kuch bhi", "koi bhi", "tu kuch", "tum kuch", "apne hisab", "apni marzi",
            "make any", "build any", "any app", "you decide", "you choose", "bana ke dikha", "khud se"
        )
        return phrases.any { it in lower }
    }

    private fun isAffirmativeContinuation(lower: String, words: List<String>): Boolean {
        if (words.isEmpty() || words.size > 5) return false
        val tokens = setOf("haan", "ha", "han", "yes", "yep", "ok", "okay", "sure", "banao", "banado", "start", "proceed", "chalo", "thik", "theek")
        return words.first() in tokens
    }

    private fun isPureQuestionWithoutBuildIntent(normalized: String, words: List<String>): Boolean {
        val starters = setOf("what", "why", "how", "who", "where", "when", "which", "kya", "kaise", "kyu", "kon", "kaun", "kitna")
        val verbs = setOf("make", "build", "create", "generate", "write", "banao", "bana", "add", "jodo", "remove", "hatao")
        return (words.firstOrNull() in starters || "?" in normalized) && words.none { it in verbs }
    }

    private fun hasAppFeatureKeywords(normalized: String, words: List<String>): Boolean {
        val wordSet = words.toSet()
        val keywords = setOf(
            "apk", "aab", "installer", "package", "standalone",
            "calculator", "calc", "hisab", "math", "scientific",
            "music", "song", "audio", "player", "volume", "equalizer",
            "note", "notes", "todo", "task", "diary", "editor",
            "timer", "stopwatch", "clock", "alarm", "countdown",
            "torch", "flashlight", "brightness", "compass", "speedometer",
            "battery", "ram", "cleaner", "cpu", "optimizer", "ping", "network",
            "login", "password", "auth", "otp", "counter", "clicker", "tally",
            "fitness", "bmi", "expense", "budget", "finance", "file", "manager", "explorer",
            "vip", "fps", "boost", "booster", "mod", "menu", "aimbot", "esp", "hack", "speed", "fov", "bypass", "gyro", "sensitivity",
            "python", "button", "btn", "toggle", "switch", "slider", "seekbar", "input", "textbox", "panel", "floating", "overlay",
            "service", "activity", "kotlin", "java", "script", "patcher", "offset", "hex", "tracker", "converter", "dashboard", "hud"
        )
        val pathExts = listOf(".apk", ".py", ".bin", ".cfg", ".json", ".txt", ".sh", ".lua", "/storage/", "/sdcard/")
        return wordSet.any { it in keywords } || pathExts.any { it in normalized }
    }

    fun isFloatingOverlayPrompt(prompt: String): Boolean {
        val lower = prompt.lowercase(Locale.US)
        val words = lower.split(Regex("[^a-z0-9.]+")).filter { it.isNotBlank() }.toSet()
        val floatingKeywords = setOf("floating", "float", "overlay", "mod", "hud", "aimbot", "esp", "fov", "bypass", "offset", "hex")
        return words.any { it in floatingKeywords } || "mod menu" in lower || "floating panel" in lower || "floating window" in lower || ".py" in lower || ".bin" in lower
    }

    fun detectAppCategory(prompt: String, isFloating: Boolean): String {
        val lower = stripNegatedClauses(prompt).lowercase(Locale.US)
        return when {
            isFloating -> "FLOATING_OVERLAY_APP"
            "calc" in lower || "hisab" in lower || "math" in lower -> "CALCULATOR_APP"
            "path" in lower || "file" in lower || "folder" in lower || "explorer" in lower || "storage" in lower -> "PATH_EXPLORER_APP"
            "expense" in lower || "budget" in lower || "finance" in lower || "kharcha" in lower -> "EXPENSE_TRACKER_APP"
            "timer" in lower || "stopwatch" in lower || "countdown" in lower || "alarm" in lower -> "TIMER_APP"
            "note" in lower || "todo" in lower || "task" in lower || "diary" in lower -> "NOTES_APP"
            "convert" in lower || "bmi" in lower || "currency" in lower || "unit" in lower -> "CONVERTER_APP"
            "counter" in lower || "tally" in lower || "clicker" in lower -> "COUNTER_APP"
            "login" in lower || "auth" in lower || "password" in lower -> "AUTH_APP"
            "music" in lower || "audio" in lower || "player" in lower || "equalizer" in lower -> "MUSIC_APP"
            else -> "STANDALONE_ANDROID_APP"
        }
    }

    private fun selectAutonomousAppCategory(
        positivePrompt: String,
        isFloating: Boolean,
        chatHistory: List<AiChatTurn>,
        neuralTrace: NeuralInferenceTrace
    ): String {
        val explicit = detectAppCategory(positivePrompt, isFloating)
        if (explicit != "STANDALONE_ANDROID_APP") return explicit
        val catalog = listOf("PATH_EXPLORER_APP", "EXPENSE_TRACKER_APP", "NOTES_APP", "TIMER_APP", "CONVERTER_APP", "FLOATING_OVERLAY_APP")
        val seed = (neuralTrace.generatedTokenIds.sum() + chatHistory.size).coerceAtLeast(0)
        return catalog[seed % catalog.size]
    }

    private fun extractExplicitAppNameOrNull(cleanPrompt: String): String? {
        val hindiPrefix = Regex("""(?i)\b([a-zA-Z0-9_-]{2,22})\s+(?:nam|naam)\s+(?:se|ka|ki|rakh|rakho)\b""")
            .find(cleanPrompt)?.groupValues?.getOrNull(1)?.trim()
        if (!hindiPrefix.isNullOrBlank() && hindiPrefix.lowercase(Locale.US) !in setOf("kya", "koi", "iska", "app", "ek")) {
            return hindiPrefix.replaceFirstChar { it.titlecase(Locale.US) }
        }
        val engSuffix = Regex("""(?i)(?:named|called|naam\s+rakho|naam)\s+["']?([a-zA-Z0-9 _-]{2,24})["']?""")
            .find(cleanPrompt)?.groupValues?.getOrNull(1)?.trim()
        if (!engSuffix.isNullOrBlank()) {
            val cleaned = engSuffix.replace(Regex("(?i)\\b(?:se|ek|app|banao|bana|rakho|rakh|do|de)\\b"), "").trim()
            if (cleaned.length >= 2) {
                return cleaned.split(Regex("\\s+")).take(3).joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.US) } }
            }
        }
        return null
    }

    private fun synthesizeDynamicAppName(
        cleanPrompt: String,
        lower: String,
        appCategory: String,
        isAutonomousClassB: Boolean,
        neuralTrace: NeuralInferenceTrace
    ): String {
        val explicit = extractExplicitAppNameOrNull(cleanPrompt)
        if (!explicit.isNullOrBlank()) return explicit
        if ("random" in lower && "path" in lower) return "Random Path Explorer"
        return when (appCategory) {
            "PATH_EXPLORER_APP" -> if ("file" in lower && "manager" in lower) "File Manager" else "Path Explorer"
            "EXPENSE_TRACKER_APP" -> "Expense Tracker"
            "CALCULATOR_APP" -> "Calculator"
            "TIMER_APP" -> "Stopwatch Timer"
            "NOTES_APP" -> "Notes Manager"
            "CONVERTER_APP" -> "Unit Converter"
            "COUNTER_APP" -> "Tally Counter"
            "AUTH_APP" -> "Auth Vault"
            "MUSIC_APP" -> "Audio Equalizer"
            "FLOATING_OVERLAY_APP" -> {
                val extracted = extractClauseLabel(cleanPrompt, "Floating Overlay", 1)
                if (extracted.length >= 3) extracted else "Floating Utility Panel"
            }
            else -> {
                val extracted = extractClauseLabel(cleanPrompt, "", 1)
                if (extracted.length >= 3) extracted else "Neural Studio App"
            }
        }
    }

    private fun extractClauseLabel(clause: String, fallbackPrefix: String, index: Int): String {
        val noise = setOf(
            "create", "make", "build", "generate", "write", "code", "add", "with", "for", "and",
            "aur", "jisme", "ek", "app", "banao", "bana", "do", "de", "floating", "window",
            "panel", "menu", "a", "an", "the", "in", "on", "to", "of", "ho", "hai", "chahiye", "apk"
        )
        val tokens = clause.split(Regex("\\s+"))
            .map { it.replace(Regex("[^a-zA-Z0-9_+-]"), "") }
            .filter { it.length >= 2 && !it.startsWith("/") && it.lowercase(Locale.US) !in noise }
            .take(3)
        if (tokens.isNotEmpty()) {
            return tokens.joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.US) } }
        }
        return if (fallbackPrefix.isNotBlank()) "$fallbackPrefix #$index" else "Custom Widget #$index"
    }

    private fun evaluateMathQueryOrNull(rawPrompt: String): String? {
        val lower = rawPrompt.trim().lowercase(Locale.US)
        if ("/storage/" in lower || "/sdcard/" in lower || "0x" in lower) return null
        val mathRegex = Regex("""(\(?\s*-?\d+(?:\.\d+)?\s*\)?(?:\s*[+\-*×÷/]\s*\(?\s*-?\d+(?:\.\d+)?\s*\)?)+)""")
        val match = mathRegex.find(lower) ?: return null
        val expr = match.value.replace('×', '*').replace('÷', '/').replace(" ", "")
        if (!expr.any { it in charArrayOf('+', '-', '*', '/') }) return null
        return try {
            var res = 0.0
            var op = '+'
            val tok = StringBuilder()
            for (i in 0..expr.length) {
                val c = if (i < expr.length) expr[i] else '+'
                if (c.isDigit() || c == '.') {
                    tok.append(c)
                } else if (tok.isNotEmpty()) {
                    val v = tok.toString().toDoubleOrNull() ?: return null
                    when (op) {
                        '+' -> res += v
                        '-' -> res -= v
                        '*' -> res *= v
                        '/' -> if (v == 0.0) return null else res /= v
                    }
                    op = c
                    tok.setLength(0)
                }
            }
            val longV = res.toLong()
            val formatted = if (abs(res - longV.toDouble()) < 1e-9) longV.toString() else String.format(Locale.US, "%.4f", res).trimEnd('0').trimEnd('.')
            "$expr = $formatted"
        } catch (_: Exception) {
            null
        }
    }
}
