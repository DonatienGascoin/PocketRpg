# Trigger System Solutions

## Requirements

| Requirement | Description |
|-------------|-------------|
| Scene transitions | WARP tiles teleport to another scene |
| Traps | Damage, status effects, push player |
| Dialogue | Talk to NPC, sign, object |
| One-shot | Fire once, never again |
| Retriggerable | Fire every time entered |
| Overlap-based | Continuous effect while inside |
| Entity filtering | Player-only vs any entity |

---

## Solution A: Extend TileBehavior (Minimal Change)

**Concept**: Use existing `TileBehavior` interface, add `onEnter`/`onExit`/`onStay` callbacks.

### Architecture

```
TileBehavior (existing)
├── checkMove(from, to, direction) → MoveResult
├── onEnter(x, y, z, entity)        ← NEW
├── onExit(x, y, z, entity)         ← NEW  
└── onStay(x, y, z, entity, dt)     ← NEW (for continuous effects)
```

### Implementation

```java
// TileBehavior.java - Extended
public interface TileBehavior {
    MoveResult checkMove(int fromX, int fromY, int toX, int toY, 
                         Direction direction, Object entity);
    
    default void onEnter(int x, int y, int z, GameObject entity) {}
    default void onExit(int x, int y, int z, GameObject entity) {}
    default void onStay(int x, int y, int z, GameObject entity, float deltaTime) {}
}

// WarpBehavior.java
public class WarpBehavior implements TileBehavior {
    @Override
    public MoveResult checkMove(...) {
        return MoveResult.allowed();
    }
    
    @Override
    public void onEnter(int x, int y, int z, GameObject entity) {
        // Problem: How do we know WHERE to warp?
        // CollisionType doesn't store per-tile data!
    }
}
```

### The Data Problem

`CollisionType` is just an enum - it can't store per-tile data like "warp to scene X at position Y".

**Workaround**: Separate `TriggerDataMap` alongside `CollisionMap`:

```java
public class TriggerDataMap {
    private Map<Long, TriggerData> triggers = new HashMap<>();
}

public record TriggerData(
    String type,
    Map<String, Object> data
) {}
```

### Pros/Cons

| Pros | Cons |
|------|------|
| Minimal code change | Data coupling problem |
| Reuses existing behavior system | No shape flexibility (always 1 tile) |
| Familiar pattern | Hard to do overlap detection |
| No new dependencies | `onStay` requires tracking which entities are on which tiles |

---

## Solution B: Component-Based Triggers (Unity-Style)

**Concept**: Triggers are `Component`s on `GameObject`s with a `Collider2D` that fires events.

### Architecture

```
GameObject "WarpZone"
├── Transform (position)
├── BoxTrigger2D (shape, size)
│      ├── onTriggerEnter(other)
│      ├── onTriggerExit(other)
│      └── onTriggerStay(other, dt)
└── WarpTrigger (custom logic)
       └── references BoxTrigger2D, handles warp
```

### Core Classes

```java
// Collider2D.java - Base trigger shape
public abstract class Collider2D extends Component {
    protected boolean isTrigger = true;
    protected int layerMask = 0xFFFF;
    
    public abstract boolean containsPoint(float worldX, float worldY);
    public abstract boolean intersects(Collider2D other);
}

// BoxTrigger2D.java
public class BoxTrigger2D extends Collider2D {
    private float width, height;
    private Vector2f offset = new Vector2f();
    
    @Override
    public boolean containsPoint(float worldX, float worldY) {
        Vector3f pos = gameObject.getTransform().getPosition();
        float left = pos.x + offset.x - width/2;
        float right = pos.x + offset.x + width/2;
        float bottom = pos.y + offset.y - height/2;
        float top = pos.y + offset.y + height/2;
        return worldX >= left && worldX <= right && 
               worldY >= bottom && worldY <= top;
    }
}

// TriggerSystem.java
public class TriggerSystem {
    private List<Collider2D> triggers = new ArrayList<>();
    private Map<Collider2D, Set<GameObject>> currentOverlaps = new HashMap<>();
    
    public void update(float deltaTime) {
        for (Collider2D trigger : triggers) {
            Set<GameObject> nowInside = findEntitiesInside(trigger);
            Set<GameObject> wasInside = currentOverlaps.getOrDefault(trigger, Set.of());
            
            // Enter/Exit/Stay events
            for (GameObject entity : nowInside) {
                if (!wasInside.contains(entity)) {
                    trigger.getGameObject().sendMessage("onTriggerEnter", entity);
                }
            }
            // ... exit and stay logic
        }
    }
}
```

### Usage Example

```java
GameObject warpZone = new GameObject("WarpToTown");
warpZone.getTransform().setPosition(5 * 16, 10 * 16, 0);

BoxTrigger2D trigger = warpZone.addComponent(new BoxTrigger2D());
trigger.setSize(16, 16);

WarpTrigger warp = warpZone.addComponent(new WarpTrigger());
warp.setTargetScene("town");
warp.setTargetPosition(3, 5);

scene.addGameObject(warpZone);
```

### Pros/Cons

| Pros | Cons |
|------|------|
| Flexible shapes (box, circle, polygon) | More code to write |
| Per-instance data (natural) | Need new TriggerSystem |
| Unity-familiar API | Performance: O(triggers × entities) per frame |
| Easy editor integration | Doesn't leverage existing CollisionMap |
| Supports multi-tile triggers | Two collision systems (tiles + triggers) |

---

## Solution C: JBox2D Sensors (Physics-Based)

**Concept**: Use JBox2D's sensor bodies for trigger detection. Sensors detect overlap but don't apply physics forces.

### Architecture

```
┌─────────────────────────────────────────────────┐
│                  PhysicsWorld                    │
│                  (JBox2D World)                  │
├─────────────────────────────────────────────────┤
│  Body (Player)          Body (WarpZone)         │
│  ├── BodyType.DYNAMIC   ├── BodyType.STATIC     │
│  └── Fixture            └── Fixture (SENSOR)    │
│       └── Shape              └── Shape          │
└─────────────────────────────────────────────────┘
                      │
                      ▼
              ContactListener
              ├── beginContact(A, B)
              └── endContact(A, B)
```

### Implementation

```java
public class PhysicsWorld {
    private World world;
    
    public PhysicsWorld() {
        world = new World(new Vec2(0, 0)); // No gravity for top-down
        world.setContactListener(new TriggerContactListener());
    }
    
    public Body createSensorBody(GameObject go, float x, float y, 
                                  float width, float height) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.STATIC;
        bodyDef.position.set(x, y);
        
        Body body = world.createBody(bodyDef);
        
        PolygonShape shape = new PolygonShape();
        shape.setAsBox(width/2, height/2);
        
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.shape = shape;
        fixtureDef.isSensor = true;  // KEY: No collision response
        
        body.createFixture(fixtureDef);
        return body;
    }
}
```

### Pros/Cons

| Pros | Cons |
|------|------|
| Battle-tested collision detection | Overkill for tile-based games |
| Efficient broad-phase (built-in) | Two coordinate systems (physics vs world) |
| Complex shapes (polygon, chain) | JBox2D uses meters, you use units |
| Future physics support ready | Learning curve |
| Handles fast-moving objects | Continuous physics step needed |

---

## Solution D: Hybrid (Recommended)

**Concept**: Keep `CollisionType` for simple tile triggers (WARP, DOOR), add lightweight `TriggerSystem` for complex cases.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Scene                                    │
├─────────────────────────────────────────────────────────────────┤
│  CollisionMap + TriggerDataMap          TriggerSystem            │
│  ┌─────────────────────────┐           ┌──────────────────┐     │
│  │ WARP at (5,10)          │           │ BoxTrigger2D     │     │
│  │ → {"scene":"cave"}      │           │ CircleTrigger2D  │     │
│  │                         │           │ (multi-tile,     │     │
│  │ DOOR at (3,3)           │           │  complex shapes) │     │
│  │ → {"locked":true}       │           └──────────────────┘     │
│  └─────────────────────────┘                                    │
│            │                                                     │
│            ▼                                                     │
│  GridMovement.onTileEnter()         TriggerSystem.update()      │
│            │                                    │                │
│            └────────────┬───────────────────────┘                │
│                         ▼                                        │
│                 TriggerEventBus                                  │
│                 (unified event dispatch)                         │
└─────────────────────────────────────────────────────────────────┘
```

### When to Use Each

| Use Case | Solution |
|----------|----------|
| Warp tile (1 tile, static) | `CollisionType.WARP` + `TriggerDataMap` |
| Door (1 tile, locked state) | `CollisionType.DOOR` + `TriggerDataMap` |
| Sign/NPC dialogue | `CollisionType.SCRIPT_TRIGGER` + `TriggerDataMap` |
| Large trap zone (5x5 tiles) | `AreaTrigger` component |
| Moving spike trap | `AreaTrigger` on moving GameObject |
| Continuous damage zone | `AreaTrigger` with `onStay()` |

### Pros/Cons

| Pros | Cons |
|------|------|
| Best of both worlds | Two systems to maintain |
| Simple cases stay simple | Slightly more complex architecture |
| Complex cases are possible | Need to decide which system for each trigger |
| Leverages existing CollisionMap | |
| No heavy dependencies | |

---

## Recommendation

**For PocketRPG: Solution D (Hybrid)**

**Reasoning**:
1. You already have tile-based collision - extending with `TriggerDataMap` is natural
2. Most RPG triggers are tile-based - warps, doors, signs, traps fit the grid
3. JBox2D is overkill - no physics needed for Zelda/Pokemon style
4. Future flexibility - component triggers available when needed

### Implementation Priority

| Phase | Feature | Effort |
|-------|---------|--------|
| 1 | `TriggerDataMap` + serialization | 1 day |
| 2 | `GridMovement` callbacks (`onTileEnter/Exit`) | 0.5 day |
| 3 | `TriggerEventBus` + basic handlers (WARP, DOOR) | 1 day |
| 4 | Editor support (trigger data editing) | 1-2 days |
| 5 | (Optional) `AreaTrigger` component for complex cases | 1 day |

### JBox2D: Keep for Later

Keep JBox2D in `pom.xml` but don't integrate now. Use it when you need:
- Platformer mechanics
- Physics puzzles (pushing blocks)
- Projectile physics
- Ragdoll effects
