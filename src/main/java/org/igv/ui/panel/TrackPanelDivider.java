package org.igv.ui.panel;

import org.igv.Globals;
import org.igv.event.IGVEvent;
import org.igv.event.IGVEventBus;
import org.igv.event.IGVEventObserver;
import org.igv.feature.RegionDisplayRule;
import org.igv.feature.RegionOfInterest;
import org.igv.prefs.Constants;
import org.igv.prefs.IGVPreferences;
import org.igv.prefs.PreferencesChangeEvent;
import org.igv.prefs.PreferencesManager;
import org.igv.track.Track;
import org.igv.track.TrackMenuUtils;
import org.igv.ui.IGV;
import org.igv.ui.util.UIUtilities;
import org.igv.ui.undo.TrackStateEdit;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * A thin draggable divider placed below a TrackPanelScrollPane. Dragging works
 * like Google Sheets row resizing:
 * <ul>
 *   <li><b>Drag down</b> – increases the height of the track above; everything
 *       below simply shifts down.</li>
 *   <li><b>Drag up</b> – decreases the height of the track above (down to
 *       {@link Track#getMinimumHeight()}); everything below simply shifts up.</li>
 * </ul>
 * Only the track above the divider is resized; tracks below are never modified.
 * Height changes are persisted via {@link Track#setHeight(int)}.
 * <p>
 * If the immediate pane above this divider is invisible (preferred height&nbsp;≤&nbsp;0),
 * the divider walks upward through siblings to find the nearest visible pane. If no
 * visible pane exists above, the divider hides itself.
 */
public class TrackPanelDivider extends JPanel implements IGVEventObserver {

    /**
     * How many pixels of extra hit-tolerance {@link DividerHoverOverlay} adds on each side of
     * this divider's own (possibly zero) height - see that class. Kept here so both classes
     * agree on one value without a runtime lookup.
     */
    public static final int HOVER_MARGIN = 3;

    /** Global default visual height, read fresh from Constants.TRACK_BORDER_HEIGHT on every layout pass - see the PreferencesChangeEvent handling below for what forces a re-layout after a Preferences change. */
    public static int getGlobalDividerHeight() {
        return Math.max(0, PreferencesManager.getPreferences().getAsInt(Constants.TRACK_BORDER_HEIGHT));
    }

    public static Color getGlobalBorderColor() {
        boolean darkMode = Globals.isDarkMode();
        IGVPreferences prefs = PreferencesManager.getPreferences();
        return darkMode && !prefs.hasExplicitValue(Constants.TRACK_BORDER_COLOR)
                ? new Color(200, 200, 200)
                : prefs.getAsColor(Constants.TRACK_BORDER_COLOR);
    }

    /**
     * This divider's own effective height - the above track's per-track override
     * (Track.getBorderHeightOverride(), set via this divider's own right-click menu) if
     * present, otherwise the global Constants.TRACK_BORDER_HEIGHT preference. 0 is valid and
     * means a genuinely zero-height divider: the tracks above/below sit flush against each
     * other with no reserved space at all - see DividerHoverOverlay for how drag/right-click
     * still work at height 0 (a separate, transparent, absolutely-positioned component
     * straddles this divider's position purely for hit-testing; this class no longer pads
     * its own layout size to stay clickable).
     */
    private int getVisualBorderHeight() {
        Track track = getOverrideTrack();
        Integer override = track == null ? null : track.getBorderHeightOverride();
        return Math.max(0, override != null ? override : getGlobalDividerHeight());
    }

    private Color computeBorderColor() {
        Track track = getOverrideTrack();
        Color override = track == null ? null : track.getBorderColorOverride();
        return override != null ? override : getGlobalBorderColor();
    }

    /**
     * The track whose per-divider override (if any) governs this specific divider - the
     * track immediately above it, same track the drag-resize handler operates on.
     */
    private Track getOverrideTrack() {
        TrackPanelScrollPane effective = getEffectiveAbovePane();
        return effective == null ? null : effective.getTrackPanel().getTrack();
    }

    /**
     * The immediate pane above this divider.
     */
    private final TrackPanelScrollPane abovePane;

    private int dragStartY;
    private int originalAboveHeight;
    /**
     * True only between a genuine resize-drag's mousePressed and its mouseReleased. Guards
     * mouseDragged, which otherwise fires for *any* held mouse button, not just the one that
     * actually started a resize: on macOS a right-click's isPopupTrigger() is already true at
     * mousePressed, which returns early without touching dragStartY/originalAboveHeight - if
     * the user then drags without releasing the right button, mouseDragged would compute a
     * delta from whatever those two fields last held (0, or a stale value from an earlier
     * unrelated drag), snapping the track above to a huge spurious height. This is that bug's
     * actual root cause - unrelated to the DividerHoverOverlay retargeting issue fixed
     * earlier for the same symptom, which this codebase does still need for other reasons.
     */
    private boolean resizing = false;
    private List<TrackStateEdit.State> resizeBefore = List.of();
    private Track resizingTrack;

    /**
     * @param abovePane the scroll pane above this divider, or {@code null} if none
     * @param belowPane the scroll pane below this divider, or {@code null} for the trailing divider
     */
    public TrackPanelDivider(TrackPanelScrollPane abovePane, TrackPanelScrollPane belowPane) {
        this.abovePane = abovePane;

        setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
        setBackground(computeBorderColor());
        IGVEventBus.getInstance().subscribe(PreferencesChangeEvent.class, this);

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                    return;
                }
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    // Not a popup trigger on this platform/timing, but also not the button a
                    // resize drag should ever start from - e.g. a right-click whose
                    // isPopupTrigger() is only true on release, not press, on some platforms.
                    return;
                }
                TrackPanelScrollPane effective = getEffectiveAbovePane();
                if (effective == null) return;
                if (PreferencesManager.getPreferences().getAsBoolean(Constants.SHOW_SINGLE_TRACK_PANE_KEY)) {
                    return;
                }
                dragStartY = e.getYOnScreen();
                originalAboveHeight = getTrackHeight(effective);
                resizingTrack = effective.getTrackPanel().getTrack();
                resizeBefore = TrackStateEdit.capture(List.of(resizingTrack));
                resizing = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (resizing && resizingTrack != null) {
                    IGV.getInstance().recordUndoableTrackChange(
                            "Resize Track", resizeBefore, List.of(resizingTrack));
                }
                resizing = false;
                resizingTrack = null;
                resizeBefore = List.of();
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!resizing) return;
                TrackPanelScrollPane effective = getEffectiveAbovePane();
                if (effective == null) return;
                if (PreferencesManager.getPreferences().getAsBoolean(Constants.SHOW_SINGLE_TRACK_PANE_KEY)) {
                    return;
                }
                int delta = e.getYOnScreen() - dragStartY;

                int minAbove = getTrackMinimumHeight(effective);
                int newAboveHeight = Math.max(minAbove, originalAboveHeight + delta);
                setTrackHeight(effective, newAboveHeight);

                // Repaint the parent container so vertical divider lines are redrawn
                Container parent = TrackPanelDivider.this.getParent();
                if (parent != null) {
                    parent.repaint();
                }
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

        // Accept TrackPanel drops on the divider — place the dropped track after the track above this divider
        new DropTarget(this, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                handleTrackPanelDrop(dtde);
            }
        }, true);
    }

    /**
     * Right-click menu for overriding just this divider's height/color, stored on the track
     * above it (Track.getBorderHeightOverride/getBorderColorOverride) so it round-trips through
     * Save/Load Session like any other per-track property.
     */
    private void showPopupMenu(MouseEvent e) {
        Track track = getOverrideTrack();
        if (track == null) return;

        IGVPopupMenu menu = new IGVPopupMenu();

        JMenuItem setHeightItem = new JMenuItem("Set Border Height...");
        setHeightItem.addActionListener(evt -> {
            Integer current = track.getBorderHeightOverride() != null ? track.getBorderHeightOverride() : getGlobalDividerHeight();
            Integer value = TrackMenuUtils.getIntegerInput("Border height (pixels)", current);
            if (value != null) {
                IGV.getInstance().runUndoableTrackChange("Set Track Border Height", List.of(track),
                        () -> track.setBorderHeightOverride(Math.max(0, value)));
                revalidate();
                repaint();
            }
        });
        menu.add(setHeightItem);

        JMenuItem setColorItem = new JMenuItem("Set Border Color...");
        setColorItem.addActionListener(evt -> {
            Color current = track.getBorderColorOverride() != null ? track.getBorderColorOverride() : getGlobalBorderColor();
            Color newColor = UIUtilities.showColorChooserDialog("Select Border Color", current);
            if (newColor != null) {
                IGV.getInstance().runUndoableTrackChange("Set Track Border Color", List.of(track),
                        () -> track.setBorderColorOverride(newColor));
                setBackground(computeBorderColor());
                repaint();
            }
        });
        menu.add(setColorItem);

        // Separator groups the two "Set..." actions above from the two "Unset..." actions
        // below, rather than sitting between the two Unset items.
        menu.addSeparator();

        if (track.getBorderHeightOverride() != null || track.getBorderColorOverride() != null) {
            JMenuItem unsetItem = new JMenuItem("Unset Border Height/Color");
            unsetItem.addActionListener(evt -> {
                IGV.getInstance().runUndoableTrackChange("Unset Track Border", List.of(track), () -> {
                    track.setBorderHeightOverride(null);
                    track.setBorderColorOverride(null);
                });
                setBackground(computeBorderColor());
                revalidate();
                repaint();
            });
            menu.add(unsetItem);
        }

        // Always present, on every divider - a divider whose own per-track height override is
        // 0 has no area to be right-clicked at all, so this needs to be reachable from ANY
        // OTHER still-clickable divider (or from the global height=0 case, from whichever
        // divider ends up with a border high enough to click) as a way back, not just from the
        // specific divider that got stuck.
        JMenuItem unsetAllItem = new JMenuItem("Unset All Borders");
        unsetAllItem.setToolTipText("Clear the border height/color override on every track, in case a track's own override was set to 0 and its divider is no longer clickable to fix directly.");
        unsetAllItem.addActionListener(evt -> {
            List<Track> allTracks = IGV.getInstance().getAllTracks();
            IGV.getInstance().runUndoableTrackChange("Unset All Track Borders", allTracks, () -> {
                for (Track t : allTracks) {
                    t.setBorderHeightOverride(null);
                    t.setBorderColorOverride(null);
                }
            });
            setBackground(computeBorderColor());
            IGV.getInstance().revalidateTrackPanels();
            IGV.getInstance().repaint();
        });
        menu.add(unsetAllItem);

        menu.show(this, e.getX(), e.getY());
    }

    /**
     * Picks up a Constants.TRACK_BORDER_COLOR/TRACK_BORDER_HEIGHT change with no restart needed:
     * refreshes the background color directly, and calls revalidate() so the layout manager
     * re-queries getPreferredSize/MinimumSize/MaximumSize (which already read the height fresh -
     * a plain repaint() alone would not re-run layout, so the new height would never take effect).
     */
    @Override
    public void receiveEvent(IGVEvent event) {
        if (event instanceof PreferencesChangeEvent) {
            setBackground(computeBorderColor());
            revalidate();
            repaint();
        }
    }

    /**
     * Handles a TrackPanel being dropped onto this divider. The dropped track is
     * placed immediately after the track above this divider (i.e. at the divider's
     * position in the track list).
     */
    void handleTrackPanelDrop(DropTargetDropEvent dtde) {
        try {
            DataFlavor trackPanelFlavor = TrackPanel.getTrackPanelDataFlavor();
            Transferable transferable = dtde.getTransferable();

            if (!transferable.isDataFlavorSupported(trackPanelFlavor)) {
                // Not a TrackPanel drag — delegate to MainPanel for file/URL drops
                MainPanel mainPanel = IGV.getInstance().getMainPanel();
                mainPanel.drop(dtde);
                return;
            }

            dtde.acceptDrop(DnDConstants.ACTION_MOVE);
            Object transferableObj = transferable.getTransferData(trackPanelFlavor);
            if (transferableObj == null) {
                dtde.dropComplete(false);
                return;
            }

            TrackPanel droppedPanel = (TrackPanel) transferableObj;
            MainPanel mainPanel = IGV.getInstance().getMainPanel();
            List<TrackPanel> panels = mainPanel.getTrackPanels();

            // Determined from the drop's own current screen position (see
            // MainPanel.findTrackPanelAbovePoint) rather than this.abovePane: a drop reaching
            // this method through DividerHoverOverlay's reused/retargeted pool can land on a
            // TrackPanelDivider instance MainPanel.rebuildDividers() already discarded, whose
            // cached abovePane no longer matches any panel in the current layout - that used
            // to silently drop the dragged track out of the reordered list below instead of
            // reinserting it. The drop point's own on-screen position is unaffected by any of
            // that, so deriving the answer from it instead can't go stale the same way.
            TrackPanel aboveTrackPanel = mainPanel.findTrackPanelAbovePoint(
                    dtde.getDropTargetContext().getComponent(), dtde.getLocation());

            // If dropped right back next to itself, do nothing
            if (droppedPanel == aboveTrackPanel) {
                dtde.dropComplete(true);
                return;
            }

            // Build the new order using direct scroll pane references
            List<TrackPanelScrollPane> orderedPanes = new ArrayList<>(panels.size());
            for (TrackPanel panel : panels) {
                if (panel == droppedPanel) continue; // skip dropped panel in its old position
                orderedPanes.add(panel.getScrollPane());
                if (panel == aboveTrackPanel) {
                    orderedPanes.add(droppedPanel.getScrollPane()); // insert after abovePanel
                }
            }
            // If aboveTrackPanel is null (dropped above every current panel), insert at position 0
            TrackPanelScrollPane droppedSp = droppedPanel.getScrollPane();
            if (!orderedPanes.contains(droppedSp)) {
                orderedPanes.add(0, droppedSp);
            }

            IGV.getInstance().runUndoableTrackStructureChange("Reorder Tracks", List.of(), () -> {
                mainPanel.reorderPanels(orderedPanes);
                mainPanel.updateMovedTrackOrder(droppedPanel);
            });
            dtde.dropComplete(true);

        } catch (Exception ex) {
            dtde.rejectDrop();
        }
    }

    /**
     * Returns the width of the name panel region, or 0 if unavailable.
     */
    private int getNamePanelWidth() {
        if (IGV.hasInstance()) {
            return IGV.getInstance().getMainPanel().getNamePanelWidth();
        }
        return 0;
    }

    /**
     * Returns the nearest visible (preferred height &gt; 0) TrackPanelScrollPane
     * above this divider, walking upward through siblings if the immediate pane
     * above has zero height. Returns {@code null} if no visible pane is found.
     */
    private TrackPanelScrollPane getEffectiveAbovePane() {
        Container parent = getParent();
        if (parent == null) return isPaneVisible(abovePane) ? abovePane : null;

        Component[] siblings = parent.getComponents();
        // Find our index
        int myIndex = -1;
        for (int i = 0; i < siblings.length; i++) {
            if (siblings[i] == this) {
                myIndex = i;
                break;
            }
        }
        if (myIndex < 0) return isPaneVisible(abovePane) ? abovePane : null;

        // Walk backwards from just before this divider to find the nearest visible pane
        for (int i = myIndex - 1; i >= 0; i--) {
            if (siblings[i] instanceof TrackPanelScrollPane) {
                TrackPanelScrollPane pane = (TrackPanelScrollPane) siblings[i];
                if (isPaneVisible(pane)) {
                    return pane;
                }
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if the given pane is non-null and contains a visible
     * track (i.e. the track's height is greater than zero).
     */
    private static boolean isPaneVisible(TrackPanelScrollPane pane) {
        if (pane == null) return false;
        Track track = pane.getTrackPanel().getTrack();
        return track != null && track.getHeight() > 0;
    }

    private static int getTrackHeight(TrackPanelScrollPane pane) {
        Track track = pane.getTrackPanel().getTrack();
        return track != null ? track.getHeight() : 0;
    }

    private static int getTrackMinimumHeight(TrackPanelScrollPane pane) {
        Track track = pane.getTrackPanel().getTrack();
        return track != null ? track.getMinimumHeight() : 10;
    }

    private static void setTrackHeight(TrackPanelScrollPane pane, int height) {
        Track track = pane.getTrackPanel().getTrack();
        if (track != null) {
            track.setHeight(height);
        }
    }

    /**
     * Returns {@code true} if this divider should be shown. The divider is hidden
     * when its immediate above pane is not visible (preferred height ≤ 0), because
     * the divider before the invisible pane already provides resize control for
     * the nearest visible track above.
     */
    private boolean shouldBeVisible() {
        return isPaneVisible(abovePane);
    }

    @Override
    public Dimension getPreferredSize() {
        int h = shouldBeVisible() ? getVisualBorderHeight() : 0;
        return new Dimension(Integer.MAX_VALUE, h);
    }

    @Override
    public Dimension getMinimumSize() {
        int h = shouldBeVisible() ? getVisualBorderHeight() : 0;
        return new Dimension(0, h);
    }

    @Override
    public Dimension getMaximumSize() {
        int h = shouldBeVisible() ? getVisualBorderHeight() : 0;
        return new Dimension(Integer.MAX_VALUE, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!shouldBeVisible()) return;
        int visualHeight = getVisualBorderHeight();
        if (visualHeight <= 0) return;
        g.setColor(computeBorderColor());
        g.fillRect(0, 0, getWidth(), Math.min(visualHeight, getHeight()));
        paintRegionalOverlay((Graphics2D) g, Math.min(visualHeight, getHeight()));
    }

    /**
     * The divider is a sibling of the tracks, so a fill painted by DataPanel can never cover
     * it. Paint region-wide background/foreground colors here as well, limited to each visible
     * reference frame's data column. Track-specific colors intentionally stop at the track edge.
     */
    private void paintRegionalOverlay(Graphics2D source, int height) {
        TrackPanelScrollPane pane = getEffectiveAbovePane();
        if (pane == null) return;
        DataPanelContainer container = pane.getTrackPanel().getDataPanelContainer();
        if (container == null) return;

        for (Component component : container.getComponents()) {
            if (!(component instanceof DataPanel dataPanel) || dataPanel.getWidth() <= 0) continue;
            ReferenceFrame frame = dataPanel.getFrame();
            Collection<RegionOfInterest> collection =
                    IGV.getInstance().getSession().getRegionsOfInterest(frame.getChrName());
            if (collection == null || collection.isEmpty()) continue;

            List<RegionOfInterest> regions = new ArrayList<>(collection);
            // Larger first, smaller last: nested region colors agree with the ROI strip's
            // visible stacking and hit-testing order.
            regions.sort(Comparator.comparingInt(RegionOfInterest::getLength).reversed());
            Point panelOrigin = SwingUtilities.convertPoint(dataPanel, 0, 0, this);
            Rectangle dataBounds = new Rectangle(panelOrigin.x, 0, dataPanel.getWidth(), height);
            Graphics2D graphics = (Graphics2D) source.create();
            try {
                graphics.clip(dataBounds);
                paintRegionalOverlayBase(graphics, frame, regions, dataBounds.x, height,
                        effectiveTrackBackground(getOverrideTrack()));
                paintRegionalOverlayPass(graphics, frame, regions, dataBounds.x, false, height);
                paintRegionalOverlayPass(graphics, frame, regions, dataBounds.x, true, height);
            } finally {
                graphics.dispose();
            }
        }
    }

    private Color effectiveTrackBackground(Track track) {
        Color override = track == null ? null : track.getBackgroundColorOverride();
        if (override != null) return override;
        IGVPreferences preferences = PreferencesManager.getPreferences();
        return Globals.isDarkMode() && !preferences.hasExplicitValue(Constants.TRACK_BACKGROUND_COLOR)
                ? UIManager.getColor("Panel.background")
                : preferences.getAsColor(Constants.TRACK_BACKGROUND_COLOR);
    }

    private void paintRegionalOverlayBase(Graphics2D graphics, ReferenceFrame frame,
                                          List<RegionOfInterest> regions, int xOffset,
                                          int height, Color baseColor) {
        double viewportStart = frame.getOrigin();
        double viewportEnd = frame.getEnd();
        for (RegionOfInterest region : regions) {
            RegionDisplayRule rule = region.getDisplayRule();
            if (rule == null || rule.isCollapsed()
                    || (rule.getRegionBackgroundColor() == null && rule.getRegionForegroundColor() == null)
                    || region.getEnd() <= viewportStart || region.getStart() >= viewportEnd) continue;
            int first = xOffset + frame.getScreenPosition(Math.max(viewportStart, region.getStart()));
            int second = xOffset + frame.getScreenPosition(Math.min(viewportEnd, region.getEnd()));
            graphics.setComposite(AlphaComposite.Src);
            graphics.setColor(baseColor);
            graphics.fillRect(Math.min(first, second), 0,
                    Math.max(1, Math.abs(second - first) + 1), height);
            graphics.setComposite(AlphaComposite.SrcOver);
        }
    }

    private void paintRegionalOverlayPass(Graphics2D graphics, ReferenceFrame frame,
                                          List<RegionOfInterest> regions, int xOffset,
                                          boolean foreground, int height) {
        double viewportStart = frame.getOrigin();
        double viewportEnd = frame.getEnd();
        for (RegionOfInterest region : regions) {
            RegionDisplayRule rule = region.getDisplayRule();
            if (rule == null || rule.isCollapsed()
                    || region.getEnd() <= viewportStart || region.getStart() >= viewportEnd) continue;
            Color color = foreground ? rule.getRegionForegroundColor() : rule.getRegionBackgroundColor();
            if (color == null) continue;
            int first = xOffset + frame.getScreenPosition(Math.max(viewportStart, region.getStart()));
            int second = xOffset + frame.getScreenPosition(Math.min(viewportEnd, region.getEnd()));
            int x = Math.min(first, second);
            int width = Math.max(1, Math.abs(second - first) + 1);
            graphics.setColor(color);
            graphics.fillRect(x, 0, width, height);
        }
    }
}
