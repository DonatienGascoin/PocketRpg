# Phase 2: Door Component

## Overview

The Door component is the first concrete implementation of Interactable. It supports:
- Open/close toggle with visual feedback
- Locked state with key requirement
- Optional teleportation to another scene/spawn
- Audio feedback for all actions
- Movement blocking when closed

---

## Door Component

```java
package com.pocket.rpg.components.interaction;

import com.pocket.rpg.audio.AudioClip;
import com.pocket.rpg.collision.TileEntityMap;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.components.audio.AudioSource;
import com.pocket.rpg.components.inventory.Inventory;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.scenes.transitions.SceneTransition;
import com.pocket.rpg.serialization.ComponentRef;
import com.pocket.rpg.serialization.Transient;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive door that can be opened, locked, and optionally teleport.
 *
 * Door States:
 * - Closed + Unlocked: Can be opened
 * - Closed + Locked: Requires key to unlock first
 * - Open: Can be walked through, can be closed
 *
 * Movement Blocking:
 * - Door registers/unregisters with TileEntityMap via StaticOccupant
 * - When closed: registers as blocking (player can't walk through)
 * - When open: unregisters (player can walk through)
 * - Blocked tiles are relative to door position
 */
public class Door extends Component implements Interactable {

    // ========================================================================
    // CONFIGURATION - Lock Settings
    // ========================================================================

    /**
     * Whether the door starts locked.
     */
    @Getter
    @Setter
    private boolean locked = false;

    /**
     * Item ID required to unlock (checked in actor's Inventory).
     */
    @Getter
    @Setter
    private String requiredKey = "";

    /**
     * Whether to remove the key from inventory when used.
     */
    @Getter
    @Setter
    private boolean consumeKey = false;

    /**
     * Message shown when player tries locked door without key.
     */
    @Getter
    @Setter
    private String lockedMessage = "The door is locked.";

    // ========================================================================
    // CONFIGURATION - Destination (Optional)
    // ========================================================================

    /**
     * Target scene name (empty = no teleport, just open/close).
     */
    @Getter
    @Setter
    private String targetScene = "";

    /**
     * Spawn point ID in target scene.
     */
    @Getter
    @Setter
    private String targetSpawnId = "";

    // ========================================================================
    // CONFIGURATION - Visuals
    // ========================================================================

    /**
     * Sprite shown when door is closed.
     */
    @Getter
    @Setter
    private Sprite closedSprite;

    /**
     * Sprite shown when door is open.
     */
    @Getter
    @Setter
    private Sprite openSprite;

    // ========================================================================
    // CONFIGURATION - Audio
    // ========================================================================

    /**
     * Sound played when door opens.
     */
    @Getter
    @Setter
    private AudioClip openSound;

    /**
     * Sound played when door closes.
     */
    @Getter
    @Setter
    private AudioClip closeSound;

    /**
     * Sound played when door is unlocked.
     */
    @Getter
    @Setter
    private AudioClip unlockSound;

    /**
     * Sound played when player tries locked door without key.
     */
    @Getter
    @Setter
    private AudioClip lockedSound;

    // ========================================================================
    // CONFIGURATION - Collision Blocking
    // ========================================================================

    /**
     * Tiles to block when door is closed.
     * Coordinates are relative to door position or absolute.
     */
    @Getter
    @Setter
    private List<TileCoord> blockedTiles = new ArrayList<>();

    /**
     * If true, blockedTiles are relative to door position.
     * If false, blockedTiles are absolute world coordinates.
     */
    @Getter
    @Setter
    private boolean tilesRelative = true;

    /**
     * Elevation level for collision blocking.
     */
    @Getter
    @Setter
    private int elevation = 0;

    // ========================================================================
    // CONFIGURATION - Interaction
    // ========================================================================

    /**
     * Direction player must face to interact (null = any direction).
     */
    @Getter
    @Setter
    private Direction requiredFacing;

    /**
     * Maximum interaction distance.
     */
    @Getter
    @Setter
    private float interactionRadius = 1.5f;

    // ========================================================================
    // COMPONENT REFERENCES
    // ========================================================================

    @ComponentRef
    private SpriteRenderer spriteRenderer;

    @ComponentRef
    private AudioSource audioSource;

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    @Transient
    @Getter
    private boolean open = false;

    /**
     * Currently registered blocking tiles (for cleanup on open/destroy).
     */
    @Transient
    private List<TileCoord> registeredTiles = new ArrayList<>();

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void start() {
        // Apply initial state
        if (spriteRenderer != null && closedSprite != null) {
            spriteRenderer.setSprite(closedSprite);
        }

        // Set initial collision
        updateCollision();
    }

    // ========================================================================
    // INTERACTABLE IMPLEMENTATION
    // ========================================================================

    @Override
    public boolean canInteract(GameObject actor) {
        // Check distance
        float dist = distanceTo(actor);
        if (dist > interactionRadius) {
            return false;
        }

        // Always can interact (even if locked - will show message)
        return true;
    }

    @Override
    public void interact(GameObject actor) {
        // If locked, try to unlock first
        if (locked) {
            if (tryUnlock(actor)) {
                // Successfully unlocked
                locked = false;
                playSound(unlockSound);
                showMessage("Unlocked!");

                // If door has destination, immediately use it
                if (hasDestination()) {
                    enterDoor();
                    return;
                }
            } else {
                // Failed to unlock
                playSound(lockedSound);
                showMessage(lockedMessage);
                return;
            }
        }

        // If door has destination, enter it
        if (hasDestination()) {
            enterDoor();
            return;
        }

        // Otherwise, toggle open/closed
        toggleDoor();
    }

    @Override
    public String getInteractionPrompt() {
        if (locked) {
            return "Unlock";
        }
        if (hasDestination()) {
            return "Enter";
        }
        return open ? "Close" : "Open";
    }

    @Override
    public Vector3f getPosition() {
        return gameObject.getTransform().getPosition();
    }

    @Override
    public float getInteractionRadius() {
        return interactionRadius;
    }

    @Override
    public boolean requiresFacing() {
        return requiredFacing != null;
    }

    // ========================================================================
    // DOOR ACTIONS
    // ========================================================================

    /**
     * Attempts to unlock the door with actor's inventory.
     */
    private boolean tryUnlock(GameObject actor) {
        // No key required = auto-unlock
        if (requiredKey == null || requiredKey.isBlank()) {
            return true;
        }

        // Check inventory
        Inventory inventory = actor.getComponent(Inventory.class);
        if (inventory == null) {
            return false;
        }

        if (!inventory.hasItem(requiredKey)) {
            return false;
        }

        // Consume key if configured
        if (consumeKey) {
            inventory.removeItem(requiredKey, 1);
        }

        return true;
    }

    /**
     * Toggles door open/closed.
     */
    public void toggleDoor() {
        if (open) {
            closeDoor();
        } else {
            openDoor();
        }
    }

    /**
     * Opens the door.
     */
    public void openDoor() {
        if (open) return;

        open = true;
        playSound(openSound);
        updateVisuals();
        updateCollision();
    }

    /**
     * Closes the door.
     */
    public void closeDoor() {
        if (!open) return;

        open = false;
        playSound(closeSound);
        updateVisuals();
        updateCollision();
    }

    /**
     * Enters the door (teleport to destination).
     */
    private void enterDoor() {
        playSound(openSound);

        // Same scene teleport
        if (targetScene == null || targetScene.isBlank()) {
            getScene().teleportToSpawn(
                    getScene().findGameObjectWithTag("Player"),
                    targetSpawnId
            );
        } else {
            // Cross-scene teleport
            SceneTransition.loadScene(targetScene, targetSpawnId);
        }
    }

    /**
     * Whether door has a teleport destination.
     */
    public boolean hasDestination() {
        return targetSpawnId != null && !targetSpawnId.isBlank();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void updateVisuals() {
        if (spriteRenderer == null) return;

        Sprite sprite = open ? openSprite : closedSprite;
        if (sprite != null) {
            spriteRenderer.setSprite(sprite);
        }
    }

    private void updateCollision() {
        if (blockedTiles.isEmpty()) return;

        TileEntityMap tileEntityMap = getScene().getCollisionSystem().getTileEntityMap();
        if (tileEntityMap == null) return;

        // Unregister from all currently registered tiles
        for (TileCoord tile : registeredTiles) {
            tileEntityMap.unregister(this, tile);
        }
        registeredTiles.clear();

        // If closed, register as blocking on blocked tiles
        if (!open) {
            for (TileCoord tile : getAbsoluteBlockedTiles()) {
                tileEntityMap.register(this, tile);
                registeredTiles.add(tile);
            }
        }
    }

    private List<TileCoord> getAbsoluteBlockedTiles() {
        if (!tilesRelative) return blockedTiles;

        Vector3f doorPos = getPosition();
        int doorX = (int) Math.floor(doorPos.x);
        int doorY = (int) Math.floor(doorPos.y);

        List<TileCoord> absolute = new ArrayList<>();
        for (TileCoord tile : blockedTiles) {
            int x = doorX + tile.x();
            int y = doorY + tile.y();
            int z = tile.elevation() != 0 ? tile.elevation() : elevation;
            absolute.add(new TileCoord(x, y, z));
        }
        return absolute;
    }

    private void playSound(AudioClip clip) {
        if (audioSource != null && clip != null) {
            audioSource.playOneShot(clip);
        }
    }

    private void showMessage(String message) {
        // TODO: Integrate with dialogue/message system
        System.out.println("[Door] " + message);
    }

    private float distanceTo(GameObject other) {
        Vector3f myPos = getPosition();
        Vector3f otherPos = other.getTransform().getPosition();
        float dx = myPos.x - otherPos.x;
        float dy = myPos.y - otherPos.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
```

---

## Door Configurations

### Simple Open/Close Door

```
Door {
    locked: false
    closedSprite: "sprites/door_closed.png"
    openSprite: "sprites/door_open.png"
    openSound: "sounds/door_open.wav"
    closeSound: "sounds/door_close.wav"
    blockedTiles: [(0, 0, 0)]  // Door's own tile
}
```

### Locked Door with Key

```
Door {
    locked: true
    requiredKey: "dungeon_key"
    consumeKey: false
    lockedMessage: "This door requires the Dungeon Key."
    closedSprite: "sprites/door_locked.png"
    openSprite: "sprites/door_open.png"
    unlockSound: "sounds/unlock.wav"
    lockedSound: "sounds/door_rattle.wav"
}
```

### Teleport Door (Scene Transition)

```
Door {
    locked: false
    targetScene: "dungeon_floor_2"
    targetSpawnId: "stairs_up"
    closedSprite: "sprites/door_dungeon.png"
    openSound: "sounds/heavy_door.wav"
}
```

### Same-Scene Teleport (Building Entrance)

```
Door {
    locked: false
    targetScene: ""  // Empty = same scene
    targetSpawnId: "house_interior"
    closedSprite: "sprites/house_door.png"
    openSound: "sounds/door_open.wav"
}
```

### Double Door (Two Tiles Wide)

```
Door {
    locked: true
    requiredKey: "castle_key"
    closedSprite: "sprites/double_door.png"  // Wide sprite
    openSprite: "sprites/double_door_open.png"
    blockedTiles: [
        (0, 0, 0),   // Left tile
        (1, 0, 0)    // Right tile
    ]
}
```

---

## Door Visual States

```
   CLOSED (locked)     CLOSED (unlocked)     OPEN
   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
   │   🔒🚪       │    │     🚪       │    │     ▯▯       │
   │              │    │              │    │              │
   │  [SOLID]     │    │  [SOLID]     │    │  [NONE]      │
   └──────────────┘    └──────────────┘    └──────────────┘

   Prompt: "Unlock"    Prompt: "Open"      Prompt: "Close"
```

---

## Integration with Collision Map

When a door is closed, it sets specific tiles to SOLID. When opened, those tiles become NONE.

```
Scene with closed door:
┌─────┬─────┬─────┬─────┬─────┐
│SOLID│SOLID│SOLID│SOLID│SOLID│  Wall
├─────┼─────┼─────┼─────┼─────┤
│SOLID│NONE │SOLID│NONE │SOLID│  Door (SOLID when closed)
├─────┼─────┼─────┼─────┼─────┤
│NONE │NONE │NONE │NONE │NONE │  Floor
└─────┴─────┴─────┴─────┴─────┘

After opening door:
┌─────┬─────┬─────┬─────┬─────┐
│SOLID│SOLID│SOLID│SOLID│SOLID│  Wall
├─────┼─────┼─────┼─────┼─────┤
│SOLID│NONE │ NONE│NONE │SOLID│  Door (NONE when open)
├─────┼─────┼─────┼─────┼─────┤
│NONE │NONE │NONE │NONE │NONE │  Floor
└─────┴─────┴─────┴─────┴─────┘
```

---

## Animation Support (Future)

For animated doors, replace sprite switching with Animator:

```java
@ComponentRef
private Animator animator;

private void updateVisuals() {
    if (animator != null) {
        animator.play(open ? "door_open" : "door_close");
    } else if (spriteRenderer != null) {
        spriteRenderer.setSprite(open ? openSprite : closedSprite);
    }
}
```

---

## Files to Create

| File | Purpose |
|------|---------|
| `components/interaction/Door.java` | Door component |

---

## Testing Checklist

- [ ] Door shows closed sprite initially
- [ ] E key opens unlocked door
- [ ] Open door shows open sprite
- [ ] Open door plays open sound
- [ ] E key closes open door
- [ ] Closed door plays close sound
- [ ] Locked door shows "Unlock" prompt
- [ ] Locked door without key shows locked message
- [ ] Locked door with key unlocks
- [ ] consumeKey removes key from inventory
- [ ] Door with destination teleports player
- [ ] Cross-scene door loads target scene
- [ ] blockedTiles registered with TileEntityMap when closed
- [ ] blockedTiles unregistered from TileEntityMap when open
- [ ] registeredTiles cleaned up on destroy

---

## Next Phase

Phase 3: Chest and Other Interactables
