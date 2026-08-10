package org.igv.renderer;


import org.igv.feature.LocusScore;
import org.igv.prefs.IGVPreferences;
import org.igv.prefs.PreferencesManager;
import org.igv.track.RenderContext;
import org.igv.track.Track;
import org.igv.ui.FontManager;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.List;

import static org.igv.prefs.Constants.*;

/**
 * @author jrobinso
 */
public abstract class XYPlotRenderer extends DataRenderer {

    static DecimalFormat formatter = new DecimalFormat();

    protected void drawDataPoint(Color graphColor, int dx, int pX, int baseY, int pY,
                                 LocusScore score, RenderContext context) {
        context.getGraphic2DForColor(graphColor).fillRect(pX, pY, dx, 2);

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

        Color posColor = context.getPositiveColor(track);
        Color negColor = context.getNegativeColor(track);

        // Get the Y axis definition, consisting of minimum, maximum, and base value.  Often
        // the base value is == min value which is == 0.

        DataRange dataRange = context.getDataRange(track);
        float maxValue = dataRange.getMaximum();
        float baseValue = dataRange.getBaseline();
        float minValue = dataRange.getMinimum();
        boolean isLog = dataRange.isLog();

        if (isLog) {
            minValue = (float) (minValue == 0 ? 0 : Math.log10(minValue));
            maxValue = (float) Math.log10(maxValue);
        }


        // Calculate the Y scale factor.

        double delta = (maxValue - minValue);
        double yScaleFactor = rect.getHeight() / delta;

        // Calculate the Y position in pixels of the base value.  Clip to bounds of rectangle
        double baseDelta = maxValue - baseValue;
        double rawBaseY = rect.getY() + baseDelta * yScaleFactor;
        int baseY = clampYPixel(rect, rawBaseY);

        for (LocusScore score : locusScores) {

            // Note -- don't cast these to an int until the range is checked.
            // could get an overflow.
            double pX = ((score.getStart() - origin) / locScale);
            double dx = Math.ceil((Math.max(1, score.getEnd() - score.getStart())) / locScale) + 1;
            if ((pX + dx < 0)) {
                continue;
            } else if (pX > rect.getMaxX()) {
                break;
            }

            float dataY = score.getScore();
            if (isLog && dataY <= 0) {
                continue;
            }

            if (!Float.isNaN(dataY)) {

                // Compute the pixel y location.  Clip to bounds of rectangle.
                double dy = isLog ? Math.log10(dataY) - baseValue : (dataY - baseValue);
                int pY = clampYPixel(rect, rawBaseY - (int) (dy * yScaleFactor));

                Color color = (dataY >= baseValue) ? posColor : negColor;
                drawDataPoint(color, (int) dx, (int) pX, baseY, pY, score, context);

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
            float maxValue = axisDefinition.getMaximum();
            float baseValue = axisDefinition.getBaseline();
            float minValue = axisDefinition.getMinimum();

            double maxX = arect.getMaxX();
            double x = arect.getX();
            double y = arect.getY();

            if ((baseValue > minValue) && (baseValue < maxValue)) {
                int baseY = computeYPixelValue(arect, axisDefinition, baseValue);

                getBaselineGraphics(context, axisDefinition).drawLine((int) x, baseY, (int) maxX, baseY);
            }

            // Default to lightGray, not the track's own color: matching-color mid lines are
            // easy to mistake for actual data (especially since a track's altColor commonly
            // defaults to the same as its main color), and are hard to see against the track's
            // own bars at all. Users can still explicitly opt into track-colored mid lines via
            // the color swatch in the Data Range dialog (DataRange.midlineColor).
            final Color borderColor = axisDefinition.getMidlineColor() != null
                    ? axisDefinition.getMidlineColor()
                    : DataRange.DEFAULT_MIDLINE_COLOR;
            Graphics2D borderGraphics = context.getGraphic2DForColor(borderColor);

            // Draw the baseline -- todo, this is a wig track option?
            double zeroValue = axisDefinition.getBaseline();
            int zeroY = computeYPixelValue(arect, axisDefinition, zeroValue);
            borderGraphics.drawLine(arect.x, zeroY, arect.x + arect.width, zeroY);

            // Optionally draw "Y" line  (UCSC track line option)
            if (track.isDrawYLine()) {
                Graphics2D yLineGraphics = context.getGraphic2DForColor(Color.gray);
                int yLine = computeYPixelValue(arect, axisDefinition, track.getYLine());
                GraphicUtils.drawDashedLine(yLineGraphics, arect.x, yLine, arect.x + arect.width, yLine);
            }

        }
    }

    /**
     * Get a graphics object for the baseline.
     * TODO -- make the line style settable by the user
     *
     * @param context
     * @return
     */
    private static Graphics2D getBaselineGraphics(RenderContext context, DataRange axisDefinition) {
        Color color = axisDefinition.getMidlineColor() != null ? axisDefinition.getMidlineColor() : DataRange.DEFAULT_MIDLINE_COLOR;
        return (Graphics2D) context.getGraphic2DForColor(color).create();
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

        double maxValue = axisDefinition.getMaximum();
        double minValue = axisDefinition.getMinimum();

        double yScaleFactor = drawingRect.getHeight() / (maxValue - minValue);

        // Compute the pixel y location.  Clip to bounds of rectangle.
        // The distince in pixels frmo the data value to the axis maximum
        double delta = (maxValue - dataY) * yScaleFactor;
        double pY = drawingRect.getY() + delta;

        // getMaxY() is y+height - one past the last actually-paintable row (rows are y..y+height-1,
        // since AWT/Java2D clip rectangles are half-open). Clamping to getMaxY() itself lands
        // exactly on that excluded row, so a value at/near the data range's max (e.g. the "Mid"
        // guide line when set at a track's own bottom edge) gets computed but never actually
        // painted - looks identical to being clipped away.
        return clampYPixel(drawingRect, pY);
    }

    /**
     * Clamp to actual paintable rows.  AWT rectangles are half-open: y + height is outside the
     * rectangle.  Keeping bars and guide lines on this same boundary is especially important for
     * SVG, where a filled rectangle ending at y + height would visibly extend past the baseline.
     */
    static int clampYPixel(Rectangle drawingRect, double pixelY) {
        if (drawingRect.height <= 0) return drawingRect.y;
        return (int) Math.max(drawingRect.getMinY(),
                Math.min(drawingRect.getMaxY() - 1, pixelY));
    }
}
