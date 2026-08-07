package org.igv.renderer;

import org.igv.feature.LocusScore;
import org.igv.track.AverageErrorBarTrack;
import org.igv.track.RenderContext;
import org.igv.track.Track;

import java.awt.Rectangle;
import java.util.List;

/**
 * "Bar Chart" render mode for {@code AverageErrorBarTrack}: draws the mean bar exactly
 * like {@link BarChartRenderer} (delegated to the superclass) and the error bar (see
 * {@link AverageErrorBarPainter}), in an order that depends on the error bar's cap style.
 * <p>
 * Adjacent bins' bars/error-bars can overlap by a pixel or two at the edges (both this
 * class's mean bars and {@code AverageErrorBarPainter}'s error bars pad their width
 * slightly to avoid gaps between bins), so whichever is drawn LAST wins that overlap.
 * SINGLE cap ("T"-shape) error bars sit flush against their own mean bar's tip with no
 * intentional overlap - drawing them FIRST, then all mean bars on top, means any stray
 * error-bar pixels that bled into a neighboring bin's mean-bar rectangle get painted over
 * with solid blue, avoiding a "blue-tan-blue-tan" stripe. DOUBLE cap ("I"-beam) error bars
 * are intentionally symmetric and expected to sit on top of the mean bar, so they're
 * drawn last, as before.
 */
public class AverageErrorBarRenderer extends BarChartRenderer {

    @Override
    public String getDisplayName() {
        return "Average + Error Bar (Bar Chart)";
    }

    @Override
    public synchronized void renderScores(Track track, List<LocusScore> locusScores, RenderContext context, Rectangle rect) {
        boolean errorBarFirst = track instanceof AverageErrorBarTrack
                && ((AverageErrorBarTrack) track).getErrorBarStyle().getCapStyle() == ErrorBarStyle.CapStyle.SINGLE;
        if (errorBarFirst) {
            AverageErrorBarPainter.drawErrorBars(track, locusScores, context, rect, this);
            super.renderScores(track, locusScores, context, rect);
        } else {
            super.renderScores(track, locusScores, context, rect);
            AverageErrorBarPainter.drawErrorBars(track, locusScores, context, rect, this);
        }
    }
}
