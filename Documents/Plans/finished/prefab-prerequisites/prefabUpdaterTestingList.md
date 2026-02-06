Manual Test Checklist — Prefab Edit Mode

A. Entry Points

1. Asset browser double-click: Double-click a .prefab.json file in the asset browser. Should enter prefab edit mode without assertion errors.                                     
   OK
2. Edit Prefab button (JSON prefab): Select a prefab instance in the scene, click "Edit Prefab" in inspector. Should enter prefab edit mode.         
   Ok, but edit prefab should be only the pen icon with a tooltip next (on the left side) of the trash button
3. Edit Prefab button (code-defined prefab): Select a code-defined prefab instance. The "Edit Prefab" button should be disabled with tooltip "Code-defined prefabs cannot be      
   edited".
4. Null/missing prefab guard: If a prefab instance references a missing prefab, the "Edit Prefab" button should not appear (or prefab info shows warning).

B. Prefab Edit Mode — Visual State

5. Inspector shows PrefabInspector: Teal header with "PREFAB MODE", prefab display name, ID, instance count.        
   Ok, but should be in the hierarchy panel instead
6. Hierarchy shows prefab hierarchy: Teal "PREFAB MODE" header, single working entity listed.
   Ok
7. Viewport shows working entity only: Only the prefab's working entity visible, not the full scene. Teal border overlay with "PREFAB: displayName".
   OK
8. Toolbar shows prefab indicator: Teal prefab name shown in toolbar. Tool buttons disabled.                                                                                      
   Half OK - teal prefab name appear. Tools I still see the move/rotate/scale, though Nothing happen when I click them. Note: those 3 Tools should be usable in order to update the entity in the scene view.

C. Editing in Prefab Edit Mode

9. Edit display name: Change display name field in PrefabInspector, verify dirty flag activates (Save button turns green).
   Ok
10. Edit category: Change category field, verify dirty flag.
    OK
11. Edit component fields: Modify a component value (e.g. Transform position), verify dirty flag.
    OK
12. Add component: Click "+ Add Component", add a component, verify it appears and dirty flag is set.
    OK
13. Remove component: Remove a component, verify dirty flag.
    OK
14. Undo/Redo within prefab edit: Ctrl+Z / Ctrl+Y should undo/redo changes made within prefab edit mode.
    OK

D. Save                                                                                                                                                                           
Save still same at the wront path ! Look at gameData/assets/prefabs folder
15. Save (Ctrl+S): Make changes, press Ctrl+S. Verify dirty flag clears, changes persist to the prefab file.
    Half OK: green button is saved, dirty flag clear, but prefab not persisted at the right location
16. Save button: Click "SAVE PREFAB" button. Same as above.
    Same half ok
17. Save & Exit: Click "Save & Exit". Should save and return to scene mode.
    OK
18. Instance cache invalidation: After saving, prefab instances in the scene should reflect the updated component values.
    OK

E. Reset / Exit

19. Reset to Saved (clean): Button should be disabled when no changes have been made.
    KO: I saved a value of 20, then edit this value to 150 and clicked on "Reset to saved" (which I said should be called "Revert all"), I got a confirmation popup with "Save and continue" -> this option makes no sense, the goal is to revert or cancel, this option should not exists. Then discard, which work and cancel which works too.
20. Reset to Saved (dirty): Make changes, click "Reset to Saved". Should show confirmation, then revert to saved state, clear dirty flag.
    OK
21. Exit (clean): Click "Exit" when clean. Should exit immediately to scene mode.
    OK
22. Exit (dirty): Click "Exit" when dirty. Should show confirmation popup with Save & Exit / Discard & Exit / Cancel.
    KO: First tthe button should always be "Exit" and not "Revert and exit". Then the popup buttons should be "Save and exit", "Discard and exist" and "cancel". Otherwise the buttons themselves works
23. Escape key (clean): Press Escape. Should exit immediately.
    OK
24. Escape key (dirty): Press Escape when dirty. Should show confirmation popup.
    Ok, tho the button naming does not fix either "save and exit, "discard and exit" "cancel"

F. Undo Scope Isolation

25. Scene undo preserved: Make scene changes (move entity, rename), enter prefab edit, make prefab changes, undo inside prefab — should only undo prefab changes. Exit prefab edit
    — scene undo history should be intact, Ctrl+Z undoes the pre-edit scene changes.
    OK
26. Prefab undo discarded on exit: After exiting prefab edit, Ctrl+Z should not undo any prefab edit changes.
    OK

G. Mode Guards

27. Delete/Duplicate suppressed: Ctrl+Delete / Ctrl+D should not work during prefab edit.
    OK
28. Play mode suppressed: Play button / shortcut should be disabled during prefab edit.
    KO I can still enter play mode
29. Tool shortcuts suppressed: Pressing B/E/F/R/P/V/G/S tool keys should not activate tools.
    OK
30. Selection guard: Clicking in the scene viewport should not select scene entities.                                                                                             
    OK, the behaviour works - but that is not good, it should be possible to select an entity and move/scale/rotate it around

H. Scene Will Change Interaction

31. Scene change while dirty: Try to open/create a new scene while in dirty prefab edit. Should cancel the scene change and show confirmation.
    OK: BUT the confirmation popup is not clear, are we saving the dirty prefab ? The dirty scene ? I Don't think the dirty prefab is even taken into account
32. Scene change while clean: Try to open/create a new scene while in clean prefab edit. Should exit prefab edit first, then proceed with scene change.                           
    OK

I. Window Close

33. Close window while dirty: Try to close the editor window while in dirty prefab edit. Should show confirmation before closing.
    OK
34. Close window while clean: Should exit prefab edit and close normally.                                                                                                         
    OK

J. Re-entry / Edge Cases

35. Enter same prefab again: While editing prefab A, try to enter prefab A again (via event). Should be a no-op (same working entity).
    OK
36. Enter different prefab while clean: Should exit current and enter new one.
    OK
37. Enter different prefab while dirty: Should show confirmation before switching.                                                                                                
    OK

K. Existing Functionality Regression

38. Scene editing still works: Exit prefab edit, verify normal scene editing (select, move, rename, add/remove entities, undo/redo) all work.
    Ok
39. Play mode still works: Exit prefab edit, enter play mode, verify play/pause/stop work correctly.
    OK
40. Inspector for scene entities: Select a scene entity after exiting prefab edit — EntityInspector should render normally.
    OK
41. Inspector for runtime objects: In play mode, select a runtime object — runtime inspector should render.
    KO: As there is a bug: I can enter play mode while Editing a prefab, I cant see the play entities in the inspector as it is stuck in the prefab mode.
42. Scene save (Ctrl+S): In scene mode, Ctrl+S should save the scene (not a prefab).
    OK
43. Asset browser navigation: Double-click non-prefab assets should still open their respective editors (sprite, animation, etc.).
    OK


Other notes:
- DemoScene.scene was dirty when I opened a prefab, so the scene name on the main menu bar was yellow with an asterisk. In prefab mode I think it should not be marked as dirty as impossible to save.
- On the footer bar, there is a "modified" text, but that is for the scene state when it should be for the prefab state when in prefab mode.
- Gizmos are not available in prefab mode, I am not seeing them in the scene panel
- save and exit buttons should be at the top of the hierarchy panel instead of the inspector panel, and in there own child panel so even if hierarchy has 100 children only the children would be scrollable and not the buttons (Always visible)
- Confirmation modal: there are plenty across the code now, extract them into an utility class in order to get a similar result would be great
- I think the confirmation modals are too small and difficult to understand (text hard to read). The button should be in the opposite order, cancel on the left, validate on the right. As it is a modal, maybe the background should become darker. Use colored icons also
- The menu bar play/pause/stop button should be disabled via 2 events in the event bus system: enter_prefab_edit and exit_prefab_exit


