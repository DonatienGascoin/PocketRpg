package com.pocket.rpg.components.dialogue;

import com.pocket.rpg.IPausable;
import com.pocket.rpg.animation.tween.Ease;
import com.pocket.rpg.animation.tween.TweenManager;
import com.pocket.rpg.animation.tween.Tweens;
import com.pocket.rpg.collision.Direction;
import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.player.InputMode;
import com.pocket.rpg.components.player.PlayerInput;
import com.pocket.rpg.components.ui.UIText;
import com.pocket.rpg.components.ui.UITransform;
import com.pocket.rpg.core.GameObject;
import com.pocket.rpg.dialogue.*;
import com.pocket.rpg.logging.Log;
import com.pocket.rpg.logging.Logger;
import com.pocket.rpg.scenes.Scene;
import com.pocket.rpg.ui.ComponentKeyRegistry;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central orchestrator for the dialogue system.
 * <p>
 * Placed on the player GameObject. Coordinates dialogue flow: starting/ending
 * conversations, advancing through entries, handling input, and dispatching events.
 * UI references are resolved from {@link ComponentKeyRegistry} and null-safe guarded,
 * so the manager works correctly even without a UI hierarchy in the scene.
 * <p>
 * <b>Planned refactoring:</b> This component currently owns several concerns that
 * should be extracted into focused helpers once the feature is stable:
 * <ul>
 *   <li>{@code DialogueTypewriter} — Encapsulates the typewriter effect state machine
 *       (charTimer, visibleChars, charsPerSecond, fullText, textFullyRevealed).
 *       The manager would call {@code typewriter.update(dt)}, {@code typewriter.skipToEnd()},
 *       and {@code typewriter.setText(text)}.</li>
 *   <li>{@code DialogueTextResolver} — Static utility for {@code [VAR_NAME]} tag substitution.
 *       Pure function: takes text + variable map, returns resolved text. No component state needed.</li>
 *   <li>{@code DialogueEventDispatcher} — Routes built-in vs custom events, performs scene
 *       query for listeners, calls {@code DialogueEventStore.markFired()}.
 *       The manager would call {@code dispatcher.dispatch(eventRef, scene)}.</li>
 * </ul>
 * What stays here: state machine transitions (start/end/advance/chain), input routing,
 * choice navigation, and pause/resume orchestration.
 * <p>
 * Design ref: §4 — PlayerDialogueManager Component
 */
@ComponentMeta(category = "Dialogue")
public class PlayerDialogueManager extends Component {

    private static final Logger LOG = Log.getLogger(PlayerDialogueManager.class);
    private static final Pattern VARIABLE_TAG = Pattern.compile("\\[([A-Z_][A-Z0-9_]*)\\]");

    // ========================================================================
    // SERIALIZED FIELDS
    // ========================================================================

    /** Typewriter speed (characters per second). */
    @Getter @Setter
    private float charsPerSecond = 30f;

    /** Duration of the slide-in / slide-out tween in seconds. */
    @Getter @Setter
    private float slideDuration = 0.3f;

    // ========================================================================
    // UI REFERENCES (resolved from ComponentKeyRegistry, all null-safe)
    // ========================================================================

    private transient UITransform dialogueBoxTransform;
    private transient UIText dialogueText;
    private transient UIText continueIndicator;
    private transient GameObject choicePanelGo;
    private transient UITransform choicePanelTransform;
    private transient UIText[] choiceArrows;
    private transient UIText[] choiceTexts;

    /** True once UI references have been resolved (lazy, first-use). */
    private transient boolean uiResolved;

    // ========================================================================
    // CONTINUE INDICATOR BLINK
    // ========================================================================

    private static final float BLINK_INTERVAL = 0.5f;
    private transient float continueBlinkTimer;
    private transient boolean continueVisible;

    // ========================================================================
    // TRANSIENT STATE
    // ========================================================================

    @Getter
    private transient DialogueVariableResolver variableResolver;

    @Getter
    private transient Dialogue currentDialogue;
    private transient int currentEntryIndex;
    private transient Map<String, String> currentVariables;

    private transient boolean active;
    @Getter
    private transient int selectedChoice;
    private transient int visibleChars;
    private transient String fullText;
    private transient float charTimer;

    /** True when the current line's text is fully revealed. */
    @Getter
    private transient boolean textFullyRevealed;

    /** True when showing a choice group (waiting for player selection). */
    @Getter
    private transient boolean showingChoices;

    /** The PlayerInput on the same GameObject. */
    private transient PlayerInput playerInput;

    /** The originating DialogueInteractable for the current conversation (set externally). */
    @Getter @Setter
    private transient DialogueInteractable sourceComponent;

    /** Listeners notified on custom events. Set externally for testing or by scene query. */
    private transient List<DialogueEventListenerCallback> eventListeners;

    /** Flag set by the DIALOGUE-mode interact callback, consumed each frame. */
    private transient boolean interactRequested;

    /** True while the dialogue box slide-in animation is playing. Blocks update() processing. */
    private transient boolean waitingForSlide;

    /** True while the dialogue box slide-out animation is playing. Blocks new interactions. */
    private transient boolean slidingOut;

    // ========================================================================
    // LIFECYCLE
    // ========================================================================

    @Override
    protected void onStart() {
        playerInput = getComponent(PlayerInput.class);
        variableResolver = new DialogueVariableResolver();
        eventListeners = new ArrayList<>();

        // Register DIALOGUE-mode interact callback on PlayerInput.
        // The callback sets a flag consumed in update() — this decouples
        // interact detection from the movement direction polling.
        if (playerInput != null) {
            playerInput.onInteract(InputMode.DIALOGUE, () -> interactRequested = true);
        }

        // Auto variable registration happens here as systems are built.
        // For now, no auto variables are registered (will be added when
        // Inventory, ProgressTracker, etc. exist).

        resolveUI();
    }

    /**
     * Resolves UI element references from {@link ComponentKeyRegistry}.
     * Called once in onStart. All references are null-safe guarded in usage.
     */
    private void resolveUI() {
        if (uiResolved) return;
        uiResolved = true;

        dialogueBoxTransform = ComponentKeyRegistry.getUITransform(DialogueUIBuilder.KEY_DIALOGUE_BOX);
        dialogueText = ComponentKeyRegistry.getText(DialogueUIBuilder.KEY_DIALOGUE_TEXT);
        continueIndicator = ComponentKeyRegistry.getText(DialogueUIBuilder.KEY_CONTINUE_INDICATOR);

        // Choice panel: find the GameObject that owns the image component
        var panelComponent = ComponentKeyRegistry.getImage(DialogueUIBuilder.KEY_CHOICE_PANEL);
        choicePanelGo = panelComponent != null ? panelComponent.getGameObject() : null;
        choicePanelTransform = choicePanelGo != null ? choicePanelGo.getComponent(UITransform.class) : null;

        choiceArrows = new UIText[DialogueUIBuilder.MAX_CHOICES];
        choiceTexts = new UIText[DialogueUIBuilder.MAX_CHOICES];
        for (int i = 0; i < DialogueUIBuilder.MAX_CHOICES; i++) {
            choiceArrows[i] = ComponentKeyRegistry.getText(DialogueUIBuilder.KEY_CHOICE_ARROW_PREFIX + i);
            choiceTexts[i] = ComponentKeyRegistry.getText(DialogueUIBuilder.KEY_CHOICE_TEXT_PREFIX + i);
        }

        // Hide choice panel now that references are resolved
        // (it's kept active in the scene so registerCachedComponents registers its keys)
        hideChoicePanel();
    }

    // ========================================================================
    // START DIALOGUE
    // ========================================================================

    /**
     * Convenience overload for simple NPC conversations.
     * Only static variables — no runtime context needed.
     */
    public void startDialogue(Dialogue dialogue, Map<String, String> staticVars) {
        startDialogue(dialogue, staticVars, Map.of());
    }

    /**
     * Full entry point for starting or chaining a dialogue.
     * <p>
     * When called externally (isActive == false): sets up the environment
     * (pause IPausable, switch to DIALOGUE mode).
     * When called internally for chaining (isActive == true): just resets
     * dialogue state, no pause/mode switch.
     */
    public void startDialogue(Dialogue dialogue, Map<String, String> staticVars,
                              Map<String, String> runtimeVars) {
        // --- Runtime validation ---
        if (dialogue == null) {
            LOG.error("startDialogue() called with null dialogue");
            if (active) endDialogue();
            return;
        }
        if (dialogue.getEntries().isEmpty()) {
            LOG.error("Dialogue '%s' has no entries", dialogue.getName());
            if (active) endDialogue();
            return;
        }

        boolean isChain = active;

        if (!isChain) {
            // Fresh conversation — set up environment
            active = true;
            if (playerInput != null) {
                playerInput.setMode(InputMode.DIALOGUE);
            }
            pauseAll();
        }

        // Reset dialogue state (both fresh and chain)
        currentDialogue = dialogue;
        currentEntryIndex = 0;
        showingChoices = false;
        selectedChoice = 0;
        textFullyRevealed = false;
        interactRequested = false; // consume any stale interact from the same button press

        // Merge variables: AUTO → STATIC → RUNTIME
        Map<String, String> autoVars = variableResolver != null
                ? variableResolver.resolveAutoVariables()
                : Map.of();
        currentVariables = DialogueVariableResolver.mergeVariables(autoVars, staticVars, runtimeVars);

        if (!isChain) {
            // Fresh conversation — block update() during slide, then show first entry
            waitingForSlide = true;
            showDialogueBox(() -> {
                waitingForSlide = false;
                interactRequested = false; // discard any interact pressed during slide
                showEntry(currentDialogue.getEntries().get(0));
            });
        } else {
            // Chain — box already visible, show entry immediately
            showEntry(currentDialogue.getEntries().get(0));
        }
    }

    // ========================================================================
    // END DIALOGUE
    // ========================================================================

    /**
     * Ends the current dialogue conversation. Resumes IPausable components,
     * restores OVERWORLD input mode, hides UI, and dispatches the originating
     * DialogueInteractable's onConversationEnd event if set.
     */
    public void endDialogue() {
        if (!active) return;

        // Capture source before clearing state
        DialogueInteractable source = sourceComponent;

        active = false;
        waitingForSlide = false;
        showingChoices = false;
        currentDialogue = null;
        currentVariables = null;
        fullText = null;
        sourceComponent = null;

        // Hide UI — kill any active box tween first (e.g. a show tween still animating)
        // to prevent its onComplete from firing after state is cleared
        killBoxTween();
        clearDialogueText();
        hideChoicePanel();
        hideContinueIndicator();

        // Slide the box out, then re-enable game input once fully hidden
        slidingOut = true;
        hideDialogueBox(() -> {
            slidingOut = false;
            if (playerInput != null) {
                playerInput.setMode(InputMode.OVERWORLD);
            }
            resumeAll();
        });

        // Dispatch onConversationEnd AFTER clearing state,
        // so any handler (e.g. START_BATTLE) runs in a clean context
        if (source != null && source.getOnConversationEnd() != null) {
            dispatchEvent(source.getOnConversationEnd());
        }
    }

    // ========================================================================
    // UPDATE — TYPEWRITER + INPUT
    // ========================================================================

    @Override
    public void update(float deltaTime) {
        if (!active || waitingForSlide) return;

        // Typewriter text reveal (only when showing a line, not choices)
        if (!showingChoices) {
            updateTextReveal(deltaTime);
        }

        // Continue indicator blink (only when text fully revealed, not during choices)
        if (textFullyRevealed && !showingChoices) {
            updateContinueIndicatorBlink(deltaTime);
        }

        // Choice navigation (UP/DOWN movement — polled every frame)
        if (showingChoices) {
            updateChoiceNavigation();
        }

        // Process interact (set by callback, consumed here)
        if (interactRequested) {
            interactRequested = false;
            handleInteract();
        }
    }

    private void updateTextReveal(float deltaTime) {
        if (fullText == null) return;

        if (visibleChars < fullText.length()) {
            charTimer += deltaTime;
            int newChars = (int) (charTimer * charsPerSecond);
            if (newChars > 0) {
                visibleChars = Math.min(visibleChars + newChars, fullText.length());
                charTimer -= newChars / charsPerSecond;
                textFullyRevealed = (visibleChars >= fullText.length());
                updateUIText();
                if (textFullyRevealed) {
                    if (isNextEntryChoices()) {
                        // Auto-advance to choices without waiting for a click
                        advanceToNextEntry();
                    } else {
                        showContinueIndicator();
                    }
                }
            }
        }
    }

    private void updateChoiceNavigation() {
        if (playerInput == null) return;

        // Navigate choices on key-up (edge-triggered, one move per press)
        Direction dir = playerInput.getMovementDirectionUp();
        if (dir != null) {
            int choiceCount = getActiveChoiceCount();
            if (dir == Direction.UP && selectedChoice > 0) {
                selectedChoice--;
                updateChoiceArrows(choiceCount);
            } else if (dir == Direction.DOWN && selectedChoice < choiceCount - 1) {
                selectedChoice++;
                updateChoiceArrows(choiceCount);
            }
        }
    }

    /**
     * Handles an interact press. Called via callback flag, not polled.
     */
    private void handleInteract() {
        if (showingChoices) {
            executeSelectedChoice();
        } else if (!textFullyRevealed) {
            // Text still revealing — check if it's actually already fully visible
            boolean alreadyFullyVisible = fullText != null && visibleChars >= fullText.length();
            if (alreadyFullyVisible) {
                // Flag was stale — advance directly
                textFullyRevealed = true;
                advanceToNextEntry();
            } else {
                // Skip to full text
                visibleChars = fullText != null ? fullText.length() : 0;
                textFullyRevealed = true;
                updateUIText();
                if (isNextEntryChoices()) {
                    // Auto-advance to choices without waiting for another click
                    advanceToNextEntry();
                } else {
                    showContinueIndicator();
                }
            }
        } else {
            advanceToNextEntry();
        }
    }

    // ========================================================================
    // ENTRY NAVIGATION
    // ========================================================================

    private void showEntry(DialogueEntry entry) {
        switch (entry) {
            case DialogueLine line -> showLine(line);
            case DialogueChoiceGroup group -> showChoiceGroup(group);
        }
    }

    private void showLine(DialogueLine line) {
        showingChoices = false;
        fullText = substituteVariables(line.getText());
        visibleChars = 0;
        charTimer = 0f;
        textFullyRevealed = false;

        // Reset UI for new line
        hideChoicePanel();
        hideContinueIndicator();
        updateUIText();
    }

    private void showChoiceGroup(DialogueChoiceGroup group) {
        // Validate: hasChoices=true with empty list → skip
        if (!group.isHasChoices() || group.getChoices().isEmpty()) {
            if (group.isHasChoices() && group.getChoices().isEmpty()) {
                LOG.warn("Dialogue '%s' has hasChoices=true with empty choices list — ending dialogue",
                        currentDialogue != null ? currentDialogue.getName() : "?");
            }
            endDialogue();
            return;
        }

        showingChoices = true;
        selectedChoice = 0;

        // Show choice panel with the available choices
        hideContinueIndicator();
        showChoicePanelUI(group);
    }

    /**
     * Returns true if the next dialogue entry is a choice group.
     * Used to auto-advance from the last LINE to CHOICES without an extra click.
     */
    private boolean isNextEntryChoices() {
        if (currentDialogue == null) return false;
        int nextIndex = currentEntryIndex + 1;
        if (nextIndex >= currentDialogue.getEntries().size()) return false;
        return currentDialogue.getEntries().get(nextIndex) instanceof DialogueChoiceGroup;
    }

    private void advanceToNextEntry() {
        if (currentDialogue == null) return;

        // Dispatch onCompleteEvent for the line we just finished
        DialogueEntry currentEntry = currentDialogue.getEntries().get(currentEntryIndex);
        if (currentEntry instanceof DialogueLine line && line.getOnCompleteEvent() != null) {
            dispatchEvent(line.getOnCompleteEvent());
        }

        currentEntryIndex++;

        if (currentEntryIndex < currentDialogue.getEntries().size()) {
            showEntry(currentDialogue.getEntries().get(currentEntryIndex));
        } else {
            endDialogue();
        }
    }

    // ========================================================================
    // CHOICE EXECUTION
    // ========================================================================

    private void executeSelectedChoice() {
        if (currentDialogue == null) return;

        DialogueEntry lastEntry = currentDialogue.getEntries().get(currentEntryIndex);
        if (!(lastEntry instanceof DialogueChoiceGroup group)) return;

        List<Choice> choices = group.getChoices();
        if (selectedChoice < 0 || selectedChoice >= choices.size()) return;

        ChoiceAction action = choices.get(selectedChoice).getAction();
        if (action == null || action.getType() == null) {
            endDialogue();
            return;
        }

        switch (action.getType()) {
            case DIALOGUE -> {
                Dialogue target = action.getDialogue();
                if (target == null || target.getEntries().isEmpty()) {
                    LOG.error("Choice action references missing/empty dialogue: %s",
                            action.getDialoguePath());
                    endDialogue();
                    return;
                }
                // Internal chain — no endDialogue, no resume, no mode switch
                startDialogue(target, currentVariables);
            }
            case BUILT_IN_EVENT -> {
                if (action.getBuiltInEvent() != null) {
                    dispatchEvent(DialogueEventRef.builtIn(action.getBuiltInEvent()));
                }
                endDialogue();
            }
            case CUSTOM_EVENT -> {
                if (action.getCustomEvent() != null && !action.getCustomEvent().isBlank()) {
                    dispatchEvent(DialogueEventRef.custom(action.getCustomEvent()));
                }
                endDialogue();
            }
        }
    }

    private int getActiveChoiceCount() {
        if (currentDialogue == null) return 0;
        DialogueEntry entry = currentDialogue.getEntries().get(currentEntryIndex);
        if (entry instanceof DialogueChoiceGroup group) {
            return Math.min(group.getChoices().size(), DialogueChoiceGroup.MAX_CHOICES);
        }
        return 0;
    }

    // ========================================================================
    // EVENT DISPATCH
    // ========================================================================

    /**
     * Dispatches a dialogue event (built-in or custom).
     */
    private void dispatchEvent(DialogueEventRef eventRef) {
        if (eventRef == null) return;

        if (eventRef.isBuiltIn()) {
            handleBuiltInEvent(eventRef.getBuiltInEvent());
        } else if (eventRef.isCustom()) {
            String eventName = eventRef.getCustomEvent();
            if (eventName != null && !eventName.isBlank()) {
                // Dispatch to registered listeners
                for (DialogueEventListenerCallback listener : eventListeners) {
                    if (eventName.equals(listener.eventName())) {
                        listener.callback().run();
                    }
                }

                // Also dispatch to scene-level listeners
                dispatchToSceneListeners(eventName);

                // Persist for cross-scene support
                DialogueEventStore.markFired(eventName);
            }
        }
    }

    private void handleBuiltInEvent(DialogueEvent event) {
        if (event == null) return;
        switch (event) {
            case END_CONVERSATION -> {
                // endDialogue() is called by the caller (choice execution)
                // so this is a no-op — the built-in event itself doesn't need extra handling
            }
        }
    }

    /**
     * Dispatches a custom event to scene-level listeners via scene query.
     */
    private void dispatchToSceneListeners(String eventName) {
        if (gameObject == null || gameObject.getScene() == null) return;

        Scene scene = gameObject.getScene();
        for (DialogueEventListener listener : scene.getComponentsImplementing(DialogueEventListener.class)) {
            if (eventName.equals(listener.getEventName())) {
                listener.onDialogueEvent();
            }
        }
    }

    // ========================================================================
    // UI — DIALOGUE BOX SHOW / HIDE
    // ========================================================================

    /**
     * Slides the dialogue box up from below the screen.
     *
     * @param onComplete callback invoked after the slide-in finishes
     */
    private void showDialogueBox(Runnable onComplete) {
        if (dialogueBoxTransform == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        // Kill any competing hide tween and clear stale text before sliding up
        killBoxTween();
        clearDialogueText();
        Tweens.offsetY(dialogueBoxTransform, 0f, slideDuration)
                .setEase(Ease.OUT_QUAD)
                .onComplete(onComplete);
    }

    /**
     * Slides the dialogue box down below the screen.
     *
     * @param onComplete callback invoked after the slide-out finishes
     */
    private void hideDialogueBox(Runnable onComplete) {
        if (dialogueBoxTransform == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        Tweens.offsetY(dialogueBoxTransform, DialogueUIBuilder.DIALOGUE_BOX_HEIGHT, slideDuration)
                .setEase(Ease.IN_QUAD)
                .onComplete(onComplete);
    }

    /**
     * Kills any active tween on the dialogue box transform (without completing it).
     * Prevents stale onComplete callbacks from firing after state is cleared.
     */
    private void killBoxTween() {
        if (dialogueBoxTransform != null) {
            TweenManager.kill(dialogueBoxTransform, false);
        }
    }

    /**
     * Clears the dialogue text UI so stale text is not visible during the next slide-in.
     */
    private void clearDialogueText() {
        if (dialogueText != null) {
            dialogueText.setText("");
        }
    }

    // ========================================================================
    // UI — TEXT DISPLAY
    // ========================================================================

    /**
     * Updates the dialogue UIText with the currently visible portion of text.
     */
    private void updateUIText() {
        if (dialogueText == null) return;
        dialogueText.setText(getVisibleText());
    }

    // ========================================================================
    // UI — CONTINUE INDICATOR
    // ========================================================================

    private void showContinueIndicator() {
        continueBlinkTimer = 0f;
        continueVisible = true;
        if (continueIndicator != null) {
            continueIndicator.setAlpha(1f);
        }
    }

    private void hideContinueIndicator() {
        continueVisible = false;
        continueBlinkTimer = 0f;
        if (continueIndicator != null) {
            continueIndicator.setAlpha(0f);
        }
    }

    private void updateContinueIndicatorBlink(float deltaTime) {
        continueBlinkTimer += deltaTime;
        if (continueBlinkTimer >= BLINK_INTERVAL) {
            continueBlinkTimer -= BLINK_INTERVAL;
            continueVisible = !continueVisible;
            if (continueIndicator != null) {
                continueIndicator.setAlpha(continueVisible ? 1f : 0f);
            }
        }
    }

    // ========================================================================
    // UI — CHOICE PANEL
    // ========================================================================

    /**
     * Shows the choice panel with the given choice group's choices.
     * Sets text on each slot, hides unused slots, and shows the arrow on selectedChoice.
     */
    private void showChoicePanelUI(DialogueChoiceGroup group) {
        if (choicePanelGo == null) {
            LOG.error("[showChoicePanelUI] choicePanelGo is NULL — choices will not render!");
            // Fallback: show choices in the dialogue text so the user can still see them
            if (dialogueText != null) {
                StringBuilder sb = new StringBuilder("Choose:\n");
                for (int i = 0; i < group.getChoices().size(); i++) {
                    sb.append(i == selectedChoice ? "> " : "  ");
                    sb.append(group.getChoices().get(i).getText()).append("\n");
                }
                dialogueText.setText(sb.toString());
            }
            return;
        }

        List<Choice> choices = group.getChoices();
        int count = Math.min(choices.size(), DialogueUIBuilder.MAX_CHOICES);

        // Set text and show/hide each slot
        for (int i = 0; i < DialogueUIBuilder.MAX_CHOICES; i++) {
            if (i < count) {
                if (choiceTexts[i] != null) {
                    String choiceLabel = substituteVariables(choices.get(i).getText());
                    choiceTexts[i].setText(choiceLabel);
                }
                // Show slot
                if (choiceTexts[i] != null && choiceTexts[i].getGameObject() != null) {
                    choiceTexts[i].getGameObject().getParent().setEnabled(true);
                }
            } else {
                // Hide unused slot
                if (choiceTexts[i] != null && choiceTexts[i].getGameObject() != null) {
                    choiceTexts[i].getGameObject().getParent().setEnabled(false);
                }
            }
        }

        // Resize panel height to fit the actual number of choices
        if (choicePanelTransform != null) {
            choicePanelTransform.setHeight(DialogueUIBuilder.choicePanelHeight(count));
        }

        updateChoiceArrows(count);
        choicePanelGo.setEnabled(true);
    }

    /**
     * Hides the choice panel.
     */
    private void hideChoicePanel() {
        if (choicePanelGo != null) {
            choicePanelGo.setEnabled(false);
        }
    }

    /**
     * Updates arrow visibility: shows the arrow on the selected choice,
     * hides arrows on all others.
     */
    private void updateChoiceArrows(int choiceCount) {
        if (choiceArrows == null) return;
        for (int i = 0; i < DialogueUIBuilder.MAX_CHOICES; i++) {
            if (choiceArrows[i] != null) {
                choiceArrows[i].setAlpha(i == selectedChoice && i < choiceCount ? 1f : 0f);
            }
        }
    }

    // ========================================================================
    // VARIABLE SUBSTITUTION
    // ========================================================================

    /**
     * Replaces [VAR_NAME] tags in text with values from the current variable map.
     * Unknown tags stay literal and a warning is logged.
     */
    String substituteVariables(String text) {
        if (text == null || text.isEmpty() || currentVariables == null) return text;

        Matcher matcher = VARIABLE_TAG.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = currentVariables.get(varName);
            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else {
                LOG.warn("Dialogue '%s' — variable '%s' not set",
                        currentDialogue != null ? currentDialogue.getName() : "?", varName);
                // Leave the tag literal
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    // ========================================================================
    // PAUSE / RESUME
    // ========================================================================

    private void pauseAll() {
        if (gameObject == null || gameObject.getScene() == null) return;
        for (IPausable pausable : gameObject.getScene().getComponentsImplementing(IPausable.class)) {
            pausable.onPause();
        }
    }

    private void resumeAll() {
        if (gameObject == null || gameObject.getScene() == null) return;
        for (IPausable pausable : gameObject.getScene().getComponentsImplementing(IPausable.class)) {
            pausable.onResume();
        }
    }

    // ========================================================================
    // EVENT LISTENER REGISTRATION (for testing / future wiring)
    // ========================================================================

    /**
     * Registers a callback for a named custom event.
     * Used for testing; at runtime, scene query finds DialogueEventListener components.
     */
    public void addEventListenerCallback(String eventName, Runnable callback) {
        if (eventListeners == null) eventListeners = new ArrayList<>();
        eventListeners.add(new DialogueEventListenerCallback(eventName, callback));
    }

    private record DialogueEventListenerCallback(String eventName, Runnable callback) {}

    // ========================================================================
    // ACCESSORS FOR TESTING
    // ========================================================================

    /**
     * Returns true if the dialogue system is busy (active conversation or slide-out in progress).
     * Used by {@link DialogueInteractable} to prevent starting a new dialogue mid-animation.
     */
    public boolean isActive() {
        return active || slidingOut;
    }

    /** Returns the current visible text (for testing typewriter progress). */
    public String getVisibleText() {
        if (fullText == null) return "";
        return fullText.substring(0, Math.min(visibleChars, fullText.length()));
    }

    /** Returns the full text after variable substitution (for testing). */
    public String getFullText() {
        return fullText;
    }

    /** Returns the current entry index (for testing). */
    public int getCurrentEntryIndex() {
        return currentEntryIndex;
    }
}
