## Phase 4: Collision Editing ✅ COMPLETED

**Goal:** Paint collision data with a flexible, extensible system that supports multiple collision types, Z-levels, and custom behaviors.

### 4.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         SERIALIZED                              │
│  (stored in .scene file)                                        │
├─────────────────────────────────────────────────────────────────┤
│  CollisionMap                                                   │
│  ├── tileSize: 1.0                                              │
│  └── zLayers                                                    │
│      ├── 0 (ground): CollisionLayer → chunks of int IDs        │
│      └── 1 (elevated): CollisionLayer → chunks of int IDs      │
│                                                                 │
│  (Just integers - CollisionType IDs. No behavior code stored)   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        NOT SERIALIZED                           │
│  (created at game startup, lives in code)                       │
├─────────────────────────────────────────────────────────────────┤
│  CollisionBehaviorRegistry (singleton)                          │
│  ├── PASSABLE → PassableBehavior                                │
│  ├── SOLID    → SolidBehavior                                   │
│  ├── WATER    → WaterBehavior                                   │
│  └── ...                                                        │
│                                                                 │
│  EntityOccupancyMap (rebuilt at scene load)                     │
│  ├── (5, 3, 0) → NPC_01                                         │
│  └── (2, 7, 0) → Player                                         │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 CollisionType Enum

```java
public enum CollisionType {
    PASSABLE(0, "Passable", new Color(0, 0, 0, 0)),
    SOLID(1, "Solid", new Color(1, 0, 0, 0.5f)),
    WATER(2, "Water", new Color(0, 0, 1, 0.5f)),
    ICE(3, "Ice", new Color(0, 1, 1, 0.5f)),
    LEDGE_DOWN(4, "Ledge ↓", new Color(1, 1, 0, 0.5f)),
    LEDGE_UP(5, "Ledge ↑", new Color(1, 1, 0, 0.5f)),
    LEDGE_LEFT(6, "Ledge ←", new Color(1, 1, 0, 0.5f)),
    LEDGE_RIGHT(7, "Ledge →", new Color(1, 1, 0, 0.5f)),
    STAIRS_UP(8, "Stairs ↑Z", new Color(0, 1, 0, 0.5f)),
    STAIRS_DOWN(9, "Stairs ↓Z", new Color(0, 0.5f, 0, 0.5f)),
    TRIGGER(10, "Trigger", new Color(1, 0, 1, 0.3f));
    
    private final int id;
    private final String displayName;
    private final Color editorColor;
    
    public record Color(float r, float g, float b, float a) {}
    
    public static CollisionType fromId(int id) {
        for (CollisionType type : values()) {
            if (type.id == id) return type;
        }
        return PASSABLE;
    }
}
```

### 4.3 Collision Data Structures

```java
public class CollisionMap {
    private final float tileSize;
    private final Map<Integer, CollisionLayer> zLayers = new HashMap<>();
    
    public CollisionMap(float tileSize) {
        this.tileSize = tileSize;
        zLayers.put(0, new CollisionLayer());  // Default ground layer
    }
    
    public CollisionType get(int x, int y, int zLevel) {
        CollisionLayer layer = zLayers.get(zLevel);
        return layer != null ? layer.get(x, y) : CollisionType.PASSABLE;
    }
    
    public void set(int x, int y, int zLevel, CollisionType type) {
        zLayers.computeIfAbsent(zLevel, k -> new CollisionLayer()).set(x, y, type);
    }
}

public class CollisionLayer {
    public static final int CHUNK_SIZE = 32;
    private final Map<Long, CollisionChunk> chunks = new HashMap<>();
    
    public CollisionType get(int x, int y) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        CollisionChunk chunk = chunks.get(packCoord(cx, cy));
        if (chunk == null) return CollisionType.PASSABLE;
        return chunk.get(Math.floorMod(x, CHUNK_SIZE), Math.floorMod(y, CHUNK_SIZE));
    }
    
    public void set(int x, int y, CollisionType type) {
        int cx = Math.floorDiv(x, CHUNK_SIZE);
        int cy = Math.floorDiv(y, CHUNK_SIZE);
        chunks.computeIfAbsent(packCoord(cx, cy), k -> new CollisionChunk())
              .set(Math.floorMod(x, CHUNK_SIZE), Math.floorMod(y, CHUNK_SIZE), type);
    }
}

public class CollisionChunk {
    private final byte[][] data = new byte[CHUNK_SIZE][CHUNK_SIZE];
    
    public CollisionType get(int lx, int ly) {
        return CollisionType.fromId(data[ly][lx] & 0xFF);
    }
    
    public void set(int lx, int ly, CollisionType type) {
        data[ly][lx] = (byte) type.getId();
    }
}
```

### 4.4 TileBehavior System

```java
public interface TileBehavior {
    MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction moveDir);
    default void onEnter(GameObject entity, int x, int y) {}
    default void onExit(GameObject entity, int x, int y) {}
    default void onStay(GameObject entity, int x, int y, float deltaTime) {}
}

public record MoveResult(boolean allowed, MovementModifier modifier, String blockedReason) {
    public static final MoveResult ALLOWED = new MoveResult(true, MovementModifier.NONE, null);
    public static final MoveResult BLOCKED = new MoveResult(false, MovementModifier.NONE, "Blocked");
    
    public static MoveResult blocked(String reason) {
        return new MoveResult(false, MovementModifier.NONE, reason);
    }
    
    public static MoveResult allowedWith(MovementModifier modifier) {
        return new MoveResult(true, modifier, null);
    }
}

public enum MovementModifier {
    NONE, SLIDE, JUMP, SLOW, FAST
}

public enum Direction {
    UP(0, 1), DOWN(0, -1), LEFT(-1, 0), RIGHT(1, 0);
    
    public final int dx, dy;
    Direction(int dx, int dy) { this.dx = dx; this.dy = dy; }
    
    public Direction opposite() {
        return switch (this) {
            case UP -> DOWN; case DOWN -> UP;
            case LEFT -> RIGHT; case RIGHT -> LEFT;
        };
    }
}
```

### 4.5 Behavior Implementations

```java
public class PassableBehavior implements TileBehavior {
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction dir) {
        return MoveResult.ALLOWED;
    }
}

public class SolidBehavior implements TileBehavior {
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction dir) {
        return MoveResult.blocked("Solid obstacle");
    }
}

public class WaterBehavior implements TileBehavior {
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction dir) {
        SwimmingAbility swimming = entity.getComponent(SwimmingAbility.class);
        if (swimming != null && swimming.canSwim()) {
            return MoveResult.ALLOWED;
        }
        return MoveResult.blocked("Cannot swim");
    }
    
    public void onEnter(GameObject entity, int x, int y) {
        Animator animator = entity.getComponent(Animator.class);
        if (animator != null) animator.play("swim_start");
    }
    
    public void onExit(GameObject entity, int x, int y) {
        Animator animator = entity.getComponent(Animator.class);
        if (animator != null) animator.play("swim_end");
    }
}

public class IceBehavior implements TileBehavior {
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction dir) {
        return MoveResult.allowedWith(MovementModifier.SLIDE);
    }
}

public class LedgeBehavior implements TileBehavior {
    private final Direction allowedDirection;
    
    public LedgeBehavior(Direction allowedDirection) {
        this.allowedDirection = allowedDirection;
    }
    
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction moveDir) {
        if (moveDir == allowedDirection) {
            return MoveResult.allowedWith(MovementModifier.JUMP);
        }
        return MoveResult.blocked("Wrong direction for ledge");
    }
}

public class ZLevelChangeBehavior implements TileBehavior {
    private final int targetZLevel;
    
    public ZLevelChangeBehavior(int targetZLevel) {
        this.targetZLevel = targetZLevel;
    }
    
    public MoveResult evaluateEntry(GameObject entity, int toX, int toY, Direction dir) {
        return MoveResult.ALLOWED;
    }
    
    public void onEnter(GameObject entity, int x, int y) {
        ZLevelComponent zLevel = entity.getComponent(ZLevelComponent.class);
        if (zLevel != null) zLevel.setZLevel(targetZLevel);
    }
}
```

### 4.6 CollisionBehaviorRegistry

```java
public class CollisionBehaviorRegistry {
    private static CollisionBehaviorRegistry instance;
    private final Map<CollisionType, TileBehavior> behaviors = new EnumMap<>(CollisionType.class);
    
    private CollisionBehaviorRegistry() {
        registerDefaults();
    }
    
    public static CollisionBehaviorRegistry getInstance() {
        if (instance == null) instance = new CollisionBehaviorRegistry();
        return instance;
    }
    
    private void registerDefaults() {
        register(CollisionType.PASSABLE, new PassableBehavior());
        register(CollisionType.SOLID, new SolidBehavior());
        register(CollisionType.WATER, new WaterBehavior());
        register(CollisionType.ICE, new IceBehavior());
        register(CollisionType.LEDGE_DOWN, new LedgeBehavior(Direction.DOWN));
        register(CollisionType.LEDGE_UP, new LedgeBehavior(Direction.UP));
        register(CollisionType.LEDGE_LEFT, new LedgeBehavior(Direction.LEFT));
        register(CollisionType.LEDGE_RIGHT, new LedgeBehavior(Direction.RIGHT));
        register(CollisionType.STAIRS_UP, new ZLevelChangeBehavior(1));
        register(CollisionType.STAIRS_DOWN, new ZLevelChangeBehavior(0));
        register(CollisionType.TRIGGER, new TriggerBehavior());
    }
    
    public void register(CollisionType type, TileBehavior behavior) {
        behaviors.put(type, behavior);
    }
    
    public TileBehavior getBehavior(CollisionType type) {
        return behaviors.getOrDefault(type, new PassableBehavior());
    }
}
```

### 4.7 GridCollisionSystem

```java
public interface CollisionSystem {
    MoveResult canMove(GameObject entity, int fromX, int fromY, int toX, int toY, Direction dir);
    void onEntityMoved(GameObject entity, int oldX, int oldY, int newX, int newY);
}

public class GridCollisionSystem implements CollisionSystem {
    private final CollisionMap collisionMap;
    private final CollisionBehaviorRegistry behaviorRegistry;
    private final EntityOccupancyMap entityOccupancy;
    
    public GridCollisionSystem(CollisionMap collisionMap) {
        this.collisionMap = collisionMap;
        this.behaviorRegistry = CollisionBehaviorRegistry.getInstance();
        this.entityOccupancy = new EntityOccupancyMap();
    }
    
    public MoveResult canMove(GameObject entity, int fromX, int fromY, int toX, int toY, Direction dir) {
        int zLevel = getEntityZLevel(entity);
        
        // Check tile collision
        CollisionType tileType = collisionMap.get(toX, toY, zLevel);
        TileBehavior behavior = behaviorRegistry.getBehavior(tileType);
        MoveResult tileResult = behavior.evaluateEntry(entity, toX, toY, dir);
        
        if (!tileResult.allowed()) return tileResult;
        
        // Check entity occupancy
        GameObject occupant = entityOccupancy.getEntityAt(toX, toY, zLevel);
        if (occupant != null && occupant != entity && !canPassThrough(entity, occupant)) {
            return MoveResult.blocked("Blocked by " + occupant.getName());
        }
        
        return tileResult;
    }
    
    public void onEntityMoved(GameObject entity, int oldX, int oldY, int newX, int newY) {
        int zLevel = getEntityZLevel(entity);
        
        entityOccupancy.remove(entity, oldX, oldY, zLevel);
        entityOccupancy.set(entity, newX, newY, zLevel);
        
        CollisionType oldType = collisionMap.get(oldX, oldY, zLevel);
        behaviorRegistry.getBehavior(oldType).onExit(entity, oldX, oldY);
        
        CollisionType newType = collisionMap.get(newX, newY, zLevel);
        behaviorRegistry.getBehavior(newType).onEnter(entity, newX, newY);
    }
    
    private int getEntityZLevel(GameObject entity) {
        ZLevelComponent zComp = entity.getComponent(ZLevelComponent.class);
        return zComp != null ? zComp.getZLevel() : 0;
    }
    
    private boolean canPassThrough(GameObject mover, GameObject occupant) {
        EntityCollider collider = occupant.getComponent(EntityCollider.class);
        return collider == null || !collider.blocksEntities();
    }
}
```

### 4.8 EntityOccupancyMap

```java
public class EntityOccupancyMap {
    private final Map<Long, GameObject> occupancy = new HashMap<>();
    private final Map<GameObject, long[]> entityPositions = new HashMap<>();
    
    public void set(GameObject entity, int x, int y, int z) {
        occupancy.put(packCoord(x, y, z), entity);
        entityPositions.put(entity, new long[]{x, y, z});
    }
    
    public void remove(GameObject entity, int x, int y, int z) {
        long key = packCoord(x, y, z);
        if (occupancy.get(key) == entity) occupancy.remove(key);
        entityPositions.remove(entity);
    }
    
    public GameObject getEntityAt(int x, int y, int z) {
        return occupancy.get(packCoord(x, y, z));
    }
    
    private long packCoord(int x, int y, int z) {
        return ((long)(z & 0xFFFF) << 48) | ((long)(x & 0xFFFFFF) << 24) | (y & 0xFFFFFF);
    }
}
```

### 4.9 ZLevelComponent

```java
public class ZLevelComponent extends Component {
    @Getter
    private int zLevel = 0;
    
    public void setZLevel(int zLevel) {
        this.zLevel = zLevel;
    }
}
```

### 4.10 GridMovement Integration

```java
public class GridMovement extends Component {
    @Getter @Setter
    private CollisionSystem collisionSystem;  // Replace TilemapRenderer
    
    public boolean move(Direction direction) {
        if (isMoving) return false;
        
        updateFacingDirection(direction.dx, direction.dy);
        
        int targetX = gridX + direction.dx;
        int targetY = gridY + direction.dy;
        
        MoveResult result;
        if (collisionSystem != null) {
            result = collisionSystem.canMove(gameObject, gridX, gridY, targetX, targetY, direction);
        } else {
            result = MoveResult.ALLOWED;
        }
        
        if (!result.allowed()) return false;
        
        startMovement(targetX, targetY, result.modifier());
        return true;
    }
    
    private void startMovement(int targetX, int targetY, MovementModifier modifier) {
        int oldX = gridX, oldY = gridY;
        gridX = targetX;
        gridY = targetY;
        
        // ... existing position interpolation setup ...
        
        isJumping = (modifier == MovementModifier.JUMP);
        isSliding = (modifier == MovementModifier.SLIDE);
        
        if (collisionSystem != null) {
            collisionSystem.onEntityMoved(gameObject, oldX, oldY, targetX, targetY);
        }
    }
    
    private void finishMovement() {
        // ... existing finish logic ...
        
        // Handle sliding (ice)
        if (isSliding && collisionSystem != null) {
            isSliding = false;
            MoveResult result = collisionSystem.canMove(
                gameObject, gridX, gridY,
                gridX + facingDirection.dx, gridY + facingDirection.dy,
                facingDirection
            );
            if (result.allowed() && result.modifier() == MovementModifier.SLIDE) {
                move(facingDirection);  // Continue sliding
            }
        }
    }
}
```

### 4.11 Editor: Collision Brush Tool

```java
public class CollisionBrushTool implements EditorTool {
    private EditorScene scene;
    private CollisionType selectedType = CollisionType.SOLID;
    private int selectedZLevel = 0;
    private int brushSize = 1;
    
    public String getName() { return "Collision"; }
    public String getShortcutKey() { return "C"; }
    
    public void onMouseDown(int tileX, int tileY, int button) {
        if (button == 0) paintAt(tileX, tileY);
        else if (button == 1) eraseAt(tileX, tileY);
    }
    
    private void paintAt(int tileX, int tileY) {
        CollisionMap map = scene.getCollisionMap();
        int half = brushSize / 2;
        for (int dy = -half; dy <= half; dy++) {
            for (int dx = -half; dx <= half; dx++) {
                map.set(tileX + dx, tileY + dy, selectedZLevel, selectedType);
            }
        }
        scene.markDirty();
    }
    
    public void renderOverlay(EditorCamera camera, int hoveredTileX, int hoveredTileY) {
        if (!scene.isCollisionVisible()) return;
        
        // Draw colored overlay for each collision tile
        CollisionMap map = scene.getCollisionMap();
        float[] bounds = camera.getWorldBounds();
        
        for (int y = (int)bounds[1]; y <= (int)bounds[3]; y++) {
            for (int x = (int)bounds[0]; x <= (int)bounds[2]; x++) {
                CollisionType type = map.get(x, y, selectedZLevel);
                if (type == CollisionType.PASSABLE) continue;
                
                CollisionType.Color c = type.getEditorColor();
                int color = ImGui.colorConvertFloat4ToU32(c.r(), c.g(), c.b(), c.a());
                drawTileHighlight(camera, x, y, color);
            }
        }
    }
}
```

### 4.12 Editor: Collision Panel

```java
public class CollisionPanel {
    private EditorScene scene;
    private CollisionBrushTool collisionTool;
    
    public void render() {
        if (ImGui.begin("Collision")) {
            // Visibility toggle
            boolean visible = scene.isCollisionVisible();
            if (ImGui.checkbox("Show Collision", visible)) {
                scene.setCollisionVisible(!visible);
            }
            
            // Z-Level selector
            int[] zLevel = {collisionTool.getSelectedZLevel()};
            ImGui.combo("Z-Level", zLevel, new String[]{"Ground (0)", "Elevated (1)"});
            collisionTool.setSelectedZLevel(zLevel[0]);
            
            // Collision type selector with color indicators
            for (CollisionType type : CollisionType.values()) {
                CollisionType.Color c = type.getEditorColor();
                ImGui.colorButton("##" + type.name(), new float[]{c.r(), c.g(), c.b(), c.a()});
                ImGui.sameLine();
                if (ImGui.radioButton(type.getDisplayName(), 
                    collisionTool.getSelectedType() == type)) {
                    collisionTool.setSelectedType(type);
                }
            }
            
            // Brush size
            int[] size = {collisionTool.getBrushSize()};
            ImGui.sliderInt("Brush Size", size, 1, 10);
            collisionTool.setBrushSize(size[0]);
        }
        ImGui.end();
    }
}
```

### 4.13 Files to Create

```
src/main/java/com/pocket/rpg/
├── collision/
│   ├── CollisionType.java
│   ├── CollisionMap.java
│   ├── CollisionLayer.java
│   ├── CollisionChunk.java
│   ├── CollisionSystem.java
│   ├── GridCollisionSystem.java
│   ├── EntityOccupancyMap.java
│   ├── MoveResult.java
│   ├── MovementModifier.java
│   ├── Direction.java
│   └── behaviors/
│       ├── TileBehavior.java
│       ├── CollisionBehaviorRegistry.java
│       ├── PassableBehavior.java
│       ├── SolidBehavior.java
│       ├── WaterBehavior.java
│       ├── IceBehavior.java
│       ├── LedgeBehavior.java
│       ├── ZLevelChangeBehavior.java
│       └── TriggerBehavior.java
├── components/
│   └── ZLevelComponent.java
└── editor/
    ├── tools/CollisionBrushTool.java
    └── panels/CollisionPanel.java
```

### 4.14 Testing Checklist

- [ ] Paint collision types on ground layer (Z=0)
- [ ] Paint collision types on elevated layer (Z=1)
- [ ] Toggle collision visibility
- [ ] Collision overlay shows correct colors
- [ ] Collision data saves/loads from .scene file
- [ ] Player blocked by SOLID tiles
- [ ] Player slides on ICE tiles
- [ ] Player jumps over LEDGE in correct direction
- [ ] Player changes Z-level on STAIRS tiles
- [ ] NPCs block player (entity occupancy)

---