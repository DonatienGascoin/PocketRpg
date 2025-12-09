package com.pocket.rpg.components;

import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.transitions.SceneTransition;
import org.joml.Vector2f;
import org.joml.Vector3f;

public class PlayerMovement extends Component {

    private final float speed = 15f;
    private final GridMovement movement;

    public PlayerMovement(GridMovement movement) {
        this.movement = movement;
    }

    @Override
    public void update(float deltaTime) {
        gridMovement(deltaTime);
//        freeMovement(deltaTime);
    }

    private void gridMovement(float deltaTime) {
        if (!movement.isMoving()) {
            if (Input.getKey(KeyCode.W)) movement.move(GridMovement.Direction.UP);
            if (Input.getKey(KeyCode.S)) movement.move(GridMovement.Direction.DOWN);
            if (Input.getKey(KeyCode.A)) movement.move(GridMovement.Direction.LEFT);
            if (Input.getKey(KeyCode.D)) movement.move(GridMovement.Direction.RIGHT);
        }
    }

    private void freeMovement(float deltaTime) {
        var hor = Input.getAxisRaw("Horizontal");
        var ver = Input.getAxisRaw("Vertical");

        if (hor != 0 || ver != 0) {
//            getTransform().setPosition(getTransform().getPosition().add(speed * hor * deltaTime, speed * ver * deltaTime, 0f));
            getTransform().setPosition(getTransform().getPosition().add(new Vector3f(hor, ver, 0).normalize().mul(speed * deltaTime)));
        }

        if (Input.getKeyDown(KeyCode.SPACE)) {
            if (getGameObject().getScene().getName().equals("Demo")) {
                SceneTransition.loadScene("Demo2");
            } else {
                SceneTransition.loadScene("Demo");
            }
        }
    }
}
