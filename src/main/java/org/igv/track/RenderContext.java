package org.igv.track;

import org.igv.prefs.PreferencesManager;
import org.igv.feature.TrackRegionOverride;
import org.igv.renderer.DataRange;
import org.igv.ui.panel.ReferenceFrame;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jrobinso
 */

public class RenderContext {

    private final Graphics2D graphics;
    private Map<Object, Graphics2D> graphicCache;
    private Map<Object, Graphics2D> screenGraphicCache;
    private ReferenceFrame referenceFrame;
    private JComponent panel;
    public Rectangle trackRectangle;
    public Rectangle visibleRect;
    private Rectangle clipBounds;
    public boolean multiframe = false;
    public int expandedInsertionPosition = -1;
    private TrackRegionOverride regionOverride;
    private DataRange regionalDataRange;
    private boolean regionalPass;
    private Double originOverride;
    private Double endOverride;
    private Double scaleOverride;
    private DisplayBinPlan displayBinPlan;
    private Rectangle labelClipBounds;

    /**
     * X translation for this context relative to its parent.  This is used in expanded insertion "multi-frame* view
     * to convert screen coordinates to parent reference system when recording the pixel location of drawn objects
     */
    public int translateX = 0;

    public RenderContext(
            JComponent panel,
            Graphics2D graphics,
            ReferenceFrame referenceFrame,
            Rectangle trackRectangle,
            Rectangle visibleRect,
            Rectangle clipBounds) {
        this.graphics = graphics;
        this.panel = panel;
        this.graphicCache = new HashMap();
        this.screenGraphicCache = new HashMap();
        this.referenceFrame = referenceFrame;
        this.trackRectangle = trackRectangle;
        this.visibleRect = visibleRect;
        this.clipBounds = clipBounds;
        if (PreferencesManager.getPreferences().getAntiAliasing() && graphics != null) {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }

    public RenderContext(RenderContext context) {
        this.graphics = (Graphics2D) context.graphics.create();
        this.graphicCache = new HashMap<>();
        this.screenGraphicCache = new HashMap<>();
        this.referenceFrame = new ReferenceFrame(context.referenceFrame);
        this.panel = context.panel;
        this.trackRectangle = new Rectangle(context.trackRectangle);
        this.visibleRect = new Rectangle(context.visibleRect);
        this.clipBounds = new Rectangle(context.clipBounds);
        this.expandedInsertionPosition = context.expandedInsertionPosition;
        this.regionOverride = context.regionOverride;
        this.regionalDataRange = context.regionalDataRange;
        this.regionalPass = context.regionalPass;
        this.originOverride = context.originOverride;
        this.endOverride = context.endOverride;
        this.scaleOverride = context.scaleOverride;
        this.displayBinPlan = context.displayBinPlan;
        this.labelClipBounds = context.labelClipBounds == null ? null : new Rectangle(context.labelClipBounds);
        if (PreferencesManager.getPreferences().getAntiAliasing() && graphics != null) {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
    }

    public Graphics2D getGraphics() {
        return graphics;
    }

    public void clearGraphicsCache() {
        for(Graphics2D g: graphicCache.values()) {
            g.dispose();
        }
        graphicCache.clear();
        for (Graphics2D g : screenGraphicCache.values()) {
            g.dispose();
        }
        screenGraphicCache.clear();
    }

    public Graphics2D getGraphics2D(Object key) {
        Graphics2D g = graphicCache.get(key);
        if (g == null) {
            g = (Graphics2D) graphics.create();
            if (PreferencesManager.getPreferences().getAntiAliasing()) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            graphicCache.put(key, g);
        }
        return g;
    }

    public Graphics2D getGraphic2DForColor(Color color) {
        Graphics2D g = getGraphics2D(color);
        g.setColor(color);
        return g;
    }

    /** Graphics for axes and labels that must stay in physical screen coordinates. */
    public Graphics2D getScreenGraphic2DForColor(Color color) {
        Graphics2D g = screenGraphicCache.get(color);
        if (g == null) {
            g = createScreenGraphics();
            if (PreferencesManager.getPreferences().getAntiAliasing()) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            screenGraphicCache.put(color, g);
        }
        g.setColor(color);
        return g;
    }

    public Graphics2D createScreenGraphics() {
        Graphics2D g = (Graphics2D) graphics.create();
        if (referenceFrame.isInverted()) {
            g.scale(-1, 1);
            g.translate(-trackRectangle.width, 0);
        }
        return g;
    }

    public String getChr() {
        return referenceFrame.getChrName();
    }

    public double getOrigin() {
        return originOverride == null ? referenceFrame.getOrigin() : originOverride;
    }

    public double getEndLocation() {
        return endOverride == null ? referenceFrame.getEnd() : endOverride;
    }

    public double getScale() {
        return scaleOverride == null ? referenceFrame.getScale() : scaleOverride;
    }

    public Rectangle getVisibleRect() {
        return visibleRect;
    }

    public Rectangle getTrackRectangle() {
        return trackRectangle;
    }

    public Rectangle getClipBounds() {
        return clipBounds;
    }

    public JComponent getPanel() {
        return panel;
    }

    public int getZoom() {
        return referenceFrame.getZoom();
    }

    public ReferenceFrame getReferenceFrame() {
        return referenceFrame;
    }

    public void setRegionOverride(TrackRegionOverride regionOverride) {
        this.regionOverride = regionOverride;
        this.regionalDataRange = null;
    }

    public TrackRegionOverride getRegionOverride() {
        return regionOverride;
    }

    public boolean isRegionalPass() {
        return regionalPass;
    }

    public void setRegionalPass(boolean regionalPass) {
        this.regionalPass = regionalPass;
    }

    public void setViewTransform(double origin, double end, double scale) {
        this.originOverride = origin;
        this.endOverride = end;
        this.scaleOverride = scale;
    }

    public void setDisplayBinPlan(DisplayBinPlan displayBinPlan) {
        this.displayBinPlan = displayBinPlan;
    }

    public DisplayBinPlan getDisplayBinPlan() {
        return displayBinPlan;
    }

    /** Logical screen bounds in which a complete annotation label may be placed. */
    public Rectangle getLabelClipBounds() {
        return labelClipBounds == null ? trackRectangle : labelClipBounds;
    }

    public void setLabelClipBounds(Rectangle labelClipBounds) {
        this.labelClipBounds = labelClipBounds == null ? null : new Rectangle(labelClipBounds);
    }

    public Color getPositiveColor(Track track) {
        return regionOverride != null && regionOverride.getPositiveColor() != null
                ? regionOverride.getPositiveColor() : track.getColor();
    }

    public Color getNegativeColor(Track track) {
        return regionOverride != null && regionOverride.getNegativeColor() != null
                ? regionOverride.getNegativeColor() : track.getAltColor();
    }

    public DataRange getDataRange(Track track) {
        if (regionOverride == null || regionOverride.getYAxisMode() == TrackRegionOverride.YAxisMode.DEFAULT) {
            return track.getDataRange();
        }
        if (regionalDataRange == null) {
            DataRange base = track.getDataRange();
            if (base == null) return null;
            if (regionOverride.getYAxisMode() == TrackRegionOverride.YAxisMode.FLIP) {
                regionalDataRange = base.flipped();
            } else if (regionOverride.getRangeMinimum() != null
                    && regionOverride.getRangeBaseline() != null
                    && regionOverride.getRangeMaximum() != null) {
                regionalDataRange = new DataRange(
                        regionOverride.getRangeMinimum(),
                        regionOverride.getRangeBaseline(),
                        regionOverride.getRangeMaximum(),
                        base.isDrawBaseline(),
                        Boolean.TRUE.equals(regionOverride.getLogScale()));
                regionalDataRange.setMidlineColor(base.getMidlineColor());
            } else {
                regionalDataRange = base;
            }
        }
        return regionalDataRange;
    }

    public void dispose() {
        // Note: don't dispose of "this.graphics", it is managed by the framwork.
        for (Graphics2D g : graphicCache.values()) {
            g.dispose();
        }
        graphicCache.clear();
        for (Graphics2D g : screenGraphicCache.values()) {
            g.dispose();
        }
        screenGraphicCache.clear();
    }

}
