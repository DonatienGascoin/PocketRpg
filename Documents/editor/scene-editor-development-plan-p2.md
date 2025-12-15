## Phase 2: Scene Serialization ✅ COMPLETED

**Status:** Implemented on 2025-12-09

**Goal:** Round-trip scene data: Editor saves → Game loads.

### Implementation Notes

**Architecture Decisions:**
- Generic component serialization via Gson TypeAdapters (not per-component)
- `ComponentSerializer`/`ComponentDeserializer` wraps components with type info
- Asset references (Sprite, Texture) serialize to file paths, resolve via AssetManager
- Hierarchy preserved by nesting children in `GameObjectData.children`
- Code-based prefabs via `PrefabRegistry` (JSON prefabs deferred to Phase 7+)

**Files Created:**
```
src/main/java/com/pocket/rpg/
├── serialization/
│   ├── ComponentSerializer.java      # Gson serializer for Component polymorphism
│   ├── ComponentDeserializer.java    # Gson deserializer
│   ├── SpriteTypeAdapter.java        # Sprite ↔ JSON with asset paths
│   ├── TextureTypeAdapter.java       # Texture ↔ path string  
│   ├── Vector2fTypeAdapter.java      # JOML Vector2f ↔ [x, y]
│   ├── Vector3fTypeAdapter.java      # JOML Vector3f ↔ [x, y, z]
│   ├── Vector4fTypeAdapter.java      # JOML Vector4f ↔ [x, y, z, w]
│   ├── SceneData.java                # Root scene structure
│   ├── GameObjectData.java           # GameObject with components + children
│   ├── SceneSerializer.java          # Save/load entry point
│   └── SceneLoader.java              # Game-side instantiation
├── prefabs/
│   ├── PrefabRegistry.java           # Code-based prefab factories
│   └── GamePrefabs.java              # Example prefab definitions
└── editor/rendering/
    ├── EditorFramebuffer.java        # OpenGL FBO for scene rendering
    └── EditorSceneRenderer.java      # Renders SceneData to framebuffer
```

**What Works:**
- ✅ Any Component subclass serializes automatically
- ✅ Sprite/Texture references resolve via AssetManager
- ✅ Parent-child hierarchy preserved
- ✅ Scene save/load round-trip
- ✅ Framebuffer rendering infrastructure
- ✅ PrefabRegistry for code-based prefabs

**Deferred to Later Phases:**
- EditorScene ↔ SceneData conversion (Phase 3.5)
- Complete save/load workflow in editor (Phase 3.5)
- CollisionMap layer (Phase 4)
- Inspector panel for editing (Phase 5)
- JSON-based prefabs (Phase 7+)

### 2.1 Scene File Format

```json
{
  "name": "Village",
  "version": 1,
  "camera": {
    "position": [0, 0],
    "orthographicSize": 15
  },
  "gameObjects": [
    {
      "name": "GroundLayer",
      "position": [0, 0, 0],
      "components": {
        "TilemapRenderer": {
          "zIndex": -1,
          "tileSize": 1.0,
          "tilesetPath": "gameData/assets/sprites/tileset.png",
          "tilesetCols": 16,
          "tilesetRows": 16,
          "chunks": {
            "0,0": {
              "tiles": [
                [0, 1, 2, -1, 3],
                [4, 5, 6, 7, 8]
              ]
            }
          }
        }
      }
    }
  ],
  "entities": [
    {
      "prefabId": "Player",
      "name": "Player",
      "position": [5, 5, 0],
      "properties": {}
    }
  ],
  "collision": {
    "tileSize": 1.0,
    "layers": {
      "0": {
        "chunks": {
          "0,0": {
            "data": [[0, 0, 1, 1], [0, 0, 1, 1]]
          }
        }
      }
    }
  },
  "triggers": [
    {
      "name": "ToForest",
      "type": "SCENE_TRANSITION",
      "bounds": [15, 10, 2, 1],
      "properties": {
        "targetScene": "Forest",
        "spawnPoint": "FromVillage"
      }
    }
  ]
}
```

---