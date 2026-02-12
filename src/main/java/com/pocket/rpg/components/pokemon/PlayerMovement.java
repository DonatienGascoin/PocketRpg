package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.ComponentReference.Source;
import com.pocket.rpg.components.player.PlayerInput;
import lombok.Getter;
import lombok.Setter;

/**
 * Handles player input and translates it to GridMovement commands.
 * <p>
 * Reads directional input from {@link PlayerInput} and calls GridMovement.move()
 * in the appropriate direction. Only processes input in OVERWORLD mode.
 */
@ComponentMeta(category = "Player")
public class PlayerMovement extends Component {

    @ComponentReference(source = Source.SELF)
    private GridMovement movement;

    @ComponentReference(source = Source.SELF)
    private PlayerInput playerInput;

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
        if (movement == null || playerInput == null) return;

        // Only process movement in OVERWORLD mode
        if (!playerInput.isOverworld()) return;

        // Don't accept input while moving (grid-based movement)
        if (movement.isMoving()) {
            return;
        }

        Direction direction = playerInput.getMovementDirection();
        if (direction != null) {
            movement.move(direction);
        }
    }
}