package com.pocket.rpg.input.callbacks;

public interface InputCallback {

    void mousePosCallback(double xPos, double yPos);

    void mouseButtonCallback(int button);

    void mouseScrollCallback(double xOffset, double yOffset);

    void keyCallback(int key);

    void windowResizeCallback(int width, int height);
}
