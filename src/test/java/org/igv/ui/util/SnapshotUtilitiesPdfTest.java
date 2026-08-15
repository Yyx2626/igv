package org.igv.ui.util;

import org.igv.ui.panel.Paintable;
import org.junit.Test;

import javax.swing.JComponent;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SnapshotUtilitiesPdfTest {

    private static final class VectorFixture extends JComponent implements Paintable {
        @Override
        public void paintOffscreen(Graphics2D graphics, Rectangle rect, boolean batch) {
            graphics.setColor(new Color(30, 90, 180, 128));
            graphics.fillRect(10, 10, 80, 30);
            graphics.setColor(Color.BLACK);
            graphics.drawLine(0, 50, 199, 50);
            graphics.drawString("IGV vector PDF", 20, 75);
        }

        @Override
        public int getSnapshotHeight(boolean batch) {
            return 100;
        }
    }

    @Test
    public void exportsSinglePagePdfAndAddsExtension() throws Exception {
        Path directory = Files.createTempDirectory("igv-pdf-test");
        File requested = directory.resolve("snapshot").toFile();
        VectorFixture fixture = new VectorFixture();
        fixture.setSize(200, 100);

        assertEquals("OK", SnapshotUtilities.doComponentSnapshot(
                fixture, requested, ImageFileTypes.Type.PDF, false));

        Path pdf = directory.resolve("snapshot.pdf");
        assertTrue(Files.exists(pdf));
        assertTrue(Files.size(pdf) > 500);
        byte[] header = Files.readAllBytes(pdf);
        assertTrue(new String(header, 0, Math.min(header.length, 8), StandardCharsets.US_ASCII)
                .startsWith("%PDF-"));
    }

    @Test
    public void exportsPngAtRequestedRasterScale() throws Exception {
        Path directory = Files.createTempDirectory("igv-png-scale-test");
        File requested = directory.resolve("snapshot").toFile();
        VectorFixture fixture = new VectorFixture();
        fixture.setSize(200, 100);

        assertEquals("OK", SnapshotUtilities.doComponentSnapshot(
                fixture, requested, ImageFileTypes.Type.PNG, false, 2));

        BufferedImage image = ImageIO.read(directory.resolve("snapshot.png").toFile());
        assertEquals(400, image.getWidth());
        assertEquals(200, image.getHeight());
    }

    @Test
    public void supportsFractionalDisplayRasterScale() throws Exception {
        Path directory = Files.createTempDirectory("igv-png-fractional-scale-test");
        File requested = directory.resolve("snapshot").toFile();
        VectorFixture fixture = new VectorFixture();
        fixture.setSize(200, 100);

        assertEquals("OK", SnapshotUtilities.doComponentSnapshot(
                fixture, requested, ImageFileTypes.Type.PNG, false, 1.5));

        BufferedImage image = ImageIO.read(directory.resolve("snapshot.png").toFile());
        assertEquals(300, image.getWidth());
        assertEquals(150, image.getHeight());
    }
}
