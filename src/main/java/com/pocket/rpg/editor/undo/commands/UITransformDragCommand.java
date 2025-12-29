package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.serialization.ComponentData;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command for undoing/redoing UI Designer drag operations.
 * Handles move, resize, anchor, and pivot changes.
 * Also captures child transforms for cascading resize.
 */
public class UITransformDragCommand implements EditorCommand {

    private final String description;
    private final EditorEntity entity;
    private final ComponentData transformData;

    // Main entity old/new values
    private final Vector2f oldOffset;
    private final float oldWidth;
    private final float oldHeight;
    private final Vector2f oldAnchor;
    private final Vector2f oldPivot;

    private final Vector2f newOffset;
    private final float newWidth;
    private final float newHeight;
    private final Vector2f newAnchor;
    private final Vector2f newPivot;

    // Child transforms (for cascading resize)
    private final List<ChildTransformState> childStates = new ArrayList<>();

    /**
     * Captures a child entity's transform state.
     */
    public static class ChildTransformState {
        public final EditorEntity entity;
        public final ComponentData transformData;
        public final Vector2f oldOffset;
        public final float oldWidth;
        public final float oldHeight;
        public Vector2f newOffset;
        public float newWidth;
        public float newHeight;

        public ChildTransformState(EditorEntity entity, ComponentData transformData,
                                   Vector2f offset, float width, float height) {
            this.entity = entity;
            this.transformData = transformData;
            this.oldOffset = new Vector2f(offset);
            this.oldWidth = width;
            this.oldHeight = height;
            // New values default to old values
            this.newOffset = new Vector2f(offset);
            this.newWidth = width;
            this.newHeight = height;
        }

        /**
         * Sets the new values after a change.
         */
        public void setNewValues(Vector2f offset, float width, float height) {
            this.newOffset = new Vector2f(offset);
            this.newWidth = width;
            this.newHeight = height;
        }

        /**
         * Captures new values from current transform state.
         */
        public void captureNewValues() {
            if (transformData != null) {
                Map<String, Object> fields = transformData.getFields();
                Object offsetObj = fields.get("offset");
                if (offsetObj instanceof Vector2f v) {
                    newOffset = new Vector2f(v);
                }
                Object widthObj = fields.get("width");
                if (widthObj instanceof Number n) {
                    newWidth = n.floatValue();
                }
                Object heightObj = fields.get("height");
                if (heightObj instanceof Number n) {
                    newHeight = n.floatValue();
                }
            }
        }

        public void applyOld() {
            if (transformData != null) {
                Map<String, Object> fields = transformData.getFields();
                fields.put("offset", new Vector2f(oldOffset));
                fields.put("width", oldWidth);
                fields.put("height", oldHeight);
            }
        }

        public void applyNew() {
            if (transformData != null) {
                Map<String, Object> fields = transformData.getFields();
                fields.put("offset", new Vector2f(newOffset));
                fields.put("width", newWidth);
                fields.put("height", newHeight);
            }
        }

        // Getters for inspection
        public EditorEntity getEntity() { return entity; }
        public Vector2f getOldOffset() { return oldOffset; }
        public float getOldWidth() { return oldWidth; }
        public float getOldHeight() { return oldHeight; }
    }

    /**
     * Creates a drag command.
     */
    public UITransformDragCommand(
            EditorEntity entity,
            ComponentData transformData,
            Vector2f oldOffset, float oldWidth, float oldHeight, Vector2f oldAnchor, Vector2f oldPivot,
            Vector2f newOffset, float newWidth, float newHeight, Vector2f newAnchor, Vector2f newPivot,
            String description
    ) {
        this.entity = entity;
        this.transformData = transformData;
        this.oldOffset = new Vector2f(oldOffset);
        this.oldWidth = oldWidth;
        this.oldHeight = oldHeight;
        this.oldAnchor = new Vector2f(oldAnchor);
        this.oldPivot = new Vector2f(oldPivot);
        this.newOffset = new Vector2f(newOffset);
        this.newWidth = newWidth;
        this.newHeight = newHeight;
        this.newAnchor = new Vector2f(newAnchor);
        this.newPivot = new Vector2f(newPivot);
        this.description = description;
    }

    /**
     * Adds a child state to track (call before execute).
     */
    public void addChildState(ChildTransformState state) {
        childStates.add(state);
    }

    /**
     * Captures new values for all children (call after drag completes).
     */
    public void captureChildNewValues() {
        for (ChildTransformState state : childStates) {
            state.captureNewValues();
        }
    }

    /**
     * Convenience constructor for move operations.
     */
    public static UITransformDragCommand move(
            EditorEntity entity,
            ComponentData transformData,
            Vector2f oldOffset, Vector2f newOffset,
            float width, float height, Vector2f anchor, Vector2f pivot
    ) {
        return new UITransformDragCommand(
                entity, transformData,
                oldOffset, width, height, anchor, pivot,
                newOffset, width, height, anchor, pivot,
                "Move " + entity.getName()
        );
    }

    /**
     * Convenience constructor for resize operations.
     */
    public static UITransformDragCommand resize(
            EditorEntity entity,
            ComponentData transformData,
            Vector2f oldOffset, float oldWidth, float oldHeight,
            Vector2f newOffset, float newWidth, float newHeight,
            Vector2f anchor, Vector2f pivot
    ) {
        return new UITransformDragCommand(
                entity, transformData,
                oldOffset, oldWidth, oldHeight, anchor, pivot,
                newOffset, newWidth, newHeight, anchor, pivot,
                "Resize " + entity.getName()
        );
    }

    /**
     * Convenience constructor for anchor changes.
     */
    public static UITransformDragCommand anchor(
            EditorEntity entity,
            ComponentData transformData,
            Vector2f oldAnchor, Vector2f oldOffset,
            Vector2f newAnchor, Vector2f newOffset,
            float width, float height, Vector2f pivot
    ) {
        return new UITransformDragCommand(
                entity, transformData,
                oldOffset, width, height, oldAnchor, pivot,
                newOffset, width, height, newAnchor, pivot,
                "Move Anchor " + entity.getName()
        );
    }

    /**
     * Convenience constructor for pivot changes.
     */
    public static UITransformDragCommand pivot(
            EditorEntity entity,
            ComponentData transformData,
            Vector2f oldPivot, Vector2f oldOffset,
            Vector2f newPivot, Vector2f newOffset,
            float width, float height, Vector2f anchor
    ) {
        return new UITransformDragCommand(
                entity, transformData,
                oldOffset, width, height, anchor, oldPivot,
                newOffset, width, height, anchor, newPivot,
                "Move Pivot " + entity.getName()
        );
    }

    @Override
    public void execute() {
        applyValues(newOffset, newWidth, newHeight, newAnchor, newPivot);
        for (ChildTransformState state : childStates) {
            state.applyNew();
        }
    }

    @Override
    public void undo() {
        applyValues(oldOffset, oldWidth, oldHeight, oldAnchor, oldPivot);
        for (ChildTransformState state : childStates) {
            state.applyOld();
        }
    }

    private void applyValues(Vector2f offset, float width, float height, Vector2f anchor, Vector2f pivot) {
        if (transformData == null) return;

        var fields = transformData.getFields();
        fields.put("offset", new Vector2f(offset));
        fields.put("width", width);
        fields.put("height", height);
        fields.put("anchor", new Vector2f(anchor));
        fields.put("pivot", new Vector2f(pivot));
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        // Don't merge drag commands - each drag is discrete
        return false;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        // Not used
    }

    /**
     * Check if this command actually changed anything.
     */
    public boolean hasChanges() {
        if (!oldOffset.equals(newOffset) ||
                oldWidth != newWidth ||
                oldHeight != newHeight ||
                !oldAnchor.equals(newAnchor) ||
                !oldPivot.equals(newPivot)) {
            return true;
        }
        // Check children
        for (ChildTransformState state : childStates) {
            if (!state.oldOffset.equals(state.newOffset) ||
                    state.oldWidth != state.newWidth ||
                    state.oldHeight != state.newHeight) {
                return true;
            }
        }
        return false;
    }
}