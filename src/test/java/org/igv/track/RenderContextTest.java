package org.igv.track;

import org.igv.ui.panel.ReferenceFrame;
import org.igv.feature.TrackRegionOverride;
import org.igv.renderer.DataRange;
import org.junit.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;

public class RenderContextTest {

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

    @Test
    public void regionalOverrideResolvesColorsAndFlippedRangeWithoutMutatingTrack() {
        BufferedImage image = new BufferedImage(100, 20, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        ReferenceFrame frame = new ReferenceFrame("test");
        Rectangle bounds = new Rectangle(0, 0, 100, 20);
        RenderContext context = new RenderContext(null, graphics, frame, bounds, bounds, bounds);
        DataSourceTrack track = new DataSourceTrack(null, "track", "track", null);
        track.setColor(Color.BLUE);
        track.setAltColor(Color.GREEN);
        track.setDataRange(new DataRange(-2, 0, 8));
        TrackRegionOverride override = new TrackRegionOverride();
        override.setPositiveColor(Color.RED);
        override.setNegativeColor(Color.YELLOW);
        override.setYAxisMode(TrackRegionOverride.YAxisMode.FLIP);
        context.setRegionOverride(override);

        assertEquals(Color.RED, context.getPositiveColor(track));
        assertEquals(Color.YELLOW, context.getNegativeColor(track));
        assertEquals(8f, context.getDataRange(track).getMinimum(), 0f);
        assertEquals(-2f, context.getDataRange(track).getMaximum(), 0f);
        assertEquals(-2f, track.getDataRange().getMinimum(), 0f);
        assertEquals(8f, track.getDataRange().getMaximum(), 0f);
        context.dispose();
        graphics.dispose();
    }

    @Test
    public void pairSwapDoesNotFlipButPairFlipDoes() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        ReferenceFrame frame = new ReferenceFrame("test");
        Rectangle bounds = new Rectangle(0, 0, 10, 10);
        RenderContext context = new RenderContext(null, graphics, frame, bounds, bounds, bounds);
        DataSourceTrack track = new DataSourceTrack(null, "track", "track", null);
        track.setDataRange(new DataRange(-2, 0, 8));
        TrackRegionOverride override = new TrackRegionOverride();
        override.setPairMode(TrackRegionOverride.PairMode.SWAP);
        context.setRegionOverride(override);
        assertEquals(-2f, context.getDataRange(track).getMinimum(), 0f);
        assertEquals(8f, context.getDataRange(track).getMaximum(), 0f);

        override.setPairMode(TrackRegionOverride.PairMode.FLIP);
        context.setRegionOverride(override);
        assertEquals(8f, context.getDataRange(track).getMinimum(), 0f);
        assertEquals(-2f, context.getDataRange(track).getMaximum(), 0f);

        override.setYAxisMode(TrackRegionOverride.YAxisMode.FLIP);
        context.setRegionOverride(override);
        assertEquals(-2f, context.getDataRange(track).getMinimum(), 0f);
        assertEquals(8f, context.getDataRange(track).getMaximum(), 0f);
        context.dispose();
        graphics.dispose();
    }
}
