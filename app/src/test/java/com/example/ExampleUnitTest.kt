package com.example

import com.example.engine.GgufBlueprintEngine
import com.example.engine.GgufModelState
import com.example.engine.LocalConfigStateWriter
import com.example.engine.StudioGenerationMode
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun ggufBlueprintEngine_generatesWidgetsAndTargetPathFromPromptWithSampleFallback() {
        val sampleState = GgufModelState(
            mode = StudioGenerationMode.AI_GGUF_MODE,
            isUsingSampleFallback = true
        )
        val spec = GgufBlueprintEngine.generateBlueprintFromPrompt(
            prompt = "Create VIP Mod Menu with FPS toggle and Speed slider for /storage/emulated/0/PREMIUM VIDEOS/py.py",
            projectId = 1L,
            defaultTargetFilePath = "/storage/emulated/0/default.py",
            modelState = sampleState
        )
        assertEquals("/storage/emulated/0/PREMIUM VIDEOS/py.py", spec.suggestedTargetFilePath)
        assertTrue(spec.components.isNotEmpty())
        assertTrue(spec.kotlinJavaSummary.contains("Sample GGUF Fallback"))
    }

    @Test
    fun ggufBlueprintEngine_repliesConversationallyToHiInsteadOfBuildingApp() {
        val hiEval = GgufBlueprintEngine.evaluateUserPrompt("hi", existingProjectName = null)
        assertFalse("Expected 'hi' not to trigger app build", hiEval.shouldBuildOrUpdateApp)
        assertTrue(
            "Expected reply to ask konsa/kaisa app banaye",
            hiEval.conversationalReply.lowercase().contains("konsa") ||
                hiEval.conversationalReply.lowercase().contains("kaisa")
        )

        val vagueEval = GgufBlueprintEngine.evaluateUserPrompt("app banao", existingProjectName = null)
        assertFalse("Expected vague 'app banao' to ask which app to build", vagueEval.shouldBuildOrUpdateApp)

        val calcEval = GgufBlueprintEngine.evaluateUserPrompt("Ek Calculator app banao", existingProjectName = null)
        assertTrue("Expected specific app request to trigger app build", calcEval.shouldBuildOrUpdateApp)
    }

    @Test
    fun ggufValidation_rejectsWrongFileAndAcceptsValidGgufFile() {
        val tempRoot = Files.createTempDirectory("gguf_validation_test").toFile()
        val wrongFile = File(tempRoot, "not_a_model.txt").apply {
            writeText("hello world", Charsets.UTF_8)
        }
        val invalidState = GgufBlueprintEngine.validateGgufFilePath(wrongFile.absolutePath)
        assertFalse("Expected non-.gguf file to be rejected", invalidState.isValidGgufLoaded)
        assertNotNull("Expected error message for non-.gguf file", invalidState.importErrorMessage)

        val validGgufFile = File(tempRoot, "my_model_q4_k_m.gguf").apply {
            writeBytes("GGUF\u0003\u0000\u0000\u0000_model_weights".toByteArray(Charsets.UTF_8))
        }
        val validState = GgufBlueprintEngine.validateGgufFilePath(validGgufFile.absolutePath)
        assertTrue("Expected valid .gguf file to be accepted", validState.isValidGgufLoaded)
        assertNull("Expected no error message for valid .gguf file", validState.importErrorMessage)

        wrongFile.delete()
        validGgufFile.delete()
        tempRoot.delete()
    }

    @Test
    fun toggleWidget_modifiesPythonFile_fromOffToOnAndBackToOff() {
        val tempRoot = Files.createTempDirectory("studio_py_test").toFile()
        val premiumVideosDir = File(tempRoot, "PREMIUM VIDEOS").apply { mkdirs() }
        val pyFile = File(premiumVideosDir, "py.py")
        pyFile.writeText("Off", Charsets.UTF_8)

        val writer = LocalConfigStateWriter.getInstance()

        // 1. Toggle ON: Original = "Off", Change = "On" -> py.py must change from "Off" to "On"
        val onSuccess = writer.applyWidgetPatchSync(
            tempRoot,
            "widget_1",
            "TOGGLE",
            pyFile.absolutePath,
            "0x04",
            "Off",
            "On",
            "On",
            true,
            "Switch 1"
        )
        assertTrue("Expected applyWidgetPatchSync ON to succeed", onSuccess)
        assertEquals("On", pyFile.readText(Charsets.UTF_8).trim())

        // 2. Toggle OFF: py.py must revert from "On" back to "Off"
        val offSuccess = writer.applyWidgetPatchSync(
            tempRoot,
            "widget_1",
            "TOGGLE",
            pyFile.absolutePath,
            "0x04",
            "Off",
            "On",
            "Off",
            false,
            "Switch 1"
        )
        assertTrue("Expected applyWidgetPatchSync OFF to succeed", offSuccess)
        assertEquals("Off", pyFile.readText(Charsets.UTF_8).trim())

        pyFile.delete()
        premiumVideosDir.delete()
        tempRoot.delete()
    }
}
