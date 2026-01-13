package com.pocket.rpg.components;

import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.scenes.SceneManager;
import com.pocket.rpg.scenes.transitions.SceneTransition;

public class SceneTransitionTest extends Component {

    private String targetSceneName;

    @Override
    public void update(float deltaTime) {
        if (Input.getKeyDown(KeyCode.SPACE)) {
            SceneTransition.loadScene(targetSceneName);
        }
    }
}
