package com.pocket.rpg.rendering.targets;

import com.pocket.rpg.editor.rendering.EditorFramebuffer;
import com.pocket.rpg.rendering.core.RenderTarget;
import com.pocket.rpg.rendering.pipeline.RenderPipeline;
import lombok.Getter;
import org.joml.Vector4f;

import static org.lwjgl.opengl.GL11.*;

/**
 * RenderTarget implementation that wraps an EditorFramebuffer.
 * <p>
 * Used by editor panels (GameViewPanel, PlayModeController) to render
 * via RenderPipeline into a framebuffer for ImGui display.
 * <p>
 * <b>RENDERING ARCHITECTURE NOTE:</b>
 * This adapter allows RenderPipeline to render to editor framebuffers
 * without coupling the core rendering system to editor-specific classes.
 *
 * @see RenderTarget
 * @see EditorFramebuffer
 * @see RenderPipeline
 */
@Getter
public class FramebufferTarget implements RenderTarget {

    private final EditorFramebuffer framebuffer;

    public FramebufferTarget(EditorFramebuffer framebuffer) {
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
