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
import com.pocket.rpg.prefab.PrefabHierarchyHelper;
import com.pocket.rpg.prefab.PrefabRegistry;
import com.pocket.rpg.serialization.ComponentReflectionUtils;
import com.pocket.rpg.serialization.ComponentRegistry;
import com.pocket.rpg.serialization.GameObjectData;
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

    private List<GameObjectData> savedGameObjects;
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

        // Snapshot the hierarchy for "Reset to Saved" baseline
        savedGameObjects = deepCloneGameObjects(prefab.getGameObjects());
        savedDisplayName = prefab.getDisplayName();
        savedCategory = prefab.getCategory();

        // Build working scene from prefab hierarchy
        buildWorkingScene();

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

        // Capture working hierarchy back into the prefab
        List<GameObjectData> capturedHierarchy = PrefabHierarchyHelper.captureHierarchy(workingEntity);
        targetPrefab.setGameObjects(capturedHierarchy);

        try {
            PrefabRegistry.getInstance().saveJsonPrefab(targetPrefab);

            // Success: update saved snapshot, clear undo, mark clean
            invalidateInstanceCaches();
            savedGameObjects = deepCloneGameObjects(targetPrefab.getGameObjects());
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
            buildWorkingScene();
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
        savedGameObjects = null;
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

    /**
     * Builds the working scene from the prefab's hierarchy.
     * Creates scratch entities from each node's components.
     */
    private void buildWorkingScene() {
        workingScene = new EditorScene();

        List<GameObjectData> nodes = savedGameObjects;
        if (nodes == null || nodes.isEmpty()) {
            // Fallback: create empty root
            workingEntity = new EditorGameObject(
                    targetPrefab.getDisplayName(), new Vector3f(), false);
            workingEntity.getComponents().add(0, new Transform());
            workingScene.addEntity(workingEntity);
            return;
        }

        // Create scratch entities from each node
        workingEntity = null;
        for (GameObjectData node : nodes) {
            EditorGameObject entity = createWorkingEntityFromNode(node);
            workingScene.addEntity(entity);

            if (node.getParentId() == null || node.getParentId().isEmpty()) {
                workingEntity = entity;
            }
        }

        // Resolve hierarchy
        workingScene.resolveHierarchy();

        // Fallback if no root found
        if (workingEntity == null && !workingScene.getEntities().isEmpty()) {
            workingEntity = workingScene.getEntities().getFirst();
        }
    }

    /**
     * Creates a scratch EditorGameObject from a prefab node's data.
     */
    private EditorGameObject createWorkingEntityFromNode(GameObjectData node) {
        // Deep-clone components for the working entity
        List<Component> cloned = deepCloneComponents(node.getComponents());

        // Create via fromData to preserve id and parentId for hierarchy resolution
        GameObjectData workingData = new GameObjectData(node.getId(), node.getName(), cloned);
        workingData.setParentId(node.getParentId());
        workingData.setOrder(node.getOrder());
        workingData.setActive(node.isActive());

        return EditorGameObject.fromData(workingData);
    }

    private List<GameObjectData> deepCloneGameObjects(List<GameObjectData> source) {
        List<GameObjectData> result = new ArrayList<>();
        if (source == null) return result;
        for (GameObjectData node : source) {
            List<Component> clonedComponents = deepCloneComponents(node.getComponents());
            GameObjectData clone = new GameObjectData(node.getId(), node.getName(), clonedComponents);
            clone.setParentId(node.getParentId());
            clone.setOrder(node.getOrder());
            clone.setActive(node.isActive());
            result.add(clone);
        }
        return result;
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
