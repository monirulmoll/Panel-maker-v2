package com.example.engine;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;

/**
 * Core background file-writing engine.
 * Modifies both binary byte offsets (0x00..0x3F) via RandomAccessFile.seek()
 * and structured key=value configuration blocks inside the designated local file.
 */
public class LocalConfigStateWriter {

    public interface OnStateWriteListener {
        void onWriteSuccess(
                String parameterKey,
                int byteOffset,
                String previousValue,
                String newValue,
                long durationMicros,
                ConfigParameterSpec.StateSnapshot updatedSnapshot
        );

        void onWriteError(String parameterKey, String errorMessage);
    }

    private static volatile LocalConfigStateWriter instance;

    private final ExecutorService fileIoExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "FloatConfig-FileWriter-IO");
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<OnStateWriteListener> listeners = new CopyOnWriteArrayList<>();
    private final ConfigParameterSpec.StateSnapshot currentState = new ConfigParameterSpec.StateSnapshot();
    private volatile File activeFile;

    public static LocalConfigStateWriter getInstance() {
        if (instance == null) {
            synchronized (LocalConfigStateWriter.class) {
                if (instance == null) {
                    instance = new LocalConfigStateWriter();
                }
            }
        }
        return instance;
    }

    private LocalConfigStateWriter() {}

    public void addListener(OnStateWriteListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(OnStateWriteListener listener) {
        listeners.remove(listener);
    }

    public synchronized void bindTargetFile(File targetFile, String operatingMode) {
        this.activeFile = targetFile;
        this.currentState.targetFilePath = targetFile.getAbsolutePath();
        this.currentState.operatingMode = operatingMode;
    }

    public synchronized File getActiveFile() {
        return activeFile;
    }

    public synchronized ConfigParameterSpec.StateSnapshot getCurrentSnapshot() {
        return currentState.copy();
    }

    /**
     * Initializes or synchronizes the target local file on the background IO thread.
     */
    public void initializeFileStateAsync(File targetFile, String operatingMode, Runnable onComplete) {
        bindTargetFile(targetFile, operatingMode);
        fileIoExecutor.execute(() -> {
            long startNs = System.nanoTime();
            try {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                synchronized (LocalConfigStateWriter.this) {
                    writeFullStateToFileLocked(targetFile);
                }
                long elapsedUs = (System.nanoTime() - startNs) / 1_000L;
                ConfigParameterSpec.StateSnapshot snapshot = getCurrentSnapshot();
                notifyWriteSuccess(
                        ConfigParameterSpec.KEY_OPERATING_MODE,
                        ConfigParameterSpec.OFFSET_MODE_TAG,
                        "UNINITIALIZED",
                        operatingMode,
                        elapsedUs,
                        snapshot
                );
                if (onComplete != null) {
                    mainHandler.post(onComplete);
                }
            } catch (IOException e) {
                notifyWriteError(ConfigParameterSpec.KEY_OPERATING_MODE, e.getMessage());
            }
        });
    }

    /**
     * Background writer for boolean toggle switches (modifies 1 byte at specific offset + key-value block).
     */
    public void writeToggleStateAsync(final String key, final int byteOffset, final boolean enabled) {
        fileIoExecutor.execute(() -> {
            long startNs = System.nanoTime();
            String oldVal;
            String newVal = enabled ? "1 (0x01)" : "0 (0x00)";
            ConfigParameterSpec.StateSnapshot snapshot;

            synchronized (LocalConfigStateWriter.this) {
                if (activeFile == null) {
                    notifyWriteError(key, "No target configuration file bound.");
                    return;
                }
                oldVal = getToggleOldValueLocked(key);
                applyToggleToMemoryLocked(key, enabled);
                try {
                    patchByteOffsetAndRebuildKeyValueLocked(activeFile, byteOffset, new byte[]{(byte) (enabled ? 1 : 0)});
                } catch (IOException e) {
                    notifyWriteError(key, "IO Failed at offset 0x" + Integer.toHexString(byteOffset) + ": " + e.getMessage());
                    return;
                }
                snapshot = currentState.copy();
            }

            long elapsedUs = (System.nanoTime() - startNs) / 1_000L;
            notifyWriteSuccess(key, byteOffset, oldVal, newVal, elapsedUs, snapshot);
        });
    }

    /**
     * Background writer for numerical sliders (modifies 4-byte Int32 at specific offset + key-value block).
     */
    public void writeSliderValueAsync(final String key, final int byteOffset, final int value) {
        fileIoExecutor.execute(() -> {
            long startNs = System.nanoTime();
            String oldVal;
            String newVal = String.valueOf(value);
            ConfigParameterSpec.StateSnapshot snapshot;

            synchronized (LocalConfigStateWriter.this) {
                if (activeFile == null) {
                    notifyWriteError(key, "No target configuration file bound.");
                    return;
                }
                oldVal = getSliderOldValueLocked(key);
                applySliderToMemoryLocked(key, value);
                byte[] intBytes = ByteBuffer.allocate(4).putInt(value).array();
                try {
                    patchByteOffsetAndRebuildKeyValueLocked(activeFile, byteOffset, intBytes);
                } catch (IOException e) {
                    notifyWriteError(key, "IO Failed at offset 0x" + Integer.toHexString(byteOffset) + ": " + e.getMessage());
                    return;
                }
                snapshot = currentState.copy();
            }

            long elapsedUs = (System.nanoTime() - startNs) / 1_000L;
            notifyWriteSuccess(key, byteOffset, oldVal, newVal, elapsedUs, snapshot);
        });
    }

    /**
     * Background writer for text input fields (modifies fixed ASCII slot at specific offset + key-value block).
     */
    public void writeTextParameterAsync(final String key, final int byteOffset, final int maxSlotLength, final String rawInput) {
        fileIoExecutor.execute(() -> {
            long startNs = System.nanoTime();
            String sanitized = (rawInput == null ? "" : rawInput.trim());
            if (sanitized.isEmpty()) {
                sanitized = "DEFAULT";
            }
            if (sanitized.length() > maxSlotLength) {
                sanitized = sanitized.substring(0, maxSlotLength);
            }
            String oldVal;
            ConfigParameterSpec.StateSnapshot snapshot;

            synchronized (LocalConfigStateWriter.this) {
                if (activeFile == null) {
                    notifyWriteError(key, "No target configuration file bound.");
                    return;
                }
                if (ConfigParameterSpec.KEY_NODE_TAG.equals(key)) {
                    oldVal = currentState.executionNodeTag;
                    currentState.executionNodeTag = sanitized;
                } else {
                    oldVal = currentState.customRegisterHex;
                    currentState.customRegisterHex = sanitized;
                }

                byte[] slotBytes = new byte[maxSlotLength];
                byte[] utfBytes = sanitized.getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(utfBytes, 0, slotBytes, 0, Math.min(utfBytes.length, maxSlotLength));

                try {
                    patchByteOffsetAndRebuildKeyValueLocked(activeFile, byteOffset, slotBytes);
                } catch (IOException e) {
                    notifyWriteError(key, "IO Failed at offset 0x" + Integer.toHexString(byteOffset) + ": " + e.getMessage());
                    return;
                }
                snapshot = currentState.copy();
            }

            long elapsedUs = (System.nanoTime() - startNs) / 1_000L;
            notifyWriteSuccess(key, byteOffset, oldVal, sanitized, elapsedUs, snapshot);
        });
    }

    /**
     * Writes a user-configured component state to any target file path and byte offset
     * configured inside the Studio Error Property Inspector Dock.
     */
    public void writeCustomComponentOffsetAsync(
            final File fallbackDir,
            final String targetFilePath,
            final String byteOffsetHex,
            final String payloadValue,
            final String componentLabel
    ) {
        fileIoExecutor.execute(() -> {
            long startNs = System.nanoTime();
            int offset = parseOffsetString(byteOffsetHex);
            byte[] payloadBytes = parsePayloadBytes(payloadValue);
            File target = resolveTargetFile(fallbackDir, targetFilePath);

            ConfigParameterSpec.StateSnapshot snapshot;
            synchronized (LocalConfigStateWriter.this) {
                try {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (RandomAccessFile raf = new RandomAccessFile(target, "rw")) {
                        if (raf.length() < offset + payloadBytes.length) {
                            raf.setLength(Math.max(64, offset + payloadBytes.length));
                        }
                        raf.seek(offset);
                        raf.write(payloadBytes);
                    }
                    activeFile = target;
                    currentState.targetFilePath = target.getAbsolutePath();
                    snapshot = currentState.copy();
                } catch (IOException e) {
                    notifyWriteError(componentLabel, "Write failed @" + byteOffsetHex + ": " + e.getMessage());
                    return;
                }
            }

            long elapsedUs = (System.nanoTime() - startNs) / 1_000L;
            notifyWriteSuccess(componentLabel, offset, "PREV", payloadValue, elapsedUs, snapshot);
        });
    }

    private int parseOffsetString(String rawOffset) {
        if (rawOffset == null || rawOffset.trim().isEmpty()) return 0;
        String clean = rawOffset.trim().toLowerCase();
        try {
            if (clean.startsWith("0x")) {
                return Math.max(0, Integer.parseInt(clean.substring(2), 16));
            }
            return Math.max(0, Integer.parseInt(clean));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private byte[] parsePayloadBytes(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return new byte[]{0x01};
        }
        String clean = payload.trim();
        try {
            if (clean.startsWith("0x") || clean.startsWith("0X")) {
                int v = Integer.parseInt(clean.substring(2), 16);
                return new byte[]{(byte) (v & 0xFF)};
            }
            int v = Integer.parseInt(clean);
            if (v >= 0 && v <= 255) {
                return new byte[]{(byte) v};
            }
            return ByteBuffer.allocate(4).putInt(v).array();
        } catch (NumberFormatException e) {
            return clean.getBytes(StandardCharsets.UTF_8);
        }
    }

    private File resolveTargetFile(File fallbackDir, String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return new File(fallbackDir, "studio_overlay_target.bin");
        }
        File candidate = new File(rawPath.trim());
        if (candidate.isAbsolute()) {
            File parent = candidate.getParentFile();
            if (parent != null && (parent.canWrite() || parent.mkdirs() || parent.exists())) {
                try {
                    if (!candidate.exists()) candidate.createNewFile();
                    if (candidate.canWrite()) return candidate;
                } catch (Exception ignored) {
                }
            }
            String name = candidate.getName().isEmpty() ? "studio_overlay_target.bin" : candidate.getName();
            return new File(fallbackDir, name);
        }
        return new File(fallbackDir, rawPath.trim());
    }

    private void patchByteOffsetAndRebuildKeyValueLocked(File file, int offset, byte[] payload) throws IOException {
        if (!file.exists() || file.length() < ConfigParameterSpec.HEADER_SIZE_BYTES) {
            writeFullStateToFileLocked(file);
            return;
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            // 1. Seek directly to the target byte offset and write payload bytes
            raf.seek(offset);
            raf.write(payload);

            // 2. Read header [0x00..0x3B] to compute updated CRC32 checksum at [0x3C..0x3F]
            byte[] headerPrefix = new byte[ConfigParameterSpec.OFFSET_CRC32];
            raf.seek(0);
            raf.readFully(headerPrefix);

            CRC32 crc32 = new CRC32();
            crc32.update(headerPrefix);
            long crcVal = crc32.getValue();
            currentState.crc32Value = crcVal;

            raf.seek(ConfigParameterSpec.OFFSET_CRC32);
            raf.writeInt((int) crcVal);

            // 3. Read full 64-byte binary header into snapshot
            raf.seek(0);
            raf.readFully(currentState.rawHeaderBytes);

            // 4. Write human-readable key-value configuration tail immediately after byte 0x40
            String kvBlock = buildKeyValueBlockLocked();
            byte[] kvBytes = kvBlock.getBytes(StandardCharsets.UTF_8);
            raf.seek(ConfigParameterSpec.HEADER_SIZE_BYTES);
            raf.write(kvBytes);
            raf.setLength(ConfigParameterSpec.HEADER_SIZE_BYTES + kvBytes.length);
            currentState.rawTextContent = kvBlock;
        }
    }

    private void writeFullStateToFileLocked(File file) throws IOException {
        byte[] header = new byte[ConfigParameterSpec.HEADER_SIZE_BYTES];
        // Magic bytes 0x00..0x03
        System.arraycopy(ConfigParameterSpec.MAGIC_BYTES, 0, header, 0, 4);

        // Toggles 0x04..0x07
        header[ConfigParameterSpec.OFFSET_HW_ACCEL] = (byte) (currentState.hwAccel ? 1 : 0);
        header[ConfigParameterSpec.OFFSET_ZERO_COPY] = (byte) (currentState.zeroCopyDma ? 1 : 0);
        header[ConfigParameterSpec.OFFSET_QUANT_INT8] = (byte) (currentState.quantInt8 ? 1 : 0);
        header[ConfigParameterSpec.OFFSET_TELEMETRY] = (byte) (currentState.kernelTelemetry ? 1 : 0);

        // Sliders 0x08..0x13
        ByteBuffer.wrap(header, ConfigParameterSpec.OFFSET_THREAD_COUNT, 4).putInt(currentState.workerThreads);
        ByteBuffer.wrap(header, ConfigParameterSpec.OFFSET_FREQ_GOVERNOR, 4).putInt(currentState.freqGovernorPct);
        ByteBuffer.wrap(header, ConfigParameterSpec.OFFSET_VRAM_CEILING, 4).putInt(currentState.vramCeilingMb);

        // Text fields 0x14..0x2D
        writeFixedAsciiToBuffer(header, ConfigParameterSpec.OFFSET_NODE_TAG, ConfigParameterSpec.LENGTH_NODE_TAG, currentState.executionNodeTag);
        writeFixedAsciiToBuffer(header, ConfigParameterSpec.OFFSET_REGISTER_HEX, ConfigParameterSpec.LENGTH_REGISTER_HEX, currentState.customRegisterHex);
        writeFixedAsciiToBuffer(header, ConfigParameterSpec.OFFSET_MODE_TAG, ConfigParameterSpec.LENGTH_MODE_TAG, currentState.operatingMode);

        // CRC32 at 0x3C..0x3F
        CRC32 crc32 = new CRC32();
        crc32.update(header, 0, ConfigParameterSpec.OFFSET_CRC32);
        long crcVal = crc32.getValue();
        currentState.crc32Value = crcVal;
        ByteBuffer.wrap(header, ConfigParameterSpec.OFFSET_CRC32, 4).putInt((int) crcVal);

        System.arraycopy(header, 0, currentState.rawHeaderBytes, 0, ConfigParameterSpec.HEADER_SIZE_BYTES);
        String kvBlock = buildKeyValueBlockLocked();
        currentState.rawTextContent = kvBlock;

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(0);
            raf.write(header);
            byte[] kvBytes = kvBlock.getBytes(StandardCharsets.UTF_8);
            raf.write(kvBytes);
            raf.setLength(ConfigParameterSpec.HEADER_SIZE_BYTES + kvBytes.length);
        }
    }

    private void writeFixedAsciiToBuffer(byte[] buffer, int offset, int maxLen, String text) {
        byte[] bytes = (text == null ? "" : text).getBytes(StandardCharsets.US_ASCII);
        int copyLen = Math.min(bytes.length, maxLen);
        System.arraycopy(bytes, 0, buffer, offset, copyLen);
    }

    private String buildKeyValueBlockLocked() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n# --- FLOATCONFIG LOCAL RUNTIME PARAMETERS ---\n");
        sb.append("# Binary Header: 64 bytes [0x00..0x3F] | CRC32: 0x")
                .append(String.format("%08X", currentState.crc32Value)).append("\n");
        sb.append(ConfigParameterSpec.KEY_OPERATING_MODE).append("=").append(currentState.operatingMode).append("\n");
        sb.append(ConfigParameterSpec.KEY_HW_ACCEL).append("=").append(currentState.hwAccel ? "1" : "0")
                .append(" # @0x04\n");
        sb.append(ConfigParameterSpec.KEY_ZERO_COPY).append("=").append(currentState.zeroCopyDma ? "1" : "0")
                .append(" # @0x05\n");
        sb.append(ConfigParameterSpec.KEY_QUANT_INT8).append("=").append(currentState.quantInt8 ? "1" : "0")
                .append(" # @0x06\n");
        sb.append(ConfigParameterSpec.KEY_TELEMETRY).append("=").append(currentState.kernelTelemetry ? "1" : "0")
                .append(" # @0x07\n");
        sb.append(ConfigParameterSpec.KEY_THREAD_COUNT).append("=").append(currentState.workerThreads)
                .append(" # @0x08\n");
        sb.append(ConfigParameterSpec.KEY_FREQ_GOVERNOR).append("=").append(currentState.freqGovernorPct)
                .append(" # @0x0C\n");
        sb.append(ConfigParameterSpec.KEY_VRAM_CEILING).append("=").append(currentState.vramCeilingMb)
                .append(" # @0x10\n");
        sb.append(ConfigParameterSpec.KEY_NODE_TAG).append("=").append(currentState.executionNodeTag)
                .append(" # @0x14\n");
        sb.append(ConfigParameterSpec.KEY_REGISTER_HEX).append("=").append(currentState.customRegisterHex)
                .append(" # @0x24\n");
        return sb.toString();
    }

    private String getToggleOldValueLocked(String key) {
        switch (key) {
            case ConfigParameterSpec.KEY_HW_ACCEL:
                return currentState.hwAccel ? "1 (0x01)" : "0 (0x00)";
            case ConfigParameterSpec.KEY_ZERO_COPY:
                return currentState.zeroCopyDma ? "1 (0x01)" : "0 (0x00)";
            case ConfigParameterSpec.KEY_QUANT_INT8:
                return currentState.quantInt8 ? "1 (0x01)" : "0 (0x00)";
            case ConfigParameterSpec.KEY_TELEMETRY:
                return currentState.kernelTelemetry ? "1 (0x01)" : "0 (0x00)";
            default:
                return "0";
        }
    }

    private void applyToggleToMemoryLocked(String key, boolean enabled) {
        switch (key) {
            case ConfigParameterSpec.KEY_HW_ACCEL:
                currentState.hwAccel = enabled;
                break;
            case ConfigParameterSpec.KEY_ZERO_COPY:
                currentState.zeroCopyDma = enabled;
                break;
            case ConfigParameterSpec.KEY_QUANT_INT8:
                currentState.quantInt8 = enabled;
                break;
            case ConfigParameterSpec.KEY_TELEMETRY:
                currentState.kernelTelemetry = enabled;
                break;
        }
    }

    private String getSliderOldValueLocked(String key) {
        switch (key) {
            case ConfigParameterSpec.KEY_THREAD_COUNT:
                return String.valueOf(currentState.workerThreads);
            case ConfigParameterSpec.KEY_FREQ_GOVERNOR:
                return String.valueOf(currentState.freqGovernorPct);
            case ConfigParameterSpec.KEY_VRAM_CEILING:
                return String.valueOf(currentState.vramCeilingMb);
            default:
                return "0";
        }
    }

    private void applySliderToMemoryLocked(String key, int value) {
        switch (key) {
            case ConfigParameterSpec.KEY_THREAD_COUNT:
                currentState.workerThreads = Math.max(1, Math.min(16, value));
                break;
            case ConfigParameterSpec.KEY_FREQ_GOVERNOR:
                currentState.freqGovernorPct = Math.max(25, Math.min(100, value));
                break;
            case ConfigParameterSpec.KEY_VRAM_CEILING:
                currentState.vramCeilingMb = Math.max(64, Math.min(2048, value));
                break;
        }
    }

    private void notifyWriteSuccess(
            String key,
            int offset,
            String oldVal,
            String newVal,
            long durationMicros,
            ConfigParameterSpec.StateSnapshot snapshot
    ) {
        mainHandler.post(() -> {
            for (OnStateWriteListener listener : listeners) {
                listener.onWriteSuccess(key, offset, oldVal, newVal, durationMicros, snapshot);
            }
        });
    }

    private void notifyWriteError(String key, String message) {
        mainHandler.post(() -> {
            for (OnStateWriteListener listener : listeners) {
                listener.onWriteError(key, message);
            }
        });
    }
}
