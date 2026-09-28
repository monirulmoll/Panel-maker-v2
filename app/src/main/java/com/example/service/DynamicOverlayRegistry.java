package com.example.service;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the user-designed floating overlay specification in memory so that
 * FloatingDashboardService can dynamically render the exact user-created components
 * inside the system-wide WindowManager overlay without any hardcoded UI items.
 */
public class DynamicOverlayRegistry {

    public static class OverlayItemSpec {
        public long id;
        public String type;
        public String label;
        public int posXDp;
        public int posYDp;
        public int widthDp;
        public int heightDp;
        public String bgColorHex;
        public String textColorHex;
        public String customImagePath;
        public String soundTrigger;
        public String customSoundPath;
        public String offSoundTrigger;
        public String offCustomSoundPath;
        public String targetFilePath;
        public String byteOffsetHex;
        public String onPayloadHex;
        public String offPayloadHex;
        public int sliderMax;
        public String currentValue;
        public String linkUrl;
    }

    private static volatile String activeProjectName = "";
    private static volatile String activePackageName = "com.aistudio.floatconfig.app001";
    private static volatile String activeOverlayTitle = "";
    private static volatile String activeFloatingLogoPath = "";
    private static volatile String activeAppLogoPath = "";
    private static volatile int activeCanvasWidthDp = 310;
    private static volatile int activeCanvasHeightDp = 380;
    private static volatile String activeCanvasBgHex = "#FFFFFF";
    private static volatile boolean activeAutoFixSize = false;
    private static volatile boolean bundledStandaloneLoaded = false;
    private static final List<OverlayItemSpec> activeItems = Collections.synchronizedList(new ArrayList<>());

    public static synchronized void updateActiveOverlay(
            String title,
            int widthDp,
            int heightDp,
            String bgHex,
            List<OverlayItemSpec> items
    ) {
        updateActiveOverlay(title, widthDp, heightDp, bgHex, false, items);
    }

    public static synchronized void updateActiveOverlay(
            String title,
            int widthDp,
            int heightDp,
            String bgHex,
            boolean autoFixSize,
            List<OverlayItemSpec> items
    ) {
        updateActiveOverlay(title, activeFloatingLogoPath, widthDp, heightDp, bgHex, autoFixSize, items);
    }

    public static synchronized void updateActiveOverlay(
            String title,
            String floatingLogoPath,
            int widthDp,
            int heightDp,
            String bgHex,
            boolean autoFixSize,
            List<OverlayItemSpec> items
    ) {
        activeOverlayTitle = title != null ? title : "";
        activeFloatingLogoPath = floatingLogoPath != null ? floatingLogoPath : "";
        activeCanvasWidthDp = Math.max(180, widthDp);
        activeCanvasHeightDp = Math.max(160, heightDp);
        activeCanvasBgHex = bgHex != null && !bgHex.trim().isEmpty() ? bgHex : "#FFFFFF";
        activeAutoFixSize = autoFixSize;
        if (items != null) {
            for (OverlayItemSpec incoming : items) {
                for (OverlayItemSpec existing : activeItems) {
                    if (existing.id == incoming.id) {
                        existing.type = incoming.type;
                        existing.label = incoming.label;
                        existing.posXDp = incoming.posXDp;
                        existing.posYDp = incoming.posYDp;
                        existing.widthDp = incoming.widthDp;
                        existing.heightDp = incoming.heightDp;
                        existing.bgColorHex = incoming.bgColorHex;
                        existing.textColorHex = incoming.textColorHex;
                        existing.customImagePath = incoming.customImagePath;
                        existing.soundTrigger = incoming.soundTrigger;
                        existing.customSoundPath = incoming.customSoundPath;
                        existing.offSoundTrigger = incoming.offSoundTrigger;
                        existing.offCustomSoundPath = incoming.offCustomSoundPath;
                        existing.targetFilePath = incoming.targetFilePath;
                        existing.byteOffsetHex = incoming.byteOffsetHex;
                        existing.onPayloadHex = incoming.onPayloadHex;
                        existing.offPayloadHex = incoming.offPayloadHex;
                        existing.sliderMax = incoming.sliderMax;
                        existing.currentValue = incoming.currentValue;
                        existing.linkUrl = incoming.linkUrl;
                    }
                }
            }
        }
        activeItems.clear();
        if (items != null) {
            activeItems.addAll(items);
        }
    }

    public static synchronized OverlayItemSpec getSpecById(long id, OverlayItemSpec fallback) {
        for (OverlayItemSpec item : activeItems) {
            if (item.id == id) {
                return item;
            }
        }
        return fallback;
    }

    public static synchronized String getActiveProjectName() {
        return activeProjectName;
    }

    public static synchronized String getActivePackageName() {
        return activePackageName;
    }

    public static synchronized boolean isActiveAutoFixSize() {
        return activeAutoFixSize;
    }

    public static synchronized String getActiveOverlayTitle() {
        return activeOverlayTitle;
    }

    public static synchronized String getActiveFloatingLogoPath() {
        return activeFloatingLogoPath;
    }

    public static synchronized String getActiveAppLogoPath() {
        return activeAppLogoPath;
    }

    public static synchronized int getActiveCanvasWidthDp() {
        return activeCanvasWidthDp;
    }

    public static synchronized int getActiveCanvasHeightDp() {
        return activeCanvasHeightDp;
    }

    public static synchronized String getActiveCanvasBgHex() {
        return activeCanvasBgHex;
    }

    public static synchronized List<OverlayItemSpec> getActiveItems() {
        return new ArrayList<>(activeItems);
    }

    /**
     * Returns true if this APK is a compiled standalone app generated by Studio Error's Build engine
     * (indicated by the presence of assets/overlay_config.json inside the APK).
     */
    public static synchronized boolean isBundledStandaloneApk(Context context) {
        if (context == null) return false;
        try (InputStream is = context.getAssets().open("overlay_config.json")) {
            return is != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Loads the compiled APK's bundled assets/overlay_config.json into the active registry
     * when no in-memory items have been pushed yet.
     */
    public static synchronized boolean loadFromBundledAssetsIfEmpty(Context context) {
        if ((bundledStandaloneLoaded || !activeItems.isEmpty()) || context == null) {
            return bundledStandaloneLoaded || !activeItems.isEmpty();
        }
        try (InputStream is = context.getAssets().open("overlay_config.json")) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            String jsonStr = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(jsonStr);
            activeProjectName = root.optString("projectName", "");
            activePackageName = root.optString("packageName", context.getPackageName());
            String title = root.optString("overlayTitle", "");
            activeAppLogoPath = extractBundledAssetIfPresent(context, root.optString("appLogoAsset", ""), "app_logo.png");
            String floatingLogo = extractBundledAssetIfPresent(context, root.optString("floatingLogoAsset", ""), "floating_logo.png");
            int widthDp = root.optInt("canvasWidthDp", 216);
            int heightDp = root.optInt("canvasHeightDp", 290);
            String bgHex = root.optString("canvasBgColorHex", "#FFFFFF");
            boolean autoFix = root.optBoolean("autoFixSize", false);

            JSONArray arr = root.optJSONArray("components");
            List<OverlayItemSpec> parsed = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject c = arr.getJSONObject(i);
                    OverlayItemSpec spec = new OverlayItemSpec();
                    spec.id = c.optLong("id", i + 1L);
                    spec.type = c.optString("type", "TOGGLE");
                    spec.label = c.optString("label", "Widget #" + (i + 1));
                    spec.posXDp = c.optInt("x", 10);
                    spec.posYDp = c.optInt("y", 10 + (i * 48));
                    spec.widthDp = c.optInt("width", 180);
                    spec.heightDp = c.optInt("height", 44);
                    spec.bgColorHex = c.optString("bgHex", "#FFFFFF");
                    spec.textColorHex = c.optString("textHex", "#0F172A");
                    spec.soundTrigger = c.optString("onSound", "NONE");
                    spec.offSoundTrigger = c.optString("offSound", "NONE");
                    spec.customImagePath = extractBundledAssetIfPresent(context, c.optString("customImageAsset", ""), "img_" + spec.id + ".jpg");
                    spec.customSoundPath = extractBundledAssetIfPresent(context, c.optString("onSoundAsset", ""), "on_snd_" + spec.id + ".mp3");
                    spec.offCustomSoundPath = extractBundledAssetIfPresent(context, c.optString("offSoundAsset", ""), "off_snd_" + spec.id + ".mp3");
                    spec.targetFilePath = c.optString("targetFile", "");
                    spec.byteOffsetHex = c.optString("offsetHex", "0x04");
                    spec.onPayloadHex = c.optString("onHex", "0x01");
                    spec.offPayloadHex = c.optString("offHex", "0x00");
                    spec.sliderMax = c.optInt("sliderMax", 100);
                    spec.currentValue = c.optString("currentValue", "0");
                    spec.linkUrl = c.optString("linkUrl", "");
                    parsed.add(spec);
                }
            }
            updateActiveOverlay(title, floatingLogo, widthDp, heightDp, bgHex, autoFix, parsed);
            bundledStandaloneLoaded = true;
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String extractBundledAssetIfPresent(Context context, String assetRelPath, String localFileName) {
        if (assetRelPath == null || assetRelPath.trim().isEmpty()) return "";
        try (InputStream is = context.getAssets().open(assetRelPath.trim())) {
            java.io.File dir = new java.io.File(context.getFilesDir(), "bundled_assets");
            if (!dir.exists()) dir.mkdirs();
            java.io.File outFile = new java.io.File(dir, localFileName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                byte[] buf = new byte[4096];
                int r;
                while ((r = is.read(buf)) != -1) {
                    fos.write(buf, 0, r);
                }
            }
            return outFile.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }
}
