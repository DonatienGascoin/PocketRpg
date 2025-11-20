package com.pocket.rpg.utils;

public interface ICallback {

    void mousePosCallback(long window, double xPos, double yPos);

    void mouseButtonCallback(long window, int button, int action, int mods);

    void mouseScrollCallback(long window, double xOffset, double yOffset);

    void keyCallback(long window, int key, int scanCode, int action, int mods);

    void windowResizeCallback(long window, int width, int height);
}
