# Save System - Scenarios by Option

This document shows concrete save file examples for different architectural choices, demonstrating how a Player, NPC, Chest, Enemies, and Boss would be saved in each scenario.

---

## Test Scenario Setup

Imagine the player has:
1. Played through "Village" scene
2. Opened 2 chests, talked to the blacksmith NPC
3. Killed 3 goblin enemies
4. Entered "DungeonLevel1" scene
5. Killed the boss, opened a chest
6. Returned to "Village"

---

# Decision 1: Scene Transition Handling

## Option A: Recreate from Save (RECOMMENDED)

All entities are destroyed and recreated from save data on scene change.

### How It Works

```
Player in Village → Enters Dungeon:
1. SaveManager captures Village state to memory
2. Village scene destroyed (all GameObjects)
3. DungeonLevel1 scene loads from .scene file
4. SaveManager applies saved state to entities with PersistentId
5. Player entity recreated at saved position
```

### Save File Example

```json
{
  "version": 1,
  "saveId": "abc123",
  "displayName": "After Boss Fight",
  "timestamp": 1705881600000,
  "playTime": 2400.0,

  "globalState": {
    "player": {
      "gold": 850,
      "level": 6,
      "experience": 3200
    }
  },

  "currentScene": "Village",

  "sceneStates": {
    "Village": {
      "sceneName": "Village",
      "modifiedEntities": {
        "player": {
          "persistentId": "player",
          "position": [24.5, 18.0, 0.0],
          "componentStates": {
            "com.pocket.rpg.components.Health": {
              "currentHealth": 95.0,
              "maxHealth": 120.0
            },
            "com.pocket.rpg.components.Inventory": {
              "items": ["sword_steel", "boss_key", "potion_health"],
              "equippedWeapon": "sword_steel"
            }
          }
        },
        "npc_blacksmith": {
          "persistentId": "npc_blacksmith",
          "componentStates": {
            "com.pocket.rpg.components.DialogueState": {
              "stage": 2,
              "flags": ["quest_accepted", "gave_sword"],
              "interactionCount": 5
            }
          }
        },
        "chest_01": {
          "persistentId": "chest_01",
          "componentStates": {
            "com.pocket.rpg.components.Chest": {
              "opened": true,
              "looted": true
            }
          }
        },
        "chest_02": {
          "persistentId": "chest_02",
          "componentStates": {
            "com.pocket.rpg.components.Chest": {
              "opened": true,
              "looted": true
            }
          }
        }
      },
      "destroyedEntities": [
        "enemy_goblin_01",
        "enemy_goblin_02",
        "enemy_goblin_03"
      ],
      "sceneFlags": {
        "visited": true,
        "tutorial_done": true
      }
    },
    "DungeonLevel1": {
      "sceneName": "DungeonLevel1",
      "modifiedEntities": {
        "player": {
          "persistentId": "player",
          "position": [50.0, 30.0, 0.0]
        },
        "chest_dungeon_01": {
          "persistentId": "chest_dungeon_01",
          "componentStates": {
            "com.pocket.rpg.components.Chest": {
              "opened": true,
              "looted": true
            }
          }
        }
      },
      "destroyedEntities": [
        "boss_minotaur"
      ],
      "sceneFlags": {
        "visited": true,
        "boss_defeated": true,
        "secret_found": false
      }
    }
  }
}
```

### Entity Breakdown

| Entity | Has PersistentId? | ISaveable? | What's Saved |
|--------|-------------------|------------|--------------|
| **Player** | Yes ("player") | Health, Inventory | Position, health, items, equipped |
| **NPC Blacksmith** | Yes ("npc_blacksmith") | DialogueState | Dialogue stage, flags |
| **Chest** | Yes ("chest_01") | Chest | opened, looted |
| **Goblin Enemies** | Yes ("enemy_goblin_01") | None | Nothing (tracked in destroyedEntities) |
| **Boss** | Yes ("boss_minotaur") | None | Nothing (tracked in destroyedEntities) |
| **Trees, Rocks** | No | No | Nothing (static scenery) |
| **Particles** | No | No | Nothing (temporary effects) |

### On Scene Load: What Happens

**Loading Village after save:**
1. Load Village.scene file (creates all entities at initial positions)
2. Apply saved state:
   - Find "player" → set position, restore Health and Inventory
   - Find "npc_blacksmith" → restore DialogueState
   - Find "chest_01", "chest_02" → restore Chest (opened=true)
   - Destroy "enemy_goblin_01", "enemy_goblin_02", "enemy_goblin_03"
3. Goblins are gone, chests stay open, player is where they saved

---

## Option B: DontDestroyOnLoad

Player entity survives scene transitions without being destroyed.

### How It Works

```
Player in Village → Enters Dungeon:
1. Player GameObject marked as persistent
2. Village scene destroyed EXCEPT persistent objects
3. Player moved to temporary "limbo"
4. DungeonLevel1 scene loads
5. Player added back to new scene
6. Player position set to spawn point
```

### Save File Example

With DontDestroyOnLoad, the player state doesn't need to be saved per-scene since the player object is never destroyed. Only scene-specific changes are saved.

```json
{
  "version": 1,
  "saveId": "abc123",
  "displayName": "After Boss Fight",
  "timestamp": 1705881600000,
  "playTime": 2400.0,

  "globalState": {
    "player": {
      "gold": 850,
      "level": 6,
      "experience": 3200
    }
  },

  "currentScene": "Village",

  "persistentEntities": {
    "player": {
      "persistentId": "player",
      "position": [24.5, 18.0, 0.0],
      "componentStates": {
        "com.pocket.rpg.components.Health": {
          "currentHealth": 95.0,
          "maxHealth": 120.0
        },
        "com.pocket.rpg.components.Inventory": {
          "items": ["sword_steel", "boss_key", "potion_health"],
          "equippedWeapon": "sword_steel"
        }
      }
    }
  },

  "sceneStates": {
    "Village": {
      "sceneName": "Village",
      "modifiedEntities": {
        "npc_blacksmith": {
          "persistentId": "npc_blacksmith",
          "componentStates": {
            "com.pocket.rpg.components.DialogueState": {
              "stage": 2,
              "flags": ["quest_accepted", "gave_sword"],
              "interactionCount": 5
            }
          }
        },
        "chest_01": {
          "persistentId": "chest_01",
          "componentStates": {
            "com.pocket.rpg.components.Chest": {
              "opened": true,
              "looted": true
            }
          }
        },
        "chest_02": {
          "persistentId": "chest_02",
          "componentStates": {
            "com.pocket.rpg.components.Chest": {
              "opened": true,
              "looted": true
            }
          }
        }
      },
      "destroyedEntities": [
        "enemy_goblin_01",
        "enemy_goblin_02",
        "enemy_goblin_03"
      ],
      "sceneFlags": {
        "visited": true
      }
    },
    "DungeonLevel1": {
      "sceneName": "DungeonLevel1",
      "modifiedEntities": {
        "chest_dungeon_01": {
          "persistentId": "chest_dungeon_01",
          "componentStates": {
            "com.pocket.rpg.components.Chest": {
              "opened": true,
              "looted": true
            }
          }
        }
      },
      "destroyedEntities": [
        "boss_minotaur"
      ],
      "sceneFlags": {
        "boss_defeated": true
      }
    }
  }
}
```

### Key Differences from Option A

| Aspect | Option A (Recreate) | Option B (DontDestroyOnLoad) |
|--------|---------------------|------------------------------|
| Player in sceneStates | Yes (per scene) | No (in persistentEntities) |
| Player position | Saved per scene | Single position |
| On scene change | Player recreated | Player moves between scenes |
| Animation state | Lost | Preserved |
| Runtime variables | Lost (unless saved) | Preserved |

### Complexity Warning

Option B requires additional infrastructure:
- New `persistentEntities` section in SaveData
- Modify SceneManager to skip destroying persistent objects
- Handle edge cases (what if scene has a "Player" prefab that conflicts?)
- Parent-child relationships become tricky

---

# Decision 2: Dynamically Spawned Entities

## Option A: Don't Save Spawned Entities (RECOMMENDED)

Only entities that exist in the scene file are saved. Runtime-spawned entities are lost.

### Scenario

Player kills 3 goblins that were in the scene file → They stay dead.
Spawner creates 2 new goblins at runtime → They respawn on reload.
Player drops a potion on the ground → It's lost on reload.

### Save File Example

```json
{
  "sceneStates": {
    "Village": {
      "modifiedEntities": {
        "player": { "..." },
        "chest_01": { "..." }
      },
      "destroyedEntities": [
        "enemy_goblin_01",
        "enemy_goblin_02",
        "enemy_goblin_03"
      ],
      "spawnedEntities": []
    }
  }
}
```

### Entity Behavior Table

| Entity | In Scene File? | Has PersistentId? | Behavior |
|--------|----------------|-------------------|----------|
| enemy_goblin_01 | Yes | Yes | Stays dead after reload |
| enemy_goblin_02 | Yes | Yes | Stays dead after reload |
| enemy_goblin_03 | Yes | Yes | Stays dead after reload |
| spawned_goblin_A | No (runtime) | No | Respawns on reload |
| spawned_goblin_B | No (runtime) | No | Respawns on reload |
| dropped_potion | No (runtime) | No | Lost on reload |

### Pros/Cons

**Pros:**
- Simple save format
- Small save files
- Enemies naturally respawn (often desired)
- No complex spawned entity tracking

**Cons:**
- Dropped items are lost
- Summoned companions disappear
- Runtime-created content not preserved

---

## Option B: Save Spawned Entities

Runtime-created entities with PersistentId are fully saved.

### Scenario

Player kills goblins → They stay dead.
Spawner creates goblins → Player kills them → They stay dead.
Player drops potion → It persists on reload.
Player summons companion → It persists.

### Save File Example

```json
{
  "sceneStates": {
    "Village": {
      "modifiedEntities": {
        "player": { "..." },
        "chest_01": { "..." }
      },
      "destroyedEntities": [
        "enemy_goblin_01",
        "enemy_goblin_02",
        "enemy_goblin_03",
        "spawned_7f3a2b1c",
        "spawned_9e4d8c2a"
      ],
      "spawnedEntities": [
        {
          "persistentId": "dropped_a1b2c3d4",
          "name": "Dropped Potion",
          "position": [15.0, 20.0, 0.0],
          "prefabId": "items/potion_health",
          "componentOverrides": {},
          "saveableStates": {}
        },
        {
          "persistentId": "companion_e5f6g7h8",
          "name": "Wolf Companion",
          "position": [23.0, 17.0, 0.0],
          "prefabId": "companions/wolf",
          "componentOverrides": {},
          "saveableStates": {
            "com.pocket.rpg.components.CompanionAI": {
              "loyalty": 85,
              "hunger": 30
            }
          }
        }
      ]
    }
  }
}
```

### Entity Behavior Table

| Entity | In Scene File? | Has PersistentId? | Behavior |
|--------|----------------|-------------------|----------|
| enemy_goblin_01 | Yes | Yes | Stays dead |
| enemy_goblin_02 | Yes | Yes | Stays dead |
| spawned_goblin_A | No | Yes (generated) | Stays dead (in destroyedEntities) |
| spawned_goblin_B | No | Yes (generated) | Stays dead |
| dropped_potion | No | Yes (generated) | Restored at position |
| wolf_companion | No | Yes (generated) | Restored with AI state |
| particle_effect | No | No | Lost (no PersistentId) |

### Implementation Requirements

When spawning an entity that should be saved:
```java
// Spawn companion that should persist
GameObject wolf = Prefabs.instantiate("companions/wolf");
PersistentId pid = new PersistentId();
pid.setId(PersistentId.generateId());  // Random ID
pid.setSaveWhenSpawned(true);          // Mark for saving
wolf.addComponent(pid);
scene.addGameObject(wolf);
```

When spawning an entity that should NOT be saved:
```java
// Spawn particle effect (temporary)
GameObject particle = Prefabs.instantiate("effects/explosion");
// No PersistentId - won't be saved
scene.addGameObject(particle);
```

---

## Option C: Configurable Per-Entity

Each spawned entity can opt-in to persistence.

### Save File Example

Same as Option B, but the game designer has fine-grained control.

### Code Example

```java
public class EnemySpawner extends Component {

    private String enemyPrefab = "enemies/goblin";
    private boolean enemiesRespawn = true;  // Config in editor

    public void spawnEnemy() {
        GameObject enemy = Prefabs.instantiate(enemyPrefab);

        if (!enemiesRespawn) {
            // These enemies stay dead
            PersistentId pid = new PersistentId();
            pid.setSaveWhenSpawned(true);
            enemy.addComponent(pid);
        }
        // else: no PersistentId, enemy respawns

        scene.addGameObject(enemy);
    }
}

public class ItemDrop extends Component {

    public void dropItem(String itemId, Vector2f position) {
        GameObject item = Prefabs.instantiate("items/" + itemId);
        item.getTransform().setPosition(position.x, position.y, 0);

        // Dropped items should persist
        PersistentId pid = new PersistentId();
        pid.setSaveWhenSpawned(true);
        pid.setPersistenceTag("dropped_item");
        item.addComponent(pid);

        scene.addGameObject(item);
    }
}
```

### Configuration Table

| Entity Type | saveWhenSpawned | Result |
|-------------|-----------------|--------|
| Spawned enemy (respawning area) | false | Respawns on reload |
| Spawned enemy (cleared dungeon) | true | Stays dead |
| Dropped loot | true | Persists |
| Summoned companion | true | Persists |
| Projectile | false | Disappears |
| Particle effect | No PersistentId | Disappears |

---

# Complete Comparison Table

## Entity: Player

| Aspect | Option A (Recreate) | Option B (DontDestroy) |
|--------|---------------------|------------------------|
| On scene change | Destroyed, recreated from save | Survives, moved to new scene |
| Position saved | Per-scene | Single global position |
| Health state | Saved in sceneStates | Preserved in memory |
| Inventory | Saved in sceneStates | Preserved in memory |
| Animation state | Lost | Preserved |
| Particle children | Lost | Preserved |

## Entity: NPC

| Aspect | Option A | Option B |
|--------|----------|----------|
| Dialogue progress | Saved per-scene | Saved per-scene |
| Position | Usually static (not saved) | Usually static |
| Quest state | Via DialogueState component | Same |

## Entity: Chest

| Aspect | Option A | Option B |
|--------|----------|----------|
| Opened state | Saved via Chest component | Same |
| Looted state | Saved via Chest component | Same |
| Position | Static (from scene file) | Same |

## Entity: Enemies (Scene-defined)

| Aspect | Option A | Option B |
|--------|----------|----------|
| Death | Tracked in destroyedEntities | Same |
| Respawn | Only if removed from destroyedEntities | Same |
| Health (if not dead) | Optionally saved via Health ISaveable | Same |

## Entity: Enemies (Spawned at Runtime)

| Aspect | Don't Save (A) | Save Spawned (B) | Configurable (C) |
|--------|----------------|------------------|------------------|
| Killed | Respawns | Stays dead | Depends on config |
| Alive | Respawns at spawn point | Restored at position | Depends on config |
| Save file size | Smaller | Larger | Medium |

## Entity: Boss

| Aspect | All Options |
|--------|-------------|
| Death | Tracked in destroyedEntities |
| Boss room state | Via sceneFlags ("boss_defeated") |
| Loot dropped | Depends on spawned entity option |
| Door unlocked | Via Door component ISaveable |

---

# Recommended Configuration

Based on typical RPG requirements:

| Decision | Choice | Reason |
|----------|--------|--------|
| Scene Transitions | **Option A: Recreate** | Simpler, matches existing architecture |
| Spawned Entities | **Option A: Don't Save** | Start simple, add later if needed |
| Auto-save | **Manual Only** | Game controls when to save |

This gives you:
- Clean scene transitions
- Small save files
- Predictable behavior
- Easy to debug
- Can add spawned entity saving later if needed
