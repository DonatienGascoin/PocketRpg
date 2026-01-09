package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import lombok.Getter;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Command for undoing/redoing UI Designer drag operations.
 * Handles move, resize, anchor, and pivot changes.
 * Also captures child transforms for cascading resize.
 */
public class UITransformDragCommand implements EditorCommand {

    private final String description;
    private final EditorGameObject entity;
    private final Component transform;

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
        // Getters for inspection
        @Getter
        public final EditorGameObject entity;
        public final Component transform;
        @Getter
        public final Vector2f oldOffset;
        @Getter
        public final float oldWidth;
        @Getter
        public final float oldHeight;
        public Vector2f newOffset;
        public float newWidth;
        public float newHeight;

        public ChildTransformState(EditorGameObject entity, Component transform,
                                   Vector2f offset, float width, float height) {
            this.entity = entity;
            this.transform = transform;
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
            if (transform != null) {
                Object offsetObj = ComponentReflectionUtils.getFieldValue(transform, "offset");
                if (offsetObj instanceof Vector2f v) {
                    newOffset = new Vector2f(v);
                }
                newWidth = ComponentReflectionUtils.getFloat(transform, "width", newWidth);
                newHeight = ComponentReflectionUtils.getFloat(transform, "height", newHeight);
            }
        }

        public void applyOld() {
            if (transform != null) {
                ComponentReflectionUtils.setFieldValue(transform, "offset", new Vector2f(oldOffset));
                ComponentReflectionUtils.setFieldValue(transform, "width", oldWidth);
                ComponentReflectionUtils.setFieldValue(transform, "height", oldHeight);
            }
        }

        public void applyNew() {
            if (transform != null) {
                ComponentReflectionUtils.setFieldValue(transform, "offset", new Vector2f(newOffset));
                ComponentReflectionUtils.setFieldValue(transform, "width", newWidth);
                ComponentReflectionUtils.setFieldValue(transform, "height", newHeight);
            }
        }
    }

    /**
     * Creates a drag command.
     */
    public UITransformDragCommand(
            EditorGameObject entity,
            Component transform,
            Vector2f oldOffset, float oldWidth, float oldHeight, Vector2f oldAnchor, Vector2f oldPivot,
            Vector2f newOffset, float newWidth, float newHeight, Vector2f newAnchor, Vector2f newPivot,
            String description
    ) {
        this.entity = entity;
        this.transform = transform;
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
            EditorGameObject entity,
            Component transform,
            Vector2f oldOffset, Vector2f newOffset,
            float width, float height, Vector2f anchor, Vector2f pivot
    ) {
        return new UITransformDragCommand(
                entity, transform,
                oldOffset, width, height, anchor, pivot,
                newOffset, width, height, anchor, pivot,
                "Move " + entity.getName()
        );
    }

    /**
     * Convenience constructor for resize operations.
     */
    public static UITransformDragCommand resize(
            EditorGameObject entity,
            Component transform,
            Vector2f oldOffset, float oldWidth, float oldHeight,
            Vector2f newOffset, float newWidth, float newHeight,
            Vector2f anchor, Vector2f pivot
    ) {
        return new UITransformDragCommand(
                entity, transform,
                oldOffset, oldWidth, oldHeight, anchor, pivot,
                newOffset, newWidth, newHeight, anchor, pivot,
                "Resize " + entity.getName()
        );
    }

    /**
     * Convenience constructor for anchor changes.
     */
    public static UITransformDragCommand anchor(
            EditorGameObject entity,
            Component transform,
            Vector2f oldAnchor, Vector2f oldOffset,
            Vector2f newAnchor, Vector2f newOffset,
            float width, float height, Vector2f pivot
    ) {
        return new UITransformDragCommand(
                entity, transform,
                oldOffset, width, height, oldAnchor, pivot,
                newOffset, width, height, newAnchor, pivot,
                "Move Anchor " + entity.getName()
        );
    }

    /**
     * Convenience constructor for pivot changes.
     */
    public static UITransformDragCommand pivot(
            EditorGameObject entity,
            Component transform,
            Vector2f oldPivot, Vector2f oldOffset,
            Vector2f newPivot, Vector2f newOffset,
            float width, float height, Vector2f anchor
    ) {
        return new UITransformDragCommand(
                entity, transform,
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
        if (transform == null) return;

        ComponentReflectionUtils.setFieldValue(transform, "offset", new Vector2f(offset));
        ComponentReflectionUtils.setFieldValue(transform, "width", width);
        ComponentReflectionUtils.setFieldValue(transform, "height", height);
        ComponentReflectionUtils.setFieldValue(transform, "anchor", new Vector2f(anchor));
        ComponentReflectionUtils.setFieldValue(transform, "pivot", new Vector2f(pivot));
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
