package com.pocket.rpg.editor.panels.hierarchy;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.UIEntityFactory;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.AddEntitiesCommand;
import com.pocket.rpg.editor.undo.commands.AddEntityCommand;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
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

        EditorGameObject entity = new EditorGameObject(name, position, false);

        // Parent under selected entity if any
        EditorGameObject selected = scene.getSelectedEntity();
        if (selected != null) {
            entity.setParent(selected);
            entity.setOrder(selected.getChildren().size());
        } else {
            entity.setOrder(getNextRootOrder());
        }

        UndoManager.getInstance().execute(new AddEntityCommand(scene, entity));

        selectAndSwitchMode(entity);
        scene.markDirty();
    }

    public void createUIElement(String uiType) {
        if (scene == null || uiFactory == null) return;

        EditorGameObject newEntity = uiFactory.create(uiType, null);
        if (newEntity == null) return;

        List<EditorGameObject> newEntitiesToAdd = new ArrayList<>();
        EditorGameObject parent = determineParentForUIElement(uiType, newEntitiesToAdd);

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
    private EditorGameObject determineParentForUIElement(String uiType, List<EditorGameObject> newEntitiesToAdd) {
        EditorGameObject selected = scene.getSelectedEntity();
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
                EditorGameObject existingCanvas = findCanvasChildOf(selected);
                if (existingCanvas != null) {
                    return existingCanvas;
                }

                // Create new canvas under selected entity
                EditorGameObject newCanvas = uiFactory.create("Canvas", "UI Canvas");
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
            EditorGameObject existingCanvas = findRootCanvas();
            if (existingCanvas != null) {
                return existingCanvas;
            }

            // Create new canvas at root
            EditorGameObject newCanvas = uiFactory.create("Canvas", "UI Canvas");
            newCanvas.setOrder(getNextRootOrder());
            newEntitiesToAdd.add(newCanvas);
            return newCanvas;
        }
    }

    /**
     * Checks if entity is a UI element (has UICanvas or UITransform component).
     */
    private boolean isUIElement(EditorGameObject entity) {
        return entity.hasComponent(UICanvas.class) || entity.hasComponent(UITransform.class);
    }

    /**
     * Finds an existing canvas that is a direct child of the given parent.
     */
    private EditorGameObject findCanvasChildOf(EditorGameObject parent) {
        for (EditorGameObject child : parent.getChildren()) {
            if (child.hasComponent(UICanvas.class)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Finds an existing root-level canvas.
     */
    private EditorGameObject findRootCanvas() {
        for (EditorGameObject entity : scene.getRootEntities()) {
            if (entity.hasComponent(UICanvas.class)) {
                return entity;
            }
        }
        return null;
    }

    /**
     * Counts entities in the list that have the specified parent.
     */
    private int countEntitiesWithParent(List<EditorGameObject> entities, EditorGameObject parent) {
        int count = 0;
        for (EditorGameObject e : entities) {
            if (e.getParent() == parent) {
                count++;
            }
        }
        return count;
    }

    /**
     * Duplicates an entity and all its children.
     * Does NOT change selection — caller is responsible for that.
     *
     * @return the root-level copy
     */
    public EditorGameObject duplicateEntity(EditorGameObject original) {
        Vector3f newPos = new Vector3f(original.getPosition()).add(1, 0, 0);
        EditorGameObject copy = cloneEntity(original, newPos);
        copy.setName(original.getName() + "_copy");

        copy.setParent(original.getParent());
        copy.setOrder(original.getOrder() + 1);

        // Shift siblings after the original
        shiftSiblingsAfter(original);

        // Collect the copy and all descendant clones for a single undo action
        List<EditorGameObject> allCopies = new ArrayList<>();
        allCopies.add(copy);
        duplicateChildrenRecursive(original, copy, allCopies);

        if (allCopies.size() == 1) {
            UndoManager.getInstance().execute(new AddEntityCommand(scene, copy));
        } else {
            UndoManager.getInstance().execute(new AddEntitiesCommand(scene, allCopies));
        }

        scene.markDirty();
        return copy;
    }

    private EditorGameObject cloneEntity(EditorGameObject original, Vector3f position) {
        EditorGameObject copy;
        if (original.isScratchEntity()) {
            copy = new EditorGameObject(original.getName(), position, false);
            for (Component comp : original.getComponents()) {
                Component compCopy = ComponentReflectionUtils.cloneComponent(comp);
                if (compCopy != null) {
                    copy.addComponent(compCopy);
                }
            }
        } else if (original.isPrefabChildNode()) {
            copy = new EditorGameObject(original.getPrefabId(), original.getPrefabNodeId(), position);
            copy.setName(original.getName());
            copyOverrides(original, copy);
        } else {
            copy = new EditorGameObject(original.getPrefabId(), position);
            copy.setName(original.getName());
            copyOverrides(original, copy);
        }
        return copy;
    }

    private void copyOverrides(EditorGameObject original, EditorGameObject copy) {
        for (Component comp : original.getComponents()) {
            String componentType = comp.getClass().getName();
            for (String fieldName : original.getOverriddenFields(componentType)) {
                Object value = original.getFieldValue(componentType, fieldName);
                copy.setFieldValue(componentType, fieldName, value);
            }
        }
    }

    private void duplicateChildrenRecursive(EditorGameObject original, EditorGameObject copyParent,
                                            List<EditorGameObject> allCopies) {
        for (EditorGameObject child : original.getChildren()) {
            Vector3f childPos = new Vector3f(child.getPosition());
            EditorGameObject childCopy = cloneEntity(child, childPos);
            childCopy.setParent(copyParent);
            childCopy.setOrder(child.getOrder());
            allCopies.add(childCopy);

            duplicateChildrenRecursive(child, childCopy, allCopies);
        }
    }

    /**
     * Shifts the order of siblings that come after the given entity.
     */
    private void shiftSiblingsAfter(EditorGameObject entity) {
        List<EditorGameObject> siblings = (entity.getParent() != null)
                ? entity.getParent().getChildrenMutable()
                : scene.getRootEntities();

        for (EditorGameObject sibling : siblings) {
            if (sibling != entity && sibling.getOrder() > entity.getOrder()) {
                sibling.setOrder(sibling.getOrder() + 1);
            }
        }
    }

    /**
     * Selects entity and switches to entity mode.
     */
    private void selectAndSwitchMode(EditorGameObject entity) {
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
