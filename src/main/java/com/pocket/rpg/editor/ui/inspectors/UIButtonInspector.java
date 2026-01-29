package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UIButton;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.joml.Vector4f;

/**
 * Custom editor for UIButton component.
 *
 * Note: Runtime-only fields (onClick, onHover, onExit, hovered, pressed)
 * are not shown as they are set via code.
 */
@InspectorFor(UIButton.class)
public class UIButtonInspector extends CustomComponentInspector<UIButton> {

    @Override
    public boolean draw() {
        boolean changed = false;

        // === UI KEY ===
        changed |= UIKeyField.draw(component);

        // === TRANSITION SECTION ===
        ImGui.text(MaterialIcons.SwapHoriz + " Transition");
        ImGui.separator();

        ImGui.spacing();
        changed |= FieldEditors.drawEnum("Mode", component, "transitionMode", UIButton.TransitionMode.class);

        UIButton.TransitionMode mode = component.getTransitionMode();
        if (mode == null) mode = UIButton.TransitionMode.COLOR_TINT;

        ImGui.spacing();

        switch (mode) {
            case COLOR_TINT -> changed |= drawColorTintFields();
            case SPRITE_SWAP -> changed |= drawSpriteSwapFields();
        }

        // === INFO SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.pushStyleColor(ImGuiCol.Text, 0.6f, 0.6f, 0.6f, 1f);
        ImGui.text(MaterialIcons.Info + " Callbacks");
        ImGui.separator();
        ImGui.textWrapped("onClick, onHover, onExit callbacks are set via code at runtime.");
        ImGui.popStyleColor();

        return changed;
    }

    // ========================================
    // COLOR_TINT mode fields
    // ========================================

    private boolean drawColorTintFields() {
        boolean changed = false;

        // Sprite
        changed |= FieldEditors.drawAsset("Sprite", component, "sprite", Sprite.class, entity);
        changed |= drawResetSizeButton("sprite");

        // === TINTS ===
        ImGui.spacing();
        changed |= drawCustomTintsCheckbox();

        // Color
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text("Color");
        ImGui.sameLine(130);
        changed |= FieldEditors.drawColor("##color", component, "color");

        // Alpha
        changed |= drawAlphaSlider();

        return changed;
    }

    // ========================================
    // SPRITE_SWAP mode fields
    // ========================================

    private boolean drawSpriteSwapFields() {
        boolean changed = false;

        // Normal Sprite
        changed |= FieldEditors.drawAsset("Normal Sprite", component, "sprite", Sprite.class, entity);
        changed |= drawResetSizeButton("sprite");

        // Hovered Sprite
        ImGui.spacing();
        changed |= FieldEditors.drawAsset("Hovered Sprite", component, "hoveredSprite", Sprite.class, entity);

        // Pressed Sprite
        ImGui.spacing();
        changed |= FieldEditors.drawAsset("Pressed Sprite", component, "pressedSprite", Sprite.class, entity);

        // Tint Color
        ImGui.spacing();
        ImGui.text("Tint Color");
        ImGui.sameLine(130);
        changed |= FieldEditors.drawColor("##color", component, "color");

        // Alpha
        changed |= drawAlphaSlider();

        return changed;
    }

    // ========================================
    // Shared helpers
    // ========================================

    private boolean drawAlphaSlider() {
        Vector4f color = FieldEditors.getVector4f(component, "color");
        float[] alphaBuf = {color.w};
        ImGui.text("Alpha");
        ImGui.sameLine(130);
        ImGui.setNextItemWidth(-1);
        // TODO: Refactor to use FieldEditors for @Required support and undo
        if (ImGui.sliderFloat("##alpha", alphaBuf, 0f, 1f)) {
            color.w = alphaBuf[0];
            ComponentReflectionUtils.setFieldValue(component, "color", color);
            return true;
        }
        return false;
    }

    private boolean drawTintSlider(String label, String fieldName, Object currentValue) {
        float tint = currentValue instanceof Number n ? n.floatValue() : 0.1f;
        float[] tintBuf = {tint};

        ImGui.text(label);
        ImGui.sameLine(130);
        ImGui.setNextItemWidth(-1);
        // TODO: Refactor to use FieldEditors for @Required support and undo
        if (ImGui.sliderFloat("##" + fieldName, tintBuf, 0f, 0.5f, "%.2f")) {
            ComponentReflectionUtils.setFieldValue(component, fieldName, tintBuf[0]);
            return true;
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("0 = no change, 0.5 = 50% darker");
        }
        return false;
    }

    private void drawTintSliderReadOnly(String label, String fieldName, float value) {
        float[] tintBuf = {value};

        ImGui.text(label);
        ImGui.sameLine(130);
        ImGui.setNextItemWidth(-1);
        ImGui.sliderFloat("##" + fieldName, tintBuf, 0f, 0.5f, "%.2f");
    }

    private boolean drawResetSizeButton(String fieldName) {
        Object spriteObj = ComponentReflectionUtils.getFieldValue(component, fieldName);
        if (spriteObj instanceof Sprite sprite && sprite.getTexture() != null) {
            ImGui.sameLine();
            if (ImGui.smallButton(MaterialIcons.OpenInFull + "##resetSize_" + fieldName)) {
                return resetSizeToSprite(sprite);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(String.format("Reset size to sprite dimensions (%dx%d)",
                        (int) sprite.getWidth(), (int) sprite.getHeight()));
            }
        }
        return false;
    }

    private boolean drawCustomTintsCheckbox() {
        boolean changed = false;

        Object hoverTintObj = ComponentReflectionUtils.getFieldValue(component, "hoverTint");
        Object pressedTintObj = ComponentReflectionUtils.getFieldValue(component, "pressedTint");
        boolean hasCustomTints = hoverTintObj != null || pressedTintObj != null;

        // TODO: Refactor to use FieldEditors for @Required support and undo
        if (ImGui.checkbox("Custom Tints", hasCustomTints)) {
            if (hasCustomTints) {
                // Disable: set both to null (use GameConfig defaults)
                ComponentReflectionUtils.setFieldValue(component, "hoverTint", null);
                ComponentReflectionUtils.setFieldValue(component, "pressedTint", null);
            } else {
                // Enable: set both to current effective values
                ComponentReflectionUtils.setFieldValue(component, "hoverTint",
                        component.getEffectiveHoverTint());
                ComponentReflectionUtils.setFieldValue(component, "pressedTint",
                        component.getEffectivePressedTint());
            }
            changed = true;
            hasCustomTints = !hasCustomTints;
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("When disabled, uses GameConfig defaults.\nWhen enabled, uses custom values.");
        }

        if (hasCustomTints) {
            // Re-read after potential toggle
            hoverTintObj = ComponentReflectionUtils.getFieldValue(component, "hoverTint");
            pressedTintObj = ComponentReflectionUtils.getFieldValue(component, "pressedTint");

            changed |= drawTintSlider("Hover Darken", "hoverTint", hoverTintObj);
            changed |= drawTintSlider("Pressed Darken", "pressedTint", pressedTintObj);
        } else {
            // Show sliders disabled with effective (config default) values
            ImGui.beginDisabled();
            drawTintSliderReadOnly("Hover Darken", "hoverTint", component.getEffectiveHoverTint());
            drawTintSliderReadOnly("Pressed Darken", "pressedTint", component.getEffectivePressedTint());
            ImGui.endDisabled();
        }

        return changed;
    }

    private boolean resetSizeToSprite(Sprite sprite) {
        if (entity == null) return false;

        Component transform = entity.getComponent(UITransform.class);
        if (transform == null) return false;

        ComponentReflectionUtils.setFieldValue(transform, "width", sprite.getWidth());
        ComponentReflectionUtils.setFieldValue(transform, "height", sprite.getHeight());
        return true;
    }
}
