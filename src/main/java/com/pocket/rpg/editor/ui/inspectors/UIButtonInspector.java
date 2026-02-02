package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UIButton;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.ui.fields.FieldEditorUtils;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.CompoundCommand;
import com.pocket.rpg.editor.undo.commands.SetComponentFieldCommand;
import com.pocket.rpg.editor.undo.commands.UITransformDragCommand;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import org.joml.Vector2f;
import org.joml.Vector4f;

/**
 * Custom editor for UIButton component.
 *
 * Note: Runtime-only fields (onClick, onHover, onExit, hovered, pressed)
 * are not shown as they are set via code.
 */
@InspectorFor(UIButton.class)
public class UIButtonInspector extends CustomComponentInspector<UIButton> {

    // Undo tracking for continuous slider edits
    private Vector4f alphaEditStartColor;
    private Float hoverTintEditStart;
    private Float pressedTintEditStart;
    private Vector4f hoveredColorEditStart;
    private Vector4f pressedColorEditStart;

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

        // === STATE COLOR OVERRIDES ===
        ImGui.spacing();
        ImGui.spacing();
        ImGui.text(MaterialIcons.Palette + " State Colors");
        ImGui.separator();
        ImGui.spacing();

        changed |= drawStateColorOverride("Hovered Color", "hoveredColor");
        ImGui.spacing();
        changed |= drawStateColorOverride("Pressed Color", "pressedColor");

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

    private boolean drawStateColorOverride(String label, String fieldName) {
        boolean changed = false;
        Object colorObj = ComponentReflectionUtils.getFieldValue(component, fieldName);
        boolean[] enabledRef = { colorObj != null };

        // Checkbox goes between label and field
        FieldEditorUtils.setNextMiddleContent(() -> {
            if (ImGui.checkbox("##enable_" + fieldName, enabledRef[0])) {
                Vector4f oldValue = enabledRef[0] ? new Vector4f((Vector4f) ComponentReflectionUtils.getFieldValue(component, fieldName)) : null;
                Vector4f newValue;

                if (enabledRef[0]) {
                    newValue = null;
                } else {
                    newValue = new Vector4f(component.getColor());
                    newValue.w = Math.max(newValue.w, 0.5f);
                }

                ComponentReflectionUtils.setFieldValue(component, fieldName, newValue);
                if (entity != null) {
                    UndoManager.getInstance().push(
                            new SetComponentFieldCommand(component, fieldName, oldValue, newValue, entity)
                    );
                }
                enabledRef[0] = !enabledRef[0];
            }
        });

        Vector4f colorVal = enabledRef[0] ? (Vector4f) ComponentReflectionUtils.getFieldValue(component, fieldName) : null;

        if (enabledRef[0] && colorVal != null) {
            float[] colorBuf = {colorVal.x, colorVal.y, colorVal.z, colorVal.w};
            boolean[] colorChanged = {false};

            FieldEditorUtils.inspectorRow(label, () -> colorChanged[0] = ImGui.colorEdit4("##" + fieldName, colorBuf));

            if (ImGui.isItemActivated()) {
                if ("hoveredColor".equals(fieldName)) {
                    hoveredColorEditStart = new Vector4f(colorVal);
                } else {
                    pressedColorEditStart = new Vector4f(colorVal);
                }
            }

            if (colorChanged[0]) {
                colorVal.set(colorBuf[0], colorBuf[1], colorBuf[2], colorBuf[3]);
                ComponentReflectionUtils.setFieldValue(component, fieldName, colorVal);
                changed = true;
            }

            if (ImGui.isItemDeactivatedAfterEdit() && entity != null) {
                Vector4f startValue = "hoveredColor".equals(fieldName) ? hoveredColorEditStart : pressedColorEditStart;
                if (startValue != null) {
                    UndoManager.getInstance().push(
                            new SetComponentFieldCommand(component, fieldName, startValue, new Vector4f(colorVal), entity)
                    );
                }
                if ("hoveredColor".equals(fieldName)) hoveredColorEditStart = null;
                else pressedColorEditStart = null;
            }
        } else {
            FieldEditorUtils.inspectorRow(label, () -> {
                ImGui.beginDisabled();
                float[] dummy = {0, 0, 0, 0};
                ImGui.colorEdit4("##" + fieldName, dummy);
                ImGui.endDisabled();
            });
        }

        return changed;
    }

    private boolean drawAlphaSlider() {
        Vector4f color = FieldEditors.getVector4f(component, "color");
        float[] alphaBuf = {color.w};
        ImGui.text("Alpha");
        ImGui.sameLine(130);
        ImGui.setNextItemWidth(-1);
        boolean alphaChanged = ImGui.sliderFloat("##alpha", alphaBuf, 0f, 1f);
        if (ImGui.isItemActivated()) {
            alphaEditStartColor = new Vector4f(color);
        }
        if (alphaChanged) {
            color.w = alphaBuf[0];
            ComponentReflectionUtils.setFieldValue(component, "color", color);
        }
        if (ImGui.isItemDeactivatedAfterEdit() && alphaEditStartColor != null) {
            Vector4f newColor = new Vector4f(FieldEditors.getVector4f(component, "color"));
            if (entity != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, "color", alphaEditStartColor, newColor, entity)
                );
            }
            alphaEditStartColor = null;
        }
        return alphaChanged;
    }

    private boolean drawTintSlider(String label, String fieldName, Object currentValue) {
        float tint = currentValue instanceof Number n ? n.floatValue() : 0.1f;
        float[] tintBuf = {tint};

        ImGui.text(label);
        ImGui.sameLine(130);
        ImGui.setNextItemWidth(-1);
        boolean tintChanged = ImGui.sliderFloat("##" + fieldName, tintBuf, 0f, 0.5f, "%.2f");
        if (ImGui.isItemActivated()) {
            if ("hoverTint".equals(fieldName)) {
                hoverTintEditStart = tint;
            } else if ("pressedTint".equals(fieldName)) {
                pressedTintEditStart = tint;
            }
        }
        if (tintChanged) {
            ComponentReflectionUtils.setFieldValue(component, fieldName, tintBuf[0]);
        }
        if (ImGui.isItemDeactivatedAfterEdit() && entity != null) {
            Float startValue = "hoverTint".equals(fieldName) ? hoverTintEditStart : pressedTintEditStart;
            if (startValue != null) {
                UndoManager.getInstance().push(
                        new SetComponentFieldCommand(component, fieldName, startValue, tintBuf[0], entity)
                );
            }
            if ("hoverTint".equals(fieldName)) hoverTintEditStart = null;
            else pressedTintEditStart = null;
        }

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("0 = no change, 0.5 = 50% darker");
        }
        return tintChanged;
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

        if (ImGui.checkbox("Custom Tints", hasCustomTints)) {
            Object oldHoverTint = hoverTintObj;
            Object oldPressedTint = pressedTintObj;
            Object newHoverTint;
            Object newPressedTint;

            if (hasCustomTints) {
                // Disable: set both to null (use GameConfig defaults)
                newHoverTint = null;
                newPressedTint = null;
            } else {
                // Enable: set both to current effective values
                newHoverTint = component.getEffectiveHoverTint();
                newPressedTint = component.getEffectivePressedTint();
            }

            ComponentReflectionUtils.setFieldValue(component, "hoverTint", newHoverTint);
            ComponentReflectionUtils.setFieldValue(component, "pressedTint", newPressedTint);

            if (entity != null) {
                UndoManager.getInstance().push(
                        new CompoundCommand("Toggle Custom Tints",
                                new SetComponentFieldCommand(component, "hoverTint", oldHoverTint, newHoverTint, entity),
                                new SetComponentFieldCommand(component, "pressedTint", oldPressedTint, newPressedTint, entity)
                        )
                );
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

        Component transformComp = entity.getComponent(UITransform.class);
        if (!(transformComp instanceof UITransform uiTransform)) return false;

        float oldWidth = FieldEditors.getFloat(transformComp, "width", 100);
        float oldHeight = FieldEditors.getFloat(transformComp, "height", 100);
        float newWidth = sprite.getWidth();
        float newHeight = sprite.getHeight();

        Vector2f offset = new Vector2f(uiTransform.getOffset());
        Vector2f anchor = new Vector2f(uiTransform.getAnchor());
        Vector2f pivot = new Vector2f(uiTransform.getPivot());

        ComponentReflectionUtils.setFieldValue(transformComp, "width", newWidth);
        ComponentReflectionUtils.setFieldValue(transformComp, "height", newHeight);

        UndoManager.getInstance().push(
                UITransformDragCommand.resize(entity, transformComp,
                        offset, oldWidth, oldHeight,
                        offset, newWidth, newHeight,
                        anchor, pivot)
        );
        return true;
    }
}
