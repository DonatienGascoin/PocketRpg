package com.pocket.rpg.components;

import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.transitions.SceneTransition;

public class PlayerMovement extends Component {

    private final float speed = 50f;

    @Override
    public void update(float deltaTime) {
        var hor = Input.getAxisRaw("Horizontal");
        var ver = Input.getAxisRaw("Vertical");

        getTransform().setPosition(getTransform().getPosition().add(speed * hor * deltaTime, speed * ver * deltaTime, 0f));

        if (Input.getKeyDown(KeyCode.SPACE)) {
            if (getGameObject().getScene().getName().equals("Demo")) {
                SceneTransition.loadScene("Demo2");
            } else {
                SceneTransition.loadScene("Demo");
            }
        }
    }
}
