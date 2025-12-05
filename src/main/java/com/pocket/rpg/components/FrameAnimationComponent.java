package com.pocket.rpg.components;

import com.pocket.rpg.rendering.Sprite;

import java.util.List;

public class FrameAnimationComponent extends Component {
    private final List<Sprite> frames;
    private final float frameTime;
    private int currentFrame = 0;
    private float timer = 0;

    public FrameAnimationComponent(List<Sprite> frames, float frameTime) {
        this.frames = frames;
        this.frameTime = frameTime;
    }

    @Override
    public void update(float deltaTime) {
        SpriteRenderer renderer = gameObject.getComponent(SpriteRenderer.class);
        if (renderer == null || frames.isEmpty()) return;

        timer += deltaTime;

        if (timer >= frameTime) {
            timer -= frameTime;
            currentFrame = (currentFrame + 1) % frames.size();
            renderer.setSprite(frames.get(currentFrame));
        }
    }
}
