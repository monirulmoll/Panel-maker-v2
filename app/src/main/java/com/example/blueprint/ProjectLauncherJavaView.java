package com.example.blueprint;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.data.StudioProjectEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Complete, non-stub Java implementation of the Studio Error Project Launcher View.
 * Provides project creation, project selection, and project deletion with zero hardcoded canvas widgets.
 */
public class ProjectLauncherJavaView extends LinearLayout {

    public interface OnProjectActionListener {
        void onCreateProject(@NonNull String projectName, @NonNull String overlayTitle);
        void onOpenProject(@NonNull StudioProjectEntity project);
        void onDeleteProject(@NonNull StudioProjectEntity project);
    }

    private final EditText projectNameInput;
    private final EditText overlayTitleInput;
    private final LinearLayout projectCardsContainer;
    private final List<StudioProjectEntity> currentProjects = new ArrayList<>();
    private OnProjectActionListener actionListener;

    public ProjectLauncherJavaView(@NonNull Context context) {
        this(context, null);
    }

    public ProjectLauncherJavaView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor("#F1F5F9"));
        int pad = dpToPx(16);
        setPadding(pad, pad, pad, pad);

        // Header Banner
        TextView titleView = new TextView(context);
        titleView.setText("STUDIO ERROR — Floating APK IDE");
        titleView.setTextColor(Color.parseColor("#0F172A"));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        addView(titleView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView subtitleView = new TextView(context);
        subtitleView.setText("Create or open a project to design your Floating Mod Menu on a 100% blank canvas and compile to a signed APK.");
        subtitleView.setTextColor(Color.parseColor("#475569"));
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LayoutParams subLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dpToPx(4);
        subLp.bottomMargin = dpToPx(12);
        addView(subtitleView, subLp);

        // New Project Card
        LinearLayout createCard = new LinearLayout(context);
        createCard.setOrientation(VERTICAL);
        int cardPad = dpToPx(12);
        createCard.setPadding(cardPad, cardPad, cardPad, cardPad);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dpToPx(10));
        cardBg.setStroke(dpToPx(1), Color.parseColor("#CBD5E1"));
        createCard.setBackground(cardBg);

        projectNameInput = new EditText(context);
        projectNameInput.setHint("Project Name (e.g., My Floating Utility)");
        projectNameInput.setSingleLine(true);
        projectNameInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        createCard.addView(projectNameInput, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        overlayTitleInput = new EditText(context);
        overlayTitleInput.setHint("Floating Window Header Title (e.g., Floating Mod Panel)");
        overlayTitleInput.setSingleLine(true);
        overlayTitleInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        createCard.addView(overlayTitleInput, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        Button createBtn = new Button(context);
        createBtn.setText("Create Blank Project");
        createBtn.setAllCaps(false);
        createBtn.setTextColor(Color.WHITE);
        createBtn.setBackgroundColor(Color.parseColor("#0288D1"));
        createBtn.setOnClickListener(v -> {
            String name = projectNameInput.getText().toString().trim();
            String overlay = overlayTitleInput.getText().toString().trim();
            if (name.isEmpty()) name = "My Floating Utility";
            if (overlay.isEmpty()) overlay = "Floating Mod Panel";
            if (actionListener != null) {
                actionListener.onCreateProject(name, overlay);
            }
        });
        LayoutParams btnLp = new LayoutParams(LayoutParams.MATCH_PARENT, dpToPx(42));
        btnLp.topMargin = dpToPx(8);
        createCard.addView(createBtn, btnLp);

        LayoutParams cardLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dpToPx(14);
        addView(createCard, cardLp);

        // Scrollable Saved Projects List
        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        projectCardsContainer = new LinearLayout(context);
        projectCardsContainer.setOrientation(VERTICAL);
        scrollView.addView(projectCardsContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public void setOnProjectActionListener(@Nullable OnProjectActionListener listener) {
        this.actionListener = listener;
    }

    public void submitProjects(@NonNull List<StudioProjectEntity> projects) {
        currentProjects.clear();
        currentProjects.addAll(projects);
        projectCardsContainer.removeAllViews();

        Context context = getContext();
        for (StudioProjectEntity project : currentProjects) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = dpToPx(12);
            row.setPadding(pad, pad, pad, pad);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dpToPx(8));
            bg.setStroke(dpToPx(1), Color.parseColor("#CBD5E1"));
            row.setBackground(bg);

            LinearLayout infoCol = new LinearLayout(context);
            infoCol.setOrientation(VERTICAL);
            TextView nameTv = new TextView(context);
            nameTv.setText(project.getName());
            nameTv.setTextColor(Color.parseColor("#0F172A"));
            nameTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            infoCol.addView(nameTv);

            TextView metaTv = new TextView(context);
            metaTv.setText(String.format(
                    Locale.US,
                    "%s • %dx%d dp • AutoSize: %s",
                    project.getOverlayTitle(),
                    project.getCanvasWidthDp(),
                    project.getCanvasHeightDp(),
                    project.getAutoFixSize() ? "ON" : "OFF"
            ));
            metaTv.setTextColor(Color.parseColor("#64748B"));
            metaTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            infoCol.addView(metaTv);

            row.addView(infoCol, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            Button openBtn = new Button(context);
            openBtn.setText("Open");
            openBtn.setAllCaps(false);
            openBtn.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onOpenProject(project);
                }
            });
            row.addView(openBtn, new LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(38)));

            Button delBtn = new Button(context);
            delBtn.setText("Delete");
            delBtn.setAllCaps(false);
            delBtn.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onDeleteProject(project);
                }
            });
            LayoutParams delLp = new LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(38));
            delLp.leftMargin = dpToPx(6);
            row.addView(delBtn, delLp);

            LayoutParams rowLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dpToPx(8);
            projectCardsContainer.addView(row, rowLp);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
