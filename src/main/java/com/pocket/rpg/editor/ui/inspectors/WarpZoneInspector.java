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
import com.pocket.rpg.editor.utils.SceneUtils;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.type.ImBoolean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final ImBoolean useFadeState = new ImBoolean();
    private final ImBoolean overrideDefaultsState = new ImBoolean();
    private final float[] fadeOutDuration = new float[1];
    private final float[] fadeInDuration = new float[1];

    private static final int ERROR_ROW_BG_COLOR = ImGui.colorConvertFloat4ToU32(1f, 0.1f, 0.1f, 0.7f);

    @Override
    public boolean draw() {
        AtomicBoolean changed = new AtomicBoolean(false);

        // Destination section
        ImGui.text("Destination");
        ImGui.separator();

        changed.set(changed.get() | drawSceneDropdown());
        ImGui.spacing();
        changed.set(changed.get() | drawSpawnPointDropdown());

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
        if (ImGui.isItemDeactivatedAfterEdit()) changed.set(true);

        ImGui.spacing();
        ImGui.spacing();

        // Audio section
        ImGui.text("Audio");
        ImGui.separator();

        changed.set(changed.get() | FieldEditors.drawAudioClip("Warp Out Sound", component, "warpOutSound", editorEntity()));

        ImGui.spacing();
        ImGui.spacing();

        // Transition section
        ImGui.text("Transition");
        ImGui.separator();

        // Use fade toggle
        useFadeState.set(component.isUseFade());
        FieldEditors.inspectorRow("Use Fade", () -> {
            if (ImGui.checkbox("##useFade", useFadeState)) {
                component.setUseFade(useFadeState.get());
                changed.set(true);
            }
        });
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Enable fade transition effect when warping.");
        }

        // Only show override option if useFade is enabled
        if (component.isUseFade()) {
            // Override defaults toggle
            overrideDefaultsState.set(component.isOverrideTransitionDefaults());
            FieldEditors.inspectorRow("Override Defaults", () -> {
                if (ImGui.checkbox("##overrideDefaults", overrideDefaultsState)) {
                    component.setOverrideTransitionDefaults(overrideDefaultsState.get());
                    changed.set(true);
                }
            });
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("Override the default transition settings from rendering config.");
            }

            // Only show custom settings if override is enabled
            if (component.isOverrideTransitionDefaults()) {
                // Fade out duration
                fadeOutDuration[0] = component.getFadeOutDuration();
                FieldEditors.inspectorRow("Fade Out", () -> {
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    if (ImGui.dragFloat("##fadeOut", fadeOutDuration, 0.01f, 0.0f, 5.0f, "%.2f s")) {
                        component.setFadeOutDuration(fadeOutDuration[0]);
                    }
                });
                if (ImGui.isItemDeactivatedAfterEdit()) changed.set(true);

                // Fade in duration
                fadeInDuration[0] = component.getFadeInDuration();
                FieldEditors.inspectorRow("Fade In", () -> {
                    ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
                    if (ImGui.dragFloat("##fadeIn", fadeInDuration, 0.01f, 0.0f, 5.0f, "%.2f s")) {
                        component.setFadeInDuration(fadeInDuration[0]);
                    }
                });
                if (ImGui.isItemDeactivatedAfterEdit()) changed.set(true);

                // Transition name dropdown
                drawTransitionNameDropdown(changed);
            }
        }

        // Preview
        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();
        drawDestinationPreview();

        return changed.get();
    }

    /**
     * Draws a transition name dropdown for the WarpZone.
     * Options: "(default)" + all entries from GameConfig + "Random".
     */
    private void drawTransitionNameDropdown(AtomicBoolean changed) {
        RenderingConfig renderingConfig = ConfigLoader.getConfig(ConfigLoader.ConfigType.RENDERING);
        List<TransitionEntry> entries = renderingConfig.getTransitions();

        String currentName = component.getTransitionName();
        String display;
        if (currentName == null || currentName.isEmpty()) {
            display = "(default)";
        } else {
            display = currentName;
        }

        FieldEditors.inspectorRow("Transition", () -> {
            ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
            if (ImGui.beginCombo("##transitionName", display)) {
                // Default option (empty name = use global default)
                boolean isDefault = currentName == null || currentName.isEmpty();
                if (ImGui.selectable("(default)", isDefault)) {
                    component.setTransitionName("");
                    changed.set(true);
                }

                // Random option
                boolean isRandom = "Random".equals(currentName);
                if (ImGui.selectable("Random", isRandom)) {
                    component.setTransitionName("Random");
                    changed.set(true);
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
                        component.setTransitionName(name);
                        changed.set(true);
                    }
                }

                ImGui.endCombo();
            }
        });
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Transition pattern to use. (default) uses the global setting.");
        }
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
                String id = spawn.getSpawnId();
                if (id != null && !id.isBlank()) {
                    spawnIds.add(id);
                }
            }
        }

        return spawnIds;
    }
}
