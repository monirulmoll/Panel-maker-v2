package com.example.service;

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
    }

    private static volatile String activeOverlayTitle = "Custom Overlay";
    private static volatile int activeCanvasWidthDp = 310;
    private static volatile int activeCanvasHeightDp = 380;
    private static volatile String activeCanvasBgHex = "#1E293B";
    private static volatile boolean activeAutoFixSize = false;
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
        activeOverlayTitle = title != null ? title : "Custom Overlay";
        activeCanvasWidthDp = Math.max(180, widthDp);
        activeCanvasHeightDp = Math.max(160, heightDp);
        activeCanvasBgHex = bgHex != null ? bgHex : "#1E293B";
        activeAutoFixSize = autoFixSize;
        activeItems.clear();
        if (items != null) {
            activeItems.addAll(items);
        }
    }

    public static synchronized boolean isActiveAutoFixSize() {
        return activeAutoFixSize;
    }

    public static synchronized String getActiveOverlayTitle() {
        return activeOverlayTitle;
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
}
