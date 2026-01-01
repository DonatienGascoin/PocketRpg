package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.scene.EditorEntity;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.UIEntityFactory;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddEntitiesCommand;
import com.pocket.rpg.editor.undo.commands.AddEntityCommand;
import com.pocket.rpg.serialization.ComponentData;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service for creating entities and UI elements.
 */
public class EntityCreationService {

    @Setter
    private EditorScene scene;

    @Setter
    private UIEntityFactory uiFactory;

    @Setter
    private HierarchySelectionHandler selectionHandler;

    public void createEmptyEntity() {
        Vector3f position = new Vector3f(0, 0, 0);
        int count = scene.getEntities().size();
        String name = "Entity_" + (count + 1);

        EditorEntity entity = new EditorEntity(name, position, false);
        entity.setOrder(getNextChildOrder(null));
        UndoManager.getInstance().execute(new AddEntityCommand(scene, entity));
        selectEntity(entity);
        scene.markDirty();
    }

    public void createUIElement(String uiType) {
        if (scene == null || uiFactory == null) return;
        EditorEntity entity = uiFactory.create(uiType, null);
        if (entity == null) return;

        EditorEntity parent = null;
        EditorEntity selected = scene.getSelectedEntity();

        if (selected != null) {
            if (selected.hasComponent(UITransform.class) || selected.hasComponent(UICanvas.class)) {
                parent = selected;
            } else {
                if (uiType.equals("Canvas")) {
                    parent = selected;
                } else {
                    EditorEntity canvas = findOrCreateCanvasUnder(selected);
                    parent = canvas;
                }
            }
        } else if (!uiType.equals("Canvas")) {
            parent = findOrCreateCanvas();
        }

        List<EditorEntity> entitiesToAdd = new ArrayList<>();

        if (parent != null) {
            entity.setParent(parent);
            entity.setOrder(parent.getChildren().size());
            entitiesToAdd.add(parent);
        } else {
            entity.setOrder(getNextChildOrder(null));
        }

        entitiesToAdd.add(entity);
        UndoManager.getInstance().execute(new AddEntitiesCommand(scene, entitiesToAdd));
        selectEntity(entity);
        scene.markDirty();
    }

    public void duplicateEntity(EditorEntity original) {
        Vector3f newPos = new Vector3f(original.getPosition()).add(1, 0, 0);
        EditorEntity copy;

        if (original.isScratchEntity()) {
            copy = new EditorEntity(original.getName() + "_copy", newPos, false);
            for (ComponentData comp : original.getComponents()) {
                ComponentData compCopy = new ComponentData(comp.getType());
                compCopy.getFields().putAll(comp.getFields());
                copy.addComponent(compCopy);
            }
        } else {
            copy = new EditorEntity(original.getPrefabId(), newPos);
            copy.setName(original.getName() + "_copy");
            for (ComponentData comp : original.getComponents()) {
                String componentType = comp.getType();
                for (String fieldName : original.getOverriddenFields(componentType)) {
                    Object value = original.getFieldValue(componentType, fieldName);
                    copy.setFieldValue(componentType, fieldName, value);
                }
            }
        }

        copy.setParent(original.getParent());
        copy.setOrder(original.getOrder() + 1);

        scene.addEntity(copy);
        selectEntity(copy);
        scene.markDirty();
    }

    private EditorEntity findOrCreateCanvasUnder(EditorEntity parent) {
        for (EditorEntity child : parent.getChildren()) {
            if (child.hasComponent("UICanvas")) {
                return child;
            }
        }

        EditorEntity canvas = uiFactory.create("Canvas", "UI Canvas");
        canvas.setParent(parent);
        canvas.setOrder(parent.getChildren().size());
        return canvas;
    }

    private EditorEntity findOrCreateCanvas() {
        for (EditorEntity entity : scene.getEntities()) {
            if (entity.hasComponent("UICanvas")) {
                return entity;
            }
        }

        EditorEntity canvas = uiFactory.create("Canvas", "UI Canvas");
        canvas.setOrder(getNextChildOrder(null));
        return canvas;
    }

    private int getNextChildOrder(EditorEntity parent) {
        if (parent == null) {
            return scene.getRootEntities().size();
        }
        return parent.getChildren().size();
    }

    private void selectEntity(EditorEntity entity) {
        if (selectionHandler != null) {
            scene.setSelection(Set.of(entity));
            // Selection handler will manage mode switching
        }
    }
}
