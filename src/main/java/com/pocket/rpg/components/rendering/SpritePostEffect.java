package com.pocket.rpg.components.rendering;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.core.Transform;
import com.pocket.rpg.rendering.postfx.PostEffect;
import com.pocket.rpg.rendering.resources.Shader;
import com.pocket.rpg.rendering.batch.Renderer;
import lombok.Getter;
import lombok.Setter;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL33.*;

/**
 * Component that applies post-processing effects to a single sprite.
 * The sprite is rendered to an offscreen framebuffer, effects are applied,
 * then the result is composited back to the scene.
 * <p>
 * STRATEGY 1: Component-Based Per-Sprite Effects
 * Best for: 1-5 special sprites (bosses, hero character, special items)
 * Performance: ~2-5ms per sprite with effects
 * TODO: FIX ME- Not working
 */
@ComponentMeta(category = "Rendering")
public class SpritePostEffect extends Component {

    @Getter
    private final List<PostEffect> effects = new ArrayList<>();

    // Framebuffers for ping-pong rendering
    private int fbo1, fbo2;
    private int texture1, texture2;
    private int quadVAO, quadVBO;

    @Setter
    @Getter
    private int bufferWidth = 256;

    @Setter
    @Getter
    private int bufferHeight = 256;

    @Setter
    @Getter
    private float padding = 64;

    private boolean initialized = false;
    private Shader compositeShader;

    private static final Vector4f GLOBAL_TINT = new Vector4f(1, 1, 1, 1);

    public void addEffect(PostEffect effect) {
        effects.add(effect);
        if (initialized) {
            effect.init();
        }
    }

    public void removeEffect(PostEffect effect) {
        effects.remove(effect);
    }

    public void clearEffects() {
        effects.clear();
    }

    @Override
    public void onStart() {
        initFramebuffers();

        compositeShader = new Shader("gameData/assets/shaders/sprite.glsl");
        compositeShader.compileAndLink();

        for (PostEffect effect : effects) {
            effect.init();
        }

        initialized = true;
    }

    private void initFramebuffers() {
        quadVAO = createQuad();

        fbo1 = glGenFramebuffers();
        texture1 = createTexture(bufferWidth, bufferHeight);
        attachTextureToFramebuffer(fbo1, texture1);

        fbo2 = glGenFramebuffers();
        texture2 = createTexture(bufferWidth, bufferHeight);
        attachTextureToFramebuffer(fbo2, texture2);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo1);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("SpritePostEffect: Framebuffer 1 incomplete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, fbo2);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("SpritePostEffect: Framebuffer 2 incomplete!");
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void renderWithEffects(Renderer renderer, SpriteRenderer spriteRenderer) {
        if (!initialized || effects.isEmpty()) {
            renderer.drawSpriteRenderer(spriteRenderer);
            return;
        }

        Transform transform = gameObject.getTransform();
        Vector3f originalPos = new Vector3f(transform.getPosition());

        // Render to FBO
        glBindFramebuffer(GL_FRAMEBUFFER, fbo1);
        glViewport(0, 0, bufferWidth, bufferHeight);
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        transform.setPosition(bufferWidth / 2.0f, bufferHeight / 2.0f, originalPos.z);
        renderer.setProjection(bufferWidth, bufferHeight);
        renderer.begin();
        renderer.drawSpriteRenderer(spriteRenderer);
        renderer.end();
        transform.setPosition(originalPos);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // Apply effects
        int currentInput = texture1;
        int currentOutput = fbo2;

        for (PostEffect effect : effects) {
            for (int pass = 0; pass < effect.getPassCount(); pass++) {
                effect.applyPass(pass, currentInput, currentOutput, quadVAO, bufferWidth, bufferHeight);

                int temp = currentInput;
                currentInput = (currentOutput == fbo1) ? texture1 : texture2;
                currentOutput = (currentOutput == fbo1) ? fbo2 : fbo1;
            }
        }

        // Composite back
        renderComposite(currentInput, originalPos, spriteRenderer);
    }

    private void renderComposite(int texture, Vector3f position, SpriteRenderer spriteRenderer) {
        compositeShader.use();
        compositeShader.uploadInt("textureSampler", 0);

        Transform transform = gameObject.getTransform();
        float width = spriteRenderer.getSprite().getWidth() * transform.getScale().x + padding * 2;
        float height = spriteRenderer.getSprite().getHeight() * transform.getScale().y + padding * 2;

        Matrix4f model = new Matrix4f()
                .translate(position.x - padding, position.y - padding, position.z)
                .scale(width, height, 1);

        compositeShader.uploadMat4f("model", model);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);

        glBindVertexArray(quadVAO);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        glBindTexture(GL_TEXTURE_2D, 0);
        compositeShader.detach();
    }

    private int createTexture(int width, int height) {
        int textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);
        return textureId;
    }

    private void attachTextureToFramebuffer(int fbo, int texture) {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private int createQuad() {
        float[] vertices = {
                0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 1.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                0.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 1.0f, 1.0f, 1.0f,
                1.0f, 0.0f, 1.0f, 0.0f
        };

        int vao = glGenVertexArrays();
        quadVBO = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);

        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);

        glBindVertexArray(0);
        return vao;
    }

    @Override
    public void onDestroy() {
        if (fbo1 != 0) glDeleteFramebuffers(fbo1);
        if (fbo2 != 0) glDeleteFramebuffers(fbo2);
        if (texture1 != 0) glDeleteTextures(texture1);
        if (texture2 != 0) glDeleteTextures(texture2);
        if (quadVAO != 0) glDeleteVertexArrays(quadVAO);
        if (quadVBO != 0) glDeleteBuffers(quadVBO);

        for (PostEffect effect : effects) {
            effect.destroy();
        }

        if (compositeShader != null) {
            compositeShader.delete();
        }
    }
}