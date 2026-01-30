# Phase 1: Interactable Core

## Overview

This phase implements the core interaction system: the `Interactable` interface and the `InteractionController` component that detects and handles interactions.

---

## Interactable Interface

```java
package com.pocket.rpg.components.interaction;

import com.pocket.rpg.core.GameObject;

/**
 * Interface for objects that can be interacted with by the player.
 *
 * Implementations: Door, Chest, Lever, Sign, NPC, SecretPassage
 */
public interface Interactable {

    /**
     * Checks if the actor can currently interact with this object.
     * Consider: distance, facing direction, prerequisites, cooldowns.
     *
     * @param actor The entity attempting to interact (usually player)
     * @return true if interaction is allowed
     */
    boolean canInteract(GameObject actor);

    /**
     * Performs the interaction.
     * Called when player presses interact button and canInteract() returns true.
     *
     * @param actor The entity performing the interaction
     */
    void interact(GameObject actor);

    /**
     * Gets the prompt text shown to the player.
     * Examples: "Open", "Talk", "Read", "Pick up"
     *
     * @return Short action text for UI prompt
     */
    String getInteractionPrompt();

    /**
     * Gets the world position of this interactable.
     * Used for distance calculations.
     *
     * @return Position vector
     */
    org.joml.Vector3f getPosition();

    /**
     * Priority for interaction selection when multiple are in range.
     * Lower values = higher priority.
     * Default: 0
     *
     * @return Priority value
     */
    default int getInteractionPriority() {
        return 0;
    }

    /**
     * Whether this interactable requires the actor to face it.
     * Default: true (most interactables need facing)
     *
     * @return true if facing is required
     */
    default boolean requiresFacing() {
        return true;
    }

    /**
     * Maximum distance for interaction.
     * Default: 1.5 tiles
     *
     * @return Interaction radius in world units
     */
    default float getInteractionRadius() {
        return 1.5f;
    }
}
```

---

## InteractionController Component

```java
package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.GridMovement;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.core.input.Input;
import com.pocket.rpg.core.input.Key;
import com.pocket.rpg.serialization.Transient;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Detects nearby Interactables and handles player interaction input.
 * Attach to the player GameObject.
 *
 * Features:
 * - Scans for Interactables within radius
 * - Filters by distance, facing, canInteract
 * - Shows interaction prompt via callback
 * - Handles interact input (E or Space)
 */
public class InteractionController extends Component {

    /**
     * Maximum distance to detect interactables.
     */
    @Getter @Setter
    private float detectionRadius = 2.0f;

    /**
     * Key to trigger interaction.
     */
    @Getter @Setter
    private Key interactKey = Key.E;

    /**
     * Alternative key for interaction.
     */
    @Getter @Setter
    private Key interactKeyAlt = Key.SPACE;

    /**
     * Callback for showing/hiding interaction prompt.
     * Called with (prompt, show) - prompt text and visibility.
     */
    @Transient
    @Setter
    private InteractionPromptCallback promptCallback;

    // Runtime state
    @Transient
    private Interactable currentTarget;

    @Transient
    private GridMovement gridMovement; // For facing direction

    @Override
    public void start() {
        gridMovement = gameObject.getComponent(GridMovement.class);
    }

    @Override
    public void update(float deltaTime) {
        // Find best interaction target
        Interactable newTarget = findBestTarget();

        // Target changed?
        if (newTarget != currentTarget) {
            currentTarget = newTarget;
            updatePrompt();
        }

        // Handle input
        if (currentTarget != null) {
            if (Input.isKeyJustPressed(interactKey) || Input.isKeyJustPressed(interactKeyAlt)) {
                if (currentTarget.canInteract(gameObject)) {
                    currentTarget.interact(gameObject);
                    // Re-check target after interaction (might have been consumed)
                    currentTarget = findBestTarget();
                    updatePrompt();
                }
            }
        }
    }

    /**
     * Finds the best interaction target based on distance, facing, and priority.
     */
    private Interactable findBestTarget() {
        List<Interactable> candidates = findNearbyInteractables();

        if (candidates.isEmpty()) {
            return null;
        }

        Vector3f myPos = gameObject.getTransform().getPosition();

        // Filter by canInteract and facing
        List<Interactable> valid = new ArrayList<>();
        for (Interactable interactable : candidates) {
            if (!interactable.canInteract(gameObject)) {
                continue;
            }

            // Check facing if required
            if (interactable.requiresFacing() && !isFacing(interactable)) {
                continue;
            }

            // Check distance within interactable's own radius
            float dist = distance(myPos, interactable.getPosition());
            if (dist > interactable.getInteractionRadius()) {
                continue;
            }

            valid.add(interactable);
        }

        if (valid.isEmpty()) {
            return null;
        }

        // Sort by priority, then distance
        valid.sort(Comparator
            .comparingInt(Interactable::getInteractionPriority)
            .thenComparingDouble(i -> distance(myPos, i.getPosition()))
        );

        return valid.get(0);
    }

    /**
     * Finds all Interactables within detection radius.
     */
    private List<Interactable> findNearbyInteractables() {
        List<Interactable> result = new ArrayList<>();
        Vector3f myPos = gameObject.getTransform().getPosition();

        // Iterate all GameObjects in scene
        for (GameObject go : getScene().getGameObjects()) {
            if (go == gameObject) continue; // Skip self

            // Check distance first (cheap)
            float dist = distance(myPos, go.getTransform().getPosition());
            if (dist > detectionRadius) continue;

            // Find Interactable components
            for (Component comp : go.getAllComponents()) {
                if (comp instanceof Interactable interactable) {
                    result.add(interactable);
                }
            }
        }

        return result;
    }

    /**
     * Checks if player is facing the interactable.
     */
    private boolean isFacing(Interactable target) {
        if (gridMovement == null) {
            return true; // No GridMovement = always facing
        }

        Vector3f myPos = gameObject.getTransform().getPosition();
        Vector3f targetPos = target.getPosition();

        float dx = targetPos.x - myPos.x;
        float dy = targetPos.y - myPos.y;

        // Determine which direction target is in
        Direction targetDir;
        if (Math.abs(dx) > Math.abs(dy)) {
            targetDir = dx > 0 ? Direction.RIGHT : Direction.LEFT;
        } else {
            targetDir = dy > 0 ? Direction.UP : Direction.DOWN;
        }

        // Check if player is facing that direction
        Direction facing = gridMovement.getFacingDirection();
        return facing == targetDir;
    }

    private float distance(Vector3f a, Vector3f b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void updatePrompt() {
        if (promptCallback == null) return;

        if (currentTarget != null) {
            promptCallback.showPrompt(currentTarget.getInteractionPrompt());
        } else {
            promptCallback.hidePrompt();
        }
    }

    /**
     * Gets the currently targeted interactable, if any.
     */
    public Interactable getCurrentTarget() {
        return currentTarget;
    }

    /**
     * Callback interface for interaction prompts.
     */
    @FunctionalInterface
    public interface InteractionPromptCallback {
        void showPrompt(String prompt);

        default void hidePrompt() {
            showPrompt(null);
        }
    }
}
```

---

## StaticOccupant Component

For static objects that block movement but don't move themselves. This is a lightweight alternative to `GridMovement` for objects like chests, pots, and signs.

```java
package com.pocket.rpg.components.interaction;

import com.pocket.rpg.collision.CollisionSystem;
import com.pocket.rpg.collision.TileCoord;
import com.pocket.rpg.components.Component;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers static objects with EntityOccupancyMap so they block movement.
 *
 * Use this for non-moving objects like chests, pots, signs, statues.
 * For moving entities, use GridMovement instead.
 *
 * Key principle: Collision map is for terrain (walls, water).
 * All objects (static or moving) use EntityOccupancyMap.
 */
public class StaticOccupant extends Component {

    /**
     * Tiles this object occupies.
     * Default: single tile at (0,0,0) relative to entity position.
     */
    @Getter @Setter
    private List<TileCoord> occupiedTiles = new ArrayList<>(List.of(new TileCoord(0, 0, 0)));

    /**
     * If true, tiles are relative to entity position.
     * If false, tiles are absolute world coordinates.
     */
    @Getter @Setter
    private boolean tilesRelative = true;

    @Transient
    private CollisionSystem collisionSystem;

    @Transient
    private List<TileCoord> registeredTiles;

    @Override
    public void onStart() {
        collisionSystem = getScene().getCollisionSystem();
        registeredTiles = getAbsoluteTiles();

        for (TileCoord tile : registeredTiles) {
            collisionSystem.registerEntity(this, tile);
        }
    }

    @Override
    public void onDestroy() {
        if (collisionSystem != null && registeredTiles != null) {
            for (TileCoord tile : registeredTiles) {
                collisionSystem.unregisterEntity(this, tile);
            }
        }
    }

    /**
     * Gets occupied tiles in world coordinates.
     */
    public List<TileCoord> getAbsoluteTiles() {
        if (!tilesRelative) {
            return new ArrayList<>(occupiedTiles);
        }

        Vector3f pos = getTransform().getPosition();
        int baseX = (int) pos.x;
        int baseY = (int) pos.y;
        int baseZ = (int) pos.z;

        List<TileCoord> absolute = new ArrayList<>();
        for (TileCoord tile : occupiedTiles) {
            absolute.add(new TileCoord(
                baseX + tile.x(),
                baseY + tile.y(),
                baseZ + tile.elevation()
            ));
        }
        return absolute;
    }

    /**
     * Checks if this occupant blocks the given tile.
     */
    public boolean occupies(int x, int y, int elevation) {
        for (TileCoord tile : getAbsoluteTiles()) {
            if (tile.x() == x && tile.y() == y && tile.elevation() == elevation) {
                return true;
            }
        }
        return false;
    }
}
```

### Why StaticOccupant Instead of Collision Map?

| Approach | Problem |
|----------|---------|
| Paint SOLID in collision map | Position sync issues if entity moves in editor |
| | Must remember to update both entity AND collision |
| | Multi-tile objects need multiple collision edits |
| Use StaticOccupant | Entity position = single source of truth |
| | Multi-tile objects just list tiles in component |
| | No manual collision painting needed |

### Usage

**Single-tile (pot, sign):**
```java
GameObject pot = new GameObject("Pot");
pot.addComponent(new SpriteRenderer());
pot.addComponent(new StaticOccupant());  // Default: blocks tile at entity position
```

**Multi-tile (table, large statue):**
```java
StaticOccupant occupant = new StaticOccupant();
occupant.setOccupiedTiles(List.of(
    new TileCoord(0, 0, 0),   // Left tile
    new TileCoord(1, 0, 0)    // Right tile
));
table.addComponent(occupant);
```

**With interactable (chest):**
```java
GameObject chest = new GameObject("Chest");
chest.addComponent(new SpriteRenderer());
chest.addComponent(new StaticOccupant());  // Blocking
chest.addComponent(new Chest());           // Behavior
```

---

## Interaction Prompt UI

The prompt UI can be implemented in multiple ways:

### Option A: World-Space UI (Above Interactable)

```java
// InteractionPromptUI component on a UI canvas
public class InteractionPromptUI extends UIComponent {
    private InteractionController controller;
    private UIText promptText;
    private UIImage keyIcon;

    @Override
    public void update(float dt) {
        Interactable target = controller.getCurrentTarget();

        if (target != null) {
            setVisible(true);
            promptText.setText("[E] " + target.getInteractionPrompt());

            // Position above target
            Vector3f worldPos = target.getPosition();
            worldPos.y += 1.5f; // Above object
            Vector2f screenPos = camera.worldToScreen(worldPos);
            setPosition(screenPos);
        } else {
            setVisible(false);
        }
    }
}
```

### Option B: Screen-Space UI (Bottom of Screen)

```java
// Fixed position prompt
public class BottomPromptUI extends UIComponent {
    @Override
    public void update(float dt) {
        Interactable target = controller.getCurrentTarget();

        if (target != null) {
            setVisible(true);
            promptText.setText("Press E to " + target.getInteractionPrompt());
        } else {
            setVisible(false);
        }
    }
}
```

### Option C: Minimal (Keyboard Icon Only)

Show just a key icon near the interactable:
```
    [E]
   ┌───┐
   │🚪 │
   └───┘
```

---

## Scene Helper Method

Add to Scene.java for easier component searching:

```java
/**
 * Finds all components of a type within radius of a position.
 *
 * @param position Center position
 * @param radius   Search radius
 * @param type     Component type to find
 * @return List of matching components
 */
public <T> List<T> findComponentsInRadius(Vector3f position, float radius, Class<T> type) {
    List<T> result = new ArrayList<>();
    float radiusSq = radius * radius;

    for (GameObject go : gameObjects) {
        Vector3f goPos = go.getTransform().getPosition();
        float dx = goPos.x - position.x;
        float dy = goPos.y - position.y;
        float distSq = dx * dx + dy * dy;

        if (distSq <= radiusSq) {
            for (Component comp : go.getAllComponents()) {
                if (type.isInstance(comp)) {
                    result.add(type.cast(comp));
                }
            }
        }
    }

    return result;
}
```

---

## Files to Create/Modify

### New Files

| File | Purpose |
|------|---------|
| `components/interaction/Interactable.java` | Core interface |
| `components/interaction/InteractionController.java` | Player component |
| `components/interaction/StaticOccupant.java` | Blocking for static objects |

### Modified Files

| File | Change |
|------|--------|
| `scenes/Scene.java` | Add `findComponentsInRadius()` |
| `components/GridMovement.java` | Ensure `getFacingDirection()` exists |

---

## Testing Checklist

### InteractionController
- [ ] Detects nearby Interactables
- [ ] Distance filtering works correctly
- [ ] Facing requirement filters correctly
- [ ] Priority sorting selects closest/highest priority
- [ ] E key triggers interaction
- [ ] Prompt callback is invoked on target change
- [ ] No interaction when no valid target
- [ ] Works without GridMovement (facing check skipped)

### StaticOccupant
- [ ] Registers with EntityOccupancyMap on start
- [ ] Unregisters on destroy
- [ ] Player blocked from moving into occupied tile
- [ ] Relative tiles calculated correctly from entity position
- [ ] Multi-tile objects block all listed tiles
- [ ] Moving entity in editor updates blocked tiles on play

---

## Next Phase

Phase 2: Door Component - First concrete implementation of Interactable
