package com.hiveworkshop.war3assetsimporter.core.service;

import systems.crigges.jmpq3.JMpqEditor;
import systems.crigges.jmpq3.MPQOpenOption;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts custom assets (MDX/BLP files) from a Warcraft 3 map's MPQ archive
 * into a user-chosen directory on disk.
 */
public class ExportService {

    private static final Logger LOG = Logger.getLogger(ExportService.class.getName());

    /**
     * Exports the specified assets from a map file to a destination folder.
     *
     * @param mapFile       the source .w3x/.w3m map file
     * @param assetPaths    set of in-MPQ paths to extract
     * @param outputFolder  destination directory (created if absent)
     * @param log           receives log messages; may be {@code null}
     * @return list of successfully exported file paths
     */
    public List<Path> exportAssets(File mapFile, Set<String> assetPaths,
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
            for (String mpqPath : assetPaths) {
                try {
                    if (!mpq.hasFile(mpqPath)) {
                        log.accept("Not found in map: " + mpqPath);
                        continue;
                    }
                    byte[] data = mpq.extractFileAsBytes(mpqPath);

                    // Derive a safe filename from the MPQ path
                    String filename = mpqPath.replace('\\', '/');
                    int sep = filename.lastIndexOf('/');
                    if (sep >= 0) filename = filename.substring(sep + 1);

                    Path dest = outputFolder.toPath().resolve(filename);
                    // Avoid overwriting — append a suffix if needed
                    int counter = 1;
                    while (Files.exists(dest)) {
                        int dot = filename.lastIndexOf('.');
                        String base = dot >= 0 ? filename.substring(0, dot) : filename;
                        String ext = dot >= 0 ? filename.substring(dot) : "";
                        dest = outputFolder.toPath().resolve(base + "_" + counter + ext);
                        counter++;
                    }

                    Files.write(dest, data);
                    exported.add(dest);
                    log.accept("Exported: " + mpqPath + " -> " + dest.getFileName());
                } catch (Exception ex) {
                    LOG.log(Level.WARNING, "Failed to export: " + mpqPath, ex);
                    log.accept("Error exporting " + mpqPath + ": " + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to open map for export", ex);
            log.accept("Error opening map: " + ex.getMessage());
        }

        return exported;
    }
}
