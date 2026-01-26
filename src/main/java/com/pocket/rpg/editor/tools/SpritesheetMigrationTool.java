package com.pocket.rpg.editor.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pocket.rpg.rendering.resources.NineSliceData;
import com.pocket.rpg.resources.AssetMetadata;
import com.pocket.rpg.resources.Assets;
import com.pocket.rpg.resources.SpriteMetadata;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tool for migrating legacy .spritesheet files to the new .png.meta format.
 * <p>
 * Migration process:
 * <ol>
 *   <li>Scans for all .spritesheet files</li>
 *   <li>For each spritesheet, creates a .meta file with spriteMode: MULTIPLE</li>
 *   <li>Updates all references in .scene.json and .prefab.json files</li>
 *   <li>Optionally deletes the old .spritesheet files</li>
 * </ol>
 * <p>
 * Supports dry-run mode to preview changes before applying.
 *
 * @see SpriteMetadata
 */
public class SpritesheetMigrationTool {

    private static final String BACKUP_DIR = "migration_backup";
    private static final Pattern SPRITESHEET_REF_PATTERN =
            Pattern.compile("\"([^\"]*\\.spritesheet(?:\\.json)?)(#\\d+)?\"");

    // ========================================================================
    // MIGRATION REPORT
    // ========================================================================

    @Getter
    public static class MigrationReport {
        private final List<String> spritesheetsFound = new ArrayList<>();
        private final List<String> spritesheetsMigrated = new ArrayList<>();
        private final List<String> metaFilesCreated = new ArrayList<>();
        private final List<String> filesUpdated = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private boolean dryRun;
        private String backupPath;

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public int getTotalChanges() {
            return spritesheetsMigrated.size() + metaFilesCreated.size() + filesUpdated.size();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Spritesheet Migration Report ===\n");
            sb.append("Mode: ").append(dryRun ? "DRY RUN (no changes made)" : "LIVE").append("\n\n");

            if (backupPath != null) {
                sb.append("Backup created at: ").append(backupPath).append("\n\n");
            }

            sb.append("Spritesheets found: ").append(spritesheetsFound.size()).append("\n");
            for (String ss : spritesheetsFound) {
                sb.append("  - ").append(ss).append("\n");
            }

            sb.append("\nMeta files created: ").append(metaFilesCreated.size()).append("\n");
            for (String meta : metaFilesCreated) {
                sb.append("  - ").append(meta).append("\n");
            }

            sb.append("\nFiles updated: ").append(filesUpdated.size()).append("\n");
            for (String file : filesUpdated) {
                sb.append("  - ").append(file).append("\n");
            }

            if (!warnings.isEmpty()) {
                sb.append("\nWarnings: ").append(warnings.size()).append("\n");
                for (String warning : warnings) {
                    sb.append("  ! ").append(warning).append("\n");
                }
            }

            if (!errors.isEmpty()) {
                sb.append("\nErrors: ").append(errors.size()).append("\n");
                for (String error : errors) {
                    sb.append("  X ").append(error).append("\n");
                }
            }

            sb.append("\n=== Summary ===\n");
            sb.append("Total changes: ").append(getTotalChanges()).append("\n");
            sb.append("Status: ").append(hasErrors() ? "COMPLETED WITH ERRORS" : "SUCCESS").append("\n");

            return sb.toString();
        }
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    /**
     * Runs the migration in dry-run mode (no changes made).
     *
     * @return Migration report showing what would be changed
     */
    public MigrationReport dryRun() {
        return migrate(true, false);
    }

    /**
     * Runs the migration, applying all changes.
     *
     * @param createBackup Whether to create a backup before migration
     * @return Migration report
     */
    public MigrationReport migrate(boolean createBackup) {
        return migrate(false, createBackup);
    }

    /**
     * Runs the migration.
     *
     * @param dryRun       If true, no changes are made
     * @param createBackup If true, creates a backup before migration (ignored in dry-run)
     * @return Migration report
     */
    public MigrationReport migrate(boolean dryRun, boolean createBackup) {
        MigrationReport report = new MigrationReport();
        report.dryRun = dryRun;

        try {
            String assetRoot = Assets.getAssetRoot();
            Path assetPath = Paths.get(assetRoot);

            // 1. Find all spritesheet files
            List<Path> spritesheetFiles = findSpritesheetFiles(assetPath);
            for (Path ss : spritesheetFiles) {
                String relativePath = assetPath.relativize(ss).toString().replace('\\', '/');
                report.spritesheetsFound.add(relativePath);
            }

            if (spritesheetFiles.isEmpty()) {
                report.warnings.add("No spritesheet files found to migrate");
                return report;
            }

            // 2. Create backup (if not dry run)
            if (!dryRun && createBackup) {
                report.backupPath = createBackup(assetPath, spritesheetFiles);
            }

            // 3. Build path mapping (old path -> new path)
            Map<String, String> pathMapping = new HashMap<>();
            for (Path ssFile : spritesheetFiles) {
                String oldPath = assetPath.relativize(ssFile).toString().replace('\\', '/');
                String newPath = computeNewPath(ssFile, assetPath);
                if (newPath != null) {
                    pathMapping.put(oldPath, newPath);
                }
            }

            // 4. Migrate each spritesheet
            for (Path ssFile : spritesheetFiles) {
                try {
                    migrateSpritesheetFile(ssFile, assetPath, report, dryRun);
                } catch (Exception e) {
                    report.errors.add("Failed to migrate " + ssFile + ": " + e.getMessage());
                }
            }

            // 5. Update references in scene/prefab files
            updateReferences(assetPath, pathMapping, report, dryRun);

            // 6. Delete old spritesheet files (if not dry run)
            if (!dryRun) {
                for (Path ssFile : spritesheetFiles) {
                    try {
                        Files.deleteIfExists(ssFile);
                        report.spritesheetsMigrated.add(assetPath.relativize(ssFile).toString());
                    } catch (Exception e) {
                        report.errors.add("Failed to delete " + ssFile + ": " + e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            report.errors.add("Migration failed: " + e.getMessage());
            e.printStackTrace();
        }

        return report;
    }

    // ========================================================================
    // INTERNAL METHODS
    // ========================================================================

    /**
     * Finds all .spritesheet files in the asset directory.
     */
    private List<Path> findSpritesheetFiles(Path assetRoot) throws IOException {
        List<Path> files = new ArrayList<>();

        Files.walkFileTree(assetRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString().toLowerCase();
                if (name.endsWith(".spritesheet") ||
                    name.endsWith(".spritesheet.json") ||
                    name.endsWith(".ss.json")) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    /**
     * Computes the new texture path for a spritesheet.
     */
    private String computeNewPath(Path spritesheetFile, Path assetRoot) {
        try {
            String content = Files.readString(spritesheetFile);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

            if (json.has("texture")) {
                return json.get("texture").getAsString();
            }
        } catch (Exception e) {
            // Fall back to deriving from filename
        }

        // Derive from filename: player.spritesheet -> player.png
        String filename = spritesheetFile.getFileName().toString();
        if (filename.endsWith(".spritesheet.json")) {
            filename = filename.substring(0, filename.length() - ".spritesheet.json".length());
        } else if (filename.endsWith(".spritesheet")) {
            filename = filename.substring(0, filename.length() - ".spritesheet".length());
        } else if (filename.endsWith(".ss.json")) {
            filename = filename.substring(0, filename.length() - ".ss.json".length());
        }

        // Assume PNG in same directory
        Path parent = spritesheetFile.getParent();
        Path texturePath = parent.resolve(filename + ".png");
        return assetRoot.relativize(texturePath).toString().replace('\\', '/');
    }

    /**
     * Migrates a single spritesheet file.
     */
    private void migrateSpritesheetFile(Path ssFile, Path assetRoot, MigrationReport report, boolean dryRun)
            throws IOException {

        String content = Files.readString(ssFile);
        JsonObject json = JsonParser.parseString(content).getAsJsonObject();

        // Get texture path
        String texturePath = json.get("texture").getAsString();

        // Verify texture exists
        Path fullTexturePath = assetRoot.resolve(texturePath);
        if (!Files.exists(fullTexturePath)) {
            report.warnings.add("Texture not found: " + texturePath + " (referenced by " + ssFile + ")");
        }

        // Create new metadata
        SpriteMetadata meta = new SpriteMetadata();

        // Set grid settings
        SpriteMetadata.GridSettings grid = new SpriteMetadata.GridSettings();
        grid.spriteWidth = json.get("spriteWidth").getAsInt();
        grid.spriteHeight = json.get("spriteHeight").getAsInt();
        grid.spacingX = json.has("spacingX") ? json.get("spacingX").getAsInt() : 0;
        grid.spacingY = json.has("spacingY") ? json.get("spacingY").getAsInt() : 0;
        grid.offsetX = json.has("offsetX") ? json.get("offsetX").getAsInt() : 0;
        grid.offsetY = json.has("offsetY") ? json.get("offsetY").getAsInt() : 0;

        meta.convertToMultiple(grid);

        // Set default pivot
        float pivotX = json.has("pivotX") ? json.get("pivotX").getAsFloat() : 0.5f;
        float pivotY = json.has("pivotY") ? json.get("pivotY").getAsFloat() : 0.5f;
        meta.defaultPivot = new SpriteMetadata.PivotData(pivotX, pivotY);

        // Migrate per-sprite pivots
        if (json.has("spritePivots") && json.get("spritePivots").isJsonObject()) {
            if (meta.sprites == null) {
                meta.sprites = new HashMap<>();
            }

            JsonObject pivots = json.getAsJsonObject("spritePivots");
            for (String key : pivots.keySet()) {
                try {
                    int frameIndex = Integer.parseInt(key);
                    JsonObject pivotObj = pivots.getAsJsonObject(key);
                    float px = pivotObj.has("pivotX") ? pivotObj.get("pivotX").getAsFloat() : pivotX;
                    float py = pivotObj.has("pivotY") ? pivotObj.get("pivotY").getAsFloat() : pivotY;

                    SpriteMetadata.SpriteOverride override = meta.sprites.computeIfAbsent(
                            frameIndex, k -> new SpriteMetadata.SpriteOverride());
                    override.pivot = new SpriteMetadata.PivotData(px, py);
                } catch (NumberFormatException e) {
                    // Skip invalid keys
                }
            }
        }

        // Migrate default 9-slice
        if (json.has("nineSlice") && json.get("nineSlice").isJsonObject()) {
            JsonObject nsJson = json.getAsJsonObject("nineSlice");
            NineSliceData ns = new NineSliceData();
            ns.left = nsJson.has("left") ? nsJson.get("left").getAsInt() : 0;
            ns.right = nsJson.has("right") ? nsJson.get("right").getAsInt() : 0;
            ns.top = nsJson.has("top") ? nsJson.get("top").getAsInt() : 0;
            ns.bottom = nsJson.has("bottom") ? nsJson.get("bottom").getAsInt() : 0;
            if (ns.hasSlicing()) {
                meta.defaultNineSlice = ns;
            }
        }

        // Migrate per-sprite 9-slices
        if (json.has("spriteNineSlices") && json.get("spriteNineSlices").isJsonObject()) {
            if (meta.sprites == null) {
                meta.sprites = new HashMap<>();
            }

            JsonObject nineSlices = json.getAsJsonObject("spriteNineSlices");
            for (String key : nineSlices.keySet()) {
                try {
                    int frameIndex = Integer.parseInt(key);
                    JsonObject nsJson = nineSlices.getAsJsonObject(key);
                    NineSliceData ns = new NineSliceData();
                    ns.left = nsJson.has("left") ? nsJson.get("left").getAsInt() : 0;
                    ns.right = nsJson.has("right") ? nsJson.get("right").getAsInt() : 0;
                    ns.top = nsJson.has("top") ? nsJson.get("top").getAsInt() : 0;
                    ns.bottom = nsJson.has("bottom") ? nsJson.get("bottom").getAsInt() : 0;

                    if (ns.hasSlicing()) {
                        SpriteMetadata.SpriteOverride override = meta.sprites.computeIfAbsent(
                                frameIndex, k -> new SpriteMetadata.SpriteOverride());
                        override.nineSlice = ns;
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid keys
                }
            }
        }

        // Save metadata
        if (!dryRun) {
            AssetMetadata.save(texturePath, meta);
        }
        report.metaFilesCreated.add(texturePath + ".meta");
    }

    /**
     * Updates references in scene and prefab files.
     */
    private void updateReferences(Path assetRoot, Map<String, String> pathMapping,
                                   MigrationReport report, boolean dryRun) throws IOException {

        // Find all scene and prefab files
        List<Path> dataFiles = new ArrayList<>();
        Path gameDataPath = assetRoot.getParent(); // Go up from assets to gameData

        Files.walkFileTree(gameDataPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString().toLowerCase();
                if (name.endsWith(".scene.json") || name.endsWith(".prefab.json") || name.endsWith(".scene")) {
                    dataFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // Update each file
        for (Path dataFile : dataFiles) {
            try {
                String content = Files.readString(dataFile);
                String updatedContent = updateFileContent(content, pathMapping);

                if (!content.equals(updatedContent)) {
                    if (!dryRun) {
                        Files.writeString(dataFile, updatedContent);
                    }
                    report.filesUpdated.add(dataFile.toString());
                }
            } catch (Exception e) {
                report.errors.add("Failed to update " + dataFile + ": " + e.getMessage());
            }
        }
    }

    /**
     * Updates spritesheet references in file content.
     */
    private String updateFileContent(String content, Map<String, String> pathMapping) {
        Matcher matcher = SPRITESHEET_REF_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String oldPath = matcher.group(1);
            String index = matcher.group(2); // May be null

            String newPath = pathMapping.get(oldPath);
            if (newPath == null) {
                // Try without .json extension
                if (oldPath.endsWith(".json")) {
                    newPath = pathMapping.get(oldPath.substring(0, oldPath.length() - 5));
                }
            }

            if (newPath != null) {
                String replacement = "\"" + newPath + (index != null ? index : "") + "\"";
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Creates a backup of files that will be modified.
     */
    private String createBackup(Path assetRoot, List<Path> spritesheetFiles) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path backupDir = assetRoot.getParent().resolve(BACKUP_DIR + "_" + timestamp);
        Files.createDirectories(backupDir);

        // Backup spritesheet files
        for (Path ssFile : spritesheetFiles) {
            Path relativePath = assetRoot.relativize(ssFile);
            Path backupFile = backupDir.resolve("spritesheets").resolve(relativePath);
            Files.createDirectories(backupFile.getParent());
            Files.copy(ssFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        }

        return backupDir.toString();
    }
}
