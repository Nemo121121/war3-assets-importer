package com.hiveworkshop.war3assetsimporter.core.model;

/**
 * Represents a custom asset already present inside a map's MPQ archive.
 *
 * @param path      the in-MPQ path (e.g. {@code "war3mapImported\\MyModel.mdx"})
 * @param size      file size in bytes, or -1 if unknown
 * @param category  the asset category
 * @param ownerName display name of the owning object (unit/item/doodad name); used to
 *                  create per-object subfolders on export. May be empty for uncategorised assets.
 */
public record MapAssetEntry(String path, long size, Category category, String ownerName) {

    /** Convenience constructor without ownerName. */
    public MapAssetEntry(String path, long size, Category category) {
        this(path, size, category, "");
    }

    public enum Category {
        UNIT_MODEL, BUILDING_MODEL, ITEM_MODEL, DESTRUCTIBLE_MODEL, DOODAD_MODEL,
        ABILITY, BUFF_EFFECT, UPGRADE,
        TEXTURE, SOUND, OTHER;

        /** Returns the subfolder name used when exporting. */
        public String folderName() {
            return switch (this) {
                case UNIT_MODEL -> "units";
                case BUILDING_MODEL -> "buildings";
                case ITEM_MODEL -> "items";
                case DESTRUCTIBLE_MODEL -> "destructibles";
                case DOODAD_MODEL -> "doodads";
                case ABILITY -> "abilities";
                case BUFF_EFFECT -> "buffs_effects";
                case UPGRADE -> "upgrades";
                case TEXTURE -> "textures";
                case SOUND -> "sounds";
                case OTHER -> "other";
            };
        }
    }

    /** Convenience: true if this is any MDX model file. */
    public boolean isMdx() {
        return category == Category.UNIT_MODEL
                || category == Category.BUILDING_MODEL
                || category == Category.ITEM_MODEL
                || category == Category.DESTRUCTIBLE_MODEL
                || category == Category.DOODAD_MODEL;
    }

    /** Returns just the filename portion of the path (after the last separator). */
    public String filename() {
        int sep = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
        return sep >= 0 ? path.substring(sep + 1) : path;
    }
}
