package org.example.core.service;

import net.moonlightflower.wc3libs.bin.app.W3I;
import net.moonlightflower.wc3libs.txt.WTS;
import org.example.core.model.MapMetadata;
import org.example.core.util.CameraBounds;
import org.example.core.util.StringUtils;
import systems.crigges.jmpq3.JMpqEditor;
import systems.crigges.jmpq3.MPQOpenOption;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Reads Warcraft 3 map metadata (name, author, version, description, preview image,
 * camera bounds) from a .w3x/.w3m MPQ archive.
 * Used by both the GUI (to populate MapOptionsPanel) and the CLI (for informational output).
 */
public class MapMetadataService {

    /**
     * Opens the map MPQ, extracts W3I + WTS files, and returns a {@link MapMetadata} record.
     *
     * <p>The preview image is always extracted when present.  If the W3I cannot be parsed
     * (e.g. a Reforged map with an unsupported format version), the method still returns a
     * valid {@link MapMetadata} whose {@link MapMetadata#loadWarning()} is non-null rather
     * than propagating the exception, so the UI can display the preview and allow the user
     * to proceed with the import.
     *
     * @param mapFile the .w3x or .w3m map file
     * @return parsed (or partially parsed) map metadata — never {@code null}
     * @throws Exception only if the MPQ archive itself cannot be opened
     */
    public MapMetadata loadMetadata(File mapFile) throws Exception {
        try (JMpqEditor mpqEditor = new JMpqEditor(mapFile, MPQOpenOption.FORCE_V0)) {

            // --- Step 1: preview image (independent of W3I format) ---
            byte[] previewBytes = null;
            if (mpqEditor.hasFile("war3mapMap.blp")) {
                previewBytes = mpqEditor.extractFileAsBytes("war3mapMap.blp");
            }

            // --- Step 2: W3I (may fail for Reforged / unknown format versions) ---
            W3I w3i;
            try {
                w3i = new W3I(mpqEditor.extractFileAsBytes("war3map.w3i"));
            } catch (Exception e) {
                // Return partial metadata so the UI still shows the preview and
                // the map file remains selectable for processing.
                return new MapMetadata("", "", "", "", "",
                        previewBytes, CameraBounds.getInstance(),
                        "W3I metadata not available (" + e.getMessage() + ")");
            }

            // --- Step 3: WTS (optional — some maps have no localised strings) ---
            Map<String, String> namedEntries = Map.of();
            if (mpqEditor.hasFile("war3map.wts")) {
                try {
                    WTS wts = new WTS(new ByteArrayInputStream(
                            mpqEditor.extractFileAsBytes("war3map.wts")));
                    namedEntries = wts.getNamedEntries();
                } catch (Exception ignored) {
                    // WTS parse failure is non-fatal; raw strings will be used
                }
            }

            String gameVersion  = StringUtils.buildGameVersionInfo(w3i);
            String editorVersion = String.valueOf(w3i.getEditorVersion());
            String name   = resolveTrigStr(w3i.getMapName(),        namedEntries, "<unknown>");
            String author = resolveTrigStr(w3i.getMapAuthor(),      namedEntries, "<unknown>");
            String desc   = resolveTrigStr(w3i.getMapDescription(), namedEntries, "<no description>");

            CameraBounds bounds = CameraBounds.getInstance();
            bounds.setCameraBounds(
                    w3i.getCameraBounds1(),
                    w3i.getCameraBounds2(),
                    w3i.getCameraBounds3(),
                    w3i.getCameraBounds4()
            );

            return new MapMetadata(name, author, gameVersion, editorVersion, desc, previewBytes, bounds);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves a value from a W3I field that may be either:
     * <ul>
     *   <li>A plain string (non-localised maps) — returned as-is.</li>
     *   <li>A WTS trigger-string reference like {@code "TRIGSTR_001"} — looked up
     *       in {@code namedEntries}.  Several key formats are tried in order
     *       ({@code "TRIGSTR_001"}, {@code "001"}, {@code "STRING 1"}) to cope with
     *       different wc3libs serialisation styles.</li>
     * </ul>
     *
     * @param raw          the raw string stored in the W3I field (may be {@code null})
     * @param namedEntries the WTS entry map returned by {@link WTS#getNamedEntries()}
     * @param fallback     the value to return when {@code raw} is {@code null} or blank
     * @return             the resolved, trimmed display string
     */
    private static String resolveTrigStr(String raw, Map<String, String> namedEntries, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;

        // 1. Direct lookup — works when wc3libs uses "TRIGSTR_NNN" as map key
        String resolved = namedEntries.get(raw);
        if (resolved != null) return resolved.trim();

        // 2. The raw value is a TRIGSTR reference; WTS may use a different key format
        if (raw.startsWith("TRIGSTR_")) {
            String numPart = raw.substring("TRIGSTR_".length()); // e.g. "001"

            // 2a. Bare numeric string: "001"
            resolved = namedEntries.get(numPart);
            if (resolved != null) return resolved.trim();

            // 2b. "STRING N" with decimal (no leading zeros): "STRING 1"
            try {
                int num = Integer.parseInt(numPart);
                resolved = namedEntries.get("STRING " + num);
                if (resolved != null) return resolved.trim();
            } catch (NumberFormatException ignored) {}

            // 2c. Fallback: the TRIGSTR token itself is the best we can show
            return raw;
        }

        // 3. Not a TRIGSTR reference — it is the literal string value
        return raw.trim();
    }
}
