package com.pocket.rpg.components;

import com.pocket.rpg.input.InputManager;
import com.pocket.rpg.input.KeyCode;
import org.joml.Vector3f;

public class MoveComponent extends Component {

    @Override
    public void update(float deltaTime) {
        if (InputManager.getKeyDown(KeyCode.SPACE)) {
            System.out.println("Fire!");
        }

        var movement = InputManager.getMousePosition();
        if (movement.lengthSquared() > 0) {
            movement.normalize().mul(5f * deltaTime); // Move speed of 5 units per second
            gameObject.getTransform().getPosition().add(new Vector3f(movement, 0f));
        }
    }
}
