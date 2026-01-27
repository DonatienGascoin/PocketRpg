package com.pocket.rpg.components.interaction;

import com.pocket.rpg.audio.Audio;
import com.pocket.rpg.audio.clips.AudioClip;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.ComponentRef;
import com.pocket.rpg.components.GridMovement;
import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.editor.gizmos.GizmoColors;
import com.pocket.rpg.editor.gizmos.GizmoContext;
import com.pocket.rpg.editor.gizmos.GizmoDrawable;
import com.pocket.rpg.scenes.transitions.SceneTransition;
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
public class WarpZone extends Component implements GizmoDrawable {

    /**
     * Reference to the TriggerZone that activates this warp.
     * Resolved automatically at runtime.
     */
    @ComponentRef
    private TriggerZone triggerZone;

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

    /**
     * Optional sound to play when warping out (departure sound).
     */
    @Getter
    @Setter
    private AudioClip warpOutSound;

    /**
     * If true, use a fade transition effect.
     */
    @Getter
    @Setter
    private boolean useFade = false;

    /**
     * If true, use custom transition settings instead of defaults from rendering config.
     */
    @Getter
    @Setter
    private boolean overrideTransitionDefaults = false;

    /**
     * Duration of fade-out in seconds (only used if overrideTransitionDefaults is true).
     */
    @Getter
    @Setter
    private float fadeOutDuration = 0.3f;

    /**
     * Duration of fade-in in seconds (only used if overrideTransitionDefaults is true).
     */
    @Getter
    @Setter
    private float fadeInDuration = 0.3f;

    /**
     * Type of transition effect (only used if overrideTransitionDefaults is true).
     */
    @Getter
    @Setter
    private TransitionConfig.TransitionType transitionType = TransitionConfig.TransitionType.FADE;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    protected void onStart() {
        // Register callback with TriggerZone (resolved via @ComponentRef)
        if (triggerZone != null) {
            triggerZone.setOnEnterCallback(this::onTriggerEnter);
        } else {
            System.err.println("[WarpZone] No TriggerZone found on " +
                (gameObject != null ? gameObject.getName() : "null"));
        }
    }

    /**
     * Called when an entity enters the trigger zone.
     */
    private void onTriggerEnter(GameObject entity, TriggerZone trigger) {
        // Only warp player
        String name = entity.getName();
        if (name == null || !name.contains("Player")) {
            return;
        }

        executeWarp(entity);
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

        // Play departure sound
        if (warpOutSound != null) {
            Audio.playOneShot(warpOutSound);
        }

        if (useFade && SceneTransition.isInitialized()) {
            // Fade warp: execute teleport at midpoint
            playLocalFadeTransition(() -> performTeleport(player, spawn));
        } else {
            // Instant warp: teleport immediately
            performTeleport(player, spawn);
        }
    }

    /**
     * Plays a fade transition for within-scene warps (no scene change).
     * Uses the SceneTransition fade overlay system to fade out, execute the action, then fade in.
     *
     * @param onMidpoint action to perform at the midpoint (when screen is fully faded)
     */
    private void playLocalFadeTransition(Runnable onMidpoint) {
        if (overrideTransitionDefaults) {
            SceneTransition.playFadeEffect(onMidpoint, buildTransitionConfig());
        } else {
            SceneTransition.playFadeEffect(onMidpoint);
        }
    }

    /**
     * Builds a TransitionConfig from the inline fields (only used when overriding defaults).
     */
    private TransitionConfig buildTransitionConfig() {
        return TransitionConfig.builder()
                .fadeOutDuration(fadeOutDuration)
                .fadeInDuration(fadeInDuration)
                .type(transitionType)
                .build();
    }

    /**
     * Performs the actual teleport: moves player, sets facing, plays arrival sound.
     */
    private void performTeleport(GameObject player, SpawnPoint spawn) {
        Vector3f spawnPos = spawn.getSpawnPosition();
        int targetGridX = (int) Math.floor(spawnPos.x);
        int targetGridY = (int) Math.floor(spawnPos.y);

        GridMovement gridMovement = player.getComponent(GridMovement.class);
        if (gridMovement != null) {
            // Update grid position (handles occupancy map + visual position)
            gridMovement.setGridPosition(targetGridX, targetGridY);
            // Set facing direction from spawn point
            if (spawn.getFacingDirection() != null) {
                gridMovement.setFacingDirection(spawn.getFacingDirection());
            }
        } else {
            // Fallback: just move transform directly
            player.getTransform().setPosition(spawnPos);
        }

        // Play arrival sound from spawn point
        AudioClip arrivalSound = spawn.getArrivalSound();
        if (arrivalSound != null) {
            Audio.playOneShot(arrivalSound);
        }

        System.out.println("[WarpZone] Warped player to grid: (" + targetGridX + ", " + targetGridY + ")");
    }

    /**
     * Initiates transition to another scene.
     */
    private void warpToScene() {
        if (!SceneTransition.isInitialized()) {
            System.err.println("[WarpZone] SceneTransition not initialized, cannot warp to scene: " + targetScene);
            return;
        }

        // Play departure sound
        if (warpOutSound != null) {
            Audio.playOneShot(warpOutSound);
        }

        // TODO: Store spawn ID so the new scene can position the player at targetSpawnId
        // Options:
        // 1. Store in static/shared location for the new scene to read
        // 2. Pass through SceneManager.loadScene(sceneName, spawnId)
        // 3. Use an event system

        if (!useFade) {
            // Instant scene change, no transition
            SceneTransition.loadSceneInstant(targetScene);
        } else if (overrideTransitionDefaults) {
            // Custom transition settings
            SceneTransition.loadScene(targetScene, buildTransitionConfig());
        } else {
            // Default transition from rendering config
            SceneTransition.loadScene(targetScene);
        }

        System.out.println("[WarpZone] Loading scene: " + targetScene);
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
    // GIZMO DRAWING - Always visible
    // ========================================================================

    @Override
    public void onDrawGizmos(GizmoContext ctx) {
        Vector3f pos = ctx.getTransform().getPosition();
        float x = pos.x;
        float y = pos.y;

        // Draw warp symbol (portal-like) at tile center
        float tileX = (float) Math.floor(x);
        float tileY = (float) Math.floor(y);
        float centerX = tileX + 0.5f;
        float centerY = tileY + 0.5f;
        float size = 0.35f;

        // Outer ring
        ctx.setColor(GizmoColors.fromRGBA(0.6f, 0.2f, 1.0f, 0.7f));
        ctx.setThickness(3.0f);
        ctx.drawCircle(centerX, centerY, size);

        // Inner ring
        ctx.setColor(GizmoColors.fromRGBA(0.8f, 0.4f, 1.0f, 0.9f));
        ctx.setThickness(2.0f);
        ctx.drawCircle(centerX, centerY, size * 0.6f);

        // Center dot
        ctx.setColor(GizmoColors.fromRGBA(1.0f, 0.8f, 1.0f, 1.0f));
        ctx.drawCircleFilled(centerX, centerY, size * 0.15f);

        // Draw destination label
        if (showDestinationLabel) {
            String label = targetScene.isEmpty()
                ? "-> " + targetSpawnId
                : "-> " + targetScene + ":" + targetSpawnId;
            ctx.setColor(GizmoColors.fromRGBA(0.8f, 0.6f, 1.0f, 1.0f));
            ctx.drawText(centerX, centerY, label, 5, -20);
        }
    }
}
