package com.hiveworkshop.war3assetsimporter.core.service;

import com.hiveworkshop.war3assetsimporter.core.model.MapAssetEntry;
import com.hiveworkshop.war3assetsimporter.core.model.MapAssetEntry.Category;
import net.moonlightflower.wc3libs.bin.Wc3BinInputStream;
import net.moonlightflower.wc3libs.bin.app.objMod.W3D;
import net.moonlightflower.wc3libs.bin.app.objMod.W3U;
import net.moonlightflower.wc3libs.misc.MetaFieldId;
import net.moonlightflower.wc3libs.misc.ObjId;
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
 * subfolders (units/, buildings/, doodads/, textures/, sounds/) and writes
 * object definition metadata alongside the exported models.
 */
public class ExportService {

    private static final Logger LOG = Logger.getLogger(ExportService.class.getName());

    /** W3U fields to export for unit/building metadata. */
    private static final String[] UNIT_FIELDS = {"unam", "umdl", "usca", "uico", "uani", "ubdg"};
    /** W3D fields to export for doodad metadata. */
    private static final String[] DOODAD_FIELDS = {"dfil", "dnam"};

    /**
     * Exports the specified assets from a map file into categorised subfolders
     * under {@code outputFolder}, and writes definition metadata JSON files.
     *
     * @param mapFile       the source .w3x/.w3m map file
     * @param assetEntries  list of assets to export (with category information)
     * @param outputFolder  destination root directory (created if absent)
     * @param log           receives log messages; may be {@code null}
     * @return list of successfully exported file paths
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

            // ---- Extract files into categorised subfolders ----
            for (MapAssetEntry entry : assetEntries) {
                try {
                    String mpqPath = entry.path();
                    if (!mpq.hasFile(mpqPath)) {
                        log.accept("Not found in map: " + mpqPath);
                        continue;
                    }
                    byte[] data = mpq.extractFileAsBytes(mpqPath);

                    // Determine subfolder from category
                    String subFolder = entry.category().folderName();
                    Path subDir = outputFolder.toPath().resolve(subFolder);
                    Files.createDirectories(subDir);

                    String filename = entry.filename();
                    Path dest = resolveUniquePath(subDir, filename);

                    Files.write(dest, data);
                    exported.add(dest);
                    log.accept("Exported: " + mpqPath + " -> " + subFolder + "/" + dest.getFileName());
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Failed to export: " + entry.path(), ex);
                    log.accept("Error exporting " + entry.path() + ": " + ex.getMessage());
                }
            }

            // ---- Write object definition metadata ----
            writeUnitMetadata(mpq, assetEntries, outputFolder, Category.UNIT_MODEL, log);
            writeUnitMetadata(mpq, assetEntries, outputFolder, Category.BUILDING_MODEL, log);
            writeDoodadMetadata(mpq, assetEntries, outputFolder, log);

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to open map for export", ex);
            log.accept("Error opening map: " + ex.getMessage());
        }

        return exported;
    }

    /**
     * Writes a JSON metadata file for unit or building definitions whose model
     * path matches one of the exported assets in the given category.
     */
    private void writeUnitMetadata(JMpqEditor mpq, List<MapAssetEntry> entries,
                                   File outputFolder, Category category, Consumer<String> log) {
        if (!mpq.hasFile("war3map.w3u")) return;

        // Collect model paths for this category
        Set<String> modelPaths = new HashSet<>();
        for (MapAssetEntry e : entries) {
            if (e.category() == category) modelPaths.add(e.path());
        }
        if (modelPaths.isEmpty()) return;

        try {
            W3U w3u = new W3U(new Wc3BinInputStream(
                    new ByteArrayInputStream(mpq.extractFileAsBytes("war3map.w3u"))));

            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            boolean first = true;

            for (W3U.Obj obj : w3u.getObjsList()) {
                Object mdlVal = obj.get(MetaFieldId.valueOf("umdl"));
                if (mdlVal == null) continue;
                if (!modelPaths.contains(mdlVal.toString())) continue;

                if (!first) sb.append(",\n");
                first = false;
                sb.append("  {\n");
                sb.append("    \"id\": \"").append(escapeJson(obj.getId().toString())).append("\",\n");
                sb.append("    \"baseId\": \"").append(escapeJson(
                        obj.getBaseId() != null ? obj.getBaseId().toString() : "")).append("\",\n");
                for (String field : UNIT_FIELDS) {
                    Object val = obj.get(MetaFieldId.valueOf(field));
                    if (val != null) {
                        sb.append("    \"").append(field).append("\": \"")
                                .append(escapeJson(val.toString())).append("\",\n");
                    }
                }
                // Remove trailing comma
                if (sb.charAt(sb.length() - 2) == ',') {
                    sb.deleteCharAt(sb.length() - 2);
                }
                sb.append("  }");
            }
            sb.append("\n]\n");

            if (!first) { // At least one entry was written
                Path subDir = outputFolder.toPath().resolve(category.folderName());
                Files.createDirectories(subDir);
                Path metaFile = subDir.resolve("definitions.json");
                Files.writeString(metaFile, sb.toString(), StandardCharsets.UTF_8);
                log.accept("Wrote " + category.folderName() + " definitions to: " + metaFile.getFileName());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not write " + category.folderName() + " metadata", e);
            log.accept("Warning: could not write " + category.folderName() + " metadata: " + e.getMessage());
        }
    }

    /**
     * Writes a JSON metadata file for doodad definitions whose model path
     * matches one of the exported doodad assets.
     */
    private void writeDoodadMetadata(JMpqEditor mpq, List<MapAssetEntry> entries,
                                     File outputFolder, Consumer<String> log) {
        if (!mpq.hasFile("war3map.w3d")) return;

        Set<String> modelPaths = new HashSet<>();
        for (MapAssetEntry e : entries) {
            if (e.category() == Category.DOODAD_MODEL) modelPaths.add(e.path());
        }
        if (modelPaths.isEmpty()) return;

        try {
            W3D w3d = new W3D(new Wc3BinInputStream(
                    new ByteArrayInputStream(mpq.extractFileAsBytes("war3map.w3d"))));

            StringBuilder sb = new StringBuilder();
            sb.append("[\n");
            boolean first = true;

            for (W3D.Dood obj : w3d.getObjsList()) {
                Object mdlVal = obj.get(MetaFieldId.valueOf("dfil"));
                if (mdlVal == null) continue;
                if (!modelPaths.contains(mdlVal.toString())) continue;

                if (!first) sb.append(",\n");
                first = false;
                sb.append("  {\n");
                sb.append("    \"id\": \"").append(escapeJson(obj.getId().toString())).append("\",\n");
                sb.append("    \"baseId\": \"").append(escapeJson(
                        obj.getBaseId() != null ? obj.getBaseId().toString() : "")).append("\",\n");
                for (String field : DOODAD_FIELDS) {
                    Object val = obj.get(MetaFieldId.valueOf(field));
                    if (val != null) {
                        sb.append("    \"").append(field).append("\": \"")
                                .append(escapeJson(val.toString())).append("\",\n");
                    }
                }
                if (sb.charAt(sb.length() - 2) == ',') {
                    sb.deleteCharAt(sb.length() - 2);
                }
                sb.append("  }");
            }
            sb.append("\n]\n");

            if (!first) {
                Path subDir = outputFolder.toPath().resolve(Category.DOODAD_MODEL.folderName());
                Files.createDirectories(subDir);
                Path metaFile = subDir.resolve("definitions.json");
                Files.writeString(metaFile, sb.toString(), StandardCharsets.UTF_8);
                log.accept("Wrote doodad definitions to: " + metaFile.getFileName());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not write doodad metadata", e);
            log.accept("Warning: could not write doodad metadata: " + e.getMessage());
        }
    }

    /** Finds a unique filename in {@code dir}, appending _1, _2 etc. if needed. */
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
