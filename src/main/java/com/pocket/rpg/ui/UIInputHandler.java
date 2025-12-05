package com.pocket.rpg.ui;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.input.Input;
import com.pocket.rpg.input.KeyCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles mouse input for UI elements.
 * Processes hover states, clicks, and sets input consumption flag.
 *
 * Usage:
 * - Create once, call update() each frame before game input processing
 * - After update(), check Input.isMouseConsumed() in game code
 *
 * Hit testing is done in reverse render order (top elements first).
 */
public class UIInputHandler {

    private final GameConfig config;

    // All buttons in the scene (refreshed each frame from canvases)
    private final List<UIButton> buttons = new ArrayList<>();

    // Currently hovered button (null if none)
    private UIButton hoveredButton = null;

    // Button that was pressed (for tracking release)
    private UIButton pressedButton = null;

    public UIInputHandler(GameConfig config) {
        this.config = config;
    }

    /**
     * Updates UI input state. Call once per frame before game input processing.
     *
     * @param canvases List of UI canvases (sorted by sortOrder)
     * @param mouseX Mouse X in game coordinates
     * @param mouseY Mouse Y in game coordinates
     */
    public void update(List<UICanvas> canvases, float mouseX, float mouseY) {
        // Reset consumed state
        Input.setMouseConsumed(false);

        // Collect all buttons from canvases (in render order)
        collectButtons(canvases);

        if (buttons.isEmpty()) {
            clearHoverState();
            return;
        }

        // Find topmost button under mouse (reverse order = top first)
        UIButton newHovered = null;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            UIButton button = buttons.get(i);
            if (!button.isEnabled() || !button.isRaycastTarget()) continue;

            if (button.containsPoint(mouseX, mouseY)) {
                newHovered = button;
                break;
            }
        }

        // Update hover states
        if (newHovered != hoveredButton) {
            // Exit old button
            if (hoveredButton != null) {
                hoveredButton.setHoveredInternal(false);
            }
            // Enter new button
            if (newHovered != null) {
                newHovered.setHoveredInternal(true);
            }
            hoveredButton = newHovered;
        }

        // Handle mouse press
        if (Input.getMouseButtonDown(KeyCode.MOUSE_BUTTON_LEFT)) {
            if (hoveredButton != null) {
                hoveredButton.setPressedInternal(true);
                pressedButton = hoveredButton;
                Input.setMouseConsumed(true);
            }
        }

        // Handle mouse release
        if (Input.getMouseButtonUp(KeyCode.MOUSE_BUTTON_LEFT)) {
            if (pressedButton != null) {
                pressedButton.setPressedInternal(false);

                // Click occurs if released over same button that was pressed
                if (pressedButton == hoveredButton && hoveredButton != null) {
                    hoveredButton.triggerClick();
                }

                pressedButton = null;
                Input.setMouseConsumed(true);
            }
        }

        // Consume input if hovering over any UI element
        if (hoveredButton != null) {
            Input.setMouseConsumed(true);
        }
    }

    /**
     * Collects all UIButton components from canvas subtrees.
     * Buttons are added in render order (earlier = bottom, later = top).
     */
    private void collectButtons(List<UICanvas> canvases) {
        buttons.clear();

        for (UICanvas canvas : canvases) {
            if (!canvas.isEnabled()) continue;
            collectButtonsRecursive(canvas.getGameObject());
        }
    }

    private void collectButtonsRecursive(GameObject go) {
        if (!go.isEnabled()) return;

        // Check for UIButton
        UIButton button = go.getComponent(UIButton.class);
        if (button != null && button.isEnabled()) {
            // Ensure button has config reference
            button.setConfig(config);
            buttons.add(button);
        }

        // Process children
        for (GameObject child : go.getChildren()) {
            collectButtonsRecursive(child);
        }
    }

    private void clearHoverState() {
        if (hoveredButton != null) {
            hoveredButton.setHoveredInternal(false);
            hoveredButton = null;
        }
        pressedButton = null;
    }

    /**
     * Clears all state. Call when changing scenes.
     */
    public void reset() {
        clearHoverState();
        buttons.clear();
    }
}