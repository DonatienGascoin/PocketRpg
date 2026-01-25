# Phase 3: Chest and Other Interactables

## Overview

This phase implements additional Interactable components:
- **Chest**: Contains items, gives to player on open
- **Lever**: Toggles state, triggers connected objects
- **Sign**: Displays text when read
- **SecretPassage**: Toggleable blocking via StaticOccupant

### All Blocking is Entity-Based

All objects use `StaticOccupant` to register with `TileEntityMap` for blocking. There is no collision map modification for interactive objects.

| Component | Blocking | Notes |
|-----------|----------|-------|
| Chest | StaticOccupant (always) | Stays blocking |
| Sign | StaticOccupant (always) | Stays blocking |
| Door | StaticOccupant (when closed) | Registers when closed, unregisters when open |
| SecretPassage | StaticOccupant (when hidden) | Registers when hidden, unregisters when revealed |
| Breakable Wall | StaticOccupant (until destroyed) | Unregisters when destroyed |

```java
// Chest blocks movement
GameObject chest = new GameObject("Chest");
chest.addComponent(new StaticOccupant());  // Registers with TileEntityMap
chest.addComponent(new Chest());

// SecretPassage toggles blocking
GameObject secret = new GameObject("SecretWall");
secret.addComponent(new SecretPassage());  // Manages its own StaticOccupant registration
```

---

## Chest Component

```java
package com.pocket.rpg.components.interaction;

import com.pocket.rpg.audio.AudioClip;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.audio.AudioSource;
import com.pocket.rpg.components.inventory.Inventory;
import com.pocket.rpg.components.inventory.ItemStack;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.serialization.ComponentRef;
import com.pocket.rpg.serialization.Transient;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive chest that contains items.
 *
 * Features:
 * - Gives items to player on first open
 * - Visual state (closed/open sprite)
 * - Optional lock with key requirement
 * - One-time use (stays open after opened)
 */
public class Chest extends Component implements Interactable {

    // ========================================================================
    // CONFIGURATION - Contents
    // ========================================================================

    /**
     * Items contained in the chest.
     * Given to player's inventory on open.
     */
    @Getter @Setter
    private List<ItemStack> contents = new ArrayList<>();

    // ========================================================================
    // CONFIGURATION - Lock
    // ========================================================================

    @Getter @Setter
    private boolean locked = false;

    @Getter @Setter
    private String requiredKey = "";

    @Getter @Setter
    private boolean consumeKey = false;

    @Getter @Setter
    private String lockedMessage = "This chest is locked.";

    // ========================================================================
    // CONFIGURATION - Visuals & Audio
    // ========================================================================

    @Getter @Setter
    private Sprite closedSprite;

    @Getter @Setter
    private Sprite openSprite;

    @Getter @Setter
    private AudioClip openSound;

    @Getter @Setter
    private AudioClip lockedSound;

    @Getter @Setter
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
    private boolean opened = false;

    // ========================================================================
    // INTERACTABLE IMPLEMENTATION
    // ========================================================================

    @Override
    public boolean canInteract(GameObject actor) {
        // Can't interact if already opened
        if (opened) {
            return false;
        }

        // Check distance
        float dist = distanceTo(actor);
        return dist <= interactionRadius;
    }

    @Override
    public void interact(GameObject actor) {
        if (opened) return;

        // Check lock
        if (locked && !tryUnlock(actor)) {
            playSound(lockedSound);
            showMessage(lockedMessage);
            return;
        }

        // Open chest
        opened = true;
        playSound(openSound);
        updateVisuals();

        // Give contents to player
        giveContents(actor);
    }

    @Override
    public String getInteractionPrompt() {
        if (locked) return "Unlock";
        return "Open";
    }

    @Override
    public Vector3f getPosition() {
        return gameObject.getTransform().getPosition();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private boolean tryUnlock(GameObject actor) {
        if (requiredKey == null || requiredKey.isBlank()) {
            return true;
        }

        Inventory inventory = actor.getComponent(Inventory.class);
        if (inventory == null || !inventory.hasItem(requiredKey)) {
            return false;
        }

        if (consumeKey) {
            inventory.removeItem(requiredKey, 1);
        }

        locked = false;
        return true;
    }

    private void giveContents(GameObject actor) {
        Inventory inventory = actor.getComponent(Inventory.class);
        if (inventory == null) {
            System.err.println("[Chest] Actor has no inventory!");
            return;
        }

        for (ItemStack item : contents) {
            inventory.addItem(item.getItemId(), item.getQuantity());
            showMessage("Received " + item.getQuantity() + "x " + item.getItemId());
        }
    }

    private void updateVisuals() {
        if (spriteRenderer != null && openSprite != null) {
            spriteRenderer.setSprite(openSprite);
        }
    }

    private void playSound(AudioClip clip) {
        if (audioSource != null && clip != null) {
            audioSource.playOneShot(clip);
        }
    }

    private void showMessage(String message) {
        // TODO: Integrate with UI system
        System.out.println("[Chest] " + message);
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

## Lever Component

```java
package com.pocket.rpg.components.interaction;

import com.pocket.rpg.audio.AudioClip;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.audio.AudioSource;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.serialization.ComponentRef;
import com.pocket.rpg.serialization.Transient;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive lever/switch that toggles state and triggers targets.
 *
 * Features:
 * - Toggle between on/off states
 * - Visual state (off/on sprite)
 * - Triggers connected Triggerable objects
 * - Optional one-shot mode (can't toggle back)
 */
public class Lever extends Component implements Interactable {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Sprites for off/on states.
     */
    @Getter @Setter
    private Sprite offSprite;

    @Getter @Setter
    private Sprite onSprite;

    /**
     * Sounds for activation.
     */
    @Getter @Setter
    private AudioClip toggleSound;

    /**
     * If true, lever can only be activated once.
     */
    @Getter @Setter
    private boolean oneShot = false;

    /**
     * Names of GameObjects to trigger when state changes.
     * These objects should have components implementing Triggerable.
     */
    @Getter @Setter
    private List<String> targetNames = new ArrayList<>();

    @Getter @Setter
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
    private boolean activated = false;

    // ========================================================================
    // INTERACTABLE IMPLEMENTATION
    // ========================================================================

    @Override
    public boolean canInteract(GameObject actor) {
        // Can't interact if one-shot and already activated
        if (oneShot && activated) {
            return false;
        }

        float dist = distanceTo(actor);
        return dist <= interactionRadius;
    }

    @Override
    public void interact(GameObject actor) {
        if (oneShot && activated) return;

        // Toggle state
        activated = !activated;

        // Update visuals and sound
        updateVisuals();
        playSound(toggleSound);

        // Trigger targets
        triggerTargets();
    }

    @Override
    public String getInteractionPrompt() {
        return activated ? "Deactivate" : "Activate";
    }

    @Override
    public Vector3f getPosition() {
        return gameObject.getTransform().getPosition();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private void updateVisuals() {
        if (spriteRenderer != null) {
            Sprite sprite = activated ? onSprite : offSprite;
            if (sprite != null) {
                spriteRenderer.setSprite(sprite);
            }
        }
    }

    private void playSound(AudioClip clip) {
        if (audioSource != null && clip != null) {
            audioSource.playOneShot(clip);
        }
    }

    private void triggerTargets() {
        for (String targetName : targetNames) {
            GameObject target = getScene().findGameObject(targetName);
            if (target == null) {
                System.err.println("[Lever] Target not found: " + targetName);
                continue;
            }

            // Find Triggerable components
            for (Component comp : target.getAllComponents()) {
                if (comp instanceof Triggerable triggerable) {
                    triggerable.onTrigger(this, activated);
                }
            }
        }
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

## Triggerable Interface

```java
package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.Component;

/**
 * Interface for objects that can be triggered by levers, buttons, etc.
 */
public interface Triggerable {

    /**
     * Called when this object is triggered.
     *
     * @param source The component that triggered this (e.g., Lever)
     * @param state  The new state (true = activated, false = deactivated)
     */
    void onTrigger(Component source, boolean state);
}
```

---

## Sign Component

```java
package com.pocket.rpg.components.interaction;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.core.GameObject;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Interactive sign that displays text when read.
 */
public class Sign extends Component implements Interactable {

    /**
     * Text displayed when reading the sign.
     * Can be multi-line.
     */
    @Getter @Setter
    private String text = "";

    /**
     * Title shown above text (optional).
     */
    @Getter @Setter
    private String title = "";

    @Getter @Setter
    private float interactionRadius = 1.5f;

    @Override
    public boolean canInteract(GameObject actor) {
        float dist = distanceTo(actor);
        return dist <= interactionRadius;
    }

    @Override
    public void interact(GameObject actor) {
        showText();
    }

    @Override
    public String getInteractionPrompt() {
        return "Read";
    }

    @Override
    public Vector3f getPosition() {
        return gameObject.getTransform().getPosition();
    }

    private void showText() {
        // TODO: Integrate with dialogue/text box UI
        if (title != null && !title.isBlank()) {
            System.out.println("[Sign: " + title + "]");
        }
        System.out.println(text);
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

## SecretPassage Component

```java
package com.pocket.rpg.components.interaction;

import com.pocket.rpg.audio.AudioClip;
import com.pocket.rpg.collision.TileEntityMap;
import com.pocket.rpg.collision.trigger.TileCoord;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.SpriteRenderer;
import com.pocket.rpg.components.audio.AudioSource;
import com.pocket.rpg.components.inventory.Inventory;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.serialization.ComponentRef;
import com.pocket.rpg.serialization.Transient;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Toggleable blocking via StaticOccupant pattern.
 *
 * Use cases:
 * - Secret passages (hidden until revealed)
 * - Breakable walls (blocks until destroyed)
 * - Drawbridges (blocks until lowered)
 * - Retractable barriers
 *
 * Key difference from Door:
 * - Door: opens to allow passage (visual door opens)
 * - SecretPassage: reveals passage (wall disappears)
 *
 * Can be triggered by:
 * - Direct interaction (Interactable)
 * - Lever/button (Triggerable)
 */
public class SecretPassage extends Component implements Interactable, Triggerable {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Tiles to block when hidden. Relative to entity position.
     */
    @Getter @Setter
    private List<TileCoord> blockedTiles = new ArrayList<>(List.of(new TileCoord(0, 0, 0)));

    /**
     * If true, can be toggled back to hidden state.
     */
    @Getter @Setter
    private boolean toggleable = false;

    /**
     * If true, can be revealed by direct interaction.
     * If false, can only be triggered by other objects (levers).
     */
    @Getter @Setter
    private boolean directlyInteractable = true;

    // ========================================================================
    // CONFIGURATION - Key Requirement
    // ========================================================================

    @Getter @Setter
    private boolean requiresKey = false;

    @Getter @Setter
    private String requiredKey = "";

    @Getter @Setter
    private boolean consumeKey = false;

    @Getter @Setter
    private String lockedMessage = "You need a key.";

    // ========================================================================
    // CONFIGURATION - Audio/Visuals
    // ========================================================================

    @Getter @Setter
    private AudioClip revealSound;

    @Getter @Setter
    private AudioClip hideSound;

    @Getter @Setter
    private AudioClip lockedSound;

    @Getter @Setter
    private float interactionRadius = 1.5f;

    // ========================================================================
    // COMPONENT REFERENCES
    // ========================================================================

    @ComponentRef
    private AudioSource audioSource;

    @ComponentRef
    private SpriteRenderer spriteRenderer;

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    @Transient
    @Getter
    private boolean revealed = false;

    @Transient
    private List<TileCoord> registeredTiles = new ArrayList<>();

    @Transient
    private TileEntityMap tileEntityMap;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void start() {
        tileEntityMap = getScene().getCollisionSystem().getTileEntityMap();
        updateBlocking();
    }

    @Override
    public void onDestroy() {
        // Unregister from all tiles
        for (TileCoord tile : registeredTiles) {
            tileEntityMap.unregister(this, tile);
        }
        registeredTiles.clear();
    }

    // ========================================================================
    // INTERACTABLE IMPLEMENTATION
    // ========================================================================

    @Override
    public boolean canInteract(GameObject actor) {
        if (!directlyInteractable) return false;
        if (!toggleable && revealed) return false;

        float dist = distanceTo(actor);
        return dist <= interactionRadius;
    }

    @Override
    public void interact(GameObject actor) {
        if (!directlyInteractable) return;
        if (!toggleable && revealed) return;

        // Check key
        if (requiresKey && !revealed && !tryUnlock(actor)) {
            playSound(lockedSound);
            showMessage(lockedMessage);
            return;
        }

        // Toggle or reveal
        if (toggleable) {
            setRevealed(!revealed);
        } else {
            setRevealed(true);
        }
    }

    @Override
    public String getInteractionPrompt() {
        if (requiresKey && !revealed) return "Unlock";
        return revealed ? "Hide" : "Reveal";
    }

    @Override
    public Vector3f getPosition() {
        return gameObject.getTransform().getPosition();
    }

    // ========================================================================
    // TRIGGERABLE IMPLEMENTATION
    // ========================================================================

    @Override
    public void onTrigger(Component source, boolean state) {
        if (toggleable) {
            setRevealed(state);
        } else if (state && !revealed) {
            setRevealed(true);
        }
    }

    // ========================================================================
    // ACTIONS
    // ========================================================================

    public void setRevealed(boolean newState) {
        if (revealed == newState) return;

        revealed = newState;
        updateBlocking();
        updateVisuals();
        playSound(revealed ? revealSound : hideSound);
    }

    private void updateBlocking() {
        // Unregister from all previously registered tiles
        for (TileCoord tile : registeredTiles) {
            tileEntityMap.unregister(this, tile);
        }
        registeredTiles.clear();

        // If hidden, register as blocking
        if (!revealed) {
            for (TileCoord tile : getAbsoluteTiles()) {
                tileEntityMap.register(this, tile);
                registeredTiles.add(tile);
            }
        }
    }

    private void updateVisuals() {
        // Hide sprite when passage is revealed
        if (spriteRenderer != null) {
            spriteRenderer.setEnabled(!revealed);
        }
    }

    private List<TileCoord> getAbsoluteTiles() {
        Vector3f pos = getPosition();
        int baseX = (int) Math.floor(pos.x);
        int baseY = (int) Math.floor(pos.y);

        List<TileCoord> absolute = new ArrayList<>();
        for (TileCoord tile : blockedTiles) {
            absolute.add(new TileCoord(
                baseX + tile.x(),
                baseY + tile.y(),
                tile.elevation()
            ));
        }
        return absolute;
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private boolean tryUnlock(GameObject actor) {
        if (requiredKey == null || requiredKey.isBlank()) {
            return true;
        }

        Inventory inventory = actor.getComponent(Inventory.class);
        if (inventory == null || !inventory.hasItem(requiredKey)) {
            return false;
        }

        if (consumeKey) {
            inventory.removeItem(requiredKey, 1);
        }

        return true;
    }

    private void playSound(AudioClip clip) {
        if (audioSource != null && clip != null) {
            audioSource.playOneShot(clip);
        }
    }

    private void showMessage(String message) {
        System.out.println("[SecretPassage] " + message);
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

## Example Configurations

### Secret Wall (Touch to Reveal)

```
SecretPassage {
    blockedTiles: [(0, 0, 0), (0, 1, 0)]
    toggleable: false
    directlyInteractable: true
    requiresKey: false
    revealSound: "sounds/stone_slide.wav"
}
+ SpriteRenderer (wall sprite)
+ AudioSource
```

### Lever-Controlled Barrier

```
// Lever entity
Lever {
    targetNames: ["SecretDoor"]
    oneShot: false
}

// Barrier entity (named "SecretDoor")
SecretPassage {
    blockedTiles: [(0, 0, 0)]
    toggleable: true
    directlyInteractable: false  // Only lever can control
}
```

### Breakable Wall (Bomb Required)

```
SecretPassage {
    blockedTiles: [(0, 0, 0)]
    requiresKey: true
    requiredKey: "bomb"
    consumeKey: true
    revealSound: "sounds/explosion.wav"
}
+ SpriteRenderer (cracked wall sprite)
+ AudioSource
```

---

## Files to Create

| File | Purpose |
|------|---------|
| `components/interaction/Chest.java` | Chest component |
| `components/interaction/Lever.java` | Lever/switch component |
| `components/interaction/Sign.java` | Readable sign component |
| `components/interaction/Triggerable.java` | Trigger interface |
| `components/interaction/SecretPassage.java` | Toggleable blocking |

---

## Testing Checklist

### Chest
- [ ] Chest shows closed sprite initially
- [ ] E key opens chest
- [ ] Items given to player inventory
- [ ] Opened chest shows open sprite
- [ ] Opened chest can't be interacted again
- [ ] Locked chest requires key
- [ ] Key consumed if configured

### Lever
- [ ] E key toggles lever state
- [ ] Lever sprite changes on toggle
- [ ] Connected targets receive onTrigger call
- [ ] One-shot lever can only activate once

### Sign
- [ ] E key shows sign text
- [ ] Title displayed if configured

### SecretPassage
- [ ] Registers with TileEntityMap when hidden
- [ ] Unregisters from TileEntityMap when revealed
- [ ] Player can walk through revealed passage
- [ ] Toggleable passage can be hidden again
- [ ] Non-interactable passage only responds to triggers
- [ ] Lever can trigger SecretPassage
- [ ] Sprite hidden when revealed

---

## Next Phase

Phase 4: Inventory System
