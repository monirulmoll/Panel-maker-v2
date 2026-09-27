package com.example.engine;

/**
 * Defines exact binary byte-offset addresses and key-value identifiers
 * for the local configuration & model state file.
 *
 * Binary Header Layout (64 bytes, 0x00 - 0x3F):
 * [0x00..0x03] : Magic Header "FCFG" (0x46, 0x43, 0x46, 0x47)
 * [0x04]       : hw_tensor_accel (uint8: 0x01 = ON, 0x00 = OFF)
 * [0x05]       : zero_copy_dma   (uint8: 0x01 = ON, 0x00 = OFF)
 * [0x06]       : quant_int8_mode (uint8: 0x01 = ON, 0x00 = OFF)
 * [0x07]       : kernel_telemetry(uint8: 0x01 = ON, 0x00 = OFF)
 * [0x08..0x0B] : worker_thread_count (int32 big-endian, 1..16)
 * [0x0C..0x0F] : freq_governor_pct   (int32 big-endian, 25..100)
 * [0x10..0x13] : vram_ceiling_mb     (int32 big-endian, 128..2048)
 * [0x14..0x23] : execution_node_tag  (16-byte fixed ASCII padded with 0x00)
 * [0x24..0x2D] : custom_register_hex (10-byte fixed ASCII padded with 0x00)
 * [0x2E..0x3B] : reserved / mode tag (14-byte ASCII: "ENGINE_INIT" or "MANUAL_MODE")
 * [0x3C..0x3F] : CRC32 checksum of bytes [0x00..0x3B]
 */
public final class ConfigParameterSpec {

    public static final int HEADER_SIZE_BYTES = 64;
    public static final byte[] MAGIC_BYTES = new byte[]{'F', 'C', 'F', 'G'};

    // Byte offsets for toggles
    public static final int OFFSET_HW_ACCEL = 0x04;
    public static final int OFFSET_ZERO_COPY = 0x05;
    public static final int OFFSET_QUANT_INT8 = 0x06;
    public static final int OFFSET_TELEMETRY = 0x07;

    // Byte offsets for numerical sliders (4-byte Int32 each)
    public static final int OFFSET_THREAD_COUNT = 0x08;
    public static final int OFFSET_FREQ_GOVERNOR = 0x0C;
    public static final int OFFSET_VRAM_CEILING = 0x10;

    // Byte offsets for text inputs
    public static final int OFFSET_NODE_TAG = 0x14;
    public static final int LENGTH_NODE_TAG = 16;

    public static final int OFFSET_REGISTER_HEX = 0x24;
    public static final int LENGTH_REGISTER_HEX = 10;

    public static final int OFFSET_MODE_TAG = 0x2E;
    public static final int LENGTH_MODE_TAG = 14;

    public static final int OFFSET_CRC32 = 0x3C;

    // Key names for key-value configuration section
    public static final String KEY_HW_ACCEL = "hw_tensor_accel";
    public static final String KEY_ZERO_COPY = "zero_copy_dma";
    public static final String KEY_QUANT_INT8 = "quant_int8_mode";
    public static final String KEY_TELEMETRY = "kernel_telemetry";
    public static final String KEY_THREAD_COUNT = "worker_thread_count";
    public static final String KEY_FREQ_GOVERNOR = "freq_governor_pct";
    public static final String KEY_VRAM_CEILING = "vram_ceiling_mb";
    public static final String KEY_NODE_TAG = "execution_node_tag";
    public static final String KEY_REGISTER_HEX = "custom_register_hex";
    public static final String KEY_OPERATING_MODE = "operating_mode";

    public static class StateSnapshot {
        public boolean hwAccel = true;
        public boolean zeroCopyDma = false;
        public boolean quantInt8 = true;
        public boolean kernelTelemetry = false;
        public int workerThreads = 4;
        public int freqGovernorPct = 85;
        public int vramCeilingMb = 512;
        public String executionNodeTag = "NPU_CLUSTER_A0";
        public String customRegisterHex = "0x7F00A1";
        public String operatingMode = "ENGINE_INIT";
        public long crc32Value = 0L;
        public byte[] rawHeaderBytes = new byte[HEADER_SIZE_BYTES];
        public String rawTextContent = "";
        public String targetFilePath = "";

        public StateSnapshot copy() {
            StateSnapshot c = new StateSnapshot();
            c.hwAccel = this.hwAccel;
            c.zeroCopyDma = this.zeroCopyDma;
            c.quantInt8 = this.quantInt8;
            c.kernelTelemetry = this.kernelTelemetry;
            c.workerThreads = this.workerThreads;
            c.freqGovernorPct = this.freqGovernorPct;
            c.vramCeilingMb = this.vramCeilingMb;
            c.executionNodeTag = this.executionNodeTag;
            c.customRegisterHex = this.customRegisterHex;
            c.operatingMode = this.operatingMode;
            c.crc32Value = this.crc32Value;
            c.rawHeaderBytes = this.rawHeaderBytes != null ? this.rawHeaderBytes.clone() : new byte[HEADER_SIZE_BYTES];
            c.rawTextContent = this.rawTextContent;
            c.targetFilePath = this.targetFilePath;
            return c;
        }
    }

    private ConfigParameterSpec() {}
}
