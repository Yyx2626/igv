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
    protected void drawDataPoint(Color graphColor, int dx, int pX, int baseY, int pY, LocusScore score, RenderContext context) {
        Rectangle bounds = barBounds(pX, dx, baseY, pY);
        if (bounds.height > 0) {
            // Use a filled rectangle even for one-pixel bars. SVG line caps extend beyond their
            // endpoints, which can make the bar protrude across the horizontal baseline.
            context.getGraphic2DForColor(graphColor).fillRect(
                    bounds.x, bounds.y, bounds.width, bounds.height);
        }
    }

    static Rectangle barBounds(int pX, int width, int baseY, int valueY) {
        return new Rectangle(pX, Math.min(baseY, valueY), Math.max(1, width),
                Math.abs(valueY - baseY));
    }
}
