package com.pocket.rpg.testing;

import com.pocket.rpg.input.InputAction;
import com.pocket.rpg.input.InputAxis;
import com.pocket.rpg.input.InputContext;
import com.pocket.rpg.input.KeyCode;
import org.joml.Vector2f;
import java.util.*;

/**
 * Mock implementation of InputContext for testing.
 * Allows you to programmatically set input states.
 */
public class MockInputTesting implements InputContext {

    private final Set<KeyCode> keysDown = EnumSet.noneOf(KeyCode.class);
    private final Set<KeyCode> keysPressed = EnumSet.noneOf(KeyCode.class);
    private final Set<KeyCode> keysReleased = EnumSet.noneOf(KeyCode.class);

    private final Map<String, Float> axisValues = new HashMap<>();

    private Vector2f mousePosition = new Vector2f(0, 0);
    private Vector2f mouseDelta = new Vector2f(0, 0);
    private float scrollDelta = 0f;

    // ========================================
    // Testing API - Set input states
    // ========================================

    public void pressKey(KeyCode key) {
        keysPressed.add(key);
        keysDown.add(key);
    }

    public void releaseKey(KeyCode key) {
        keysReleased.add(key);
        keysDown.remove(key);
    }

    public void setMousePos(float x, float y) {
        this.mousePosition.set(x, y);
    }

    public void setMouseDelta(float dx, float dy) {
        this.mouseDelta.set(dx, dy);
    }

    public void setScroll(float delta) {
        this.scrollDelta = delta;
    }

    // ========================================
    // InputContext Implementation
    // ========================================

    @Override
    public void update(float deltaTime) {
        // Mock doesn't need update logic
    }

    @Override
    public void endFrame() {
        keysPressed.clear();
        keysReleased.clear();
        mouseDelta.set(0, 0);
        scrollDelta = 0f;
    }

    @Override
    public void clear() {
        keysDown.clear();
        keysPressed.clear();
        keysReleased.clear();
        axisValues.clear();
        mousePosition.set(0, 0);
        mouseDelta.set(0, 0);
        scrollDelta = 0f;
    }

    @Override
    public void destroy() {
        clear();
    }

    @Override
    public boolean getKey(KeyCode key) {
        return keysDown.contains(key);
    }

    @Override
    public boolean getKeyDown(KeyCode key) {
        return keysPressed.contains(key);
    }

    @Override
    public boolean getKeyUp(KeyCode key) {
        return keysReleased.contains(key);
    }

    @Override
    public boolean anyKey() {
        return !keysDown.isEmpty();
    }

    @Override
    public boolean anyKeyDown() {
        return !keysPressed.isEmpty();
    }

    @Override
    public boolean isActionHeld(InputAction action) {
        // Mock: just check if any key is down
        return !keysDown.isEmpty();
    }

    @Override
    public boolean isActionPressed(InputAction action) {
        return !keysPressed.isEmpty();
    }

    @Override
    public boolean isActionReleased(InputAction action) {
        return !keysReleased.isEmpty();
    }

    @Override
    public Vector2f getMousePosition() {
        return new Vector2f(mousePosition);
    }

    @Override
    public Vector2f getMouseDelta() {
        return new Vector2f(mouseDelta);
    }

    @Override
    public float getMouseScrollDelta() {
        return scrollDelta;
    }

    @Override
    public boolean getMouseButton(KeyCode button) {
        return keysDown.contains(button);
    }

    @Override
    public boolean getMouseButtonDown(KeyCode button) {
        return keysPressed.contains(button);
    }

    @Override
    public boolean getMouseButtonUp(KeyCode button) {
        return keysReleased.contains(button);
    }

    @Override
    public boolean isMouseDragging(KeyCode button) {
        return keysDown.contains(button) && (mouseDelta.x != 0 || mouseDelta.y != 0);
    }

    @Override
    public float getAxis(InputAxis axis) {
        return getAxis(axis.name());
    }

    @Override
    public float getAxis(String axisName) {
        return axisValues.getOrDefault(axisName, 0f);
    }

    @Override
    public float getAxisRaw(InputAxis axis) {
        return getAxis(axis);
    }

    @Override
    public float getAxisRaw(String axisName) {
        return getAxis(axisName);
    }

    @Override
    public void setAxisValue(String axisName, float value) {
        axisValues.put(axisName, value);
    }
}