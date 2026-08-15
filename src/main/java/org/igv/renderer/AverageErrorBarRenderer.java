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
 * Mean bars and error bars share the configured bar overlap. Scatter placement is centered
 * on that painted bar and inset by the effective overlap on both sides. SINGLE cap ("T"-shape)
 * error bars are drawn before the mean bars; DOUBLE cap ("I"-beam) error bars are
 * intentionally symmetric and drawn afterward.
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
        AverageScatterPointPainter.draw(track, locusScores, context, rect, this);
    }
}
