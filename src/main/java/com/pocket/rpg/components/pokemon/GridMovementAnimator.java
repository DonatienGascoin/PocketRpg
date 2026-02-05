package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.ComponentRef;
import com.pocket.rpg.components.HideInInspector;
import com.pocket.rpg.components.animations.AnimatorComponent;
import lombok.Getter;
import lombok.Setter;

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

    @Getter
    @Setter
    private String movingParam = "isMoving";

    @Getter
    @Setter
    private String slidingParam = "isSliding";

    @Getter
    @Setter
    private String jumpTrigger = null;

    @Getter
    @Setter
    private String directionParam = "direction";

    @Getter
    @Setter
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
        if (syncDirection && directionParam != null && !directionParam.isEmpty()) {
            Direction direction = gridMovement.getFacingDirection();
            if (direction != lastDirection) {
                animator.setDirection(directionParam, direction);
                lastDirection = direction;
            }
        }
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

        if (syncDirection && directionParam != null && !directionParam.isEmpty()) {
            animator.setDirection(directionParam, gridMovement.getFacingDirection());
            lastDirection = gridMovement.getFacingDirection();
        }

        wasJumping = gridMovement.isJumping();
    }
}
