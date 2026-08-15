package org.igv.renderer;

import org.igv.data.AverageErrorLocusScore;
import org.igv.feature.LocusScore;
import org.igv.track.AverageErrorBarTrack;
import org.igv.track.ErrorBarType;
import org.igv.track.RenderContext;
import org.igv.track.Track;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Shared error-bar drawing logic for {@code AverageErrorBarTrack}, reused across render
 * modes (Bar, Points, Line Plot). Package-private: only {@code AverageErrorBarRenderer},
 * {@code AverageErrorBarPointsRenderer} and {@code AverageErrorBarLineplotRenderer} call
 * this, each choosing where in their own draw order to call it (e.g. Points draws the
 * error bar first, then the average point on top of it, to keep the point marker legible).
 */
final class AverageErrorBarPainter {

    private AverageErrorBarPainter() {
    }

    /**
     * Draw a per-bin error-bar marker (bar or T/I-beam line, per {@code ErrorBarStyle})
     * for every bin meeting the track's configured minimum N. Used by Bar and Points modes.
     */
    static void drawErrorBars(Track track, List<LocusScore> locusScores, RenderContext context,
                               Rectangle rect, XYPlotRenderer renderer) {
        if (!(track instanceof AverageErrorBarTrack)) {
            return;
        }
        AverageErrorBarTrack errTrack = (AverageErrorBarTrack) track;
        if (errTrack.getErrorBarType() == ErrorBarType.NONE) {
            return;
        }
        ErrorBarStyle style = errTrack.getErrorBarStyle();
        DataRange dataRange = context.getDataRange(track);
        double origin = context.getOrigin();
        double locScale = context.getScale();
        if (!Double.isFinite(locScale) || locScale <= 0) return;
        double barOverlap = XYPlotRenderer.barOverlapPixels();

        for (LocusScore score : locusScores) {
            AverageErrorLocusScore es = errorScoreOf(score, errTrack.getMinimumErrorBarN());
            if (es == null) {
                continue;
            }
            float mean = es.getScore();
            float err = errTrack.getErrorBarType() == ErrorBarType.SD ? es.getSd() : es.getSem();
            if (Float.isNaN(mean) || Float.isNaN(err) || err <= 0) {
                continue;
            }

            XYPlotRenderer.ScorePixelSpan xSpan =
                    XYPlotRenderer.scorePixelSpan(score, origin, locScale, barOverlap);
            double dx = renderer.scorePixelWidth(xSpan);
            if (xSpan.rawStart() + dx < 0) {
                continue;
            } else if (xSpan.rawStart() > rect.getMaxX()) {
                break;
            }

            // Use the exact same continuous-coordinate formula as
            // XYPlotRenderer.renderScores(), with one final pixel conversion.
            int[] span = errorPixelSpan(dataY -> barModeYPixel(rect, dataRange, (float) dataY),
                    dataRange.getBaseline(), mean, err, style);

            Color color = errorBarColor(track, style, mean, dataRange, context);
            drawOne(context, color, xSpan.rawStart(), dx,
                    span[0], span[1], span[2] == 1, style, renderer);
        }
    }

    /**
     * Draw the error region as a filled band connecting consecutive bins' error caps
     * (a trapezoid strip per consecutive pair, so gaps between non-adjacent bins don't
     * distort the shape). Used by Line Plot mode; the average line is drawn afterward,
     * on top of this band, by the caller.
     */
    static void drawErrorBand(Track track, List<LocusScore> locusScores, RenderContext context,
                               Rectangle rect, XYPlotRenderer renderer) {
        if (!(track instanceof AverageErrorBarTrack)) {
            return;
        }
        AverageErrorBarTrack errTrack = (AverageErrorBarTrack) track;
        if (errTrack.getErrorBarType() == ErrorBarType.NONE) {
            return;
        }
        ErrorBarStyle style = errTrack.getErrorBarStyle();
        DataRange dataRange = context.getDataRange(track);
        double origin = context.getOrigin();
        double locScale = context.getScale();

        List<int[]> segments = new ArrayList<>(); // {x, yHi, yLo}

        for (LocusScore score : locusScores) {
            AverageErrorLocusScore es = errorScoreOf(score, errTrack.getMinimumErrorBarN());
            if (es == null) {
                continue;
            }
            float mean = es.getScore();
            float err = errTrack.getErrorBarType() == ErrorBarType.SD ? es.getSd() : es.getSem();
            if (Float.isNaN(mean) || Float.isNaN(err) || err <= 0) {
                continue;
            }

            double pX = ((score.getStart() + score.getEnd()) / 2.0 - origin) / locScale;
            // LineplotRenderer.renderScores() uses a single (int) cast of the whole
            // pixel expression, same as computeYPixelValue() - no baseY pre-rounding to
            // match here, unlike the Bar/Points path above.
            int[] span = errorPixelSpan(dataY -> renderer.computeYPixelValue(rect, dataRange, dataY),
                    dataRange.getBaseline(), mean, err, style);
            segments.add(new int[]{(int) pX, span[0], span[1]});
        }

        if (segments.size() < 2) {
            return;
        }

        Color color = errorBarColor(track, style, 0, dataRange, context);
        Graphics2D g = context.getGraphic2DForColor(color);
        for (int i = 1; i < segments.size(); i++) {
            int[] prev = segments.get(i - 1);
            int[] cur = segments.get(i);
            int[] xs = {prev[0], cur[0], cur[0], prev[0]};
            int[] ys = {prev[1], cur[1], cur[2], prev[2]};
            g.fillPolygon(xs, ys, 4);
        }
    }

    /**
     * Pixel-y span {top, bottom} (top &lt;= bottom) of the error indicator for one bin.
     * {@code SINGLE} cap style ("T"-shape) draws only the outward half of the error -
     * from the mean out to mean+err (or mean-err, if the mean bar itself extends below
     * the baseline) - so it sits flush against the mean bar's own tip instead of
     * overlapping/hiding the portion of the mean bar between mean-err and mean.
     * {@code DOUBLE} cap style ("I"-beam) keeps the symmetric mean +/- err span except
     * where that span would cross the plot baseline; the inward end is clipped there.
     */
    static int[] errorPixelSpan(DoubleUnaryOperator toPixel, float baseline,
                                float mean, float err, ErrorBarStyle style) {
        // Keep the uncertainty marker on the same side of the baseline as its mean.
        // In particular, SVG exposes even a small symmetric mean +/- error extension
        // beyond the gray midline that raster output can visually hide beneath the line.
        float[] dataSpan = style.dataSpan(baseline, mean, err);
        float low = dataSpan[0];
        float high = dataSpan[1];
        int yLo = (int) toPixel.applyAsDouble(low);
        int yHi = (int) toPixel.applyAsDouble(high);

        // Whether increasing the data value moves toward a larger or smaller pixel coordinate
        // is a property of toPixel alone (it already bakes in any axis flip) - probe it with two
        // arbitrary, always-distinct points rather than comparing already-computed pixel values
        // that can legitimately be equal (e.g. a tiny err rounds low and high to the same row),
        // which would otherwise make the outward direction undecidable right when it matters most.
        boolean pixelIncreasesWithValue = toPixel.applyAsDouble(baseline + 1) > toPixel.applyAsDouble(baseline);
        boolean outwardAtTop = (mean >= baseline) != pixelIncreasesWithValue;

        if (style.getCapStyle() == ErrorBarStyle.CapStyle.SINGLE) {
            // SINGLE ("T"-shape): only the outward half, from the mean out to the outward tip.
            int yMean = (int) toPixel.applyAsDouble(mean);
            int yOutward = mean >= baseline ? yHi : yLo;
            int top = Math.min(yOutward, yMean);
            int bottom = Math.max(yOutward, yMean);
            return new int[]{top, bottom, outwardAtTop ? 1 : 0};
        }
        return new int[]{Math.min(yLo, yHi), Math.max(yLo, yHi), outwardAtTop ? 1 : 0};
    }

    /**
     * Replicates {@code XYPlotRenderer.renderScores()}'s pixel-y formula for the mean
     * bar's own top/bottom edge (used by both Bar and Points render modes, since
     * {@code PointsRenderer} doesn't override {@code renderScores}). The calculation stays
     * continuous until {@link XYPlotRenderer#clampYPixel} performs the sole conversion to
     * the shared integer plot coordinate. The full data range maps onto the rectangle's actual
     * rows ({@code y} through {@code y + height - 1}), exactly as in XYPlotRenderer.
     */
    static int barModeYPixel(Rectangle rect, DataRange dataRange, float dataY) {
        return XYPlotRenderer.dataYPixel(rect, dataRange, dataY);
    }

    private static AverageErrorLocusScore errorScoreOf(LocusScore score, int minimumN) {
        if (score instanceof AverageErrorLocusScore) {
            AverageErrorLocusScore es = (AverageErrorLocusScore) score;
            return es.getN() >= minimumN ? es : null;
        }
        return null;
    }

    private static Color errorBarColor(Track track, ErrorBarStyle style, float mean,
                                       DataRange dataRange, RenderContext context) {
        if (style.getColorOverride() != null) {
            return style.getColorOverride();
        }
        Color base = mean >= dataRange.getBaseline()
                ? context.getPositiveColor(track) : context.getNegativeColor(track);
        return base != null ? base.darker() : ErrorBarStyle.DEFAULT_COLOR;
    }

    private static void drawOne(RenderContext context, Color color, double pX, double dx,
                                int top, int bottom, boolean outwardAtTop,
                                ErrorBarStyle style, XYPlotRenderer renderer) {
        if (bottom <= top) {
            // The error span rounds to less than a single pixel at this scale (typically a
            // low-signal bin on a track whose dynamic range is set by much taller peaks
            // elsewhere) - nothing meaningful to draw, matching BarChartRenderer.drawDataPoint()'s
            // own "skip when height <= 0" rule for the mean bar. Forcing a visible 1px mark here
            // instead used to grow unconditionally downward, which could push a baseline-clamped,
            // degenerate error mark one row past the gray midline in SVG/PDF export.
            return;
        }
        Graphics2D g = context.getGraphic2DForColor(color);
        double centerX = pX + dx / 2;
        int height = bottom - top;

        if (style.getShape() == ErrorBarStyle.Shape.BAR) {
            double barWidth = dx * style.getBarWidthPercent() / 100.0;
            double barX = centerX - barWidth / 2;
            renderer.fillScoreRectangle(g, barX, top, barWidth, height);
        } else {
            Stroke oldStroke = g.getStroke();
            g.setStroke(new BasicStroke(Math.max(1, style.getLineWidthPx())));
            renderer.drawScoreLine(g, centerX, top, centerX, bottom);
            double capHalf = Math.max(2, dx / 4);
            if (style.getCapStyle() == ErrorBarStyle.CapStyle.DOUBLE) {
                // "I"-beam: cap at both ends.
                renderer.drawScoreLine(g, centerX - capHalf, top, centerX + capHalf, top);
                renderer.drawScoreLine(g, centerX - capHalf, bottom, centerX + capHalf, bottom);
            } else {
                // "T"-shape: cap only at the outward tip (away from the mean), whichever end
                // that is - for a below-baseline mean the outward tip is the bottom end, not
                // the top.
                int capY = outwardAtTop ? top : bottom;
                renderer.drawScoreLine(g, centerX - capHalf, capY, centerX + capHalf, capY);
            }
            g.setStroke(oldStroke);
        }
    }
}
