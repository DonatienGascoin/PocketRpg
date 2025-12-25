package com.pocket.rpg.editor.rendering;

import com.pocket.rpg.config.GameConfig;
import com.pocket.rpg.config.RenderingConfig;
import com.pocket.rpg.core.ViewportConfig;
import com.pocket.rpg.postProcessing.PostEffect;
import com.pocket.rpg.rendering.OverlayRenderer;
import com.pocket.rpg.rendering.Shader;
import com.pocket.rpg.rendering.renderers.RenderInterface;
import com.pocket.rpg.rendering.renderers.OpenGLRenderer;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.transitions.TransitionManager;
import lombok.Getter;

import java.nio.ByteBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders the game scene during Play Mode.
 * <p>
 * Unlike the main game's PostProcessor which renders to screen,
 * this renderer captures output to a texture for ImGui display.
 * <p>
 * Pipeline:
 * 1. Render scene to sceneTexture (at game resolution)
 * 2. Apply post-processing effects (ping-pong between fboA/fboB)
 * 3. Final output is available via getOutputTexture() for ImGui
 */
public class PlayModeRenderer {

    private final GameConfig gameConfig;
    private final RenderingConfig renderingConfig;

    @Getter
    private int gameWidth;
    @Getter
    private int gameHeight;

    // Scene rendering FBO
    private int sceneFbo;
    private int sceneTexture;
    private int sceneDepthRbo;

    // Post-processing ping-pong FBOs
    private int fboA;
    private int fboB;
    private int textureA;
    private int textureB;

    // Track which texture has the final output
    private int outputTexture;

    // Fullscreen quad for blitting
    private int quadVao;
    private Shader blitShader;

    // Post-processing effects
    private List<PostEffect> effects;

    // Scene renderer
    private RenderInterface renderer;

    // Overlay renderer from the RenderInterface (for transitions)
    @Getter
    private OverlayRenderer overlayRenderer;

    private boolean initialized = false;

    public PlayModeRenderer(GameConfig gameConfig, RenderingConfig renderingConfig) {
        this.gameConfig = gameConfig;
        this.renderingConfig = renderingConfig;
    }

    /**
     * Initializes the renderer with game resolution and effects.
     */
    public void init() {
        if (initialized) {
            return;
        }

        gameWidth = gameConfig.getGameWidth();
        gameHeight = gameConfig.getGameHeight();

        // Get effects from config
        effects = gameConfig.getPostProcessingEffects();

        // Create FBOs
        createSceneFbo();
        createPostProcessingFbos();
        createFullscreenQuad();

        // Create blit shader
        blitShader = new Shader("gameData/assets/shaders/passThrough.glsl");
        blitShader.compileAndLink();
        blitShader.use();
        blitShader.uploadInt("screenTexture", 0);
        blitShader.detach();

        // Initialize effects
        for (PostEffect effect : effects) {
            effect.init();
        }

        // Create renderer
        renderer = new OpenGLRenderer(new ViewportConfig(gameConfig), renderingConfig);
        renderer.init(gameWidth, gameHeight);

        // Get overlay renderer for transitions
        overlayRenderer = renderer.getOverlayRenderer();
        if (overlayRenderer != null) {
            overlayRenderer.setScreenSize(gameWidth, gameHeight);
        }

        // Initial output is scene texture (before any effects)
        outputTexture = sceneTexture;

        initialized = true;
        System.out.println("PlayModeRenderer initialized: " + gameWidth + "x" + gameHeight +
                " with " + effects.size() + " effects");
    }

    /**
     * Renders the game scene with post-processing.
     *
     * @param scene             The runtime scene to render
     * @param transitionManager The transition manager for overlay effects
     */
    public void render(Scene scene, TransitionManager transitionManager) {
        if (!initialized || scene == null) {
            return;
        }

        // Step 1: Render scene to scene FBO
        renderSceneToFbo(scene, transitionManager);

        // Step 2: Apply post-processing effects
        if (!effects.isEmpty()) {
            applyPostProcessing();
        } else {
            // No effects, output is scene texture
            outputTexture = sceneTexture;
        }

        // Unbind any FBO (return to default)
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /**
     * Renders scene to the scene FBO.
     */
    private void renderSceneToFbo(Scene scene, TransitionManager transitionManager) {
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
        glViewport(0, 0, gameWidth, gameHeight);

        // Clear with dark background
        glClearColor(0.1f, 0.1f, 0.15f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // Render scene
        renderer.render(scene);

        // Render transition overlay (if active)
        if (transitionManager != null && transitionManager.isTransitioning()) {
            transitionManager.render();
        }
    }

    /**
     * Applies post-processing effects using ping-pong technique.
     */
    private void applyPostProcessing() {
        glDisable(GL_DEPTH_TEST);

        int inputTexture = sceneTexture;
        int currentOutputFbo = fboA;

        for (int i = 0; i < effects.size(); i++) {
            PostEffect effect = effects.get(i);
            int passCount = effect.getPassCount();

            for (int passIndex = 0; passIndex < passCount; passIndex++) {
                // Apply effect pass
                effect.applyPass(passIndex, inputTexture, currentOutputFbo, quadVao, gameWidth, gameHeight);

                // Swap for next pass
                if (currentOutputFbo == fboA) {
                    inputTexture = textureA;
                    currentOutputFbo = fboB;
                } else {
                    inputTexture = textureB;
                    currentOutputFbo = fboA;
                }
            }
        }

        // The last written texture is our output
        outputTexture = inputTexture;

        glEnable(GL_DEPTH_TEST);
    }

    /**
     * Gets the output texture for ImGui display.
     * This texture contains the final rendered image after post-processing.
     */
    public int getOutputTexture() {
        return outputTexture;
    }

    // ========================================================================
    // FBO CREATION
    // ========================================================================

    private void createSceneFbo() {
        sceneFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);

        // Color texture
        sceneTexture = createTexture(gameWidth, gameHeight);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneTexture, 0);

        // Depth renderbuffer
        sceneDepthRbo = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, sceneDepthRbo);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, gameWidth, gameHeight);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, sceneDepthRbo);

        checkFramebufferComplete("Scene FBO");
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private void createPostProcessingFbos() {
        // FBO A
        fboA = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboA);
        textureA = createTexture(gameWidth, gameHeight);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureA, 0);
        checkFramebufferComplete("FBO A");

        // FBO B
        fboB = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboB);
        textureB = createTexture(gameWidth, gameHeight);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textureB, 0);
        checkFramebufferComplete("FBO B");

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private int createTexture(int width, int height) {
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        return texId;
    }

    private void createFullscreenQuad() {
        float[] vertices = {
                // positions   // texCoords
                -1.0f,  1.0f,  0.0f, 1.0f,
                -1.0f, -1.0f,  0.0f, 0.0f,
                 1.0f, -1.0f,  1.0f, 0.0f,

                -1.0f,  1.0f,  0.0f, 1.0f,
                 1.0f, -1.0f,  1.0f, 0.0f,
                 1.0f,  1.0f,  1.0f, 1.0f
        };

        quadVao = glGenVertexArrays();
        int vbo = glGenBuffers();

        glBindVertexArray(quadVao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);

        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2L * Float.BYTES);

        glBindVertexArray(0);
    }

    private void checkFramebufferComplete(String name) {
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException(name + " incomplete: " + status);
        }
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    public void destroy() {
        if (!initialized) {
            return;
        }

        // Destroy effects
        for (PostEffect effect : effects) {
            effect.destroy();
        }

        // Destroy renderer
        if (renderer != null) {
            renderer.destroy();
            renderer = null;
        }

        // Destroy shader
        if (blitShader != null) {
            blitShader.delete();
            blitShader = null;
        }

        // Delete FBOs and textures
        glDeleteFramebuffers(sceneFbo);
        glDeleteFramebuffers(fboA);
        glDeleteFramebuffers(fboB);
        glDeleteTextures(sceneTexture);
        glDeleteTextures(textureA);
        glDeleteTextures(textureB);
        glDeleteRenderbuffers(sceneDepthRbo);
        glDeleteVertexArrays(quadVao);

        overlayRenderer = null;
        initialized = false;

        System.out.println("PlayModeRenderer destroyed");
    }
}
