package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.serialization.ComponentData;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.joml.Vector2f;

import java.util.Map;

/**
 * Custom editor for UITransform component.
 * <p>
 * Features:
 * - 9-button anchor preset grid
 * - 9-button pivot preset grid
 * - Offset drag fields
 * - Size drag fields with aspect lock option
 */
public class UITransformEditor implements CustomComponentEditor {

    // Anchor/Pivot preset positions
    private static final float[][] PRESETS = {
            {0f, 0f},     // Top-Left
            {0.5f, 0f},   // Top-Center
            {1f, 0f},     // Top-Right
            {0f, 0.5f},   // Middle-Left
            {0.5f, 0.5f}, // Center
            {1f, 0.5f},   // Middle-Right
            {0f, 1f},     // Bottom-Left
            {0.5f, 1f},   // Bottom-Center
            {1f, 1f}      // Bottom-Right
    };

    // Preset labels (short)
    private static final String[] PRESET_LABELS = {
            "TL", "T", "TR",
            "L", "C", "R",
            "BL", "B", "BR"
    };

    // Preset tooltips
    private static final String[] PRESET_TOOLTIPS = {
            "Top-Left (0, 0)",
            "Top-Center (0.5, 0)",
            "Top-Right (1, 0)",
            "Middle-Left (0, 0.5)",
            "Center (0.5, 0.5)",
            "Middle-Right (1, 0.5)",
            "Bottom-Left (0, 1)",
            "Bottom-Center (0.5, 1)",
            "Bottom-Right (1, 1)"
    };

    private boolean lockAspectRatio = false;
    private float lastWidth = 0;
    private float lastHeight = 0;

    @Override
    public boolean draw(ComponentData data, EditorEntity entity) {
        Map<String, Object> fields = data.getFields();
        boolean changed = false;

        // Section: Anchor
        ImGui.text(FontAwesomeIcons.Anchor + " Anchor");
        ImGui.sameLine(100);
        Vector2f anchor = FieldEditors.getVector2f(fields, "anchor");
        ImGui.textDisabled(String.format("(%.2f, %.2f)", anchor.x, anchor.y));

        changed |= drawPresetGrid("anchor", fields, anchor);

        ImGui.spacing();

        // Section: Pivot
        ImGui.text(FontAwesomeIcons.Crosshairs + " Pivot");
        ImGui.sameLine(100);
        Vector2f pivot = FieldEditors.getVector2f(fields, "pivot");
        ImGui.textDisabled(String.format("(%.2f, %.2f)", pivot.x, pivot.y));

        changed |= drawPresetGrid("pivot", fields, pivot);

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Section: Offset
        ImGui.text(FontAwesomeIcons.ArrowsAlt + " Offset");
        changed |= FieldEditors.drawVector2f("##offset", fields, "offset", 1.0f);

        ImGui.sameLine();
        if (ImGui.smallButton("Reset##offset")) {
            fields.put("offset", new Vector2f(0, 0));
            changed = true;
        }

        ImGui.spacing();

        // Section: Size
        ImGui.text(FontAwesomeIcons.ExpandAlt + " Size");

        // Lock aspect ratio toggle
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 25);
        if (ImGui.smallButton(lockAspectRatio ? FontAwesomeIcons.Lock : FontAwesomeIcons.LockOpen)) {
            lockAspectRatio = !lockAspectRatio;
            if (lockAspectRatio) {
                lastWidth = FieldEditors.getFloat(fields, "width", 100);
                lastHeight = FieldEditors.getFloat(fields, "height", 100);
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(lockAspectRatio ? "Aspect ratio locked" : "Lock aspect ratio");
        }

        // Width
        float width = FieldEditors.getFloat(fields, "width", 100);
        float height = FieldEditors.getFloat(fields, "height", 100);

        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() * 0.5f - 30);
        float[] widthBuf = {width};
        if (ImGui.dragFloat("W", widthBuf, 1.0f, 1.0f, 10000f)) {
            float newWidth = Math.max(1, widthBuf[0]);
            fields.put("width", newWidth);
            changed = true;

            if (lockAspectRatio && lastWidth > 0) {
                float ratio = newWidth / lastWidth;
                float newHeight = lastHeight * ratio;
                fields.put("height", newHeight);
                lastWidth = newWidth;
                lastHeight = newHeight;
            }
        }

        ImGui.sameLine();

        // Height
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX() - 20);
        float[] heightBuf = {height};
        if (ImGui.dragFloat("H", heightBuf, 1.0f, 1.0f, 10000f)) {
            float newHeight = Math.max(1, heightBuf[0]);
            fields.put("height", newHeight);
            changed = true;

            if (lockAspectRatio && lastHeight > 0) {
                float ratio = newHeight / lastHeight;
                float newWidth = lastWidth * ratio;
                fields.put("width", newWidth);
                lastWidth = newWidth;
                lastHeight = newHeight;
            }
        }

        // Quick size presets
        ImGui.spacing();
        if (ImGui.smallButton("32x32")) {
            fields.put("width", 32f);
            fields.put("height", 32f);
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.smallButton("64x64")) {
            fields.put("width", 64f);
            fields.put("height", 64f);
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.smallButton("128x128")) {
            fields.put("width", 128f);
            fields.put("height", 128f);
            changed = true;
        }
        ImGui.sameLine();
        if (ImGui.smallButton("100%##fullsize")) {
            // Would need canvas size - placeholder
            ImGui.setTooltip("Match parent size (not implemented)");
        }

        return changed;
    }

    /**
     * Draws a 3x3 preset grid for anchor or pivot.
     */
    private boolean drawPresetGrid(String fieldKey, Map<String, Object> fields, Vector2f current) {
        boolean changed = false;

        float buttonSize = 24;
        float spacing = 2;

        ImGui.pushID(fieldKey + "_grid");

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                float presetX = PRESETS[idx][0];
                float presetY = PRESETS[idx][1];

                boolean isSelected = Math.abs(current.x - presetX) < 0.01f &&
                                     Math.abs(current.y - presetY) < 0.01f;

                if (col > 0) ImGui.sameLine(0, spacing);

                // Highlight selected
                if (isSelected) {
                    ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.6f, 0.9f, 1.0f);
                    ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.7f, 1.0f, 1.0f);
                }

                ImGui.pushID(idx);
                if (ImGui.button(PRESET_LABELS[idx], buttonSize, buttonSize)) {
                    fields.put(fieldKey, new Vector2f(presetX, presetY));
                    changed = true;
                }
                ImGui.popID();

                if (isSelected) {
                    ImGui.popStyleColor(2);
                }

                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(PRESET_TOOLTIPS[idx]);
                }
            }
        }

        // Custom value input (collapsed)
        ImGui.sameLine(0, 10);
        ImGui.pushID("custom");
        if (ImGui.smallButton("...")) {
            ImGui.openPopup("custom_value");
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Enter custom value");
        }

        if (ImGui.beginPopup("custom_value")) {
            ImGui.text("Custom " + (fieldKey.equals("anchor") ? "Anchor" : "Pivot"));
            ImGui.separator();

            float[] xBuf = {current.x};
            float[] yBuf = {current.y};

            ImGui.setNextItemWidth(80);
            if (ImGui.sliderFloat("X", xBuf, 0f, 1f)) {
                current.x = xBuf[0];
                fields.put(fieldKey, new Vector2f(current));
                changed = true;
            }

            ImGui.setNextItemWidth(80);
            if (ImGui.sliderFloat("Y", yBuf, 0f, 1f)) {
                current.y = yBuf[0];
                fields.put(fieldKey, new Vector2f(current));
                changed = true;
            }

            ImGui.endPopup();
        }
        ImGui.popID();

        ImGui.popID();
        return changed;
    }
}
