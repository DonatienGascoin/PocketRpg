package com.pocket.rpg.components.ui;

/**
 * Describes which UITransform fields are driven by a parent component.
 *
 * @param entirelyDriven true if all fields are managed (skip all editing)
 * @param widthDriven    true if width is managed
 * @param heightDriven   true if height is managed
 * @param positionDriven true if position/offset is managed
 * @param label          display label for the driver (e.g. "parent layout", "ScrollView")
 */
public record TransformDriverInfo(
        boolean entirelyDriven,
        boolean widthDriven,
        boolean heightDriven,
        boolean positionDriven,
        String label
) {

    /** Convenience: everything is driven. */
    public static TransformDriverInfo entirelyDriven(String label) {
        return new TransformDriverInfo(true, true, true, true, label);
    }
}
