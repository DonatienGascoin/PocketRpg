package com.pocket.rpg.editor;

import com.pocket.rpg.animation.animator.AnimatorController;
import com.pocket.rpg.animation.animator.AnimatorState;
import com.pocket.rpg.animation.animator.AnimatorTransition;
import com.pocket.rpg.editor.scene.EditorGameObject;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Wraps EditorSelectionManager to intercept selection changes based on editor mode.
 * When in PREFAB_EDIT mode with unsaved changes, selection changes are deferred
 * through a confirmation flow. SelectionManager itself stays controller-free.
 */
public class SelectionGuard {

    private final EditorSelectionManager selectionManager;
    private final EditorModeManager modeManager;

    /**
     * Callback for when a selection change is attempted during a mode that
     * requires confirmation. The guard calls this with a Runnable that
     * performs the actual selection change.
     */
    @FunctionalInterface
    public interface SelectionInterceptor {
        void intercept(Runnable action);
    }

    private SelectionInterceptor interceptor = Runnable::run;

    /**
     * Predicate that identifies entities exempt from the guard (e.g., working scene entities
     * during prefab edit mode). Returns true if the entity should bypass the guard.
     * Null when no exemption is active.
     */
    private Predicate<EditorGameObject> guardExemption;

    public SelectionGuard(EditorSelectionManager selectionManager,
                          EditorModeManager modeManager) {
        this.selectionManager = selectionManager;
        this.modeManager = modeManager;
    }

    /**
     * Returns the underlying EditorSelectionManager for read-only queries
     * (e.g., isCameraSelected, getSelectedEntities). Write operations should
     * go through the guarded methods on this class.
     */
    public EditorSelectionManager getSelectionManager() {
        return selectionManager;
    }

    /**
     * Sets the interceptor for mode-gated selection changes.
     * PrefabEditController sets this on mode entry and clears it on exit.
     */
    public void setInterceptor(SelectionInterceptor interceptor) {
        this.interceptor = interceptor != null ? interceptor : Runnable::run;
    }

    /**
     * Sets a predicate that identifies entities exempt from the guard.
     * During prefab edit mode, working scene entities should bypass the guard
     * so the user can freely select within the prefab hierarchy.
     */
    public void setGuardExemption(Predicate<EditorGameObject> exemption) {
        this.guardExemption = exemption != null ? exemption : e -> false;
    }

    // ========================================================================
    // GUARDED SELECTION METHODS
    // ========================================================================

    public void selectEntity(EditorGameObject entity) {
        if (needsGuard() && !isExempt(entity)) {
            interceptor.intercept(() -> selectionManager.selectEntity(entity));
        } else {
            selectionManager.selectEntity(entity);
        }
    }

    public void selectEntities(Set<EditorGameObject> entities) {
        if (needsGuard() && entities.stream().anyMatch(e -> !isExempt(e))) {
            interceptor.intercept(() -> selectionManager.selectEntities(entities));
        } else {
            selectionManager.selectEntities(entities);
        }
    }

    public void toggleEntitySelection(EditorGameObject entity) {
        if (needsGuard() && !isExempt(entity)) {
            interceptor.intercept(() -> selectionManager.toggleEntitySelection(entity));
        } else {
            selectionManager.toggleEntitySelection(entity);
        }
    }

    public void selectCamera() {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.selectCamera());
        } else {
            selectionManager.selectCamera();
        }
    }

    public void selectTilemapLayer(int layerIndex) {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.selectTilemapLayer(layerIndex));
        } else {
            selectionManager.selectTilemapLayer(layerIndex);
        }
    }

    public void selectCollisionLayer() {
        if (needsGuard()) {
            interceptor.intercept(() -> selectionManager.selectCollisionLayer());
        } else {
            selectionManager.selectCollisionLayer();
        }
    }

    public void selectAsset(String path, Class<?> type) {
        // Asset selection doesn't require mode guard — it's not a scene-level selection
        // that conflicts with prefab editing. Allow freely during prefab edit mode.
        selectionManager.selectAsset(path, type);
    }

    public void selectAnimatorState(AnimatorState state,
                                     AnimatorController controller,
                                     Runnable onModified) {
        // Animator selections don't require mode guard — they're panel-specific,
        // not scene-level selections that conflict with prefab editing.
        selectionManager.selectAnimatorState(state, controller, onModified);
    }

    public void selectAnimatorTransition(AnimatorTransition transition,
                                          AnimatorController controller,
                                          Runnable onModified) {
        // Animator selections don't require mode guard — they're panel-specific,
        // not scene-level selections that conflict with prefab editing.
        selectionManager.selectAnimatorTransition(transition, controller, onModified);
    }

    public void clearSelection() {
        if (needsGuard() && !hasGuardExemption()) {
            interceptor.intercept(selectionManager::clearSelection);
        } else {
            selectionManager.clearSelection();
        }
    }

    // ========================================================================
    // PRIVATE
    // ========================================================================

    private boolean needsGuard() {
        return modeManager.isPrefabEditMode();
    }

    private boolean isExempt(EditorGameObject entity) {
        return guardExemption != null && guardExemption.test(entity);
    }

    private boolean hasGuardExemption() {
        return guardExemption != null;
    }
}
