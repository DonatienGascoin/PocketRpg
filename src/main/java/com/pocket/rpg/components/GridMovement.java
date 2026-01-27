package com.pocket.rpg.components;

import com.pocket.rpg.collision.CollisionSystem;
import com.pocket.rpg.collision.CollisionType;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.MoveResult;
import com.pocket.rpg.collision.MovementModifier;
import com.pocket.rpg.collision.TileEntityMap;
import com.pocket.rpg.collision.trigger.TriggerSystem;
import com.pocket.rpg.components.interaction.TriggerZone;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.List;

/**
 * Component for Pokémon-style grid-based movement.
 * Player moves exactly one tile at a time, with smooth interpolation.
 *
 * <h2>Usage</h2>
 * <pre>
 * GridMovement movement = player.addComponent(new GridMovement());
 * movement.setGridPosition(5, 5); // Starting position
 *
 * // In your input handler:
 * if (Input.isKeyPressed(Key.UP)) {
 *     movement.move(Direction.UP);
 * }
 * </pre>
 *
 * <h2>Collision</h2>
 * Uses CollisionSystem to check tile collision before starting movement.
 * Respects solid tiles, ledges, water, ice, and other terrain types.
 *
 * <h2>Movement Modifiers</h2>
 * Terrain can modify movement speed and behavior:
 * - JUMP: Ledge jumps (slower, parabolic arc)
 * - SLOW: Sand/mud (60% speed)
 * - SLIDE: Ice (continues sliding until stopped)
 * - SWIM: Water (70% speed)
 * - ENCOUNTER: Tall grass (triggers random encounters)
 */
@ComponentMeta(category = "Physics")
public class GridMovement extends Component {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Base movement speed in tiles per second.
     * Default 4.0 means crossing one tile takes 0.25 seconds.
     */
    @Getter
    @Setter
    private float baseSpeed = 4f;

    /**
     * Maximum height of jump arc in world units.
     * Default 0.5 means the character rises half a tile at peak.
     */
    @Getter
    @Setter
    private float jumpHeight = 0.5f;

    /**
     * Tile size in world units (default 1.0).
     * Used to convert between tile coordinates and world positions.
     */
    @Getter
    @Setter
    private float tileSize = 1.0f;

    /**
     * Z-level for collision checking (default 0 = ground level).
     */
    @Getter
    @Setter
    private int zLevel = 0;

    // ========================================================================
    // STATE
    // ========================================================================

    /**
     * Current grid X position (tile coordinates).
     */
    @Getter
    private int gridX = 0;

    /**
     * Current grid Y position (tile coordinates).
     */
    @Getter
    private int gridY = 0;

    /**
     * True while moving between tiles.
     */
    @Getter
    private boolean isMoving = false;

    /**
     * True while sliding on ice (will auto-continue after movement completes).
     */
    @Getter
    private boolean isSliding = false;

    /**
     * Current movement modifier (affects speed/behavior).
     */
    @Getter
    private MovementModifier currentModifier = MovementModifier.NORMAL;

    /**
     * Current facing direction.
     */
    @Getter
    @Setter
    private Direction facingDirection = Direction.DOWN;

    // Internal movement state
    private final Vector3f startPos = new Vector3f();
    private final Vector3f targetPos = new Vector3f();
    private float moveProgress = 0f;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Creates GridMovement with default settings.
     */
    public GridMovement() {
    }

    /**
     * Creates GridMovement with specified tile size.
     *
     * @param tileSize Size of each tile in world units
     */
    public GridMovement(float tileSize) {
        this.tileSize = tileSize;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    protected void onStart() {
        // Register with entity occupancy map
        CollisionSystem collisionSystem = getCollisionSystem();
        if (collisionSystem != null) {
            collisionSystem.registerEntity(gameObject, gridX, gridY, zLevel);
        }
    }

    @Override
    protected void onDestroy() {
        // Unregister from entity occupancy map
        CollisionSystem collisionSystem = getCollisionSystem();
        if (collisionSystem != null) {
            collisionSystem.unregisterEntity(gameObject, gridX, gridY, zLevel);
        }
    }

    // ========================================================================
    // GRID POSITION
    // ========================================================================

    /**
     * Sets the grid position and snaps transform to that tile.
     * Use for teleportation or initial placement.
     *
     * @param gridX Tile X coordinate
     * @param gridY Tile Y coordinate
     */
    public void setGridPosition(int gridX, int gridY) {
        // Update entity occupancy
        CollisionSystem collisionSystem = getCollisionSystem();
        if (collisionSystem != null) {
            collisionSystem.moveEntity(gameObject, this.gridX, this.gridY, zLevel, gridX, gridY, zLevel);
        }

        this.gridX = gridX;
        this.gridY = gridY;
        isSliding = false; // Cancel any slide in progress
        snapToGrid();
    }

    /**
     * Snaps transform to current grid position.
     */
    private void snapToGrid() {
        if (gameObject == null) return;

        float worldX = gridX * tileSize + tileSize * 0.5f; // Center of tile
        float worldY = gridY * tileSize + tileSize * 0.5f;

        Vector3f pos = gameObject.getTransform().getPosition();
        gameObject.getTransform().setPosition(worldX, worldY, pos.z);
    }

    // ========================================================================
    // MOVEMENT
    // ========================================================================

    /**
     * Attempts to move in the given direction.
     * Does nothing if already moving or if blocked.
     *
     * @param direction Direction to move
     * @return true if movement started, false if blocked or already moving
     */
    public boolean move(Direction direction) {
        if (isMoving) {
            return false;
        }

        // Update facing direction
        facingDirection = direction;

        int targetX = gridX + direction.dx;
        int targetY = gridY + direction.dy;

        // Check collision using CollisionSystem
        MoveResult result = checkCollision(targetX, targetY, direction);

        if (!result.allowed()) {
            // If we were sliding and got blocked, stop sliding
            isSliding = false;
            return false;
        }

        // Start movement with modifier and direction
        startMovement(targetX, targetY, result.modifier(), direction);
        return true;
    }

    /**
     * Gets the CollisionSystem from the scene, or null if not available.
     */
    private CollisionSystem getCollisionSystem() {
        if (gameObject == null || gameObject.getScene() == null) {
            return null;
        }
        return gameObject.getScene().getCollisionSystem();
    }

    /**
     * Gets the TriggerSystem from the scene, or null if not available.
     */
    private TriggerSystem getTriggerSystem() {
        if (gameObject == null || gameObject.getScene() == null) {
            return null;
        }
        return gameObject.getScene().getTriggerSystem();
    }

    /**
     * Gets the TileEntityMap from the scene, or null if not available.
     */
    private TileEntityMap getTileEntityMap() {
        CollisionSystem collisionSystem = getCollisionSystem();
        return collisionSystem != null ? collisionSystem.getTileEntityMap() : null;
    }

    /**
     * Checks collision using the scene's CollisionSystem.
     */
    private MoveResult checkCollision(int targetX, int targetY, Direction direction) {
        CollisionSystem collisionSystem = getCollisionSystem();
        if (collisionSystem == null) {
            return MoveResult.Allowed(); // No collision system = allow movement
        }

        return collisionSystem.canMove(
                gridX, gridY, zLevel,
                targetX, targetY, zLevel,
                direction,
                gameObject
        );
    }

    /**
     * Starts movement to target tile.
     *
     * @param targetX   Target X coordinate
     * @param targetY   Target Y coordinate
     * @param modifier  Movement modifier (normal, slide, jump)
     * @param direction The direction of movement
     */
    private void startMovement(int targetX, int targetY, MovementModifier modifier, Direction direction) {
        if (gameObject == null) return;

        CollisionSystem collisionSystem = getCollisionSystem();
        TriggerSystem triggerSystem = getTriggerSystem();
        TileEntityMap tileEntityMap = getTileEntityMap();

        // Trigger exit on current tile (collision behavior)
        if (collisionSystem != null) {
            collisionSystem.triggerExit(gridX, gridY, zLevel, gameObject);
        }

        // Trigger exit on current tile (trigger system) with direction
        if (triggerSystem != null) {
            triggerSystem.onTileExit(gameObject, gridX, gridY, zLevel, direction);
        }

        // Notify TriggerZone components on exit (component-based triggers)
        if (tileEntityMap != null) {
            List<TriggerZone> triggers = tileEntityMap.get(gridX, gridY, zLevel, TriggerZone.class);
            for (TriggerZone trigger : triggers) {
                trigger.onEntityExit(gameObject);
            }
        }

        // Update entity occupancy
        if (collisionSystem != null) {
            collisionSystem.moveEntity(gameObject, gridX, gridY, zLevel, targetX, targetY, zLevel);
        }

        // Update grid position immediately (for collision purposes)
        gridX = targetX;
        gridY = targetY;

        // Store start position
        startPos.set(gameObject.getTransform().getPosition());

        // Calculate target world position
        targetPos.set(
                targetX * tileSize + tileSize * 0.5f,
                targetY * tileSize + tileSize * 0.5f,
                startPos.z
        );

        isMoving = true;
        currentModifier = modifier;
        moveProgress = 0f;

        // Track if we're starting a slide
        isSliding = (modifier == MovementModifier.SLIDE);
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    @Override
    public void update(float deltaTime) {
        if (!isMoving || gameObject == null) {
            return;
        }

        // Apply speed modifier
        float effectiveSpeed = baseSpeed * currentModifier.getSpeedMultiplier();

        // Update progress
        moveProgress += deltaTime * effectiveSpeed;

        if (moveProgress >= 1f) {
            // Movement complete
            finishMovement();
        } else {
            // Interpolate position
            updatePosition();
        }
    }

    /**
     * Updates transform position during movement.
     */
    private void updatePosition() {
        // Linear interpolation for X and Z
        float x = lerp(startPos.x, targetPos.x, moveProgress);
        float z = startPos.z;

        // Y interpolation with optional jump arc
        float y;
        if (currentModifier == MovementModifier.JUMP) {
            // Parabolic arc: 4 * t * (1 - t) peaks at 1.0 when t = 0.5
            float arc = 4f * moveProgress * (1f - moveProgress);
            float baseY = lerp(startPos.y, targetPos.y, moveProgress);
            y = baseY + (arc * jumpHeight);
        } else {
            y = lerp(startPos.y, targetPos.y, moveProgress);
        }

        gameObject.getTransform().setPosition(x, y, z);
    }

    /**
     * Completes movement and snaps to grid.
     */
    private void finishMovement() {
        moveProgress = 1f;
        isMoving = false;
        MovementModifier finishedModifier = currentModifier;
        currentModifier = MovementModifier.NORMAL;
        snapToGrid();

        // Apply elevation change from elevated ledges
        applyElevationChange();

        // Trigger enter on new tile (collision behavior)
        CollisionSystem collisionSystem = getCollisionSystem();
        if (collisionSystem != null) {
            collisionSystem.triggerEnter(gridX, gridY, zLevel, gameObject);
        }

        // Trigger enter on new tile (trigger system)
        TriggerSystem triggerSystem = getTriggerSystem();
        if (triggerSystem != null) {
            triggerSystem.onTileEnter(gameObject, gridX, gridY, zLevel);
        }

        // Notify TriggerZone components on enter (component-based triggers)
        TileEntityMap tileEntityMap = getTileEntityMap();
        if (tileEntityMap != null) {
            List<TriggerZone> triggers = tileEntityMap.get(gridX, gridY, zLevel, TriggerZone.class);
            for (TriggerZone trigger : triggers) {
                trigger.onEntityEnter(gameObject);
            }
        }

        // Handle ice sliding - continue sliding if we entered via slide
        // and the current tile is also ice
        if (isSliding) {
            handleIceSliding();
        }
    }

    /**
     * Applies elevation change from elevated ledge tiles.
     * Called after landing on a tile to adjust z-level.
     */
    private void applyElevationChange() {
        CollisionSystem collisionSystem = getCollisionSystem();
        if (collisionSystem == null) return;

        CollisionType tileType = collisionSystem.getCollisionMap().get(gridX, gridY, zLevel);
        if (tileType != null && tileType.hasElevationChange()) {
            int oldLevel = zLevel;
            zLevel += tileType.getElevationChange();
            System.out.println("[GridMovement] Elevated ledge: z-level " + oldLevel + " -> " + zLevel);
        }
    }

    /**
     * Handles ice sliding continuation.
     * Called after finishing movement onto a potentially icy tile.
     */
    private void handleIceSliding() {
        CollisionSystem collisionSystem = getCollisionSystem();
        if (collisionSystem == null) {
            isSliding = false;
            return;
        }

        // Check if we can continue sliding in the same direction
        int nextX = gridX + facingDirection.dx;
        int nextY = gridY + facingDirection.dy;

        MoveResult result = collisionSystem.canMove(
                gridX, gridY, zLevel,
                nextX, nextY, zLevel,
                facingDirection,
                gameObject
        );

        if (result.allowed()) {
            if (result.modifier() == MovementModifier.SLIDE) {
                // Next tile is also ice - continue sliding
                startMovement(nextX, nextY, MovementModifier.SLIDE, facingDirection);
            } else {
                // Next tile is not ice - slide onto it and stop
                isSliding = false;
                startMovement(nextX, nextY, result.modifier(), facingDirection);
            }
        } else {
            // Blocked - stop sliding
            isSliding = false;
        }
    }

    /**
     * Linear interpolation.
     */
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ========================================================================
    // UTILITY
    // ========================================================================

    /**
     * Checks if movement in a direction would be blocked.
     * Useful for animation/facing logic without starting movement.
     *
     * @param direction Direction to check
     * @return true if blocked
     */
    public boolean isBlocked(Direction direction) {
        int targetX = gridX + direction.dx;
        int targetY = gridY + direction.dy;
        MoveResult result = checkCollision(targetX, targetY, direction);
        return !result.allowed();
    }

    /**
     * Attempts to interact with a trigger at the current position or facing tile.
     * <p>
     * Call this when the player presses an interact button. Checks the tile
     * the entity is standing on first, then the tile they're facing.
     *
     * @return true if a trigger was activated
     */
    public boolean tryInteract() {
        TriggerSystem triggerSystem = getTriggerSystem();
        if (triggerSystem == null) {
            return false;
        }
        return triggerSystem.tryInteract(gameObject, gridX, gridY, zLevel, facingDirection);
    }

    /**
     * Forces an immediate stop and snap to current grid position.
     * Use sparingly - can cause visual glitches.
     */
    public void forceStop() {
        isMoving = false;
        isSliding = false;
        currentModifier = MovementModifier.NORMAL;
        moveProgress = 0f;
        snapToGrid();
    }

    /**
     * Checks if currently jumping.
     */
    public boolean isJumping() {
        return isMoving && currentModifier == MovementModifier.JUMP;
    }

    @Override
    public String toString() {
        return String.format("GridMovement[grid=(%d,%d), z=%d, moving=%b, sliding=%b, modifier=%s, facing=%s]",
                gridX, gridY, zLevel, isMoving, isSliding, currentModifier, facingDirection);
    }
}