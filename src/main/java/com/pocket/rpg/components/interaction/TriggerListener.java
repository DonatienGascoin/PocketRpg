package com.pocket.rpg.components.interaction;

import com.pocket.rpg.core.GameObject;

/**
 * Interface for components that respond to trigger zone events.
 * <p>
 * Implementing classes:
 * - WarpZone (teleport on enter)
 * - CutsceneTrigger (start cutscene)
 * - DamageZone (hurt player)
 * - EventTrigger (custom scripting)
 *
 * <h2>Usage</h2>
 * <pre>
 * public class WarpZone extends Component implements TriggerListener {
 *     private String targetScene;
 *     private String targetSpawnId;
 *
 *     {@literal @}Override
 *     public void onTriggerEnter(GameObject entity, TriggerZone trigger) {
 *         SceneManager.transition(targetScene, targetSpawnId);
 *     }
 * }
 * </pre>
 */
public interface TriggerListener {

    /**
     * Called when an entity enters the trigger zone.
     *
     * @param entity  The entity that entered (usually player)
     * @param trigger The TriggerZone that was triggered
     */
    void onTriggerEnter(GameObject entity, TriggerZone trigger);

    /**
     * Called when an entity exits the trigger zone.
     * Default implementation does nothing.
     *
     * @param entity  The entity that exited
     * @param trigger The TriggerZone that was exited
     */
    default void onTriggerExit(GameObject entity, TriggerZone trigger) {
        // Override in subclasses if needed
    }
}
