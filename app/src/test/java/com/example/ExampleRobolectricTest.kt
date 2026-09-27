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
}
