package com.pocket.rpg.input;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

public enum InputAction {

    Fire(KeyCode.SPACE);

    @Getter
    private final List<KeyCode> binding;

    InputAction(KeyCode... glfwKey) {
        binding = Arrays.asList(glfwKey);
    }
}
