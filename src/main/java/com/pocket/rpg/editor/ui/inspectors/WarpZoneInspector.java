package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.interaction.SpawnPoint;
import com.pocket.rpg.components.interaction.TriggerZone;
import com.pocket.rpg.components.interaction.WarpZone;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.utils.SceneUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImBoolean;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom editor for WarpZone component.
 * <p>
 * Features:
 * - Target scene dropdown (uses SceneUtils)
 * - Target spawn point dropdown (local or cross-scene)
 * - Show label option for gizmo
 * - Red background for missing/broken spawn point references
 */
@InspectorFor(WarpZone.class)
public class WarpZoneInspector extends CustomComponentInspector<WarpZone> {

    private final ImBoolean showLabelState = new ImBoolean();

    private static final int ERROR_ROW_BG_COLOR = ImGui.colorConvertFloat4ToU32(1f, 0.1f, 0.1f, 0.7f);

    @Override
    public boolean draw() {
        boolean changed = false;

        // Destination section
        ImGui.text("Destination");
        ImGui.separator();

        changed |= drawSceneDropdown();
        ImGui.spacing();
        changed |= drawSpawnPointDropdown();

        ImGui.spacing();
        ImGui.spacing();

        // Options section
        ImGui.text("Options");
        ImGui.separator();

        // Show destination label
        showLabelState.set(component.isShowDestinationLabel());
        FieldEditors.inspectorRow("Show Label", () -> {
            if (ImGui.checkbox("##showLabel", showLabelState)) {
                component.setShowDestinationLabel(showLabelState.get());
            }
        });
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("If true, show warp destination in editor gizmo.");
        }
        if (ImGui.isItemDeactivatedAfterEdit()) changed = true;

        // Preview
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        drawDestinationPreview();

        return changed;
    }

    private boolean drawSceneDropdown() {
        // Always refresh when drawing (no stale cache)
        List<String> availableScenes = SceneUtils.getAvailableSceneNames();

        String currentScene = component.getTargetScene();
        String display = (currentScene == null || currentScene.isBlank()) ? "(same scene)" : currentScene;

        final boolean[] changed = {false};
        FieldEditors.inspectorRow("Target Scene", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginCombo("##targetScene", display)) {
                // Option for same scene (empty value)
                if (ImGui.selectable("(same scene)", currentScene == null || currentScene.isBlank())) {
                    component.setTargetScene("");
                    changed[0] = true;
                }

                // Separator
                if (!availableScenes.isEmpty()) {
                    ImGui.separator();
                }

                // Available scenes
                for (String sceneName : availableScenes) {
                    boolean isSelected = sceneName.equals(currentScene);
                    if (ImGui.selectable(sceneName, isSelected)) {
                        component.setTargetScene(sceneName);
                        changed[0] = true;
                    }
                }

                ImGui.endCombo();
            }
        });
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Target scene to load. Leave empty to warp within the same scene.");
        }

        return changed[0];
    }

    private boolean drawSpawnPointDropdown() {
        String targetScene = component.getTargetScene();
        boolean isCrossScene = targetScene != null && !targetScene.isBlank();

        List<String> spawnIds;
        if (isCrossScene) {
            // Cross-scene: use spawn points from target scene
            spawnIds = SceneUtils.getSpawnPoints(targetScene);
        } else {
            // Same scene: use local spawn points from EditorScene
            spawnIds = getLocalSpawnIds();
        }

        return drawSpawnDropdown(spawnIds, isCrossScene);
    }

    private boolean drawSpawnDropdown(List<String> spawnIds, boolean isCrossScene) {
        String currentSpawn = component.getTargetSpawnId();
        String display = (currentSpawn == null || currentSpawn.isBlank()) ? "(select spawn point)" : currentSpawn;

        // Check if spawn point is missing or broken
        boolean isMissing = currentSpawn == null || currentSpawn.isBlank();
        boolean isBroken = !isMissing && !spawnIds.contains(currentSpawn);
        boolean hasError = isMissing || isBroken;

        // Begin row highlight if error
        float rowStartY = 0;
        if (hasError) {
            ImVec2 cursorPos = ImGui.getCursorScreenPos();
            rowStartY = cursorPos.y;
            ImGui.getWindowDrawList().channelsSplit(2);
            ImGui.getWindowDrawList().channelsSetCurrent(1);
        }

        final boolean[] changed = {false};
        FieldEditors.inspectorRow("Spawn Point", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginCombo("##targetSpawn", display)) {
                if (spawnIds.isEmpty()) {
                    ImGui.textDisabled("No spawn points found");
                    if (isCrossScene) {
                        ImGui.textDisabled("(save target scene first)");
                    } else {
                        ImGui.textDisabled("(add SpawnPoint to scene)");
                    }
                } else {
                    for (String spawnId : spawnIds) {
                        boolean isSelected = spawnId.equals(currentSpawn);
                        if (ImGui.selectable(spawnId, isSelected)) {
                            component.setTargetSpawnId(spawnId);
                            changed[0] = true;
                        }
                    }
                }

                ImGui.endCombo();
            }
        });
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Target spawn point ID. Must match a SpawnPoint's spawnId.");
        }

        // End row highlight
        if (hasError) {
            ImDrawList drawList = ImGui.getWindowDrawList();
            drawList.channelsSetCurrent(0);
            float padding = 2f;
            float startX = ImGui.getWindowPos().x + ImGui.getWindowContentRegionMin().x - padding;
            float endX = ImGui.getWindowPos().x + ImGui.getWindowContentRegionMax().x + padding;
            float startY = rowStartY - padding;
            float endY = ImGui.getCursorScreenPos().y;
            drawList.addRectFilled(startX, startY, endX, endY, ERROR_ROW_BG_COLOR);
            drawList.channelsMerge();
        }

        // Warning text
        if (isMissing) {
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "Spawn point required");
        } else if (isBroken) {
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "Spawn point '" + currentSpawn + "' not found");
        }

        return changed[0];
    }

    private void drawDestinationPreview() {
        ImGui.textDisabled("Destination Preview:");

        String targetScene = component.getTargetScene();
        String targetSpawn = component.getTargetSpawnId();

        boolean hasSpawn = targetSpawn != null && !targetSpawn.isBlank();

        if (targetScene == null || targetScene.isBlank()) {
            if (hasSpawn) {
                ImGui.text("  Same scene -> " + targetSpawn);
            } else {
                ImGui.textColored(1.0f, 0.5f, 0.2f, 1.0f, "  (incomplete - select spawn point)");
            }
        } else {
            if (hasSpawn) {
                ImGui.text("  " + targetScene + " -> " + targetSpawn);
            } else {
                ImGui.textColored(1.0f, 0.5f, 0.2f, 1.0f, "  " + targetScene + " -> (select spawn point)");
            }
        }
    }

    /**
     * Gets spawn points from the current EditorScene.
     * Refreshes every call to avoid stale data.
     */
    private List<String> getLocalSpawnIds() {
        List<String> spawnIds = new ArrayList<>();

        EditorScene scene = FieldEditorContext.getCurrentScene();
        if (scene == null) {
            return spawnIds;
        }

        // Scan EditorGameObjects for SpawnPoint components
        for (EditorGameObject obj : scene.getEntities()) {
            SpawnPoint spawn = obj.getComponent(SpawnPoint.class);
            if (spawn != null) {
                String id = spawn.getSpawnId();
                if (id != null && !id.isBlank()) {
                    spawnIds.add(id);
                }
            }
        }

        return spawnIds;
    }
}
