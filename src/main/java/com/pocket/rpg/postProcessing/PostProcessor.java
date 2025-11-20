package com.pocket.rpg.postProcessing;

import com.pocket.rpg.engine.Window;
import com.pocket.rpg.utils.WindowConfig;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Manages the post-processing pipeline, coordinating multiple effects
 * and handling frame buffer ping-ponging between passes.
 * Now properly separates the effect pipeline from the final screen presentation.
 */
public class PostProcessor {

    /**
     * Scaling mode for rendering to screen when pillarbox is disabled.
     */
    public enum ScalingMode {
        /**
         * Stretch the image to fill the entire window (may distort)
         */
        STRETCH,
        /**
         * Maintain aspect ratio and center (adds black bars, like pillarbox)
         */
        MAINTAIN_ASPECT_RATIO
    }

    private final int width;
    private final int height;

    private int fboA;
    private int fboB;
    private int textureA;
    private int textureB;
    private int rbo;
    private int quadVAO;

    private final List<PostEffect> effects = new ArrayList<>();
    private PillarBox pillarBox;
    private Window window;
    private ScalingMode scalingMode = ScalingMode.MAINTAIN_ASPECT_RATIO;

    /**
     * Creates a post processor for the specified window configuration.
     *
     * @param config Window configuration containing internal resolution and effects
     */
    public PostProcessor(WindowConfig config) {
        if (config.getInitialWidth() <= 0 || config.getInitialHeight() <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
        this.width = config.getInitialWidth();
        this.height = config.getInitialHeight();
        // Add effects from config
        this.effects.addAll(config.getPostProcessingEffects());

        // Enable pillarbox if configured
        if (config.isEnablePillarbox()) {
            enablePillarBox(config.getPillarboxAspectRatio());
        } else {
            scalingMode = config.getScalingMode();
        }
    }

    /**
     * Initializes all OpenGL resources including FBOs, textures, and effects.
     * Must be called after OpenGL context is current.
     *
     * @param window The window reference for effects
     */
    public void init(Window window) {
        this.window = window;
        setupFBOs();
        setupFullScreenQuad();

        // Initialize all effects
        for (PostEffect effect : effects) {
            effect.init(window);
        }

        // Initialize pillarbox if needed
        if (pillarBox != null) {
            pillarBox.init(window);
        }
    }

    /**
     * Adds a post-processing effect to the pipeline.
     * Effects are applied in the order they are added.
     *
     * @param effect The effect to add
     */
    public void addEffect(PostEffect effect) {
        this.effects.add(effect);
    }

    /**
     * Convenience method to enable pillarbox with target aspect ratio.
     *
     * @param aspectRatio Target aspect ratio (e.g., 16f/9f, 4f/3f)
     */
    public void enablePillarBox(float aspectRatio) {
        this.pillarBox = new PillarBox(aspectRatio);
        if (window != null) {
            this.pillarBox.init(window);
        }
    }

    public void beginCapture() {
        if (!effects.isEmpty() || pillarBox != null) {
            bindFboA();
        }
    }

    public void endCaptureAndApplyEffects() {
        if (!effects.isEmpty() || pillarBox != null) {
            applyEffects();
        }
    }

    /**
     * Binds FBO A for rendering the initial game scene.
     */
    private void bindFboA() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboA);
        glViewport(0, 0, width, height);
    }

    /**
     * Applies all post-processing effects in sequence, then renders to screen.
     * FIXED: Now properly handles the effect pipeline and final screen output.
     */
    private void applyEffects() {
        // Disable depth testing for 2D post-processing
        glDisable(GL_DEPTH_TEST);

        if (effects.isEmpty()) {
            // No effects - just render directly to screen
            renderToScreen(textureA);
            return;
        }

        // Start with texture A (which contains the rendered game scene)
        int inputTexture = textureA;
        int outputFbo = fboB;

        // Apply all effects, ping-ponging between FBOs
        for (int i = 0; i < effects.size(); i++) {
            PostEffect effect = effects.get(i);
            int passCount = effect.getPassCount();

            for (int passIndex = 0; passIndex < passCount; passIndex++) {
                // Apply the effect pass
                effect.applyPass(passIndex, inputTexture, outputFbo, quadVAO, width, height);

                // Swap buffers for next pass/effect
                // The output becomes the input for the next iteration
                if (outputFbo == fboA) {
                    inputTexture = textureA;
                    outputFbo = fboB;
                } else {
                    inputTexture = textureB;
                    outputFbo = fboA;
                }
            }
        }

        // After all effects, inputTexture contains the final result
        // Render it to screen (with or without pillarbox)
        renderToScreen(inputTexture);
    }

    /**
     * Renders the final texture to the screen.
     * Uses pillarbox if enabled, otherwise uses the configured scaling mode.
     *
     * @param textureId The final processed texture
     */
    private void renderToScreen(int textureId) {
        if (pillarBox != null) {
            // Use pillarbox for aspect ratio preservation
            pillarBox.renderToScreen(textureId, quadVAO);
        } else {
            // Use scaling mode
            blitToScreen(textureId);
        }
    }

    /**
     * Blits a texture directly to the screen using the configured scaling mode.
     *
     * @param textureId The texture to display
     */
    private void blitToScreen(int textureId) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        int viewportX, viewportY, viewportWidth, viewportHeight;

        if (scalingMode == ScalingMode.MAINTAIN_ASPECT_RATIO) {
            // Calculate viewport to maintain aspect ratio and center the content
            float targetAspect = (float) width / height;
            float windowAspect = (float) window.getScreenWidth() / window.getScreenHeight();

            if (windowAspect > targetAspect) {
                // Window is wider - add pillarboxes
                viewportHeight = window.getScreenHeight();
                viewportWidth = (int) (viewportHeight * targetAspect);
                viewportX = (window.getScreenWidth() - viewportWidth) / 2;
                viewportY = 0;
            } else {
                // Window is taller - add letterboxes
                viewportWidth = window.getScreenWidth();
                viewportHeight = (int) (viewportWidth / targetAspect);
                viewportX = 0;
                viewportY = (window.getScreenHeight() - viewportHeight) / 2;
            }

            // Clear entire window to black
            glViewport(0, 0, window.getScreenWidth(), window.getScreenHeight());
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        } else {
            // STRETCH mode - fill entire window
            viewportX = 0;
            viewportY = 0;
            viewportWidth = window.getScreenWidth();
            viewportHeight = window.getScreenHeight();

            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        }

        // Set viewport
        glViewport(viewportX, viewportY, viewportWidth, viewportHeight);

        glDisable(GL_DEPTH_TEST);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Use nearest neighbor for sharp pixels
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
    }

    /**
     * Sets up the two frame buffer objects for ping-ponging between effect passes.
     */
    private void setupFBOs() {
        // Create FBO A with color attachment
        fboA = glGenFramebuffers();
        textureA = createFBOTexture(width, height);
        attachTextureToFBO(fboA, textureA);

        // Create FBO B with color attachment
        fboB = glGenFramebuffers();
        textureB = createFBOTexture(width, height);
        attachTextureToFBO(fboB, textureB);

        // Create shared depth/stencil renderbuffer
        rbo = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rbo);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, width, height);

        // Attach depth buffer to both FBOs
        glBindFramebuffer(GL_FRAMEBUFFER, fboA);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                GL_RENDERBUFFER, rbo);

        // Check FBO A completeness
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer A is not complete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, fboB);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT,
                GL_RENDERBUFFER, rbo);

        // Check FBO B completeness
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer B is not complete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Creates a texture suitable for use as an FBO color attachment.
     * Uses NEAREST filtering for sharp pixel-perfect rendering.
     */
    private int createFBOTexture(int texWidth, int texHeight) {
        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texID);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texWidth, texHeight, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        // Use NEAREST filtering for sharp pixel-perfect rendering
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Clamp to edge to avoid border artifacts
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_2D, 0);
        return texID;
    }

    /**
     * Attaches a texture as the color attachment of an FBO.
     */
    private void attachTextureToFBO(int fboID, int textureID) {
        glBindFramebuffer(GL_FRAMEBUFFER, fboID);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                GL_TEXTURE_2D, textureID, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Sets up a full-screen quad for rendering post-processing effects.
     */
    private void setupFullScreenQuad() {
        // Vertex data: position (x, y) + texture coords (u, v)
        float[] quadVertices = {
                // Position    // TexCoords
                -1.0f, 1.0f, 0.0f, 1.0f,  // Top-left
                -1.0f, -1.0f, 0.0f, 0.0f,  // Bottom-left
                1.0f, -1.0f, 1.0f, 0.0f,  // Bottom-right

                -1.0f, 1.0f, 0.0f, 1.0f,  // Top-left
                1.0f, -1.0f, 1.0f, 0.0f,  // Bottom-right
                1.0f, 1.0f, 1.0f, 1.0f   // Top-right
        };

        quadVAO = glGenVertexArrays();
        int vbo = glGenBuffers();

        glBindVertexArray(quadVAO);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, quadVertices, GL_STATIC_DRAW);

        // Position attribute
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);

        // Texture coordinate attribute
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        glBindVertexArray(0);
    }

    /**
     * Cleans up all OpenGL resources.
     */
    public void destroy() {
        glDeleteFramebuffers(fboA);
        glDeleteFramebuffers(fboB);
        glDeleteTextures(textureA);
        glDeleteTextures(textureB);
        glDeleteRenderbuffers(rbo);
        glDeleteVertexArrays(quadVAO);

        for (PostEffect effect : effects) {
            effect.destroy();
        }

        if (pillarBox != null) {
            pillarBox.destroy();
        }
    }
}