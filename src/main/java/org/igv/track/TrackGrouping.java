package org.igv.track;

import org.igv.event.IGVEventBus;
import org.igv.event.TrackSelectionEvent;
import org.igv.ui.IGV;
import org.igv.ui.panel.TrackPanel;
import org.igv.ui.panel.TrackPanelScrollPane;
import org.igv.ui.panel.TrackSelectionPanel;

/**
 * Stateless helper implementing RTS-game-style numbered track groups (1-9). Shared by
 * both the keyboard shortcuts ({@code GlobalKeyDispatcher}) and the group-tab buttons
 * ({@code GroupTabsPanel}) so both drive the exact same logic.
 * <p>
 * Group membership is stored per-track as a set of group numbers
 * ({@link Track#getTrackGroups()}), so a track can belong to more than one group.
 */
public class TrackGrouping {

    private TrackGrouping() {
    }

    /**
     * Replace the current selection with exactly the tracks belonging to group
     * {@code n} (like pressing a plain number key in an RTS game).
     */
    public static void selectGroup(int n) {
        for (TrackPanel tp : IGV.getInstance().getMainPanel().getTrackPanels()) {
            TrackPanelScrollPane sp = tp.getScrollPane();
            if (sp == null) continue;
            TrackSelectionPanel selPanel = sp.getSelectionPanel();
            if (selPanel == null) continue;
            Track track = tp.getTrack();
            selPanel.setTrackSelected(track != null && track.getTrackGroups().contains(n));
        }
    }

    /**
     * Redefine group {@code n} to be exactly the currently selected tracks (like
     * Ctrl+N in an RTS game) - tracks previously in group {@code n} but not currently
     * selected are removed from it.
     */
    public static void assignGroup(int n) {
        java.util.List<Track> selected = IGV.getSelectedTracks();
        for (Track track : IGV.getInstance().getAllTracks()) {
            if (selected.contains(track)) {
                track.addToTrackGroup(n);
            } else {
                track.removeFromTrackGroup(n);
            }
        }
        notifyGroupsChanged();
    }

    /**
     * Add the currently selected tracks to group {@code n} (like Shift+N in an RTS
     * game), without removing any existing members of group {@code n} that aren't
     * currently selected.
     */
    public static void addToGroup(int n) {
        for (Track track : IGV.getSelectedTracks()) {
            track.addToTrackGroup(n);
        }
        notifyGroupsChanged();
    }

    /**
     * Group membership isn't reflected by track-selection checkboxes, so reuse
     * {@link TrackSelectionEvent} to let {@code GroupTabsPanel} refresh its tooltips
     * whenever membership changes (assignGroup/addToGroup), not just when the
     * selection itself changes.
     */
    private static void notifyGroupsChanged() {
        IGVEventBus.getInstance().post(new TrackSelectionEvent());
    }
}
