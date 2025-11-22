package com.pocket.rpg.utils;

import static org.lwjgl.glfw.GLFW.glfwGetTime;

public class Time {

    private static double fps;
    private static float deltaTime;

    private static double previousTime;
    private static int frameCount = 0;

    private static float frameTimeMs;
    private static float avgFrameTimeMs;
    private static float totalFrameTime = 0f;


    private static float beginTime;

    /**
     * @return time since the application started
     */
    public static float getTime() {
        return (float) glfwGetTime();
    }

    public static double fps() {
        return fps;
    }

    public static float deltaTime() {
        return deltaTime;
    }

    public static void init() {
        previousTime = getTime();
        beginTime = getTime();
        deltaTime = -1.0f;
    }

    public static void update() {
        updateFps();
        updateTime();
    }

    public static float frameTimeMs() {
        return frameTimeMs;
    }

    public static float avgFrameTimeMs() {
        return avgFrameTimeMs;
    }

    private static void updateTime() {
        float endTime = getTime();
        deltaTime = endTime - beginTime;
        frameTimeMs = deltaTime * 1000f;
        beginTime = endTime;
    }

    private static void updateFps() {
        double currentTime = getTime();
        frameCount++;
        totalFrameTime += frameTimeMs;
        // If a second has passed.
        if (currentTime - previousTime >= 1.0) {
            fps = frameCount;
            avgFrameTimeMs = totalFrameTime / frameCount;

            frameCount = 0;
            totalFrameTime = 0f;
            previousTime = currentTime;
        }
    }
}
