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
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.OverScroller;
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
        DynamicOverlayRegistry.loadFromBundledAssetsIfEmpty(this);

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
        panelBg.setColor(parseSafeColor(DynamicOverlayRegistry.getActiveCanvasBgHex(), Color.WHITE));
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

        String activeTitle = DynamicOverlayRegistry.getActiveOverlayTitle();
        String displayTitle = (activeTitle != null && !activeTitle.trim().isEmpty())
                ? activeTitle.trim()
                : DynamicOverlayRegistry.getActiveProjectName();
        String floatingLogoPath = DynamicOverlayRegistry.getActiveFloatingLogoPath();
        Bitmap rawLogoBitmap = null;
        if (floatingLogoPath != null && !floatingLogoPath.trim().isEmpty()) {
            File lf = new File(floatingLogoPath.trim());
            if (lf.exists()) {
                rawLogoBitmap = BitmapFactory.decodeFile(lf.getAbsolutePath());
            }
        }

        if (rawLogoBitmap != null) {
            ImageView headerLogoIv = new ImageView(this);
            headerLogoIv.setImageBitmap(createCircularBitmap(rawLogoBitmap, dpToPx(24)));
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dpToPx(24), dpToPx(24));
            logoLp.rightMargin = dpToPx(8);
            header.addView(headerLogoIv, logoLp);
        }

        TextView titleTv = new TextView(this);
        titleTv.setText(displayTitle != null ? displayTitle : "");
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(titleTv, titleLp);

        // Build the collapsed round ("goal" / गोल) floating logo bubble
        int bubbleSizePx = dpToPx(58);
        FrameLayout goalLogoBubble = new FrameLayout(this);
        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setShape(GradientDrawable.OVAL);
        bubbleBg.setColor(Color.parseColor("#2563EB"));
        bubbleBg.setStroke(dpToPx(2), Color.WHITE);
        goalLogoBubble.setBackground(bubbleBg);
        goalLogoBubble.setVisibility(View.GONE);

        if (rawLogoBitmap != null) {
            ImageView bubbleIv = new ImageView(this);
            bubbleIv.setImageBitmap(createCircularBitmap(rawLogoBitmap, bubbleSizePx));
            bubbleIv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            goalLogoBubble.addView(bubbleIv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        } else {
            TextView bubbleTitleTv = new TextView(this);
            String fallbackBubbleText = (displayTitle != null && !displayTitle.trim().isEmpty())
                    ? displayTitle.trim()
                    : "Float";
            bubbleTitleTv.setText(fallbackBubbleText);
            bubbleTitleTv.setTextColor(Color.WHITE);
            bubbleTitleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            bubbleTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
            bubbleTitleTv.setGravity(Gravity.CENTER);
            bubbleTitleTv.setMaxLines(2);
            bubbleTitleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            bubbleTitleTv.setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));
            goalLogoBubble.addView(bubbleTitleTv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
            ));
        }

        // Clicking ✕ minimizes the panel into the round ("goal") logo bubble instead of closing everything!
        TextView closeBtn = new TextView(this);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        closeBtn.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
        closeBtn.setOnClickListener(v -> {
            setOverlayFocusable(false);
            container.setVisibility(View.GONE);
            goalLogoBubble.setVisibility(View.VISIBLE);
            if (floatingRootView != null && windowManager != null) {
                windowManager.updateViewLayout(floatingRootView, overlayLayoutParams);
            }
        });
        header.addView(closeBtn);

        // Free / Auto-Fixed Scrollable Canvas Frame matching user's Studio Error workspace
        boolean isAutoFix = DynamicOverlayRegistry.isActiveAutoFixSize();
        FrameLayout canvasFrame = new FrameLayout(this);
        int currentCanvasW = dpToPx(DynamicOverlayRegistry.getActiveCanvasWidthDp());
        int currentCanvasH = dpToPx(DynamicOverlayRegistry.getActiveCanvasHeightDp());
        LinearLayout.LayoutParams canvasLp = new LinearLayout.LayoutParams(currentCanvasW, currentCanvasH);

        // Jitter-Free OverflowOnlyScrollLayout (extends FrameLayout, NOT ScrollView):
        // 1. Completely bypasses OEM/Android 12+ ScrollView StretchEdgeEffect & SpringOverScroller that cause shaking!
        // 2. Scroll is strictly 0 (disabled) while all widgets fit inside the visual window.
        // 3. Scroll turns ON automatically with hard-clamped [0..maxRange] bounds as soon as any widget goes outside.
        OverflowOnlyScrollLayout scrollView = new OverflowOnlyScrollLayout(this);
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
            if (minTopDp < 0 || dpToPx(maxBottomDp) > currentCanvasH) {
                freeCanvas.setPadding(0, 0, 0, dpToPx(8));
            }
            scrollView.addView(freeCanvas, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        canvasFrame.addView(scrollView, scrollLp);

        header.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int startWinX;
            private int startWinY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startWinX = overlayLayoutParams.x;
                        startWinY = overlayLayoutParams.y;
                        setOverlayFocusable(false);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int totalDx = Math.round(event.getRawX() - downRawX);
                        int totalDy = Math.round(event.getRawY() - downRawY);
                        int nextX = Math.max(0, startWinX + totalDx);
                        int nextY = Math.max(32, startWinY + totalDy);
                        if (floatingRootView != null && windowManager != null
                                && (nextX != overlayLayoutParams.x || nextY != overlayLayoutParams.y)) {
                            overlayLayoutParams.x = nextX;
                            overlayLayoutParams.y = nextY;
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

        // Touch listener on the round ("goal") floating logo bubble: drag to move, tap to expand back!
        final int bubbleTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        goalLogoBubble.setOnTouchListener(new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int startWinX;
            private int startWinY;
            private boolean wasDragged;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startWinX = overlayLayoutParams.x;
                        startWinY = overlayLayoutParams.y;
                        wasDragged = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int totalDx = Math.round(event.getRawX() - downRawX);
                        int totalDy = Math.round(event.getRawY() - downRawY);
                        if (Math.abs(totalDx) > bubbleTouchSlop || Math.abs(totalDy) > bubbleTouchSlop) {
                            wasDragged = true;
                        }
                        int nextX = Math.max(0, startWinX + totalDx);
                        int nextY = Math.max(32, startWinY + totalDy);
                        if (floatingRootView != null && windowManager != null
                                && (nextX != overlayLayoutParams.x || nextY != overlayLayoutParams.y)) {
                            overlayLayoutParams.x = nextX;
                            overlayLayoutParams.y = nextY;
                            windowManager.updateViewLayout(floatingRootView, overlayLayoutParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!wasDragged) {
                            goalLogoBubble.setVisibility(View.GONE);
                            container.setVisibility(View.VISIBLE);
                            if (floatingRootView != null && windowManager != null) {
                                windowManager.updateViewLayout(floatingRootView, overlayLayoutParams);
                            }
                        }
                        return true;
                }
                return false;
            }
        });

        FrameLayout rootWrapper = new FrameLayout(this);
        rootWrapper.addView(container, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        rootWrapper.addView(goalLogoBubble, new FrameLayout.LayoutParams(
                bubbleSizePx,
                bubbleSizePx
        ));

        floatingRootView = rootWrapper;
        windowManager.addView(floatingRootView, overlayLayoutParams);
    }

    private Bitmap createCircularBitmap(Bitmap src, int sizePx) {
        if (src == null || sizePx <= 0) return null;
        Bitmap scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true);
        Bitmap output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(output);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        float radius = sizePx / 2f;
        android.graphics.BitmapShader shader = new android.graphics.BitmapShader(
                scaled,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP
        );
        paint.setShader(shader);
        canvas.drawCircle(radius, radius, radius - dpToPx(2), paint);

        android.graphics.Paint borderPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(android.graphics.Paint.Style.STROKE);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStrokeWidth(dpToPx(2));
        canvas.drawCircle(radius, radius, radius - dpToPx(1), borderPaint);
        return output;
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
                    LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(
                            getFilesDir(),
                            "widget_" + spec.id,
                            "TOGGLE",
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            spec.offPayloadHex,
                            spec.onPayloadHex,
                            payload,
                            isChecked,
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
                        spec.currentValue = String.valueOf(progress);
                        if (fromUser) {
                            if (progress == 0) {
                                SoundTriggerPlayer.playSoundTrigger(
                                        FloatingDashboardService.this,
                                        sb,
                                        spec.offSoundTrigger,
                                        spec.offCustomSoundPath
                                );
                            }
                            LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(
                                    getFilesDir(),
                                    "widget_" + spec.id,
                                    "SLIDER",
                                    spec.targetFilePath,
                                    spec.byteOffsetHex,
                                    spec.offPayloadHex,
                                    spec.onPayloadHex,
                                    String.valueOf(progress),
                                    progress > 0,
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
                et.setText("0".equals(spec.currentValue) ? "" : spec.currentValue);
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
                et.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        String val = s != null ? s.toString() : "";
                        spec.currentValue = val;
                        boolean isActive = !val.trim().isEmpty() && !"0".equals(val.trim()) && !"false".equalsIgnoreCase(val.trim());
                        LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(
                                getFilesDir(),
                                "widget_" + spec.id,
                                "INPUT",
                                spec.targetFilePath,
                                spec.byteOffsetHex,
                                spec.offPayloadHex,
                                spec.onPayloadHex,
                                val,
                                isActive,
                                spec.label
                        );
                    }
                });
                et.setOnEditorActionListener((v, actionId, event) -> {
                    String val = v.getText().toString();
                    boolean isOff = val.trim().isEmpty() || "0".equals(val.trim()) || "false".equalsIgnoreCase(val.trim());
                    if (isOff) {
                        SoundTriggerPlayer.playSoundTrigger(this, v, spec.offSoundTrigger, spec.offCustomSoundPath);
                    } else {
                        SoundTriggerPlayer.playSoundTrigger(this, v, spec.soundTrigger, spec.customSoundPath);
                    }
                    LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(
                            getFilesDir(),
                            "widget_" + spec.id,
                            "INPUT",
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            spec.offPayloadHex,
                            spec.onPayloadHex,
                            val,
                            !isOff,
                            spec.label
                    );
                    setOverlayFocusable(false);
                    return true;
                });
                return et;
            }
            case "LINK": {
                LinearLayout linkRow = new LinearLayout(this);
                linkRow.setOrientation(LinearLayout.HORIZONTAL);
                linkRow.setGravity(Gravity.CENTER_VERTICAL);
                linkRow.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));

                GradientDrawable linkBg = new GradientDrawable();
                linkBg.setColor(bgColor);
                linkBg.setCornerRadius(dpToPx(8));
                linkBg.setStroke(dpToPx(2), Color.parseColor("#38BDF8"));
                linkRow.setBackground(linkBg);

                TextView iconTv = new TextView(this);
                iconTv.setText("🌐");
                iconTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                iconLp.rightMargin = dpToPx(6);

                TextView labelTv = new TextView(this);
                labelTv.setText(spec.label);
                labelTv.setTextColor(txtColor);
                labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                labelTv.setTypeface(Typeface.DEFAULT_BOLD);
                labelTv.setSingleLine(true);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                );

                TextView openBadge = new TextView(this);
                openBadge.setText("OPEN ↗");
                openBadge.setTextColor(Color.WHITE);
                openBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
                openBadge.setTypeface(Typeface.DEFAULT_BOLD);
                openBadge.setPadding(dpToPx(7), dpToPx(2), dpToPx(7), dpToPx(2));
                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setColor(Color.parseColor("#0288D1"));
                badgeBg.setCornerRadius(dpToPx(99));
                openBadge.setBackground(badgeBg);

                View.OnClickListener openClick = v -> {
                    SoundTriggerPlayer.playSoundTrigger(this, linkRow, spec.soundTrigger, spec.customSoundPath);
                    openLinkUrl(spec.linkUrl != null && !spec.linkUrl.trim().isEmpty() ? spec.linkUrl : spec.onPayloadHex);
                };
                linkRow.setOnClickListener(openClick);
                openBadge.setOnClickListener(openClick);

                linkRow.addView(iconTv, iconLp);
                linkRow.addView(labelTv, labelLp);
                linkRow.addView(openBadge);
                return linkRow;
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
                    String payload = isImgOn[0] ? spec.onPayloadHex : spec.offPayloadHex;
                    LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(
                            getFilesDir(),
                            "widget_" + spec.id,
                            "IMAGE",
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            spec.offPayloadHex,
                            spec.onPayloadHex,
                            payload,
                            isImgOn[0],
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
                    String payload = isTxtOn[0] ? spec.onPayloadHex : spec.offPayloadHex;
                    LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(
                            getFilesDir(),
                            "widget_" + spec.id,
                            "TEXT",
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            spec.offPayloadHex,
                            spec.onPayloadHex,
                            payload,
                            isTxtOn[0],
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
                    if (spec.linkUrl != null && !spec.linkUrl.trim().isEmpty() && isBtnOn[0]) {
                        openLinkUrl(spec.linkUrl);
                    }
                    String payload = isBtnOn[0] ? spec.onPayloadHex : spec.offPayloadHex;
                    LocalConfigStateWriter.getInstance().applyWidgetPatchAsync(
                            getFilesDir(),
                            "widget_" + spec.id,
                            "BUTTON",
                            spec.targetFilePath,
                            spec.byteOffsetHex,
                            spec.offPayloadHex,
                            spec.onPayloadHex,
                            payload,
                            isBtnOn[0],
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

    private void openLinkUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return;
        String url = rawUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(browserIntent);
        } catch (Exception ignored) {
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

    /**
     * Deterministic, zero-jitter vertical scroll container that extends FrameLayout (NOT ScrollView)
     * so OEM ROMs (RealmeUI / ColorOS / MIUI) and Android 12+ StretchEdgeEffect never apply
     * rubber-band bounce or shaking when touching/scrolling widgets inside the floating window.
     */
    private static final class OverflowOnlyScrollLayout extends FrameLayout {
        private final OverScroller scroller;
        private final int touchSlop;
        private final int minFlingVelocity;
        private final int maxFlingVelocity;
        private VelocityTracker velocityTracker;
        private float downRawY;
        private float lastRawY;
        private boolean isBeingDragged = false;

        OverflowOnlyScrollLayout(Context context) {
            super(context);
            this.scroller = new OverScroller(context);
            ViewConfiguration vc = ViewConfiguration.get(context);
            this.touchSlop = vc.getScaledTouchSlop();
            this.minFlingVelocity = vc.getScaledMinimumFlingVelocity();
            this.maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
            setOverScrollMode(View.OVER_SCROLL_NEVER);
            setClipChildren(true);
            setClipToPadding(true);
        }

        private int getMaxScrollRange() {
            if (getChildCount() == 0) return 0;
            View child = getChildAt(0);
            return Math.max(0, child.getHeight() - getHeight());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (getChildCount() > 0) {
                View child = getChildAt(0);
                int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
                int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                child.measure(widthSpec, heightSpec);
            }
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (getChildCount() > 0) {
                View child = getChildAt(0);
                child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            }
            int maxRange = getMaxScrollRange();
            if (maxRange <= 0) {
                if (getScrollY() != 0) {
                    scrollTo(0, 0);
                }
            } else if (getScrollY() > maxRange) {
                scrollTo(0, maxRange);
            }
        }

        @Override
        public void requestChildFocus(View child, View focused) {
            // Prevent EditText focus from auto-jumping/shaking the floating container
            super.requestChildFocus(child, focused);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            int maxRange = getMaxScrollRange();
            if (maxRange <= 0) {
                isBeingDragged = false;
                if (getScrollY() != 0) {
                    scrollTo(0, 0);
                }
                return false;
            }

            int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                isBeingDragged = false;
                recycleVelocityTracker();
                return false;
            }

            if (action != MotionEvent.ACTION_DOWN && isBeingDragged) {
                return true;
            }

            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    downRawY = ev.getRawY();
                    lastRawY = downRawY;
                    if (!scroller.isFinished()) {
                        scroller.abortAnimation();
                    }
                    isBeingDragged = false;
                    initOrResetVelocityTracker();
                    velocityTracker.addMovement(ev);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    float rawY = ev.getRawY();
                    float yDiff = Math.abs(rawY - downRawY);
                    if (yDiff > touchSlop) {
                        isBeingDragged = true;
                        lastRawY = rawY;
                        initVelocityTrackerIfNotExists();
                        velocityTracker.addMovement(ev);
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                    break;
                }
            }
            return isBeingDragged;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int maxRange = getMaxScrollRange();
            if (maxRange <= 0) {
                isBeingDragged = false;
                if (getScrollY() != 0) {
                    scrollTo(0, 0);
                }
                return false;
            }

            initVelocityTrackerIfNotExists();
            velocityTracker.addMovement(ev);

            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    if (!scroller.isFinished()) {
                        scroller.abortAnimation();
                    }
                    downRawY = ev.getRawY();
                    lastRawY = downRawY;
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    float rawY = ev.getRawY();
                    float deltaY = lastRawY - rawY;
                    if (!isBeingDragged && Math.abs(rawY - downRawY) > touchSlop) {
                        isBeingDragged = true;
                        if (deltaY > 0) {
                            deltaY -= touchSlop;
                        } else {
                            deltaY += touchSlop;
                        }
                    }
                    if (isBeingDragged && Math.abs(deltaY) >= 1.0f) {
                        int stepPx = Math.round(deltaY);
                        lastRawY = rawY;
                        int targetScrollY = Math.max(0, Math.min(maxRange, getScrollY() + stepPx));
                        if (targetScrollY != getScrollY()) {
                            scrollTo(0, targetScrollY);
                        }
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    if (isBeingDragged) {
                        velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                        int yVelocity = (int) velocityTracker.getYVelocity();
                        if (Math.abs(yVelocity) > minFlingVelocity) {
                            scroller.fling(0, getScrollY(), 0, -yVelocity, 0, 0, 0, maxRange);
                            postInvalidateOnAnimation();
                        }
                        isBeingDragged = false;
                    }
                    recycleVelocityTracker();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL: {
                    isBeingDragged = false;
                    recycleVelocityTracker();
                    return true;
                }
            }
            return true;
        }

        @Override
        public void computeScroll() {
            if (scroller.computeScrollOffset()) {
                int maxRange = getMaxScrollRange();
                int targetY = Math.max(0, Math.min(maxRange, scroller.getCurrY()));
                if (targetY != getScrollY()) {
                    scrollTo(0, targetY);
                }
                postInvalidateOnAnimation();
            }
        }

        private void initOrResetVelocityTracker() {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            } else {
                velocityTracker.clear();
            }
        }

        private void initVelocityTrackerIfNotExists() {
            if (velocityTracker == null) {
                velocityTracker = VelocityTracker.obtain();
            }
        }

        private void recycleVelocityTracker() {
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }
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
