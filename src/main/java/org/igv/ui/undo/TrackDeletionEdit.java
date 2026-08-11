package org.igv.ui.undo;

import org.igv.track.Track;
import org.igv.ui.IGV;
import org.igv.ui.panel.MainPanel;

import javax.swing.undo.AbstractUndoableEdit;
import java.util.List;

/** Reversible removal of live track panels. */
public final class TrackDeletionEdit extends AbstractUndoableEdit {

    private final IGV igv;
    private final List<MainPanel.DetachedTrackPanel> placements;
    private boolean detached = true;

    public TrackDeletionEdit(IGV igv, List<MainPanel.DetachedTrackPanel> placements) {
        this.igv = igv;
        this.placements = List.copyOf(placements);
    }

    @Override
    public void undo() {
        super.undo();
        igv.getMainPanel().restoreDetachedTrackPanels(placements);
        detached = false;
        refresh();
    }

    @Override
    public void redo() {
        super.redo();
        igv.getMainPanel().detachTrackPanels(tracks());
        detached = true;
        refresh();
    }

    @Override
    public void die() {
        if (detached) igv.disposeDetachedTracks(tracks());
        super.die();
    }

    @Override
    public String getPresentationName() {
        return placements.size() == 1
                ? "Delete " + placements.get(0).track().getName()
                : "Delete " + placements.size() + " Tracks";
    }

    private List<Track> tracks() {
        return placements.stream().map(MainPanel.DetachedTrackPanel::track).toList();
    }

    private void refresh() {
        igv.revalidateTrackPanels();
        igv.repaint();
    }
}
