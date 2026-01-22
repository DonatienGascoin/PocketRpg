# Collision Type Enhancements

## Problems to Solve

1. **Missing elevation transitions**: No way for players to move between floors (stairs, ladders)
2. **Hardcoded UI**: CollisionTypeSelector manually lists each type - must update for new types
3. **No visual distinction**: Triggers look the same in scene view (just colored squares)
4. **No icon support**: Types don't have associated icons for scene view rendering

**Terminology Note**:
- **Elevation** = Floor/layer level for collision (distinct from sprite zIndex for rendering)

---

## Solution 1: Add CollisionCategory Enum

**File**: `src/main/java/com/pocket/rpg/collision/CollisionCategory.java`

```java
public enum CollisionCategory {
    MOVEMENT("Movement", 0),
    LEDGE("Ledges", 1),
    TERRAIN("Terrain", 2),
    ELEVATION("Elevation", 3),
    TRIGGER("Triggers", 4);

    private final String displayName;
    private final int order;

    // Constructor, getters...

    public static CollisionCategory[] inOrder() {
        return Arrays.stream(values())
            .sorted(Comparator.comparingInt(CollisionCategory::getOrder))
            .toArray(CollisionCategory[]::new);
    }
}
```

---

## Solution 2: Enhance CollisionType Enum

**File**: `src/main/java/com/pocket/rpg/collision/CollisionType.java`

### Changes

1. Add `category` field
2. Add `icon` field (Material Icons codepoint)
3. Add new types: `STAIRS_UP`, `STAIRS_DOWN`
4. Add helper methods: `isTrigger()`, `requiresMetadata()`, `getIcon()`

### Updated Enum

```java
@Getter
public enum CollisionType {
    // === MOVEMENT ===
    NONE(0, "None", Category.MOVEMENT,
        new float[]{0.0f, 0.0f, 0.0f, 0.0f}, null, null),

    SOLID(1, "Solid", Category.MOVEMENT,
        new float[]{0.8f, 0.2f, 0.2f, 0.6f}, null, MaterialIcons.Block),

    // === LEDGES ===
    LEDGE_DOWN(2, "Ledge " + MaterialIcons.ArrowDownward, Category.LEDGE,
        new float[]{1.0f, 0.5f, 0.0f, 0.6f}, Direction.DOWN, MaterialIcons.ArrowDownward),

    LEDGE_UP(3, "Ledge " + MaterialIcons.ArrowUpward, Category.LEDGE,
        new float[]{1.0f, 0.7f, 0.0f, 0.6f}, Direction.UP, MaterialIcons.ArrowUpward),

    LEDGE_LEFT(4, "Ledge " + MaterialIcons.ArrowBack, Category.LEDGE,
        new float[]{1.0f, 0.6f, 0.0f, 0.6f}, Direction.LEFT, MaterialIcons.ArrowBack),

    LEDGE_RIGHT(5, "Ledge " + MaterialIcons.ArrowForward, Category.LEDGE,
        new float[]{1.0f, 0.65f, 0.0f, 0.6f}, Direction.RIGHT, MaterialIcons.ArrowForward),

    // === TERRAIN ===
    WATER(6, "Water", Category.TERRAIN,
        new float[]{0.2f, 0.5f, 0.9f, 0.6f}, null, MaterialIcons.Water),

    TALL_GRASS(7, "Tall Grass", Category.TERRAIN,
        new float[]{0.3f, 0.8f, 0.3f, 0.6f}, null, MaterialIcons.Grass),

    ICE(8, "Ice", Category.TERRAIN,
        new float[]{0.7f, 0.9f, 1.0f, 0.6f}, null, MaterialIcons.AcUnit),

    SAND(9, "Sand", Category.TERRAIN,
        new float[]{0.9f, 0.85f, 0.6f, 0.6f}, null, MaterialIcons.Terrain),

    // === ELEVATION TRANSITIONS (NEW) ===
    STAIRS_UP(13, "Stairs Up", Category.ELEVATION,
        new float[]{0.5f, 0.7f, 0.9f, 0.6f}, null, MaterialIcons.ArrowUpward),

    STAIRS_DOWN(14, "Stairs Down", Category.ELEVATION,
        new float[]{0.4f, 0.6f, 0.8f, 0.6f}, null, MaterialIcons.ArrowDownward),

    // === TRIGGERS ===
    WARP(10, "Warp", Category.TRIGGER,
        new float[]{0.8f, 0.3f, 0.8f, 0.6f}, null, MaterialIcons.ExitToApp),

    DOOR(11, "Door", Category.TRIGGER,
        new float[]{0.6f, 0.4f, 0.2f, 0.6f}, null, MaterialIcons.DoorFront),

    SCRIPT_TRIGGER(12, "Script", Category.TRIGGER,
        new float[]{0.9f, 0.9f, 0.3f, 0.6f}, null, MaterialIcons.Code);

    private final int id;
    private final String displayName;
    private final CollisionCategory category;
    private final float[] overlayColor;
    private final Direction ledgeDirection;
    private final String icon; // Material icon codepoint (nullable)

    // Constructor...

    /**
     * Returns true if this type requires trigger metadata configuration.
     */
    public boolean requiresMetadata() {
        return this == WARP || this == DOOR || this == SCRIPT_TRIGGER
            || this == STAIRS_UP || this == STAIRS_DOWN;
    }

    /**
     * Returns true if this type is a trigger (fires events).
     */
    public boolean isTrigger() {
        return category == CollisionCategory.TRIGGER
            || category == CollisionCategory.ELEVATION;
    }

    /**
     * Returns true if this type has an icon for scene view rendering.
     */
    public boolean hasIcon() {
        return icon != null;
    }

    /**
     * Gets all types in a specific category.
     */
    public static List<CollisionType> getByCategory(CollisionCategory category) {
        return Arrays.stream(values())
            .filter(t -> t.category == category)
            .toList();
    }
}
```

---

## Solution 3: Auto-Generated CollisionTypeSelector

**File**: `src/main/java/com/pocket/rpg/editor/panels/collisions/CollisionTypeSelector.java`

### Key Changes

1. Iterate `CollisionCategory.inOrder()` to render categories
2. For each category, iterate `CollisionType.getByCategory(category)`
3. No hardcoded type references
4. Automatically adapts when new types are added

### New Implementation

```java
public class CollisionTypeSelector {

    @Getter private CollisionType selectedType = CollisionType.SOLID;
    @Setter private Consumer<CollisionType> onTypeSelected;

    /**
     * Renders collision types grouped by category (horizontal layout).
     * Automatically generates UI from CollisionType enum.
     */
    public void renderHorizontal() {
        for (CollisionCategory category : CollisionCategory.inOrder()) {
            List<CollisionType> types = CollisionType.getByCategory(category);
            if (types.isEmpty()) continue;

            ImGui.textDisabled(category.getDisplayName());

            boolean first = true;
            for (CollisionType type : types) {
                if (!first) ImGui.sameLine();
                first = false;
                renderCollisionButton(type);
            }

            ImGui.spacing();
        }
    }

    /**
     * Renders collision types grouped by category (vertical layout).
     */
    public void render() {
        ImGui.text("Collision Types");

        for (CollisionCategory category : CollisionCategory.inOrder()) {
            List<CollisionType> types = CollisionType.getByCategory(category);
            if (types.isEmpty()) continue;

            ImGui.spacing();
            ImGui.textDisabled(category.getDisplayName());

            for (CollisionType type : types) {
                renderCollisionButton(type);
            }
        }

        ImGui.spacing();
        ImGui.separator();
        ImGui.text("Selected: " + selectedType.getDisplayName());
        ImGui.textWrapped(getTypeDescription(selectedType));
    }

    private void renderCollisionButton(CollisionType type) {
        boolean isSelected = (type == selectedType);
        float[] color = type.getOverlayColor();

        // ... existing styling logic ...

        // Calculate button width based on whether it's a ledge (shorter name)
        float buttonWidth = type.isLedge() ? 65 : 100;

        if (ImGui.button(type.getDisplayName() + "##" + type.name(), buttonWidth, 0)) {
            selectType(type);
        }

        // ... pop style colors ...

        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(getTypeDescription(type));
        }
    }

    /**
     * Gets description from CollisionType (could be stored in enum too).
     */
    public String getTypeDescription(CollisionType type) {
        return switch (type) {
            case NONE -> "No collision - fully walkable";
            case SOLID -> "Solid wall - blocks all movement";
            case LEDGE_DOWN -> "Ledge - can jump down (south)";
            case LEDGE_UP -> "Ledge - can jump up (north)";
            case LEDGE_LEFT -> "Ledge - can jump left (west)";
            case LEDGE_RIGHT -> "Ledge - can jump right (east)";
            case WATER -> "Water - requires swimming ability";
            case TALL_GRASS -> "Tall grass - triggers wild encounters";
            case ICE -> "Ice - causes sliding movement";
            case SAND -> "Sand - slows movement";
            case STAIRS_UP -> "Stairs going up - increases elevation";
            case STAIRS_DOWN -> "Stairs going down - decreases elevation";
            case WARP -> "Warp zone - triggers scene transition";
            case DOOR -> "Door - triggers door interaction";
            case SCRIPT_TRIGGER -> "Script trigger - executes custom script";
        };
    }
}
```

---

## Solution 4: Stairs Behavior

**File**: `src/main/java/com/pocket/rpg/collision/behavior/StairsBehavior.java`

```java
public class StairsBehavior implements TileBehavior {

    private final int zDelta; // +1 for STAIRS_UP, -1 for STAIRS_DOWN

    public StairsBehavior(int zDelta) {
        this.zDelta = zDelta;
    }

    @Override
    public MoveResult checkMove(int fromX, int fromY, int toX, int toY,
                                 Direction direction, MoveContext context) {
        // Stairs are always walkable
        return MoveResult.allowed(MovementModifier.NORMAL);
    }

    @Override
    public void onEnter(int x, int y, int z, GameObject entity) {
        // Z-level change is handled by TriggerSystem, not here
        // This allows for metadata like target Z-level override
    }
}
```

**Registration** in `CollisionBehaviorRegistry`:

```java
public void registerDefaults() {
    // ... existing registrations ...
    register(CollisionType.STAIRS_UP, new StairsBehavior(+1));
    register(CollisionType.STAIRS_DOWN, new StairsBehavior(-1));
}
```

---

## Material Icons to Add

Ensure these icons are available in `MaterialIcons.java`:

| Icon | Codepoint | Used For |
|------|-----------|----------|
| `ExitToApp` | `\uE879` | WARP |
| `DoorFront` | `\uEFFC` | DOOR |
| `Code` | `\uE86F` | SCRIPT_TRIGGER |
| `ArrowUpward` | `\uE5D8` | STAIRS_UP, LEDGE_UP |
| `ArrowDownward` | `\uE5DB` | STAIRS_DOWN, LEDGE_DOWN |
| `ArrowBack` | `\uE5C4` | LEDGE_LEFT |
| `ArrowForward` | `\uE5C8` | LEDGE_RIGHT |
| `Water` | `\uEA12` | WATER |
| `Grass` | `\uF205` | TALL_GRASS |
| `AcUnit` | `\uEB3B` | ICE |
| `Terrain` | `\uE564` | SAND |
| `Block` | `\uE868` | SOLID |
| `Warning` | `\uE002` | Missing metadata indicator |

---

## Summary of Changes

| File | Change Type | Description |
|------|-------------|-------------|
| `CollisionCategory.java` | **NEW** | Category enum for grouping |
| `CollisionType.java` | **MODIFY** | Add category, icon, new types, helpers |
| `CollisionTypeSelector.java` | **MODIFY** | Auto-generate UI from enum |
| `StairsBehavior.java` | **NEW** | Behavior for stairs |
| `CollisionBehaviorRegistry.java` | **MODIFY** | Register stairs behavior |
| `MaterialIcons.java` | **MODIFY** | Add missing icons |

---

## Testing Checklist

- [ ] All 15 collision types render in UI
- [ ] Types are grouped by category correctly
- [ ] Adding a new type to enum automatically appears in UI
- [ ] Stairs types have correct icons
- [ ] Description tooltips work for all types
