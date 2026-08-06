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

            int yLo = renderer.computeYPixelValue(rect, dataRange, mean - err);
            int yHi = renderer.computeYPixelValue(rect, dataRange, mean + err);
            int top = Math.min(yLo, yHi);
            int bottom = Math.max(yLo, yHi);

            Color color = errorBarColor(track, style, mean, dataRange);
            drawOne(context, color, (int) pX, (int) dx, top, bottom, style);
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
            int yHi = renderer.computeYPixelValue(rect, dataRange, mean + err);
            int yLo = renderer.computeYPixelValue(rect, dataRange, mean - err);
            segments.add(new int[]{(int) pX, yHi, yLo});
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
