package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.serialization.ComponentData;
import imgui.ImGui;
import imgui.flag.ImGuiCol;

import java.util.Map;

/**
 * Custom editor for UIButton component.
 *
 * Note: Runtime-only fields (onClick, onHover, onExit, hovered, pressed) 
 * are not shown as they are set via code.
 */
public class UIButtonEditor implements CustomComponentEditor {

    @Override
    public boolean draw(ComponentData data, EditorEntity entity) {
        Map<String, Object> fields = data.getFields();
        boolean changed = false;

        // === APPEARANCE SECTION ===
        ImGui.text(FontAwesomeIcons.Palette + " Appearance");
        ImGui.separator();

        // Sprite
        ImGui.spacing();
        changed |= FieldEditors.drawAsset("Sprite", fields, "sprite", Sprite.class, data, entity);

        // Reset size to sprite size button
        Object spriteObj = fields.get("sprite");
        if (spriteObj instanceof Sprite sprite && sprite.getTexture() != null) {
            ImGui.sameLine();
            if (ImGui.smallButton(FontAwesomeIcons.Expand + "##resetSize")) {
                changed |= resetSizeToSprite(entity, sprite);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(String.format("Reset size to sprite dimensions (%dx%d)",
                        (int) sprite.getWidth(), (int) sprite.getHeight()));
            }
        }

        // Color
        ImGui.spacing();
        ImGui.text("Color");
        ImGui.sameLine(130);
        changed |= FieldEditors.drawColor("##color", fields, "color");

        // Alpha slider
        float alpha = getAlpha(fields);
        float[] alphaBuf = {alpha};
        ImGui.text("Alpha");
        ImGui.sameLine(130);
        ImGui.setNextItemWidth(-1);
        if (ImGui.sliderFloat("##alpha", alphaBuf, 0f, 1f)) {
            setAlpha(fields, alphaBuf[0]);
            changed = true;
        }

        // === HOVER SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(FontAwesomeIcons.HandPointer + " Hover Effect");
        ImGui.separator();

        // Hover tint
        ImGui.spacing();
        Object hoverTintObj = fields.get("hoverTint");
        boolean hasCustomTint = hoverTintObj != null;

        // Checkbox to enable custom tint
        if (ImGui.checkbox("Custom Hover Tint", hasCustomTint)) {
            if (hasCustomTint) {
                fields.put("hoverTint", null);
            } else {
                fields.put("hoverTint", 0.1f);
            }
            changed = true;
            hasCustomTint = !hasCustomTint;
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("When disabled, uses GameConfig default.\nWhen enabled, uses custom value.");
        }

        if (hasCustomTint) {
            float tint = hoverTintObj instanceof Number n ? n.floatValue() : 0.1f;
            float[] tintBuf = {tint};

            ImGui.text("Darken Amount");
            ImGui.sameLine(130);
            ImGui.setNextItemWidth(-1);
            if (ImGui.sliderFloat("##hoverTint", tintBuf, 0f, 0.5f, "%.2f")) {
                fields.put("hoverTint", tintBuf[0]);
                changed = true;
            }

            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("0 = no change, 0.5 = 50% darker on hover");
            }
        } else {
            ImGui.textDisabled("Using GameConfig default");
        }

        // === INFO SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1f);
        ImGui.text(FontAwesomeIcons.InfoCircle + " Callbacks");
        ImGui.separator();
        ImGui.textWrapped("onClick, onHover, onExit callbacks are set via code at runtime.");
        ImGui.popStyleColor();

        return changed;
    }

    private boolean resetSizeToSprite(EditorEntity entity, Sprite sprite) {
        if (entity == null) return false;

        var transform = entity.getComponentByType("UITransform");
        if (transform == null) return false;

        Map<String, Object> transformFields = transform.getFields();
        transformFields.put("width", sprite.getWidth());
        transformFields.put("height", sprite.getHeight());
        return true;
    }

    private float getAlpha(Map<String, Object> fields) {
        return FieldEditors.getVector4f(fields, "color").w;
    }

    private void setAlpha(Map<String, Object> fields, float alpha) {
        var color = FieldEditors.getVector4f(fields, "color");
        color.w = alpha;
        fields.put("color", color);
    }
}