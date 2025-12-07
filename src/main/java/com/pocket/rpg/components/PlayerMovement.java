package com.pocket.rpg.components;

import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.transitions.SceneTransition;
import org.joml.Vector2f;
import org.joml.Vector3f;

public class PlayerMovement extends Component {

    private final float speed = 15f;

    @Override
    public void update(float deltaTime) {
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
