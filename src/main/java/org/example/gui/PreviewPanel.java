package org.example.gui;

import org.example.gui.i18n.Messages;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel that displays either:
 * <ul>
 *   <li>A single scaled preview of a selected asset (BLP texture or placeholder for MDX)</li>
 *   <li>A scrollable thumbnail grid when a folder is focused in the asset tree</li>
 * </ul>
 *
 * <p>Grid thumbnails are loaded asynchronously via a {@link SwingWorker} so the EDT
 * is never blocked.  Switching view (single ↔ grid) cancels any in-flight load.
 */
public class PreviewPanel extends JPanel {

    private static final int PREVIEW_SIZE = 256;
    private static final int THUMB_SIZE   = 96;

    // ---- Single-image mode ----
    private final JLabel  singleLabel     = new JLabel();
    private final JPanel  singleContainer;

    // ---- Grid mode ----
    // FlowLayout placed in BorderLayout.NORTH of a wrapper so it wraps at the
    // viewport width and allows the scroll pane to provide vertical scrolling.
    private final JPanel  gridPanel   = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
    private final JPanel  gridWrapper = new JPanel(new BorderLayout());

    // ---- Shared scroll pane (always present) ----
    private final JScrollPane scrollPane;

    // ---- Async thumb loading ----
    private SwingWorker<?, ?> thumbLoader;

    public PreviewPanel() {
        setLayout(new BorderLayout());

        // Single-image: centred label inside a GridBagLayout panel
        singleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        singleLabel.setVerticalAlignment(SwingConstants.CENTER);
        singleContainer = new JPanel(new GridBagLayout());
        singleContainer.add(singleLabel);

        // Grid: flow panel constrained to viewport width by BorderLayout.NORTH
        gridWrapper.add(gridPanel, BorderLayout.NORTH);

        // Start in single-image mode
        scrollPane = new JScrollPane(singleContainer);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);

        add(scrollPane, BorderLayout.CENTER);

        applyI18n();
        setPreferredSize(new Dimension(PREVIEW_SIZE + 40, PREVIEW_SIZE + 40));
    }

    // -------------------------------------------------------------------------
    // Single-image setters
    // -------------------------------------------------------------------------

    public void setImage(BufferedImage image) {
        cancelThumbLoader();
        switchTo(singleContainer);
        singleLabel.setIcon(image != null
                ? scaled(image, PREVIEW_SIZE)
                : fallback(PREVIEW_SIZE, 4));
    }

    public void setImage(File file) {
        cancelThumbLoader();
        switchTo(singleContainer);
        try {
            BufferedImage img = ImageIO.read(file);
            singleLabel.setIcon(img != null ? scaled(img, PREVIEW_SIZE) : fallback(PREVIEW_SIZE, 4));
        } catch (IOException e) {
            singleLabel.setIcon(fallback(PREVIEW_SIZE, 4));
        }
    }

    public void setImage(byte[] imageData) {
        cancelThumbLoader();
        switchTo(singleContainer);
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageData));
            singleLabel.setIcon(img != null ? scaled(img, PREVIEW_SIZE) : fallback(PREVIEW_SIZE, 4));
        } catch (IOException e) {
            singleLabel.setIcon(fallback(PREVIEW_SIZE, 4));
        }
    }

    // -------------------------------------------------------------------------
    // Grid (folder) mode
    // -------------------------------------------------------------------------

    /**
     * Shows a scrollable thumbnail grid for the supplied image files.
     * Placeholders appear immediately; actual images load on a background thread.
     * Any previously running load is cancelled first.
     */
    public void setImages(List<File> files) {
        cancelThumbLoader();
        gridPanel.removeAll();
        switchTo(gridWrapper);

        if (files.isEmpty()) {
            gridPanel.revalidate();
            return;
        }

        // Create placeholder labels synchronously so the layout is ready immediately.
        List<JLabel> labels = new ArrayList<>(files.size());
        ImageIcon placeholder = fallback(THUMB_SIZE, 2);
        for (File f : files) {
            JLabel lbl = new JLabel(truncate(f.getName(), 14), placeholder, SwingConstants.CENTER);
            lbl.setVerticalTextPosition(SwingConstants.BOTTOM);
            lbl.setHorizontalTextPosition(SwingConstants.CENTER);
            lbl.setPreferredSize(new Dimension(THUMB_SIZE + 20, THUMB_SIZE + 30));
            lbl.setToolTipText(f.getName());
            gridPanel.add(lbl);
            labels.add(lbl);
        }
        gridPanel.revalidate();
        gridPanel.repaint();

        // Load actual images in the background and publish results to the EDT.
        thumbLoader = new SwingWorker<Void, ThumbResult>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < files.size() && !isCancelled(); i++) {
                    try {
                        BufferedImage img = ImageIO.read(files.get(i));
                        if (img != null) {
                            publish(new ThumbResult(labels.get(i), scaled(img, THUMB_SIZE)));
                        }
                    } catch (Exception ignored) {}
                }
                return null;
            }

            @Override
            protected void process(List<ThumbResult> chunks) {
                for (ThumbResult r : chunks) {
                    r.label().setIcon(r.icon());
                }
                gridPanel.repaint();
            }
        };
        thumbLoader.execute();
    }

    // -------------------------------------------------------------------------
    // i18n
    // -------------------------------------------------------------------------

    /** Refreshes the titled border text after a locale change. */
    public void applyI18n() {
        setBorder(BorderFactory.createTitledBorder(Messages.get("label.preview")));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void switchTo(JPanel content) {
        if (scrollPane.getViewport().getView() != content) {
            scrollPane.setViewportView(content);
        }
    }

    private void cancelThumbLoader() {
        if (thumbLoader != null && !thumbLoader.isDone()) {
            thumbLoader.cancel(true);
        }
    }

    private static ImageIcon scaled(BufferedImage src, int size) {
        return new ImageIcon(src.getScaledInstance(size, size, Image.SCALE_SMOOTH));
    }

    private static ImageIcon fallback(int size, int stroke) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.LIGHT_GRAY);
        g.fillRect(0, 0, size, size);
        g.setColor(Color.DARK_GRAY);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g.drawLine(0, 0, size, size);
        g.drawLine(size, 0, 0, size);
        g.dispose();
        return new ImageIcon(img);
    }

    private static String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 1) + "…";
    }

    // -------------------------------------------------------------------------
    // Inner record
    // -------------------------------------------------------------------------

    private record ThumbResult(JLabel label, ImageIcon icon) {}
}
