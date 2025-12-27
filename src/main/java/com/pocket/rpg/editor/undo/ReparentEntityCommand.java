package com.pocket.rpg.editor.undo;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Command to change an entity's parent and/or position.
 */
public class ReparentEntityCommand implements EditorCommand {

    private final EditorScene scene;
    private final EditorEntity entity;
    private final EditorEntity newParent;
    private final int insertIndex;

    private EditorEntity oldParent;
    private int oldIndex;

    public ReparentEntityCommand(EditorScene scene, EditorEntity entity, 
                                  EditorEntity newParent, int insertIndex) {
        this.scene = scene;
        this.entity = entity;
        this.newParent = newParent;
        this.insertIndex = insertIndex;
    }

    @Override
    public void execute() {
        oldParent = entity.getParent();
        
        // Save actual position in sibling list (not just order field)
        List<EditorEntity> oldSiblings;
        if (oldParent == null) {
            oldSiblings = scene.getRootEntities();
        } else {
            oldSiblings = new ArrayList<>(oldParent.getChildren());
        }
        oldSiblings.sort(Comparator.comparingInt(EditorEntity::getOrder));
        oldIndex = oldSiblings.indexOf(entity);
        if (oldIndex == -1) oldIndex = 0;

        scene.insertEntityAtPosition(entity, newParent, insertIndex);
        scene.markDirty();
    }

    @Override
    public void undo() {
        scene.insertEntityAtPosition(entity, oldParent, oldIndex);
        scene.markDirty();
    }

    @Override
    public String getDescription() {
        if (newParent == null && oldParent != null) {
            return "Unparent " + entity.getName();
        } else if (newParent != null && oldParent != newParent) {
            return "Reparent " + entity.getName() + " to " + newParent.getName();
        }
        return "Reorder " + entity.getName();
    }
}
