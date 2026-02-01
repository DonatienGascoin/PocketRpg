package com.pocket.rpg.rendering.postfx;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.core.window.AbstractWindow;
import com.pocket.rpg.rendering.core.RenderTarget;
import com.pocket.rpg.rendering.resources.Shader;
import lombok.Getter;
import lombok.Setter;

import lombok.Setter;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Manages the post-processing pipeline, coordinating multiple effects
 * and handling frame buffer ping-ponging between passes.
 *
 * <p>Supports dynamic effect management - effects can be added or removed at runtime.
 * Effects are applied in the order they were added to the pipeline.
 */
public class PostProcessor {

    private boolean initialized = false;
    @Getter
    @Setter
    private boolean enabled = true;

    /**
     * Scaling mode for rendering to screen when pillarbox is disabled.
     */
    public enum ScalingMode {
        STRETCH,
        MAINTAIN_ASPECT_RATIO
    }

    // FIX: This is the GAME resolution (fixed internal resolution)
    private final int gameWidth;
    private final int gameHeight;

    private int fboA;
    private int fboB;
    private int textureA;
    private int textureB;
    private int rbo;
    private int quadVAO;

    @Getter
    private final List<PostEffect> effects = new ArrayList<>();
    private PillarBox pillarBox;
    private AbstractWindow window;
    private ScalingMode scalingMode = ScalingMode.MAINTAIN_ASPECT_RATIO;

    private Shader blitShader;

    @Setter
    private Vector4f clearColor = new Vector4f(0, 0, 0, 1);

    /**
     * FIX: Creates a post processor with fixed game resolution.
     */
    public PostProcessor(GameConfig config) {
        if (config.getGameWidth() <= 0 || config.getGameHeight() <= 0) {
            throw new IllegalArgumentException("Game resolution must be positive: " +
                    config.getGameWidth() + "x" + config.getGameHeight());
        }

        this.gameWidth = config.getGameWidth();
        this.gameHeight = config.getGameHeight();

        System.out.println("PostProcessor using game resolution: " + gameWidth + "x" + gameHeight);

        this.effects.addAll(config.getPostProcessingEffects());

        if (config.isEnablePillarBox()) {
            enablePillarBox(config.getEffectivePillarboxAspectRatio());
        } else {
            scalingMode = config.getScalingMode();
        }
    }

    /**
     * Initializes all OpenGL resources.
     */
    public void init(AbstractWindow window) {
        this.window = window;
        setupFBOs();
        setupFullScreenQuad();

        blitShader = new Shader("gameData/assets/shaders/passThrough.glsl");
        blitShader.compileAndLink();
        blitShader.use();
        blitShader.uploadInt("screenTexture", 0);
        blitShader.detach();

        for (PostEffect effect : effects) {
            effect.init();
        }

        if (pillarBox != null) {
            pillarBox.init(window);
        }

        initialized = true;
        // TODO System.out.println("PostProcessor initialized with " + effects.size() + " effects");
    }

    /**
     * Adds a post-processing effect to the pipeline.
     * The effect will be applied in the order it was added.
     * If the processor is already initialized, the effect is initialized immediately.
     *
     * @param effect The effect to add
     */
    public void addEffect(PostEffect effect) {
        if (effect == null) {
            throw new IllegalArgumentException("Effect cannot be null");
        }

        effects.add(effect);

        // If we're already initialized, initialize the effect now
        if (initialized) {
            effect.init();
            System.out.println("Added effect: " + effect.getClass().getSimpleName());
        }
    }

    /**
     * Removes a post-processing effect from the pipeline.
     *
     * @param effect The effect to remove
     * @return true if the effect was removed, false if it wasn't in the pipeline
     */
    public boolean removeEffect(PostEffect effect) {
        if (effect == null) {
            return false;
        }

        boolean removed = effects.remove(effect);
        if (removed && initialized) {
            effect.destroy();
            System.out.println("Removed effect: " + effect.getClass().getSimpleName());
        }
        return removed;
    }

    /**
     * Removes all post-processing effects from the pipeline.
     */
    public void clearEffects() {
        if (initialized) {
            for (PostEffect effect : effects) {
                effect.destroy();
            }
        }
        effects.clear();
        System.out.println("Cleared all effects");
    }

    /**
     * Gets the number of effects currently in the pipeline.
     *
     * @return The number of active effects
     */
    public int getEffectCount() {
        return effects.size();
    }

    public void enablePillarBox(float aspectRatio) {
        this.pillarBox = new PillarBox(aspectRatio);
        if (window != null) {
            this.pillarBox.init(window);
        }
    }

    public void beginCapture() {
        // Only capture if post-processing is enabled and needed
        if (enabled && needsPostProcessing()) {
            bindFboA();
            // Clear the FBO with the configured clear color
            glClearColor(clearColor.x, clearColor.y, clearColor.z, clearColor.w);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }
    }

    /**
     * Ends capture and applies effects, rendering to the specified target.
     *
     * @param target The render target for final output (use null for screen)
     */
    public void endCaptureAndApplyEffects(RenderTarget target) {
        // Only apply effects if enabled and needed
        if (enabled && needsPostProcessing()) {
            applyEffects(target);
        }
    }

    /**
     * Ends capture and applies effects, rendering to screen.
     * @deprecated Use {@link #endCaptureAndApplyEffects(RenderTarget)} instead
     */
    @Deprecated
    public void endCaptureAndApplyEffects() {
        endCaptureAndApplyEffects(null);
    }

    private boolean needsPostProcessing() {
        return !effects.isEmpty() || pillarBox != null || scalingMode == ScalingMode.MAINTAIN_ASPECT_RATIO;
    }

    /**
     * FIX: Binds FBO A with game resolution viewport.
     */
    private void bindFboA() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboA);
        glViewport(0, 0, gameWidth, gameHeight);
    }

    /**
     * Applies all post-processing effects in sequence, then renders to target.
     *
     * @param target The render target for final output (null = screen)
     */
    private void applyEffects(RenderTarget target) {
        glDisable(GL_DEPTH_TEST);

        if (effects.isEmpty()) {
            renderToTarget(textureA, target);
            return;
        }

        int inputTexture = textureA;
        int outputFbo = fboB;

        for (int i = 0; i < effects.size(); i++) {
            PostEffect effect = effects.get(i);
            int passCount = effect.getPassCount();

            for (int passIndex = 0; passIndex < passCount; passIndex++) {
                // FIX: Pass game resolution to effects
                effect.applyPass(passIndex, inputTexture, outputFbo, quadVAO, gameWidth, gameHeight);

                if (outputFbo == fboA) {
                    inputTexture = textureA;
                    outputFbo = fboB;
                } else {
                    inputTexture = textureB;
                    outputFbo = fboA;
                }
            }
        }

        renderToTarget(inputTexture, target);
    }

    /**
     * Renders the final texture to the target with proper scaling.
     *
     * @param textureId The texture to render
     * @param target    The render target (null = screen)
     */
    private void renderToTarget(int textureId, RenderTarget target) {
        if (pillarBox != null) {
            // PillarBox always renders to screen - not supported with custom targets
            if (target != null) {
                System.err.println("PostProcessor: PillarBox not supported with custom render targets");
            }
            pillarBox.renderToScreen(textureId, quadVAO);
        } else {
            blitToTarget(textureId, target);
        }
    }

    /**
     * Blits texture to the specified target.
     *
     * @param textureId The texture to blit
     * @param target    The render target (null = screen with aspect ratio handling)
     */
    private void blitToTarget(int textureId, RenderTarget target) {
        if (target != null) {
            // Render to custom target (editor framebuffer) - fill entire target
            target.bind();
            glViewport(0, 0, target.getWidth(), target.getHeight());
        } else {
            // Render to screen with aspect ratio handling
            glBindFramebuffer(GL_FRAMEBUFFER, 0);

            int viewportX, viewportY, viewportWidth, viewportHeight;

            if (scalingMode == ScalingMode.MAINTAIN_ASPECT_RATIO) {
                float gameAspect = (float) gameWidth / gameHeight;
                float windowAspect = (float) window.getScreenWidth() / window.getScreenHeight();

                if (windowAspect > gameAspect) {
                    viewportHeight = window.getScreenHeight();
                    viewportWidth = (int) (viewportHeight * gameAspect);
                    viewportX = (window.getScreenWidth() - viewportWidth) / 2;
                    viewportY = 0;
                } else {
                    viewportWidth = window.getScreenWidth();
                    viewportHeight = (int) (viewportWidth / gameAspect);
                    viewportX = 0;
                    viewportY = (window.getScreenHeight() - viewportHeight) / 2;
                }

                glViewport(0, 0, window.getScreenWidth(), window.getScreenHeight());
                glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            } else {
                viewportX = 0;
                viewportY = 0;
                viewportWidth = window.getScreenWidth();
                viewportHeight = window.getScreenHeight();

                glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            }

            glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
        }

        glDisable(GL_DEPTH_TEST);

        blitShader.use();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        blitShader.detach();
    }

    /**
     * FIX: Sets up FBOs with game resolution.
     */
    private void setupFBOs() {
        fboA = glGenFramebuffers();
        textureA = createFBOTexture(gameWidth, gameHeight);
        attachTextureToFBO(fboA, textureA);

        fboB = glGenFramebuffers();
        textureB = createFBOTexture(gameWidth, gameHeight);
        attachTextureToFBO(fboB, textureB);

        rbo = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rbo);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, gameWidth, gameHeight);

        glBindFramebuffer(GL_FRAMEBUFFER, fboA);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                GL_RENDERBUFFER, rbo);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer A is not complete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, fboB);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                GL_RENDERBUFFER, rbo);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer B is not complete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // TODO System.out.println("FBOs created at game resolution: " + gameWidth + "x" + gameHeight);
    }

    private int createFBOTexture(int texWidth, int texHeight) {
        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texID);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texWidth, texHeight, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_2D, 0);
        return texID;
    }

    private void attachTextureToFBO(int fboID, int textureID) {
        glBindFramebuffer(GL_FRAMEBUFFER, fboID);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, textureID, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void setupFullScreenQuad() {
        float[] quadVertices = {
                -1.0f, 1.0f, 0.0f, 1.0f,
                -1.0f, -1.0f, 0.0f, 0.0f,
                1.0f, -1.0f, 1.0f, 0.0f,

                -1.0f, 1.0f, 0.0f, 1.0f,
                1.0f, -1.0f, 1.0f, 0.0f,
                1.0f, 1.0f, 1.0f, 1.0f
        };

        quadVAO = glGenVertexArrays();
        int vbo = glGenBuffers();

        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);

        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        glBindVertexArray(0);
    }

    public void destroy() {
        glDeleteFramebuffers(fboA);
        glDeleteFramebuffers(fboB);
        glDeleteTextures(textureA);
        glDeleteTextures(textureB);
        glDeleteRenderbuffers(rbo);
        glDeleteVertexArrays(quadVAO);

        if (blitShader != null) {
            blitShader.delete();
        }

        for (PostEffect effect : effects) {
            effect.destroy();
        }

        if (pillarBox != null) {
            pillarBox.destroy();
        }

        initialized = false;
        // TODO aSystem.out.println("PostProcessor destroyed");
    }
}