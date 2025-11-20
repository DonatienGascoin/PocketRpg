package com.pocket.rpg.aiEngineWithPostProcess;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

public class PostProcessor {
    private final int width;
    private final int height;
    private final float targetAspectRatio;

    private int fboA_ID;
    private int fboB_ID;
    private int textureA_ID;
    private int textureB_ID;
    private int rboID;
    private int quadVAO;

    private final List<PostEffect> effects = new ArrayList<>();
//
//    private Shader finalShader;

    /**
     * Creates a post processor for the specified internal resolution.
     *
     * @param width  Internal game width (e.g., 1280)
     * @param height Internal game height (e.g., 720)
     */
    public PostProcessor(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }
        this.width = width;
        this.height = height;
        this.targetAspectRatio = (float) width / height;
    }

    /**
     * Initializes all OpenGL resources including FBOs, textures, and effects.
     * Must be called after OpenGL context is current.
     */
    public void init() {
        setupFBOs();
        setupFullScreenQuad();
//        finalShader = new Shader("assets/shaders/finalShader.glsl");
//        finalShader.compileAndLink();
//        finalShader.use();
//        finalShader.uploadInt("screenTexture", 0);
//        finalShader.detach();

        // Initialize all effects
        for (PostEffect effect : effects) {
            effect.init();
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
     * Binds FBO A for rendering the initial game scene.
     */
    public void bindFboA() {
        glBindFramebuffer(GL_FRAMEBUFFER, fboA_ID);
        glViewport(0, 0, width, height);
    }

    /**
     * Applies all post-processing effects in sequence.
     *
     */
    public void applyEffects() {
        int inputTexture = textureA_ID;
        int outputFbo = fboB_ID;

        // Apply all effects in the chain
        for (PostEffect effect : effects) {
            int passCount = effect.getPassCount();

            // Apply each pass of the effect
            for (int passIndex = 0; passIndex < passCount; passIndex++) {
                effect.applyPass(passIndex, inputTexture, outputFbo, quadVAO, width, height);

                // Swap FBOs for next pass
                inputTexture = (inputTexture == textureA_ID) ? textureB_ID : textureA_ID;
                outputFbo = (outputFbo == fboA_ID) ? fboB_ID : fboA_ID;
            }
        }
    }

    /**
     * Blits the final processed texture to the screen with aspect ratio preservation.
     * Black bars (pillarbox/letterbox) are added as needed.
     *
     * @param finalTextureId The texture containing the final processed image
     * @param windowWidth    Current window width
     * @param windowHeight   Current window height
     */
    /*private void blitFinalTextureToScreen(int finalTextureId, int windowWidth, int windowHeight) {
        // Clear entire window black
        glViewport(0, 0, windowWidth, windowHeight);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Apply pillarboxed viewport
        int newWidth = windowWidth;
        int newHeight = (int) (newWidth / targetAspectRatio);
        if (newHeight > windowHeight) {
            newHeight = windowHeight;
            newWidth = (int) (newHeight * targetAspectRatio);
        }
        int viewportX = (windowWidth - newWidth) / 2;
        int viewportY = (windowHeight - newHeight) / 2;
        glViewport(viewportX, viewportY, newWidth, newHeight);

        // Draw final texture using the internal finalBlitShader
        glDisable(GL_DEPTH_TEST);
        finalShader.use();
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, finalTextureId);

        // Use nearest neighbor filtering for sharp pixels (fixes pixelation)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        // Cleanup
        glBindTexture(GL_TEXTURE_2D, 0);
        glBindVertexArray(0);
        finalShader.detach();
    }*/

    /**
     * Sets up the two frame buffer objects for ping-ponging between effect passes.
     */
    private void setupFBOs() {
        // Create FBO A with color and depth attachments
        fboA_ID = glGenFramebuffers();
        textureA_ID = createFBOTexture(width, height);
        attachTextureToFBO(fboA_ID, textureA_ID);

        // Create FBO B with color attachment
        fboB_ID = glGenFramebuffers();
        textureB_ID = createFBOTexture(width, height);
        attachTextureToFBO(fboB_ID, textureB_ID);

        // Create shared depth/stencil render buffer
        rboID = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rboID);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_STENCIL, width, height);

        // Attach depth buffer to both FBOs
        glBindFramebuffer(GL_FRAMEBUFFER, fboA_ID);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rboID);

        // Check FBO A completeness
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer A is not complete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private int createFBOTexture(int texWidth, int texHeight) {
        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texID);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, texWidth, texHeight, 0,
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

    private void attachTextureToFBO(int fboID, int textureID) {
        glBindFramebuffer(GL_FRAMEBUFFER, fboID);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureID, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

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
        glDeleteFramebuffers(fboA_ID);
        glDeleteFramebuffers(fboB_ID);
        glDeleteTextures(textureA_ID);
        glDeleteTextures(textureB_ID);
        glDeleteRenderbuffers(rboID);
        glDeleteVertexArrays(quadVAO);
//        finalShader.delete();

        for (PostEffect effect : effects) {
            effect.destroy();
        }
    }
}
