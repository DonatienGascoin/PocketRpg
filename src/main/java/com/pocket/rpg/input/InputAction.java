package com.pocket.rpg.input;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;

public enum InputAction {

    Move_Up(GLFW_KEY_W),
    Move_Down(GLFW_KEY_S),
    Move_Left(GLFW_KEY_A),
    Move_Right(GLFW_KEY_D),
    Fire(GLFW_KEY_SPACE);

    @Getter
    private final List<Integer> binding;

    InputAction(Integer... glfwKey) {
        binding = Arrays.asList(glfwKey);
    }
}
