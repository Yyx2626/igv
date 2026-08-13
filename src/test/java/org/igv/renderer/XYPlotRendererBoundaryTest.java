package org.igv.renderer;

import org.igv.data.AverageErrorLocusScore;
import org.igv.data.BasicScore;
import org.igv.feature.LocusScore;
import org.igv.track.AverageErrorBarTrack;
import org.igv.track.ErrorBarType;
import org.igv.track.RenderContext;
import org.igv.ui.panel.ReferenceFrame;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.function.DoubleUnaryOperator;

import static org.junit.Assert.assertEquals;

public class XYPlotRendererBoundaryTest {

    @Test
    public void bottomAxisAndPositiveBarsShareLastPaintableRow() {
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
    public void positiveBarBottomStopsExactlyAtAxis() {
        Rectangle bar = BarChartRenderer.barBounds(10, 4, 50, 20);

        assertEquals(20, bar.y);
        assertEquals(50, bar.getMaxY(), 0);
    }

    @Test
    public void negativeBarTopStartsExactlyAtAxis() {
        Rectangle bar = BarChartRenderer.barBounds(10, 1, 50, 80);

        assertEquals(50, bar.y);
        assertEquals(80, bar.getMaxY(), 0);
        assertEquals(1, bar.width);
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
    public void barModeYPixelAtBaselineSharesLastPaintableRowWithMidline() {
        // All-non-negative track (baseline == minimum == 0), matching the common case where
        // a positive-only error bar's low end is clamped to the baseline in data space.
        Rectangle track = new Rectangle(0, 5, 100, 20);
        DataRange dataRange = new DataRange(0, 0, 4.59f);

        int baselinePixel = AverageErrorBarPainter.barModeYPixel(track, dataRange, 0f);
        int midlinePixel = XYPlotRenderer.clampYPixel(track, track.getY() + track.getHeight());

        assertEquals("error bar clamped to baseline must land on the same row as the midline, "
                + "not one row past it (which SVG/PDF export renders as an overshoot below the line)",
                midlinePixel, baselinePixel);
        assertEquals(24, baselinePixel);
    }

    @Test
    public void barModeYPixelMatchesUnclampedBaseReferenceAwayFromBaseline() {
        // With dataY away from the baseline, clamping baseY before subtracting the delta
        // (instead of clamping only the final pixel, like XYPlotRenderer.renderScores() does)
        // would shift this result by a pixel relative to where the mean bar itself actually
        // gets drawn, breaking the "sits flush against the mean bar" contract this method
        // documents. yScaleFactor is exactly 1 here (height 20 over a 0..20 range) so the
        // expected pixel is exact, with no float-rounding ambiguity.
        Rectangle track = new Rectangle(0, 5, 100, 20);
        DataRange dataRange = new DataRange(0, 0, 20f);

        assertEquals(17, AverageErrorBarPainter.barModeYPixel(track, dataRange, 8f));
    }

    @Test
    public void degenerateErrorBarIsSkippedRatherThanDrawnPastBaseline() {
        // End-to-end: a low-signal bin on a track with a wide dynamic range (much taller peaks
        // elsewhere) rounds its error span to less than a single pixel. Rather than forcing a
        // visible 1px mark - which used to grow unconditionally downward and could poke past the
        // gray midline in SVG/PDF export - nothing should be painted for this bin's error bar at
        // all, matching how BarChartRenderer.drawDataPoint() already skips a zero-height mean bar.
        BufferedImage image = new BufferedImage(20, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        ReferenceFrame frame = new ReferenceFrame("test");
        Rectangle bounds = new Rectangle(0, 5, 20, 20);
        RenderContext context = new RenderContext(null, graphics, frame, bounds, bounds, bounds);

        AverageErrorBarTrack track = new AverageErrorBarTrack("t", "t");
        track.setDataRange(new DataRange(0, 0, 100f));
        track.setErrorBarType(ErrorBarType.SEM);
        ErrorBarStyle style = new ErrorBarStyle();
        style.setShape(ErrorBarStyle.Shape.BAR);
        style.setCapStyle(ErrorBarStyle.CapStyle.DOUBLE);
        track.setErrorBarStyle(style);
        AverageErrorLocusScore score = new AverageErrorLocusScore(0, 10, 1f, 1f, 1f, 2);

        AverageErrorBarPainter.drawErrorBars(track, Collections.singletonList((LocusScore) score),
                context, bounds, new BarChartRenderer());

        for (int x = 0; x < image.getWidth(); x++) {
            assertEquals("baseline row must stay untouched", 0, image.getRGB(x, 24));
            assertEquals("row past the baseline must stay untouched", 0, image.getRGB(x, 25));
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
        renderer.renderGuides(track, context, bounds);

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
    public void errorPixelSpanCanBeDegenerateOnAWideDynamicRangeTrack() {
        // A track with a wide dynamic range (0..100, e.g. because it also has much taller peaks
        // elsewhere) makes a modest mean+error - tiny relative to the full range, though not
        // literally zero - round to the exact same pixel row as the baseline itself once scaled.
        // This is the actual mechanism behind the reported bug: a low-signal bin's error mark
        // rounding degenerate, not a mistake in the baseline-clamping math itself.
        Rectangle track = new Rectangle(0, 5, 100, 20);
        DataRange wideRange = new DataRange(0, 0, 100f);
        DoubleUnaryOperator toPixel = dataY -> AverageErrorBarPainter.barModeYPixel(track, wideRange, (float) dataY);
        ErrorBarStyle style = new ErrorBarStyle();
        style.setCapStyle(ErrorBarStyle.CapStyle.DOUBLE);

        int[] span = AverageErrorBarPainter.errorPixelSpan(toPixel, 0, 1f, 1f, style);

        assertEquals(24, span[0]);
        assertEquals("low and high both round to the baseline's own pixel row - degenerate", 24, span[1]);
        assertEquals("mean is above the baseline, so growth must go upward, away from it", 1, span[2]);
    }
}
