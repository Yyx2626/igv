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
     * for every bin with at least 2 contributing members. Used by Bar and Points modes.
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
        DataRange dataRange = track.getDataRange();
        double origin = context.getOrigin();
        double locScale = context.getScale();

        for (LocusScore score : locusScores) {
            AverageErrorLocusScore es = errorScoreOf(score);
            if (es == null) {
                continue;
            }
            float mean = es.getScore();
            float err = errTrack.getErrorBarType() == ErrorBarType.SD ? es.getSd() : es.getSem();
            if (Float.isNaN(mean) || Float.isNaN(err) || err <= 0) {
                continue;
            }

            double pX = (score.getStart() - origin) / locScale;
            double dx = Math.ceil(Math.max(1, score.getEnd() - score.getStart()) / locScale) + 1;
            if (pX + dx < 0) {
                continue;
            } else if (pX > rect.getMaxX()) {
                break;
            }

            // Use the exact same baseY-then-delta pixel formula BarChartRenderer/
            // XYPlotRenderer.renderScores() uses for the mean bar itself (not the
            // single-rounding computeYPixelValue()) - otherwise the two round slightly
            // differently and a 1px white gap can appear between the mean bar's top edge
            // and the error bar sitting flush against it.
            int[] span = errorPixelSpan(dataY -> barModeYPixel(rect, dataRange, (float) dataY),
                    dataRange.getBaseline(), mean, err, style);

            Color color = errorBarColor(track, style, mean, dataRange);
            drawOne(context, color, (int) pX, (int) dx, span[0], span[1], style);
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
        DataRange dataRange = track.getDataRange();
        double origin = context.getOrigin();
        double locScale = context.getScale();

        List<int[]> segments = new ArrayList<>(); // {x, yHi, yLo}

        for (LocusScore score : locusScores) {
            AverageErrorLocusScore es = errorScoreOf(score);
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

        Color color = errorBarColor(track, style, 0, dataRange);
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
     * {@code DOUBLE} cap style ("I"-beam) keeps the traditional symmetric mean +/- err
     * span on both sides.
     */
    private static int[] errorPixelSpan(DoubleUnaryOperator toPixel, float baseline,
                                         float mean, float err, ErrorBarStyle style) {
        int yLo = (int) toPixel.applyAsDouble(mean - err);
        int yHi = (int) toPixel.applyAsDouble(mean + err);
        if (style.getCapStyle() == ErrorBarStyle.CapStyle.SINGLE) {
            int yMean = (int) toPixel.applyAsDouble(mean);
            if (mean >= baseline) {
                return new int[]{Math.min(yHi, yMean), Math.max(yHi, yMean)};
            } else {
                return new int[]{Math.min(yMean, yLo), Math.max(yMean, yLo)};
            }
        }
        return new int[]{Math.min(yLo, yHi), Math.max(yLo, yHi)};
    }

    /**
     * Replicates {@code XYPlotRenderer.renderScores()}'s exact pixel-y formula for the
     * mean bar's own top/bottom edge (used by both Bar and Points render modes, since
     * {@code PointsRenderer} doesn't override {@code renderScores}): {@code baseY} is
     * rounded to an {@code int} first, then the data-to-baseline delta is rounded and
     * subtracted from it - two separate roundings, unlike {@code computeYPixelValue}'s
     * single rounding of the whole expression. Using {@code computeYPixelValue} here
     * instead can be off by a pixel from where the mean bar's edge actually lands,
     * leaving a thin white gap between the mean bar and an error bar drawn flush against it.
     */
    private static int barModeYPixel(Rectangle rect, DataRange dataRange, float dataY) {
        float maxValue = dataRange.getMaximum();
        float baseValue = dataRange.getBaseline();
        float minValue = dataRange.getMinimum();
        boolean isLog = dataRange.isLog();
        if (isLog) {
            minValue = (float) (minValue == 0 ? 0 : Math.log10(minValue));
            maxValue = (float) Math.log10(maxValue);
        }
        double yScaleFactor = rect.getHeight() / (double) (maxValue - minValue);
        double baseDelta = maxValue - baseValue;
        int baseY = (int) (rect.getY() + baseDelta * yScaleFactor);
        baseY = Math.max(rect.y, Math.min(rect.y + rect.height, baseY));

        double dy = isLog ? Math.log10(dataY) - baseValue : (dataY - baseValue);
        int pY = baseY - (int) (dy * yScaleFactor);
        return Math.max(rect.y, Math.min(rect.y + rect.height, pY));
    }

    private static AverageErrorLocusScore errorScoreOf(LocusScore score) {
        if (score instanceof AverageErrorLocusScore) {
            AverageErrorLocusScore es = (AverageErrorLocusScore) score;
            return es.getN() >= 2 ? es : null;
        }
        return null;
    }

    private static Color errorBarColor(Track track, ErrorBarStyle style, float mean, DataRange dataRange) {
        if (style.getColorOverride() != null) {
            return style.getColorOverride();
        }
        Color base = mean >= dataRange.getBaseline() ? track.getColor() : track.getAltColor();
        return base != null ? base.darker() : ErrorBarStyle.DEFAULT_COLOR;
    }

    private static void drawOne(RenderContext context, Color color, int pX, int dx, int top, int bottom, ErrorBarStyle style) {
        Graphics2D g = context.getGraphic2DForColor(color);
        int centerX = pX + dx / 2;
        int height = Math.max(1, bottom - top);

        if (style.getShape() == ErrorBarStyle.Shape.BAR) {
            int barWidth = Math.max(1, (int) Math.round(dx * style.getBarWidthPercent() / 100.0));
            int barX = centerX - barWidth / 2;
            g.fillRect(barX, top, barWidth, height);
        } else {
            Stroke oldStroke = g.getStroke();
            g.setStroke(new BasicStroke(Math.max(1, style.getLineWidthPx())));
            g.drawLine(centerX, top, centerX, bottom);
            int capHalf = Math.max(2, dx / 4);
            // SINGLE ("T"-shape): cap at the top end only. DOUBLE ("I"-beam): caps at both ends.
            g.drawLine(centerX - capHalf, top, centerX + capHalf, top);
            if (style.getCapStyle() == ErrorBarStyle.CapStyle.DOUBLE) {
                g.drawLine(centerX - capHalf, bottom, centerX + capHalf, bottom);
            }
            g.setStroke(oldStroke);
        }
    }
}
