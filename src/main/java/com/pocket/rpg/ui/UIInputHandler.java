package com.pocket.rpg.ui;

import com.pocket.rpg.components.ui.UIButton;
import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UIScrollView;
import com.pocket.rpg.components.ui.UIScrollbar;
import com.pocket.rpg.components.ui.UITransform;
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
 * <h3>Usage:</h3>
 * <pre>{@code
 * // Create once during initialization
 * UIInputHandler uiInputHandler = new UIInputHandler(gameConfig);
 * gameEngine.setUIInputHandler(uiInputHandler);
 *
 * // In game loop, before game input processing:
 * float gameMouseX = convertToGameX(Input.getMousePosition().x);
 * float gameMouseY = convertToGameY(Input.getMousePosition().y);
 * gameEngine.updateUIInput(gameMouseX, gameMouseY);
 *
 * // In game code - no manual consumption check needed!
 * if (Input.getMouseButtonDown(KeyCode.MOUSE_BUTTON_LEFT)) {
 *     // Automatically false if UI consumed the click
 *     handleGameClick();
 * }
 * }</pre>
 *
 * <h3>How it works:</h3>
 * <ol>
 *   <li>UIInputHandler uses raw mouse methods to see actual input state</li>
 *   <li>If UI element handles the input, setMouseConsumed(true) is called</li>
 *   <li>Normal mouse methods in Input class return false when consumed</li>
 * </ol>
 *
 * Hit testing is done in reverse render order (top elements first).
 */
public class UIInputHandler {

    private final GameConfig config;

    // All buttons in the scene (refreshed each frame from canvases)
    private final List<UIButton> buttons = new ArrayList<>();

    // All scroll views in the scene (refreshed each frame)
    private final List<UIScrollView> scrollViews = new ArrayList<>();

    // All scrollbars in the scene (refreshed each frame)
    private final List<UIScrollbar> scrollbars = new ArrayList<>();

    // Currently hovered button (null if none)
    private UIButton hoveredButton = null;

    // Button that was pressed (for tracking release)
    private UIButton pressedButton = null;

    // Active scrollbar being dragged
    private UIScrollbar activeScrollbar = null;

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
        // Reset consumed state at start of frame
        Input.setMouseConsumed(false);

        // Collect all interactive UI elements from canvases (in render order)
        collectInteractables(canvases);

        // Handle scrollbar drag (takes priority — continues even if mouse leaves scrollbar)
        if (activeScrollbar != null) {
            if (Input.getMouseButtonRaw(KeyCode.MOUSE_BUTTON_LEFT)) {
                activeScrollbar.updateDrag(mouseY);
                Input.setMouseConsumed(true);
            }
            if (Input.getMouseButtonUpRaw(KeyCode.MOUSE_BUTTON_LEFT)) {
                activeScrollbar.endDrag();
                activeScrollbar = null;
                Input.setMouseConsumed(true);
            }
            // During drag, skip other input processing
            return;
        }

        // Handle mouse scroll over scroll views
        float scrollDelta = Input.getMouseScrollDelta();
        if (scrollDelta != 0) {
            for (int i = scrollViews.size() - 1; i >= 0; i--) {
                UIScrollView sv = scrollViews.get(i);
                if (!sv.canScroll()) continue;

                GameObject viewport = sv.getViewport();
                if (viewport == null) continue;

                UITransform vt = viewport.getComponent(UITransform.class);
                if (vt == null) continue;

                var pos = vt.getScreenPosition();
                float vw = vt.getEffectiveWidth();
                float vh = vt.getEffectiveHeight();

                if (mouseX >= pos.x && mouseX < pos.x + vw
                        && mouseY >= pos.y && mouseY < pos.y + vh) {
                    sv.scroll(-scrollDelta * sv.getScrollSensitivity());
                    Input.setMouseConsumed(true);
                    break;
                }
            }
        }

        // Handle scrollbar click/drag
        if (Input.getMouseButtonDownRaw(KeyCode.MOUSE_BUTTON_LEFT)) {
            for (int i = scrollbars.size() - 1; i >= 0; i--) {
                UIScrollbar sb = scrollbars.get(i);
                UIScrollView sv = sb.getScrollView();
                if (sv == null || !sv.isScrollbarVisible()) continue;

                if (sb.isPointOverHandle(mouseX, mouseY)) {
                    sb.beginDrag(mouseY);
                    activeScrollbar = sb;
                    Input.setMouseConsumed(true);
                    break;
                } else if (sb.isPointOverTrack(mouseX, mouseY)) {
                    sb.jumpToTrackPosition(mouseY);
                    Input.setMouseConsumed(true);
                    break;
                }
            }
        }

        // Handle buttons
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

        // Handle mouse press - use RAW method to see actual input
        if (Input.getMouseButtonDownRaw(KeyCode.MOUSE_BUTTON_LEFT)) {
            if (hoveredButton != null) {
                hoveredButton.setPressedInternal(true);
                pressedButton = hoveredButton;
                Input.setMouseConsumed(true);
            }
        }

        // Handle mouse release - use RAW method to see actual input
        if (Input.getMouseButtonUpRaw(KeyCode.MOUSE_BUTTON_LEFT)) {
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
     * Collects all interactive UI components from canvas subtrees.
     * Elements are added in render order (earlier = bottom, later = top).
     */
    private void collectInteractables(List<UICanvas> canvases) {
        buttons.clear();
        scrollViews.clear();
        scrollbars.clear();

        for (UICanvas canvas : canvases) {
            if (!canvas.isEnabled()) continue;
            collectInteractablesRecursive(canvas.getGameObject());
        }
    }

    private void collectInteractablesRecursive(GameObject go) {
        if (!go.isEnabled()) return;

        // Check for UIButton
        UIButton button = go.getComponent(UIButton.class);
        if (button != null && button.isEnabled()) {
            button.setConfig(config);
            buttons.add(button);
        }

        // Check for UIScrollView
        UIScrollView scrollView = go.getComponent(UIScrollView.class);
        if (scrollView != null && scrollView.isEnabled()) {
            scrollViews.add(scrollView);
        }

        // Check for UIScrollbar
        UIScrollbar scrollbar = go.getComponent(UIScrollbar.class);
        if (scrollbar != null && scrollbar.isEnabled()) {
            scrollbars.add(scrollbar);
        }

        // Process children
        for (GameObject child : go.getChildren()) {
            collectInteractablesRecursive(child);
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
        scrollViews.clear();
        scrollbars.clear();
        activeScrollbar = null;
    }
}