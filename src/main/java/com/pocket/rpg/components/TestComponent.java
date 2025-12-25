package com.pocket.rpg.components;

import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;

public class TestComponent extends Component{

    private String msg1 = "Message 1";
    private String msg2 = "Message 2";

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
