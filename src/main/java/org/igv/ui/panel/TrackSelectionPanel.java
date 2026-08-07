package org.igv.ui.panel;

import org.igv.Globals;
import org.igv.event.IGVEventBus;
import org.igv.event.TrackSelectionEvent;
import org.igv.prefs.Constants;
import org.igv.prefs.PreferencesManager;
import org.igv.track.Track;
import org.igv.ui.GlobalKeyDispatcher;
import org.igv.ui.IGV;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * A panel that contains a checkbox for selecting a track.
 * This panel is displayed to the left of the drag handle in TrackPanel
 * and is invisible by default. It can be activated from a menu item.
 * The checkbox state is the source of truth for track selection.
 */
public class TrackSelectionPanel extends JPanel {

    public static final int SELECTION_PANEL_WIDTH = 24;

    // Shared "anchor" for shift-click range selection, mirroring Explorer/Finder/Gmail
    // checkbox-list behavior: a plain click sets the anchor to that track; a shift-click
    // on another track selects every track between the anchor and the clicked track
    // (inclusive), in addition to whatever else is already selected. Package-visible (not
    // private) because TrackNamePanel drives the same anchor/range-select from name
    // clicks - see that class's igvMouseClicked, which is the primary way users actually
    // select tracks (clicking the tiny checkbox directly is secondary).
    static Track anchorTrack;

    private final TrackPanel trackPanel;
    private final JCheckBox checkBox;

    // True while setTrackSelected() is programmatically driving this checkbox (from
    // selectRange below, TrackGrouping, HeaderSelectAllPanel, etc.) - guards the
    // itemListener below so only a genuine user click updates the anchor / triggers a
    // range-select, even though setSelected() fires itemStateChanged either way.
    private boolean programmaticUpdate = false;

    public TrackSelectionPanel(TrackPanel trackPanel) {
        this.trackPanel = trackPanel;
        setPreferredSize(new Dimension(SELECTION_PANEL_WIDTH, 0));
        setMinimumSize(new Dimension(SELECTION_PANEL_WIDTH, 0));
        setLayout(new GridBagLayout());

        checkBox = new JCheckBox();
        checkBox.setOpaque(true);

        // NOTE: an ActionListener on this checkbox never fired in testing (root cause
        // still unconfirmed - possibly the LAF's checkbox UI delegate not routing through
        // AbstractButton's normal fireActionPerformed path). itemStateChanged is confirmed
        // to fire (it's what's kept the rest of the UI, e.g. HeaderSelectAllPanel, in sync
        // all along), so all logic - both "notify" and "shift-click range" - lives here now.
        checkBox.addItemListener(e -> {
            IGVEventBus.getInstance().post(new TrackSelectionEvent());
            if (programmaticUpdate) {
                return;
            }
            Track track = getTrack();
            if (GlobalKeyDispatcher.isShiftDown() && anchorTrack != null && anchorTrack != track) {
                selectRange(anchorTrack, track);
            } else {
                anchorTrack = track;
            }
        });

        add(checkBox);

        // Initially invisible
        setVisible(false);
    }

    /**
     * Select (check) every track between {@code anchor} and {@code target}, inclusive,
     * in current visual top-to-bottom order. Tracks outside the range are left as-is.
     */
    static void selectRange(Track anchor, Track target) {
        List<TrackPanel> panels = IGV.getInstance().getMainPanel().getTrackPanels();
        int i1 = -1, i2 = -1;
        for (int i = 0; i < panels.size(); i++) {
            Track t = panels.get(i).getTrack();
            if (t == anchor) i1 = i;
            if (t == target) i2 = i;
        }
        if (i1 == -1 || i2 == -1) {
            return;
        }
        int lo = Math.min(i1, i2), hi = Math.max(i1, i2);
        for (int i = lo; i <= hi; i++) {
            TrackPanelScrollPane sp = panels.get(i).getScrollPane();
            if (sp != null && sp.getSelectionPanel() != null) {
                sp.getSelectionPanel().setTrackSelected(true);
            }
        }
    }

    /**
     * Check if the track is selected (checkbox is checked)
     */
    public boolean isTrackSelected() {
        return checkBox.isSelected();
    }

    /**
     * Set the selection state of this track
     */
    public void setTrackSelected(boolean selected) {
        programmaticUpdate = true;
        try {
            checkBox.setSelected(selected);
        } finally {
            programmaticUpdate = false;
        }
    }

    /**
     * Get the track associated with this selection panel
     */
    public Track getTrack() {
        return trackPanel.getTrack();
    }

    /**
     * Get the width of the selection panel (0 if not visible)
     */
    public int getEffectiveWidth() {
        return isVisible() ? SELECTION_PANEL_WIDTH : 0;
    }

    @Override
    public void setBackground(Color bg) {
        super.setBackground(bg);
        if (checkBox != null) {
            checkBox.setBackground(bg);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        boolean darkMode = Globals.isDarkMode();
        Track track = getTrack();
        Color override = track == null ? null : track.getBackgroundColorOverride();
        Color background = override != null ? override
                : darkMode && !PreferencesManager.getPreferences().hasExplicitValue(Constants.TRACK_BACKGROUND_COLOR)
                ? UIManager.getColor("Panel.background")
                : PreferencesManager.getPreferences().getAsColor(Constants.TRACK_BACKGROUND_COLOR);
        setBackground(background);
        super.paintComponent(g);
    }

    public JCheckBox getCheckBox() {
        return checkBox;
    }

    public TrackPanel getTrackPanel() {
        return trackPanel;
    }
}
