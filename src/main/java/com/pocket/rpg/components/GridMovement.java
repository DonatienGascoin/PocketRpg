package com.pocket.rpg.components;

import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Component for Pokémon-style grid-based movement.
 * Player moves exactly one tile at a time, with smooth interpolation.
 *
 * <h2>Usage</h2>
 * <pre>
 * GridMovement movement = player.addComponent(new GridMovement(tilemap));
 * movement.setGridPosition(5, 5); // Starting position
 *
 * // In your input handler:
 * if (Input.isKeyPressed(Key.UP)) {
 *     movement.move(0, 1);
 * }
 * </pre>
 *
 * <h2>Collision</h2>
 * Checks tile collision before starting movement. Solid tiles block movement.
 * Ledge tiles allow jumping in their specified direction.
 *
 * <h2>Jumping</h2>
 * When moving onto a ledge tile in the ledge's direction, a jump is triggered.
 * Jumps have a parabolic visual arc but move exactly one tile.
 */
public class GridMovement extends Component {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Reference to tilemap for collision checking and tile size.
     */
    @Getter
    @Setter
    private TilemapRenderer tilemap;

    /**
     * Movement speed in tiles per second.
     * Default 4.0 means crossing one tile takes 0.25 seconds.
     */
    @Getter
    @Setter
    private float moveSpeed = 4f;

    /**
     * Jump speed multiplier. Jumps take longer than normal steps.
     * Default 0.5 means jumps take twice as long.
     */
    @Getter
    @Setter
    private float jumpSpeedMultiplier = 0.5f;

    /**
     * Maximum height of jump arc in world units.
     * Default 0.5 means the character rises half a tile at peak.
     */
    @Getter
    @Setter
    private float jumpHeight = 0.5f;

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
     * True while performing a jump.
     */
    @Getter
    private boolean isJumping = false;

    /**
     * Current facing direction.
     */
    @Getter
    private Direction facingDirection = Direction.DOWN;

    // Internal movement state
    private final Vector3f startPos = new Vector3f();
    private final Vector3f targetPos = new Vector3f();
    private float moveProgress = 0f;

    // ========================================================================
    // DIRECTION ENUM
    // ========================================================================

    /**
     * Movement/facing directions.
     */
    public enum Direction {
        UP(0, 1),
        DOWN(0, -1),
        LEFT(-1, 0),
        RIGHT(1, 0);

        public final int dx;
        public final int dy;

        Direction(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        /**
         * Converts to LedgeDirection for collision checking.
         */
        public TilemapRenderer.LedgeDirection toLedgeDirection() {
            return switch (this) {
                case UP -> TilemapRenderer.LedgeDirection.UP;
                case DOWN -> TilemapRenderer.LedgeDirection.DOWN;
                case LEFT -> TilemapRenderer.LedgeDirection.LEFT;
                case RIGHT -> TilemapRenderer.LedgeDirection.RIGHT;
            };
        }
    }

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    /**
     * Creates GridMovement without tilemap reference.
     * Set tilemap later via {@link #setTilemap(TilemapRenderer)}.
     */
    public GridMovement() {
    }

    /**
     * Creates GridMovement with tilemap reference.
     *
     * @param tilemap The tilemap for collision and tile size
     */
    public GridMovement(TilemapRenderer tilemap) {
        this.tilemap = tilemap;
    }

    // ========================================================================
    // GRID POSITION
    // ========================================================================

    /**
     * Sets the grid position and snaps transform to that tile.
     *
     * @param gridX Tile X coordinate
     * @param gridY Tile Y coordinate
     */
    public void setGridPosition(int gridX, int gridY) {
        this.gridX = gridX;
        this.gridY = gridY;
        snapToGrid();
    }

    /**
     * Snaps transform to current grid position.
     */
    private void snapToGrid() {
        if (gameObject == null) return;

        float tileSize = getTileSize();
        float worldX = gridX * tileSize + tileSize * 0.5f; // Center of tile
        float worldY = gridY * tileSize + tileSize * 0.5f;

        Vector3f pos = gameObject.getTransform().getPosition();
        gameObject.getTransform().setPosition(worldX, worldY, pos.z);
    }

    /**
     * Gets the tile size from tilemap, or defaults to 1.0.
     */
    private float getTileSize() {
        if (tilemap != null) {
            return tilemap.getTileSize();
        }
        return 1f;
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
        return move(direction.dx, direction.dy);
    }

    /**
     * Attempts to move by the given tile offset.
     * Does nothing if already moving or if blocked.
     *
     * @param dx X offset (-1, 0, or 1)
     * @param dy Y offset (-1, 0, or 1)
     * @return true if movement started, false if blocked or already moving
     */
    public boolean move(int dx, int dy) {
        if (isMoving) {
            return false;
        }

        if (dx == 0 && dy == 0) {
            return false;
        }

        // Update facing direction
        updateFacingDirection(dx, dy);

        int targetX = gridX + dx;
        int targetY = gridY + dy;

        // Check collision
        MoveResult result = canMoveTo(targetX, targetY);

        if (result == MoveResult.BLOCKED) {
            return false;
        }

        // Start movement
        startMovement(targetX, targetY, result == MoveResult.JUMP);
        return true;
    }

    /**
     * Result of movement collision check.
     */
    private enum MoveResult {
        ALLOWED,
        BLOCKED,
        JUMP
    }

    /**
     * Checks if movement to target tile is allowed.
     *
     * @param targetX Target tile X
     * @param targetY Target tile Y
     * @return Movement result
     */
    private MoveResult canMoveTo(int targetX, int targetY) {
        if (tilemap == null) {
            return MoveResult.ALLOWED; // No tilemap = no collision
        }

        // Check current tile for ledge (can we jump FROM here?)
        TilemapRenderer.Tile currentTile = tilemap.get(gridX, gridY);
        if (currentTile != null && currentTile.isLedge()) {
            if (currentTile.canJumpInDirection(facingDirection.toLedgeDirection())) {
                return MoveResult.JUMP;
            }
        }

        // Check target tile
        TilemapRenderer.Tile targetTile = tilemap.get(targetX, targetY);
        if (targetTile != null && targetTile.blocksMovement()) {
            return MoveResult.BLOCKED;
        }

        return MoveResult.ALLOWED;
    }

    /**
     * Starts movement to target tile.
     */
    private void startMovement(int targetX, int targetY, boolean jump) {
        if (gameObject == null) return;

        // Update grid position immediately (for collision purposes)
        gridX = targetX;
        gridY = targetY;

        // Store start position
        startPos.set(gameObject.getTransform().getPosition());

        // Calculate target world position
        float tileSize = getTileSize();
        targetPos.set(
                targetX * tileSize + tileSize * 0.5f,
                targetY * tileSize + tileSize * 0.5f,
                startPos.z
        );

        isMoving = true;
        isJumping = jump;
        moveProgress = 0f;
    }

    /**
     * Updates facing direction based on movement delta.
     */
    private void updateFacingDirection(int dx, int dy) {
        if (dy > 0) {
            facingDirection = Direction.UP;
        } else if (dy < 0) {
            facingDirection = Direction.DOWN;
        } else if (dx < 0) {
            facingDirection = Direction.LEFT;
        } else if (dx > 0) {
            facingDirection = Direction.RIGHT;
        }
    }

    // ========================================================================
    // UPDATE
    // ========================================================================

    @Override
    public void update(float deltaTime) {
        if (!isMoving || gameObject == null) {
            return;
        }

        // Calculate speed (slower for jumps)
        float speed = moveSpeed;
        if (isJumping) {
            speed *= jumpSpeedMultiplier;
        }

        // Update progress
        moveProgress += deltaTime * speed;

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
        if (isJumping) {
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
        isJumping = false;
        snapToGrid();
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
        return canMoveTo(targetX, targetY) == MoveResult.BLOCKED;
    }

    /**
     * Forces an immediate stop and snap to current grid position.
     * Use sparingly - can cause visual glitches.
     */
    public void forceStop() {
        isMoving = false;
        isJumping = false;
        moveProgress = 0f;
        snapToGrid();
    }

    @Override
    public String toString() {
        return String.format("GridMovement[grid=(%d,%d), moving=%b, jumping=%b, facing=%s]",
                gridX, gridY, isMoving, isJumping, facingDirection);
    }
}