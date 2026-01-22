package com.pocket.rpg.editor.ui.viewport;

import com.pocket.rpg.editor.camera.EditorCamera;
import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import imgui.ImGui;
import imgui.ImVec2;
import lombok.Getter;
import lombok.Setter;

/**
 * Handles framebuffer display and viewport sizing.
 */
public class ViewportRenderer {

    @Getter
    private EditorFramebuffer framebuffer;

    @Getter
    @Setter
    private boolean contentVisible = false;

    private final EditorCamera camera;

    public ViewportRenderer(EditorCamera camera) {
        this.camera = camera;
    }

    public void init(int initialWidth, int initialHeight) {
        framebuffer = new EditorFramebuffer(initialWidth, initialHeight);
        framebuffer.init();
    }

    /**
     * Renders the framebuffer texture and handles resizing.
     *
     * @return viewport bounds [x, y, width, height]
     */
    public float[] render(float viewportX, float viewportY, float viewportWidth, float viewportHeight) {
        // Resize framebuffer and sync camera if needed
        if (framebuffer != null && viewportWidth > 0 && viewportHeight > 0) {
            int newWidth = (int) viewportWidth;
            int newHeight = (int) viewportHeight;

            boolean framebufferChanged = newWidth != framebuffer.getWidth() || newHeight != framebuffer.getHeight();
            boolean cameraChanged = camera.getViewportWidth() != newWidth || camera.getViewportHeight() != newHeight;

            // Always update both together to keep them in sync
            if (framebufferChanged || cameraChanged) {
                framebuffer.resize(newWidth, newHeight);
                camera.setViewportSize(newWidth, newHeight);
            }
        }

        // Display framebuffer texture
        if (framebuffer != null && framebuffer.isInitialized()) {
            ImGui.image(
                    framebuffer.getTextureId(),
                    viewportWidth,
                    viewportHeight,
                    0, 1,
                    1, 0
            );
        }

        return new float[]{viewportX, viewportY, viewportWidth, viewportHeight};
    }

    /**
     * Forces the viewport to recalculate on the next frame.
     * Used when the window moves between monitors.
     */
    public void invalidate() {
        // Reset camera viewport to force recalculation
        camera.setViewportSize(0, 0);
        camera.setViewportSize(framebuffer.getWidth(), framebuffer.getHeight());
    }

    public void destroy() {
        if (framebuffer != null) {
            framebuffer.destroy();
            framebuffer = null;
        }
    }
}
