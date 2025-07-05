package org.example;

import net.moonlightflower.wc3libs.bin.app.W3I;
import net.moonlightflower.wc3libs.txt.WTS;
import systems.crigges.jmpq3.JMpqEditor;
import systems.crigges.jmpq3.MPQOpenOption;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;

public class War3ModelImporterGUI {
    private JFrame frame;
    private JTextArea logArea;
    private File mapFile;
    private File modelsFolder;
    private MapOptionsPanel optionsPanel;
    private AssetTreePanel assetTreePanel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new War3ModelImporterGUI().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Warcraft 3 Model Importer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Buttons Panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());

        JButton openMapButton = new JButton("Open Map");
        JButton importModelsButton = new JButton("Import Models Folder");
        JButton processButton = new JButton("Process and Save");

        buttonPanel.add(openMapButton);
        buttonPanel.add(importModelsButton);
        buttonPanel.add(processButton);

        // Log Area
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        // Asset tree (right side)
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Model Assets");

        optionsPanel = new MapOptionsPanel();
        assetTreePanel = new AssetTreePanel();

        JSplitPane leftRightPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, optionsPanel, assetTreePanel);
        leftRightPane.setResizeWeight(0.3);
        leftRightPane.setDividerLocation(250);

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(600, 150));

        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, leftRightPane, logScrollPane);
        verticalSplit.setResizeWeight(0.8);
        verticalSplit.setDividerLocation(300);
        verticalSplit.setOneTouchExpandable(true);

        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(verticalSplit, BorderLayout.CENTER);

        frame.getContentPane().add(panel);
        frame.setVisible(true);

        // Button Actions
        openMapButton.addActionListener(this::onOpenMap);
        importModelsButton.addActionListener(this::onImportModels);
        processButton.addActionListener(this::onProcess);
    }

    private void onOpenMap(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser(new File("src/test-sample"));
        fileChooser.setDialogTitle("Select Warcraft 3 Map (.w3x/.w3m)");
        int result = fileChooser.showOpenDialog(frame);

        if (result != JFileChooser.APPROVE_OPTION) return;

        mapFile = fileChooser.getSelectedFile();
        log("Selected map: " + mapFile.getAbsolutePath());

        try (JMpqEditor mpqEditor = new JMpqEditor(mapFile, MPQOpenOption.FORCE_V0)) {
            displayMapInfo(mpqEditor);
        } catch (Exception ex) {
            log("Error loading map: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private String buildGameVersionInfo(W3I w3i) {
        // Format: Game Version: major.minor.rev.build
        return String.format(
                "%d.%d.%d.%d",
                w3i.getGameVersion_major(),
                w3i.getGameVersion_minor(),
                w3i.getGameVersion_rev(),
                w3i.getGameVersion_build()
        );
    }

    private void displayMapInfo(JMpqEditor mpqEditor) throws Exception {
        W3I w3i = new W3I(mpqEditor.extractFileAsBytes("war3map.w3i"));
        WTS wts = new WTS(new ByteArrayInputStream(mpqEditor.extractFileAsBytes("war3map.wts")));
        Map<String, String> namedEntries = wts.getNamedEntries();

        String gameVersion = buildGameVersionInfo(w3i);
        String editorVersion = String.valueOf(w3i.getEditorVersion());
        String name = namedEntries.getOrDefault(w3i.getMapName(), "<unknown>");
        String author = namedEntries.getOrDefault(w3i.getMapAuthor(), "<unknown>");
        String desc = namedEntries.getOrDefault(w3i.getMapDescription(), "<no description>");

        optionsPanel.setMapName(name);
        optionsPanel.setDescription(desc);
        optionsPanel.setAuthor(author);
        optionsPanel.setMapVersion(gameVersion);
        optionsPanel.setEditorVersion(editorVersion);

        String info = String.format(
                "%s - Author: %s\nWidth: %d\nHeight: %d\nPlayers: %d\nDescription: %s",
                name,
                author,
                w3i.getWidth(),
                w3i.getHeight(),
                w3i.getPlayers().size(),
                desc
        );

        logArea.append("Map Info: " + info + "\n");
    }


    private final java.util.List<String> mdxFiles = new ArrayList<String>();
    private final java.util.List<String> blpFiles = new ArrayList<String>();

    private void onImportModels(ActionEvent e) {
        JFileChooser dirChooser = new JFileChooser((new File("src/test-sample")));
        dirChooser.setDialogTitle("Select Folder with Models");
        dirChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = dirChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            modelsFolder = dirChooser.getSelectedFile();
            log("Selected model folder: " + modelsFolder.getAbsolutePath());

            mdxFiles.clear();
            blpFiles.clear();

            try {
                Files.walk(modelsFolder.toPath())
                        .filter(Files::isRegularFile)
                        .forEach(path -> {
                            String relPath = modelsFolder.toPath().relativize(path).toString().replace("\\", "/");
                            if (relPath.toLowerCase().endsWith(".mdx")) {
                                mdxFiles.add(relPath);
                            } else if (relPath.toLowerCase().endsWith(".blp")) {
                                blpFiles.add(relPath);
                            }
                        });

                log("Found " + mdxFiles.size() + " .mdx files");
                log("Found " + blpFiles.size() + " .blp files");

            } catch (IOException ex) {
                log("Error reading files: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
        updateAssetTree();
    }

    private void updateAssetTree() {
        assetTreePanel.updateTree(mdxFiles, blpFiles);
    }

    private void onProcess(ActionEvent e) {
        if (mapFile == null || modelsFolder == null) {
            log("Please select both a map and a models folder first.");
            return;
        }

        log("Processing...");

        try {
            // Create the destination file path
            File parentDir = mapFile.getParentFile();
            String originalName = mapFile.getName();
            File processedFile = new File(parentDir, "processed_" + originalName);

            // Copy the file
            File targetMap = Files.copy(mapFile.toPath(), processedFile.toPath(), StandardCopyOption.REPLACE_EXISTING).toFile();
            try (JMpqEditor mpqEditor = new JMpqEditor(targetMap, MPQOpenOption.FORCE_V0)) {
                log("Opened map: " + targetMap.getName());

                // Process the map options
//                optionsPanel.applyToMap(mpqEditor);

                // Import models from the selected folder
                Wc3MapAssetImporter.importAssetFiles(mpqEditor, modelsFolder);
            }

            log("Copied map to: " + processedFile.getAbsolutePath());

            // Placeholder: process the map and save changes to processedFile
            // WarcraftMapProcessor.process(processedFile, modelsFolder);

            log("Processing complete.");
        } catch (IOException ex) {
            log("Error during processing: " + ex.getMessage());
            ex.printStackTrace();
        }

        log("Processing complete. (Pretend we saved it!)");
    }

    private void log(String message) {
        logArea.append(message + "\n");
    }
}
