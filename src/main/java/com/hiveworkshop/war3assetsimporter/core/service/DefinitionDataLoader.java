package com.hiveworkshop.war3assetsimporter.core.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.moonlightflower.wc3libs.bin.ObjMod;
import net.moonlightflower.wc3libs.dataTypes.app.War3Int;
import net.moonlightflower.wc3libs.dataTypes.app.War3Real;
import net.moonlightflower.wc3libs.dataTypes.app.War3String;
import net.moonlightflower.wc3libs.misc.MetaFieldId;

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
 * Reads {@code definitions.json} files (produced by ExportService) and provides
 * a lookup from model path to saved field data.  Used by ImportService to
 * recreate units/buildings/doodads with all their original Object Editor properties.
 */
public class DefinitionDataLoader {

    private static final Logger LOG = Logger.getLogger(DefinitionDataLoader.class.getName());

    /**
     * Parsed definition: base ID + all field modifications.
     */
    public record SavedDefinition(String baseId, Map<String, FieldEntry> fields) {
    }

    /**
     * A single field value with its W3U/W3D type.
     */
    public record FieldEntry(String type, String value) {
    }

    /**
     * Scans the given folder (and its {@code units/}, {@code buildings/}, {@code doodads/}
     * subfolders) for {@code definitions.json} files and builds a model-path → definition lookup.
     *
     * <p>The model path key is the value of the {@code umdl} (units/buildings) or {@code dfil}
     * (doodads) field stored in the JSON.
     *
     * @param rootFolder the assets root folder selected by the user
     * @param log        receives log messages; may be {@code null}
     * @return map from model path → saved definition, possibly empty
     */
    public static Map<String, SavedDefinition> loadAll(File rootFolder, Consumer<String> log) {
        if (log == null) log = s -> {};
        Map<String, SavedDefinition> result = new LinkedHashMap<>();

        // Check root folder and each standard subfolder
        String[] subFolders = {"", "units", "buildings", "doodads"};
        for (String sub : subFolders) {
            File dir = sub.isEmpty() ? rootFolder : new File(rootFolder, sub);
            File defFile = new File(dir, "definitions.json");
            if (!defFile.exists()) continue;

            try {
                String json = Files.readString(defFile.toPath(), StandardCharsets.UTF_8);
                JsonArray arr = JsonParser.parseString(json).getAsJsonArray();

                int count = 0;
                for (JsonElement elem : arr) {
                    JsonObject obj = elem.getAsJsonObject();
                    String baseId = obj.has("baseId") ? obj.get("baseId").getAsString() : "";

                    // Parse all fields
                    Map<String, FieldEntry> fields = new LinkedHashMap<>();
                    if (obj.has("fields") && obj.get("fields").isJsonObject()) {
                        for (var entry : obj.getAsJsonObject("fields").entrySet()) {
                            JsonObject fieldObj = entry.getValue().getAsJsonObject();
                            String type = fieldObj.has("type") ? fieldObj.get("type").getAsString() : "STRING";
                            String value = fieldObj.has("value") ? fieldObj.get("value").getAsString() : "";
                            fields.put(entry.getKey(), new FieldEntry(type, value));
                        }
                    }

                    // Determine the model path key (umdl for units/buildings, dfil for doodads)
                    String modelPath = null;
                    if (fields.containsKey("umdl")) {
                        modelPath = fields.get("umdl").value();
                    } else if (fields.containsKey("dfil")) {
                        modelPath = fields.get("dfil").value();
                    }

                    if (modelPath != null && !modelPath.isEmpty()) {
                        result.put(modelPath, new SavedDefinition(baseId, fields));
                        count++;
                    }
                }

                if (count > 0) {
                    log.accept("Loaded " + count + " saved definition(s) from: "
                            + (sub.isEmpty() ? "definitions.json" : sub + "/definitions.json"));
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Could not parse definitions.json in " + dir, e);
                log.accept("Warning: could not read " + defFile + ": " + e.getMessage());
            }
        }

        return result;
    }

    /**
     * Applies all saved field modifications from a {@link SavedDefinition} to a
     * W3U/W3D object, skipping the model path field (which is set separately by the caller).
     *
     * @param obj             the new W3U or W3D object to populate
     * @param def             the saved definition data
     * @param modelPathField  the field ID for the model path ("umdl" or "dfil") — skipped
     *                        since the caller sets it to the new import path
     * @param log             log callback
     */
    public static void applyFields(ObjMod.Obj obj, SavedDefinition def,
                                   String modelPathField, Consumer<String> log) {
        int applied = 0;
        for (var entry : def.fields().entrySet()) {
            String fieldId = entry.getKey();
            // Skip the model path field — the caller sets it to the new import path
            if (fieldId.equals(modelPathField)) continue;

            FieldEntry fe = entry.getValue();
            try {
                MetaFieldId metaId = MetaFieldId.valueOf(fieldId);
                switch (fe.type()) {
                    case "INT" -> obj.set(metaId, War3Int.valueOf(Integer.parseInt(fe.value())));
                    case "REAL", "UNREAL" -> obj.set(metaId, new War3Real(Float.parseFloat(fe.value())));
                    default -> obj.set(metaId, new War3String(fe.value()));
                }
                applied++;
            } catch (Exception e) {
                LOG.log(Level.FINE, "Could not apply field " + fieldId + "=" + fe.value(), e);
            }
        }
        if (applied > 0 && log != null) {
            log.accept("  Applied " + applied + " saved field(s) from definitions.json");
        }
    }
}
