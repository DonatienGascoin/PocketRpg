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
        entity.setOrder(getNextRootOrder());
        UndoManager.getInstance().execute(new AddEntityCommand(scene, entity));

        selectAndSwitchMode(entity);
        scene.markDirty();
    }

    public void createUIElement(String uiType) {
        if (scene == null || uiFactory == null) return;

        EditorEntity newEntity = uiFactory.create(uiType, null);
        if (newEntity == null) return;

        List<EditorEntity> newEntitiesToAdd = new ArrayList<>();
        EditorEntity parent = determineParentForUIElement(uiType, newEntitiesToAdd);

        // Set up parent-child relationship and order
        if (parent != null) {
            newEntity.setParent(parent);
            // Count existing children + any new entities that will become siblings
            int existingChildren = parent.getChildren().size();
            int newSiblings = countEntitiesWithParent(newEntitiesToAdd, parent);
            newEntity.setOrder(existingChildren + newSiblings);
        } else {
            newEntity.setOrder(getNextRootOrder() + newEntitiesToAdd.size());
        }

        newEntitiesToAdd.add(newEntity);

        // Single undo command for all new entities
        if (newEntitiesToAdd.size() == 1) {
            UndoManager.getInstance().execute(new AddEntityCommand(scene, newEntity));
        } else {
            UndoManager.getInstance().execute(new AddEntitiesCommand(scene, newEntitiesToAdd));
        }

        selectAndSwitchMode(newEntity);
        scene.markDirty();
    }

    /**
     * Determines the parent for a new UI element.
     * May create a new canvas if needed (added to newEntitiesToAdd).
     *
     * @param uiType            Type of UI element being created
     * @param newEntitiesToAdd  List to add any newly created parent entities (e.g., canvas)
     * @return Parent entity, or null if creating at root level
     */
    private EditorEntity determineParentForUIElement(String uiType, List<EditorEntity> newEntitiesToAdd) {
        EditorEntity selected = scene.getSelectedEntity();
        boolean isCreatingCanvas = "Canvas".equals(uiType);

        // Case 1: Something is selected
        if (selected != null) {
            // If selected is a UI element (has UICanvas or UITransform), use it as parent
            if (isUIElement(selected)) {
                return selected;
            }

            // Selected is not a UI element
            if (isCreatingCanvas) {
                // Creating canvas under non-UI entity - that's fine
                return selected;
            } else {
                // Creating non-canvas UI element under non-UI entity
                // Need to find or create a canvas under this entity
                EditorEntity existingCanvas = findCanvasChildOf(selected);
                if (existingCanvas != null) {
                    return existingCanvas;
                }

                // Create new canvas under selected entity
                EditorEntity newCanvas = uiFactory.create("Canvas", "UI Canvas");
                newCanvas.setParent(selected);
                newCanvas.setOrder(selected.getChildren().size());
                newEntitiesToAdd.add(newCanvas);
                return newCanvas;
            }
        }

        // Case 2: Nothing selected
        if (isCreatingCanvas) {
            // Creating canvas at root level
            return null;
        } else {
            // Creating non-canvas UI element with no selection
            // Find existing root canvas or create one
            EditorEntity existingCanvas = findRootCanvas();
            if (existingCanvas != null) {
                return existingCanvas;
            }

            // Create new canvas at root
            EditorEntity newCanvas = uiFactory.create("Canvas", "UI Canvas");
            newCanvas.setOrder(getNextRootOrder());
            newEntitiesToAdd.add(newCanvas);
            return newCanvas;
        }
    }

    /**
     * Checks if entity is a UI element (has UICanvas or UITransform component).
     */
    private boolean isUIElement(EditorEntity entity) {
        return entity.hasComponent("UICanvas") || entity.hasComponent("UITransform");
    }

    /**
     * Finds an existing canvas that is a direct child of the given parent.
     */
    private EditorEntity findCanvasChildOf(EditorEntity parent) {
        for (EditorEntity child : parent.getChildren()) {
            if (child.hasComponent("UICanvas")) {
                return child;
            }
        }
        return null;
    }

    /**
     * Finds an existing root-level canvas.
     */
    private EditorEntity findRootCanvas() {
        for (EditorEntity entity : scene.getRootEntities()) {
            if (entity.hasComponent("UICanvas")) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Counts entities in the list that have the specified parent.
     */
    private int countEntitiesWithParent(List<EditorEntity> entities, EditorEntity parent) {
        int count = 0;
        for (EditorEntity e : entities) {
            if (e.getParent() == parent) {
                count++;
            }
        }
        return count;
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

        // Shift siblings after the original
        shiftSiblingsAfter(original);

        UndoManager.getInstance().execute(new AddEntityCommand(scene, copy));

        selectAndSwitchMode(copy);
        scene.markDirty();
    }

    /**
     * Shifts the order of siblings that come after the given entity.
     */
    private void shiftSiblingsAfter(EditorEntity entity) {
        List<EditorEntity> siblings = (entity.getParent() != null)
                ? entity.getParent().getChildrenMutable()
                : scene.getRootEntities();

        for (EditorEntity sibling : siblings) {
            if (sibling != entity && sibling.getOrder() > entity.getOrder()) {
                sibling.setOrder(sibling.getOrder() + 1);
            }
        }
    }

    /**
     * Selects entity and switches to entity mode.
     */
    private void selectAndSwitchMode(EditorEntity entity) {
        if (selectionHandler != null) {
            scene.setSelection(Set.of(entity));
            selectionHandler.selectEntity(entity);
        } else {
            scene.setSelection(Set.of(entity));
        }
    }

    private int getNextRootOrder() {
        return scene.getRootEntities().size();
    }
}