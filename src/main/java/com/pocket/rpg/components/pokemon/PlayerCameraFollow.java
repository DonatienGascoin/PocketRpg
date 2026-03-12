package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.scenes.SceneManager;
import org.joml.Vector3f;

@ComponentMeta(category = "Rendering")
public class PlayerCameraFollow extends Component {
    private final Vector3f offset = new Vector3f(0, -5, 0);


    @Override
    public void lateUpdate(float deltaTime) {
        SceneManager.getActiveScene().getCamera().setPosition(getTransform().getPosition().add(offset));
    }
}
