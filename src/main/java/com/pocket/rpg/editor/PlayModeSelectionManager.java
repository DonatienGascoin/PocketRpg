package com.pocket.rpg.editor;

import com.pocket.rpg.core.GameObject;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Manages selection of runtime GameObjects during play mode.
 * Separate from {@link EditorSelectionManager} to keep runtime and editor state isolated.
 */
public class PlayModeSelectionManager {

    @Getter
    private final Set<GameObject> selectedObjects = new LinkedHashSet<>();

    public void select(GameObject obj) {
        selectedObjects.clear();
        if (obj != null) {
            selectedObjects.add(obj);
        }
    }

    public void toggleSelection(GameObject obj) {
        if (obj == null) return;
        if (!selectedObjects.remove(obj)) {
            selectedObjects.add(obj);
        }
    }

    public void clearSelection() {
        selectedObjects.clear();
    }

    public GameObject getSingleSelected() {
        return selectedObjects.size() == 1 ? selectedObjects.iterator().next() : null;
    }

    public boolean isSelected(GameObject obj) {
        return selectedObjects.contains(obj);
    }

    /**
     * Removes any selected objects that have been destroyed at runtime.
     * Call each frame before rendering to handle objects destroyed during gameplay.
     */
    public void pruneDestroyedObjects() {
        selectedObjects.removeIf(GameObject::isDestroyed);
    }
}
