package org.igv.track;

import org.igv.AbstractHeadlessTest;
import org.igv.ui.panel.ReferenceFrame;
import org.junit.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;

public class RenderContextTest extends AbstractHeadlessTest {

    @Test
    public void screenGraphicsUndoGenomicMirror() {
        BufferedImage image = new BufferedImage(100, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D genomicGraphics = image.createGraphics();
        genomicGraphics.translate(100, 0);
        genomicGraphics.scale(-1, 1);

        ReferenceFrame frame = new ReferenceFrame("test");
        frame.setInverted(true);
        Rectangle bounds = new Rectangle(0, 0, 100, 20);
        RenderContext context = new RenderContext(null, genomicGraphics, frame, bounds, bounds, bounds);

        context.getScreenGraphic2DForColor(Color.RED).fillRect(0, 0, 10, 10);

        assertEquals(Color.RED.getRGB(), image.getRGB(5, 5));
        assertEquals(0, image.getRGB(95, 5));
        context.dispose();
        genomicGraphics.dispose();
    }
}
