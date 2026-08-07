package org.igv.ui.panel;

import org.igv.Globals;
import org.igv.event.IGVEvent;
import org.igv.event.IGVEventBus;
import org.igv.event.IGVEventObserver;
import org.igv.prefs.Constants;
import org.igv.prefs.IGVPreferences;
import org.igv.prefs.PreferencesChangeEvent;
import org.igv.prefs.PreferencesManager;
import org.igv.track.Track;
import org.igv.track.TrackMenuUtils;
import org.igv.ui.IGV;
import org.igv.ui.util.UIUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
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
     * Minimum Swing component height (and mouse hit-testable area) for this divider,
     * regardless of the configured visual border height. Two separate attempts at a
     * genuinely zero-footprint divider (a transparent overlay floating in a JLayeredPane,
     * absolutely positioned to straddle this divider's boundary) each broke drag-and-drop
     * file loading in a different way (first: no response at all; second, after fixing the
     * DnD action constant: only some of several simultaneously-dropped files actually got
     * added to the track panel, and then further drops silently did nothing) - both
     * un-debuggable further without live interactive testing, so this stays a plain
     * flow-participating component with a small minimum height instead. Kept deliberately
     * small (2, not the original 4) to minimize the break it causes in per-track vertical
     * elements (e.g. the Y-axis boundary line) that assume their own track's top/bottom edge
     * is the only gap - see computeBlendBackground().
     */
    private static final int MIN_HIT_HEIGHT = 2;

    /** Global default visual height, read fresh from Constants.TRACK_BORDER_HEIGHT on every layout pass - see the PreferencesChangeEvent handling below for what forces a re-layout after a Preferences change. */
    public static int getGlobalDividerHeight() {
        return Math.max(0, PreferencesManager.getPreferences().getAsInt(Constants.TRACK_BORDER_HEIGHT));
    }

    private static Color computeGlobalBorderColor() {
        boolean darkMode = Globals.isDarkMode();
        IGVPreferences prefs = PreferencesManager.getPreferences();
        return darkMode && !prefs.hasExplicitValue(Constants.TRACK_BORDER_COLOR)
                ? new Color(200, 200, 200)
                : prefs.getAsColor(Constants.TRACK_BORDER_COLOR);
    }

    /**
     * This divider's own effective VISUAL height (how many pixels get painted in the border
     * color, top-aligned against the track above) - the above track's per-track override
     * (Track.getBorderHeightOverride(), set via this divider's own right-click menu) if
     * present, otherwise the global Constants.TRACK_BORDER_HEIGHT preference. 0 is valid here
     * (no visible border at all) - see MIN_HIT_HEIGHT for why the component itself never
     * actually shrinks to 0.
     */
    private int getVisualBorderHeight() {
        Track track = getOverrideTrack();
        Integer override = track == null ? null : track.getBorderHeightOverride();
        return Math.max(0, override != null ? override : getGlobalDividerHeight());
    }

    /** Actual Swing component height: at least MIN_HIT_HEIGHT, so drag/right-click always work even when getVisualBorderHeight() is 0. */
    private int getComponentHeight() {
        return Math.max(getVisualBorderHeight(), MIN_HIT_HEIGHT);
    }

    private Color computeBorderColor() {
        Track track = getOverrideTrack();
        Color override = track == null ? null : track.getBorderColorOverride();
        return override != null ? override : computeGlobalBorderColor();
    }

    /**
     * Fill color for whatever part of this divider's (at-least-MIN_HIT_HEIGHT) component
     * area isn't covered by the actual border-color strip, so an unconfigured/zero-height
     * border reads as "no border" instead of a visible gap - the same background resolution
     * DataPanel uses for the track immediately above.
     */
    private Color computeBlendBackground() {
        Track track = getOverrideTrack();
        Color override = track == null ? null : track.getBackgroundColorOverride();
        if (override != null) return override;
        boolean darkMode = Globals.isDarkMode();
        IGVPreferences prefs = PreferencesManager.getPreferences();
        return darkMode && !prefs.hasExplicitValue(Constants.TRACK_BACKGROUND_COLOR)
                ? UIManager.getColor("Panel.background")
                : prefs.getAsColor(Constants.TRACK_BACKGROUND_COLOR);
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
                TrackPanelScrollPane effective = getEffectiveAbovePane();
                if (effective == null) return;
                if (PreferencesManager.getPreferences().getAsBoolean(Constants.SHOW_SINGLE_TRACK_PANE_KEY)) {
                    return;
                }
                dragStartY = e.getYOnScreen();
                originalAboveHeight = getTrackHeight(effective);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopupMenu(e);
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
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
                track.setBorderHeightOverride(Math.max(0, value));
                revalidate();
                repaint();
            }
        });
        menu.add(setHeightItem);

        JMenuItem setColorItem = new JMenuItem("Set Border Color...");
        setColorItem.addActionListener(evt -> {
            Color current = track.getBorderColorOverride() != null ? track.getBorderColorOverride() : computeGlobalBorderColor();
            Color newColor = UIUtilities.showColorChooserDialog("Select Border Color", current);
            if (newColor != null) {
                track.setBorderColorOverride(newColor);
                setBackground(computeBorderColor());
                repaint();
            }
        });
        menu.add(setColorItem);

        if (track.getBorderHeightOverride() != null || track.getBorderColorOverride() != null) {
            menu.addSeparator();
            JMenuItem unsetItem = new JMenuItem("Unset Border Height/Color");
            unsetItem.addActionListener(evt -> {
                track.setBorderHeightOverride(null);
                track.setBorderColorOverride(null);
                setBackground(computeBorderColor());
                revalidate();
                repaint();
            });
            menu.add(unsetItem);
        }

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
    private void handleTrackPanelDrop(DropTargetDropEvent dtde) {
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

            // Find the track panel above this divider
            TrackPanel aboveTrackPanel = abovePane != null ? abovePane.getTrackPanel() : null;

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
            // If abovePane is null (divider is at top), insert at position 0
            if (aboveTrackPanel == null) {
                TrackPanelScrollPane droppedSp = droppedPanel.getScrollPane();
                if (!orderedPanes.contains(droppedSp)) {
                    orderedPanes.add(0, droppedSp);
                }
            }

            mainPanel.reorderPanels(orderedPanes);
            mainPanel.updateMovedTrackOrder(droppedPanel);
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
        int h = shouldBeVisible() ? getComponentHeight() : 0;
        return new Dimension(Integer.MAX_VALUE, h);
    }

    @Override
    public Dimension getMinimumSize() {
        int h = shouldBeVisible() ? getComponentHeight() : 0;
        return new Dimension(0, h);
    }

    @Override
    public Dimension getMaximumSize() {
        int h = shouldBeVisible() ? getComponentHeight() : 0;
        return new Dimension(Integer.MAX_VALUE, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!shouldBeVisible()) return;

        // Two-layer fill, not a single super.paintComponent()/getBackground() fill: the
        // component itself is always at least MIN_HIT_HEIGHT tall (for dragging/right-click),
        // but only the configured getVisualBorderHeight() worth of that - top-aligned, flush
        // against the track above - is actually painted in the border color. Any remaining
        // pixels are painted with computeBlendBackground() so a 0-height border reads as "no
        // border" instead of a visible gap of some other color.
        int visualHeight = getVisualBorderHeight();
        int componentHeight = getHeight();

        g.setColor(computeBlendBackground());
        g.fillRect(0, 0, getWidth(), componentHeight);

        if (visualHeight > 0) {
            g.setColor(computeBorderColor());
            g.fillRect(0, 0, getWidth(), Math.min(visualHeight, componentHeight));
        }
    }
}

