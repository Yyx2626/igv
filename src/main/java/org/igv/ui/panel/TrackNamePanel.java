/*
 * TrackPanel.java
 *
 * Created on Sep 5, 2007, 4:09:39 PM
 *
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.igv.ui.panel;


import org.igv.logging.LogManager;
import org.igv.logging.Logger;
import org.igv.prefs.Constants;
import org.igv.prefs.PreferencesManager;
import org.igv.track.Track;
import org.igv.track.TrackClickEvent;
import org.igv.track.TrackGroup;
import org.igv.ui.IGV;
import org.igv.ui.util.IGVMouseInputAdapter;
import org.jdesktop.layout.GroupLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author jrobinso
 */
public class TrackNamePanel extends TrackPanelComponent implements Paintable {

    private static Logger log = LogManager.getLogger(TrackNamePanel.class);

    /**
     * Width of a "dead zone" along the left edge of the name panel, adjacent to the drag
     * handle.  Clicks landing in this margin are ignored to avoid accidentally toggling
     * track selection when the user is aiming for the drag handle.
     */
    private static final int LEFT_CLICK_MARGIN = 6;

    List<GroupExtent> groupExtents = new ArrayList();

    public TrackNamePanel(TrackPanel trackPanel) {
        super(trackPanel);
        init();
    }

    @Override
    public void paintComponent(Graphics g) {

        super.paintComponent(g);

        Rectangle trackRectangle = new Rectangle(getBounds());
        Rectangle visibleRect = getVisibleRect();
        trackRectangle.x = 0; // getBounds returns a rectangle with x/y relative to the parent, we want relative to this component
        trackRectangle.y = 0;

        if (trackRectangle != null && trackRectangle.height > 10) {

            Graphics2D fontGraphics = null;

            try {
                if (darkMode) {
                    setBackground(UIManager.getColor("Panel.background"));
                }

                fontGraphics = (Graphics2D) g.create();

                Color override = getTrack() == null ? null : getTrack().getBackgroundColorOverride();
                final Color backgroundColor = override != null ? override
                        : darkMode ? UIManager.getColor("Panel.background")
                        : PreferencesManager.getPreferences().getAsColor(Constants.TRACK_BACKGROUND_COLOR);
                fontGraphics.setBackground(backgroundColor);
                fontGraphics.setColor(backgroundColor);
                fontGraphics.fillRect(visibleRect.x, visibleRect.y, visibleRect.width, visibleRect.height);

                fontGraphics.setColor(darkMode ? Color.white : Color.BLACK);

                if (PreferencesManager.getPreferences().getAntiAliasing()) {
                    ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                }

                removeMousableRegions();

                paintImpl(fontGraphics, trackRectangle, getVisibleRect(), false);
            } finally {
                fontGraphics.dispose();
            }
        }
    }


    public void paintOffscreen(Graphics2D g, Rectangle rect, boolean batch) {
        paintImpl(g, rect, rect, true);
    }

    @Override
    public int getSnapshotHeight(boolean batch) {
        return getHeight();
    }

    private void paintImpl(Graphics2D g, Rectangle trackRectangle, Rectangle visibleRect, boolean snapshot) {
        Track track = getTrack();
        if (track.isVisible()) {
            track.renderName(g, trackRectangle, visibleRect);
        }
    }

    private void init() {

        GroupLayout dataTrackNamePanelLayout = new GroupLayout(this);
        setLayout(dataTrackNamePanelLayout);
        dataTrackNamePanelLayout.setHorizontalGroup(
                dataTrackNamePanelLayout.createParallelGroup(GroupLayout.LEADING).add(0, 148, Short.MAX_VALUE));
        dataTrackNamePanelLayout.setVerticalGroup(
                dataTrackNamePanelLayout.createParallelGroup(GroupLayout.LEADING).add(0, 528, Short.MAX_VALUE));

        NamePanelMouseAdapter mouseAdapter = new NamePanelMouseAdapter();
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

    }


    public String getTooltipTextForLocation(int x, int y) {

        List<MouseableRegion> mouseableRegions = TrackNamePanel.this.getMouseRegions();

        String text = null;
        for (MouseableRegion mouseableRegion : mouseableRegions) {
            if (mouseableRegion.containsPoint(x, y)) {
                Collection<Track> tracks = mouseableRegion.getTracks();
                if (tracks != null && tracks.size() == 1) {
                    Track track = tracks.iterator().next();
                    text = track.getTooltipText(y);
                } else {
                    text = mouseableRegion.getText();
                }
                break;
            }
        }
        return text;
    }


    private TrackGroup getGroup(int y) {
        for (GroupExtent ge : groupExtents) {
            if (ge.contains(y)) {
                return ge.group;
            }
        }
        return null;
    }

    /**
     * Mouse adapter for the track name panel.  Supports multiple selection,
     * popup menu, and drag & drop within or between name panels.
     */
    class NamePanelMouseAdapter extends IGVMouseInputAdapter {

        @Override
        /**
         * Mouse down.  Track selection logic goes here.
         */
        public void mousePressed(MouseEvent e) {
            super.mousePressed(e);

            requestFocus();
            grabFocus();

            if (e.isPopupTrigger()) {
                TrackClickEvent te = new TrackClickEvent(e, null);
                openPopupMenu(te);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {

            super.mouseReleased(e);

            if (e.isPopupTrigger()) {
                TrackClickEvent te = new TrackClickEvent(e, null);
                openPopupMenu(te);
            }
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            int x = e.getX();
            int y = e.getY();
            setToolTipText(getTooltipTextForLocation(x, y));
        }

        /**
         * Mouse was clicked.  Toggle selection of the associated track (revealing the
         * selection checkboxes if they are hidden), then delegate the click to the track.
         * Shift-click selects the whole range between the last plain-clicked track and
         * this one (mirroring Explorer/Finder/Gmail), matching the checkbox's own
         * shift-click behavior in {@link TrackSelectionPanel}. Clicks in the left margin,
         * adjacent to the drag handle, are ignored.
         * <p>
         * Right-clicks must never affect selection - only pop up the context menu. In
         * principle the superclass's own isPopupTrigger() check already prevents a
         * popup-triggering release from reaching here, but on macOS isPopupTrigger() isn't
         * always reported consistently between mousePressed and mouseReleased for the same
         * gesture, which could leave a stale "mouseDown" in the superclass and let this
         * fire anyway. Checking the mouse button directly is more robust than relying on
         * that flag.
         *
         * @param e
         */
        @Override
        public void igvMouseClicked(final MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || e.getX() < LEFT_CLICK_MARGIN) {
                return;
            }
            handleNameClickSelection(e.isShiftDown());
            getTrack().handleNameClick(e);
        }

    }

    /**
     * Drives track selection from a name-panel click: a plain click toggles this track's
     * checkbox and becomes the new range-select anchor; a shift-click (with an existing
     * anchor) instead selects every track between the anchor and this one, leaving the
     * anchor unchanged so further shift-clicks keep extending from the same start point.
     * Selection state is held by the checkbox in the {@link TrackSelectionPanel}, not by
     * the track itself. If the selection checkboxes are not currently visible, reveal them
     * first (revealing leaves the checkbox unchecked, so a plain click then selects it).
     */
    private void handleNameClickSelection(boolean shiftDown) {
        TrackPanelScrollPane scrollPane = getTrackPanel().getScrollPane();
        if (scrollPane == null) {
            return;
        }
        if (!PreferencesManager.getPreferences().getAsBoolean(Constants.SHOW_SELECTION_PANEL)) {
            IGV.getInstance().getMainPanel().setSelectionPanelsVisible(true);
        }
        TrackSelectionPanel selectionPanel = scrollPane.getSelectionPanel();
        if (selectionPanel == null) {
            return;
        }
        Track track = getTrack();
        if (shiftDown && TrackSelectionPanel.anchorTrack != null && TrackSelectionPanel.anchorTrack != track) {
            TrackSelectionPanel.selectRange(TrackSelectionPanel.anchorTrack, track);
        } else {
            selectionPanel.setTrackSelected(!selectionPanel.isTrackSelected());
            TrackSelectionPanel.anchorTrack = track;
        }
    }

    class GroupExtent {
        TrackGroup group;
        int minY;
        int maxY;

        GroupExtent(TrackGroup group, int minY, int maxY) {
            this.group = group;
            this.maxY = maxY;
            this.minY = minY;
        }

        boolean contains(int y) {
            return y > minY && y <= maxY;
        }

        boolean isAfter(int y) {
            return minY > y;
        }
    }

}
