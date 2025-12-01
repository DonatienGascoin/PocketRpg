package com.pocket.rpg.input;

/**
 * Platform-independent gamepad button identifiers.
 * Based on the standard gamepad layout (Xbox/PlayStation style).
 */
public enum GamepadButton {
    // Face buttons
    A,              // Xbox A / PlayStation Cross
    B,              // Xbox B / PlayStation Circle
    X,              // Xbox X / PlayStation Square
    Y,              // Xbox Y / PlayStation Triangle

    // Shoulder buttons
    LEFT_SHOULDER,  // LB / L1
    RIGHT_SHOULDER, // RB / R1

    // Triggers (as buttons)
    LEFT_TRIGGER,   // LT / L2
    RIGHT_TRIGGER,  // RT / R2

    // D-Pad
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,

    // Special buttons
    START,
    BACK,           // Select / Share
    GUIDE,          // Xbox button / PS button

    // Stick clicks
    LEFT_STICK_BUTTON,   // L3
    RIGHT_STICK_BUTTON,  // R3

    UNKNOWN
}