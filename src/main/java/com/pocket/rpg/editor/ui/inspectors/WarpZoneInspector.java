package com.pocket.rpg.editor.ui.inspectors;

import com.pocket.rpg.components.interaction.SpawnPoint;
import com.pocket.rpg.components.interaction.WarpZone;
import com.pocket.rpg.config.ConfigLoader;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.config.TransitionEntry;
import com.pocket.rpg.editor.core.EditorColors;
import com.pocket.rpg.editor.scene.EditorGameObject;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.fields.FieldEditorContext;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.undo.UndoManager;
import com.pocket.rpg.editor.undo.commands.SetterUndoCommand;
import com.pocket.rpg.editor.utils.SceneUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;

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

    private static final int ERROR_ROW_BG_COLOR = ImGui.colorConvertFloat4ToU32(1f, 0.1f, 0.1f, 0.7f);

    @Override
    public boolean draw() {
        boolean changed = false;
        String id = String.valueOf(System.identityHashCode(component));

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
        changed |= FieldEditors.drawBoolean("Show Label", "warp.showLabel." + id,
                component::isShowDestinationLabel, component::setShowDestinationLabel);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("If true, show warp destination in editor gizmo.");
        }

        ImGui.spacing();
        ImGui.spacing();

        // Audio section
        ImGui.text("Audio");
        ImGui.separator();

        changed |= FieldEditors.drawAudioClip("Warp Out Sound", component, "warpOutSound", editorEntity());

        ImGui.spacing();
        ImGui.spacing();

        // Transition section
        ImGui.text("Transition");
        ImGui.separator();

        // Use fade toggle
        changed |= FieldEditors.drawBoolean("Use Fade", "warp.useFade." + id,
                component::isUseFade, component::setUseFade);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Enable fade transition effect when warping.");
        }

        // Only show override option if useFade is enabled
        if (component.isUseFade()) {
            // Override defaults toggle
            changed |= FieldEditors.drawBoolean("Override Defaults", "warp.overrideDefaults." + id,
                    component::isOverrideTransitionDefaults, component::setOverrideTransitionDefaults);
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Override the default transition settings from rendering config.");
            }

            // Only show custom settings if override is enabled
            if (component.isOverrideTransitionDefaults()) {
                // Fade out duration
                changed |= FieldEditors.drawFloat("Fade Out", "warp.fadeOut." + id,
                        component::getFadeOutDuration, v -> component.setFadeOutDuration((float) v),
                        0.01f, 0.0f, 5.0f, "%.2f s");

                // Fade in duration
                changed |= FieldEditors.drawFloat("Fade In", "warp.fadeIn." + id,
                        component::getFadeInDuration, v -> component.setFadeInDuration((float) v),
                        0.01f, 0.0f, 5.0f, "%.2f s");

                // Transition name dropdown
                changed |= drawTransitionNameDropdown();
            }
        }

        // Preview
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        drawDestinationPreview();

        return changed;
    }

    /**
     * Draws a transition name dropdown for the WarpZone.
     * Options: "(default)" + all entries from GameConfig + "Random".
     */
    private boolean drawTransitionNameDropdown() {
        RenderingConfig renderingConfig = ConfigLoader.getConfig(ConfigLoader.ConfigType.RENDERING);
        List<TransitionEntry> entries = renderingConfig.getTransitions();

        String currentName = component.getTransitionName();
        String display;
        if (currentName == null || currentName.isEmpty()) {
            display = "(default)";
        } else {
            display = currentName;
        }

        final boolean[] changed = {false};
        FieldEditors.inspectorRow("Transition", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginCombo("##transitionName", display)) {
                // Default option (empty name = use global default)
                boolean isDefault = currentName == null || currentName.isEmpty();
                if (ImGui.selectable("(default)", isDefault)) {
                    String oldValue = currentName;
                    component.setTransitionName("");
                    UndoManager.getInstance().push(new SetterUndoCommand<>(
                            component::setTransitionName, oldValue, "", "Change Transition"
                    ));
                    changed[0] = true;
                }

                // Random option
                boolean isRandom = "Random".equals(currentName);
                if (ImGui.selectable("Random", isRandom)) {
                    String oldValue = currentName;
                    component.setTransitionName("Random");
                    UndoManager.getInstance().push(new SetterUndoCommand<>(
                            component::setTransitionName, oldValue, "Random", "Change Transition"
                    ));
                    changed[0] = true;
                }

                if (!entries.isEmpty()) {
                    ImGui.separator();
                }

                // Named entries
                for (TransitionEntry entry : entries) {
                    String name = entry.getName();
                    if (name == null || name.isEmpty()) continue;
                    boolean isSelected = name.equals(currentName);
                    if (ImGui.selectable(name, isSelected)) {
                        String oldValue = currentName;
                        component.setTransitionName(name);
                        UndoManager.getInstance().push(new SetterUndoCommand<>(
                                component::setTransitionName, oldValue, name, "Change Transition"
                        ));
                        changed[0] = true;
                    }
                }

                ImGui.endCombo();
            }
        });
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Transition pattern to use. (default) uses the global setting.");
        }

        return changed[0];
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
                    String oldValue = currentScene;
                    component.setTargetScene("");
                    UndoManager.getInstance().push(new SetterUndoCommand<>(
                            component::setTargetScene, oldValue, "", "Change Target Scene"
                    ));
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
                        String oldValue = currentScene;
                        component.setTargetScene(sceneName);
                        UndoManager.getInstance().push(new SetterUndoCommand<>(
                                component::setTargetScene, oldValue, sceneName, "Change Target Scene"
                        ));
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
                            String oldValue = currentSpawn;
                            component.setTargetSpawnId(spawnId);
                            UndoManager.getInstance().push(new SetterUndoCommand<>(
                                    component::setTargetSpawnId, oldValue, spawnId, "Change Spawn Point"
                            ));
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
            EditorColors.textColored(EditorColors.WARNING, "Spawn point required");
        } else if (isBroken) {
            EditorColors.textColored(EditorColors.WARNING, "Spawn point '" + currentSpawn + "' not found");
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
                EditorColors.textColored(EditorColors.WARNING, "  (incomplete - select spawn point)");
            }
        } else {
            if (hasSpawn) {
                ImGui.text("  " + targetScene + " -> " + targetSpawn);
            } else {
                EditorColors.textColored(EditorColors.WARNING, "  " + targetScene + " -> (select spawn point)");
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
                String spawnId = spawn.getSpawnId();
                if (spawnId != null && !spawnId.isBlank()) {
                    spawnIds.add(spawnId);
                }
            }
        }

        return spawnIds;
    }
}
