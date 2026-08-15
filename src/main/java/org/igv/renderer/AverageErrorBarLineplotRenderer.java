package org.igv.renderer;

import org.igv.feature.LocusScore;
import org.igv.track.RenderContext;
import org.igv.track.Track;

import java.awt.Rectangle;
import java.util.List;

/**
 * "Line Plot" render mode for {@code AverageErrorBarTrack}: draws the error region as a
 * filled band connecting consecutive bins' error caps, then the average line on top of
 * it (via {@link LineplotRenderer}), so the line stays legible against the band behind it.
 */
public class AverageErrorBarLineplotRenderer extends LineplotRenderer {

    @Override
    public String getDisplayName() {
        return "Average + Error Bar (Line Plot)";
    }

    @Override
    public synchronized void renderScores(Track track, List<LocusScore> locusScores, RenderContext context, Rectangle rect) {
        AverageErrorBarPainter.drawErrorBand(track, locusScores, context, rect, this);
        super.renderScores(track, locusScores, context, rect);
        AverageScatterPointPainter.draw(track, locusScores, context, rect, this);
    }
}
