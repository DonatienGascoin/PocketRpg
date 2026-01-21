package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UIImage;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.ui.fields.EnumEditor;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.ui.fields.PrimitiveEditors;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import imgui.type.ImInt;
import org.joml.Vector4f;

/**
 * Custom editor for UIImage component.
 * Provides Unity-style Image Type selection with conditional fields.
 */
public class UIImageInspector extends CustomComponentInspector<UIImage> {

    @Override
    public boolean draw() {
        boolean changed = false;

        // Sprite
        ImGui.text(MaterialIcons.Image + " Sprite");
        changed |= FieldEditors.drawAsset("sprite", component, "sprite", Sprite.class, entity);

        // Reset size to sprite size button
        Object spriteObj = ComponentReflectionUtils.getFieldValue(component, "sprite");
        if (spriteObj instanceof Sprite sprite && sprite.getTexture() != null) {
            ImGui.sameLine();
            if (ImGui.smallButton(MaterialIcons.OpenInFull + "##resetSize")) {
                changed |= resetSizeToSprite(sprite);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(String.format("Reset size to sprite dimensions (%dx%d)",
                        (int) sprite.getWidth(), (int) sprite.getHeight()));
            }
        }

        ImGui.spacing();

        // Color (tint)
        ImGui.text(MaterialIcons.Palette + " Tint Color");
        changed |= FieldEditors.drawColor("color", component, "color");

        // Quick alpha slider
        ImGui.spacing();
        Vector4f color = FieldEditors.getVector4f(component, "color");
        float[] alphaBuf = {color.w};
        ImGui.setNextItemWidth(-1);
        // TODO: Refactor to use FieldEditors for @Required support and undo
        if (ImGui.sliderFloat("Alpha", alphaBuf, 0f, 1f)) {
            color.w = alphaBuf[0];
            ComponentReflectionUtils.setFieldValue(component, "color", color);
            changed = true;
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Image Type dropdown
        changed |= EnumEditor.drawEnum("Image Type", component, "imageType", UIImage.ImageType.class);

        UIImage.ImageType imageType = component.getImageType();

        // Conditional fields based on image type
        switch (imageType) {
            case SLICED -> changed |= drawSlicedOptions();
            case TILED -> changed |= drawTiledOptions();
            case FILLED -> changed |= drawFilledOptions();
            default -> {} // SIMPLE has no extra options
        }

        return changed;
    }

    private boolean drawSlicedOptions() {
        boolean changed = false;

        ImGui.spacing();
        ImGui.indent();

        // Fill Center checkbox
        changed |= PrimitiveEditors.drawBoolean("Fill Center", component, "fillCenter");

        // Check if sprite has 9-slice data
        Sprite sprite = component.getSprite();
        if (sprite != null && !sprite.hasNineSlice()) {
            ImGui.spacing();
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, MaterialIcons.Warning + " Sprite has no 9-slice data");
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Configure 9-slice borders in the Sprite Editor");
            }
        }

        ImGui.unindent();
        return changed;
    }

    private boolean drawTiledOptions() {
        boolean changed = false;

        ImGui.spacing();
        ImGui.indent();

        // Pixels Per Unit
        changed |= PrimitiveEditors.drawFloat("Pixels Per Unit", component, "pixelsPerUnit", 1.0f, 1f, 1000f);

        ImGui.unindent();
        return changed;
    }

    private boolean drawFilledOptions() {
        boolean changed = false;

        ImGui.spacing();
        ImGui.indent();

        // Fill Method
        changed |= EnumEditor.drawEnum("Fill Method", component, "fillMethod", UIImage.FillMethod.class);

        UIImage.FillMethod fillMethod = component.getFillMethod();

        // Fill Origin - options depend on fill method
        ImGui.spacing();
        changed |= drawFillOriginForMethod(fillMethod);

        // Fill Amount slider
        ImGui.spacing();
        changed |= PrimitiveEditors.drawFloatSlider("Fill Amount", component, "fillAmount", 0f, 1f);

        // Clockwise checkbox - only for radial methods
        if (fillMethod == UIImage.FillMethod.RADIAL_90 ||
            fillMethod == UIImage.FillMethod.RADIAL_180 ||
            fillMethod == UIImage.FillMethod.RADIAL_360) {
            ImGui.spacing();
            changed |= PrimitiveEditors.drawBoolean("Clockwise", component, "fillClockwise");
        }

        ImGui.unindent();
        return changed;
    }

    private boolean drawFillOriginForMethod(UIImage.FillMethod fillMethod) {
        // Get valid origins for this fill method
        UIImage.FillOrigin[] validOrigins = getValidOrigins(fillMethod);
        String[] originNames = new String[validOrigins.length];
        for (int i = 0; i < validOrigins.length; i++) {
            originNames[i] = formatOriginName(validOrigins[i]);
        }

        UIImage.FillOrigin currentOrigin = component.getFillOrigin();

        // Find current index (or default to 0 if current is invalid for this method)
        int currentIndex = 0;
        for (int i = 0; i < validOrigins.length; i++) {
            if (validOrigins[i] == currentOrigin) {
                currentIndex = i;
                break;
            }
        }

        ImInt selected = new ImInt(currentIndex);
        boolean changed = false;

        ImGui.pushID("fillOrigin");
        ImGui.text("Fill Origin");
        ImGui.sameLine();
        ImGui.setNextItemWidth(-1);
        if (ImGui.combo("##fillOrigin", selected, originNames)) {
            UIImage.FillOrigin newOrigin = validOrigins[selected.get()];
            component.setFillOrigin(newOrigin);
            changed = true;
        }
        ImGui.popID();

        return changed;
    }

    private UIImage.FillOrigin[] getValidOrigins(UIImage.FillMethod method) {
        return switch (method) {
            case HORIZONTAL -> new UIImage.FillOrigin[]{
                    UIImage.FillOrigin.LEFT,
                    UIImage.FillOrigin.RIGHT
            };
            case VERTICAL -> new UIImage.FillOrigin[]{
                    UIImage.FillOrigin.BOTTOM,
                    UIImage.FillOrigin.TOP
            };
            case RADIAL_90, RADIAL_180, RADIAL_360 -> new UIImage.FillOrigin[]{
                    UIImage.FillOrigin.BOTTOM_LEFT,
                    UIImage.FillOrigin.TOP_LEFT,
                    UIImage.FillOrigin.TOP_RIGHT,
                    UIImage.FillOrigin.BOTTOM_RIGHT
            };
        };
    }

    private String formatOriginName(UIImage.FillOrigin origin) {
        // Convert BOTTOM_LEFT to "Bottom Left"
        String name = origin.name().replace("_", " ");
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
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
