package com.pocket.rpg.animation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimationPlayerTest {

    private AnimationPlayer player;
    private Animation animation;

    @BeforeEach
    void setUp() {
        player = new AnimationPlayer();

        // Create a test animation with 3 frames, 0.1s each
        animation = new Animation("test");
        animation.addFrame(new AnimationFrame("sprite1.png", 0.1f));
        animation.addFrame(new AnimationFrame("sprite2.png", 0.1f));
        animation.addFrame(new AnimationFrame("sprite3.png", 0.1f));
        animation.setLooping(true);
    }

    @Test
    void testInitialState() {
        assertEquals(AnimationPlayer.PlaybackState.STOPPED, player.getState());
        assertEquals(0, player.getCurrentFrame());
        assertEquals(0, player.getTimer(), 0.001f);
        assertFalse(player.hasAnimation());
    }

    @Test
    void testSetAnimation() {
        player.setAnimation(animation);

        assertEquals(AnimationPlayer.PlaybackState.PLAYING, player.getState());
        assertEquals(0, player.getCurrentFrame());
        assertTrue(player.hasAnimation());
        assertSame(animation, player.getAnimation());
    }

    @Test
    void testSetAnimationWithoutPlaying() {
        player.setAnimationWithoutPlaying(animation);

        assertEquals(AnimationPlayer.PlaybackState.STOPPED, player.getState());
        assertEquals(0, player.getCurrentFrame());
        assertTrue(player.hasAnimation());
    }

    @Test
    void testPlay() {
        player.setAnimationWithoutPlaying(animation);
        player.play();

        assertEquals(AnimationPlayer.PlaybackState.PLAYING, player.getState());
        assertTrue(player.isPlaying());
    }

    @Test
    void testPause() {
        player.setAnimation(animation);
        player.pause();

        assertEquals(AnimationPlayer.PlaybackState.PAUSED, player.getState());
        assertTrue(player.isPaused());
        assertFalse(player.isPlaying());
    }

    @Test
    void testResume() {
        player.setAnimation(animation);
        player.pause();
        player.resume();

        assertEquals(AnimationPlayer.PlaybackState.PLAYING, player.getState());
        assertTrue(player.isPlaying());
    }

    @Test
    void testStop() {
        player.setAnimation(animation);
        player.update(0.15f); // Advance to frame 2
        player.stop();

        assertEquals(AnimationPlayer.PlaybackState.STOPPED, player.getState());
        assertEquals(0, player.getCurrentFrame());
        assertEquals(0, player.getTimer(), 0.001f);
        assertTrue(player.isStopped());
    }

    @Test
    void testUpdateAdvancesFrame() {
        player.setAnimation(animation);

        // Update less than frame duration - should stay on frame 0
        boolean changed = player.update(0.05f);
        assertFalse(changed);
        assertEquals(0, player.getCurrentFrame());

        // Update to exceed frame duration - should advance to frame 1
        changed = player.update(0.06f);
        assertTrue(changed);
        assertEquals(1, player.getCurrentFrame());
    }

    @Test
    void testUpdateLooping() {
        player.setAnimation(animation);

        // Advance past all frames (total 0.3s)
        player.update(0.35f);

        // Should loop back
        assertTrue(player.getCurrentFrame() < animation.getFrameCount());
        assertEquals(AnimationPlayer.PlaybackState.PLAYING, player.getState());
    }

    @Test
    void testUpdateNonLooping() {
        animation.setLooping(false);
        player.setAnimation(animation);

        // Advance past all frames
        player.update(0.5f);

        // Should stop on last frame
        assertEquals(animation.getFrameCount() - 1, player.getCurrentFrame());
        assertEquals(AnimationPlayer.PlaybackState.FINISHED, player.getState());
        assertTrue(player.isFinished());
    }

    @Test
    void testUpdateWhilePaused() {
        player.setAnimation(animation);
        player.pause();

        int frameBefore = player.getCurrentFrame();
        float timerBefore = player.getTimer();

        player.update(0.2f);

        assertEquals(frameBefore, player.getCurrentFrame());
        assertEquals(timerBefore, player.getTimer(), 0.001f);
    }

    @Test
    void testSpeed() {
        player.setAnimation(animation);
        player.setSpeed(2.0f);

        // At 2x speed, 0.05s should advance like 0.1s
        player.update(0.05f);
        assertEquals(1, player.getCurrentFrame());
    }

    @Test
    void testProgress() {
        player.setAnimation(animation);

        assertEquals(0f, player.getProgress(), 0.01f);

        player.update(0.15f); // Halfway through frame 2

        float expectedProgress = 0.15f / 0.3f; // 0.5
        assertEquals(expectedProgress, player.getProgress(), 0.01f);
    }

    @Test
    void testRestart() {
        player.setAnimation(animation);
        player.update(0.2f);
        player.restart();

        assertEquals(0, player.getCurrentFrame());
        assertEquals(0, player.getTimer(), 0.001f);
        assertEquals(AnimationPlayer.PlaybackState.PLAYING, player.getState());
    }

    @Test
    void testPlayWithNoAnimation() {
        player.play(); // Should not crash
        assertEquals(AnimationPlayer.PlaybackState.STOPPED, player.getState());
    }

    @Test
    void testUpdateWithNoAnimation() {
        boolean changed = player.update(0.1f);
        assertFalse(changed);
    }
}
