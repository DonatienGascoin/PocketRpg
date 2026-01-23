package com.pocket.rpg.components;

import org.joml.Vector3f;

@ComponentMeta(category = "Rendering")
public class PlayerCameraFollow extends Component {
    private final Vector3f offset = new Vector3f(0, -5, 0);


    @Override
    public void lateUpdate(float deltaTime) {
        getGameObject().getScene().getCamera().setPosition(getTransform().getPosition().add(offset));
    }
}
