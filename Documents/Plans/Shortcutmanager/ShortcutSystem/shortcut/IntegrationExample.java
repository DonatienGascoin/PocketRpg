package com.pocket.rpg.editor.shortcut;

/**
 * INTEGRATION GUIDE
 * 
 * 1. Initialize in EditorApplication or EditorContext:
 * 
 *    ShortcutRegistry registry = ShortcutRegistry.getInstance();
 *    EditorShortcuts.registerDefaults(registry);
 *    registry.loadConfig("editor/editorShortcuts.json");
 *    EditorShortcuts.bindHandlers(registry, new EditorShortcutHandlersImpl(...));
 * 
 * 
 * 2. Process shortcuts at START of frame (before ImGui windows):
 * 
 *    // In render loop, BEFORE any ImGui.begin() calls:
 *    ShortcutContext context = ShortcutContext.current();
 *    registry.processShortcuts(context);
 * 
 * 
 * 3. For panel-scoped shortcuts, panels must report their state.
 *    Option A - Simple (use ShortcutContext.current() which only handles global/popup):
 *    
 *    // Just works for GLOBAL and POPUP scopes
 *    registry.processShortcuts(ShortcutContext.current());
 * 
 *    Option B - Full (track panel visibility/focus):
 *    
 *    // Build context during render
 *    ShortcutContextBuilder contextBuilder = new ShortcutContextBuilder();
 *    
 *    // In each panel's render method, after ImGui.begin():
 *    if (ImGui.begin("Scene View")) {
 *        contextBuilder.reportPanel(EditorShortcuts.PanelIds.SCENE_VIEW);
 *        // ... render content
 *    }
 *    ImGui.end();
 *    
 *    // Then process with built context
 *    registry.processShortcuts(contextBuilder.build());
 * 
 * 
 * 4. Add ShortcutsConfigTab to ConfigPanel:
 * 
 *    tabs.add(new ShortcutsConfigTab(context));
 * 
 * 
 * 5. Update menu bar to use registry for display:
 * 
 *    // Instead of hardcoded "Ctrl+S":
 *    String saveShortcut = registry.getBindingDisplay(EditorShortcuts.FILE_SAVE);
 *    if (ImGui.menuItem("Save", saveShortcut)) { ... }
 * 
 * 
 * 6. Implement EditorShortcutHandlers:
 * 
 *    public class EditorShortcutHandlersImpl implements EditorShortcutHandlers {
 *        private final EditorContext context;
 *        private final ConfigPanel configPanel;
 *        // ... other dependencies
 *        
 *        @Override
 *        public void onNewScene() {
 *            // Check unsaved changes, then create new scene
 *        }
 *        
 *        @Override
 *        public void onUndo() {
 *            UndoManager.getInstance().undo();
 *            if (context.getCurrentScene() != null) {
 *                context.getCurrentScene().markDirty();
 *            }
 *        }
 *        // ... implement other handlers
 *    }
 */
public class IntegrationExample {
    // See comments above
}
