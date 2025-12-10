package com.pocket.rpg.editor.core;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.nfd.NFDFilterItem;
import org.lwjgl.util.nfd.NativeFileDialog;

import java.io.File;
import java.util.Optional;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.nfd.NativeFileDialog.*;

/**
 * Native file dialog utilities using LWJGL's NFD binding.
 * Provides cross-platform file open/save dialogs.
 */
public class FileDialogs {

    // Filter for scene files
    private static final String SCENE_FILTER_NAME = "Scene Files";
    private static final String SCENE_FILTER_SPEC = "scene,json";

    // Filter for image files (tilesets)
    private static final String IMAGE_FILTER_NAME = "Image Files";
    private static final String IMAGE_FILTER_SPEC = "png,jpg,jpeg,bmp";

    // Filter for all files
    private static final String ALL_FILTER_NAME = "All Files";
    private static final String ALL_FILTER_SPEC = "*";

    static {
        // Initialize NFD
        int result = NFD_Init();
        if (result != NFD_OKAY) {
            System.err.println("WARNING: Failed to initialize NFD: " + NFD_GetError());
        }
    }

    /**
     * Opens a file dialog for selecting a scene file.
     *
     * @param defaultPath Default directory to open (can be null)
     * @return Selected file path, or empty if cancelled
     */
    public static Optional<String> openSceneFile(String defaultPath) {
        return openFile(defaultPath, SCENE_FILTER_NAME, SCENE_FILTER_SPEC);
    }

    /**
     * Opens a save dialog for scene files.
     *
     * @param defaultPath Default directory
     * @param defaultName Default file name
     * @return Selected file path, or empty if cancelled
     */
    public static Optional<String> saveSceneFile(String defaultPath, String defaultName) {
        return saveFile(defaultPath, defaultName, SCENE_FILTER_NAME, SCENE_FILTER_SPEC);
    }

    /**
     * Opens a file dialog for selecting an image file (tileset).
     *
     * @param defaultPath Default directory
     * @return Selected file path, or empty if cancelled
     */
    public static Optional<String> openImageFile(String defaultPath) {
        return openFile(defaultPath, IMAGE_FILTER_NAME, IMAGE_FILTER_SPEC);
    }

    /**
     * Opens a folder selection dialog.
     *
     * @param defaultPath Default directory
     * @return Selected folder path, or empty if cancelled
     */
    public static Optional<String> openFolder(String defaultPath) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer outPath = stack.mallocPointer(1);

            int result = NFD_PickFolder(outPath, defaultPath);

            if (result == NFD_OKAY) {
                String path = outPath.getStringUTF8(0);
                NFD_FreePath(outPath.get(0));
                return Optional.of(path);
            } else if (result == NFD_CANCEL) {
                return Optional.empty();
            } else {
                System.err.println("NFD Error: " + NFD_GetError());
                return Optional.empty();
            }
        }
    }

    /**
     * Opens a generic file dialog with custom filter.
     *
     * @param defaultPath Default directory
     * @param filterName Filter display name (e.g., "Image Files")
     * @param filterSpec Filter spec (e.g., "png,jpg,jpeg")
     * @return Selected file path, or empty if cancelled
     */
    public static Optional<String> openFile(String defaultPath, String filterName, String filterSpec) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer outPath = stack.mallocPointer(1);

            NFDFilterItem.Buffer filterList = NFDFilterItem.malloc(1, stack);
            filterList.get(0)
                .name(stack.UTF8(filterName))
                .spec(stack.UTF8(filterSpec));

            int result = NFD_OpenDialog(outPath, filterList, defaultPath);

            if (result == NFD_OKAY) {
                String path = outPath.getStringUTF8(0);
                NFD_FreePath(outPath.get(0));
                return Optional.of(path);
            } else if (result == NFD_CANCEL) {
                return Optional.empty();
            } else {
                System.err.println("NFD Error: " + NFD_GetError());
                return Optional.empty();
            }
        }
    }

    /**
     * Opens a save file dialog with custom filter.
     *
     * @param defaultPath Default directory
     * @param defaultName Default file name
     * @param filterName Filter display name
     * @param filterSpec Filter spec
     * @return Selected file path, or empty if cancelled
     */
    public static Optional<String> saveFile(String defaultPath, String defaultName, 
                                            String filterName, String filterSpec) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer outPath = stack.mallocPointer(1);

            NFDFilterItem.Buffer filterList = NFDFilterItem.malloc(1, stack);
            filterList.get(0)
                .name(stack.UTF8(filterName))
                .spec(stack.UTF8(filterSpec));

            int result = NFD_SaveDialog(outPath, filterList, defaultPath, defaultName);

            if (result == NFD_OKAY) {
                String path = outPath.getStringUTF8(0);
                NFD_FreePath(outPath.get(0));
                
                // Ensure extension is added if missing
                path = ensureExtension(path, filterSpec);
                
                return Optional.of(path);
            } else if (result == NFD_CANCEL) {
                return Optional.empty();
            } else {
                System.err.println("NFD Error: " + NFD_GetError());
                return Optional.empty();
            }
        }
    }

    /**
     * Opens a file dialog for selecting multiple files.
     *
     * @param defaultPath Default directory
     * @param filterName Filter display name
     * @param filterSpec Filter spec
     * @return Array of selected file paths, or empty array if cancelled
     */
    public static String[] openMultipleFiles(String defaultPath, String filterName, String filterSpec) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer outPaths = stack.mallocPointer(1);

            NFDFilterItem.Buffer filterList = NFDFilterItem.malloc(1, stack);
            filterList.get(0)
                .name(stack.UTF8(filterName))
                .spec(stack.UTF8(filterSpec));

            int result = NFD_OpenDialogMultiple(outPaths, filterList, defaultPath);

            if (result == NFD_OKAY) {
                long pathSet = outPaths.get(0);
                
                // Get count
                int[] count = new int[1];
                NFD_PathSet_GetCount(pathSet, count);
                
                String[] paths = new String[count[0]];
                
                // Get each path
                PointerBuffer pathBuffer = stack.mallocPointer(1);
                for (int i = 0; i < count[0]; i++) {
                    NFD_PathSet_GetPath(pathSet, i, pathBuffer);
                    paths[i] = pathBuffer.getStringUTF8(0);
                    NFD_PathSet_FreePath(pathBuffer.get(0));
                }
                
                NFD_PathSet_Free(pathSet);
                return paths;
            } else if (result == NFD_CANCEL) {
                return new String[0];
            } else {
                System.err.println("NFD Error: " + NFD_GetError());
                return new String[0];
            }
        }
    }

    /**
     * Ensures the file path has the appropriate extension.
     */
    private static String ensureExtension(String path, String filterSpec) {
        if (filterSpec == null || filterSpec.equals("*")) {
            return path;
        }

        // Get first extension from spec
        String[] extensions = filterSpec.split(",");
        if (extensions.length == 0) {
            return path;
        }

        String primaryExt = extensions[0].trim();
        if (primaryExt.isEmpty()) {
            return path;
        }

        // Check if path already has a valid extension
        String lowerPath = path.toLowerCase();
        for (String ext : extensions) {
            if (lowerPath.endsWith("." + ext.trim().toLowerCase())) {
                return path;
            }
        }

        // Add primary extension
        return path + "." + primaryExt;
    }

    /**
     * Gets the default scenes directory.
     */
    public static String getScenesDirectory() {
        File dir = new File("gameData/scenes");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    /**
     * Gets the default assets directory.
     */
    public static String getAssetsDirectory() {
        File dir = new File("gameData/assets");
        return dir.getAbsolutePath();
    }

    /**
     * Cleans up NFD resources.
     * Call on application shutdown.
     */
    public static void cleanup() {
        NFD_Quit();
    }
}
