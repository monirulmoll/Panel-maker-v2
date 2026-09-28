package com.example

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.CanvasComponentEntity
import com.example.engine.ConfigParameterSpec
import com.example.engine.LocalConfigStateWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context and verify default toggle size`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Studio Error", appName)

        val defaultToggle = CanvasComponentEntity(
            projectId = 1L,
            type = "TOGGLE",
            label = "Switch #1"
        )
        assertEquals(165, defaultToggle.widthDp)
        assertEquals(36, defaultToggle.heightDp)
    }

    @Test
    fun `verify custom component offset writer writes exact byte offset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val writer = LocalConfigStateWriter.getInstance()
        val latch = CountDownLatch(1)
        val targetFile = File(context.filesDir, "custom_test_state.bin")
        if (targetFile.exists()) targetFile.delete()

        val listener = object : LocalConfigStateWriter.OnStateWriteListener {
            override fun onWriteSuccess(
                parameterKey: String,
                byteOffset: Int,
                previousValue: String,
                newValue: String,
                durationMicros: Long,
                updatedSnapshot: ConfigParameterSpec.StateSnapshot
            ) {
                if (parameterKey == "CustomButton1") {
                    latch.countDown()
                }
            }

            override fun onWriteError(parameterKey: String, errorMessage: String) {
                latch.countDown()
            }
        }
        writer.addListener(listener)
        writer.writeCustomComponentOffsetAsync(
            context.filesDir,
            targetFile.absolutePath,
            "0x10",
            "0x7F",
            "CustomButton1"
        )

        for (i in 0 until 50) {
            shadowOf(Looper.getMainLooper()).idle()
            if (latch.await(50, TimeUnit.MILLISECONDS)) break
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(latch.count == 0L)
        writer.removeListener(listener)

        RandomAccessFile(targetFile, "r").use { raf ->
            raf.seek(0x10)
            val byteVal = raf.readByte().toInt() and 0xFF
            assertEquals(0x7F, byteVal)
        }
    }

    @Test
    fun `verify blueprint generation and signed apk compilation engine`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = com.example.data.StudioProjectEntity(
            id = 1L,
            name = "My Floating Utility",
            overlayTitle = "Floating Mod Panel",
            canvasWidthDp = 216,
            canvasHeightDp = 290,
            canvasBgColorHex = "#1E293B",
            defaultTargetFilePath = File(context.filesDir, "overlay_state.bin").absolutePath,
            autoFixSize = true
        )
        val components = listOf(
            CanvasComponentEntity(
                id = 1L,
                projectId = 1L,
                type = "TOGGLE",
                label = "esp hack",
                posXDp = 10,
                posYDp = 10,
                widthDp = 196,
                heightDp = 44,
                byteOffsetHex = "0x04",
                onPayloadHex = "0x01",
                offPayloadHex = "0x00",
                targetFilePath = File(context.filesDir, "overlay_state.bin").absolutePath
            ),
            CanvasComponentEntity(
                id = 2L,
                projectId = 1L,
                type = "BUTTON",
                label = "teleport hack",
                posXDp = 10,
                posYDp = 62,
                widthDp = 176,
                heightDp = 44,
                byteOffsetHex = "0x08",
                onPayloadHex = "0xFF",
                offPayloadHex = "0x00",
                targetFilePath = File(context.filesDir, "overlay_state.bin").absolutePath
            )
        )

        val blueprintFiles = com.example.blueprint.ApkCompilationEngine.generateProjectBlueprintFiles(project, components)
        assertTrue(blueprintFiles.containsKey("AndroidManifest.xml"))
        assertTrue(blueprintFiles.containsKey("build.gradle"))
        assertTrue(blueprintFiles.containsKey("src/main/java/com/floating/modmenu/MainActivity.java"))
        assertTrue(blueprintFiles.containsKey("src/main/java/com/floating/modmenu/FloatingModMenuService.java"))
        assertTrue(blueprintFiles.containsKey("src/main/java/com/floating/modmenu/BinaryOffsetPatcher.java"))
        assertTrue(blueprintFiles.containsKey("src/main/res/layout/activity_main.xml"))
        assertTrue(blueprintFiles.containsKey("src/main/res/layout/inspector_dock.xml"))
        assertTrue(blueprintFiles.containsKey("src/main/res/layout/floating_view.xml"))

        val outApk = File(context.cacheDir, "test_compiled_signed.apk")
        if (outApk.exists()) outApk.delete()

        val result = com.example.blueprint.ApkCompilationEngine.compileAndSignProjectApk(
            context,
            project,
            components,
            outApk
        )
        assertTrue(result.signedApkFile.exists())
        assertTrue("Compiled APK must be > 1 MB, was ${result.apkSizeBytes}", result.apkSizeBytes > 1_000_000L)

        val verifier = com.android.apksig.ApkVerifier.Builder(result.signedApkFile)
            .setMinCheckedPlatformVersion(24)
            .build()
        val verifyResult = verifier.verify()
        assertTrue("APK signature verification failed: ${verifyResult.errors}", verifyResult.isVerified)
        assertTrue(verifyResult.isVerifiedUsingV2Scheme || verifyResult.isVerifiedUsingV3Scheme)

        java.util.zip.ZipFile(result.signedApkFile).use { zip ->
            val arsc = zip.getEntry("resources.arsc")
            val dex = zip.getEntry("classes.dex")
            val config = zip.getEntry("assets/overlay_config.json")
            val genService = zip.getEntry("assets/generated_project/src/main/java/com/floating/modmenu/FloatingModMenuService.java")
            val genPatcher = zip.getEntry("assets/generated_project/src/main/java/com/floating/modmenu/BinaryOffsetPatcher.java")
            assertTrue("resources.arsc must exist and be STORED (0)", arsc != null && arsc.method == java.util.zip.ZipEntry.STORED)
            assertTrue("classes.dex must exist in compiled APK", dex != null)
            assertTrue("assets/overlay_config.json must be injected in signed APK", config != null)
            assertTrue("Generated FloatingModMenuService.java must be packaged in signed APK", genService != null)
            assertTrue("Generated BinaryOffsetPatcher.java must be packaged in signed APK", genPatcher != null)
        }

        // Verify XML layouts and Java blueprint views inflate cleanly
        val launcherView = com.example.blueprint.ProjectLauncherJavaView(context)
        launcherView.submitProjects(listOf(project))
        val canvasView = com.example.blueprint.EmptyCanvasWorkspaceView(context)
        canvasView.bindWorkspaceState(project, components, 1L)
        val inspectorView = com.example.blueprint.BottomPropertyInspectorView(context)
        inspectorView.bindComponent(components[0])
    }
}
