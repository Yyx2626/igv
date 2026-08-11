/*
 * TrackPanel.java
 *
 * Created on Sep 5, 2007, 4:09:39 PM
 *
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.igv.ui.panel;

import com.google.common.base.Objects;
import org.igv.Globals;
import org.igv.event.DataLoadedEvent;
import org.igv.event.IGVEvent;
import org.igv.event.IGVEventObserver;
import org.igv.feature.RegionOfInterest;
import org.igv.feature.RegionDisplayRule;
import org.igv.feature.TrackRegionOverride;
import org.igv.logging.LogManager;
import org.igv.logging.Logger;
import org.igv.prefs.Constants;
import org.igv.prefs.PreferencesManager;
import org.igv.renderer.FeatureLabelCollector;
import org.igv.track.RenderContext;
import org.igv.track.Track;
import org.igv.track.TrackClickEvent;
import org.igv.track.TrackPairing;
import org.igv.track.DisplayBinPlan;
import org.igv.track.RegionDisplayBinPlanner;
import org.igv.ui.AbstractDataPanelTool;
import org.igv.ui.IGV;
import org.igv.ui.UIConstants;
import org.igv.ui.WaitCursorManager;
import org.igv.ui.util.DataPanelTool;
import org.igv.ui.util.MessageUtils;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TimerTask;

/**
 * The batch panel for displaying tracks and data.  A DataPanel is always associated with a ReferenceFrame.  Normally
 * there is a single reference frame (and thus panel), but when "gene list" or other split screen views are
 * invoked there can be multiple panels.
 *
 * @author jrobinso
 */
public class DataPanel extends JComponent implements Paintable, IGVEventObserver {

    private static Logger log = LogManager.getLogger(DataPanel.class);
    private final boolean darkMode;

    private DataPanelTool defaultTool;
    private DataPanelTool currentTool;
    private ReferenceFrame frame;
    private DataPanelContainer parent;
    private DataPanelPainter painter;
    private String tooltipText = "";

    public DataPanel(ReferenceFrame frame, DataPanelContainer parent) {

        init();
        this.darkMode = Globals.isDarkMode();
        this.defaultTool = new PanTool(this);
        this.currentTool = defaultTool;
        this.frame = frame;
        this.parent = parent;
        setFocusable(true);
        setAutoscrolls(true);
        setOpaque(true);
        setDoubleBuffered(true);
        setToolTipText("");
        painter = new DataPanelPainter();

        if (darkMode && !PreferencesManager.getPreferences().hasExplicitValue(Constants.TRACK_BACKGROUND_COLOR)) {
            setBackground(UIManager.getColor("Panel.background"));
        } else {
            setBackground(PreferencesManager.getPreferences().getAsColor(Constants.TRACK_BACKGROUND_COLOR));
        }

        ToolTipManager.sharedInstance().registerComponent(this);
    }

    @Override
    public void receiveEvent(IGVEvent event) {
        if (event instanceof DataLoadedEvent e) {
            if (e.referenceFrame() == frame) {
                log.debug("Data loaded repaint " + frame);
                repaint();
            }
        }
    }

    /**
     * @return
     */
    public JScrollBar getVerticalScrollbar() {
        Component sp = getParent();
        while (sp != null && !(sp instanceof JScrollPane)) {
            sp = sp.getParent();
        }
        return sp == null ? null : ((JScrollPane) sp).getVerticalScrollBar();
    }

    public void setCurrentTool(final AbstractDataPanelTool tool) {
        this.currentTool = (tool == null) ? defaultTool : tool;
        if (currentTool != null) {
            setCursor(currentTool.getCursor());
        }
    }


    @Override
    public void paintComponent(final Graphics g) {

        super.paintComponent(g);

        // Explicitly fill background - JComponent without UI delegate doesn't do this automatically.
        // Read Constants.TRACK_BACKGROUND_COLOR fresh (not the cached getBackground() from construction
        // time) so a Preferences change is reflected on the very next repaint, no restart needed -
        // mirrors TrackNamePanel's own live background-color read. A track's own
        // backgroundColorOverride (set via "Set Track Background Color...") always wins.
        Graphics2D graphics2D = (Graphics2D) g;
        Rectangle clip = graphics2D.getClipBounds();
        if (clip != null) {
            graphics2D.setColor(getEffectiveTrackBackground());
            graphics2D.fillRect(clip.x, clip.y, clip.width, clip.height);
        }
        final Rectangle visibleRect = getVisibleRect();
        int visibilityWindow = getTrack().getVisibilityWindow();
        double bpwidth = getBounds().width * frame.getScale();

        if ((!getTrack().supportsWholeGenome() && frame.getChrName().equals(Globals.CHR_ALL)) ||
                (visibilityWindow == 0 && frame.getChrName().equals(Globals.CHR_ALL)) ||
                (visibilityWindow > 0 && bpwidth > visibilityWindow)) {

            graphics2D.setColor(darkMode ? Color.white : Color.GRAY);
            String msg = frame.getChrName().equals(Globals.CHR_ALL) ?
                    "Select a chromosome and zoom in to see data." :
                    "Zoom in to see data, or right-click to increase Feature Visibility Window.";
            FontMetrics fm = graphics2D.getFontMetrics();
            int msgWidth = fm.stringWidth(msg);
            int msgHeight = fm.getHeight();
            int x = (getWidth() - msgWidth) / 2;
            int y = (visibleRect.height + msgHeight) / 2;
            graphics2D.drawString(msg, x, y);
            return;
        }

        RenderContext context = null;
        Graphics2D trackGraphics = null;
        try {

            final Rectangle trackRectangle = new Rectangle(getBounds());
            trackRectangle.x = 0;               // Adjust to be relative to the panel, not the parent
            trackRectangle.y = 0;
            final Rectangle clipBounds = g.getClipBounds();
            FeatureLabelCollector labelCollector = new FeatureLabelCollector();
            registerRegionalLabelTargets(labelCollector, trackRectangle);

            drawRegionFills(graphics2D, trackRectangle, false);

            RegionDisplayCoordinateMap coordinateMap = frame.getRegionDisplayCoordinateMap();
            if (coordinateMap.hasCollapsedIntervals()) {
                paintTrackWithDisplaySegments(getTrack(), null, graphics2D, trackRectangle,
                        visibleRect, clipBounds, null, null, coordinateMap, labelCollector);
            } else {
                trackGraphics = createTrackGraphics(graphics2D, getWidth());
                context = new RenderContext(this, trackGraphics, frame, trackRectangle, visibleRect, clipBounds);
                context.setLabelClipBounds(trackRectangle);
                context.setLabelCoordinateTransform(createLabelTransform(getWidth(), null));
                context.setFeatureLabelCollector(labelCollector, 0);
                painter.paint(getTrack(), context);
            }
            List<RegionalForeground> regionalForegrounds = paintTrackRegionOverrides(
                    graphics2D, trackRectangle, visibleRect, clipBounds, labelCollector);
            labelCollector.paint(graphics2D);
            paintRegionalForegrounds(graphics2D, regionalForegrounds);
            drawRegionFills(graphics2D, trackRectangle, true);

            // If there is a partial ROI in progress draw it first
            if (currentTool instanceof RegionOfInterestTool) {
                int startLoc = ((RegionOfInterestTool) currentTool).getRoiStart();
                if (startLoc > 0) {
                    int start = frame.getScreenPosition(startLoc);
                    g.setColor(Color.BLACK);
                    graphics2D.drawLine(start, 0, start, getHeight());
                }
            }

            drawAllRegions(g);

        } catch (Exception e) {
            MessageUtils.showMessage("Unexpected error repainting view.  See igv.log for details.");
            log.error("Error painting DataPanel", e);
        } finally {
            if (context != null) {
                context.dispose();
            }
            if (trackGraphics != null) {
                trackGraphics.dispose();
            }
        }
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Short.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        Insets insets = this.getInsets();
        frame.setBounds(x + insets.left, width - insets.left - insets.right);
    }

    /**
     * Paint method designed to paint to an offscreen image
     *
     * @param g
     * @param rect
     */

    public void paintOffscreen(final Graphics2D g, Rectangle rect, boolean batch) {

        RenderContext context = null;
        Graphics2D trackGraphics = null;


        try {
            g.setColor(getEffectiveTrackBackground());
            g.fillRect(rect.x, rect.y, rect.width, rect.height);
            FeatureLabelCollector labelCollector = new FeatureLabelCollector();
            registerRegionalLabelTargets(labelCollector, rect);
            drawRegionFills(g, rect, false);
            RegionDisplayCoordinateMap coordinateMap = frame.getRegionDisplayCoordinateMap();
            if (coordinateMap.hasCollapsedIntervals()) {
                paintTrackWithDisplaySegments(getTrack(), null, g, rect, rect, rect,
                        null, null, coordinateMap, labelCollector);
            } else {
                trackGraphics = createTrackGraphics(g, rect.width);
                context = new RenderContext(null, trackGraphics, frame, rect, rect, rect);
                Insets insets = getInsets();
                Rectangle contentRect = new Rectangle(
                        rect.x + insets.left,
                        rect.y + insets.top,
                        rect.width - (insets.left + insets.right),
                        rect.height - (insets.top + insets.bottom));
                context.getGraphics().setClip(contentRect);
                context.setLabelClipBounds(contentRect);
                context.setLabelCoordinateTransform(createLabelTransform(rect.width, null));
                context.setFeatureLabelCollector(labelCollector, 0);
                painter.paint(getTrack(), context);
            }
            List<RegionalForeground> regionalForegrounds = paintTrackRegionOverrides(
                    g, rect, rect, rect, labelCollector);
            labelCollector.paint(g);
            paintRegionalForegrounds(g, regionalForegrounds);
            drawRegionFills(g, rect, true);
            drawAllRegions(g);

        } finally {
            if (context != null) {
                context.dispose();
            }
            if (trackGraphics != null) {
                trackGraphics.dispose();
            }
        }
    }

    private Color getEffectiveTrackBackground() {
        return getEffectiveTrackBackground(getTrack());
    }

    private void registerRegionalLabelTargets(FeatureLabelCollector collector, Rectangle trackRect) {
        Track track = getTrack();
        if (track == null || track.getId() == null || !IGV.hasInstance()) return;
        Collection<RegionOfInterest> regions =
                IGV.getInstance().getSession().getRegionsOfInterest(frame.getChrName());
        if (regions == null) return;
        double viewportStart = frame.getOrigin();
        double viewportEnd = frame.getEnd();
        for (RegionOfInterest region : regions) {
            RegionDisplayRule rule = region.getDisplayRule();
            if (rule == null || rule.isCollapsed()
                    || region.getEnd() <= viewportStart || region.getStart() >= viewportEnd) continue;
            TrackRegionOverride override = rule.getTrackOverride(track.getId());
            if (override == null || !override.hasAnyEffect()) continue;
            int first = frame.getScreenPosition(Math.max(viewportStart, region.getStart()));
            int second = frame.getScreenPosition(Math.min(viewportEnd, region.getEnd()));
            collector.addRegionalTarget(new Rectangle(Math.min(first, second), trackRect.y,
                    Math.max(1, Math.abs(second - first) + 1), trackRect.height));
        }
    }

    private Color getEffectiveTrackBackground(Track track) {
        Color override = track == null ? null : track.getBackgroundColorOverride();
        return override != null ? override
                : darkMode && !PreferencesManager.getPreferences().hasExplicitValue(Constants.TRACK_BACKGROUND_COLOR)
                ? UIManager.getColor("Panel.background")
                : PreferencesManager.getPreferences().getAsColor(Constants.TRACK_BACKGROUND_COLOR);
    }

    private Graphics2D createTrackGraphics(Graphics2D source, int width) {
        Graphics2D result = (Graphics2D) source.create();
        if (frame.isInverted()) {
            result.translate(width, 0);
            result.scale(-1, 1);
        }
        return result;
    }

    private void drawRegionFills(Graphics2D source, Rectangle trackRect, boolean foreground) {
        Collection<RegionOfInterest> regions =
                IGV.getInstance().getSession().getRegionsOfInterest(frame.getChrName());
        if (regions == null || regions.isEmpty()) return;

        double viewportStart = frame.getOrigin();
        double viewportEnd = frame.getEnd();
        Graphics2D graphics = (Graphics2D) source.create();
        try {
            graphics.clip(trackRect);
            List<RegionOfInterest> paintOrder = new ArrayList<>(regions);
            paintOrder.sort(Comparator.comparingInt(RegionOfInterest::getLength).reversed());
            for (RegionOfInterest region : paintOrder) {
                RegionDisplayRule rule = region.getDisplayRule();
                if (rule == null || rule.isCollapsed()) continue;
                Color color = foreground ? rule.getRegionForegroundColor() : rule.getRegionBackgroundColor();
                if (color == null) continue;
                if (region.getEnd() <= viewportStart || region.getStart() >= viewportEnd) continue;
                int first = frame.getScreenPosition(Math.max(viewportStart, region.getStart()));
                int second = frame.getScreenPosition(Math.min(viewportEnd, region.getEnd()));
                int x = Math.min(first, second);
                // Include both boundary pixels so the region fill covers the ROI border area.
                int width = Math.max(1, Math.abs(second - first) + 1);
                graphics.setColor(color);
                graphics.fillRect(x, trackRect.y, width, trackRect.height);
            }
        } finally {
            graphics.dispose();
        }
    }

    private List<RegionalForeground> paintTrackRegionOverrides(
            Graphics2D source, Rectangle trackRect, Rectangle visibleRect,
            Rectangle clipBounds, FeatureLabelCollector labelCollector) {
        Track track = getTrack();
        if (track == null || track.getId() == null) return List.of();
        Collection<RegionOfInterest> allRegions =
                IGV.getInstance().getSession().getRegionsOfInterest(frame.getChrName());
        if (allRegions == null || allRegions.isEmpty()) return List.of();

        double viewportStart = frame.getOrigin();
        double viewportEnd = frame.getEnd();
        ArrayList<RegionOfInterest> regions = new ArrayList<>();
        for (RegionOfInterest region : allRegions) {
            RegionDisplayRule rule = region.getDisplayRule();
            if (rule == null || rule.isCollapsed()
                    || region.getEnd() <= viewportStart || region.getStart() >= viewportEnd) continue;
            TrackRegionOverride override = rule.getTrackOverride(track.getId());
            if (override != null && override.hasAnyEffect()) regions.add(region);
        }
        regions.sort(Comparator.comparingInt(region -> region.getDisplayRule().getPriority()));

        if (regions.isEmpty()) return List.of();

        List<RegionalForeground> foregrounds = new ArrayList<>();
        for (RegionalTrackSlice slice : createRegionalTrackSlices(
                regions, allRegions, track, viewportStart, viewportEnd)) {
            TrackRegionOverride override = slice.override();
            Track renderTrack = track;
            TrackRegionOverride renderOverride = override;
            if (override.exchangesTrackPair() && TrackPairing.isPaired(track)) {
                Track partner = TrackPairing.findPartner(track, IGV.getInstance().getAllTracks());
                if (partner != null) {
                    renderTrack = partner;
                }
            }
            int first = frame.getScreenPosition(slice.start());
            int second = frame.getScreenPosition(slice.end());
            Rectangle regionRect = new Rectangle(
                    Math.min(first, second), trackRect.y,
                    Math.max(1, Math.abs(second - first) + 1), trackRect.height);
            labelCollector.addRegionalTarget(regionRect);

            Graphics2D background = (Graphics2D) source.create();
            try {
                background.clip(regionRect);
                background.setComposite(AlphaComposite.Src);
                background.setColor(getEffectiveTrackBackground(renderTrack));
                background.fill(regionRect);
                background.setComposite(AlphaComposite.SrcOver);
                if (renderOverride.getBackgroundColor() != null) {
                    background.setColor(renderOverride.getBackgroundColor());
                    background.fill(regionRect);
                } else if (slice.highlightColor() != null) {
                    background.setColor(slice.highlightColor());
                    background.fill(regionRect);
                }
            } finally {
                background.dispose();
            }

            RegionDisplayCoordinateMap coordinateMap = frame.getRegionDisplayCoordinateMap();
            if (coordinateMap.hasCollapsedIntervals()) {
                paintTrackWithDisplaySegments(renderTrack, renderOverride, source, trackRect,
                        visibleRect, clipBounds, slice.start(), slice.end(), coordinateMap,
                        labelCollector);
            } else if (renderOverride.isReverseX() && slice.inversionSum() != null) {
                paintInvertedRegionalSlice(renderTrack, renderOverride, source, trackRect,
                        visibleRect, regionRect, slice.start(), slice.end(), slice.inversionSum(),
                        labelCollector);
            } else {
                Graphics2D clippedSource = (Graphics2D) source.create();
                Graphics2D regionalTrackGraphics = null;
                RenderContext regionalContext = null;
                try {
                    clippedSource.clip(regionRect);
                    regionalTrackGraphics = createTrackGraphics(clippedSource, trackRect.width);
                    regionalContext = new RenderContext(this, regionalTrackGraphics, frame,
                            trackRect, visibleRect, regionRect);
                    regionalContext.setLabelClipBounds(regionRect);
                    regionalContext.setLabelCoordinateTransform(createLabelTransform(trackRect.width, null));
                    regionalContext.setFeatureLabelCollector(labelCollector, 0);
                    regionalContext.setRegionOverride(renderOverride);
                    regionalContext.setRegionalPass(true);
                    painter.paint(renderTrack, regionalContext);
                } finally {
                    if (regionalContext != null) regionalContext.dispose();
                    if (regionalTrackGraphics != null) regionalTrackGraphics.dispose();
                    clippedSource.dispose();
                }
            }

            if (renderOverride.getForegroundMaskColor() != null) {
                foregrounds.add(new RegionalForeground(
                        new Rectangle(regionRect), renderOverride.getForegroundMaskColor()));
            }
        }
        return foregrounds;
    }

    private void paintRegionalForegrounds(Graphics2D source,
                                           List<RegionalForeground> foregrounds) {
        for (RegionalForeground regionalForeground : foregrounds) {
            Graphics2D foreground = (Graphics2D) source.create();
            try {
                foreground.clip(regionalForeground.rectangle());
                foreground.setColor(regionalForeground.color());
                foreground.fill(regionalForeground.rectangle());
            } finally {
                foreground.dispose();
            }
        }
    }

    private record RegionalForeground(Rectangle rectangle, Color color) {
    }

    /**
     * Partition overlapping rules into non-overlapping slices. Coordinate inversion and Y flip
     * compose by XOR, so a nested second inversion restores the source orientation.
     */
    private List<RegionalTrackSlice> createRegionalTrackSlices(List<RegionOfInterest> regions,
                                                               Collection<RegionOfInterest> allRegions,
                                                               Track track,
                                                               double viewportStart, double viewportEnd) {
        List<Double> boundaries = new ArrayList<>();
        for (RegionOfInterest region : regions) {
            boundaries.add(Math.max(viewportStart, (double) region.getStart()));
            boundaries.add(Math.min(viewportEnd, (double) region.getEnd()));
        }
        boundaries = boundaries.stream().distinct().sorted().toList();
        List<RegionOfInterest> displayOrder = allRegions.stream()
                .filter(region -> region.getDisplayRule() != null)
                .sorted(Comparator.comparingInt(region -> region.getDisplayRule().getPriority()))
                .toList();
        List<RegionalTrackSlice> result = new ArrayList<>();
        for (int i = 0; i + 1 < boundaries.size(); i++) {
            double start = boundaries.get(i);
            double end = boundaries.get(i + 1);
            if (end <= start) continue;
            double midpoint = start + (end - start) / 2.0;
            List<RegionOfInterest> covering = regions.stream()
                    .filter(region -> region.getStart() <= midpoint && region.getEnd() > midpoint)
                    .toList();
            if (covering.isEmpty()) continue;
            TrackRegionOverride effective = composeOverrides(covering, track);
            Double inversionSum = effective.isReverseX()
                    ? composeInversionSum(covering, track) : null;
            Color highlight = null;
            for (RegionOfInterest region : displayOrder) {
                RegionDisplayRule rule = region.getDisplayRule();
                if (!rule.isCollapsed() && region.getStart() <= midpoint && region.getEnd() > midpoint
                        && rule.getRegionBackgroundColor() != null) {
                    highlight = rule.getRegionBackgroundColor();
                }
            }
            result.add(new RegionalTrackSlice(start, end, effective, highlight, inversionSum));
        }
        return result;
    }

    /**
     * Compose fixed genomic reflection axes in display priority order.  For one ROI this is
     * simply {@code roiStart + roiEnd}; importantly it never depends on the visible viewport.
     */
    private Double composeInversionSum(List<RegionOfInterest> regions, Track track) {
        boolean reversed = false;
        double offset = 0;
        for (RegionOfInterest region : regions) {
            TrackRegionOverride override = region.getDisplayRule().getTrackOverride(track.getId());
            if (override != null && override.isReverseX()) {
                reversed = !reversed;
                offset = region.getStart() + (double) region.getEnd() - offset;
            }
        }
        return reversed ? offset : null;
    }

    private TrackRegionOverride composeOverrides(List<RegionOfInterest> regions, Track track) {
        List<TrackRegionOverride> overrides = new ArrayList<>();
        for (RegionOfInterest region : regions) {
            overrides.add(region.getDisplayRule().getTrackOverride(track.getId()));
        }
        return TrackRegionOverride.compose(overrides);
    }

    private record RegionalTrackSlice(double start, double end, TrackRegionOverride override,
                                      Color highlightColor, Double inversionSum) {
    }

    static double[] invertedSourceInterval(double targetStart, double targetEnd,
                                           double inversionSum) {
        return new double[]{inversionSum - targetEnd, inversionSum - targetStart};
    }

    private void paintInvertedRegionalSlice(Track track, TrackRegionOverride override,
                                            Graphics2D source, Rectangle trackRect,
                                            Rectangle visibleRect, Rectangle targetRect,
                                            double targetStart, double targetEnd,
                                            double inversionSum,
                                            FeatureLabelCollector labelCollector) {
        double[] sourceInterval = invertedSourceInterval(targetStart, targetEnd, inversionSum);
        double sourceStart = sourceInterval[0];
        double sourceEnd = sourceInterval[1];
        if (sourceEnd <= sourceStart) return;

        Graphics2D sliceSource = (Graphics2D) source.create();
        Graphics2D sliceTrackGraphics = null;
        RenderContext sliceContext = null;
        try {
            sliceSource.clip(targetRect);
            sliceSource.translate(targetRect.x, 0);
            sliceSource.translate(targetRect.width, 0);
            sliceSource.scale(-1, 1);
            sliceTrackGraphics = createTrackGraphics(sliceSource, targetRect.width);

            Rectangle localTrackRect = new Rectangle(
                    0, trackRect.y, targetRect.width, trackRect.height);
            Rectangle localVisibleRect = new Rectangle(
                    0, visibleRect.y, targetRect.width, visibleRect.height);
            sliceContext = new RenderContext(this, sliceTrackGraphics, frame,
                    localTrackRect, localVisibleRect, localTrackRect);
            sliceContext.setLabelClipBounds(localTrackRect);
            sliceContext.setLabelCoordinateTransform(createLabelTransform(targetRect.width,
                    new Rectangle(0, trackRect.y, targetRect.width, trackRect.height)));
            sliceContext.setFeatureLabelCollector(labelCollector, targetRect.x);
            sliceContext.setViewTransform(sourceStart, sourceEnd,
                    (sourceEnd - sourceStart) / Math.max(1, targetRect.width));

            int requestedBins = Math.max(1,
                    PreferencesManager.getPreferences().getAsInt(Constants.SCREENSHOT_DATA_BINS));
            int sliceBins = Math.max(1, (int) Math.ceil(
                    requestedBins * targetRect.width / (double) Math.max(1, trackRect.width)));
            sliceContext.setDisplayBinPlan(RegionDisplayBinPlanner.create(frame.getChrName(),
                    Math.max(0, (int) Math.floor(sourceStart)),
                    Math.max(1, (int) Math.ceil(sourceEnd)), sliceBins));
            sliceContext.setRegionOverride(override);
            sliceContext.setRegionalPass(true);
            painter.paint(track, sliceContext);
        } finally {
            if (sliceContext != null) sliceContext.dispose();
            if (sliceTrackGraphics != null) sliceTrackGraphics.dispose();
            sliceSource.dispose();
        }
    }

    private void paintTrackWithDisplaySegments(Track track, TrackRegionOverride override,
                                               Graphics2D source, Rectangle trackRect,
                                               Rectangle visibleRect, Rectangle clipBounds,
                                               Double limitingStart, Double limitingEnd,
                                               RegionDisplayCoordinateMap coordinateMap,
                                               FeatureLabelCollector labelCollector) {
        int requestedBins = Math.max(1,
                PreferencesManager.getPreferences().getAsInt(Constants.SCREENSHOT_DATA_BINS));

        for (RegionDisplayCoordinateMap.Segment segment : coordinateMap.getSegments()) {
            double genomicStart = segment.genomicStart();
            double genomicEnd = segment.genomicEnd();
            if (limitingStart != null && limitingEnd != null) {
                genomicStart = Math.max(genomicStart, limitingStart);
                genomicEnd = Math.min(genomicEnd, limitingEnd);
                if (genomicEnd <= genomicStart) continue;
            }
            int first = coordinateMap.getScreenPosition(genomicStart);
            int second = coordinateMap.getScreenPosition(genomicEnd);
            Rectangle screenRect = new Rectangle(Math.min(first, second), trackRect.y,
                    Math.max(1, Math.abs(second - first)), trackRect.height);

            Graphics2D segmentSource = (Graphics2D) source.create();
            Graphics2D segmentTrackGraphics = null;
            RenderContext segmentContext = null;
            try {
                segmentSource.clip(screenRect);
                segmentSource.translate(screenRect.x, 0);
                if (override != null && override.isReverseX()) {
                    segmentSource.translate(screenRect.width, 0);
                    segmentSource.scale(-1, 1);
                }
                segmentTrackGraphics = createTrackGraphics(segmentSource, screenRect.width);
                Rectangle localTrackRect = new Rectangle(0, trackRect.y, screenRect.width, trackRect.height);
                Rectangle localVisibleRect = new Rectangle(0, visibleRect.y, screenRect.width, visibleRect.height);
                segmentContext = new RenderContext(this, segmentTrackGraphics, frame,
                        localTrackRect, localVisibleRect, localTrackRect);
                segmentContext.setLabelClipBounds(localTrackRect);
                segmentContext.setLabelCoordinateTransform(
                        createLabelTransform(screenRect.width,
                                override != null && override.isReverseX()
                                        ? new Rectangle(0, trackRect.y, screenRect.width, trackRect.height)
                                        : null));
                segmentContext.setFeatureLabelCollector(labelCollector, screenRect.x);
                segmentContext.setViewTransform(genomicStart, genomicEnd, coordinateMap.getDisplayScale());
                int segmentBins = Math.max(1, (int) Math.ceil(
                        requestedBins * screenRect.width / (double) Math.max(1, trackRect.width)));
                segmentContext.setDisplayBinPlan(RegionDisplayBinPlanner.create(frame.getChrName(),
                        Math.max(0, (int) Math.floor(genomicStart)),
                        Math.max(1, (int) Math.ceil(genomicEnd)), segmentBins));
                segmentContext.setRegionOverride(override);
                segmentContext.setRegionalPass(override != null || screenRect.x != 0);
                painter.paint(track, segmentContext);
            } finally {
                if (segmentContext != null) segmentContext.dispose();
                if (segmentTrackGraphics != null) segmentTrackGraphics.dispose();
                segmentSource.dispose();
            }
        }
    }

    @Override
    public int getSnapshotHeight(boolean batch) {
        return getHeight();
    }


    /**
     * Draw vertical lines demarcating regions of interest.
     */
    public void drawAllRegions(final Graphics g) {

        // TODO -- get rid of this ugly reference to IGV
        Collection<RegionOfInterest> regions =
                IGV.getInstance().getSession().getRegionsOfInterest(frame.getChrName());

        if ((regions == null) || regions.isEmpty()) {
            return;
        }

        boolean drawBars = PreferencesManager.getPreferences().getAsBoolean(Constants.SHOW_REGION_BARS);
        Graphics2D graphics2D = (Graphics2D) g.create();
        try {

            for (RegionOfInterest regionOfInterest : regions) {
                if (drawBars || regionOfInterest == RegionOfInterestPanel.getSelectedRegion()) {
                    drawRegion(graphics2D, regionOfInterest);
                }
            }
        } finally {
            if (graphics2D != null) {
                graphics2D.dispose();
            }
        }
    }

    private boolean drawRegion(Graphics2D graphics2D, RegionOfInterest regionOfInterest) {
        Integer regionStart = regionOfInterest.getStart();
        if (regionStart == null) {
            return true;
        }

        Integer regionEnd = regionOfInterest.getEnd();
        if (regionEnd == null) {
            regionEnd = regionStart;
        }
        ReferenceFrame referenceFrame = frame;
        int start = referenceFrame.getScreenPosition(regionStart);
        int end = referenceFrame.getScreenPosition(regionEnd);

        // Set foreground color of boundaries
        int height = getHeight();
        graphics2D.setColor(regionOfInterest == RegionOfInterestPanel.getSelectedRegion()
                ? Color.BLACK : regionOfInterest.getForegroundColor());
        graphics2D.drawLine(start, 0, start, height);
        graphics2D.drawLine(end, 0, end, height);
        return false;
    }

    /** Mapping from renderer pixel coordinates to the untransformed label clip coordinates. */
    private AffineTransform createLabelTransform(int width, Rectangle regionalMirror) {
        AffineTransform transform = new AffineTransform();
        if (regionalMirror != null) {
            transform.translate(2.0 * regionalMirror.x + regionalMirror.width, 0);
            transform.scale(-1, 1);
        }
        if (frame.isInverted()) {
            transform.translate(width, 0);
            transform.scale(-1, 1);
        }
        return transform;
    }


    /**
     * Do not remove - Used for debugging only
     *
     * @param trackName
     */
    public void debugDump(String trackName) {

        // Get the view that holds the track name, attribute and data panels
        TrackPanel trackView = (TrackPanel) getParent();

        if (trackView == null) {
            return;
        }


        if (trackView.hasTracks()) {
            String name = parent.getTrackSetID().toString();
            System.out.println(
                    "\n\n" + name + " Track COUNT:" + trackView.getTracks().size());
            System.out.println(
                    "\t\t\t\t" + name + " scrollpane height     = " + trackView.getScrollPane().getHeight());
            System.out.println(
                    "\t\t\t\t" + name + " viewport height       = " + trackView.getViewportHeight());
            System.out.println(
                    "\t\t\t\t" + name + " TrackView min height  = " + trackView.getMinimumSize().getHeight());
            System.out.println(
                    "\t\t\t\t" + name + " TrackView pref height = " + trackView.getPreferredSize().getHeight());
            System.out.println(
                    "\t\t\t\t" + name + " TrackView height      = " + trackView.getSize().getHeight());
        }

    }

    /**
     * Return html formatted text for mouse position (pixels).
     * TODO  this will be a lot easier when each track has its own panel.
     */
    static DecimalFormat locationFormatter = new DecimalFormat();


    public Track getTrack() {
        return parent.getTrack();
    }


    @Override
    public void setToolTipText(String text) {
        if (!Objects.equal(tooltipText, text)) {
            this.tooltipText = text;
            putClientProperty(TOOL_TIP_TEXT_KEY, text);
        }

    }

    /**
     * {@inheritDoc}
     * <p/>
     * The tooltip text may be null, in which case no tooltip is displayed
     */
    @Override
    final public String getToolTipText() {
        //TODO Suppress tooltips instead. This is hard to get exactly right
        //TODO with our different tooltip settings
        if (currentTool instanceof RegionOfInterestTool) {
            return null;
        }
        return tooltipText;
    }


    /**
     * Update tooltip text for the current mouse position (x, y)
     *
     * @param x Mouse x position in pixels
     * @param y Mouse y position in pixels
     */
    public void updateTooltipText(int x, int y, Track track) {

        //Tooltip here specifically means text that is shown on hover
        //We disable it unless that option is specified
        if (!IGV.getInstance().isShowDetailsOnHover()) {
            setToolTipText(null);
            return;
        }

        double position = getTrackSourcePosition(x, track);

        List<MouseableRegion> regions = parent.getMouseRegions();
        StringBuffer popupTextBuffer = new StringBuffer();
        popupTextBuffer.append("<html>");

        if (track != null) {
            String valueString = track.getValueStringAt(frame.getChrName(), position, x, y, frame);
            if (valueString != null) {
                popupTextBuffer.append(valueString);
                popupTextBuffer.append("<br>");

            }
        }

        if (popupTextBuffer.length() > 6) {   // 6 characters for <html>
            //popupTextBuffer.append("<br>--------------------------");
            //popupTextBuffer.append(positionString);
            String puText = popupTextBuffer.toString().trim();
            if (!puText.equals(tooltipText)) {
                setToolTipText(puText);
            }
        } else {
            setToolTipText(null);
        }
    }


    private void init() {

        setRequestFocusEnabled(false);

        // Key Events
        KeyAdapter keyAdapter = new KeyAdapter() {

            @Override
            public void keyPressed(KeyEvent e) {
                int shiftOriginPixels = Integer.MIN_VALUE;
                int zoomIncr = Integer.MIN_VALUE;
                boolean showWaitCursor = false;

                if (e.getKeyChar() == '+' || e.getKeyCode() == KeyEvent.VK_PLUS) {
                    zoomIncr = +1;
                    showWaitCursor = true;
                } else if (e.getKeyChar() == '-' || e.getKeyCode() == KeyEvent.VK_PLUS) {
                    zoomIncr = -1;
                    showWaitCursor = true;
                } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    shiftOriginPixels = 50;
                } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    shiftOriginPixels = -50;
                } else if (e.getKeyCode() == KeyEvent.VK_HOME) {
                    shiftOriginPixels = -getWidth();
                    showWaitCursor = true;
                } else if (e.getKeyCode() == KeyEvent.VK_END) {
                    shiftOriginPixels = getWidth();
                    showWaitCursor = true;
                } else if (e.getKeyCode() == KeyEvent.VK_PLUS) {
                } else if (e.getKeyCode() == KeyEvent.VK_MINUS) {
                }

                WaitCursorManager.CursorToken token = null;
                if (showWaitCursor) token = WaitCursorManager.showWaitCursor();
                try {
                    if (zoomIncr > Integer.MIN_VALUE) {
                        frame.doZoomIncrement(zoomIncr);
                    } else if (shiftOriginPixels > Integer.MIN_VALUE) {
                        frame.shiftOriginPixels(frame.isInverted() ? -shiftOriginPixels : shiftOriginPixels);
                    } else {
                        return;
                    }

                    //Assume that anything special enough to warrant a wait cursor
                    //should be in history
                    if (showWaitCursor) {
                        frame.recordHistory();
                    }
                } finally {
                    if (token != null) WaitCursorManager.removeWaitCursor(token);
                }

            }
        };
        addKeyListener(keyAdapter);


        // Mouse Events
        MouseInputAdapter mouseAdapter = new DataPanelMouseAdapter();

        addMouseMotionListener(mouseAdapter);
        addMouseListener(mouseAdapter);
        addMouseWheelListener(mouseAdapter);
    }

    protected void removeMousableRegions() {
        parent.getMouseRegions().clear();
    }

    public ReferenceFrame getFrame() {
        return frame;
    }


    /**
     * Receives all mouse events for a data panel.  Handling of some events are delegated to the current tool or track.
     */
    class DataPanelMouseAdapter extends MouseInputAdapter {

        /**
         * A scheduler is used to distinguish a click from a double click.
         */
        private ClickTaskScheduler clickScheduler = new ClickTaskScheduler();

        long lastClickTime = 0;
        MouseEvent mouseDown = null;


        @Override
        public void mouseMoved(MouseEvent e) {
            String position = null;
            if (!frame.getChrName().equals(Globals.CHR_ALL)) {
                int location = (int) frame.getChromosomePosition(e) + 1;
                position = frame.getChrName() + ":" + locationFormatter.format(location);
                IGV.getInstance().setStatusBarMessag2(position);
            }
            updateTooltipText(e.getX(), e.getY(), getTrack());

            if (IGV.getInstance().isRulerEnabled()) {
                IGV.getInstance().repaint();
            }

        }

        /**
         * The mouse has been pressed.  If this is the platform's popup trigger select the track and popup a menu.
         * Otherwise delegate handling to the current tool.
         */
        @Override
        public void mousePressed(final MouseEvent e) {

            if (SwingUtilities.getWindowAncestor(DataPanel.this).isActive()) {
                DataPanel.this.requestFocus();
            }
            if (e.isPopupTrigger()) {
                doPopupMenu(e);
                e.consume();
            } else {
                if (currentTool != null)
                    currentTool.mousePressed(e);
                mouseDown = e;
            }
        }

        /**
         * The mouse has been released.  If this is the platform's popup trigger select the track and popup a menu.
         * Otherwise delegate handling to the current tool.
         */
        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                doPopupMenu(e);
                e.consume();
            } else {
                if (mouseDown != null && distance(mouseDown, e) < 5) {
                    doMouseClick(e);
                } else if (currentTool != null)
                    currentTool.mouseReleased(e);
            }
            mouseDown = null;
        }

        private void doPopupMenu(MouseEvent e) {
            TrackClickEvent te = new TrackClickEvent(e, frame, getTrackSourcePosition(e.getX(), getTrack()));
            parent.openPopupMenu(te);
        }

        private double distance(MouseEvent e1, MouseEvent e2) {
            double dx = e1.getX() - e2.getX();
            double dy = e1.getY() - e2.getY();
            return Math.sqrt(dx * dx + dy * dy);
        }


        /**
         * The mouse has been dragged.  Delegate to current tool.
         *
         * @param e
         */
        @Override
        public void mouseDragged(MouseEvent e) {
            if (mouseDown != null && currentTool != null)
                currentTool.mouseDragged(e);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            mouseDown = null;
        }

        @Override
        public void mouseExited(MouseEvent e) {
            mouseDown = null;
        }

        /**
         * Zoom in/out when modifier + scroll wheel used
         *
         * @param e
         */
        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            //we use either ctrl or meta to deal with PCs and Macs
            if (e.isControlDown() || e.isMetaDown()) {
                int wheelRotation = e.getWheelRotation();
                //Mouse move up is negative, that should zoom in
                int zoomIncr = -wheelRotation / 2;
                getFrame().doZoomIncrement(zoomIncr);
            }
            //TODO Use this to pan. Seems weird, but it's how side scrolling on my mouse gets interpreted,
            //so could be handy for people with 2D wheels
//            else if(e.isShiftDown()){
//                System.out.println(e);
//            }
            else {
                //Default action if no modifier
                e.getComponent().getParent().dispatchEvent(e);
            }
        }


        /**
         * The mouse was clicked. If this is the second click of a double click, cancel the scheduled single click task.
         * The shift and alt keys are alternative  zoom options
         * shift zooms in by 8x,  alt zooms out by 2x
         * <p>
         * NOTE: mouseClick is not used because in Java a mouseClick event is emitted only if the mouse has not
         * moved at all between press and release.  This is difficult to do, even when trying.
         * <p>
         * <p/>
         * TODO -- the "currentTool" is also a mouselistener, so there are two.  This makes mouse event handling
         * TODO -- needlessly complicated, which handler has preference, etc.  Move this code to the default
         * TODO -- PanAndZoomTool
         *
         * @param e
         */

        public void doMouseClick(final MouseEvent e) {

            long clickTime = System.currentTimeMillis();

            if (e.isPopupTrigger()) {
                return;
            }

            if (currentTool instanceof RegionOfInterestTool) {
                currentTool.mouseClicked(e);
                e.consume();
                return;
            }

            if (e.isPopupTrigger()) {
                doPopupMenu(e);
                e.consume();
                return;
            }

            Object source = e.getSource();
            if (source instanceof DataPanel && e.getButton() == MouseEvent.BUTTON1) {
                final Track track = ((DataPanel) e.getSource()).getTrack();

                if (e.isShiftDown()) {
                    final double locationClicked = frame.getChromosomePosition(e);
                    frame.doIncrementZoom(3, locationClicked);
                    e.consume();
                } else if (e.isAltDown()) {
                    final double locationClicked = frame.getChromosomePosition(e);
                    frame.doIncrementZoom(-1, locationClicked);
                    e.consume();
                } else if ((e.isMetaDown() || e.isControlDown()) && track != null) {
                    TrackClickEvent te = new TrackClickEvent(e, frame, getTrackSourcePosition(e.getX(), track));
                    if (track.handleDataClick(te)) {
                        e.consume();
                    }

                } else {

                    // No modifier, left-click.  Defer processing with a timer until we are sure this is not the
                    // first of a "double-click".

                    if (clickTime - lastClickTime < UIConstants.getDoubleClickInterval()) {
                        clickScheduler.cancelClickTask();
                        final double locationClicked = frame.getChromosomePosition(e);
                        frame.doIncrementZoom(1, locationClicked);

                    } else {

                        lastClickTime = clickTime;

                        // Unhandled single click.  Delegate to track or tool unless second click arrives within
                        // double-click interval.
                        TimerTask clickTask = new TimerTask() {
                            @Override
                            public void run() {
                                Object source = e.getSource();
                                if (source instanceof DataPanel) {

                                    if (track != null) {
                                        TrackClickEvent te = new TrackClickEvent(e, frame, getTrackSourcePosition(e.getX(), track));

                                        boolean handled = track.handleDataClick(te);

                                        if (!handled && currentTool != null)
                                            currentTool.mouseClicked(e);
                                    }
                                }
                            }
                        };
                        clickScheduler.scheduleClickTask(clickTask);
                    }

                }
            }
        }

    }

    double getTrackSourcePosition(int screenX, Track track) {
        double position = frame.getChromosomePosition(screenX);
        if (track == null || track.getId() == null) return position;
        Collection<RegionOfInterest> regions =
                IGV.getInstance().getSession().getRegionsOfInterest(frame.getChrName());
        if (regions == null || regions.isEmpty()) return position;

        RegionOfInterest selected = null;
        int selectedPriority = Integer.MIN_VALUE;
        for (RegionOfInterest region : regions) {
            if (position < region.getStart() || position >= region.getEnd()) continue;
            RegionDisplayRule rule = region.getDisplayRule();
            TrackRegionOverride override = rule == null ? null : rule.getTrackOverride(track.getId());
            if (override != null && override.isReverseX() && rule.getPriority() >= selectedPriority) {
                selected = region;
                selectedPriority = rule.getPriority();
            }
        }
        if (selected == null) return position;
        double visibleStart = Math.max(frame.getOrigin(), selected.getStart());
        double visibleEnd = Math.min(frame.getEnd(), selected.getEnd());
        RegionDisplayCoordinateMap coordinateMap = frame.getRegionDisplayCoordinateMap();
        for (RegionDisplayCoordinateMap.Segment segment : coordinateMap.getSegments()) {
            if (position >= segment.genomicStart() && position <= segment.genomicEnd()) {
                visibleStart = Math.max(visibleStart, segment.genomicStart());
                visibleEnd = Math.min(visibleEnd, segment.genomicEnd());
                break;
            }
        }
        double reversed = visibleStart + visibleEnd - position;
        return Math.max(visibleStart, Math.min(Math.nextDown(visibleEnd), reversed));
    }

    /**
     * A utility class for schedueling single-click actions "in the future",
     *
     * @author jrobinso
     * @date Dec 17, 2010
     */
    public class PopupTextUpdater {

        private TimerTask currentClickTask;

        public void cancelClickTask() {
            if (currentClickTask != null) {
                currentClickTask.cancel();
                currentClickTask = null;
            }
        }

        public void scheduleUpdateTask(TimerTask task) {
            cancelClickTask();
            currentClickTask = task;
            (new java.util.Timer()).schedule(currentClickTask, UIConstants.getDoubleClickInterval());
        }
    }
}
