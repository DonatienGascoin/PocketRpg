package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UIButton;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
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
public class UIButtonInspector extends CustomComponentInspector<UIButton> {

    @Override
    public boolean draw() {
        boolean changed = false;

        // === APPEARANCE SECTION ===
        ImGui.text(FontAwesomeIcons.Palette + " Appearance");
        ImGui.separator();

        // Sprite
        ImGui.spacing();
        changed |= FieldEditors.drawAsset("Sprite", component, "sprite", Sprite.class, entity);

        // Reset size to sprite size button
        Object spriteObj = ComponentReflectionUtils.getFieldValue(component, "sprite");
        if (spriteObj instanceof Sprite sprite && sprite.getTexture() != null) {
            ImGui.sameLine();
            if (ImGui.smallButton(FontAwesomeIcons.Expand + "##resetSize")) {
                changed |= resetSizeToSprite(sprite);
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
        changed |= FieldEditors.drawColor("##color", component, "color");

        // Alpha slider
        Vector4f color = FieldEditors.getVector4f(component, "color");
        float[] alphaBuf = {color.w};
        ImGui.text("Alpha");
        ImGui.sameLine(130);
        ImGui.setNextItemWidth(-1);
        // TODO: Refactor to use FieldEditors for @Required support and undo
        if (ImGui.sliderFloat("##alpha", alphaBuf, 0f, 1f)) {
            color.w = alphaBuf[0];
            ComponentReflectionUtils.setFieldValue(component, "color", color);
            changed = true;
        }

        // === HOVER SECTION ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(FontAwesomeIcons.HandPointer + " Hover Effect");
        ImGui.separator();

        // Hover tint
        ImGui.spacing();
        Object hoverTintObj = ComponentReflectionUtils.getFieldValue(component, "hoverTint");
        boolean hasCustomTint = hoverTintObj != null;

        // Checkbox to enable custom tint
        // TODO: Refactor to use FieldEditors for @Required support and undo
        if (ImGui.checkbox("Custom Hover Tint", hasCustomTint)) {
            if (hasCustomTint) {
                ComponentReflectionUtils.setFieldValue(component, "hoverTint", null);
            } else {
                ComponentReflectionUtils.setFieldValue(component, "hoverTint", 0.1f);
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
            // TODO: Refactor to use FieldEditors for @Required support and undo
            if (ImGui.sliderFloat("##hoverTint", tintBuf, 0f, 0.5f, "%.2f")) {
                ComponentReflectionUtils.setFieldValue(component, "hoverTint", tintBuf[0]);
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

    private boolean resetSizeToSprite(Sprite sprite) {
        if (entity == null) return false;

        Component transform = entity.getComponent(UITransform.class);
        if (transform == null) return false;

        ComponentReflectionUtils.setFieldValue(transform, "width", sprite.getWidth());
        ComponentReflectionUtils.setFieldValue(transform, "height", sprite.getHeight());
        return true;
    }
}
