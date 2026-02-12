package com.pocket.rpg.components.dialogue;

import com.pocket.rpg.components.ui.UICanvas;
import com.pocket.rpg.components.ui.UIImage;
import com.pocket.rpg.components.ui.UIText;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.components.ui.UIVerticalLayoutGroup;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.rendering.resources.Sprite;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.ui.AnchorPreset;
import com.pocket.rpg.ui.text.HorizontalAlignment;
import com.pocket.rpg.ui.text.VerticalAlignment;

/**
 * Programmatic prefab builder for the dialogue UI hierarchy.
 * <p>
 * Creates the full UI tree that {@link PlayerDialogueManager} operates on.
 * All UI elements are registered via {@code componentKey} so the manager
 * can find them through {@link com.pocket.rpg.ui.ComponentKeyRegistry}.
 * <p>
 * Usage:
 * <pre>{@code
 * // In your scene's onLoad():
 * scene.addGameObject(DialogueUIBuilder.build());
 * }</pre>
 * <p>
 * Structure:
 * <pre>
 * UICanvas "DialogueUI" (sortOrder: 10)
 * └── UIImage "DialogueBox"                    (key: "dialogue_box", SLICED)
 *     │  battleDialogue.png, anchor: bottom-left, 100% width, 150px height
 *     │  starts off-screen (offset Y +150), slides up/down via Tween
 *     ├── UIText "DialogueText"                (key: "dialogue_text")
 *     │     wordWrap=true, offset(20,20), 85%w x 75%h
 *     ├── UIText "ContinueIndicator"           (key: "dialogue_continue")
 *     │     small v at bottom-right, blinks when text fully revealed
 *     └── UIImage "ChoicePanel"                (key: "dialogue_choice_panel")
 *         │  dialogueChoiceBg.png, anchor: top-right, 35% width
 *         │  (right-aligned, Pokémon-style choice box)
 *         └── "ChoiceContainer"                (anchor: 0.15,0.10, 80%w x 80%h)
 *             │  inset to avoid unusable left edge of bg image
 *             ├── "Choice0" [25% height]
 *             │   ├── UIText "Arrow0" [20%w]   (key: "dialogue_choice_arrow_0")
 *             │   └── UIText "Text0"  [80%w]   (key: "dialogue_choice_text_0")
 *             ├── "Choice1"  ... (same)
 *             ├── "Choice2"  ... (same)
 *             └── "Choice3"  ... (same)
 * </pre>
 * <p>
 * The ChoicePanel is a child of DialogueBox so it slides with the box
 * during show/hide tweens.
 */
public class DialogueUIBuilder {

    // ========================================================================
    // COMPONENT KEYS
    // ========================================================================

    public static final String KEY_DIALOGUE_BOX = "dialogue_box";
    public static final String KEY_DIALOGUE_TEXT = "dialogue_text";
    public static final String KEY_CONTINUE_INDICATOR = "dialogue_continue";
    public static final String KEY_CHOICE_PANEL = "dialogue_choice_panel";
    public static final String KEY_CHOICE_ARROW_PREFIX = "dialogue_choice_arrow_";
    public static final String KEY_CHOICE_TEXT_PREFIX = "dialogue_choice_text_";

    // ========================================================================
    // LAYOUT CONSTANTS
    // ========================================================================

    public static final int MAX_CHOICES = 4;
    public static final float DIALOGUE_BOX_HEIGHT = 150f;
    public static final float CHOICE_HEIGHT = 40f;
    public static final float CHOICE_SPACING = 5f;
    /** Fraction of the ChoicePanel occupied by the ChoiceContainer (anchor-based inset). */
    public static final float CONTAINER_HEIGHT_FRACTION = 0.8f;
    public static final String DEFAULT_FONT = "fonts/Pokemon-Red.ttf";
    public static final int DEFAULT_FONT_SIZE = 20;
    public static final int ARROW_FONT_SIZE = 16;
    public static final String DIALOGUE_BOX_SPRITE = "sprites/UIs/Battle/battleDialogue.png";
    public static final String CHOICE_PANEL_SPRITE = "sprites/UIs/Battle/dialogueChoiceBg.png";

    private DialogueUIBuilder() {}

    /**
     * Computes the ChoicePanel height needed for the given number of choices.
     * Accounts for the ChoiceContainer inset (80% of panel height) and spacing.
     */
    public static float choicePanelHeight(int choiceCount) {
        float contentHeight = choiceCount * CHOICE_HEIGHT + Math.max(0, choiceCount - 1) * CHOICE_SPACING;
        return contentHeight / CONTAINER_HEIGHT_FRACTION;
    }

    // ========================================================================
    // BUILD
    // ========================================================================

    /**
     * Builds the full dialogue UI hierarchy.
     * Add the returned GameObject to your scene.
     *
     * @return Root GameObject with UICanvas (sortOrder 10)
     */
    public static GameObject build() {
        GameObject root = new GameObject("DialogueUI");
        UITransform rootTransform = new UITransform();
        rootTransform.setMatchParent();
        root.addComponent(rootTransform);
        root.addComponent(new UICanvas(UICanvas.RenderMode.SCREEN_SPACE_OVERLAY, 10));

        GameObject dialogueBox = buildDialogueBox();
        root.addChild(dialogueBox);

        return root;
    }

    // ========================================================================
    // DIALOGUE BOX
    // ========================================================================

    private static GameObject buildDialogueBox() {
        GameObject box = new GameObject("DialogueBox");

        UITransform t = new UITransform();
        t.setAnchor(AnchorPreset.BOTTOM_LEFT);
        t.setPivot(0, 1); // bottom-left of self at anchor
        t.setWidthMode(UITransform.SizeMode.PERCENT);
        t.setWidthPercent(100);
        t.setHeightMode(UITransform.SizeMode.FIXED);
        t.setHeight(DIALOGUE_BOX_HEIGHT);
        t.setOffset(0, DIALOGUE_BOX_HEIGHT); // hidden below screen
        box.addComponent(t);

        UIImage bg = new UIImage(Assets.load(DIALOGUE_BOX_SPRITE, Sprite.class));
        bg.setImageType(UIImage.ImageType.SLICED);
        bg.setComponentKey(KEY_DIALOGUE_BOX);
        box.addComponent(bg);

        box.addChild(buildDialogueText());
        box.addChild(buildContinueIndicator());
        box.addChild(buildChoicePanel());

        return box;
    }

    // ========================================================================
    // DIALOGUE TEXT
    // ========================================================================

    private static GameObject buildDialogueText() {
        GameObject go = new GameObject("DialogueText");

        UITransform t = new UITransform();
        t.setAnchor(0, 0);
        t.setOffset(20, 20);
        t.setWidthMode(UITransform.SizeMode.PERCENT);
        t.setWidthPercent(85);
        t.setHeightMode(UITransform.SizeMode.PERCENT);
        t.setHeightPercent(75);
        go.addComponent(t);

        UIText text = new UIText(DEFAULT_FONT, DEFAULT_FONT_SIZE, "");
        text.setWordWrap(true);
        text.setColor(0, 0, 0, 1);
        text.setComponentKey(KEY_DIALOGUE_TEXT);
        go.addComponent(text);

        return go;
    }

    // ========================================================================
    // CONTINUE INDICATOR
    // ========================================================================

    private static GameObject buildContinueIndicator() {
        GameObject go = new GameObject("ContinueIndicator");

        UITransform t = new UITransform();
        t.setAnchor(AnchorPreset.BOTTOM_RIGHT);
        t.setPivot(1, 1);
        t.setSize(24, 24);
        t.setOffset(-8, -8);
        go.addComponent(t);

        UIText indicator = new UIText(DEFAULT_FONT, ARROW_FONT_SIZE, "v");
        indicator.setColor(0, 0, 0, 0); // hidden initially (alpha 0)
        indicator.setHorizontalAlignment(HorizontalAlignment.CENTER);
        indicator.setVerticalAlignment(VerticalAlignment.MIDDLE);
        indicator.setComponentKey(KEY_CONTINUE_INDICATOR);
        go.addComponent(indicator);

        return go;
    }

    // ========================================================================
    // CHOICE PANEL
    // ========================================================================

    private static GameObject buildChoicePanel() {
        GameObject panel = new GameObject("ChoicePanel");

        UITransform t = new UITransform();
        t.setAnchor(AnchorPreset.TOP_RIGHT); // top-right of dialogue box
        t.setPivot(1, 1);  // bottom-right of self aligns with anchor
        t.setWidthMode(UITransform.SizeMode.PERCENT);
        t.setWidthPercent(35);
        t.setHeightMode(UITransform.SizeMode.FIXED);
        t.setHeight(choicePanelHeight(MAX_CHOICES)); // default for max; resized at runtime
        t.setOffset(-10, 0); // small margin from right edge
        panel.addComponent(t);

        UIImage bg = new UIImage(Assets.load(CHOICE_PANEL_SPRITE, Sprite.class));
        bg.setComponentKey(KEY_CHOICE_PANEL);
        panel.addComponent(bg);

        // Container inside the panel — avoids the unusable left ~15% of the bg image
        // and adds 10% top/bottom padding. VerticalLayoutGroup stacks choices with spacing.
        GameObject container = buildChoiceContainer();
        panel.addChild(container);

        for (int i = 0; i < MAX_CHOICES; i++) {
            container.addChild(buildChoiceSlot(i));
        }

        // NOTE: Do NOT disable here — Scene.registerCachedComponents() skips disabled
        // GOs, so componentKeys would never be registered. PlayerDialogueManager.resolveUI()
        // hides the panel after resolving references.

        return panel;
    }

    private static GameObject buildChoiceContainer() {
        GameObject container = new GameObject("ChoiceContainer");

        UITransform t = new UITransform();
        t.setAnchor(0, 0.10f); // top 10%
        t.setOffset(16, 0);
        t.setWidthMode(UITransform.SizeMode.PERCENT);
        t.setWidthPercent(75);
        t.setHeightMode(UITransform.SizeMode.PERCENT);
        t.setHeightPercent(80); // 80% height (10% top + 10% bottom padding)
        container.addComponent(t);

        UIVerticalLayoutGroup layout = new UIVerticalLayoutGroup();
        layout.setSpacing(CHOICE_SPACING);
        layout.setChildForceExpandWidth(true);
        container.addComponent(layout);

        return container;
    }

    // ========================================================================
    // CHOICE SLOT
    // ========================================================================

    private static GameObject buildChoiceSlot(int index) {
        GameObject slot = new GameObject("Choice" + index);

        UITransform t = new UITransform();
        t.setAnchor(0, 0);
        t.setWidthMode(UITransform.SizeMode.PERCENT);
        t.setWidthPercent(100);
        t.setHeightMode(UITransform.SizeMode.FIXED);
        t.setHeight(CHOICE_HEIGHT);
        slot.addComponent(t);

        // Arrow indicator (20% width)
        slot.addChild(buildChoiceArrow(index));

        // Choice text (80% width)
        slot.addChild(buildChoiceText(index));

        return slot;
    }

    private static GameObject buildChoiceArrow(int index) {
        GameObject go = new GameObject("Arrow" + index);

        UITransform t = new UITransform();
        t.setAnchor(0, 0);
        t.setWidthMode(UITransform.SizeMode.PERCENT);
        t.setWidthPercent(20);
        t.setHeightMode(UITransform.SizeMode.PERCENT);
        t.setHeightPercent(100);
        go.addComponent(t);

        UIText arrow = new UIText(DEFAULT_FONT, 25, "<");
        arrow.setColor(1, 1, 1, 1);
        arrow.setHorizontalAlignment(HorizontalAlignment.CENTER);
        arrow.setVerticalAlignment(VerticalAlignment.MIDDLE);
        arrow.setComponentKey(KEY_CHOICE_ARROW_PREFIX + index);
        go.addComponent(arrow);

        return go;
    }

    private static GameObject buildChoiceText(int index) {
        GameObject go = new GameObject("Text" + index);

        UITransform t = new UITransform();
        t.setAnchor(0.2f, 0); // starts after the 20% arrow
        t.setWidthMode(UITransform.SizeMode.PERCENT);
        t.setWidthPercent(80);
        t.setHeightMode(UITransform.SizeMode.PERCENT);
        t.setHeightPercent(100);
        go.addComponent(t);

        UIText text = new UIText(DEFAULT_FONT, DEFAULT_FONT_SIZE, "");
        text.setWordWrap(true);
        text.setColor(1, 1, 1, 1);
        text.setVerticalAlignment(VerticalAlignment.MIDDLE);
        text.setComponentKey(KEY_CHOICE_TEXT_PREFIX + index);
        go.addComponent(text);

        return go;
    }
}
