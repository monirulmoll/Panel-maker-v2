package com.example.engine

import com.example.data.CanvasComponentEntity
import com.example.data.ComponentWidgetType

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

data class GeneratedFileArtifact(
    val name: String,
    val relativePath: String,
    val fullPath: String,
    val role: String
)

enum class AiRequestClassification {
    CLASS_A_SPECIFIC_APP,
    CLASS_B_AUTONOMOUS_CHOICE,
    CLASS_C_NEEDS_CLARIFICATION,
    CONVERSATIONAL_CHAT
}

data class AiChatTurn(
    val id: Long = System.currentTimeMillis(),
    val userPrompt: String,
    val aiResponseText: String,
    val steps: List<AiBuildStepStatus> = emptyList(),
    val isAppReady: Boolean = false,
    val isConversationalReply: Boolean = false,
    val generatedCodePreview: String = "",
    val generatedScratchFiles: Map<String, String> = emptyMap(),
    val appName: String = "",
    val packageName: String = "",
    val apkFileName: String = "",
    val apkFilePath: String = "",
    val publicDownloadApkPath: String = "",
    val projectRootPath: String = "",
    val targetDataFileName: String = "",
    val targetDataFilePath: String = "",
    val isFloatingOverlayApp: Boolean = false,
    val appCategory: String = "STANDALONE_ANDROID_APP",
    val fileArtifacts: List<GeneratedFileArtifact> = emptyList(),
    val requestClass: AiRequestClassification = AiRequestClassification.CLASS_A_SPECIFIC_APP,
    val decisionAnnouncement: String = "",
    val appPurpose: String = "",
    val buildPlanSummary: String = "",
    val expectedBehavior: String = ""
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
    val conversationalReply: String = "",
    val requestClass: AiRequestClassification = AiRequestClassification.CONVERSATIONAL_CHAT,
    val selectedConceptName: String = "",
    val decisionAnnouncement: String = ""
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

data class NeuralInferenceTrace(
    val usedNativeJni: Boolean,
    val ggufVersion: Int,
    val tensorCount: Long,
    val kvCount: Long,
    val vocabSize: Int,
    val inputTokenIds: List<Int>,
    val generatedTokenIds: List<Int>,
    val decodedTokenStream: String,
    val topLogitConfidence: Float,
    val embeddingDimension: Int,
    val attentionHeads: Int,
    val layersEvaluated: Int
)

data class StructuredWidgetSpec(
    val widgetType: ComponentWidgetType,
    val label: String,
    val fieldSlug: String,
    val byteOffsetHex: String,
    val offPayload: String,
    val onPayload: String,
    val initialValue: String,
    val sliderMax: Int,
    val bgColorHex: String,
    val textColorHex: String,
    val soundTrigger: String,
    val customLogicExpression: String
)

data class StructuredAppSpecification(
    val appName: String,
    val packageName: String,
    val overlayTitle: String,
    val targetFilePath: String,
    val isFloatingOverlay: Boolean,
    val appCategory: String,
    val requestClass: AiRequestClassification,
    val decisionAnnouncement: String,
    val appPurpose: String,
    val expectedBehavior: String,
    val widgets: List<StructuredWidgetSpec>,
    val inferenceTrace: NeuralInferenceTrace
)

data class GeneratedBlueprintSpec(
    val suggestedAppName: String,
    val suggestedPackageName: String,
    val suggestedOverlayTitle: String,
    val suggestedTargetFilePath: String,
    val components: List<CanvasComponentEntity>,
    val kotlinJavaSummary: String,
    val generatedScratchFiles: Map<String, String> = emptyMap(),
    val compilerDiagnostics: List<String> = emptyList(),
    val autoPatchedFixes: List<String> = emptyList(),
    val finalErrorCount: Int = 0,
    val isFloatingOverlayApp: Boolean = false,
    val appCategory: String = "STANDALONE_ANDROID_APP",
    val apkFileName: String = "",
    val apkOutputPath: String = "",
    val publicDownloadApkPath: String = "",
    val projectRootPath: String = "",
    val structuredBuildOutput: String = "",
    val fileArtifacts: List<GeneratedFileArtifact> = emptyList(),
    val requestClass: AiRequestClassification = AiRequestClassification.CLASS_A_SPECIFIC_APP,
    val decisionAnnouncement: String = "",
    val appPurpose: String = "",
    val buildPlanSummary: String = "",
    val expectedBehavior: String = ""
)
