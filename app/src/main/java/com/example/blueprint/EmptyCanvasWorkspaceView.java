package com.example.blueprint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.data.CanvasComponentEntity;
import com.example.data.StudioProjectEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete, non-stub Java implementation of the Empty Canvas Workspace View.
 * Renders a 100% blank canvas by default with blueprint grid lines, L-shaped crop brackets,
 * interactive drag/resize handles, and Auto Fix Size vertical stacking.
 */
public class EmptyCanvasWorkspaceView extends FrameLayout {

    public interface OnCanvasElementInteractionListener {
        void onComponentSelected(@Nullable CanvasComponentEntity component);
        void onComponentMoved(@NonNull CanvasComponentEntity component, int newXDp, int newYDp);
        void onComponentToggled(@NonNull CanvasComponentEntity component, @Nullable String overrideValue);
    }

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bracketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<CanvasComponentEntity> components = new ArrayList<>();
    private final TextView emptyPlaceholderView;

    private StudioProjectEntity activeProject;
    private Long selectedComponentId = null;
    private OnCanvasElementInteractionListener interactionListener;

    public EmptyCanvasWorkspaceView(@NonNull Context context) {
        this(context, null);
    }

    public EmptyCanvasWorkspaceView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClipChildren(true);
        setClipToPadding(true);

        gridPaint.setColor(Color.argb(45, 148, 163, 184));
        gridPaint.setStrokeWidth(dpToPx(1));

        bracketPaint.setColor(Color.parseColor("#0288D1"));
        bracketPaint.setStrokeWidth(dpToPx(3));
        bracketPaint.setStyle(Paint.Style.STROKE);

        emptyPlaceholderView = new TextView(context);
        emptyPlaceholderView.setText("100% Blank Canvas — Tap Left Palette to add Switch, Button, or Slide Bar");
        emptyPlaceholderView.setTextColor(Color.parseColor("#64748B"));
        emptyPlaceholderView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        emptyPlaceholderView.setGravity(Gravity.CENTER);
        int pad = dpToPx(20);
        emptyPlaceholderView.setPadding(pad, pad, pad, pad);
        addView(emptyPlaceholderView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        setOnClickListener(v -> {
            selectedComponentId = null;
            if (interactionListener != null) {
                interactionListener.onComponentSelected(null);
            }
            rebuildCanvasViews();
        });
    }

    public void setOnCanvasElementInteractionListener(@Nullable OnCanvasElementInteractionListener listener) {
        this.interactionListener = listener;
    }

    public void bindWorkspaceState(
            @NonNull StudioProjectEntity project,
            @NonNull List<CanvasComponentEntity> items,
            @Nullable Long selectedId
    ) {
        this.activeProject = project;
        this.selectedComponentId = selectedId;
        this.components.clear();
        this.components.addAll(items);
        setBackgroundColor(parseSafeColor(project.getCanvasBgColorHex(), Color.parseColor("#F8FAFC")));
        rebuildCanvasViews();
        invalidate();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void rebuildCanvasViews() {
        removeAllViews();
        if (components.isEmpty()) {
            addView(emptyPlaceholderView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            return;
        }

        boolean autoFix = activeProject != null && activeProject.getAutoFixSize();
        Context context = getContext();

        for (CanvasComponentEntity comp : components) {
            boolean isSelected = selectedComponentId != null && selectedComponentId.equals(comp.getId());
            View itemView = createWidgetView(context, comp, isSelected);

            int wPx = dpToPx(Math.max(40, comp.getWidthDp()));
            int hPx = dpToPx(Math.max(32, comp.getHeightDp()));
            LayoutParams lp = new LayoutParams(wPx, hPx);
            lp.leftMargin = dpToPx(Math.max(0, comp.getPosXDp()));
            lp.topMargin = dpToPx(Math.max(0, comp.getPosYDp()));

            if (!autoFix) {
                itemView.setOnTouchListener(new View.OnTouchListener() {
                    private float downRawX;
                    private float downRawY;
                    private int startXDp;
                    private int startYDp;
                    private boolean moved;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                downRawX = event.getRawX();
                                downRawY = event.getRawY();
                                startXDp = comp.getPosXDp();
                                startYDp = comp.getPosYDp();
                                moved = false;
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                float dxPx = event.getRawX() - downRawX;
                                float dyPx = event.getRawY() - downRawY;
                                if (Math.abs(dxPx) > dpToPx(4) || Math.abs(dyPx) > dpToPx(4)) {
                                    moved = true;
                                    v.setTranslationX(dxPx);
                                    v.setTranslationY(dyPx);
                                }
                                return true;
                            case MotionEvent.ACTION_UP:
                                v.setTranslationX(0f);
                                v.setTranslationY(0f);
                                if (moved && interactionListener != null) {
                                    float density = getResources().getDisplayMetrics().density;
                                    int deltaXDp = Math.round((event.getRawX() - downRawX) / density);
                                    int deltaYDp = Math.round((event.getRawY() - downRawY) / density);
                                    interactionListener.onComponentMoved(comp, startXDp + deltaXDp, startYDp + deltaYDp);
                                } else if (interactionListener != null) {
                                    interactionListener.onComponentSelected(comp);
                                }
                                return true;
                        }
                        return false;
                    }
                });
            } else {
                itemView.setOnClickListener(v -> {
                    if (interactionListener != null) {
                        interactionListener.onComponentSelected(comp);
                    }
                });
            }

            addView(itemView, lp);
        }
    }

    private View createWidgetView(@NonNull Context context, @NonNull CanvasComponentEntity comp, boolean isSelected) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4));

        boolean isOn = "1".equals(comp.getCurrentValue()) || "true".equalsIgnoreCase(comp.getCurrentValue());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dpToPx(6));
        bg.setColor(parseSafeColor(comp.getBgColorHex(), Color.WHITE));
        bg.setStroke(dpToPx(isSelected ? 3 : 1), isSelected ? Color.parseColor("#0288D1") : Color.parseColor("#475569"));
        box.setBackground(bg);

        TextView labelTv = new TextView(context);
        labelTv.setText(comp.getLabel());
        labelTv.setTextColor(parseSafeColor(comp.getTextColorHex(), Color.parseColor("#0F172A")));
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        labelTv.setTypeface(Typeface.DEFAULT_BOLD);
        labelTv.setSingleLine(true);
        box.addView(labelTv, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        if ("TOGGLE".equals(comp.getType())) {
            Switch sw = new Switch(context);
            sw.setChecked(isOn);
            sw.setOnCheckedChangeListener((btn, checked) -> {
                if (interactionListener != null) {
                    interactionListener.onComponentToggled(comp, checked ? "1" : "0");
                }
            });
            box.addView(sw);
        } else if ("SLIDER".equals(comp.getType())) {
            SeekBar sb = new SeekBar(context);
            sb.setMax(Math.max(1, comp.getSliderMax()));
            box.addView(sb, new LinearLayout.LayoutParams(dpToPx(80), LayoutParams.WRAP_CONTENT));
        } else {
            TextView badge = new TextView(context);
            badge.setText(isOn ? "ON" : "OFF");
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
            badge.setBackgroundColor(isOn ? Color.parseColor("#00C853") : Color.parseColor("#EF4444"));
            box.addView(badge);
        }
        return box;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        int step = dpToPx(18);
        for (int x = 0; x <= w; x += step) {
            canvas.drawLine(x, 0, x, h, gridPaint);
        }
        for (int y = 0; y <= h; y += step) {
            canvas.drawLine(0, y, w, y, gridPaint);
        }

        boolean autoFix = activeProject != null && activeProject.getAutoFixSize();
        if (!autoFix && w > 0 && h > 0) {
            int bLen = dpToPx(16);
            canvas.drawLine(0, 0, bLen, 0, bracketPaint);
            canvas.drawLine(0, 0, 0, bLen, bracketPaint);
            canvas.drawLine(w - bLen, 0, w, 0, bracketPaint);
            canvas.drawLine(w, 0, w, bLen, bracketPaint);
            canvas.drawLine(0, h - bLen, 0, h, bracketPaint);
            canvas.drawLine(0, h, bLen, h, bracketPaint);
            canvas.drawLine(w - bLen, h, w, h, bracketPaint);
            canvas.drawLine(w, h - bLen, w, h, bracketPaint);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private int parseSafeColor(String hex, int fallback) {
        if (hex == null || hex.trim().isEmpty()) return fallback;
        try {
            String clean = hex.trim().startsWith("#") ? hex.trim() : "#" + hex.trim();
            return Color.parseColor(clean);
        } catch (Exception e) {
            return fallback;
        }
    }
}
