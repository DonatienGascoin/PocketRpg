package com.pocket.rpg.editor.utils;

import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.rendering.Sprite;
import com.pocket.rpg.serialization.ComponentData;
import imgui.ImGui;

import java.util.Map;

/**
 * Custom editor for UIImage component.
 */
public class UIImageEditor implements CustomComponentEditor {

    @Override
    public boolean draw(ComponentData data, EditorEntity entity) {
        Map<String, Object> fields = data.getFields();
        boolean changed = false;

        // Sprite
        ImGui.text(FontAwesomeIcons.Image + " Sprite");
        changed |= FieldEditors.drawAsset("##sprite", fields, "sprite", Sprite.class, data, entity);

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

        ImGui.spacing();

        // Color (tint)
        ImGui.text(FontAwesomeIcons.Palette + " Tint Color");
        changed |= FieldEditors.drawColor("##color", fields, "color");

        // Quick alpha slider
        ImGui.spacing();
        float alpha = getAlpha(fields);
        float[] alphaBuf = {alpha};
        ImGui.setNextItemWidth(-1);
        if (ImGui.sliderFloat("Alpha", alphaBuf, 0f, 1f)) {
            setAlpha(fields, alphaBuf[0]);
            changed = true;
        }

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