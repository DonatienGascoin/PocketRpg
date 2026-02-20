package com.pocket.rpg.save;

import com.pocket.rpg.components.interaction.SpawnPoint;
import com.pocket.rpg.components.pokemon.GridMovement;
import com.pocket.rpg.components.pokemon.PlayerMovement;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.scenes.SceneLifecycleListener;
import com.pocket.rpg.scenes.SceneManager;

/**
 * Handles player placement after scene loading.
 * <p>
 * Combines two concerns in a fixed order:
 * <ol>
 *   <li><b>Battle return</b> — if {@code PlayerData.returningFromBattle} is true,
 *       teleports the player to the saved position and clears the flag.</li>
 *   <li><b>Spawn teleport</b> — if a spawnId was provided for this scene load,
 *       teleports the player to the spawn point (overwrites battle-return position).</li>
 * </ol>
 * <p>
 * The ordering is enforced here rather than relying on listener registration order.
 * If no player entity exists in the scene (e.g., cutscene or battle scene), both
 * concerns are skipped and the battle-return flag is preserved for the next scene.
 */
public class PlayerPlacementHandler implements SceneLifecycleListener {

    private final SceneManager sceneManager;

    public PlayerPlacementHandler(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
    }

    @Override
    public void onPostSceneInitialize(Scene scene) {
        applyBattleReturn(scene);
        applySpawnTeleport(scene);
    }

    @Override
    public void onSceneLoaded(Scene scene) {}

    @Override
    public void onSceneUnloaded(Scene scene) {}

    /**
     * If returning from battle, teleports the player to the saved position
     * and clears the flag. If no player entity exists, the flag is preserved.
     */
    private void applyBattleReturn(Scene scene) {
        PlayerData data = PlayerData.load();
        if (data == null || !data.returningFromBattle) return;

        GameObject player = scene.findGameObjectByComponent(PlayerMovement.class);
        if (player == null) return; // No player — flag stays for next scene

        GridMovement gm = player.getComponent(GridMovement.class);
        if (gm != null) {
            gm.setGridPosition(data.lastGridX, data.lastGridY);
            gm.setFacingDirection(data.lastDirection);
        }

        data.returningFromBattle = false;
        data.save();
    }

    /**
     * If a spawnId was provided for this scene load, teleports the player
     * to the spawn point and applies facing direction and camera bounds.
     */
    private void applySpawnTeleport(Scene scene) {
        String spawnId = sceneManager.getPendingSpawnId();
        if (spawnId == null || spawnId.isEmpty()) return;

        GameObject player = scene.findGameObjectByComponent(PlayerMovement.class);
        if (player == null) {
            System.err.println("[PlayerPlacementHandler] No player entity found for spawn teleport");
            return;
        }

        SpawnPoint spawn = findSpawnPoint(scene, spawnId);
        if (spawn == null) {
            System.err.println("[PlayerPlacementHandler] Spawn point not found: " + spawnId);
            return;
        }

        scene.teleportToSpawn(player, spawnId);

        GridMovement gm = player.getComponent(GridMovement.class);
        if (gm != null && spawn.getFacingDirection() != null) {
            gm.setFacingDirection(spawn.getFacingDirection());
        }

        if (scene.getCamera() != null) {
            spawn.applyCameraBounds(scene.getCamera());
        }
    }

    private SpawnPoint findSpawnPoint(Scene scene, String spawnId) {
        for (GameObject go : scene.getGameObjects()) {
            SpawnPoint found = findSpawnPointRecursive(go, spawnId);
            if (found != null) return found;
        }
        return null;
    }

    private SpawnPoint findSpawnPointRecursive(GameObject go, String spawnId) {
        SpawnPoint sp = go.getComponent(SpawnPoint.class);
        if (sp != null && spawnId.equals(sp.getSpawnId())) {
            return sp;
        }
        for (GameObject child : go.getChildren()) {
            SpawnPoint found = findSpawnPointRecursive(child, spawnId);
            if (found != null) return found;
        }
        return null;
    }
}
