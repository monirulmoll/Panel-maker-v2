package com.example.engine;

import android.content.Context;

import java.io.File;

/**
 * Manages initialization of the Local Processing Engine and resolves
 * either user-supplied local model/configuration paths or Manual Mode bypass files.
 */
public class LocalProcessingEngine {

    public interface EngineInitCallback {
        void onEngineInitialized(File resolvedFile, String modeTag, String statusSummary);
        void onEngineFailure(String reason);
    }

    public static final String DEFAULT_ENGINE_FILENAME = "engine_runtime.conf";
    public static final String DEFAULT_MANUAL_FILENAME = "manual_override_state.conf";

    private final Context appContext;
    private final LocalConfigStateWriter stateWriter;

    public LocalProcessingEngine(Context context) {
        this.appContext = context.getApplicationContext();
        this.stateWriter = LocalConfigStateWriter.getInstance();
    }

    public String getDefaultConfigPath() {
        return new File(appContext.getFilesDir(), DEFAULT_ENGINE_FILENAME).getAbsolutePath();
    }

    public String getPresetPath(String presetName) {
        return new File(appContext.getFilesDir(), presetName).getAbsolutePath();
    }

    /**
     * Button 1 Action: Validates path, initializes binary header + key-value blocks,
     * and launches the Local Processing Engine state writer.
     */
    public void initializeEngine(String rawPathInput, EngineInitCallback callback) {
        if (rawPathInput == null || rawPathInput.trim().isEmpty()) {
            if (callback != null) {
                callback.onEngineFailure("Configuration path cannot be empty. Provide a valid path or select Manual Mode.");
            }
            return;
        }

        File targetFile = resolveWritableFile(rawPathInput.trim(), DEFAULT_ENGINE_FILENAME);
        stateWriter.initializeFileStateAsync(targetFile, "ENGINE_INIT", () -> {
            if (callback != null) {
                callback.onEngineInitialized(
                        targetFile,
                        "ENGINE_INIT",
                        "Local Processing Engine initialized [64B Header + KV Block @ " + targetFile.getName() + "]"
                );
            }
        });
    }

    /**
     * Prominent Bottom Button Action ("Use Manual Mode Only"):
     * Bypasses model/engine validation and directly binds the manual target file for immediate floating control.
     */
    public void launchManualModeBypass(String optionalPathInput, EngineInitCallback callback) {
        String candidate = (optionalPathInput == null || optionalPathInput.trim().isEmpty())
                ? new File(appContext.getFilesDir(), DEFAULT_MANUAL_FILENAME).getAbsolutePath()
                : optionalPathInput.trim();

        File targetFile = resolveWritableFile(candidate, DEFAULT_MANUAL_FILENAME);
        stateWriter.initializeFileStateAsync(targetFile, "MANUAL_MODE", () -> {
            if (callback != null) {
                callback.onEngineInitialized(
                        targetFile,
                        "MANUAL_MODE",
                        "Manual Mode active (Engine validation bypassed) -> " + targetFile.getName()
                );
            }
        });
    }

    private File resolveWritableFile(String inputPath, String fallbackName) {
        File candidate = new File(inputPath);
        if (!candidate.isAbsolute()) {
            return new File(appContext.getFilesDir(), inputPath);
        }
        File parent = candidate.getParentFile();
        if (parent != null && (parent.canWrite() || parent.mkdirs() || parent.exists())) {
            try {
                if (!candidate.exists()) {
                    candidate.createNewFile();
                }
                if (candidate.canWrite()) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Fall back to app-private sandbox if external path is restricted by Scoped Storage
            }
        }
        String safeName = candidate.getName().isEmpty() ? fallbackName : candidate.getName();
        return new File(appContext.getFilesDir(), safeName);
    }
}
