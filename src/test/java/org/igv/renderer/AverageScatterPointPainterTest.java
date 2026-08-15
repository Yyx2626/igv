package org.igv.renderer;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AverageScatterPointPainterTest {

    @Test
    public void threeRepeatsFitCompletelyInsideCenteredFiftyPercent() {
        double defaultPointSize = 100 * 0.50 / 3;
        assertArrayEquals(new double[]{100.0 / 3, 50, 200.0 / 3},
                AverageScatterPointPainter.xPositions(
                        0, 100, 3, 50, defaultPointSize), 0.0001);
    }

    @Test
    public void threeRepeatsFitCompletelyInsideCenteredSeventyFivePercent() {
        assertArrayEquals(new double[]{25, 50, 75},
                AverageScatterPointPainter.xPositions(0, 100, 3, 75, 25), 0.0001);
    }

    @Test
    public void oneRepeatStaysOnBarCenter() {
        assertArrayEquals(new double[]{60},
                AverageScatterPointPainter.xPositions(10, 100, 1, 75, 25), 0.0001);
    }

    @Test
    public void computedDefaultDiameterEqualsAvailableWidthPerRepeat() {
        ScatterPointStyle style = new ScatterPointStyle();
        style.initializeDefaultsForFirstSettingsOpen(100, 3);
        double[] centers = AverageScatterPointPainter.xPositions(
                0, 100, 3, style.getWidthPercent(), style.getPointSizePx());

        assertEquals(style.getPointSizePx(), centers[1] - centers[0], 0.0001);
        assertEquals(style.getPointSizePx(), centers[2] - centers[1], 0.0001);
    }

    @Test
    public void pointCentersCollapseToBarCenterWhenPointIsWiderThanScatterRegion() {
        assertArrayEquals(new double[]{10, 10, 10},
                AverageScatterPointPainter.xPositions(0, 20, 3, 50, 100), 0.0001);
    }

    @Test
    public void scatterUsesOwnedWidthCenteredWithinPaintedOverlap() {
        org.igv.data.BasicScore score = new org.igv.data.BasicScore(0, 80, 1);
        XYPlotRenderer.ScorePixelSpan span = XYPlotRenderer.scorePixelSpan(score, 0, 10, 1);

        assertEquals(8, span.ownedWidth(), 0.0001);
        assertEquals(9, span.paintWidth(), 0.0001);
        assertEquals(7, span.scatterUsableWidth(), 0.0001);
        assertEquals(1, span.scatterUsableLeft(), 0.0001);
        assertArrayEquals(new double[]{2, 4.5, 7},
                AverageScatterPointPainter.xPositions(
                        span.scatterUsableLeft(), span.scatterUsableWidth(),
                        3, 100, 2), 0.0001);
    }

    @Test
    public void allBarRenderersUseConfiguredPaintOverlap() {
        org.igv.data.BasicScore score = new org.igv.data.BasicScore(0, 80, 1);
        XYPlotRenderer.ScorePixelSpan span = XYPlotRenderer.scorePixelSpan(score, 0, 10, 2);

        assertEquals(8, span.ownedWidth(), 0.0001);
        assertEquals(10, span.paintWidth(), 0.0001);
        assertEquals(span.paintWidth(), new AverageErrorBarRenderer().scorePixelWidth(span), 0.0001);
        assertEquals(span.paintWidth(), new AverageErrorBarPointsRenderer().scorePixelWidth(span), 0.0001);
        assertEquals("ordinary and average bars must share the same overlap",
                span.paintWidth(), new BarChartRenderer().scorePixelWidth(span), 0.0001);
    }

    @Test
    public void zeroOverlapKeepsScatterAndBarOnTheExactBin() {
        org.igv.data.BasicScore score = new org.igv.data.BasicScore(0, 80, 1);
        XYPlotRenderer.ScorePixelSpan span = XYPlotRenderer.scorePixelSpan(score, 0, 10, 0);

        assertEquals(span.ownedWidth(), span.paintWidth(), 0.0001);
        assertEquals(span.ownedWidth(), span.scatterUsableWidth(), 0.0001);
        assertEquals(0, span.scatterUsableLeft(), 0.0001);
    }

    @Test
    public void subpixelBinsRetainFractionalGeometry() {
        org.igv.data.BasicScore score = new org.igv.data.BasicScore(0, 80, 1);
        XYPlotRenderer.ScorePixelSpan span = XYPlotRenderer.scorePixelSpan(score, 0, 160, 0.25);

        assertEquals(0.5, span.ownedWidth(), 0.0001);
        assertEquals(0.25, span.overlapWidth(), 0.0001);
        assertEquals(0.75, span.paintWidth(), 0.0001);
        assertEquals(0.25, span.scatterUsableWidth(), 0.0001);
        assertEquals(0.25, span.scatterUsableLeft(), 0.0001);
    }

    @Test
    public void overlapIsCappedAtSubpixelBinWidth() {
        org.igv.data.BasicScore score = new org.igv.data.BasicScore(0, 80, 1);
        XYPlotRenderer.ScorePixelSpan span = XYPlotRenderer.scorePixelSpan(score, 0, 160, 1.0);

        assertEquals(0.5, span.ownedWidth(), 0.0001);
        assertEquals(0.5, span.overlapWidth(), 0.0001);
        assertEquals(1.0, span.paintWidth(), 0.0001);
        assertEquals(0.0, span.scatterUsableWidth(), 0.0001);
        assertEquals(0.5, span.scatterUsableLeft(), 0.0001);
    }
}
