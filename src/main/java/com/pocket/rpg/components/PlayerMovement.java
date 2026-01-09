package com.pocket.rpg.components;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.collision.MovementModifier;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
import lombok.Getter;

/**
 * Handles player input and translates it to GridMovement commands.
 * <p>
 * Reads keyboard input and calls GridMovement.move() in the appropriate direction.
 * Also handles debug output for collision testing.
 */
public class PlayerMovement extends Component {

    @ComponentRef
    private GridMovement movement;

    @Getter
    private boolean debugOutput = true;

    public PlayerMovement() {

    }

    @Override
    protected void onStart() {
        movement.setGridPosition((int)getTransform().getPosition().x, (int)getTransform().getPosition().y);
    }

    @Override
    public void update(float deltaTime) {
        if (movement == null) return;

        // Don't accept input while moving (grid-based movement)
        if (movement.isMoving()) {
            return;
        }

        // Check directional input
        Direction direction = null;

        if (Input.getKey(KeyCode.W) || Input.getKey(KeyCode.UP)) {
            direction = Direction.UP;
        } else if (Input.getKey(KeyCode.S) || Input.getKey(KeyCode.DOWN)) {
            direction = Direction.DOWN;
        } else if (Input.getKey(KeyCode.A) || Input.getKey(KeyCode.LEFT)) {
            direction = Direction.LEFT;
        } else if (Input.getKey(KeyCode.D) || Input.getKey(KeyCode.RIGHT)) {
            direction = Direction.RIGHT;
        }

        if (direction != null) {
            boolean moved = movement.move(direction);

            if (debugOutput) {
                if (moved) {
                    MovementModifier modifier = movement.getCurrentModifier();
                    String modStr = modifier != MovementModifier.NORMAL ? " [" + modifier + "]" : "";
                    System.out.printf("[PlayerMovement] Moving %s to (%d, %d)%s%n",
                            direction, movement.getGridX(), movement.getGridY(), modStr);
                } else {
                    System.out.printf("[PlayerMovement] Blocked %s from (%d, %d)%n",
                            direction, movement.getGridX(), movement.getGridY());
                }
            }
        }
    }

    /**
     * Enables or disables debug output.
     */
    public void setDebugOutput(boolean enabled) {
        this.debugOutput = enabled;
    }
}