package com.pocket.rpg.components.interaction;

import com.pocket.rpg.collision.TileEntityMap;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.GridMovement;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.InputAction;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Detects nearby interactables and handles player interaction input.
 * <p>
 * Attach to the player entity. Scans adjacent tiles for Interactable
 * components and allows interaction when the interact key is pressed.
 * <p>
 * Works with the interaction prompt UI to show "Press E to [action]".
 *
 * <h2>Usage</h2>
 * <pre>
 * GameObject player = new GameObject("Player");
 * player.addComponent(new GridMovement());
 * player.addComponent(new InteractionController());
 * </pre>
 */
@ComponentMeta(category = "Interaction")
public class InteractionController extends Component {

    /**
     * Maximum range for interaction detection (in tiles).
     * Default 1 = only adjacent tiles.
     */
    @Getter
    @Setter
    private int interactionRange = 1;

    /**
     * If true, only check tile in facing direction.
     * If false, check all adjacent tiles.
     */
    @Getter
    @Setter
    private boolean facingOnly = true;

    // Runtime state
    private transient TileEntityMap tileEntityMap;
    private transient GridMovement gridMovement;
    private transient Interactable currentTarget;
    private transient GameObject currentTargetObject;

    @Override
    protected void onStart() {
        tileEntityMap = getTileEntityMap();
        gridMovement = gameObject.getComponent(GridMovement.class);
    }

    @Override
    public void update(float deltaTime) {
        updateCurrentTarget();
        handleInput();
    }

    /**
     * Gets the current interaction target, if any.
     *
     * @return The Interactable currently in range, or null
     */
    public Interactable getCurrentTarget() {
        return currentTarget;
    }

    /**
     * Gets the GameObject of the current target.
     *
     * @return The target's GameObject, or null
     */
    public GameObject getCurrentTargetObject() {
        return currentTargetObject;
    }

    /**
     * Checks if there's an interactable in range.
     *
     * @return true if an interactable is available
     */
    public boolean hasTarget() {
        return currentTarget != null && currentTarget.canInteract(gameObject);
    }

    /**
     * Gets the interaction prompt text for the current target.
     *
     * @return Prompt text like "Open" or "Talk", or null if no target
     */
    public String getPromptText() {
        if (currentTarget == null) return null;
        String prompt = currentTarget.getInteractionPrompt();
        return prompt != null ? prompt : "Interact";
    }

    /**
     * Triggers interaction with the current target.
     * Called automatically when interact key is pressed, but can also
     * be called programmatically.
     */
    public void triggerInteraction() {
        if (currentTarget != null && currentTarget.canInteract(gameObject)) {
            currentTarget.interact(gameObject);
        }
    }

    private void updateCurrentTarget() {
        if (tileEntityMap == null) {
            currentTarget = null;
            currentTargetObject = null;
            return;
        }

        List<InteractableCandidate> candidates = findInteractables();

        if (candidates.isEmpty()) {
            currentTarget = null;
            currentTargetObject = null;
            return;
        }

        // Sort by priority (highest first), then by distance
        candidates.sort(Comparator
                .comparingInt((InteractableCandidate c) -> -c.interactable.getInteractionPriority())
                .thenComparingDouble(c -> c.distance));

        InteractableCandidate best = candidates.get(0);
        currentTarget = best.interactable;
        currentTargetObject = best.gameObject;
    }

    private List<InteractableCandidate> findInteractables() {
        List<InteractableCandidate> result = new ArrayList<>();

        Vector3f pos = getTransform().getPosition();
        int playerX = (int) Math.floor(pos.x);
        int playerY = (int) Math.floor(pos.y);
        int playerZ = getElevation();

        if (facingOnly && gridMovement != null) {
            // Only check the tile in facing direction
            int dx = gridMovement.getFacingDirection().dx;
            int dy = gridMovement.getFacingDirection().dy;

            for (int dist = 1; dist <= interactionRange; dist++) {
                int checkX = playerX + dx * dist;
                int checkY = playerY + dy * dist;
                addInteractablesAt(result, checkX, checkY, playerZ, dist);
            }
        } else {
            // Check all adjacent tiles
            for (int dx = -interactionRange; dx <= interactionRange; dx++) {
                for (int dy = -interactionRange; dy <= interactionRange; dy++) {
                    if (dx == 0 && dy == 0) continue;

                    int checkX = playerX + dx;
                    int checkY = playerY + dy;
                    double dist = Math.sqrt(dx * dx + dy * dy);

                    if (dist <= interactionRange) {
                        addInteractablesAt(result, checkX, checkY, playerZ, dist);
                    }
                }
            }
        }

        return result;
    }

    private void addInteractablesAt(List<InteractableCandidate> result,
                                     int x, int y, int z, double distance) {
        TileCoord coord = new TileCoord(x, y, z);
        Set<Component> components = tileEntityMap.getAll(coord);

        for (Component comp : components) {
            if (comp instanceof Interactable interactable) {
                if (interactable.canInteract(gameObject)) {
                    result.add(new InteractableCandidate(
                            interactable,
                            comp.getGameObject(),
                            distance
                    ));
                }
            }
        }
    }

    private void handleInput() {
        if (Input.hasContext() && Input.isActionPressed(InputAction.INTERACT)) {
            triggerInteraction();
        }
    }

    private int getElevation() {
        if (gridMovement != null) {
            return gridMovement.getZLevel();
        }
        return 0;
    }

    private TileEntityMap getTileEntityMap() {
        if (gameObject == null || gameObject.getScene() == null) {
            return null;
        }
        return gameObject.getScene().getCollisionSystem().getTileEntityMap();
    }

    private record InteractableCandidate(
            Interactable interactable,
            GameObject gameObject,
            double distance
    ) {}
}
