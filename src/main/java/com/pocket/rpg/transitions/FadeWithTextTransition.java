package com.pocket.rpg.transitions;

import com.pocket.rpg.config.TransitionConfig;
import com.pocket.rpg.rendering.OverlayRenderer;
import org.joml.Vector4f;

/**
 * Fade transition with text overlay.
 * Displays text during the transition (e.g., "Loading...").
 * <p>
 * Note: Text rendering requires a text rendering system.
 * This is a placeholder implementation that will be enhanced
 * once text rendering is available.
 */
public class FadeWithTextTransition extends FadeTransition {

    private final String transitionText;

    /**
     * Creates a fade transition with text from configuration.
     *
     * @param config transition configuration
     */
    public FadeWithTextTransition(TransitionConfig config) {
        super(config);
        this.transitionText = config.getTransitionText();
    }

    /**
     * Creates a fade transition with text and specific parameters.
     *
     * @param fadeOutDuration duration of fade out in seconds
     * @param fadeInDuration  duration of fade in in seconds
     * @param fadeColor       color to fade to (RGBA)
     * @param text            text to display during transition
     */
    public FadeWithTextTransition(float fadeOutDuration, float fadeInDuration,
                                  Vector4f fadeColor, String text) {
        super(fadeOutDuration, fadeInDuration, fadeColor);
        this.transitionText = text;
    }

    @Override
    public void render(OverlayRenderer overlayRenderer) {
        // Render the fade overlay
        super.render(overlayRenderer);

        // Render text if we're at significant alpha and text exists
        if (alpha > 0.3f && transitionText != null && !transitionText.isEmpty()) {
            renderText(overlayRenderer);
        }
    }

    /**
     * Renders the transition text.
     * TODO: Implement once text rendering system is available.
     *
     * @param overlayRenderer the overlay renderer to use
     */
    private void renderText(OverlayRenderer overlayRenderer) {
        // Placeholder for text rendering
        // Once text rendering is implemented, this would render centered text
        // For now, we just log it once for debugging
        if (alpha > 0.9f && !midpointReached) {
            System.out.println("Transition Text: " + transitionText);
        }

        // Future implementation would look like:
        // float textAlpha = Math.min(1.0f, alpha * 1.5f); // Text fades in faster
        // Vector4f textColor = new Vector4f(1, 1, 1, textAlpha); // White text
        // textRenderer.drawText(transitionText, centerX, centerY, textColor, TextAlign.CENTER);
    }

    /**
     * Gets the transition text.
     *
     * @return the text displayed during transition
     */
    public String getTransitionText() {
        return transitionText;
    }
}