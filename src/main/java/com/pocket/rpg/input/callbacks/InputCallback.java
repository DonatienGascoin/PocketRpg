package com.pocket.rpg.input.callbacks;

public interface InputCallback {

    void mousePosCallback(double xPos, double yPos);

    void mouseButtonCallback(int button, int action, int mods);

    void mouseScrollCallback(double xOffset, double yOffset);

    void keyCallback(int key, int scanCode, int action, int mods);

    void windowResizeCallback(int width, int height);
}
