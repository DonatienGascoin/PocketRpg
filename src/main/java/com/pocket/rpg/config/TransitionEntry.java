package com.pocket.rpg.config;

import com.pocket.rpg.rendering.resources.Sprite;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A named luma transition pattern.
 * Associates a display name with a grayscale sprite used as a wipe pattern.
 * <p>
 * The sprite is serialized as an asset path string by the existing
 * asset serialization system (e.g., "sprites/transitions/circle_out.png").
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransitionEntry {

    /**
     * Display name for this transition (e.g., "Circle Out", "Wipe Left").
     */
    private String name = "";

    /**
     * Grayscale sprite asset used as the luma wipe pattern.
     * Black pixels reveal first, white pixels reveal last.
     */
    private Sprite lumaSprite;
}
