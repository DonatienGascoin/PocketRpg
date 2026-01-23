package com.pocket.rpg.components;

import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;
import com.pocket.rpg.rendering.resources.Sprite;

import java.util.List;

public class TestComponent extends Component {

    private String msg1 = "Message 1";
    private String msg2 = "Message 2";

    private List<String> items;
    private Sprite sprite;
    private List<Sprite> sprites;

    @Override
    public void update(float deltaTime) {
        if (Input.getKeyDown(KeyCode.SPACE)) {
            System.out.println(msg1);
        }
        if (Input.getKeyDown(KeyCode.ENTER)) {
            System.out.println(msg2);
        }


    }
}
