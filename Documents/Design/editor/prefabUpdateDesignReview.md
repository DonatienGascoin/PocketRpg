 ---                                                                                                                                                                               
Critical Issues (all reviewers converge on these)

1. SceneWillChangeEvent cannot be cancelled                                                                                                                                       
   Raised by: Engineer, QA, Architect. The event is fire-and-forget. The "Cancel" button on the scene-change popup is a lie — there's no veto mechanism. Either the scene change     
   system needs a vetoable event pattern, or prefab edit must auto-exit on scene change (like play mode does) without offering Cancel.

2. scene.markDirty() scattered throughout shared code paths                                                                                                                       
   Raised by: Engineer, QA. EntityInspector, ComponentFieldEditor, and undo commands like BulkMoveCommand, RemoveEntityCommand all call scene.markDirty(). The working entity has no
   scene. Reusing these code paths will NPE or silently corrupt the real scene's dirty state. The shortcut handler's onUndo() also unconditionally calls scene.markDirty(). This     
   requires either a ComponentListRenderer extraction (Architect's recommendation) or a guard in every markDirty() call site.

3. Keyboard shortcuts are not guarded during prefab edit                                                                                                                          
   Raised by: QA. Delete, Duplicate (Ctrl+D), tool-switching keys (1-5), and other shortcuts operate on the hidden scene. They are not blocked or redirected by the design.

  ---                                                                                                                                                                               
High Severity

4. Hierarchy panel behavior is unspecified                                                                                                                                        
   Raised by: Engineer, Architect. The hierarchy still shows the full scene entity list during prefab edit. Users can click entities, triggering guards, but the panel itself is     
   confusing. It should be hidden, greyed out, or show only the working entity.

5. Shallow clone of mutable sub-objects                                                                                                                                           
   Raised by: Engineer. deepCopyValue() in ComponentReflectionUtils only handles Vector2f/3f/4f. List, Map, and array fields are shared between the clone and original. Editing a    
   list field on the working entity would mutate the prefab's in-memory definition.

6. Ctrl+Z during confirmation popup                                                                                                                                               
   Raised by: QA. Keyboard shortcuts are processed before ImGui renders modals. Pressing Ctrl+Z while the unsaved-changes popup is open can desync dirty state with the popup's      
   buttons, leading to data corruption (e.g., "Save & Continue" saves a state the user just undid).

7. Double-clicking a second prefab while already editing one is unspecified                                                                                                       
   Raised by: QA, Engineer. The guard intercepts asset selection, not the "enter prefab edit" action. The design needs an explicit guard in PrefabEditController.enterEditMode()     
   itself.

8. No escape hatch                                                                                                                                                                
   Raised by: QA. If the inspector fails to render, there's no keyboard shortcut (Escape) to exit prefab edit mode. The user is stuck.

  ---                                                                                                                                                                               
Architectural Concerns

9. EditorSelectionManager coupling (Architect, strongly recommended change)                                                                                                       
   Adding guards to every select*() method breaks SelectionManager's clean boundary. Recommendation: extract a SelectionGuard wrapper class. Call sites call the guard;              
   SelectionManager stays controller-free. This is extensible for future modes.

10. PrefabInspector reuse mechanism is ambiguous (Architect, Engineer)                                                                                                            
    EntityInspector has private methods and inline scene.markDirty() calls. Inheritance won't work. Recommendation: extract a ComponentListRenderer shared utility that both          
    EntityInspector and PrefabInspector delegate to.

11. Undo stash/restore should be scoped (Architect)                                                                                                                               
    Instead of caller-held snapshots, use pushScope()/popScope() on UndoManager. This handles nesting, avoids stale snapshot bugs, and is cleaner. Also: lastCommand/lastCommandTime  
    should be scoped too.

12. Use RequestPrefabEditEvent (Architect)                                                                                                                                        
    Entry points (asset browser, inspector button) should publish an event rather than holding a direct reference to PrefabEditController. This matches existing patterns like        
    OpenAnimationEditorEvent.

  ---                                                                                                                                                                               
UX Issues

13. Ctrl+S should save the prefab, not be blocked (Product Owner, strong recommendation)                                                                                          
    Blocking Ctrl+S fights muscle memory. Remapping it to save the prefab during prefab edit mode is what modal editors (Photoshop, Blender) do.

14. No "Save & Exit" button (Product Owner)                                                                                                                                       
    The most common workflow (edit, save, leave) requires two clicks. "Revert & Exit" is also misleading after saving — it should say "Exit" when clean.

15. "Reset to Saved" has no confirmation (Product Owner)                                                                                                                          
    It clears undo history. One accidental click destroys minutes of work with no recovery.

16. Transform component is a trap (Product Owner)                                                                                                                                 
    If the user drags the working entity in the viewport and saves, every instance without a position override jumps. The design doesn't address this. Transform may need special     
    treatment or a warning.

17. Component reordering is in the goals but not in the design (Product Owner)                                                                                                    
    Goal 1 says "reorder" but no UI or mechanism is described. Remove from goals or design it.

  ---                                                                                                                                                                               
Lower Severity

- JsonPrefab.sourcePath may be null after deserialization (Engineer) — save-back needs the file path
- Temporary EditorScene for rendering needs careful isolation — must never be set as context.getCurrentScene() (Engineer, Architect)
- PlayModeController.play() called directly bypasses the prefab edit guard (QA)
- External prefab file modification during editing produces stale "Reset to Saved" (QA)
- No instance count shown ("This prefab has 7 instances in the current scene") (Product Owner)
- Layout shifts when "Reset to Saved" button appears/disappears (Product Owner)