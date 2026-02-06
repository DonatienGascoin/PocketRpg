package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.ComponentReference.Source;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
import lombok.Getter;
import lombok.Setter;

/**
 * Handles player input and translates it to GridMovement commands.
 * <p>
 * Reads keyboard input and calls GridMovement.move() in the appropriate direction.
 * Also handles debug output for collision testing.
 */
@ComponentMeta(category = "Player")
public class PlayerMovement extends Component {

    @ComponentReference(source = Source.SELF)
    private GridMovement movement;

    /**
     * -- SETTER --
     *  Enables or disables debug output.
     */
    @Setter
    @Getter
    private boolean debugOutput = true;

    public PlayerMovement() {

    }

    @Override
    protected void onStart() {
        // Grid position is now derived from the transform by GridMovement.onStart()
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
        }
    }
}