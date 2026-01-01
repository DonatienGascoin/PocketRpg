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
        // Resize framebuffer if needed
        if (framebuffer != null && viewportWidth > 0 && viewportHeight > 0) {
            int newWidth = (int) viewportWidth;
            int newHeight = (int) viewportHeight;

            if (newWidth != framebuffer.getWidth() || newHeight != framebuffer.getHeight()) {
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

    public void destroy() {
        if (framebuffer != null) {
            framebuffer.destroy();
            framebuffer = null;
        }
    }
}
