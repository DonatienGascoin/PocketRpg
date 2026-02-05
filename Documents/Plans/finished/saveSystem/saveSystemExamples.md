# Save System - Example Implementations

This document provides complete example implementations of ISaveable components.

---

## Example 1: Health Component

A basic health component that saves current and max health.

```java
package com.pocket.rpg.components;

import com.pocket.rpg.save.ISaveable;
import com.pocket.rpg.serialization.HideInInspector;

import java.util.HashMap;
import java.util.Map;

/**
 * Health component with save support.
 *
 * Saved state: currentHealth, maxHealth (if modified from default)
 * Not saved: isDead (derived from currentHealth <= 0)
 */
public class Health extends Component implements ISaveable {

    // ========================================================================
    // SERIALIZED FIELDS (saved in scene file)
    // ========================================================================

    /**
     * Maximum health. Set in editor/scene file.
     */
    private float maxHealth = 100f;

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    /**
     * Current health. Starts at maxHealth, changes at runtime.
     */
    @HideInInspector
    private float currentHealth;

    /**
     * Cached death state.
     */
    @HideInInspector
    private transient boolean isDead = false;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    public Health() {
        // Required no-arg constructor
    }

    @Override
    protected void onStart() {
        // Initialize current health to max if not loaded from save
        if (currentHealth <= 0 && !isDead) {
            currentHealth = maxHealth;
        }
    }

    // ========================================================================
    // GAME LOGIC
    // ========================================================================

    public void takeDamage(float amount) {
        if (isDead) return;

        currentHealth -= amount;
        if (currentHealth <= 0) {
            currentHealth = 0;
            isDead = true;
            onDeath();
        }
    }

    public void heal(float amount) {
        if (isDead) return;

        currentHealth = Math.min(currentHealth + amount, maxHealth);
    }

    public void setMaxHealth(float newMax) {
        float ratio = currentHealth / maxHealth;
        maxHealth = newMax;
        currentHealth = maxHealth * ratio;
    }

    private void onDeath() {
        // Trigger death logic, animations, etc.
    }

    // Getters
    public float getCurrentHealth() { return currentHealth; }
    public float getMaxHealth() { return maxHealth; }
    public boolean isDead() { return isDead; }
    public float getHealthPercent() { return currentHealth / maxHealth; }

    // ========================================================================
    // ISaveable IMPLEMENTATION
    // ========================================================================

    @Override
    public Map<String, Object> getSaveState() {
        Map<String, Object> state = new HashMap<>();

        // Always save current health
        state.put("currentHealth", currentHealth);

        // Save maxHealth only if it differs from scene default
        // (In practice, you might always save it for simplicity)
        state.put("maxHealth", maxHealth);

        return state;
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        if (state == null) return;

        if (state.containsKey("currentHealth")) {
            currentHealth = ((Number) state.get("currentHealth")).floatValue();
            isDead = currentHealth <= 0;
        }

        if (state.containsKey("maxHealth")) {
            maxHealth = ((Number) state.get("maxHealth")).floatValue();
        }
    }

    @Override
    public boolean hasSaveableState() {
        // Always save health if entity has PersistentId
        return true;
    }
}
```

**Save output:**
```json
{
  "currentHealth": 75.0,
  "maxHealth": 100.0
}
```

---

## Example 2: Inventory Component

A more complex example with collections.

```java
package com.pocket.rpg.components;

import com.pocket.rpg.save.ISaveable;
import com.pocket.rpg.serialization.HideInInspector;

import java.util.*;

/**
 * Inventory component with save support.
 *
 * Saved state: gold, items list, equipped items
 * Not saved: maxSlots (configuration), dirty flag
 */
public class Inventory extends Component implements ISaveable {

    // ========================================================================
    // CONFIGURATION (from scene file)
    // ========================================================================

    /**
     * Maximum inventory slots. Set in editor.
     */
    private int maxSlots = 20;

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    @HideInInspector
    private int gold = 0;

    @HideInInspector
    private List<String> items = new ArrayList<>();

    @HideInInspector
    private String equippedWeapon = null;

    @HideInInspector
    private String equippedArmor = null;

    // Transient - not saved
    @HideInInspector
    private transient boolean dirty = false;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    public Inventory() {
    }

    // ========================================================================
    // GAME LOGIC
    // ========================================================================

    public void addGold(int amount) {
        gold += amount;
        dirty = true;
    }

    public boolean spendGold(int amount) {
        if (gold < amount) return false;
        gold -= amount;
        dirty = true;
        return true;
    }

    public boolean addItem(String itemId) {
        if (items.size() >= maxSlots) return false;
        items.add(itemId);
        dirty = true;
        return true;
    }

    public boolean removeItem(String itemId) {
        boolean removed = items.remove(itemId);
        if (removed) {
            dirty = true;
            // Unequip if removed item was equipped
            if (itemId.equals(equippedWeapon)) equippedWeapon = null;
            if (itemId.equals(equippedArmor)) equippedArmor = null;
        }
        return removed;
    }

    public boolean hasItem(String itemId) {
        return items.contains(itemId);
    }

    public void equipWeapon(String itemId) {
        if (items.contains(itemId)) {
            equippedWeapon = itemId;
            dirty = true;
        }
    }

    public void equipArmor(String itemId) {
        if (items.contains(itemId)) {
            equippedArmor = itemId;
            dirty = true;
        }
    }

    // Getters
    public int getGold() { return gold; }
    public List<String> getItems() { return Collections.unmodifiableList(items); }
    public int getItemCount() { return items.size(); }
    public int getMaxSlots() { return maxSlots; }
    public String getEquippedWeapon() { return equippedWeapon; }
    public String getEquippedArmor() { return equippedArmor; }

    // ========================================================================
    // ISaveable IMPLEMENTATION
    // ========================================================================

    @Override
    public Map<String, Object> getSaveState() {
        Map<String, Object> state = new HashMap<>();

        state.put("gold", gold);
        state.put("items", new ArrayList<>(items));  // Copy to avoid mutation

        if (equippedWeapon != null) {
            state.put("equippedWeapon", equippedWeapon);
        }
        if (equippedArmor != null) {
            state.put("equippedArmor", equippedArmor);
        }

        return state;
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        if (state == null) return;

        if (state.containsKey("gold")) {
            gold = ((Number) state.get("gold")).intValue();
        }

        if (state.containsKey("items")) {
            items.clear();
            @SuppressWarnings("unchecked")
            List<String> savedItems = (List<String>) state.get("items");
            if (savedItems != null) {
                items.addAll(savedItems);
            }
        }

        equippedWeapon = (String) state.get("equippedWeapon");  // null if not present
        equippedArmor = (String) state.get("equippedArmor");

        dirty = false;
    }

    @Override
    public boolean hasSaveableState() {
        // Save if player has any items or gold
        return gold > 0 || !items.isEmpty();
    }
}
```

**Save output:**
```json
{
  "gold": 1500,
  "items": ["sword_steel", "potion_health", "key_dungeon", "shield_wood"],
  "equippedWeapon": "sword_steel",
  "equippedArmor": null
}
```

---

## Example 3: Chest Component

Simple binary state - opened or not.

```java
package com.pocket.rpg.components;

import com.pocket.rpg.components.rendering.SpriteRenderer;
import com.pocket.rpg.save.ISaveable;
import com.pocket.rpg.serialization.HideInInspector;

import java.util.HashMap;
import java.util.Map;

/**
 * Chest component - tracks if chest has been opened/looted.
 *
 * Saved state: opened, looted
 * Not saved: lootTable (configuration)
 */
public class Chest extends Component implements ISaveable {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Items to give when opened. Set in editor.
     */
    private String[] lootTable = new String[0];

    /**
     * Gold to give when opened.
     */
    private int goldAmount = 0;

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    @HideInInspector
    private boolean opened = false;

    @HideInInspector
    private boolean looted = false;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    public Chest() {
    }

    @Override
    protected void onStart() {
        // Update visual based on state
        updateVisual();
    }

    // ========================================================================
    // GAME LOGIC
    // ========================================================================

    /**
     * Called when player interacts with chest.
     */
    public void interact(Inventory playerInventory) {
        if (looted) {
            // Already looted, do nothing
            return;
        }

        if (!opened) {
            opened = true;
            updateVisual();
            // Play open animation/sound
        }

        // Give loot
        if (goldAmount > 0) {
            playerInventory.addGold(goldAmount);
        }
        for (String item : lootTable) {
            playerInventory.addItem(item);
        }

        looted = true;
    }

    private void updateVisual() {
        // Change sprite to open/closed state
        SpriteRenderer sr = getComponent(SpriteRenderer.class);
        if (sr != null) {
            // sr.setSprite(opened ? openSprite : closedSprite);
        }
    }

    // Getters
    public boolean isOpened() {
        return opened;
    }

    public boolean isLooted() {
        return looted;
    }

    // ========================================================================
    // ISaveable IMPLEMENTATION
    // ========================================================================

    @Override
    public Map<String, Object> getSaveState() {
        Map<String, Object> state = new HashMap<>();
        state.put("opened", opened);
        state.put("looted", looted);
        return state;
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        if (state == null) return;

        if (state.containsKey("opened")) {
            opened = (Boolean) state.get("opened");
        }
        if (state.containsKey("looted")) {
            looted = (Boolean) state.get("looted");
        }

        // Update visual after loading
        updateVisual();
    }

    @Override
    public boolean hasSaveableState() {
        // Only save if chest state changed
        return opened || looted;
    }
}
```

**Save output:**
```json
{
  "opened": true,
  "looted": true
}
```

---

## Example 4: Quest Tracker Component

Complex state with enums and maps.

```java
package com.pocket.rpg.components;

import com.pocket.rpg.save.ISaveable;
import com.pocket.rpg.serialization.HideInInspector;

import java.util.*;

/**
 * Tracks quest progress across the game.
 *
 * Saved state: quest states, objective progress
 */
public class QuestTracker extends Component implements ISaveable {

    // ========================================================================
    // ENUMS
    // ========================================================================

    public enum QuestState {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    @HideInInspector
    private Map<String, QuestState> quests = new HashMap<>();

    @HideInInspector
    private Map<String, Integer> objectiveProgress = new HashMap<>();

    @HideInInspector
    private Set<String> claimedRewards = new HashSet<>();

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    public QuestTracker() {
    }

    // ========================================================================
    // GAME LOGIC
    // ========================================================================

    public void startQuest(String questId) {
        if (!quests.containsKey(questId)) {
            quests.put(questId, QuestState.IN_PROGRESS);
            objectiveProgress.put(questId, 0);
        }
    }

    public void incrementProgress(String questId, int amount) {
        if (quests.get(questId) == QuestState.IN_PROGRESS) {
            objectiveProgress.merge(questId, amount, Integer::sum);
        }
    }

    public void completeQuest(String questId) {
        quests.put(questId, QuestState.COMPLETED);
    }

    public void failQuest(String questId) {
        quests.put(questId, QuestState.FAILED);
    }

    public void claimReward(String questId) {
        claimedRewards.add(questId);
    }

    // Getters
    public QuestState getQuestState(String questId) {
        return quests.getOrDefault(questId, QuestState.NOT_STARTED);
    }

    public int getProgress(String questId) {
        return objectiveProgress.getOrDefault(questId, 0);
    }

    public boolean isQuestActive(String questId) {
        return quests.get(questId) == QuestState.IN_PROGRESS;
    }

    public boolean isQuestCompleted(String questId) {
        return quests.get(questId) == QuestState.COMPLETED;
    }

    public boolean hasClaimedReward(String questId) {
        return claimedRewards.contains(questId);
    }

    public List<String> getActiveQuests() {
        List<String> active = new ArrayList<>();
        for (Map.Entry<String, QuestState> entry : quests.entrySet()) {
            if (entry.getValue() == QuestState.IN_PROGRESS) {
                active.add(entry.getKey());
            }
        }
        return active;
    }

    // ========================================================================
    // ISaveable IMPLEMENTATION
    // ========================================================================

    @Override
    public Map<String, Object> getSaveState() {
        Map<String, Object> state = new HashMap<>();

        // Convert enum map to string map for JSON
        Map<String, String> questStates = new HashMap<>();
        for (Map.Entry<String, QuestState> entry : quests.entrySet()) {
            questStates.put(entry.getKey(), entry.getValue().name());
        }
        state.put("quests", questStates);

        // Progress is already Map<String, Integer>
        state.put("progress", new HashMap<>(objectiveProgress));

        // Claimed rewards as list
        state.put("claimedRewards", new ArrayList<>(claimedRewards));

        return state;
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        if (state == null) return;

        // Load quest states
        quests.clear();
        @SuppressWarnings("unchecked")
        Map<String, String> savedQuests = (Map<String, String>) state.get("quests");
        if (savedQuests != null) {
            for (Map.Entry<String, String> entry : savedQuests.entrySet()) {
                try {
                    quests.put(entry.getKey(), QuestState.valueOf(entry.getValue()));
                } catch (IllegalArgumentException e) {
                    // Unknown state, default to NOT_STARTED
                    quests.put(entry.getKey(), QuestState.NOT_STARTED);
                }
            }
        }

        // Load progress
        objectiveProgress.clear();
        @SuppressWarnings("unchecked")
        Map<String, Number> savedProgress = (Map<String, Number>) state.get("progress");
        if (savedProgress != null) {
            for (Map.Entry<String, Number> entry : savedProgress.entrySet()) {
                objectiveProgress.put(entry.getKey(), entry.getValue().intValue());
            }
        }

        // Load claimed rewards
        claimedRewards.clear();
        @SuppressWarnings("unchecked")
        List<String> savedRewards = (List<String>) state.get("claimedRewards");
        if (savedRewards != null) {
            claimedRewards.addAll(savedRewards);
        }
    }

    @Override
    public boolean hasSaveableState() {
        return !quests.isEmpty();
    }
}
```

**Save output:**
```json
{
  "quests": {
    "main_quest": "COMPLETED",
    "side_quest_blacksmith": "COMPLETED",
    "side_quest_herbs": "IN_PROGRESS",
    "side_quest_lost_cat": "NOT_STARTED"
  },
  "progress": {
    "side_quest_herbs": 3,
    "main_quest": 5
  },
  "claimedRewards": ["main_quest", "side_quest_blacksmith"]
}
```

---

## Example 5: Dialogue State Component

For NPCs that remember conversation progress.

```java
package com.pocket.rpg.components;

import com.pocket.rpg.save.ISaveable;
import com.pocket.rpg.serialization.HideInInspector;

import java.util.*;

/**
 * Tracks dialogue state for an NPC.
 *
 * Saved state: dialogue stage, flags, last interaction time
 */
public class DialogueState extends Component implements ISaveable {

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    /**
     * Current dialogue tree stage.
     * 0 = first meeting, increments as player progresses.
     */
    @HideInInspector
    private int dialogueStage = 0;

    /**
     * Conversation flags (choices made, topics unlocked).
     */
    @HideInInspector
    private Set<String> flags = new HashSet<>();

    /**
     * Whether player has talked to this NPC today (resets daily).
     * Could be used for daily quests, shop resets, etc.
     */
    @HideInInspector
    private boolean talkedToday = false;

    /**
     * Number of times player has talked to this NPC.
     */
    @HideInInspector
    private int interactionCount = 0;

    // ========================================================================
    // GAME LOGIC
    // ========================================================================

    public void advanceStage() {
        dialogueStage++;
    }

    public void setFlag(String flag) {
        flags.add(flag);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public void markTalkedToday() {
        talkedToday = true;
        interactionCount++;
    }

    public void resetDaily() {
        talkedToday = false;
    }

    // Getters
    public int getDialogueStage() { return dialogueStage; }
    public boolean hasTalkedToday() { return talkedToday; }
    public int getInteractionCount() { return interactionCount; }

    // ========================================================================
    // ISaveable IMPLEMENTATION
    // ========================================================================

    @Override
    public Map<String, Object> getSaveState() {
        Map<String, Object> state = new HashMap<>();

        state.put("stage", dialogueStage);
        state.put("flags", new ArrayList<>(flags));
        state.put("talkedToday", talkedToday);
        state.put("interactionCount", interactionCount);

        return state;
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        if (state == null) return;

        if (state.containsKey("stage")) {
            dialogueStage = ((Number) state.get("stage")).intValue();
        }

        flags.clear();
        @SuppressWarnings("unchecked")
        List<String> savedFlags = (List<String>) state.get("flags");
        if (savedFlags != null) {
            flags.addAll(savedFlags);
        }

        if (state.containsKey("talkedToday")) {
            talkedToday = (Boolean) state.get("talkedToday");
        }

        if (state.containsKey("interactionCount")) {
            interactionCount = ((Number) state.get("interactionCount")).intValue();
        }
    }

    @Override
    public boolean hasSaveableState() {
        // Save if player has interacted at all
        return interactionCount > 0 || dialogueStage > 0;
    }
}
```

---

## Example 6: Door/Lock Component

Entity that can be permanently unlocked.

```java
package com.pocket.rpg.components;

import com.pocket.rpg.save.ISaveable;
import com.pocket.rpg.serialization.HideInInspector;

import java.util.*;

/**
 * A door that can be locked/unlocked.
 */
public class Door extends Component implements ISaveable {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Key item ID required to unlock. Empty = not locked.
     */
    private String requiredKey = "";

    /**
     * Whether key is consumed on use.
     */
    private boolean consumeKey = true;

    /**
     * Scene to load when entering (for scene transition doors).
     */
    private String targetScene = "";

    // ========================================================================
    // RUNTIME STATE
    // ========================================================================

    @HideInInspector
    private boolean unlocked = false;

    @HideInInspector
    private boolean isOpen = false;

    // ========================================================================
    // GAME LOGIC
    // ========================================================================

    public boolean tryUnlock(Inventory playerInventory) {
        if (unlocked || requiredKey.isEmpty()) {
            return true;  // Already unlocked or no key needed
        }

        if (playerInventory.hasItem(requiredKey)) {
            if (consumeKey) {
                playerInventory.removeItem(requiredKey);
            }
            unlocked = true;
            return true;
        }

        return false;  // Player doesn't have key
    }

    public void open() {
        if (unlocked || requiredKey.isEmpty()) {
            isOpen = true;
            // Play animation, disable collider, etc.
        }
    }

    public void close() {
        isOpen = false;
    }

    // Getters
    public boolean isUnlocked() { return unlocked || requiredKey.isEmpty(); }
    public boolean isOpen() { return isOpen; }
    public String getRequiredKey() { return requiredKey; }
    public String getTargetScene() { return targetScene; }

    // ========================================================================
    // ISaveable IMPLEMENTATION
    // ========================================================================

    @Override
    public Map<String, Object> getSaveState() {
        Map<String, Object> state = new HashMap<>();

        // Only save if state changed from default
        if (unlocked) {
            state.put("unlocked", true);
        }
        if (isOpen) {
            state.put("isOpen", true);
        }

        return state;
    }

    @Override
    public void loadSaveState(Map<String, Object> state) {
        if (state == null) return;

        unlocked = state.containsKey("unlocked") && (Boolean) state.get("unlocked");
        isOpen = state.containsKey("isOpen") && (Boolean) state.get("isOpen");
    }

    @Override
    public boolean hasSaveableState() {
        // Only save if door state changed
        return unlocked || isOpen;
    }
}
```

---

## Pattern Summary

| Component Type | What to Save | What NOT to Save |
|----------------|--------------|------------------|
| **Health** | currentHealth, maxHealth | isDead (derived) |
| **Inventory** | gold, items, equipped | maxSlots (config) |
| **Chest** | opened, looted | lootTable (config) |
| **Quest** | states, progress, rewards | quest definitions |
| **Dialogue** | stage, flags, count | dialogue content |
| **Door** | unlocked, isOpen | requiredKey (config) |

**General Rules:**
1. Save player-modified runtime state
2. Don't save configuration (set in editor)
3. Don't save derived/computed values
4. Use `@HideInInspector` for runtime fields
5. Use `hasSaveableState()` to skip unchanged entities
