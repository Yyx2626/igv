package org.igv.renderer;

import org.igv.DirectoryManager;
import org.igv.Globals;
import org.igv.feature.BasicFeature;
import org.igv.track.FeatureTrack;
import org.igv.track.RenderContext;
import org.igv.prefs.PreferencesManager;
import org.junit.BeforeClass;
import org.junit.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertTrue;

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

        FeatureTrack track = new FeatureTrack(null, "annotation", "annotation");
        track.setRowHeight(40);
        track.setShowFeatureNames(true);
        BasicFeature feature = new BasicFeature("chr1", 120, 130);
        feature.setName("LONG_ANNOTATION_LABEL");

        new IGVFeatureRenderer().render(List.of(feature), context,
                new Rectangle(0, 0, 300, 50), track);
        context.dispose();
        graphics.dispose();

        int textPixels = 0;
        for (int y = 20; y < 49; y++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                if ((image.getRGB(x, y) & 0x00FFFFFF) != 0x00FFFFFF) textPixels++;
            }
        }
        assertTrue("Expected annotation text pixels in inverted region", textPixels > 0);
    }
}
