package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.core.FontAwesomeIcons;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import imgui.ImGui;
import org.joml.Vector4f;

/**
 * Custom editor for UIImage component.
 */
public class UIImageInspector implements CustomComponentInspector {

    @Override
    public boolean draw(Component component, EditorGameObject entity) {
        boolean changed = false;

        // Sprite
        ImGui.text(FontAwesomeIcons.Image + " Sprite");
        changed |= FieldEditors.drawAsset("##sprite", component, "sprite", Sprite.class, entity);

        // Reset size to sprite size button
        Object spriteObj = ComponentReflectionUtils.getFieldValue(component, "sprite");
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
        changed |= FieldEditors.drawColor("##color", component, "color");

        // Quick alpha slider
        ImGui.spacing();
        Vector4f color = FieldEditors.getVector4f(component, "color");
        float[] alphaBuf = {color.w};
        ImGui.setNextItemWidth(-1);
        if (ImGui.sliderFloat("Alpha", alphaBuf, 0f, 1f)) {
            color.w = alphaBuf[0];
            ComponentReflectionUtils.setFieldValue(component, "color", color);
            changed = true;
        }

        return changed;
    }

    private boolean resetSizeToSprite(EditorGameObject entity, Sprite sprite) {
        if (entity == null) return false;

        Component transform = entity.getComponent(UITransform.class);
        if (transform == null) return false;

        ComponentReflectionUtils.setFieldValue(transform, "width", sprite.getWidth());
        ComponentReflectionUtils.setFieldValue(transform, "height", sprite.getHeight());
        return true;
    }
}
