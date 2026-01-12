package com.pocket.rpg.rendering.targets;

import com.pocket.rpg.core.window.ViewportConfig;
import com.pocket.rpg.rendering.core.RenderTarget;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL33.*;

/**
 * Render target for the default framebuffer (screen).
 * Uses game resolution for dimensions.
 */
public class ScreenTarget implements RenderTarget {

    private final ViewportConfig viewportConfig;

    public ScreenTarget(ViewportConfig viewportConfig) {
        this.viewportConfig = viewportConfig;
    }

    @Override
    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, getWidth(), getHeight());
    }

    @Override
    public void unbind() {
        // No-op for screen - already the default target
    }

    @Override
    public void clear(Vector4f color) {
        glClearColor(color.x, color.y, color.z, color.w);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    @Override
    public int getWidth() {
        return viewportConfig.getGameWidth();
    }

    @Override
    public int getHeight() {
        return viewportConfig.getGameHeight();
    }

    @Override
    public int getTextureId() {
        return 0; // Screen has no texture
    }

    @Override
    public boolean isOffscreen() {
        return false;
    }
}