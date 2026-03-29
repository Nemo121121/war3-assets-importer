package com.hiveworkshop.war3assetsimporter.core.util;

import java.util.Set;
import java.util.logging.Logger;

/**
 * Generates unique 4-character doodad IDs for Warcraft 3 custom doodad definitions.
 * IDs follow the format [A-Z][0-9]{3} (uppercase, e.g. D000, D001 … Z999, A000 …).
 * Starts at 'D' to avoid collisions with common built-in doodad ID ranges.
 */
public class DoodadIDGenerator {

    private static final Logger LOG = Logger.getLogger(DoodadIDGenerator.class.getName());

    /**
     * Returns the next available doodad ID not already in {@code existingIds}.
     *
     * @param existingIds set of already-used doodad IDs
     * @return next unique ID string, or {@code null} if all 26,000 IDs are exhausted
     */
    public static String generateNextId(Set<String> existingIds) {
        LOG.fine("Generating next doodad ID (pool size: " + existingIds.size() + " already used)");
        // Start at 'D' (index 3)
        int startIndex = 'D' - 'A';

        for (int i = 0; i < 26; i++) {
            char prefix = (char) ('A' + (startIndex + i) % 26);

            for (int j = 0; j <= 999; j++) {
                String id = String.format("%c%03d", prefix, j);
                if (!existingIds.contains(id)) {
                    LOG.fine("Generated doodad ID: " + id);
                    return id;
                }
            }
        }

        LOG.warning("Doodad ID space exhausted — all 26,000 IDs are in use");
        return null;
    }
}
