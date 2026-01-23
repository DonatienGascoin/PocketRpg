package com.pocket.rpg.audio.mixing;

/**
 * Audio channels for volume control and mixing.
 * Each channel can have independent volume and mute settings.
 */
public enum AudioChannel {
    /**
     * Master channel - affects all audio.
     */
    MASTER,

    /**
     * Background music.
     */
    MUSIC,

    /**
     * Sound effects (explosions, footsteps, etc.).
     */
    SFX,

    /**
     * Character dialogue and voice.
     */
    VOICE,

    /**
     * Environmental/ambient sounds.
     */
    AMBIENT,

    /**
     * UI/menu sounds.
     */
    UI
}
