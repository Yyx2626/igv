package org.igv.renderer;

import org.junit.Test;

import java.awt.Rectangle;
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
}
