package com.pocket.rpg.editor.undo;

import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Command to reorder an entity among its siblings.
 */
public class ReorderEntityCommand implements EditorCommand {

    private final EditorScene scene;
    private final EditorEntity entity;
    private final int newIndex;

    private int oldIndex;

    public ReorderEntityCommand(EditorScene scene, EditorEntity entity, int newIndex) {
        this.scene = scene;
        this.entity = entity;
        this.newIndex = newIndex;
    }

    @Override
    public void execute() {
        // Save actual position in sibling list
        EditorEntity parent = entity.getParent();
        List<EditorEntity> siblings;
        if (parent == null) {
            siblings = scene.getRootEntities();
        } else {
            siblings = new ArrayList<>(parent.getChildren());
        }
        siblings.sort(Comparator.comparingInt(EditorEntity::getOrder));
        oldIndex = siblings.indexOf(entity);
        if (oldIndex == -1) oldIndex = 0;

        scene.insertEntityAtPosition(entity, parent, newIndex);
        scene.markDirty();
    }

    @Override
    public void undo() {
        scene.insertEntityAtPosition(entity, entity.getParent(), oldIndex);
        scene.markDirty();
    }

    @Override
    public String getDescription() {
        return "Reorder " + entity.getName();
    }
}
