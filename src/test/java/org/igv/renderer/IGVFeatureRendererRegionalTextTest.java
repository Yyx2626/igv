package org.igv.renderer;

import org.igv.DirectoryManager;
import org.igv.Globals;
import org.igv.feature.BasicFeature;
import org.igv.track.FeatureTrack;
import org.igv.track.RenderContext;
import org.igv.prefs.PreferencesManager;
import org.igv.ui.panel.ReferenceFrame;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class IGVFeatureRendererRegionalTextTest {

    @BeforeClass
    public static void configureHeadlessPreferences() throws IOException {
        Globals.setHeadless(true);
        DirectoryManager.setIgvDirectory(new File(System.getProperty("java.io.tmpdir")));
        File preferences = File.createTempFile("igv-regional-label-test", ".properties");
        preferences.deleteOnExit();
        PreferencesManager.setPrefsFile(preferences.getAbsolutePath());
    }

    @Test
    public void invertedRegionalPassDrawsAnnotationText() {
        BufferedImage image = new BufferedImage(300, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        Rectangle region = new Rectangle(100, 0, 80, 50);
        graphics.clip(region);
        graphics.translate(2.0 * region.x + region.width, 0);
        graphics.scale(-1, 1);

        RenderContext context = new RenderContext(null, graphics, null,
                new Rectangle(0, 0, 300, 50), new Rectangle(0, 0, 300, 50), region);
        context.setViewTransform(0, 300, 1);
        context.setRegionalPass(true);
        context.setLabelClipBounds(region);
        AffineTransform labelTransform = new AffineTransform();
        labelTransform.translate(2.0 * region.x + region.width, 0);
        labelTransform.scale(-1, 1);
        context.setLabelCoordinateTransform(labelTransform);
        FeatureLabelCollector labelCollector = new FeatureLabelCollector();
        labelCollector.addRegionalTarget(region);
        context.setFeatureLabelCollector(labelCollector, 0);

        FeatureTrack track = new FeatureTrack(null, "annotation", "annotation");
        track.setRowHeight(40);
        track.setShowFeatureNames(true);
        BasicFeature feature = new BasicFeature("chr1", 120, 130);
        feature.setName("LONG_ANNOTATION_LABEL");

        new IGVFeatureRenderer().render(List.of(feature), context,
                new Rectangle(0, 0, 300, 50), track);
        context.dispose();
        graphics.dispose();
        Graphics2D labelGraphics = image.createGraphics();
        labelCollector.paint(labelGraphics);
        labelGraphics.dispose();

        int textPixels = 0;
        int textPixelsOutsideRegion = 0;
        // Feature blocks are near the top. Pixels in this lower band can only be the label.
        for (int y = 28; y < 49; y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00FFFFFF) != 0x00FFFFFF) {
                    textPixels++;
                    if (x < region.x || x >= region.x + region.width) textPixelsOutsideRegion++;
                }
            }
        }
        assertTrue("Expected annotation text pixels in inverted region", textPixels > 0);
        assertTrue("Expected the complete annotation label to extend beyond the region clip",
                textPixelsOutsideRegion > 0);
    }

    @Test
    public void deferredBaseLabelsUseFeatureOwnershipInsteadOfTextOverlap() {
        FeatureLabelCollector collector = new FeatureLabelCollector();
        collector.addRegionalTarget(new Rectangle(100, 0, 80, 50));

        assertTrue(collector.shouldPaintBaseLabelAt(90));
        assertTrue(collector.shouldPaintBaseLabelAt(180));
        assertTrue(!collector.shouldPaintBaseLabelAt(120));
    }

    @Test
    public void featureTrackRenderKeepsContextRectangleStableAcrossPasses() {
        BufferedImage image = new BufferedImage(300, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Rectangle trackRectangle = new Rectangle(0, 0, 300, 50);
        RenderContext context = new RenderContext(null, graphics, new ReferenceFrame("test"),
                trackRectangle, trackRectangle, trackRectangle);
        List<Rectangle> rectanglesSeenByRenderer = new ArrayList<>();
        FeatureTrack track = new FeatureTrack(null, "annotation", "annotation") {
            @Override
            protected void renderFeatures(RenderContext renderContext, Rectangle renderRectangle) {
                rectanglesSeenByRenderer.add(new Rectangle(renderContext.getTrackRectangle()));
            }
        };

        track.render(context);
        track.render(context);

        assertEquals(List.of(new Rectangle(0, 5, 300, 45), new Rectangle(0, 5, 300, 45)),
                rectanglesSeenByRenderer);
        assertEquals(new Rectangle(0, 0, 300, 50), context.getTrackRectangle());
        context.dispose();
        graphics.dispose();
    }
}
