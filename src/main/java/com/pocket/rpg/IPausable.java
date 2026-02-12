package com.pocket.rpg;

/**
 * Interface for components that should freeze during dialogue or other pause scenarios.
 * <p>
 * Components implement this to define their own freeze/resume behavior.
 * The component stays enabled (no lifecycle side effects) — it simply
 * stops processing in its update loop.
 */
public interface IPausable {
    void onPause();
    void onResume();
}
