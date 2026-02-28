package org.example.gui.settings;

import org.example.gui.i18n.Messages;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

/**
 * Modal settings dialog with three tabs:
 * <ol>
 *   <li><b>Language</b> — locale switcher; triggers live UI refresh via callback</li>
 *   <li><b>Keybindings</b> — hotkey table; persisted on dialog close</li>
 *   <li><b>Look & Feel</b> — Swing UI style selector; applied immediately</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   SettingsDialog dlg = new SettingsDialog(parentFrame, keybindingsConfig, appearanceConfig);
 *   dlg.setLocaleChangeListener(locale -> mainFrame.applyI18n());
 *   dlg.setLookAndFeelChangeListener(() -> SwingUtilities.updateComponentTreeUI(mainFrame));
 *   dlg.setVisible(true);
 * </pre>
 */
public class SettingsDialog extends JDialog {

    private final KeybindingsConfig config;
    private final AppearanceConfig appearanceConfig;
    private final LanguagePanel languagePanel;
    private final KeybindingPanel keybindingPanel;
    private final LookAndFeelPanel lookAndFeelPanel;

    public SettingsDialog(JFrame parent, KeybindingsConfig config, AppearanceConfig appearanceConfig) {
        super(parent, Messages.get("settings.title"), true /* modal */);
        this.config = config;
        this.appearanceConfig = appearanceConfig;

        languagePanel = new LanguagePanel();
        keybindingPanel = new KeybindingPanel(config);
        lookAndFeelPanel = new LookAndFeelPanel(appearanceConfig);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab(Messages.get("settings.tab.language"), languagePanel);
        tabs.addTab(Messages.get("settings.tab.keybindings"), keybindingPanel);
        tabs.addTab("Look & Feel", lookAndFeelPanel);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> onClose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(closeBtn);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        setSize(500, 380);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    /**
     * Registers a callback that fires immediately when the user switches locale.
     * Typically used to call {@code mainFrame.applyI18n()}.
     */
    public void setLocaleChangeListener(LanguagePanel.LocaleChangeListener listener) {
        languagePanel.setLocaleChangeListener(locale -> {
            // Refresh dialog's own labels first
            applyI18n();
            // Then notify the parent
            listener.onLocaleChanged(locale);
        });
    }

    /**
     * Registers a callback that fires immediately when the user switches Look & Feel.
     * Typically used to call {@code SwingUtilities.updateComponentTreeUI(mainFrame)}.
     */
    public void setLookAndFeelChangeListener(LookAndFeelPanel.LookAndFeelChangeListener listener) {
        lookAndFeelPanel.setLookAndFeelChangeListener(() -> {
            // Refresh dialog's own UI tree first
            SwingUtilities.updateComponentTreeUI(this);
            pack();
            // Then notify the parent (main frame)
            listener.onLookAndFeelChanged();
        });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void onClose() {
        config.save();
        appearanceConfig.save();
        dispose();
    }

    /** Refreshes all labels in the dialog after a locale switch. */
    public void applyI18n() {
        setTitle(Messages.get("settings.title"));
        // Re-title tabs — find them by index
        JTabbedPane tabs = (JTabbedPane) ((BorderLayout) getContentPane().getLayout())
                .getLayoutComponent(BorderLayout.CENTER);
        if (tabs != null) {
            tabs.setTitleAt(0, Messages.get("settings.tab.language"));
            tabs.setTitleAt(1, Messages.get("settings.tab.keybindings"));
        }
        languagePanel.applyI18n();
        keybindingPanel.applyI18n();
        repaint();
    }
}
