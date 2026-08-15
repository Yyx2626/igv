package org.igv.renderer;

import org.igv.feature.LocusScore;
import org.igv.track.RenderContext;
import org.igv.track.Track;

import java.awt.Rectangle;
import java.util.List;

/**
 * "Points" render mode for {@code AverageErrorBarTrack}: draws the error bar first, then
 * the average point marker on top of it (via {@link PointsRenderer}), so the point stays
 * legible against the error bar behind it.
 */
public class AverageErrorBarPointsRenderer extends PointsRenderer {

    @Override
    public String getDisplayName() {
        return "Average + Error Bar (Points)";
    }

    @Override
    public synchronized void renderScores(Track track, List<LocusScore> locusScores, RenderContext context, Rectangle rect) {
        AverageErrorBarPainter.drawErrorBars(track, locusScores, context, rect, this);
        super.renderScores(track, locusScores, context, rect);
        AverageScatterPointPainter.draw(track, locusScores, context, rect, this);
    }
}
