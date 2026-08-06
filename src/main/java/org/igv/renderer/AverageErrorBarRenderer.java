package org.igv.renderer;

import org.igv.feature.LocusScore;
import org.igv.track.RenderContext;
import org.igv.track.Track;

import java.awt.Rectangle;
import java.util.List;

/**
 * "Bar Chart" render mode for {@code AverageErrorBarTrack}: draws the mean bar exactly
 * like {@link BarChartRenderer} (delegated to the superclass), then the error bar on top
 * (see {@link AverageErrorBarPainter}).
 */
public class AverageErrorBarRenderer extends BarChartRenderer {

    @Override
    public String getDisplayName() {
        return "Average + Error Bar (Bar Chart)";
    }

    @Override
    public synchronized void renderScores(Track track, List<LocusScore> locusScores, RenderContext context, Rectangle rect) {
        super.renderScores(track, locusScores, context, rect);
        AverageErrorBarPainter.drawErrorBars(track, locusScores, context, rect, this);
    }
}
