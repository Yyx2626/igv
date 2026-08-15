package org.igv.renderer;

import org.igv.data.AverageErrorLocusScore;
import org.igv.feature.LocusScore;
import org.igv.track.AverageErrorBarTrack;
import org.igv.track.RenderContext;
import org.igv.track.Track;

import java.awt.Color;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

/** Draws the member values retained in each Average bin as deterministic scatter points. */
final class AverageScatterPointPainter {

    private AverageScatterPointPainter() {
    }

    static void draw(Track track, List<LocusScore> locusScores, RenderContext context,
                     Rectangle rect, XYPlotRenderer renderer) {
        if (!(track instanceof AverageErrorBarTrack averageTrack)
                || !averageTrack.isScatterPointsEnabled()) {
            return;
        }

        ScatterPointStyle style = averageTrack.getScatterPointStyle();
        DataRange dataRange = context.getDataRange(track);
        double origin = context.getOrigin();
        double locScale = context.getScale();
        if (!Double.isFinite(locScale) || locScale <= 0) return;
        float baseline = dataRange.getBaseline();
        double barOverlap = XYPlotRenderer.barOverlapPixels();

        for (LocusScore score : locusScores) {
            if (!(score instanceof AverageErrorLocusScore averageScore)) continue;
            float[] values = averageScore.getMemberValues();
            if (values == null || values.length == 0) continue;

            XYPlotRenderer.ScorePixelSpan xSpan =
                    XYPlotRenderer.scorePixelSpan(score, origin, locScale, barOverlap);
            if (xSpan.rawStart() + xSpan.paintWidth() < 0) continue;
            if (xSpan.rawStart() > rect.getMaxX()) break;

            // A theoretical bin width W is painted as W+E to cover seams. Center the scatter
            // region on that painted bar, but limit its usable width to W-E. At 100% this leaves
            // E pixels on both sides inside the painted bar: [left+E, left+W].
            double[] xPositions = xPositions(xSpan.scatterUsableLeft(),
                    xSpan.scatterUsableWidth(), values.length,
                    style.getWidthPercent(), style.getPointSizePx());
            for (int member = 0; member < values.length; member++) {
                float value = values[member];
                if (Float.isNaN(value)) continue;
                double centerX = xPositions[member];
                int centerY = renderer instanceof LineplotRenderer
                        ? renderer.computeYPixelValue(rect, dataRange, value)
                        : AverageErrorBarPainter.barModeYPixel(rect, dataRange, value);
                Color border = value >= baseline
                        ? style.getPositiveColorOverride() : style.getNegativeColorOverride();
                if (border == null) border = value >= baseline
                        ? context.getPositiveColor(track) : context.getNegativeColor(track);
                if (border == null) border = Color.BLACK;
                drawPoint(context, centerX, centerY, style.getPointSizePx(), style, border);
            }
        }
    }

    /**
     * Places the complete member-point set inside a centered fraction of the bar width.
     * Point radius is removed from both ends before the centers are distributed, so the
     * outer edges rather than merely the centers stay inside the requested region.
     */
    static double[] xPositions(double barLeft, double barWidth, int count,
                               int widthPercent, double pointSizePx) {
        if (count <= 0) return new double[0];
        double[] positions = new double[count];
        double center = barLeft + barWidth / 2.0;
        if (count == 1) {
            positions[0] = center;
            return positions;
        }
        double scatterWidth = scatterWidth(barWidth, widthPercent);
        double pointDiameter = Math.max(0, pointSizePx);
        double centerSpan = Math.max(0, scatterWidth - pointDiameter);
        double left = center - centerSpan / 2.0;
        double step = centerSpan / (count - 1);
        for (int i = 0; i < count; i++) positions[i] = left + i * step;
        return positions;
    }

    private static double scatterWidth(double barWidth, int widthPercent) {
        return Math.max(0, barWidth) * Math.max(1, Math.min(100, widthPercent)) / 100.0;
    }

    private static void drawPoint(RenderContext context, double centerX, double centerY,
                                  double size, ScatterPointStyle style, Color border) {
        double borderWidth = Math.min(style.getBorderLineWidthPx(),
                Math.max(0, size - 0.01));
        // Point size is the marker's outside diameter. BasicStroke is centered on the
        // shape outline, so shrink the geometry by one stroke width to keep that outside
        // diameter equal to the stored absolute pixel size.
        double geometrySize = Math.max(0.01, size - borderWidth);
        Shape point = pointShape(centerX, centerY, geometrySize, style.getShape());
        Graphics2D fillGraphics = (Graphics2D) context
                .getGraphic2DForColor(style.getInnerColor()).create();
        Graphics2D borderGraphics = (Graphics2D) context
                .getGraphic2DForColor(border).create();
        try {
            fillGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            borderGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            fillGraphics.fill(point);
            if (borderWidth > 0) {
                borderGraphics.setStroke(new BasicStroke((float) borderWidth));
                borderGraphics.draw(point);
            }
        } finally {
            fillGraphics.dispose();
            borderGraphics.dispose();
        }
    }

    private static Shape pointShape(double centerX, double centerY, double size,
                                    ScatterPointStyle.Shape shape) {
        double radius = size / 2.0;
        if (shape == ScatterPointStyle.Shape.SQUARE) {
            return new Rectangle2D.Double(centerX - radius, centerY - radius, size, size);
        }
        if (shape == ScatterPointStyle.Shape.CIRCLE) {
            return new Ellipse2D.Double(centerX - radius, centerY - radius, size, size);
        }
        Path2D.Double path = new Path2D.Double();
        path.moveTo(centerX, centerY - radius);
        if (shape == ScatterPointStyle.Shape.DIAMOND) {
            path.lineTo(centerX + radius, centerY);
            path.lineTo(centerX, centerY + radius);
            path.lineTo(centerX - radius, centerY);
        } else {
            path.lineTo(centerX + radius, centerY + radius);
            path.lineTo(centerX - radius, centerY + radius);
        }
        path.closePath();
        return path;
    }
}
