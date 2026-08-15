package org.igv.renderer;

import org.igv.Globals;
import org.igv.DirectoryManager;
import org.igv.data.AverageErrorLocusScore;
import org.igv.data.BasicScore;
import org.igv.feature.LocusScore;
import org.igv.track.AverageErrorBarTrack;
import org.igv.track.ErrorBarType;
import org.igv.track.RenderContext;
import org.igv.ui.panel.ReferenceFrame;
import org.junit.Test;
import org.junit.BeforeClass;

import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collections;
import java.util.function.DoubleUnaryOperator;
import javax.swing.JPanel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class XYPlotRendererBoundaryTest {

    @BeforeClass
    public static void configureHeadlessIGV() {
        Globals.setHeadless(true);
        File testIgvDirectory = new File("build/test-igv");
        if (!testIgvDirectory.isDirectory() && !testIgvDirectory.mkdirs()) {
            throw new IllegalStateException("Cannot create test IGV directory: " + testIgvDirectory);
        }
        DirectoryManager.setIgvDirectory(testIgvDirectory);
    }

    @Test
    public void bottomAxisAndPositiveBarsUseLastOwnedPixelRow() {
        Rectangle track = new Rectangle(0, 5, 100, 20);

        assertEquals(24, XYPlotRenderer.clampYPixel(track, 25));
        assertEquals(24, XYPlotRenderer.clampYPixel(track, 1000));
    }

    @Test
    public void topAxisAndNegativeBarsShareFirstPaintableRow() {
        Rectangle track = new Rectangle(0, 5, 100, 20);

        assertEquals(5, XYPlotRenderer.clampYPixel(track, 5));
        assertEquals(5, XYPlotRenderer.clampYPixel(track, -1000));
    }

    @Test
    public void emptyTrackRectangleHasStableBoundary() {
        assertEquals(7, XYPlotRenderer.clampYPixel(new Rectangle(0, 7, 100, 0), 100));
    }

    @Test
    public void fractionalCoordinatesRoundSymmetricallyAroundBaseline() {
        Rectangle track = new Rectangle(0, 0, 100, 100);
        int baseline = 50;

        assertEquals(10, baseline - XYPlotRenderer.clampYPixel(track, baseline - 10.25));
        assertEquals(10, XYPlotRenderer.clampYPixel(track, baseline + 10.25) - baseline);
        assertEquals(10, baseline - XYPlotRenderer.clampYPixel(track, baseline - 10.5));
        assertEquals(10, XYPlotRenderer.clampYPixel(track, baseline + 10.5) - baseline);
    }

    @Test
    public void identicalValuesHaveEqualHeightsOnNormalAndFlippedTracks() {
        Rectangle normalTrack = new Rectangle(0, 0, 100, 60);
        Rectangle flippedTrack = new Rectangle(0, 60, 100, 60);
        DataRange normalRange = new DataRange(0, 0, 16);
        DataRange flippedRange = normalRange.flipped();
        int normalBaseline = XYPlotRenderer.dataYPixel(normalTrack, normalRange, 0);
        int flippedBaseline = XYPlotRenderer.dataYPixel(flippedTrack, flippedRange, 0);

        assertEquals(59, normalBaseline);
        assertEquals(60, flippedBaseline);
        for (int value = 1; value <= 16; value++) {
            int upwardHeight = normalBaseline -
                    XYPlotRenderer.dataYPixel(normalTrack, normalRange, value);
            int downwardHeight = XYPlotRenderer.dataYPixel(flippedTrack, flippedRange, value) -
                    flippedBaseline;
            assertEquals("value " + value + " must have the same height in both directions",
                    upwardHeight, downwardHeight);
        }
    }

    @Test
    public void positiveBarBottomStopsExactlyAtAxis() {
        Rectangle bar = BarChartRenderer.barBounds(10, 4, 50, 20);

        assertEquals(20, bar.y);
        assertEquals(51, bar.getMaxY(), 0);
    }

    @Test
    public void negativeBarTopStartsExactlyAtAxis() {
        Rectangle bar = BarChartRenderer.barBounds(10, 1, 50, 80);

        assertEquals(50, bar.y);
        assertEquals(81, bar.getMaxY(), 0);
        assertEquals(1, bar.width);
    }

    @Test
    public void barEndpointAtBottomBoundaryFillsLastInteriorRowWithoutOvershoot() {
        BufferedImage image = new BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Rectangle bounds = new Rectangle(0, 5, 20, 20);
        graphics.setClip(bounds);
        RenderContext context = new RenderContext(null, graphics,
                new ReferenceFrame("test"), bounds, bounds, bounds);
        context.setViewTransform(0, 20, 1);
        org.igv.track.DataSourceTrack track =
                new org.igv.track.DataSourceTrack(null, "t", "t", null);
        track.setDataRange(new DataRange(0, 0, 1f));

        new BarChartRenderer().renderScores(track,
                Collections.singletonList((LocusScore) new BasicScore(0, 10, 1f)), context, bounds);

        assertNotEquals("bar must reach the last interior row", 0, image.getRGB(5, 24));
        assertEquals("bar must not cross the geometric boundary", 0, image.getRGB(5, 25));
        context.dispose();
        graphics.dispose();
    }

    @Test
    public void standaloneScreenBaselineAtBottomRemainsVisible() {
        BufferedImage image = new BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Rectangle bounds = new Rectangle(0, 5, 20, 20);
        graphics.setClip(bounds);
        RenderContext context = new RenderContext(new JPanel(), graphics,
                new ReferenceFrame("test"), bounds, bounds, bounds);
        org.igv.track.DataSourceTrack track =
                new org.igv.track.DataSourceTrack(null, "t", "t", null);
        track.setDataRange(new DataRange(0, 0, 1f, true));

        new BarChartRenderer().renderGuides(track, context, bounds);

        assertEquals(DataRange.DEFAULT_MIDLINE_COLOR.getRGB(), image.getRGB(10, 24));
        assertEquals(0, image.getRGB(10, 25));
        context.dispose();
        graphics.dispose();
    }

    @Test
    public void adjacentTracksKeepTheirOwnEdgeBaselineRows() {
        BufferedImage image = new BufferedImage(20, 45, BufferedImage.TYPE_INT_ARGB);
        Rectangle topBounds = new Rectangle(0, 5, 20, 20);
        Rectangle bottomBounds = new Rectangle(0, 25, 20, 20);
        ReferenceFrame frame = new ReferenceFrame("test");

        Graphics2D topGraphics = image.createGraphics();
        topGraphics.setClip(topBounds);
        RenderContext topContext = new RenderContext(null, topGraphics, frame,
                topBounds, topBounds, topBounds);
        org.igv.track.DataSourceTrack topTrack =
                new org.igv.track.DataSourceTrack(null, "top", "top", null);
        topTrack.setDataRange(new DataRange(0, 0, 1f, true));
        new BarChartRenderer().renderGuides(topTrack, topContext, topBounds);

        Graphics2D bottomGraphics = image.createGraphics();
        bottomGraphics.setClip(bottomBounds);
        RenderContext bottomContext = new RenderContext(null, bottomGraphics, frame,
                bottomBounds, bottomBounds, bottomBounds);
        org.igv.track.DataSourceTrack bottomTrack =
                new org.igv.track.DataSourceTrack(null, "bottom", "bottom", null);
        bottomTrack.setDataRange(new DataRange(-1f, 1f, 1f, true));
        new BarChartRenderer().renderGuides(bottomTrack, bottomContext, bottomBounds);

        assertEquals("top track owns and paints its last row",
                DataRange.DEFAULT_MIDLINE_COLOR.getRGB(), image.getRGB(10, 24));
        assertEquals("bottom track owns and paints its first row",
                DataRange.DEFAULT_MIDLINE_COLOR.getRGB(), image.getRGB(10, 25));
        assertEquals("row below the shared boundary must remain clear", 0, image.getRGB(10, 26));
        topContext.dispose();
        bottomContext.dispose();
        topGraphics.dispose();
        bottomGraphics.dispose();
    }

    @Test
    public void positiveDoubleErrorBarDoesNotCrossBaseline() {
        ErrorBarStyle style = new ErrorBarStyle();
        style.setCapStyle(ErrorBarStyle.CapStyle.DOUBLE);
        DoubleUnaryOperator invertedPixel = value -> 100 - value;

        int[] span = AverageErrorBarPainter.errorPixelSpan(
                invertedPixel, 0, 10, 20, style);

        assertEquals(70, span[0]);
        assertEquals(100, span[1]);
    }

    @Test
    public void negativeDoubleErrorBarDoesNotCrossBaseline() {
        ErrorBarStyle style = new ErrorBarStyle();
        style.setCapStyle(ErrorBarStyle.CapStyle.DOUBLE);
        DoubleUnaryOperator invertedPixel = value -> 100 - value;

        int[] span = AverageErrorBarPainter.errorPixelSpan(
                invertedPixel, 0, -10, 20, style);

        assertEquals(100, span[0]);
        assertEquals(130, span[1]);
    }

    @Test
    public void positiveSingleErrorBarCapsAtOutwardTip() {
        ErrorBarStyle style = new ErrorBarStyle();
        style.setCapStyle(ErrorBarStyle.CapStyle.SINGLE);
        DoubleUnaryOperator invertedPixel = value -> 100 - value;

        int[] span = AverageErrorBarPainter.errorPixelSpan(
                invertedPixel, 0, 10, 20, style);

        assertEquals(70, span[0]);
        assertEquals(90, span[1]);
        assertEquals("outward tip (mean+err) is the smaller pixel value here, so the cap "
                + "belongs at the top", 1, span[2]);
    }

    @Test
    public void negativeSingleErrorBarCapsAtOutwardTipNotTheMeanEnd() {
        ErrorBarStyle style = new ErrorBarStyle();
        style.setCapStyle(ErrorBarStyle.CapStyle.SINGLE);
        DoubleUnaryOperator invertedPixel = value -> 100 - value;

        int[] span = AverageErrorBarPainter.errorPixelSpan(
                invertedPixel, 0, -10, 20, style);

        assertEquals(110, span[0]);
        assertEquals(130, span[1]);
        assertEquals("outward tip (mean-err) is the larger pixel value for a below-baseline "
                + "mean, so the cap belongs at the bottom, not at span[0]/top", 0, span[2]);
    }

    @Test
    public void barModeYPixelAtBaselineSharesGeometricBoundaryWithMidline() {
        // All-non-negative track (baseline == minimum == 0), matching the common case where
        // a positive-only error bar's low end is clamped to the baseline in data space.
        Rectangle track = new Rectangle(0, 5, 100, 20);
        DataRange dataRange = new DataRange(0, 0, 4.59f);

        int baselinePixel = AverageErrorBarPainter.barModeYPixel(track, dataRange, 0f);
        int midlinePixel = XYPlotRenderer.clampYPixel(track, track.getY() + track.getHeight());

        assertEquals("error bar and midline must use the same geometry boundary, "
                + "while their painters independently respect the track clip",
                midlinePixel, baselinePixel);
        assertEquals(24, baselinePixel);
    }

    @Test
    public void barModeYPixelUsesOneFinalConversionAwayFromBaseline() {
        // The 20-pixel track owns 19 intervals between its 20 pixel centers.
        Rectangle track = new Rectangle(0, 5, 100, 20);
        DataRange dataRange = new DataRange(0, 0, 20f);

        assertEquals(16, AverageErrorBarPainter.barModeYPixel(track, dataRange, 8f));
    }

    @Test
    public void fractionalHeightIsConvertedToIntegerOnlyAtTheEnd() {
        Rectangle track = new Rectangle(0, 5, 100, 20);
        DataRange dataRange = new DataRange(0, 0, 3f);

        // Baseline row = 24; the rounded distance for value 1 is 6 rows.
        assertEquals(18, AverageErrorBarPainter.barModeYPixel(track, dataRange, 1f));
    }

    @Test
    public void interiorBaselineIsDrawnOnlyOnce() {
        BufferedImage image = new BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        ReferenceFrame frame = new ReferenceFrame("test");
        Rectangle bounds = new Rectangle(0, 5, 20, 20);
        RenderContext context = new RenderContext(null, graphics, frame, bounds, bounds, bounds);
        org.igv.track.DataSourceTrack track =
                new org.igv.track.DataSourceTrack(null, "t", "t", null);
        DataRange range = new DataRange(-1, 0, 1, true);
        range.setMidlineColor(new Color(100, 100, 100, 128));
        track.setDataRange(range);

        new BarChartRenderer().renderGuides(track, context, bounds);

        int baselineY = XYPlotRenderer.clampYPixel(bounds, 15);
        assertEquals("a half-transparent baseline drawn once must retain alpha 128",
                128, new Color(image.getRGB(10, baselineY), true).getAlpha());
        context.dispose();
        graphics.dispose();
    }

    @Test
    public void disabledBaselineIsNotDrawn() {
        BufferedImage image = new BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        ReferenceFrame frame = new ReferenceFrame("test");
        Rectangle bounds = new Rectangle(0, 5, 20, 20);
        RenderContext context = new RenderContext(null, graphics, frame, bounds, bounds, bounds);
        org.igv.track.DataSourceTrack track =
                new org.igv.track.DataSourceTrack(null, "t", "t", null);
        track.setDataRange(new DataRange(-1, 0, 1, false));

        new BarChartRenderer().renderGuides(track, context, bounds);

        assertEquals(0, image.getRGB(10, 15));
        context.dispose();
        graphics.dispose();
    }

    @Test
    public void onePixelErrorBarStopsAtGeometricBaseline() {
        // A low-signal error span can occupy the final interior row, but its rectangle endpoint
        // is the shared geometry baseline and therefore must not paint across that boundary.
        BufferedImage image = new BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        ReferenceFrame frame = new ReferenceFrame("test");
        Rectangle bounds = new Rectangle(0, 5, 20, 20);
        RenderContext context = new RenderContext(null, graphics, frame, bounds, bounds, bounds);
        context.setViewTransform(0, 20, 1);

        AverageErrorBarTrack track = new AverageErrorBarTrack("t", "t");
        track.setDataRange(new DataRange(0, 0, 100f));
        track.setErrorBarType(ErrorBarType.SEM);
        ErrorBarStyle style = new ErrorBarStyle();
        style.setShape(ErrorBarStyle.Shape.BAR);
        style.setCapStyle(ErrorBarStyle.CapStyle.DOUBLE);
        track.setErrorBarStyle(style);
        AverageErrorLocusScore score = new AverageErrorLocusScore(0, 10, 2f, 2f, 2f, 2);

        AverageErrorBarPainter.drawErrorBars(track, Collections.singletonList((LocusScore) score),
                context, bounds, new BarChartRenderer());

        assertNotEquals("the one-pixel error span should end immediately above the baseline",
                0, image.getRGB(1, 23));
        assertEquals("the baseline row is not crossed", 0, image.getRGB(1, 24));
        for (int x = 0; x < image.getWidth(); x++) {
            assertEquals("the geometric baseline must not be crossed", 0, image.getRGB(x, 25));
        }
        context.dispose();
        graphics.dispose();
    }

    @Test
    public void plainBarChartBarNeverOvershootsTheMidlineEither() {
        // The "None"-windowing envelope feature (NumericTrackBinner.binEnvelope) renders its
        // mid-to-max/mid-to-min bars through the ordinary BarChartRenderer/XYPlotRenderer path,
        // not AverageErrorBarPainter (the class that actually had the midline-overshoot bug
        // fixed above) - confirming that path was never affected, on a value close enough to
        // the baseline to be the same kind of edge case.
        BufferedImage image = new BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        ReferenceFrame frame = new ReferenceFrame("test");
        Rectangle bounds = new Rectangle(0, 5, 20, 20);
        RenderContext context = new RenderContext(null, graphics, frame, bounds, bounds, bounds);

        org.igv.track.DataSourceTrack track = new org.igv.track.DataSourceTrack(null, "t", "t", null);
        track.setDataRange(new DataRange(0, 0, 100f));
        BasicScore lowSignal = new BasicScore(0, 10, 0.05f); // near-zero, wide-range track
        BarChartRenderer renderer = new BarChartRenderer();

        renderer.renderScores(track, Collections.singletonList((LocusScore) lowSignal), context, bounds);
        int paintedBelowMidline = 0;
        for (int y = 25; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != 0) paintedBelowMidline++;
            }
        }
        assertEquals("nothing should be painted below the last paintable row", 0, paintedBelowMidline);
        context.dispose();
        graphics.dispose();
    }

    @Test
    public void errorPixelSpanOutwardDirectionFlipsWithAxis() {
        ErrorBarStyle style = new ErrorBarStyle();
        style.setCapStyle(ErrorBarStyle.CapStyle.DOUBLE);

        // Normal (non-flipped) axis: values above the baseline map to smaller pixels, so a
        // mean above the baseline must be marked outward-at-top.
        DoubleUnaryOperator normalAxis = value -> 100 - value;
        int[] normalSpan = AverageErrorBarPainter.errorPixelSpan(normalAxis, 0, 10, 5, style);
        assertEquals(85, normalSpan[0]);
        assertEquals(95, normalSpan[1]);
        assertEquals(1, normalSpan[2]);

        // Flipped axis: same mean/error, but toPixel now increases with value - the outward
        // direction in pixel space must flip too, even though nothing else about the bin did.
        DoubleUnaryOperator flippedAxis = value -> value;
        int[] flippedSpan = AverageErrorBarPainter.errorPixelSpan(flippedAxis, 0, 10, 5, style);
        assertEquals("outward must flip to downward (bottom) on a flipped axis", 0, flippedSpan[2]);
    }

    @Test
    public void subpixelErrorSpanCollapsesOntoBaselineRow() {
        Rectangle track = new Rectangle(0, 5, 100, 20);
        DataRange wideRange = new DataRange(0, 0, 100f);
        DoubleUnaryOperator toPixel = dataY -> AverageErrorBarPainter.barModeYPixel(track, wideRange, (float) dataY);
        ErrorBarStyle style = new ErrorBarStyle();
        style.setCapStyle(ErrorBarStyle.CapStyle.DOUBLE);

        int[] span = AverageErrorBarPainter.errorPixelSpan(toPixel, 0, 1f, 1f, style);

        assertEquals(24, span[0]);
        assertEquals("both endpoints round to the baseline row", 24, span[1]);
        assertEquals("mean is above the baseline, so growth must go upward, away from it", 1, span[2]);
    }
}
