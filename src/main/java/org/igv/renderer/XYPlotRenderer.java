package org.igv.renderer;


import org.igv.feature.LocusScore;
import org.igv.prefs.IGVPreferences;
import org.igv.prefs.PreferencesManager;
import org.igv.track.RenderContext;
import org.igv.track.Track;
import org.igv.ui.FontManager;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.List;

import static org.igv.prefs.Constants.*;

/**
 * @author jrobinso
 */
public abstract class XYPlotRenderer extends DataRenderer {

    static DecimalFormat formatter = new DecimalFormat();
    private final Rectangle2D.Double reusableRectangle = new Rectangle2D.Double();
    private final Line2D.Double reusableLine = new Line2D.Double();

    protected void drawDataPoint(Color graphColor, double dx, double pX, int baseY, int pY,
                                 LocusScore score, RenderContext context) {
        fillScoreRectangle(context.getGraphic2DForColor(graphColor), pX, pY, dx, 2);

    }

    /**
     * Render the track in the given rectangle.
     *
     * @param track
     * @param locusScores
     * @param context
     */
    public synchronized void renderScores(Track track, List<LocusScore> locusScores, RenderContext context, Rectangle rect) {

        double origin = context.getOrigin();
        double locScale = context.getScale();
        if (!Double.isFinite(locScale) || locScale <= 0) return;

        Color posColor = context.getPositiveColor(track);
        Color negColor = context.getNegativeColor(track);

        // Get the Y axis definition, consisting of minimum, maximum, and base value.  Often
        // the base value is == min value which is == 0.

        DataRange dataRange = context.getDataRange(track);
        float baseValue = dataRange.getBaseline();
        boolean isLog = dataRange.isLog();
        int baseY = dataYPixel(rect, dataRange, baseValue);
        double barOverlap = barOverlapPixels();

        for (LocusScore score : locusScores) {

            ScorePixelSpan xSpan = scorePixelSpan(score, origin, locScale, barOverlap);
            if ((xSpan.rawStart() + xSpan.paintWidth() < 0)) {
                continue;
            } else if (xSpan.rawStart() > rect.getMaxX()) {
                break;
            }

            float dataY = score.getScore();
            if (isLog && dataY <= 0) {
                continue;
            }

            if (!Float.isNaN(dataY)) {

                // Compute the pixel y location.  Clip to bounds of rectangle.
                int pY = dataYPixel(rect, dataRange, dataY);

                Color color = (dataY >= baseValue) ? posColor : negColor;
                drawDataPoint(color, scorePixelWidth(xSpan), xSpan.rawStart(),
                        baseY, pY, score, context);

            }
        }
    }

    /**
     * Method description
     *
     * @param track
     * @param context
     */
    @Override
    public void renderAxis(Track track, RenderContext context, Rectangle ignore) {

        if (context.multiframe) {
            return;
        }

        Rectangle arect = context.getTrackRectangle();

        super.renderAxis(track, context, arect);

        IGVPreferences prefs = PreferencesManager.getPreferences();

        Color labelColor = prefs.getAsBoolean(CHART_COLOR_TRACK_NAME) ? context.getPositiveColor(track) : Color.black;
        Graphics2D labelGraphics = context.getScreenGraphic2DForColor(labelColor);

        labelGraphics.setFont(FontManager.getFont(8));

        if (prefs.getAsBoolean(CHART_DRAW_TRACK_NAME)) {

            // Only attempt if track height is > 25 pixels
            if (arect.getHeight() > 25) {
                Rectangle labelRect = new Rectangle(arect.x, arect.y + 10, arect.width, 10);
                labelGraphics.setFont(FontManager.getFont(10));
                GraphicUtils.drawCenteredText(track.getName(), labelRect, labelGraphics);
            }
        }

        if (prefs.getAsBoolean(CHART_DRAW_Y_AXIS)) {

            Rectangle axisRect = new Rectangle(arect.x, arect.y + 1, AXIS_AREA_WIDTH, arect.height);

            DataRange axisDefinition = context.getDataRange(track);
            float maxValue = axisDefinition.getMaximum();
            float baseValue = axisDefinition.getBaseline();
            float minValue = axisDefinition.getMinimum();
            
            // Bottom (minimum tick mark)
            int pY = computeYPixelValue(arect, axisDefinition, minValue);

            labelGraphics.drawLine(axisRect.x + AXIS_AREA_WIDTH - 10, pY, axisRect.x + AXIS_AREA_WIDTH - 5, pY);
            GraphicUtils.drawRightJustifiedText(formatter.format(minValue), axisRect.x + AXIS_AREA_WIDTH - 15, pY, labelGraphics);

            // Top (maximum tick mark)
            int topPY = computeYPixelValue(arect, axisDefinition, maxValue);

            labelGraphics.drawLine(axisRect.x + AXIS_AREA_WIDTH - 10, topPY,
                    axisRect.x + AXIS_AREA_WIDTH - 5, topPY);
            // Baseline offset by the font's ascent (not a flat "+4"), so the label's glyphs
            // stay below topPY (near the very top of the track rect) instead of extending
            // above it - a flat offset smaller than the ascent let the label bleed into
            // whatever sits directly above this track (e.g. TrackPanelDivider), clipping it.
            int topLabelBaseline = topPY + labelGraphics.getFontMetrics().getAscent();
            GraphicUtils.drawRightJustifiedText(formatter.format(maxValue),
                    axisRect.x + AXIS_AREA_WIDTH - 15, topLabelBaseline, labelGraphics);

            // Connect top and bottom
            labelGraphics.drawLine(axisRect.x + AXIS_AREA_WIDTH - 10, topPY,
                    axisRect.x + AXIS_AREA_WIDTH - 10, pY);

            // Middle tick mark.  Draw only if room
            int midPY = computeYPixelValue(arect, axisDefinition, baseValue);

            if ((midPY < pY - 15) && (midPY > topPY + 15)) {
                labelGraphics.drawLine(axisRect.x + AXIS_AREA_WIDTH - 10, midPY,
                        axisRect.x + AXIS_AREA_WIDTH - 5, midPY);
                GraphicUtils.drawRightJustifiedText(formatter.format(baseValue),
                        axisRect.x + AXIS_AREA_WIDTH - 15, midPY + 4, labelGraphics);
            }

        } else if (track.isShowDataRange() && arect.height > 20) {
            drawScale(context.getDataRange(track), context, arect);
        }
    }

    @Override
    public void renderGuides(Track track, RenderContext context, Rectangle ignore) {

        Rectangle arect = context.getTrackRectangle();

        // Draw boundaries if there is room
        if (arect.getHeight() >= 10) {

            ///TrackProperties pros = track.getProperties();

            // midline
            DataRange axisDefinition = context.getDataRange(track);

            // Default to lightGray, not the track's own color: matching-color mid lines are
            // easy to mistake for actual data (especially since a track's altColor commonly
            // defaults to the same as its main color), and are hard to see against the track's
            // own bars at all. Users can still explicitly opt into track-colored mid lines via
            // the color swatch in the Data Range dialog (DataRange.midlineColor).
            final Color borderColor = axisDefinition.getMidlineColor() != null
                    ? axisDefinition.getMidlineColor()
                    : DataRange.DEFAULT_MIDLINE_COLOR;
            Graphics2D borderGraphics = context.getGraphic2DForColor(borderColor);

            if (axisDefinition.isDrawBaseline()) {
                double zeroValue = axisDefinition.getBaseline();
                int zeroY = computeYPixelValue(arect, axisDefinition, zeroValue);
                // A one-row fill follows the same half-open pixel convention as the bars.
                // drawLine is stroke-centered and can cover a different Retina device row at
                // a component edge, which makes a bar appear to cross its own baseline.
                borderGraphics.fillRect(arect.x, zeroY, arect.width, 1);
            }

            // Optionally draw "Y" line  (UCSC track line option)
            if (track.isDrawYLine()) {
                Graphics2D yLineGraphics = context.getGraphic2DForColor(Color.gray);
                int yLine = computeYPixelValue(arect, axisDefinition, track.getYLine());
                GraphicUtils.drawDashedLine(yLineGraphics, arect.x, yLine, arect.x + arect.width, yLine);
            }

        }
    }

    /**
     * Method description
     *
     * @return
     */
    public String getDisplayName() {
        return "Scatter Plot";
    }

    protected int computeYPixelValue(Rectangle drawingRect, DataRange axisDefinition, double dataY) {
        return dataYPixel(drawingRect, axisDefinition, dataY);
    }

    /**
     * Horizontal bin geometry shared by the signal and scatter painters. {@code ownedWidth}
     * is the true interval from the score's start to end. {@code paintWidth} adds the
     * user-configurable right-side overlap used to cover rasterization seams between bars.
     */
    static ScorePixelSpan scorePixelSpan(LocusScore score, double origin, double scale) {
        return scorePixelSpan(score, origin, scale, barOverlapPixels());
    }

    static ScorePixelSpan scorePixelSpan(LocusScore score, double origin, double scale,
                                         double barOverlap) {
        double rawStart = (score.getStart() - origin) / scale;
        double ownedWidth = Math.max(1.0, score.getEnd() - score.getStart()) / scale;
        double effectiveOverlap = Math.min(Math.max(0, barOverlap), ownedWidth);
        return new ScorePixelSpan(rawStart, ownedWidth, effectiveOverlap);
    }

    record ScorePixelSpan(double rawStart, double ownedWidth, double overlapWidth) {
        double paintWidth() {
            return ownedWidth + overlapWidth;
        }

        double scatterUsableWidth() {
            return Math.max(0, ownedWidth - overlapWidth());
        }

        double scatterUsableLeft() {
            double paintedCenter = rawStart + paintWidth() / 2.0;
            return paintedCenter - scatterUsableWidth() / 2.0;
        }
    }

    protected double scorePixelWidth(ScorePixelSpan span) {
        return span.paintWidth();
    }

    static double barOverlapPixels() {
        double configured = PreferencesManager.getPreferences().getAsFloat(CHART_BAR_OVERLAP);
        return Double.isFinite(configured) ? Math.max(0, configured) : 0;
    }

    /** Fill a logical-pixel rectangle without allocating a Shape for every score. */
    protected final void fillScoreRectangle(Graphics2D graphics, double x, double y,
                                            double width, double height) {
        if (isIntegerPixel(x) && isIntegerPixel(y)
                && isIntegerPixel(width) && isIntegerPixel(height)) {
            graphics.fillRect((int) x, (int) y, (int) width, (int) height);
        } else {
            reusableRectangle.setRect(x, y, width, height);
            graphics.fill(reusableRectangle);
        }
    }

    /** Draw a logical-pixel line without allocating a Shape for every error-bar segment. */
    protected final void drawScoreLine(Graphics2D graphics, double x1, double y1,
                                       double x2, double y2) {
        if (isIntegerPixel(x1) && isIntegerPixel(y1)
                && isIntegerPixel(x2) && isIntegerPixel(y2)) {
            graphics.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
        } else {
            reusableLine.setLine(x1, y1, x2, y2);
            graphics.draw(reusableLine);
        }
    }

    private static boolean isIntegerPixel(double value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                && value == Math.rint(value);
    }

    /** Map a value to a pixel row relative to the same mapped baseline used by all guides. */
    static int dataYPixel(Rectangle drawingRect, DataRange axisDefinition, double dataY) {

        double maxValue = axisDefinition.getMaximum();
        double minValue = axisDefinition.getMinimum();
        double baseValue = axisDefinition.getBaseline();

        if (axisDefinition.isLog()) {
            minValue = minValue == 0 ? 0 : Math.log10(minValue);
            maxValue = Math.log10(maxValue);
            // Preserve IGV's existing log-axis convention that a zero baseline remains at
            // numeric zero while positive data values are transformed.
            if (Double.compare(dataY, baseValue) != 0) {
                dataY = Math.log10(dataY);
            }
        }

        double yScaleFactor = Math.max(0, drawingRect.getHeight() - 1) /
                (maxValue - minValue);
        int baselineY = clampYPixel(drawingRect,
                drawingRect.getY() + (maxValue - baseValue) * yScaleFactor);
        double signedDistance = (dataY - baseValue) * yScaleFactor;
        int pixelDistance = (int) Math.round(Math.abs(signedDistance));
        int pixelY = signedDistance < 0
                ? baselineY + pixelDistance
                : baselineY - pixelDistance;
        return clampYPixel(drawingRect, pixelY);
    }

    /**
     * Clamp every numeric renderer to the same set of paintable pixel rows. A rectangle of
     * height {@code h} owns rows {@code y} through {@code y + h - 1}; the next row belongs to
     * the next component. Data values and guides both use this conversion.
     */
    static int clampYPixel(Rectangle drawingRect, double pixelY) {
        if (drawingRect.height <= 0) return drawingRect.y;
        double clamped = Math.max(drawingRect.getMinY(),
                Math.min(drawingRect.getMaxY() - 1, pixelY));
        return drawingRect.y + (int) Math.rint(clamped - drawingRect.y);
    }
}
