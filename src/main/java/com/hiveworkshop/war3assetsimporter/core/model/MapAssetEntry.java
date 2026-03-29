package com.hiveworkshop.war3assetsimporter.core.model;

/**
 * Represents a custom asset (MDX model or texture) already present inside a map's MPQ archive.
 *
 * @param path     the in-MPQ path (e.g. {@code "war3mapImported\\MyModel.mdx"})
 * @param size     file size in bytes, or -1 if unknown
 * @param isMdx    true if this is an MDX model file
 */
public record MapAssetEntry(String path, long size, boolean isMdx) {

    /**
     * Returns just the filename portion of the path (after the last separator).
     */
    public String filename() {
        int sep = Math.max(path.lastIndexOf('\\'), path.lastIndexOf('/'));
        return sep >= 0 ? path.substring(sep + 1) : path;
    }
}
