package com.pocket.rpg.audio;

/**
 * Supplies a base volume for an audio source.
 * The engine multiplies this by the mixer gain each frame.
 */
@FunctionalInterface
public interface VolumeProvider {
    float getVolume();
}
