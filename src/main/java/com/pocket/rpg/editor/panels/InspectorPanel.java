package com.pocket.rpg.editor.panels;

import com.pocket.rpg.animation.animator.AnimatorController;
import com.pocket.rpg.animation.animator.AnimatorState;
import com.pocket.rpg.animation.animator.AnimatorTransition;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.camera.GameCamera;
import com.pocket.rpg.editor.EditorSelectionManager;
import com.pocket.rpg.editor.PlayModeController;
import com.pocket.rpg.editor.PlayModeSelectionManager;
import com.pocket.rpg.editor.PrefabEditController;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.panels.inspector.*;
import com.pocket.rpg.editor.panels.inspector.AssetInspectorRegistry;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.scene.RuntimeGameObjectAdapter;
import com.pocket.rpg.editor.shortcut.KeyboardLayout;
import com.pocket.rpg.editor.shortcut.ShortcutAction;
import com.pocket.rpg.editor.shortcut.ShortcutBinding;
import com.pocket.rpg.editor.ui.fields.ReflectionFieldEditor;
import com.pocket.rpg.editor.utils.IconUtils;
import com.pocket.rpg.scenes.Scene;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiTreeNodeFlags;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.List;
import java.util.Set;

/**
 * Context-sensitive inspector panel - orchestrates specialized inspectors.
 * Shows the current selection regardless of editor mode.
 */
public class InspectorPanel extends EditorPanel {

    private static final String PANEL_ID = "inspector";
    private static final float LABEL_WIDTH = 90f;

    @Setter
    private EditorScene scene;

    @Setter
    private EditorSelectionManager selectionManager;

    @Setter
    private PlayModeController playModeController;

    @Setter
    private PrefabEditController prefabEditController;

    private final CameraInspector cameraInspector = new CameraInspector();
    private final TilemapLayersInspector tilemapInspector = new TilemapLayersInspector();
    private final CollisionMapInspector collisionInspector = new CollisionMapInspector();
    private final EntityInspector entityInspector = new EntityInspector();
    private final MultiSelectionInspector multiSelectionInspector = new MultiSelectionInspector();
    private final AssetInspector assetInspector = new AssetInspector();
    private final PrefabInspector prefabInspector = new PrefabInspector(
            new ComponentFieldEditor(), new ComponentBrowserPopup());

    @Getter
    private final TriggerInspector triggerInspector = new TriggerInspector();

    // Animator inspectors
    private final AnimatorStateInspector animatorStateInspector = new AnimatorStateInspector();
    private final AnimatorTransitionInspector animatorTransitionInspector = new AnimatorTransitionInspector();

    // Track if we were showing asset inspector last frame
    private boolean wasShowingAssetInspector = false;

    public InspectorPanel() {
        super(PANEL_ID, true); // Default open - core panel
    }

    @Override
    public void render() {
        if (!isOpen()) return;
        if (ImGui.begin("Inspector")) {
            if (prefabEditController != null && prefabEditController.isActive()) {
                prefabInspector.render(prefabEditController);
            } else if (isPlayMode()) {
                renderPlayModeInspector();
            } else {
                renderEditorInspector();
            }
        }
        ReflectionFieldEditor.renderAssetPicker();
        entityInspector.renderDeleteConfirmationPopup();
        ImGui.end();
    }

    private boolean isPlayMode() {
        return playModeController != null && playModeController.isActive();
    }

    private void renderEditorInspector() {
        if (scene == null) {
            ImGui.textDisabled("No scene loaded");
            return;
        }

        // Update scene references
        cameraInspector.setScene(scene);
        tilemapInspector.setScene(scene);
        collisionInspector.setScene(scene);
        entityInspector.setScene(scene);
        multiSelectionInspector.setScene(scene);
        triggerInspector.setScene(scene);

        // Determine what should be shown
        boolean shouldShowAsset = selectionManager != null && selectionManager.isAssetSelected();

        // Check if asset inspector has a pending popup that needs to be handled
        if (assetInspector.hasPendingPopup()) {
            // Keep showing asset inspector while popup is active
            assetInspector.render();
        } else if (wasShowingAssetInspector && !shouldShowAsset) {
            // Switching away from asset inspector - check for unsaved changes
            if (assetInspector.checkUnsavedChangesBeforeLeaving()) {
                // Has unsaved changes, show popup (will be rendered next frame)
                assetInspector.render();
            } else {
                // No unsaved changes, render the new inspector
                renderCurrentInspector(shouldShowAsset);
            }
        } else {
            // Normal rendering
            renderCurrentInspector(shouldShowAsset);
        }

        // Track state for next frame
        wasShowingAssetInspector = shouldShowAsset || assetInspector.hasPendingPopup();
    }

    private void renderPlayModeInspector() {
        PlayModeSelectionManager selMgr = playModeController.getPlayModeSelectionManager();
        if (selMgr == null) return;

        // Camera selected — show camera inspector
        if (selMgr.isCameraSelected()) {
            renderRuntimeCameraInspector();
            return;
        }

        // Entity selection
        Set<GameObject> selected = selMgr.getSelectedObjects();
        if (selected.isEmpty()) {
            ImGui.textDisabled("Select an item in the hierarchy");
            return;
        }

        if (selected.size() > 1) {
            ImGui.text(selected.size() + " objects selected");
            ImGui.textDisabled("Multi-selection not supported in play mode");
            return;
        }

        GameObject obj = selected.iterator().next();
        entityInspector.renderRuntime(RuntimeGameObjectAdapter.of(obj));
    }

    private void renderRuntimeCameraInspector() {
        Scene runtimeScene = playModeController.getRuntimeScene();
        if (runtimeScene == null) return;

        GameCamera camera = runtimeScene.getCamera();
        if (camera == null) return;

        ImGui.text(IconUtils.getCameraIcon() + " Scene Camera");
        ImGui.separator();

        Vector3f pos = camera.getPosition();
        float[] posBuffer = {pos.x, pos.y};
        ImGui.text("Position");
        ImGui.sameLine(LABEL_WIDTH);
        ImGui.setNextItemWidth(-1);
        if (ImGui.dragFloat2("##camPos", posBuffer, 0.5f)) {
            camera.setPosition(posBuffer[0], posBuffer[1]);
        }

        float[] zoomBuffer = {camera.getZoom()};
        ImGui.text("Zoom");
        ImGui.sameLine(LABEL_WIDTH);
        ImGui.setNextItemWidth(-1);
        if (ImGui.dragFloat("##camZoom", zoomBuffer, 0.01f, 0.1f, 10f)) {
            camera.setZoom(zoomBuffer[0]);
        }

        float[] rotBuffer = {camera.getRotation()};
        ImGui.text("Rotation");
        ImGui.sameLine(LABEL_WIDTH);
        ImGui.setNextItemWidth(-1);
        if (ImGui.dragFloat("##camRot", rotBuffer, 0.5f)) {
            camera.setRotation(rotBuffer[0]);
        }

        if (camera.hasBounds()) {
            ImGui.textDisabled("Bounds: active");
        }

        ImGui.separator();
        ImGui.textDisabled("Changes reset when play mode stops");
    }

    /**
     * Renders the appropriate inspector based on current selection.
     */
    private void renderCurrentInspector(boolean shouldShowAsset) {
        // Check if a trigger is selected (takes priority when collision layer is active)
        if (triggerInspector.hasSelection() && selectionManager != null && selectionManager.isCollisionLayerSelected()) {
            triggerInspector.render();
        } else if (selectionManager == null) {
            ImGui.textDisabled("Select an item to inspect");
        } else if (selectionManager.isCameraSelected()) {
            cameraInspector.render();
        } else if (selectionManager.isTilemapLayerSelected()) {
            tilemapInspector.render();
        } else if (selectionManager.isCollisionLayerSelected()) {
            collisionInspector.render();
        } else if (selectionManager.isAnimatorStateSelected()) {
            animatorStateInspector.render();
        } else if (selectionManager.isAnimatorTransitionSelected()) {
            animatorTransitionInspector.render();
        } else if (shouldShowAsset) {
            assetInspector.setAsset(
                selectionManager.getSelectedAssetPath(),
                selectionManager.getSelectedAssetType()
            );
            assetInspector.render();
        } else if (selectionManager.hasEntitySelection()) {
            Set<EditorGameObject> selected = selectionManager.getSelectedEntities();
            if (selected.size() > 1) {
                multiSelectionInspector.render(selected);
            } else if (selected.size() == 1) {
                entityInspector.render(selected.iterator().next());
            }
        } else {
            ImGui.textDisabled("Select an item to inspect");
        }
    }

    /**
     * Sets the selected trigger tile for the trigger inspector.
     *
     * @param tile The trigger tile to inspect, or null to clear selection
     */
    public void setSelectedTrigger(TileCoord tile) {
        triggerInspector.setSelectedTile(tile);
    }

    /**
     * Clears the trigger selection.
     */
    public void clearTriggerSelection() {
        triggerInspector.clearSelection();
    }

    // ========================================================================
    // SHORTCUTS
    // ========================================================================

    @Override
    public List<ShortcutAction> provideShortcuts(KeyboardLayout layout) {
        ShortcutBinding undoBinding = layout == KeyboardLayout.AZERTY
                ? ShortcutBinding.ctrl(ImGuiKey.W)
                : ShortcutBinding.ctrl(ImGuiKey.Z);
        ShortcutBinding redoBinding = layout == KeyboardLayout.AZERTY
                ? ShortcutBinding.ctrlShift(ImGuiKey.W)
                : ShortcutBinding.ctrlShift(ImGuiKey.Z);

        return List.of(
                panelShortcut()
                        .id("inspector.undo")
                        .displayName("Inspector Undo")
                        .defaultBinding(undoBinding)
                        .allowInInput(true)
                        .handler(AssetInspectorRegistry::undo)
                        .build(),
                panelShortcut()
                        .id("inspector.redo")
                        .displayName("Inspector Redo")
                        .defaultBinding(redoBinding)
                        .allowInInput(true)
                        .handler(AssetInspectorRegistry::redo)
                        .build(),
                panelShortcut()
                        .id("inspector.redoAlt")
                        .displayName("Inspector Redo (Alt)")
                        .defaultBinding(ShortcutBinding.ctrl(ImGuiKey.Y))
                        .allowInInput(true)
                        .handler(AssetInspectorRegistry::redo)
                        .build()
        );
    }

    // ========================================================================
    // ANIMATOR SELECTION
    // ========================================================================

    /**
     * Sets the selected animator state for the inspector.
     *
     * @param state The state to inspect
     * @param controller The controller containing the state
     * @param onModified Callback when state is modified (for undo capture)
     */
    public void setAnimatorState(AnimatorState state, AnimatorController controller, Runnable onModified) {
        animatorTransitionInspector.clearSelection();
        animatorStateInspector.setSelection(state, controller, onModified);
    }

    /**
     * Sets the selected animator transition for the inspector.
     *
     * @param transition The transition to inspect
     * @param controller The controller containing the transition
     * @param onModified Callback when transition is modified (for undo capture)
     */
    public void setAnimatorTransition(AnimatorTransition transition, AnimatorController controller, Runnable onModified) {
        animatorStateInspector.clearSelection();
        animatorTransitionInspector.setSelection(transition, controller, onModified);
    }

    /**
     * Clears the animator selection (both state and transition).
     */
    public void clearAnimatorSelection() {
        animatorStateInspector.clearSelection();
        animatorTransitionInspector.clearSelection();
    }

    /**
     * Checks if an animator state or transition is currently selected.
     */
    public boolean hasAnimatorSelection() {
        return animatorStateInspector.hasSelection() || animatorTransitionInspector.hasSelection();
    }
}
