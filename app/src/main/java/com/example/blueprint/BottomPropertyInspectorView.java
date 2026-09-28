package com.example.blueprint;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.R;
import com.example.data.CanvasComponentEntity;

/**
 * Complete, non-stub Java implementation of the Bottom Property Inspector Dock View.
 * Inflates R.layout.inspector_dock and binds all component properties (label, width, height,
 * target file path, byte offset hex, ON/OFF payload hex, and background/text colors) to live updates.
 */
public class BottomPropertyInspectorView extends LinearLayout {

    public interface OnInspectorPropertyChangeListener {
        void onUpdateComponent(@NonNull CanvasComponentEntity updatedComponent);
        void onToggleAutoFixSize();
        void onDuplicateComponent(@NonNull CanvasComponentEntity component);
        void onDeleteComponent(@NonNull CanvasComponentEntity component);
        void onTestTriggerWrite(@NonNull CanvasComponentEntity component);
        void onCloseInspector();
    }

    private final TextView tvWidgetId;
    private final Button btnStateToggle;
    private final EditText etLabel;
    private final EditText etWidth;
    private final EditText etHeight;
    private final EditText etTargetFilePath;
    private final EditText etByteOffsetHex;
    private final EditText etOnPayloadHex;
    private final EditText etOffPayloadHex;
    private final EditText etBgColorHex;
    private final EditText etTextColorHex;

    private CanvasComponentEntity boundComponent;
    private OnInspectorPropertyChangeListener propertyListener;
    private boolean isBinding = false;

    public BottomPropertyInspectorView(@NonNull Context context) {
        this(context, null);
    }

    public BottomPropertyInspectorView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.inspector_dock, this, true);

        tvWidgetId = findViewById(R.id.tv_inspector_widget_id);
        btnStateToggle = findViewById(R.id.btn_inspector_state_toggle);
        etLabel = findViewById(R.id.et_component_label);
        etWidth = findViewById(R.id.et_component_width);
        etHeight = findViewById(R.id.et_component_height);
        etTargetFilePath = findViewById(R.id.et_target_file_path);
        etByteOffsetHex = findViewById(R.id.et_byte_offset_hex);
        etOnPayloadHex = findViewById(R.id.et_on_payload_hex);
        etOffPayloadHex = findViewById(R.id.et_off_payload_hex);
        etBgColorHex = findViewById(R.id.et_bg_color_hex);
        etTextColorHex = findViewById(R.id.et_text_color_hex);

        ImageButton btnDuplicate = findViewById(R.id.btn_inspector_duplicate);
        ImageButton btnDelete = findViewById(R.id.btn_inspector_delete);
        ImageButton btnSaveClose = findViewById(R.id.btn_inspector_save_close);
        Button chipAutoFix = findViewById(R.id.chip_auto_fix);
        Button chipInject = findViewById(R.id.chip_inject);

        android.text.TextWatcher autoSaveWatcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (!isBinding) {
                    commitCurrentFieldValues();
                }
            }
        };

        etLabel.addTextChangedListener(autoSaveWatcher);
        etWidth.addTextChangedListener(autoSaveWatcher);
        etHeight.addTextChangedListener(autoSaveWatcher);
        etTargetFilePath.addTextChangedListener(autoSaveWatcher);
        etByteOffsetHex.addTextChangedListener(autoSaveWatcher);
        etOnPayloadHex.addTextChangedListener(autoSaveWatcher);
        etOffPayloadHex.addTextChangedListener(autoSaveWatcher);
        etBgColorHex.addTextChangedListener(autoSaveWatcher);
        etTextColorHex.addTextChangedListener(autoSaveWatcher);

        btnStateToggle.setOnClickListener(v -> {
            if (boundComponent != null && propertyListener != null) {
                propertyListener.onTestTriggerWrite(boundComponent);
            }
        });

        btnDuplicate.setOnClickListener(v -> {
            if (boundComponent != null && propertyListener != null) {
                propertyListener.onDuplicateComponent(boundComponent);
            }
        });

        btnDelete.setOnClickListener(v -> {
            if (boundComponent != null && propertyListener != null) {
                propertyListener.onDeleteComponent(boundComponent);
            }
        });

        btnSaveClose.setOnClickListener(v -> {
            commitCurrentFieldValues();
            if (propertyListener != null) {
                propertyListener.onCloseInspector();
            }
        });

        chipAutoFix.setOnClickListener(v -> {
            if (propertyListener != null) {
                propertyListener.onToggleAutoFixSize();
            }
        });

        chipInject.setOnClickListener(v -> etByteOffsetHex.requestFocus());
    }

    public void setOnInspectorPropertyChangeListener(@Nullable OnInspectorPropertyChangeListener listener) {
        this.propertyListener = listener;
    }

    public void bindComponent(@NonNull CanvasComponentEntity component) {
        this.isBinding = true;
        try {
            this.boundComponent = component;
            tvWidgetId.setText("widget" + component.getId() + " (" + component.getLabel() + ")");
            boolean isOn = "1".equals(component.getCurrentValue()) || "true".equalsIgnoreCase(component.getCurrentValue());
            btnStateToggle.setText(isOn ? "ON" : "OFF");

            etLabel.setText(component.getLabel());
            etWidth.setText(String.valueOf(component.getWidthDp()));
            etHeight.setText(String.valueOf(component.getHeightDp()));
            etTargetFilePath.setText(component.getTargetFilePath());
            etByteOffsetHex.setText(component.getByteOffsetHex());
            etOnPayloadHex.setText(component.getOnPayloadHex());
            etOffPayloadHex.setText(component.getOffPayloadHex());
            etBgColorHex.setText(component.getBgColorHex());
            etTextColorHex.setText(component.getTextColorHex());
        } finally {
            this.isBinding = false;
        }
    }

    public void commitCurrentFieldValues() {
        if (boundComponent == null || propertyListener == null) return;
        int w = parseSafeInt(etWidth.getText().toString(), boundComponent.getWidthDp(), 40, 380);
        int h = parseSafeInt(etHeight.getText().toString(), boundComponent.getHeightDp(), 28, 320);

        CanvasComponentEntity updated = new CanvasComponentEntity(
                boundComponent.getId(),
                boundComponent.getProjectId(),
                boundComponent.getType(),
                etLabel.getText().toString().trim().isEmpty() ? boundComponent.getLabel() : etLabel.getText().toString().trim(),
                boundComponent.getPosXDp(),
                boundComponent.getPosYDp(),
                w,
                h,
                etBgColorHex.getText().toString().trim(),
                etTextColorHex.getText().toString().trim(),
                boundComponent.getCustomImagePath(),
                boundComponent.getSoundTrigger(),
                boundComponent.getCustomSoundPath(),
                boundComponent.getOffSoundTrigger(),
                boundComponent.getOffCustomSoundPath(),
                etTargetFilePath.getText().toString().trim(),
                etByteOffsetHex.getText().toString().trim(),
                etOnPayloadHex.getText().toString().trim(),
                etOffPayloadHex.getText().toString().trim(),
                boundComponent.getSliderMax(),
                boundComponent.getCurrentValue(),
                boundComponent.getLinkUrl()
        );
        this.boundComponent = updated;
        propertyListener.onUpdateComponent(updated);
    }

    private int parseSafeInt(String raw, int fallback, int min, int max) {
        try {
            int val = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, val));
        } catch (Exception e) {
            return fallback;
        }
    }
}
