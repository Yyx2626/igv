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

import java.awt.*;

/**
 * @author jrobinso
 */
public class BarChartRenderer extends XYPlotRenderer {

    @Override
    public String getDisplayName() {
        return "Bar Chart";
    }

    /**
     * Render the data track as a bar chart.
     */
    @Override
    protected void drawDataPoint(Color graphColor, double dx, double pX, int baseY, int pY,
                                 LocusScore score, RenderContext context) {
        int pixelSpan = Math.abs(pY - baseY);
        if (pixelSpan > 0) {
            // Use a filled rectangle even for one-pixel bars. SVG line caps extend beyond their
            // endpoints, which can make the bar protrude across the horizontal baseline.
            fillScoreRectangle(context.getGraphic2DForColor(graphColor), pX,
                    Math.min(baseY, pY), Math.max(Math.ulp(1.0), dx), pixelSpan + 1);
        }
    }

    static Rectangle barBounds(int pX, int width, int baseY, int valueY) {
        int pixelSpan = Math.abs(valueY - baseY);
        return new Rectangle(pX, Math.min(baseY, valueY), Math.max(1, width),
                pixelSpan == 0 ? 0 : pixelSpan + 1);
    }
}
