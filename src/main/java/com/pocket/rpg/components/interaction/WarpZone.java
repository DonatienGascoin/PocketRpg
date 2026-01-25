package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import com.pocket.rpg.editor.gizmos.GizmoDrawableSelected;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Teleports the player to another scene or spawn point when triggered.
 * <p>
 * Requires a TriggerZone on the same GameObject to detect player entry.
 * When the player enters the trigger, WarpZone initiates the scene transition.
 *
 * <h2>Usage</h2>
 * <pre>
 * // Warp to another scene
 * GameObject warp = new GameObject("Warp_To_Cave");
 * warp.addComponent(new TriggerZone());
 * WarpZone warpZone = new WarpZone();
 * warpZone.setTargetScene("cave_entrance");
 * warpZone.setTargetSpawnId("from_forest");
 * warp.addComponent(warpZone);
 *
 * // Warp within same scene (just move player)
 * WarpZone localWarp = new WarpZone();
 * localWarp.setTargetSpawnId("secret_area");
 * // Leave targetScene empty for same-scene warp
 * </pre>
 */
@ComponentMeta(category = "Interaction")
public class WarpZone extends Component implements TriggerListener, GizmoDrawableSelected {

    /**
     * Target scene to load. Leave empty to warp within the same scene.
     */
    @Getter
    @Setter
    private String targetScene = "";

    /**
     * Target spawn point ID in the destination scene.
     * Must match a SpawnPoint's spawnId.
     */
    @Getter
    @Setter
    private String targetSpawnId = "";

    /**
     * If true, show warp destination in editor gizmo.
     */
    @Getter
    @Setter
    private boolean showDestinationLabel = true;

    // ========================================================================
    // TRIGGER LISTENER
    // ========================================================================

    @Override
    public void onTriggerEnter(GameObject entity, TriggerZone trigger) {
        // Only warp player
        String name = entity.getName();
        if (name == null || !name.contains("Player")) {
            return;
        }

        executeWarp(entity);
    }

    @Override
    public void onTriggerExit(GameObject entity, TriggerZone trigger) {
        // Not used
    }

    // ========================================================================
    // WARP EXECUTION
    // ========================================================================

    /**
     * Executes the warp transition.
     */
    private void executeWarp(GameObject player) {
        System.out.println("[WarpZone] Warping to: " +
            (targetScene.isEmpty() ? "(same scene)" : targetScene) +
            " spawn: " + targetSpawnId);

        if (targetScene.isEmpty()) {
            // Same-scene warp: just move player to spawn point
            warpWithinScene(player);
        } else {
            // Scene transition
            warpToScene();
        }
    }

    /**
     * Warps player to a spawn point within the current scene.
     */
    private void warpWithinScene(GameObject player) {
        SpawnPoint spawn = findSpawnPoint(targetSpawnId);
        if (spawn == null) {
            System.err.println("[WarpZone] Spawn point not found: " + targetSpawnId);
            return;
        }

        // Move player to spawn position
        Vector3f spawnPos = spawn.getSpawnPosition();
        player.getTransform().setPosition(spawnPos);

        // TODO: Set facing direction
        // TODO: Play warp effect/sound

        System.out.println("[WarpZone] Warped player to: " + spawnPos);
    }

    /**
     * Initiates transition to another scene.
     */
    private void warpToScene() {
        // TODO: Implement scene transition when SceneManager API is finalized
        // Need to access SceneManager instance and call loadScene with spawn info
        // Options:
        // 1. Store spawn ID in a static/shared location for the new scene to read
        // 2. Pass spawn ID through SceneManager.loadScene(sceneName, spawnId)
        // 3. Use an event system
        System.out.println("[WarpZone] Scene transition not yet implemented: " + targetScene);
    }

    /**
     * Finds a SpawnPoint in the current scene by ID.
     */
    private SpawnPoint findSpawnPoint(String spawnId) {
        if (gameObject == null || gameObject.getScene() == null) {
            return null;
        }

        // Search all game objects for matching SpawnPoint
        for (GameObject obj : gameObject.getScene().getGameObjects()) {
            SpawnPoint spawn = obj.getComponent(SpawnPoint.class);
            if (spawn != null && spawnId.equals(spawn.getSpawnId())) {
                return spawn;
            }
        }
        return null;
    }

    // ========================================================================
    // GIZMO DRAWING
    // ========================================================================

    @Override
    public void onDrawGizmosSelected(GizmoContext ctx) {
        Vector3f pos = ctx.getTransform().getPosition();
        float x = pos.x;
        float y = pos.y;

        // Draw warp symbol (portal-like)
        float size = 0.4f;

        // Outer ring
        ctx.setColor(GizmoColors.fromRGBA(0.6f, 0.2f, 1.0f, 0.6f));
        ctx.setThickness(3.0f);
        ctx.drawCircle(x + 0.5f, y + 0.5f, size);

        // Inner ring
        ctx.setColor(GizmoColors.fromRGBA(0.8f, 0.4f, 1.0f, 0.8f));
        ctx.setThickness(2.0f);
        ctx.drawCircle(x + 0.5f, y + 0.5f, size * 0.6f);

        // Center dot
        ctx.setColor(GizmoColors.fromRGBA(1.0f, 0.8f, 1.0f, 1.0f));
        ctx.drawCircleFilled(x + 0.5f, y + 0.5f, size * 0.15f);

        // Draw destination label
        if (showDestinationLabel) {
            String label = targetScene.isEmpty()
                ? "-> " + targetSpawnId
                : "-> " + targetScene + ":" + targetSpawnId;
            ctx.setColor(GizmoColors.fromRGBA(0.8f, 0.6f, 1.0f, 1.0f));
            ctx.drawText(x + 0.5f, y + 0.5f, label, 5, -20);
        }
    }
}
