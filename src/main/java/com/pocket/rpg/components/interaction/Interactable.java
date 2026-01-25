package com.pocket.rpg.components.interaction;

import com.pocket.rpg.core.GameObject;

/**
 * Interface for components that can be interacted with by the player.
 * <p>
 * Implementing components respond to player interaction (pressing E key).
 * Examples: Chest, Sign, NPC, Door, Lever.
 * <p>
 * The InteractionController on the player detects nearby Interactables
 * and calls {@link #interact(GameObject)} when the player presses interact.
 *
 * <h2>Usage</h2>
 * <pre>
 * public class Chest extends Component implements Interactable {
 *     private boolean opened = false;
 *
 *     {@literal @}Override
 *     public boolean canInteract(GameObject player) {
 *         return !opened;  // Can only open once
 *     }
 *
 *     {@literal @}Override
 *     public void interact(GameObject player) {
 *         opened = true;
 *         // Give items to player, play animation, etc.
 *     }
 *
 *     {@literal @}Override
 *     public String getInteractionPrompt() {
 *         return "Open";
 *     }
 * }
 * </pre>
 */
public interface Interactable {

    /**
     * Checks if this object can currently be interacted with.
     * <p>
     * Return false to temporarily disable interaction (e.g., chest already opened,
     * NPC is busy, door is locked without key).
     *
     * @param player The player attempting to interact
     * @return true if interaction is allowed
     */
    default boolean canInteract(GameObject player) {
        return true;
    }

    /**
     * Called when the player interacts with this object.
     * <p>
     * Implement your interaction logic here: open chest, show dialogue,
     * toggle switch, etc.
     *
     * @param player The player performing the interaction
     */
    void interact(GameObject player);

    /**
     * Gets the text shown in the interaction prompt.
     * <p>
     * Examples: "Open", "Talk", "Read", "Pull"
     * Shown as "Press E to [prompt]"
     *
     * @return The action verb for the prompt, or null for default "Interact"
     */
    default String getInteractionPrompt() {
        return null;  // Use default prompt
    }

    /**
     * Gets the priority for interaction selection.
     * <p>
     * When multiple interactables are in range, the one with highest
     * priority is selected. Default is 0.
     * <p>
     * Examples:
     * - NPCs: 10 (prefer talking to NPCs)
     * - Chests: 5
     * - Signs: 0
     *
     * @return Priority value (higher = more important)
     */
    default int getInteractionPriority() {
        return 0;
    }
}
