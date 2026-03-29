package com.hiveworkshop.war3assetsimporter.core.service;

import com.hiveworkshop.war3assetsimporter.core.model.MapAssetEntry;
import com.hiveworkshop.war3assetsimporter.core.model.MapAssetEntry.Category;
import net.moonlightflower.wc3libs.bin.ObjMod;
import net.moonlightflower.wc3libs.bin.Wc3BinInputStream;
import net.moonlightflower.wc3libs.bin.app.objMod.W3D;
import net.moonlightflower.wc3libs.bin.app.objMod.W3U;
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
 * subfolders and writes complete object-editor definition data alongside the
 * exported models so they can be fully recreated on import.
 */
public class ExportService {

    private static final Logger LOG = Logger.getLogger(ExportService.class.getName());

    /**
     * Exports the specified assets from a map file into categorised subfolders
     * under {@code outputFolder}, and writes complete definition JSON files.
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

            // ---- Write complete object definition data ----
            writeUnitDefinitions(mpq, assetEntries, outputFolder, Category.UNIT_MODEL, log);
            writeUnitDefinitions(mpq, assetEntries, outputFolder, Category.BUILDING_MODEL, log);
            writeDoodadDefinitions(mpq, assetEntries, outputFolder, log);

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to open map for export", ex);
            log.accept("Error opening map: " + ex.getMessage());
        }

        return exported;
    }

    /**
     * Writes ALL object-editor fields for unit/building definitions to definitions.json.
     * Every modification (mod) on each W3U object is serialised with its field ID,
     * value type, and value so it can be fully recreated on import.
     */
    private void writeUnitDefinitions(JMpqEditor mpq, List<MapAssetEntry> entries,
                                      File outputFolder, Category category, Consumer<String> log) {
        if (!mpq.hasFile("war3map.w3u")) return;

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
                writeObjJson(sb, obj);
            }
            sb.append("\n]\n");

            if (!first) {
                Path subDir = outputFolder.toPath().resolve(category.folderName());
                Files.createDirectories(subDir);
                Path metaFile = subDir.resolve("definitions.json");
                Files.writeString(metaFile, sb.toString(), StandardCharsets.UTF_8);
                log.accept("Wrote " + category.folderName() + " definitions ("
                        + countEntries(sb) + " objects) to: definitions.json");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not write " + category.folderName() + " definitions", e);
            log.accept("Warning: could not write " + category.folderName() + " definitions: " + e.getMessage());
        }
    }

    /**
     * Writes ALL object-editor fields for doodad definitions to definitions.json.
     */
    private void writeDoodadDefinitions(JMpqEditor mpq, List<MapAssetEntry> entries,
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
                writeObjJson(sb, obj);
            }
            sb.append("\n]\n");

            if (!first) {
                Path subDir = outputFolder.toPath().resolve(Category.DOODAD_MODEL.folderName());
                Files.createDirectories(subDir);
                Path metaFile = subDir.resolve("definitions.json");
                Files.writeString(metaFile, sb.toString(), StandardCharsets.UTF_8);
                log.accept("Wrote doodad definitions to: definitions.json");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Could not write doodad definitions", e);
            log.accept("Warning: could not write doodad definitions: " + e.getMessage());
        }
    }

    /**
     * Writes a single ObjMod.Obj (unit, building, or doodad) as a JSON object,
     * including ALL modification fields with their type information.
     */
    private void writeObjJson(StringBuilder sb, ObjMod.Obj obj) {
        sb.append("  {\n");
        sb.append("    \"id\": \"").append(escapeJson(obj.getId().toString())).append("\",\n");
        sb.append("    \"baseId\": \"").append(escapeJson(
                obj.getBaseId() != null ? obj.getBaseId().toString() : "")).append("\",\n");

        // Write every field modification with its type
        sb.append("    \"fields\": {\n");
        List<ObjMod.Obj.Mod> mods = obj.getMods();
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

    private int countEntries(StringBuilder sb) {
        int count = 0;
        for (int i = 0; i < sb.length(); i++) {
            if (sb.charAt(i) == '{' && i > 0 && sb.charAt(i - 1) != '{') count++;
        }
        // Rough count: top-level objects = count of "  {" patterns
        return Math.max(1, count / 2);
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
