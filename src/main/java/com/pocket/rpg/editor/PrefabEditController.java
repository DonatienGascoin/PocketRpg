package com.pocket.rpg.editor;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.editor.events.*;
import com.pocket.rpg.editor.panels.StaleReferencesPopup;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.prefab.JsonPrefab;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.editor.core.EditorColors;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Controls the Prefab Edit Mode lifecycle.
 * <p>
 * When active, the editor works on an isolated working entity built from a JsonPrefab's
 * components. Uses scoped undo (Plan 3) to isolate prefab edits from the scene undo history.
 * <p>
 * Entry: {@link RequestPrefabEditEvent} or direct call to {@link #enterEditMode(JsonPrefab)}.
 * Exit: Escape key, exit button, or scene change.
 */
public class PrefabEditController {

    public enum State { INACTIVE, EDITING }

    @Getter
    private State state = State.INACTIVE;

    private final EditorContext context;

    @Getter
    private JsonPrefab targetPrefab;

    @Getter
    private EditorGameObject workingEntity;

    @Getter
    private EditorScene workingScene;

    private List<Component> savedComponents;
    private String savedDisplayName;
    private String savedCategory;

    @Getter
    private boolean dirty = false;

    // Confirmation popup state
    private boolean showConfirmationPopup = false;
    private Runnable pendingAction = null;
    private String confirmationMessage = "";
    private boolean isRevertConfirmation = false;  // true for revert, false for exit

    // Reference to shortcut handler's activeDirtyTracker setter
    @Setter
    private java.util.function.Consumer<com.pocket.rpg.editor.scene.DirtyTracker> dirtyTrackerSetter;

    // Stale references popup (shared with scene controller)
    @Setter
    private StaleReferencesPopup staleReferencesPopup;

    public PrefabEditController(EditorContext context) {
        this.context = context;

        EditorEventBus.get().subscribe(RequestPrefabEditEvent.class, event -> {
            enterEditMode(event.prefab());
        });

        EditorEventBus.get().subscribe(SceneWillChangeEvent.class, event -> {
            if (state == State.EDITING && dirty) {
                event.cancel();
                requestExit(null);
            } else if (state == State.EDITING) {
                exitEditMode();
            }
        });
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    public void enterEditMode(JsonPrefab prefab) {
        if (prefab == null) return;

        // Already editing a different prefab — request exit first
        if (state == State.EDITING && targetPrefab != prefab) {
            requestExit(() -> enterEditMode(prefab));
            return;
        }

        // Already editing this prefab
        if (state == State.EDITING) return;

        this.targetPrefab = prefab;

        // Capture fallback resolutions from the prefab load (reset happened in asset browser handler)
        List<String> resolutions = ComponentRegistry.getFallbackResolutions();

        // Push undo scope for isolation
        UndoManager.getInstance().pushScope();

        // Deep-clone components for "Reset to Saved" baseline
        savedComponents = deepCloneComponents(prefab.getComponents());
        savedDisplayName = prefab.getDisplayName();
        savedCategory = prefab.getCategory();

        // Build working entity as a scratch entity with cloned components
        buildWorkingEntity(savedComponents);

        // Set mode
        context.getModeManager().setMode(EditorMode.PREFAB_EDIT);

        // Set SelectionGuard interceptor
        context.getSelectionGuard().setInterceptor(action -> {
            if (dirty) {
                requestExit(action);
            } else {
                exitEditMode();
                action.run();
            }
        });

        // Set activeDirtyTracker to our markDirty
        if (dirtyTrackerSetter != null) {
            dirtyTrackerSetter.accept(this::markDirty);
        }

        dirty = false;
        state = State.EDITING;

        // Auto-select the working entity (bypass guard since we're already in prefab edit)
        context.getSelectionGuard().getSelectionManager().selectEntity(workingEntity);

        EditorEventBus.get().publish(new PrefabEditStartedEvent());

        // Show popup if any stale references were found
        if (!resolutions.isEmpty() && staleReferencesPopup != null) {
            Log.warn("PrefabEditController", "Prefab has " + resolutions.size() + " stale component reference(s)");
            staleReferencesPopup.open(resolutions, () -> {
                try {
                    save();
                    // Show status message via event bus
                    EditorEventBus.get().publish(new StatusMessageEvent("Prefab saved - stale references updated"));
                } catch (Exception e) {
                    Log.error("PrefabEditController", "Failed to save prefab after stale reference prompt", e);
                    EditorEventBus.get().publish(new StatusMessageEvent("Save failed: " + e.getMessage()));
                }
            });
        }
    }

    public void save() {
        if (state != State.EDITING || targetPrefab == null) return;

        // Deep-clone working entity's components into the prefab
        List<Component> clonedComponents = deepCloneComponents(workingEntity.getComponents());
        targetPrefab.setComponents(clonedComponents);

        try {
            PrefabRegistry.getInstance().saveJsonPrefab(targetPrefab);

            // Success: update saved snapshot, clear undo, mark clean
            invalidateInstanceCaches();
            savedComponents = deepCloneComponents(workingEntity.getComponents());
            savedDisplayName = targetPrefab.getDisplayName();
            savedCategory = targetPrefab.getCategory();
            UndoManager.getInstance().clear();
            dirty = false;

            System.out.println("Prefab saved: " + targetPrefab.getDisplayName());
        } catch (Exception e) {
            System.err.println("Failed to save prefab: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveAndExit() {
        save();
        if (!dirty) {
            exitEditMode();
        }
    }

    public void resetToSaved() {
        if (state != State.EDITING) return;
        if (!isDirty()) return;

        confirmationMessage = "Revert all changes to the prefab?\nThis cannot be undone.";
        isRevertConfirmation = true;
        pendingAction = () -> {
            buildWorkingEntity(savedComponents);
            targetPrefab.setDisplayName(savedDisplayName);
            targetPrefab.setCategory(savedCategory);
            UndoManager.getInstance().clear();
            dirty = false;
        };
        showConfirmationPopup = true;
    }

    public void requestExit(Runnable afterExit) {
        if (state != State.EDITING) {
            if (afterExit != null) afterExit.run();
            return;
        }

        if (dirty) {
            confirmationMessage = "You have unsaved prefab changes.";
            isRevertConfirmation = false;
            pendingAction = afterExit;
            showConfirmationPopup = true;
        } else {
            exitEditMode();
            if (afterExit != null) afterExit.run();
        }
    }

    public void exitEditMode() {
        if (state != State.EDITING) return;

        // Pop undo scope
        UndoManager.getInstance().popScope();

        // Clear SelectionGuard interceptor
        context.getSelectionGuard().setInterceptor(null);

        // Restore activeDirtyTracker to current scene
        if (dirtyTrackerSetter != null) {
            EditorScene scene = context.getCurrentScene();
            if (scene != null) {
                dirtyTrackerSetter.accept(scene::markDirty);
            }
        }

        // Restore mode
        context.getModeManager().setMode(EditorMode.SCENE);

        // Discard references
        workingEntity = null;
        workingScene = null;
        targetPrefab = null;
        savedComponents = null;
        savedDisplayName = null;
        savedCategory = null;
        dirty = false;

        state = State.INACTIVE;

        EditorEventBus.get().publish(new PrefabEditStoppedEvent());
    }

    // ========================================================================
    // DIRTY TRACKING
    // ========================================================================

    public void markDirty() {
        dirty = true;
    }

    // ========================================================================
    // CONFIRMATION POPUP
    // ========================================================================

    public void renderConfirmationPopup() {
        if (!showConfirmationPopup) return;

        String popupTitle = isRevertConfirmation ? "Revert Changes" : "Unsaved Prefab Changes";
        ImGui.openPopup(popupTitle);

        if (ImGui.beginPopupModal(popupTitle, ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoMove)) {
            ImGui.text(confirmationMessage);
            ImGui.spacing();
            ImGui.separator();
            ImGui.spacing();

            float buttonWidth = 120;

            if (isRevertConfirmation) {
                // Revert confirmation: Cancel and Discard only
                if (ImGui.button("Cancel", buttonWidth, 0)) {
                    pendingAction = null;
                    showConfirmationPopup = false;
                    ImGui.closeCurrentPopup();
                }

                ImGui.sameLine();

                if (ImGui.button("Discard changes", buttonWidth, 0)) {
                    Runnable action = pendingAction;
                    pendingAction = null;
                    showConfirmationPopup = false;
                    ImGui.closeCurrentPopup();
                    if (action != null) action.run();
                }
            } else {
                // Exit confirmation: Cancel, Discard and exit, Save and exit
                if (ImGui.button("Cancel", buttonWidth, 0)) {
                    pendingAction = null;
                    showConfirmationPopup = false;
                    ImGui.closeCurrentPopup();
                }

                ImGui.sameLine();

                if (ImGui.button("Discard and exit", buttonWidth, 0)) {
                    showConfirmationPopup = false;
                    Runnable action = pendingAction;
                    pendingAction = null;
                    dirty = false;
                    exitEditMode();
                    if (action != null) action.run();
                    ImGui.closeCurrentPopup();
                }

                ImGui.sameLine();

                EditorColors.pushSuccessButton();
                if (ImGui.button("Save and exit", buttonWidth, 0)) {
                    showConfirmationPopup = false;
                    Runnable action = pendingAction;
                    pendingAction = null;
                    saveAndExit();
                    if (action != null) action.run();
                    ImGui.closeCurrentPopup();
                }
                EditorColors.popButtonColors();
            }

            ImGui.endPopup();
        }
    }

    // ========================================================================
    // QUERY
    // ========================================================================

    public boolean isActive() {
        return state == State.EDITING;
    }

    public int getInstanceCount() {
        EditorScene scene = context.getCurrentScene();
        if (scene == null || targetPrefab == null) return 0;

        String prefabId = targetPrefab.getId();
        int count = 0;
        for (EditorGameObject entity : scene.getEntities()) {
            if (prefabId.equals(entity.getPrefabId()) && !entity.isPrefabChildNode()) {
                count++;
            }
        }
        return count;
    }

    // ========================================================================
    // PRIVATE
    // ========================================================================

    private void buildWorkingEntity(List<Component> sourceComponents) {
        // Create scratch entity with cloned components
        workingEntity = new EditorGameObject(
                targetPrefab.getDisplayName(),
                new Vector3f(0, 0, 0),
                false  // scratch, not prefab instance
        );

        // Remove the default Transform added by the constructor —
        // we'll add our own from the cloned components
        Transform defaultTransform = workingEntity.getTransform();
        if (defaultTransform != null) {
            workingEntity.getComponents().remove(defaultTransform);
        }

        // Add deep-cloned components
        List<Component> cloned = deepCloneComponents(sourceComponents);
        boolean hasTransform = false;
        for (Component comp : cloned) {
            if (comp instanceof Transform) {
                hasTransform = true;
            }
            workingEntity.getComponents().add(comp);
        }

        // Ensure Transform exists
        if (!hasTransform) {
            workingEntity.getComponents().add(0, new Transform());
        }

        // Create temporary scene containing just the working entity
        workingScene = new EditorScene();
        workingScene.addEntity(workingEntity);
    }

    private List<Component> deepCloneComponents(List<Component> source) {
        List<Component> result = new ArrayList<>();
        if (source == null) return result;
        for (Component comp : source) {
            Component clone = ComponentReflectionUtils.cloneComponent(comp);
            if (clone != null) {
                result.add(clone);
            }
        }
        return result;
    }

    private void invalidateInstanceCaches() {
        EditorScene scene = context.getCurrentScene();
        if (scene == null || targetPrefab == null) return;

        String prefabId = targetPrefab.getId();
        for (EditorGameObject entity : scene.getEntities()) {
            if (prefabId.equals(entity.getPrefabId())) {
                entity.invalidateComponentCache();
            }
        }
    }
}
