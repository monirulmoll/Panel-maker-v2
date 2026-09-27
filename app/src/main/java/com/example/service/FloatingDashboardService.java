package com.example.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.MainActivity;
import com.example.R;
import com.example.engine.LocalConfigStateWriter;
import com.example.engine.SoundTriggerPlayer;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * System-wide Floating Control Dashboard Service.
 * Dynamically builds the floating overlay window from the user's Studio Error canvas
 * components (with zero hardcoded controls) and supports ACTION_MOVE touch dragging.
 */
public class FloatingDashboardService extends Service {

    public static final String ACTION_START_OVERLAY = "com.example.service.ACTION_START_OVERLAY";
    public static final String ACTION_STOP_OVERLAY = "com.example.service.ACTION_STOP_OVERLAY";
    private static final String CHANNEL_ID = "studio_error_overlay_channel";
    private static final int NOTIFICATION_ID = 4102;

    private static volatile boolean running = false;

    private WindowManager windowManager;
    private View floatingRootView;
    private WindowManager.LayoutParams overlayLayoutParams;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_OVERLAY.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification());

        if (Settings.canDrawOverlays(this)) {
            removeSystemOverlayWindow();
            showDynamicSystemOverlayWindow();
            running = true;
        } else {
            running = false;
            stopSelf();
        }
        return START_STICKY;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showDynamicSystemOverlayWindow() {
        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        overlayLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        overlayLayoutParams.gravity = Gravity.TOP | Gravity.START;
        overlayLayoutParams.x = 32;
        overlayLayoutParams.y = 160;

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(parseSafeColor(DynamicOverlayRegistry.getActiveCanvasBgHex(), Color.parseColor("#1E293B")));
        panelBg.setCornerRadius(dpToPx(16));
        panelBg.setStroke(dpToPx(2), Color.parseColor("#3B82F6"));
        container.setBackground(panelBg);

        // Draggable Header Bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dpToPx(12), dpToPx(8), dpToPx(8), dpToPx(8));
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColor(Color.parseColor("#2563EB"));
        float r = dpToPx(14);
        headerBg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        header.setBackground(headerBg);

        List<DynamicOverlayRegistry.OverlayItemSpec> specs = DynamicOverlayRegistry.getActiveItems();

        TextView titleTv = new TextView(this);
        titleTv.setText(String.format(
                Locale.US,
                "%s (%d items)",
                DynamicOverlayRegistry.getActiveOverlayTitle(),
                specs.size()
        ));
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleTv, titleLp);

        // Close button
        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        closeBtn.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
        closeBtn.setOnClickListener(v -> stopSelf());
        header.addView(closeBtn);

        // Free / Auto-Fixed Scrollable Canvas Frame matching user's Studio Error workspace
        boolean isAutoFix = DynamicOverlayRegistry.isActiveAutoFixSize();
        FrameLayout canvasFrame = new FrameLayout(this);
        final int[] currentCanvasW = new int[]{dpToPx(DynamicOverlayRegistry.getActiveCanvasWidthDp())};
        final int[] currentCanvasH = new int[]{dpToPx(DynamicOverlayRegistry.getActiveCanvasHeightDp())};
        LinearLayout.LayoutParams canvasLp = new LinearLayout.LayoutParams(currentCanvasW[0], currentCanvasH[0]);

        // Custom ScrollView:
        // 1. Scroll is 0 (disabled) as long as all widgets fit inside the visible window.
        // 2. Scroll turns ON automatically as soon as any widget goes outside the visual window (top or bottom).
        // 3. OVER_SCROLL_NEVER eliminates rubber-band shaking/jittering ("kaanp").
        ScrollView scrollView = new ScrollView(this) {
            private boolean isOverflowingVisualWindow() {
                if (getChildCount() == 0) return false;
                View child = getChildAt(0);
                return child.getHeight() > getHeight();
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (!isOverflowingVisualWindow()) {
                    if (getScrollY() != 0) scrollTo(0, 0);
                    return false;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (!isOverflowingVisualWindow()) {
                    if (getScrollY() != 0) scrollTo(0, 0);
                    return false;
                }
                return super.onTouchEvent(ev);
            }
        };
        scrollView.setFillViewport(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(true);
        FrameLayout.LayoutParams scrollLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );

        if (isAutoFix) {
            LinearLayout autoStack = new LinearLayout(this);
            autoStack.setOrientation(LinearLayout.VERTICAL);
            autoStack.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            for (int i = 0; i < specs.size(); i++) {
                DynamicOverlayRegistry.OverlayItemSpec spec = specs.get(i);
                View childView = buildDynamicComponentView(spec);
                LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(Math.max(36, spec.heightDp))
                );
                if (i < specs.size() - 1) {
                    itemLp.bottomMargin = dpToPx(6);
                }
                autoStack.addView(childView, itemLp);
            }
            scrollView.addView(autoStack, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            ));
        } else {
            FrameLayout freeCanvas = new FrameLayout(this);
            int minTopDp = 0;
            for (DynamicOverlayRegistry.OverlayItemSpec spec : specs) {
                minTopDp = Math.min(minTopDp, spec.posYDp);
            }
            int topOverflowShiftDp = minTopDp < 0 ? (-minTopDp + 8) : 0;
            int maxBottomDp = 0;
            for (DynamicOverlayRegistry.OverlayItemSpec spec : specs) {
                View childView = buildDynamicComponentView(spec);
                int wDp = Math.max(36, spec.widthDp);
                int hDp = Math.max(32, spec.heightDp);
                int xDp = Math.max(0, spec.posXDp);
                int yDp = spec.posYDp + topOverflowShiftDp;
                maxBottomDp = Math.max(maxBottomDp, yDp + hDp);
                FrameLayout.LayoutParams itemLp = new FrameLayout.LayoutParams(
                        dpToPx(wDp),
                        dpToPx(hDp)
                );
                itemLp.leftMargin = dpToPx(xDp);
                itemLp.topMargin = dpToPx(yDp);
                freeCanvas.addView(childView, itemLp);
            }
            if (minTopDp < 0 || dpToPx(maxBottomDp) > currentCanvasH[0]) {
                freeCanvas.setPadding(0, 0, 0, dpToPx(8));
            }
            scrollView.addView(freeCanvas, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        canvasFrame.addView(scrollView, scrollLp);

        // Image-Crop Style Endpoint Resize Handle at Bottom-Right Corner (Hidden & disabled when Auto Fix Size is ON)
        if (!isAutoFix) {
            TextView resizeHandle = new TextView(this);
            resizeHandle.setText("↘");
            resizeHandle.setTextColor(Color.WHITE);
            resizeHandle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            resizeHandle.setTypeface(Typeface.DEFAULT_BOLD);
            resizeHandle.setGravity(Gravity.CENTER);
            GradientDrawable handleBg = new GradientDrawable();
            handleBg.setColor(Color.parseColor("#0288D1"));
            handleBg.setCornerRadius(dpToPx(6));
            handleBg.setStroke(dpToPx(1), Color.WHITE);
            resizeHandle.setBackground(handleBg);

            FrameLayout.LayoutParams handleLp = new FrameLayout.LayoutParams(dpToPx(24), dpToPx(24));
            handleLp.gravity = Gravity.BOTTOM | Gravity.END;
            canvasFrame.addView(resizeHandle, handleLp);

            resizeHandle.setOnTouchListener(new View.OnTouchListener() {
                private float lastRawX;
                private float lastRawY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            lastRawX = event.getRawX();
                            lastRawY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            int dx = Math.round(event.getRawX() - lastRawX);
                            int dy = Math.round(event.getRawY() - lastRawY);
                            lastRawX = event.getRawX();
                            lastRawY = event.getRawY();
                            currentCanvasW[0] = Math.max(dpToPx(170), Math.min(dpToPx(420), currentCanvasW[0] + dx));
                            currentCanvasH[0] = Math.max(dpToPx(160), Math.min(dpToPx(620), currentCanvasH[0] + dy));
                            LinearLayout.LayoutParams updatedLp = new LinearLayout.LayoutParams(currentCanvasW[0], currentCanvasH[0]);
                            canvasFrame.setLayoutParams(updatedLp);
                            if (floatingRootView != null && windowManager != null) {
                                windowManager.updateViewLayout(floatingRootView, overlayLayoutParams);
                            }
                            return true;
                    }
                    return false;
                }
            });
        }

        header.setOnTouchListener(new View.OnTouchListener() {
            private float lastRawX;
            private float lastRawY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        lastRawX = event.getRawX();
                        lastRawY = event.getRawY();
                        setOverlayFocusable(false);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(event.getRawX() - lastRawX);
                        int dy = Math.round(event.getRawY() - lastRawY);
                        lastRawX = event.getRawX();
                        lastRawY = event.getRawY();
                        if (floatingRootView != null && windowManager != null) {
                            overlayLayoutParams.x = Math.max(0, overlayLayoutParams.x + dx);
                            overlayLayoutParams.y = Math.max(32, overlayLayoutParams.y + dy);
                            windowManager.updateViewLayout(floatingRootView, overlayLayoutParams);
                        }
                        return true;
                }
                return false;
            }
        });

        container.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        container.addView(canvasFrame, canvasLp);

        floatingRootView = container;
        windowManager.addView(floatingRootView, overlayLayoutParams);
    }

    private View buildDynamicComponentView(DynamicOverlayRegistry.OverlayItemSpec spec) {
        int bgColor = parseSafeColor(spec.bgColorHex, Color.parseColor("#2563EB"));
        int txtColor = parseSafeColor(spec.textColorHex, Color.WHITE);

        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setColor(bgColor);
        itemBg.setCornerRadius(dpToPx(10));

        String type = spec.type != null ? spec.type : "BUTTON";
        switch (type) {
            case "TOGGLE": {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

                final boolean[] isCheckedState = new boolean[]{
                        "1".equals(spec.currentValue) || "true".equalsIgnoreCase(spec.currentValue)
                };

                LinearLayout textCol = new LinearLayout(this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textColLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                );

                TextView titleTv = new TextView(this);
                titleTv.setText(spec.label);
                titleTv.setTextColor(txtColor);
                titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                titleTv.setTypeface(Typeface.DEFAULT_BOLD);
                titleTv.setSingleLine(true);

                textCol.addView(titleTv);

                TextView onOffBadge = new TextView(this);
                onOffBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
                onOffBadge.setTypeface(Typeface.DEFAULT_BOLD);
                onOffBadge.setTextColor(Color.WHITE);
                onOffBadge.setPadding(dpToPx(5), dpToPx(2), dpToPx(5), dpToPx(2));
                LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                badgeLp.leftMargin = dpToPx(6);
                badgeLp.rightMargin = dpToPx(4);

                Switch toggleSwitch = new Switch(this);
                toggleSwitch.setShowText(false);
                toggleSwitch.setChecked(isCheckedState[0]);

                Runnable updateVisuals = () -> {
                    boolean on = isCheckedState[0];
                    GradientDrawable rowBg = new GradientDrawable();
                    rowBg.setColor(on ? Color.parseColor("#ECFDF5") : bgColor);
                    rowBg.setCornerRadius(dpToPx(8));
                    rowBg.setStroke(dpToPx(2), on ? Color.parseColor("#00C853") : Color.parseColor("#64748B"));
                    row.setBackground(rowBg);

                    GradientDrawable badgeBg = new GradientDrawable();
                    badgeBg.setCornerRadius(dpToPx(4));
                    badgeBg.setColor(on ? Color.parseColor("#00C853") : Color.parseColor("#EF4444"));
                    onOffBadge.setBackground(badgeBg);
                    onOffBadge.setText(on ? "ON" : "OFF");
                };
                updateVisuals.run();

                toggleSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                    isCheckedState[0] = isChecked;
                    spec.currentValue = isChecked ? "1" : "0";
                    updateVisuals.run();
                    if (isChecked) {
                        SoundTriggerPlayer.playSoundTrigger(this, btn, spec.soundTrigger, spec.customSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger(this, btn, spec.offSoundTrigger, spec.offCustomSoundPath);
                    }
                    String payload = isChecked ? spec.onPayloadHex : spec.offPayloadHex;
                    LocalConfigStateWriter.getInstance().writeCustomComponentOffsetAsync(
                            getFilesDir(),
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            payload,
                            spec.label
                    );
                });

                View.OnClickListener rowClick = v -> toggleSwitch.setChecked(!toggleSwitch.isChecked());
                onOffBadge.setOnClickListener(rowClick);
                row.setOnClickListener(rowClick);

                row.addView(textCol, textColLp);
                row.addView(onOffBadge, badgeLp);
                row.addView(toggleSwitch);
                return row;
            }
            case "SLIDER": {
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setGravity(Gravity.CENTER_VERTICAL);
                box.setBackground(itemBg);
                box.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

                int maxVal = Math.max(1, spec.sliderMax);
                TextView labelTv = new TextView(this);
                labelTv.setText(spec.label + " (0-" + maxVal + "): " + spec.currentValue);
                labelTv.setTextColor(txtColor);
                labelTv.setTypeface(Typeface.DEFAULT_BOLD);
                labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);

                SeekBar seekBar = new SeekBar(this);
                seekBar.setMax(maxVal);
                int initProgress = 50;
                try {
                    initProgress = Integer.parseInt(spec.currentValue);
                } catch (Exception ignored) {
                }
                seekBar.setProgress(initProgress);
                seekBar.setOnTouchListener((v, event) -> {
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return false;
                });
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                        labelTv.setText(spec.label + " (0-" + maxVal + "): " + progress);
                        if (fromUser) {
                            if (progress == 0) {
                                SoundTriggerPlayer.playSoundTrigger(
                                        FloatingDashboardService.this,
                                        sb,
                                        spec.offSoundTrigger,
                                        spec.offCustomSoundPath
                                );
                            }
                            LocalConfigStateWriter.getInstance().writeCustomComponentOffsetAsync(
                                    getFilesDir(),
                                    spec.targetFilePath,
                                    spec.byteOffsetHex,
                                    String.valueOf(progress),
                                    spec.label
                            );
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar sb) {
                        SoundTriggerPlayer.playSoundTrigger(
                                FloatingDashboardService.this,
                                sb,
                                spec.soundTrigger,
                                spec.customSoundPath
                        );
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar sb) {
                        if (sb.getProgress() == 0) {
                            SoundTriggerPlayer.playSoundTrigger(
                                    FloatingDashboardService.this,
                                    sb,
                                    spec.offSoundTrigger,
                                    spec.offCustomSoundPath
                            );
                        }
                    }
                });

                box.addView(labelTv);
                box.addView(seekBar);
                return box;
            }
            case "INPUT": {
                EditText et = new EditText(this);
                et.setHint(spec.label);
                et.setText(spec.currentValue);
                et.setTextColor(txtColor);
                et.setHintTextColor(Color.LTGRAY);
                et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                et.setSingleLine(true);
                et.setBackground(itemBg);
                et.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
                et.setOnClickListener(v -> {
                    setOverlayFocusable(true);
                    SoundTriggerPlayer.playSoundTrigger(this, v, spec.soundTrigger, spec.customSoundPath);
                });
                et.setOnEditorActionListener((v, actionId, event) -> {
                    String val = v.getText().toString();
                    boolean isOff = val.trim().isEmpty() || "0".equals(val.trim()) || "false".equalsIgnoreCase(val.trim());
                    if (isOff) {
                        SoundTriggerPlayer.playSoundTrigger(this, v, spec.offSoundTrigger, spec.offCustomSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger(this, v, spec.soundTrigger, spec.customSoundPath);
                    }
                    LocalConfigStateWriter.getInstance().writeCustomComponentOffsetAsync(
                            getFilesDir(),
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            val,
                            spec.label
                    );
                    setOverlayFocusable(false);
                    return true;
                });
                return et;
            }
            case "IMAGE": {
                ImageView iv = new ImageView(this);
                iv.setBackground(itemBg);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                if (spec.customImagePath != null && !spec.customImagePath.trim().isEmpty()) {
                    File imgFile = new File(spec.customImagePath.trim());
                    if (imgFile.exists()) {
                        Bitmap bmp = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
                        if (bmp != null) {
                            iv.setImageBitmap(bmp);
                        }
                    }
                }
                final boolean[] isImgOn = new boolean[]{
                        "1".equals(spec.currentValue) || "true".equalsIgnoreCase(spec.currentValue)
                };
                iv.setOnClickListener(v -> {
                    isImgOn[0] = !isImgOn[0];
                    spec.currentValue = isImgOn[0] ? "1" : "0";
                    if (isImgOn[0]) {
                        SoundTriggerPlayer.playSoundTrigger(this, v, spec.soundTrigger, spec.customSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger(this, v, spec.offSoundTrigger, spec.offCustomSoundPath);
                    }
                    LocalConfigStateWriter.getInstance().writeCustomComponentOffsetAsync(
                            getFilesDir(),
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            isImgOn[0] ? spec.onPayloadHex : spec.offPayloadHex,
                            spec.label
                    );
                });
                return iv;
            }
            case "TEXT": {
                TextView tv = new TextView(this);
                tv.setText(spec.label);
                tv.setTextColor(txtColor);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                tv.setGravity(Gravity.CENTER);
                tv.setBackground(itemBg);
                final boolean[] isTxtOn = new boolean[]{
                        "1".equals(spec.currentValue) || "true".equalsIgnoreCase(spec.currentValue)
                };
                tv.setOnClickListener(v -> {
                    isTxtOn[0] = !isTxtOn[0];
                    spec.currentValue = isTxtOn[0] ? "1" : "0";
                    if (isTxtOn[0]) {
                        SoundTriggerPlayer.playSoundTrigger(this, v, spec.soundTrigger, spec.customSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger(this, v, spec.offSoundTrigger, spec.offCustomSoundPath);
                    }
                    LocalConfigStateWriter.getInstance().writeCustomComponentOffsetAsync(
                            getFilesDir(),
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            isTxtOn[0] ? spec.onPayloadHex : spec.offPayloadHex,
                            spec.label
                    );
                });
                return tv;
            }
            case "BUTTON":
            default: {
                final boolean[] isBtnOn = new boolean[]{
                        "1".equals(spec.currentValue) || "true".equalsIgnoreCase(spec.currentValue)
                };
                LinearLayout btnRow = new LinearLayout(this);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setGravity(Gravity.CENTER_VERTICAL);
                btnRow.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));

                TextView labelTv = new TextView(this);
                labelTv.setText(spec.label);
                labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                labelTv.setTypeface(Typeface.DEFAULT_BOLD);
                labelTv.setSingleLine(true);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                );

                TextView pillBadge = new TextView(this);
                pillBadge.setTextColor(Color.WHITE);
                pillBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                pillBadge.setTypeface(Typeface.DEFAULT_BOLD);
                pillBadge.setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2));
                LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                pillLp.leftMargin = dpToPx(6);

                Runnable updateBtnVisuals = () -> {
                    boolean on = isBtnOn[0];
                    GradientDrawable btnBg = new GradientDrawable();
                    btnBg.setColor(on ? Color.parseColor("#00C853") : bgColor);
                    btnBg.setCornerRadius(dpToPx(8));
                    btnBg.setStroke(dpToPx(2), on ? Color.parseColor("#00C853") : Color.parseColor("#475569"));
                    btnRow.setBackground(btnBg);

                    labelTv.setTextColor(on ? Color.WHITE : txtColor);

                    GradientDrawable pillBg = new GradientDrawable();
                    pillBg.setColor(on ? Color.parseColor("#047857") : Color.parseColor("#EF4444"));
                    pillBg.setCornerRadius(dpToPx(99));
                    pillBg.setStroke(dpToPx(1), Color.WHITE);
                    pillBadge.setBackground(pillBg);
                    pillBadge.setText(on ? "ON" : "OFF");
                };
                updateBtnVisuals.run();

                View.OnClickListener clickListener = v -> {
                    isBtnOn[0] = !isBtnOn[0];
                    spec.currentValue = isBtnOn[0] ? "1" : "0";
                    updateBtnVisuals.run();
                    if (isBtnOn[0]) {
                        SoundTriggerPlayer.playSoundTrigger(this, btnRow, spec.soundTrigger, spec.customSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger(this, btnRow, spec.offSoundTrigger, spec.offCustomSoundPath);
                    }
                    String payload = isBtnOn[0] ? spec.onPayloadHex : spec.offPayloadHex;
                    LocalConfigStateWriter.getInstance().writeCustomComponentOffsetAsync(
                            getFilesDir(),
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            payload,
                            spec.label
                    );
                };

                btnRow.setOnClickListener(clickListener);
                pillBadge.setOnClickListener(clickListener);

                btnRow.addView(labelTv, labelLp);
                btnRow.addView(pillBadge, pillLp);
                return btnRow;
            }
        }
    }

    private void setOverlayFocusable(boolean focusable) {
        if (floatingRootView == null || windowManager == null || overlayLayoutParams == null) return;
        boolean currentlyNotFocusable =
                (overlayLayoutParams.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0;
        if (focusable && currentlyNotFocusable) {
            overlayLayoutParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(floatingRootView, overlayLayoutParams);
        } else if (!focusable && !currentlyNotFocusable) {
            overlayLayoutParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(floatingRootView, overlayLayoutParams);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int parseSafeColor(String hex, int fallback) {
        if (hex == null || hex.trim().isEmpty()) return fallback;
        try {
            String formatted = hex.trim().startsWith("#") ? hex.trim() : "#" + hex.trim();
            return Color.parseColor(formatted);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void removeSystemOverlayWindow() {
        if (floatingRootView != null && windowManager != null) {
            try {
                windowManager.removeView(floatingRootView);
            } catch (Exception ignored) {
            }
            floatingRootView = null;
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        removeSystemOverlayWindow();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.service_notification_channel),
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildForegroundNotification() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
}
