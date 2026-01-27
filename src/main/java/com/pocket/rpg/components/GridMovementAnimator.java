package com.pocket.rpg.components;

import com.pocket.rpg.collision.Direction;

/**
 * Middleware component that bridges GridMovement to AnimatorComponent.
 * <p>
 * Automatically sets animator parameters based on GridMovement state:
 * - "isMoving" (bool): true while the entity is moving between tiles
 * - "isSliding" (bool): true while sliding on ice
 * - direction: the facing direction (used for directional animations)
 * <p>
 * Optionally supports jump triggers and custom parameter names.
 *
 * <h2>Usage</h2>
 * <pre>
 * // Requires both components on the same GameObject
 * GridMovement movement = go.addComponent(new GridMovement());
 * AnimatorComponent animator = go.addComponent(new AnimatorComponent(controller));
 * GridMovementAnimator bridge = go.addComponent(new GridMovementAnimator());
 *
 * // The animator controller should have these parameters:
 * // - "isMoving" (bool) for walk/idle transitions
 * // - Optional: direction parameter for directional states
 * </pre>
 */
@ComponentMeta(category = "Animation")
public class GridMovementAnimator extends Component {

    // ========================================================================
    // CONFIGURATION
    // ========================================================================

    /**
     * Parameter name for moving state (default: "isMoving").
     */
    private String movingParam = "isMoving";

    /**
     * Parameter name for sliding state (default: "isSliding").
     * Set to null or empty to disable.
     */
    private String slidingParam = "isSliding";

    /**
     * Trigger name for jump start (default: null = disabled).
     * Set to a trigger name to fire when jumping starts.
     */
    private String jumpTrigger = null;

    /**
     * Whether to sync direction from GridMovement to animator.
     */
    private boolean syncDirection = true;

    // ========================================================================
    // COMPONENT REFERENCES
    // ========================================================================

    @ComponentRef
    private GridMovement gridMovement;

    @ComponentRef
    private AnimatorComponent animator;

    // ========================================================================
    // STATE TRACKING
    // ========================================================================

    @HideInInspector
    private boolean wasMoving = false;

    @HideInInspector
    private boolean wasJumping = false;

    @HideInInspector
    private Direction lastDirection = null;

    // ========================================================================
    // CONSTRUCTORS
    // ========================================================================

    public GridMovementAnimator() {
    }

    /**
     * Creates a GridMovementAnimator with custom parameter names.
     *
     * @param movingParam Name of the "is moving" bool parameter
     */
    public GridMovementAnimator(String movingParam) {
        this.movingParam = movingParam;
    }

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    public void update(float deltaTime) {
        if (gridMovement == null || animator == null) {
            return;
        }

        // Sync moving state
        boolean isMoving = gridMovement.isMoving();
        if (isMoving != wasMoving) {
            animator.setBool(movingParam, isMoving);
            wasMoving = isMoving;
        }

        // Sync sliding state (if configured)
        if (slidingParam != null && !slidingParam.isEmpty()) {
            animator.setBool(slidingParam, gridMovement.isSliding());
        }

        // Fire jump trigger on jump start
        if (jumpTrigger != null && !jumpTrigger.isEmpty()) {
            boolean isJumping = gridMovement.isJumping();
            if (isJumping && !wasJumping) {
                animator.setTrigger(jumpTrigger);
            }
            wasJumping = isJumping;
        }

        // Sync direction
        if (syncDirection) {
            Direction direction = gridMovement.getFacingDirection();
            if (direction != lastDirection) {
                animator.setDirection(direction);
                lastDirection = direction;
            }
        }
    }

    // ========================================================================
    // CONFIGURATION API
    // ========================================================================

    /**
     * Sets the parameter name for moving state.
     */
    public void setMovingParam(String name) {
        this.movingParam = name;
    }

    /**
     * Gets the parameter name for moving state.
     */
    public String getMovingParam() {
        return movingParam;
    }

    /**
     * Sets the parameter name for sliding state.
     * Set to null or empty to disable.
     */
    public void setSlidingParam(String name) {
        this.slidingParam = name;
    }

    /**
     * Gets the parameter name for sliding state.
     */
    public String getSlidingParam() {
        return slidingParam;
    }

    /**
     * Sets the trigger name for jump start.
     * Set to null or empty to disable.
     */
    public void setJumpTrigger(String name) {
        this.jumpTrigger = name;
    }

    /**
     * Gets the trigger name for jump start.
     */
    public String getJumpTrigger() {
        return jumpTrigger;
    }

    /**
     * Sets whether to sync direction from GridMovement to animator.
     */
    public void setSyncDirection(boolean sync) {
        this.syncDirection = sync;
    }

    /**
     * Gets whether direction syncing is enabled.
     */
    public boolean isSyncDirection() {
        return syncDirection;
    }

    // ========================================================================
    // MANUAL CONTROL
    // ========================================================================

    /**
     * Forces a refresh of all animator parameters from GridMovement.
     * Useful after changing animator controller.
     */
    public void refresh() {
        if (gridMovement == null || animator == null) return;

        animator.setBool(movingParam, gridMovement.isMoving());
        wasMoving = gridMovement.isMoving();

        if (slidingParam != null && !slidingParam.isEmpty()) {
            animator.setBool(slidingParam, gridMovement.isSliding());
        }

        if (syncDirection) {
            animator.setDirection(gridMovement.getFacingDirection());
            lastDirection = gridMovement.getFacingDirection();
        }

        wasJumping = gridMovement.isJumping();
    }
}
