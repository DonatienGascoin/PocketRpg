package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.serialization.ComponentData;
import com.pocket.rpg.editor.undo.EditorCommand;

/**
 * Command for changing a component field value.
 */
public class SetComponentFieldCommand implements EditorCommand {

    private final ComponentData component;
    private final String fieldName;
    private final Object oldValue;
    private Object newValue;

    // Optional entity reference for cache invalidation
    private final EditorEntity entity;

    public SetComponentFieldCommand(ComponentData component, String fieldName,
                                    Object oldValue, Object newValue) {
        this(component, fieldName, oldValue, newValue, null);
    }

    public SetComponentFieldCommand(ComponentData component, String fieldName,
                                    Object oldValue, Object newValue, EditorEntity entity) {
        this.component = component;
        this.fieldName = fieldName;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.entity = entity;
    }

    @Override
    public void execute() {
        component.getFields().put(fieldName, newValue);
        invalidatePreviewIfNeeded();
    }

    @Override
    public void undo() {
        if (oldValue == null) {
            component.getFields().remove(fieldName);
        } else {
            component.getFields().put(fieldName, oldValue);
        }
        invalidatePreviewIfNeeded();
    }

    private void invalidatePreviewIfNeeded() {
        if (entity != null && isPreviewAffectingField()) {
            entity.refreshPreviewCache();
        }
    }

    private boolean isPreviewAffectingField() {
        return "sprite".equals(fieldName) &&
                "SpriteRenderer".equals(component.getSimpleName());
    }

    @Override
    public String getDescription() {
        return "Change " + fieldName;
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof SetComponentFieldCommand cmd)) {
            return false;
        }

        return cmd.component == this.component &&
                cmd.fieldName.equals(this.fieldName);
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof SetComponentFieldCommand cmd) {
            this.newValue = cmd.newValue;
        }
    }
}