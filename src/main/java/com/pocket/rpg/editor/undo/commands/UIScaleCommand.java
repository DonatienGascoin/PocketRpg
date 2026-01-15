package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.undo.EditorCommand;
import org.joml.Vector2f;

/**
 * Undo command for scaling UITransform components.
 * Supports command merging for smooth drag operations.
 * Uses localScale values directly (not computed values from matchParentScale).
 */
public class UIScaleCommand implements EditorCommand {
    private final EditorGameObject entity;
    private final UITransform transform;
    private final Vector2f oldScale;
    private Vector2f newScale;

    public UIScaleCommand(EditorGameObject entity, UITransform transform,
                          Vector2f oldScale, Vector2f newScale) {
        this.entity = entity;
        this.transform = transform;
        this.oldScale = new Vector2f(oldScale);
        this.newScale = new Vector2f(newScale);
    }

    @Override
    public void execute() {
        transform.setScale2D(newScale.x, newScale.y);
    }

    @Override
    public void undo() {
        transform.setScale2D(oldScale.x, oldScale.y);
    }

    @Override
    public String getDescription() {
        return "Scale " + entity.getName();
    }

    @Override
    public boolean canMergeWith(EditorCommand other) {
        if (!(other instanceof UIScaleCommand cmd)) {
            return false;
        }
        return cmd.entity == this.entity && cmd.transform == this.transform;
    }

    @Override
    public void mergeWith(EditorCommand other) {
        if (other instanceof UIScaleCommand cmd) {
            this.newScale = new Vector2f(cmd.newScale);
        }
    }
}
