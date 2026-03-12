package com.pocket.rpg.rendering.targets;

import com.pocket.rpg.rendering.core.RenderTarget;
import lombok.Getter;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.*;

/**
 * RenderTarget implementation that wraps a {@link Framebuffer}.
 * <p>
 * Used by editor panels (GameViewPanel, PlayModeController) to render
 * via RenderPipeline into a framebuffer for ImGui display.
 *
 * @see RenderTarget
 * @see Framebuffer
 */
@Getter
public class FramebufferTarget implements RenderTarget {

    private final Framebuffer framebuffer;

    public FramebufferTarget(Framebuffer framebuffer) {
        this.framebuffer = framebuffer;
    }

    @Override
    public void bind() {
        framebuffer.bind();
        glViewport(0, 0, framebuffer.getWidth(), framebuffer.getHeight());
    }

    @Override
    public void unbind() {
        framebuffer.unbind();
    }

    @Override
    public void clear(Vector4f color) {
        glClearColor(color.x, color.y, color.z, color.w);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    @Override
    public int getWidth() {
        return framebuffer.getWidth();
    }

    @Override
    public int getHeight() {
        return framebuffer.getHeight();
    }

    /**
     * Gets the texture ID for ImGui rendering.
     */
    public int getTextureId() {
        return framebuffer.getTextureId();
    }

}
