package org.example;

import com.hiveworkshop.blizzard.blp.BLPReaderSpi;
import com.hiveworkshop.blizzard.blp.BLPWriterSpi;
import org.example.cli.ImportCommand;
import org.example.gui.MainFrame;

import javax.imageio.spi.IIORegistry;
import javax.swing.*;

/**
 * Application entry point.
 *
 * <ul>
 *   <li>No arguments launches the Swing GUI</li>
 *   <li>Any arguments delegates to the picocli CLI (--help for usage)</li>
 * </ul>
 *
 * GUI:  java -jar war3importer.jar
 * CLI:  java -jar war3importer.jar -m map.w3x -f models/ --create-units --place-units
 */
public class Main {

    public static void main(String[] args) {
        // The blp-iio-plugin.jar ships without META-INF/services registration,
        // so ImageIO cannot auto-discover it via ServiceLoader.  Register the
        // SPI providers manually before any ImageIO call is made.
        registerBlpPlugin();

        if (args.length == 0) {
            SwingUtilities.invokeLater(MainFrame::new);
        } else {
            System.exit(ImportCommand.run(args));
        }
    }

    private static void registerBlpPlugin() {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        registry.registerServiceProvider(new BLPReaderSpi());
        registry.registerServiceProvider(new BLPWriterSpi());
    }
}
