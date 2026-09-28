package com.example

import com.example.engine.LocalConfigStateWriter
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
