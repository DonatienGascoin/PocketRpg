package com.pocket.rpg.editor.panels.inspector;

import com.pocket.rpg.collision.CollisionMap;
import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.ElevationLevel;
import com.pocket.rpg.collision.trigger.*;
import com.pocket.rpg.editor.core.MaterialIcons;
import com.pocket.rpg.editor.scene.EditorScene;
import com.pocket.rpg.editor.ui.fields.FieldEditors;
import com.pocket.rpg.editor.utils.SceneUtils;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Inspector panel for editing trigger properties.
 * Uses FieldEditors for consistent layout and undo support.
 * Changes are auto-applied when fields are edited.
 */
public class TriggerInspector {

    @Setter
    private EditorScene scene;

    private TileCoord selectedTile;
    private CollisionType collisionType;
    private TriggerData currentData;

    // Cached spawn point IDs for dropdown
    private List<String> localSpawnIds = new ArrayList<>();

    // Cached available scenes for dropdown
    private List<String> availableScenes = new ArrayList<>();
    private long scenesLastRefreshed = 0;
    private static final long SCENE_REFRESH_INTERVAL_MS = 5000; // Refresh every 5 seconds

    // Cached cross-scene spawn points
    private String cachedCrossSceneTarget = null;
    private List<String> crossSceneSpawnIds = new ArrayList<>();

    public void setSelectedTile(TileCoord tile) {
        if (tile == null) {
            this.selectedTile = null;
            this.collisionType = null;
            this.currentData = null;
            return;
        }

        this.selectedTile = tile;

        // Get collision type and trigger data
        if (scene != null) {
            this.collisionType = scene.getCollisionMap().get(tile.x(), tile.y(), tile.elevation());
            this.currentData = scene.getTriggerDataMap().get(tile.x(), tile.y(), tile.elevation());
            refreshLocalSpawnIds();
        }
    }

    private void refreshLocalSpawnIds() {
        localSpawnIds.clear();
        if (scene == null) return;

        CollisionMap collisionMap = scene.getCollisionMap();
        TriggerDataMap triggerDataMap = scene.getTriggerDataMap();
        if (collisionMap == null || triggerDataMap == null) return;

        // Scan all elevations for spawn points
        for (int elevation : collisionMap.getZLevels()) {
            for (CollisionMap.CollisionChunk chunk : collisionMap.getChunksForLevel(elevation)) {
                int baseX = chunk.getChunkX() * CollisionMap.CollisionChunk.CHUNK_SIZE;
                int baseY = chunk.getChunkY() * CollisionMap.CollisionChunk.CHUNK_SIZE;

                for (int tx = 0; tx < CollisionMap.CollisionChunk.CHUNK_SIZE; tx++) {
                    for (int ty = 0; ty < CollisionMap.CollisionChunk.CHUNK_SIZE; ty++) {
                        CollisionType type = chunk.get(tx, ty);
                        if (type == CollisionType.SPAWN_POINT) {
                            TriggerData data = triggerDataMap.get(baseX + tx, baseY + ty, elevation);
                            if (data instanceof SpawnPointData spawn && spawn.id() != null && !spawn.id().isBlank()) {
                                localSpawnIds.add(spawn.id());
                            }
                        }
                    }
                }
            }
        }
    }

    private void refreshAvailableScenes() {
        long now = System.currentTimeMillis();
        if (now - scenesLastRefreshed < SCENE_REFRESH_INTERVAL_MS) {
            return; // Use cached value
        }

        availableScenes = SceneUtils.getAvailableSceneNames();

        // Remove current scene from the list (can't warp to same scene with scene field)
        if (scene != null) {
            String currentSceneName = scene.getName();
            availableScenes.remove(currentSceneName);
        }

        scenesLastRefreshed = now;
    }

    private void refreshCrossSceneSpawnIds(String targetScene) {
        if (targetScene == null || targetScene.isBlank()) {
            crossSceneSpawnIds.clear();
            cachedCrossSceneTarget = null;
            return;
        }

        // Only refresh if target scene changed
        if (targetScene.equals(cachedCrossSceneTarget)) {
            return;
        }

        crossSceneSpawnIds = SceneUtils.getSpawnPoints(targetScene);
        cachedCrossSceneTarget = targetScene;
    }

    public void render() {
        if (selectedTile == null) {
            renderNoSelection();
            return;
        }

        if (collisionType == null || !collisionType.requiresMetadata()) {
            renderNotATrigger();
            return;
        }

        renderHeader();
        ImGui.separator();

        // Type-specific explanation and editor
        switch (collisionType) {
            case WARP -> {
                renderWarpExplanation();
                renderWarpEditor();
            }
            case DOOR -> {
                renderDoorExplanation();
                renderDoorEditor();
            }
            case STAIRS -> {
                renderStairsExplanation();
                renderStairsEditor();
            }
            case SPAWN_POINT -> {
                renderSpawnPointExplanation();
                renderSpawnPointEditor();
            }
            default -> ImGui.textDisabled("No editor for " + collisionType);
        }

        // Common options only for triggers with configurable activation
        // (not spawn points or stairs which have fixed activation modes)
        if (collisionType != CollisionType.SPAWN_POINT && collisionType != CollisionType.STAIRS) {
            ImGui.separator();
            renderCommonOptions();
        }

        ImGui.separator();
        renderDeleteAction();
    }

    private void renderNoSelection() {
        ImGui.textDisabled("No trigger selected");
        ImGui.spacing();
        ImGui.textWrapped("Select a trigger tile in the Scene View or from the Triggers list in the Collision Panel.");
    }

    private void renderNotATrigger() {
        ImGui.textDisabled("Not a trigger tile");
        ImGui.text("Position: (" + selectedTile.x() + ", " + selectedTile.y() + ")");
        if (collisionType != null) {
            ImGui.text("Type: " + collisionType.getDisplayName());
        }
    }

    private void renderHeader() {
        // Icon and type name
        if (collisionType.hasIcon()) {
            ImGui.text(collisionType.getIcon());
            ImGui.sameLine();
        }
        ImGui.text(collisionType.getDisplayName());

        // Position
        ImGui.textDisabled("at (" + selectedTile.x() + ", " + selectedTile.y() + ", elev=" + selectedTile.elevation() + ")");

        // Validation warnings
        if (currentData != null) {
            List<String> errors = currentData.validate();
            if (!errors.isEmpty()) {
                ImGui.spacing();
                ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.5f, 0.2f, 1.0f);
                for (String error : errors) {
                    ImGui.text(MaterialIcons.Warning + " " + error);
                }
                ImGui.popStyleColor();
            }
        } else {
            ImGui.spacing();
            ImGui.pushStyleColor(ImGuiCol.Text, 1.0f, 0.5f, 0.2f, 1.0f);
            ImGui.text(MaterialIcons.Warning + " Not configured");
            ImGui.text("Fill in the fields below.");
            ImGui.popStyleColor();
        }
    }

    // ========== EXPLANATIONS ==========

    private void renderWarpExplanation() {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.7f, 0.7f, 1.0f);
        ImGui.textWrapped("Warp teleports the player to a spawn point. " +
                "Leave 'Scene' empty for same-scene teleport, or specify another scene.");
        ImGui.popStyleColor();
        ImGui.spacing();
    }

    private void renderDoorExplanation() {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.7f, 0.7f, 1.0f);
        ImGui.textWrapped("Door can be locked, requiring a key item. " +
                "Optionally teleports to a spawn point after unlocking.");
        ImGui.popStyleColor();
        ImGui.spacing();
    }

    private void renderStairsExplanation() {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.7f, 0.7f, 1.0f);
        ImGui.textWrapped("Stairs change elevation based on exit direction. " +
                "Configure which direction(s) change elevation and by how much. " +
                "Triggers on tile EXIT, not enter - making stairs naturally bidirectional.");
        ImGui.popStyleColor();
        ImGui.spacing();
    }

    private void renderSpawnPointExplanation() {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.7f, 0.7f, 0.7f, 1.0f);
        ImGui.textWrapped("Spawn point is an arrival marker. Warps and doors reference " +
                "spawn points by ID. Give each spawn point a unique, descriptive name.");
        ImGui.popStyleColor();
        ImGui.spacing();
    }

    // ========== SPAWN POINT EDITOR ==========

    private void renderSpawnPointEditor() {
        SpawnPointData spawn = currentData instanceof SpawnPointData s ? s : getDefaultSpawnPoint();

        FieldEditors.drawString("ID", "spawn_id",
                spawn::id,
                value -> applySpawnPoint(spawn.withId(value)));

        ImGui.spacing();

        // Facing direction dropdown
        Direction current = spawn.facingDirection();
        String currentName = current != null ? current.name() : "None";

        ImGui.text("Facing Direction");
        ImGui.setNextItemWidth(-1);
        if (ImGui.beginCombo("##spawn_facing", currentName)) {
            if (ImGui.selectable("None", current == null)) {
                applySpawnPoint(spawn.withFacingDirection(null));
            }
            for (Direction dir : Direction.values()) {
                if (ImGui.selectable(dir.name(), dir == current)) {
                    applySpawnPoint(spawn.withFacingDirection(dir));
                }
            }
            ImGui.endCombo();
        }
        ImGui.textDisabled("Direction player faces after spawning");
    }

    private void applySpawnPoint(SpawnPointData spawn) {
        saveTriggerData(spawn);
        refreshLocalSpawnIds(); // Update dropdown list
    }

    private SpawnPointData getDefaultSpawnPoint() {
        return new SpawnPointData("", null, ActivationMode.ON_ENTER, false, true);
    }

    // ========== WARP EDITOR ==========

    private void renderWarpEditor() {
        WarpTriggerData warp = currentData instanceof WarpTriggerData w ? w : getDefaultWarp();

        boolean isCrossScene = warp.isCrossScene();

        // Scene dropdown
        refreshAvailableScenes();
        renderSceneDropdown("Target Scene", "warp_scene", warp.targetScene(),
                value -> applyWarp(warp.withTargetScene(value)));
        ImGui.textDisabled("Empty = same scene");

        ImGui.spacing();

        // Spawn point selector
        if (isCrossScene) {
            // Cross-scene: dropdown of target scene's spawn points
            refreshCrossSceneSpawnIds(warp.targetScene());
            renderCrossSceneSpawnDropdown("Spawn Point", warp.targetSpawnId(),
                    value -> applyWarp(warp.withTargetSpawnId(value)));
        } else {
            // Same scene: dropdown of local spawn points
            renderSpawnPointDropdown("Spawn Point", warp.targetSpawnId(),
                    value -> applyWarp(warp.withTargetSpawnId(value)));
        }

        ImGui.spacing();

        FieldEditors.drawEnum("Transition", "warp_transition",
                warp::transition,
                value -> applyWarp(warp.withTransition(value)),
                TransitionType.class);
    }

    private void applyWarp(WarpTriggerData warp) {
        saveTriggerData(warp);
    }

    private WarpTriggerData getDefaultWarp() {
        return new WarpTriggerData("", "",
                TransitionType.FADE,
                ActivationMode.ON_ENTER,
                false,
                true);
    }

    // ========== DOOR EDITOR ==========

    private void renderDoorEditor() {
        DoorTriggerData door = currentData instanceof DoorTriggerData d ? d : getDefaultDoor();

        // Lock settings
        FieldEditors.drawBoolean("Locked", "door_locked",
                door::locked,
                value -> applyDoor(door.withLocked(value)));

        if (door.locked()) {
            ImGui.indent();

            FieldEditors.drawString("Required Key", "door_key",
                    door::requiredKey,
                    value -> applyDoor(door.withRequiredKey(value)));

            FieldEditors.drawBoolean("Consume Key", "door_consume",
                    door::consumeKey,
                    value -> applyDoor(door.withConsumeKey(value)));

            FieldEditors.drawString("Locked Message", "door_msg",
                    door::lockedMessage,
                    value -> applyDoor(door.withLockedMessage(value)));

            ImGui.unindent();
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Destination
        ImGui.text("Destination");
        ImGui.textDisabled("Leave empty to unlock in place");

        boolean isCrossScene = door.isCrossScene();

        refreshAvailableScenes();
        renderSceneDropdown("Target Scene", "door_scene", door.targetScene(),
                value -> applyDoor(door.withTargetScene(value)));

        ImGui.spacing();

        if (isCrossScene) {
            // Cross-scene: dropdown of target scene's spawn points
            refreshCrossSceneSpawnIds(door.targetScene());
            renderCrossSceneSpawnDropdown("Spawn Point", door.targetSpawnId(),
                    value -> applyDoor(door.withTargetSpawnId(value)));
        } else if (door.hasDestination() || !localSpawnIds.isEmpty()) {
            // Same scene: dropdown of local spawn points
            renderSpawnPointDropdown("Spawn Point", door.targetSpawnId(),
                    value -> applyDoor(door.withTargetSpawnId(value)));
        }

        if (door.hasDestination()) {
            ImGui.spacing();
            FieldEditors.drawEnum("Transition", "door_transition",
                    door::transition,
                    value -> applyDoor(door.withTransition(value)),
                    TransitionType.class);
        }
    }

    private void applyDoor(DoorTriggerData door) {
        saveTriggerData(door);
    }

    private DoorTriggerData getDefaultDoor() {
        return new DoorTriggerData(false, "", false, "The door is locked.",
                "", "",
                TransitionType.FADE,
                ActivationMode.ON_INTERACT,
                false,
                true);
    }

    // ========== STAIRS EDITOR ==========

    private void renderStairsEditor() {
        StairsData stairs = currentData instanceof StairsData s ? s : getDefaultStairs();
        int currentElevation = selectedTile != null ? selectedTile.elevation() : 0;

        ImGui.text("Mono-Direction Stairs");
        ImGui.textDisabled("Elevation changes when exiting in the specified direction.");
        ImGui.spacing();

        // Show current elevation
        ImGui.text("Current: " + ElevationLevel.getDisplayName(currentElevation));
        ImGui.spacing();

        // Exit direction dropdown
        FieldEditors.drawEnum("Exit Direction", "stairs_direction",
                stairs::exitDirection,
                value -> applyStairs(stairs.withExitDirection(value)),
                Direction.class);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("The direction player must exit to trigger elevation change");
        }

        // Destination elevation dropdown
        int destElevation = currentElevation + stairs.elevationChange();
        String destArrow = stairs.elevationChange() > 0 ? MaterialIcons.ArrowUpward : MaterialIcons.ArrowDownward;
        String destLabel = destArrow + " " + ElevationLevel.getDisplayName(destElevation) + " (level " + destElevation + ")";

        ImGui.text("Destination");
        ImGui.sameLine();
        ImGui.setNextItemWidth(ImGui.getContentRegionAvailX());
        if (ImGui.beginCombo("##stairs_dest", destLabel)) {
            // Iterate in reverse order so lower levels appear at bottom
            ElevationLevel[] levels = ElevationLevel.getAll();
            for (int i = levels.length - 1; i >= 0; i--) {
                ElevationLevel level = levels[i];

                // Show current level as disabled indicator
                if (level.getLevel() == currentElevation) {
                    ImGui.beginDisabled();
                    ImGui.selectable("  " + level.getDisplayName() + " (level " + level.getLevel() + ") - current", false);
                    ImGui.endDisabled();
                    continue;
                }

                boolean isSelected = level.getLevel() == destElevation;
                String arrow = level.getLevel() > currentElevation ? MaterialIcons.ArrowUpward : MaterialIcons.ArrowDownward;
                String label = arrow + " " + level.getDisplayName() + " (level " + level.getLevel() + ")";

                if (ImGui.selectable(label, isSelected)) {
                    int change = level.getLevel() - currentElevation;
                    applyStairs(stairs.withElevationChange(change));
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("The elevation level player will be at after using stairs");
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.spacing();

        // Quick presets based on current elevation
        ImGui.text("Quick Actions");
        ImGui.spacing();

        float buttonWidth = (ImGui.getContentRegionAvailX() - 8) / 2;

        // Go Up button (if not already at max)
        boolean canGoUp = currentElevation < ElevationLevel.getMaxLevel();
        if (!canGoUp) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.ArrowUpward + " Go Up", buttonWidth, 0)) {
            applyStairs(StairsData.goingUp(Direction.UP));
        }
        if (!canGoUp) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            String destName = ElevationLevel.getDisplayName(currentElevation + 1);
            ImGui.setTooltip("Exit UP -> " + destName);
        }

        ImGui.sameLine();

        // Go Down button (if not already at min)
        boolean canGoDown = currentElevation > 0;
        if (!canGoDown) ImGui.beginDisabled();
        if (ImGui.button(MaterialIcons.ArrowDownward + " Go Down", buttonWidth, 0)) {
            applyStairs(StairsData.goingDown(Direction.DOWN));
        }
        if (!canGoDown) ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            String destName = ElevationLevel.getDisplayName(currentElevation - 1);
            ImGui.setTooltip("Exit DOWN -> " + destName);
        }

        // Info text
        ImGui.spacing();
        ImGui.textDisabled("For bidirectional stairs, place matching");
        ImGui.textDisabled("stairs at the destination elevation.");
    }

    private void applyStairs(StairsData stairs) {
        saveTriggerData(stairs);
    }

    private StairsData getDefaultStairs() {
        return new StairsData();  // Default: exit UP, +1 elevation
    }

    // ========== COMMON OPTIONS ==========

    private void renderCommonOptions() {
        ImGui.text("Options");
        ImGui.spacing();

        TriggerData data = currentData != null ? currentData : getDefaultForType();

        FieldEditors.drawEnum("Activation", "trigger_activation",
                data::activationMode,
                value -> applyCommon(data, value, data.oneShot(), data.playerOnly()),
                ActivationMode.class);

        FieldEditors.drawBoolean("One Shot", "trigger_oneshot",
                data::oneShot,
                value -> applyCommon(data, data.activationMode(), value, data.playerOnly()));
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Trigger fires only once per game session");
        }

        FieldEditors.drawBoolean("Player Only", "trigger_playeronly",
                data::playerOnly,
                value -> applyCommon(data, data.activationMode(), data.oneShot(), value));
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Only the player can activate this trigger");
        }
    }

    private void applyCommon(TriggerData data, ActivationMode activation, boolean oneShot, boolean playerOnly) {
        TriggerData newData = switch (data) {
            case WarpTriggerData w -> w.withCommon(activation, oneShot, playerOnly);
            case DoorTriggerData d -> d.withCommon(activation, oneShot, playerOnly);
            case StairsData s -> s;  // StairsData has fixed activation mode (ON_EXIT)
            case SpawnPointData sp -> sp.withCommon(activation, oneShot, playerOnly);
        };
        saveTriggerData(newData);
    }

    private TriggerData getDefaultForType() {
        return switch (collisionType) {
            case WARP -> getDefaultWarp();
            case DOOR -> getDefaultDoor();
            case STAIRS -> getDefaultStairs();
            case SPAWN_POINT -> getDefaultSpawnPoint();
            default -> getDefaultWarp();
        };
    }

    // ========== ACTIONS ==========

    private void renderDeleteAction() {
        // Reset button - clears trigger data but keeps collision tile
        if (ImGui.button("Reset", ImGui.getContentRegionAvailX() * 0.5f - 4, 0)) {
            resetTriggerData();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Clear configuration (keeps collision tile)");
        }

        ImGui.sameLine();

        // Delete button - removes both trigger data AND collision tile
        ImGui.pushStyleColor(ImGuiCol.Button, 0.6f, 0.2f, 0.2f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.7f, 0.3f, 0.3f, 1.0f);
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.5f, 0.15f, 0.15f, 1.0f);
        if (ImGui.button("Delete", -1, 0)) {
            ImGui.openPopup("ConfirmDeleteTrigger");
        }
        ImGui.popStyleColor(3);
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Remove tile completely");
        }

        // Confirmation popup for delete
        if (ImGui.beginPopupModal("ConfirmDeleteTrigger")) {
            ImGui.text("Delete this trigger tile?");
            ImGui.text("Both the collision tile and configuration will be removed.");
            ImGui.spacing();
            if (ImGui.button("Delete", 120, 0)) {
                deleteTriggerTile();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 120, 0)) {
                ImGui.closeCurrentPopup();
            }
            ImGui.endPopup();
        }
    }

    private void resetTriggerData() {
        if (scene == null || selectedTile == null) return;

        scene.getTriggerDataMap().remove(selectedTile.x(), selectedTile.y(), selectedTile.elevation());
        currentData = null;
        scene.markDirty();
    }

    private void deleteTriggerTile() {
        if (scene == null || selectedTile == null) return;

        // Remove trigger data
        scene.getTriggerDataMap().remove(selectedTile.x(), selectedTile.y(), selectedTile.elevation());

        // Remove collision tile
        scene.getCollisionMap().clear(selectedTile.x(), selectedTile.y(), selectedTile.elevation());

        // Clear selection
        currentData = null;
        collisionType = null;
        selectedTile = null;

        scene.markDirty();
    }

    // ========== HELPERS ==========

    private void renderSpawnPointDropdown(String label, String currentValue, java.util.function.Consumer<String> setter) {
        ImGui.text(label);
        ImGui.setNextItemWidth(-1);

        String display = (currentValue == null || currentValue.isBlank()) ? "(none)" : currentValue;

        if (ImGui.beginCombo("##" + label.toLowerCase().replace(" ", "_"), display)) {
            // Option to clear
            if (ImGui.selectable("(none)", currentValue == null || currentValue.isBlank())) {
                setter.accept("");
            }

            // Local spawn points
            for (String spawnId : localSpawnIds) {
                boolean isSelected = spawnId.equals(currentValue);
                if (ImGui.selectable(spawnId, isSelected)) {
                    setter.accept(spawnId);
                }
            }

            // Option to type custom (for spawns not yet created)
            ImGui.separator();
            ImGui.textDisabled("Type to add new:");

            ImGui.endCombo();
        }

        if (localSpawnIds.isEmpty()) {
            ImGui.textDisabled("No spawn points in scene");
        }
    }

    private void renderSceneDropdown(String label, String id, String currentValue, java.util.function.Consumer<String> setter) {
        ImGui.text(label);
        ImGui.setNextItemWidth(-1);

        String display = (currentValue == null || currentValue.isBlank()) ? "(same scene)" : currentValue;

        if (ImGui.beginCombo("##" + id, display)) {
            // Option for same scene (empty value)
            if (ImGui.selectable("(same scene)", currentValue == null || currentValue.isBlank())) {
                setter.accept("");
            }

            // Separator
            if (!availableScenes.isEmpty()) {
                ImGui.separator();
            }

            // Available scenes
            for (String sceneName : availableScenes) {
                boolean isSelected = sceneName.equals(currentValue);
                if (ImGui.selectable(sceneName, isSelected)) {
                    setter.accept(sceneName);
                }
            }

            ImGui.endCombo();
        }
    }

    private void renderCrossSceneSpawnDropdown(String label, String currentValue, java.util.function.Consumer<String> setter) {
        ImGui.text(label);
        ImGui.setNextItemWidth(-1);

        String display = (currentValue == null || currentValue.isBlank()) ? "(none)" : currentValue;

        if (ImGui.beginCombo("##cross_spawn", display)) {
            // Option to clear
            if (ImGui.selectable("(none)", currentValue == null || currentValue.isBlank())) {
                setter.accept("");
            }

            if (!crossSceneSpawnIds.isEmpty()) {
                ImGui.separator();
                // Spawn points from target scene
                for (String spawnId : crossSceneSpawnIds) {
                    boolean isSelected = spawnId.equals(currentValue);
                    if (ImGui.selectable(spawnId, isSelected)) {
                        setter.accept(spawnId);
                    }
                }
            } else {
                ImGui.separator();
                ImGui.textDisabled("No spawn points found");
                ImGui.textDisabled("(save target scene first)");
            }

            ImGui.endCombo();
        }

        if (crossSceneSpawnIds.isEmpty() && cachedCrossSceneTarget != null) {
            ImGui.textDisabled("Target scene has no spawn points");
        }
    }

    // ========== DATA SAVING ==========

    private void saveTriggerData(TriggerData data) {
        if (scene == null || selectedTile == null) return;

        scene.getTriggerDataMap().set(selectedTile.x(), selectedTile.y(), selectedTile.elevation(), data);
        currentData = data;
        scene.markDirty();
    }

    public boolean hasSelection() {
        return selectedTile != null && collisionType != null && collisionType.requiresMetadata();
    }

    public void clearSelection() {
        selectedTile = null;
        collisionType = null;
        currentData = null;
    }
}
