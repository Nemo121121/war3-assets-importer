package com.hiveworkshop.war3assetsimporter.core.service;

import com.hiveworkshop.war3assetsimporter.core.model.MapAssetEntry;
import com.hiveworkshop.war3assetsimporter.core.model.MapAssetEntry.Category;
import net.moonlightflower.wc3libs.bin.ObjMod;
import net.moonlightflower.wc3libs.bin.Wc3BinInputStream;
import net.moonlightflower.wc3libs.bin.app.objMod.*;
import net.moonlightflower.wc3libs.misc.MetaFieldId;
import systems.crigges.jmpq3.JMpqEditor;
import systems.crigges.jmpq3.MPQOpenOption;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts custom assets from a Warcraft 3 map's MPQ archive into categorised
 * subfolders with per-object grouping and writes complete object-editor
 * definition data for all 7 WC3 object types.
 */
public class ExportService {

    private static final Logger LOG = Logger.getLogger(ExportService.class.getName());

    /** Object file → (model/art field ID, name field ID) for each type that references files. */
    private static final Map<String, String[]> OBJ_FILE_FIELDS = Map.of(
            "war3map.w3u", new String[]{"umdl", "unam"},
            "war3map.w3t", new String[]{"ifil", "unam"},
            "war3map.w3b", new String[]{"bfil", "bnam"},
            "war3map.w3d", new String[]{"dfil", "dnam"},
            "war3map.w3a", new String[]{"aart", "anam"},
            "war3map.w3h", new String[]{"fart", "fnam"},
            "war3map.w3q", new String[]{"gar1", "gnam"}
    );

    /** Maps object file names to the ObjMod subclass used to parse them. */
    private static final Map<String, Class<? extends ObjMod<?>>> OBJ_FILE_CLASSES = Map.of(
            "war3map.w3u", W3U.class, "war3map.w3t", W3T.class,
            "war3map.w3b", W3B.class, "war3map.w3d", W3D.class,
            "war3map.w3a", W3A.class, "war3map.w3h", W3H.class,
            "war3map.w3q", W3Q.class
    );

    /**
     * Exports the specified assets from a map file into categorised subfolders
     * with per-object grouping under {@code outputFolder}.
     */
    public List<Path> exportAssets(File mapFile, List<MapAssetEntry> assetEntries,
                                   File outputFolder, Consumer<String> log) {
        List<Path> exported = new ArrayList<>();
        if (log == null) log = s -> {};

        try {
            Files.createDirectories(outputFolder.toPath());
        } catch (IOException e) {
            log.accept("Error: could not create output folder: " + e.getMessage());
            return exported;
        }

        try (JMpqEditor mpq = new JMpqEditor(mapFile, MPQOpenOption.FORCE_V0)) {

            // ---- Extract files into categorised subfolders with per-object grouping ----
            for (MapAssetEntry entry : assetEntries) {
                // Skip pseudo-path entries (abilities/buffs/upgrades definitions — no actual file)
                if (entry.path().startsWith("__objdef__/")) continue;

                try {
                    String mpqPath = entry.path();
                    if (!mpq.hasFile(mpqPath)) {
                        log.accept("Not found in map: " + mpqPath);
                        continue;
                    }
                    byte[] data = mpq.extractFileAsBytes(mpqPath);

                    // Build path: category/ownerName/filename (or category/filename if no owner)
                    String subFolder = entry.category().folderName();
                    Path subDir = outputFolder.toPath().resolve(subFolder);

                    String ownerName = entry.ownerName();
                    if (ownerName != null && !ownerName.isEmpty()) {
                        // Sanitise owner name for filesystem
                        String safeName = ownerName.replaceAll("[^a-zA-Z0-9_\\- ]", "").trim();
                        if (!safeName.isEmpty()) {
                            subDir = subDir.resolve(safeName);
                        }
                    }
                    Files.createDirectories(subDir);

                    String filename = entry.filename();
                    Path dest = resolveUniquePath(subDir, filename);

                    Files.write(dest, data);
                    exported.add(dest);

                    String relPath = outputFolder.toPath().relativize(dest).toString().replace('\\', '/');
                    log.accept("Exported: " + mpqPath + " -> " + relPath);
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Failed to export: " + entry.path(), ex);
                    log.accept("Error exporting " + entry.path() + ": " + ex.getMessage());
                }
            }

            // ---- Write complete object definition data for all 7 WC3 types ----
            writeAllDefinitions(mpq, assetEntries, outputFolder, log);

            // ---- Export raw Object Editor binary files (the full W3x data) ----
            exportRawObjectFiles(mpq, outputFolder, log);

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to open map for export", ex);
            log.accept("Error opening map: " + ex.getMessage());
        }

        return exported;
    }

    /**
     * For each object file that has custom entries matching exported assets,
     * writes a definitions.json with ALL object editor fields.
     */
    private void writeAllDefinitions(JMpqEditor mpq, List<MapAssetEntry> entries,
                                     File outputFolder, Consumer<String> log) {
        // Group exported entries by category folder
        Map<String, Set<String>> modelPathsByFolder = new HashMap<>();
        Set<String> categoriesWithEntries = new HashSet<>();
        for (MapAssetEntry e : entries) {
            String folder = e.category().folderName();
            categoriesWithEntries.add(folder);
            if (!e.path().startsWith("__objdef__/")) {
                modelPathsByFolder.computeIfAbsent(folder, k -> new HashSet<>()).add(e.path());
                modelPathsByFolder.get(folder).add(e.path().replace('\\', '/'));
            }
        }

        // Write definitions for each object type
        writeObjDefinitions(mpq, "war3map.w3u", W3U.class, "umdl",
                Set.of("units", "buildings"), modelPathsByFolder, outputFolder, log);
        writeObjDefinitions(mpq, "war3map.w3t", W3T.class, "ifil",
                Set.of("items"), modelPathsByFolder, outputFolder, log);
        writeObjDefinitions(mpq, "war3map.w3b", W3B.class, "bfil",
                Set.of("destructibles"), modelPathsByFolder, outputFolder, log);
        writeObjDefinitions(mpq, "war3map.w3d", W3D.class, "dfil",
                Set.of("doodads"), modelPathsByFolder, outputFolder, log);

        // For abilities/buffs/upgrades, write definitions if any custom objects exist
        writeObjDefinitionsAll(mpq, "war3map.w3a", W3A.class, "abilities", categoriesWithEntries, outputFolder, log);
        writeObjDefinitionsAll(mpq, "war3map.w3h", W3H.class, "buffs_effects", categoriesWithEntries, outputFolder, log);
        writeObjDefinitionsAll(mpq, "war3map.w3q", W3Q.class, "upgrades", categoriesWithEntries, outputFolder, log);
    }

    /**
     * Writes definitions.json for an object type, filtering to objects whose model/art
     * path matches one of the exported files.
     */
    @SuppressWarnings("unchecked")
    private void writeObjDefinitions(JMpqEditor mpq, String fileName,
                                     Class<? extends ObjMod<?>> cls, String modelField,
                                     Set<String> targetFolders,
                                     Map<String, Set<String>> modelPathsByFolder,
                                     File outputFolder, Consumer<String> log) {
        if (!mpq.hasFile(fileName)) return;

        // Collect all model paths from the target folders
        Set<String> allPaths = new HashSet<>();
        for (String folder : targetFolders) {
            Set<String> paths = modelPathsByFolder.get(folder);
            if (paths != null) allPaths.addAll(paths);
        }
        if (allPaths.isEmpty()) return;

        try {
            ObjMod<?> objMod = cls.getConstructor(Wc3BinInputStream.class)
                    .newInstance(new Wc3BinInputStream(
                            new ByteArrayInputStream(mpq.extractFileAsBytes(fileName))));

            // Group objects by target folder for per-folder definitions.json
            Map<String, List<ObjMod.Obj>> objsByFolder = new LinkedHashMap<>();
            for (ObjMod.Obj obj : objMod.getObjsList()) {
                Object mdlVal = obj.get(MetaFieldId.valueOf(modelField));
                if (mdlVal == null) continue;
                String path = mdlVal.toString();
                String normPath = path.replace('\\', '/');

                for (String folder : targetFolders) {
                    Set<String> folderPaths = modelPathsByFolder.get(folder);
                    if (folderPaths != null && (folderPaths.contains(path) || folderPaths.contains(normPath))) {
                        objsByFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(obj);
                        break;
                    }
                }
            }

            for (var entry : objsByFolder.entrySet()) {
                writeDefinitionsJson(outputFolder, entry.getKey(), entry.getValue(), log);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not write definitions from " + fileName, e);
        }
    }

    /**
     * Writes definitions.json for ALL objects in the given file (used for abilities/buffs/upgrades
     * which don't have IMP file entries).
     */
    @SuppressWarnings("unchecked")
    private void writeObjDefinitionsAll(JMpqEditor mpq, String fileName,
                                        Class<? extends ObjMod<?>> cls, String folder,
                                        Set<String> categoriesWithEntries,
                                        File outputFolder, Consumer<String> log) {
        if (!mpq.hasFile(fileName)) return;
        if (!categoriesWithEntries.contains(folder)) return;

        try {
            ObjMod<?> objMod = cls.getConstructor(Wc3BinInputStream.class)
                    .newInstance(new Wc3BinInputStream(
                            new ByteArrayInputStream(mpq.extractFileAsBytes(fileName))));

            List<ObjMod.Obj> allObjs = new ArrayList<>();
            for (ObjMod.Obj obj : objMod.getObjsList()) {
                allObjs.add(obj);
            }
            if (!allObjs.isEmpty()) {
                writeDefinitionsJson(outputFolder, folder, allObjs, log);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not write definitions from " + fileName, e);
        }
    }

    /** Writes a list of objects as definitions.json in the given subfolder. */
    private void writeDefinitionsJson(File outputFolder, String subfolder,
                                      List<ObjMod.Obj> objects, Consumer<String> log) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        boolean first = true;
        for (ObjMod.Obj obj : objects) {
            if (!first) sb.append(",\n");
            first = false;
            writeObjJson(sb, obj);
        }
        sb.append("\n]\n");

        Path subDir = outputFolder.toPath().resolve(subfolder);
        Files.createDirectories(subDir);
        Path metaFile = subDir.resolve("definitions.json");
        Files.writeString(metaFile, sb.toString(), StandardCharsets.UTF_8);
        log.accept("Wrote " + objects.size() + " definition(s) to: " + subfolder + "/definitions.json");
    }

    /**
     * Writes a single ObjMod.Obj as a JSON object with ALL modification fields.
     */
    /**
     * All WC3 Object Editor files to extract — the raw binary that contains
     * every custom unit/item/ability/buff/upgrade/destructible/doodad definition
     * with ALL their Object Editor settings (stats, sounds, abilities, etc.).
     */
    private static final String[][] RAW_OBJECT_FILES = {
            {"war3map.w3u", "units"},           // Custom unit data
            {"war3map.w3t", "items"},           // Custom item data
            {"war3map.w3b", "destructibles"},   // Custom destructible data
            {"war3map.w3d", "doodads"},         // Custom doodad data
            {"war3map.w3a", "abilities"},        // Custom ability data
            {"war3map.w3h", "buffs_effects"},    // Custom buff/effect data
            {"war3map.w3q", "upgrades"},         // Custom upgrade data
    };

    /**
     * Extracts the raw Object Editor binary files (war3map.w3u, .w3t, .w3b, etc.)
     * from the MPQ and saves them into an {@code object_data/} subfolder.
     * These files contain ALL custom object settings (stats, sounds, abilities,
     * pathing, everything in the Object Editor) and can be merged back on import.
     */
    private void exportRawObjectFiles(JMpqEditor mpq, File outputFolder, Consumer<String> log) {
        Path objectDataDir = outputFolder.toPath().resolve("object_data");
        int count = 0;
        for (String[] entry : RAW_OBJECT_FILES) {
            String mpqName = entry[0];
            try {
                if (!mpq.hasFile(mpqName)) continue;
                byte[] data = mpq.extractFileAsBytes(mpqName);
                if (data == null || data.length == 0) continue;

                Files.createDirectories(objectDataDir);
                Path dest = objectDataDir.resolve(mpqName);
                Files.write(dest, data);
                count++;
                log.accept("Exported object data: " + mpqName + " (" + data.length + " bytes)");
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Could not export " + mpqName, e);
                log.accept("Warning: could not export " + mpqName + ": " + e.getMessage());
            }
        }
        if (count > 0) {
            log.accept("Exported " + count + " Object Editor data file(s) to object_data/");
        }
    }

    private void writeObjJson(StringBuilder sb, ObjMod.Obj obj) {
        sb.append("  {\n");
        sb.append("    \"id\": \"").append(escapeJson(obj.getId().toString())).append("\",\n");
        sb.append("    \"baseId\": \"").append(escapeJson(
                obj.getBaseId() != null ? obj.getBaseId().toString() : "")).append("\",\n");

        sb.append("    \"fields\": {\n");
        List<ObjMod.Obj.Mod> mods = obj.getMods();
        if (mods == null) mods = List.of();
        for (int i = 0; i < mods.size(); i++) {
            ObjMod.Obj.Mod mod = mods.get(i);
            String fieldId = mod.getId().toString();
            String valType = mod.getValType() != null ? mod.getValType().name() : "STRING";
            String value = mod.getVal() != null ? mod.getVal().toString() : "";

            sb.append("      \"").append(escapeJson(fieldId)).append("\": { ");
            sb.append("\"type\": \"").append(valType).append("\", ");
            sb.append("\"value\": \"").append(escapeJson(value)).append("\" }");
            if (i < mods.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("    }\n");
        sb.append("  }");
    }

    private static Path resolveUniquePath(Path dir, String filename) {
        Path dest = dir.resolve(filename);
        int counter = 1;
        while (Files.exists(dest)) {
            int dot = filename.lastIndexOf('.');
            String base = dot >= 0 ? filename.substring(0, dot) : filename;
            String ext = dot >= 0 ? filename.substring(dot) : "";
            dest = dir.resolve(base + "_" + counter + ext);
            counter++;
        }
        return dest;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
