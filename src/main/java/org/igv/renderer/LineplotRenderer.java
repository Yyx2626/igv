/*
 * TrackRenderer.java
 *
 * Created on Sep 6, 2007, 10:07:39 AM
 *
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.igv.renderer;

import org.igv.feature.LocusScore;
import org.igv.track.RenderContext;
import org.igv.track.Track;

import java.awt.*;
import java.util.List;


/**
 * @author jrobinso
 */
public class LineplotRenderer extends XYPlotRenderer {

    /**
     * Render the track in the given rectangle.
     *
     * @param track
     * @param locusScores
     * @param context
     * @param rect
     */
    @Override
    public void renderScores(Track track, List<LocusScore> locusScores, RenderContext context, Rectangle rect) {

        if (locusScores.size() == 0) return;

        double origin = context.getOrigin();
        double locScale = context.getScale();

        Color posColor = context.getPositiveColor(track);
        Color negColor = context.getNegativeColor(track);

        Graphics2D gPos = context.getGraphic2DForColor(posColor);
        Graphics2D gNeg = context.getGraphic2DForColor(negColor);

        // Get the Y axis definition, consisting of minimum, maximum, and base value.  Often
        // the base value is == min value which is == 0.
        DataRange axisDefinition = context.getDataRange(track);
        float maxValue = (float) axisDefinition.getMaximum();
        float baseValue = (float) axisDefinition.getBaseline();
        float minValue = (float) axisDefinition.getMinimum();

        // Calculate the Y scale factor.
        int lastPx = (int) ((locusScores.get(0).getStart() - origin) / locScale);
        int lastPy = computeYPixelValue(rect, axisDefinition, 0);

        for (LocusScore score : locusScores) {

            float dataY = score.getScore();
            // Missing data in a dataset is signifed by NaN.  Just skip these.
            if (!Float.isNaN(dataY)) {

                double x = ((score.getStart() - origin) / locScale);
                double dx = (score.getEnd() - score.getStart()) / locScale;
                int pX = (int) x;
                int pY = computeYPixelValue(rect, axisDefinition, dataY);
                double slope = ((double) pY - lastPy) / (pX - lastPx);

                int clippedLastPX = lastPx;
                int clippedLastPY = lastPy;
                int lastRow = rect.y + Math.max(0, rect.height - 1);
                if (lastPy < rect.y || lastPy > lastRow) {
                    clippedLastPY = (lastPy < rect.y) ? rect.y : lastRow;
                    clippedLastPX = lastPx + (int) ((clippedLastPY - lastPy) / slope);
                }

                int clippedPX = pX;
                int clippedPY = pY;

                // Clip and interpolate if neccessary
                if (pY < rect.y || pY > lastRow) {
                    clippedPY = (pY < rect.getMinY()) ? rect.y : lastRow;
                    clippedPX = lastPx + (int) ((clippedPY - lastPy) / slope);
                }

                Graphics2D g = (dataY >= 0) ? gPos : gNeg;

                g.drawLine(clippedLastPX, clippedLastPY, clippedPX, clippedPY);

                if (dx >= 1 && (clippedPY == pY)) {
                    g.drawLine(pX, clippedPY, (int) (pX + dx), clippedPY);
                }

                lastPx = (int) (pX + dx);
                lastPy = (int) pY;

                if (lastPx > rect.getMaxX()) {
                    break;
                }
            }
        }

    }
}
