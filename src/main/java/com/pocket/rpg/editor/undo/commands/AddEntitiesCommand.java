package com.pocket.rpg.editor.undo.commands;

import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.EditorCommand;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * Command for adding multiple entities to a scene.
 */
public class AddEntitiesCommand implements EditorCommand {

    @AllArgsConstructor
    private static class EntityInfo {
        public EditorGameObject parent;
        public int order;
    }

    private final EditorScene scene;
    private final EditorGameObject[] entities;
    private final EntityInfo[] entityInfos;

    public AddEntitiesCommand(EditorScene scene, EditorGameObject... entities) {
        this.scene = scene;
        this.entities = entities;
        this.entityInfos = new EntityInfo[entities.length];
    }

    public AddEntitiesCommand(EditorScene scene, List<EditorGameObject> entities) {
        this.scene = scene;
        this.entities = entities.toArray(new EditorGameObject[0]);
        this.entityInfos = new EntityInfo[entities.size()];
    }

    @Override
    public void execute() {
        for (int i = 0; i < entities.length; i++) {
            var entity = entities[i];
            if (entityInfos[i] != null) {
                entity.setParent(entityInfos[i].parent);
                entity.setOrder(entityInfos[i].order);
            } else {
                entityInfos[i] = new EntityInfo(entity.getParent(), entity.getOrder());
            }
            scene.addEntity(entity);
        }
    }

    @Override
    public void undo() {
        for (var entity : entities) {
            scene.removeEntity(entity);
        }
    }

    @Override
    public String getDescription() {
        return "Add " + entities.length + " entities";
    }
}