/*
 * DataRenderer.java
 *
 * Created on November 27, 2007, 9:20 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */


package org.igv.renderer;


import org.igv.logging.*;
import org.igv.Globals;
import org.igv.feature.LocusScore;
import org.igv.prefs.IGVPreferences;
import org.igv.prefs.PreferencesManager;
import org.igv.track.RenderContext;
import org.igv.track.Track;
import org.igv.ui.FontManager;

import javax.swing.UIManager;
import java.awt.*;
import java.util.List;

import static org.igv.prefs.Constants.CHART_DRAW_Y_AXIS;
import static org.igv.prefs.Constants.TRACK_BACKGROUND_COLOR;

/**
 * @author jrobinso
 */
public abstract class DataRenderer implements Renderer<LocusScore> {

    private static Logger log = LogManager.getLogger(DataRenderer.class);

    protected static final int AXIS_AREA_WIDTH = 60;
    protected static Color axisLineColor = new Color(255, 180, 180);


    public int getMinimumHeight() {
        return 20;
    }

    /**
     * Render the track in the given rectangle.
     *
     * @param track
     * @param scores
     * @param context
     * @param rect
     */
    public void render(List<LocusScore> scores, RenderContext context, Rectangle rect, Track track) {

        if (scores != null) {
            // Prevent modification of the scores collection during rendering.  This collection
            // has caused concurrent modification exceptions.
            synchronized (scores) {
                renderScores(track, scores, context, rect);
                renderAxis(track, context, rect);
            }
        }
        renderGuides(track, context, rect);
    }

    /**
     * Render a border.  By default does nothing.
     *
     * @param track
     * @param context
     * @param rect
     */
    public void renderGuides(Track track, RenderContext context, Rectangle rect) {
    }

    /**
     * Render a Y axis.  By default does nothing.
     *
     * @param track
     * @param context
     * @param rect
     */
    public void renderAxis(Track track, RenderContext context, Rectangle rect) {
        IGVPreferences prefs = PreferencesManager.getPreferences();

        // For now disable axes for all chromosome view
        if (context.getChr().equals(Globals.CHR_ALL)) {
            return;
        }
        if (prefs.getAsBoolean(CHART_DRAW_Y_AXIS)) {

            Rectangle axisRect = new Rectangle(rect.x, rect.y + 1, AXIS_AREA_WIDTH, rect.height);
            // Re-covers whatever renderScores() drew under the axis-label strip with the track's
            // own background color (not a hardcoded white), so this strip doesn't show up as a
            // visibly different-colored column against the rest of a customized track background.
            boolean darkMode = Globals.isDarkMode();
            Color override = track.getBackgroundColorOverride();
            Color axisBackground = override != null ? override
                    : darkMode && !prefs.hasExplicitValue(TRACK_BACKGROUND_COLOR)
                    ? UIManager.getColor("Panel.background")
                    : prefs.getAsColor(TRACK_BACKGROUND_COLOR);
            Graphics2D axisBackgroundGraphics = context.getScreenGraphic2DForColor(axisBackground);

            axisBackgroundGraphics.fillRect(axisRect.x, axisRect.y, axisRect.width, axisRect.height);

            Graphics2D axisGraphics = context.getScreenGraphic2DForColor(axisLineColor);

            axisGraphics.drawLine(rect.x + AXIS_AREA_WIDTH, rect.y, rect.x + AXIS_AREA_WIDTH,
                    rect.y + rect.height);
        }


    }

    /**
     * Render the provided scores. No border, scales, axes, or anything else
     *
     * @param track
     * @param scores
     * @param context
     * @param arect
     */
    public abstract void renderScores(Track track, List<LocusScore> scores,
                                      RenderContext context, Rectangle arect);


    /**
     * Draw scale in top left of rectangle
     *
     * @param range
     * @param context
     * @param arect
     */
    public static void drawScale(DataRange range, RenderContext context, Rectangle arect) {
        if (range != null && context.multiframe == false) {
            Graphics2D g = context.createScreenGraphics();
            if(Globals.isDarkMode()) {
                g.setColor(Color.WHITE);
            } else {
                g.setColor(Color.BLACK);
            }
            Font smallFont = FontManager.getFont(8);
            try {
                g.setFont(smallFont);
                String minString = range.getMinimum() == 0f ? "0" : String.format("%.3f", range.getMinimum());
                String fmtString = range.getMaximum() > 10 ? "%.0f" : "%.2f";
                String maxString = String.format(fmtString, range.getMaximum());
                String scale = "[" + minString + " - " + maxString + "]";
                GraphicUtils.drawStringUpright(g, scale, arect.x + 5, arect.y + 10);

            } finally {
                g.dispose();
            }
        }
    }

}
