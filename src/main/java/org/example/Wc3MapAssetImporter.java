package org.example;


import net.moonlightflower.wc3libs.bin.Wc3BinOutputStream;
import net.moonlightflower.wc3libs.bin.app.IMP;
import systems.crigges.jmpq3.JMpqEditor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

public class Wc3MapAssetImporter {
//    public static void importAsset(File mapFile, File assetFile, String targetPathInMap, File outputFile) throws IOException {
//        // Step 1: Load the .w3x archive
//        MPQArchive archive = new MPQArchive(mapFile);
//
//        System.out.println("Opened map: " + mapFile.getName());
//
//        // Step 2: Read model file into bytes
//        byte[] assetBytes = Files.readAllBytes(assetFile.toPath());
//
//        // Step 3: Insert or replace file in archive
//        archive.addFile(targetPathInMap, assetBytes);
//        System.out.println("Added asset to map: " + targetPathInMap);
//
//        // Step 4: Save as new file
//        archive.writeTo(outputFile);
//        System.out.println("Saved updated map to: " + outputFile.getAbsolutePath());
//    }

    private static LinkedList<File> getFilesOfDirectory(File dir, LinkedList<File> addTo) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                getFilesOfDirectory(f, addTo);
            } else {
                addTo.add(f);
            }
        }
        return addTo;

    }

    private static void insertImportedFiles(JMpqEditor mpq, List<File> directories) throws Exception {
        IMP importFile = new IMP();
        for (File directory : directories) {
            LinkedList<File> files = new LinkedList<>();
            getFilesOfDirectory(directory, files);

            for (File f : files) {
                Path p = f.toPath();
                p = directory.toPath().relativize(p);
                String normalizedWc3Path = p.toString().replaceAll("/", "\\\\");
                IMP.Obj importObj = new IMP.Obj();
                importObj.setPath(normalizedWc3Path);
                importObj.setStdFlag(IMP.StdFlag.CUSTOM);
                importFile.addObj(importObj);
                mpq.deleteFile(normalizedWc3Path);
                mpq.insertFile(normalizedWc3Path, f, false);
            }
        }
        mpq.deleteFile(IMP.GAME_PATH);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (Wc3BinOutputStream wc3BinOutputStream = new Wc3BinOutputStream(byteArrayOutputStream)) {
            importFile.write(wc3BinOutputStream);
        }
        byteArrayOutputStream.flush();
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        mpq.insertByteArray(IMP.GAME_PATH, byteArray);
    }

    public static void importAssetFiles(JMpqEditor ed, File projectFolder) {
        LinkedList<File> folders = new LinkedList<>();
        folders.add(projectFolder);

        folders.removeIf(folder -> !folder.exists());

        try {
            insertImportedFiles(ed, folders);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

}
