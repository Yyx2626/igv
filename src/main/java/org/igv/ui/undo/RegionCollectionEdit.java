package org.igv.ui.undo;

import org.igv.feature.RegionOfInterest;
import org.igv.ui.IGV;
import org.igv.ui.panel.FrameManager;
import org.igv.ui.panel.RegionOfInterestPanel;

import javax.swing.undo.AbstractUndoableEdit;
import java.util.List;

/** Undoable addition or deletion of one user-created ROI collection. */
public final class RegionCollectionEdit extends AbstractUndoableEdit {

    private final IGV igv;
    private final List<RegionOfInterest> regions;
    private final boolean initiallyAdded;

    public RegionCollectionEdit(IGV igv, List<RegionOfInterest> regions,
                                boolean initiallyAdded) {
        this.igv = igv;
        this.regions = List.copyOf(regions);
        this.initiallyAdded = initiallyAdded;
    }

    @Override
    public void undo() {
        super.undo();
        if (initiallyAdded) remove();
        else add();
    }

    @Override
    public void redo() {
        super.redo();
        if (initiallyAdded) add();
        else remove();
    }

    @Override
    public String getPresentationName() {
        String action = initiallyAdded ? "Add" : "Delete";
        return regions.size() == 1 ? action + " Region" : action + " " + regions.size() + " Regions";
    }

    private void add() {
        igv.getSession().addRegionsOfInterest(regions);
        refresh();
    }

    private void remove() {
        igv.getSession().removeRegionsOfInterest(regions);
        if (regions.contains(RegionOfInterestPanel.getSelectedRegion())) {
            RegionOfInterestPanel.setSelectedRegion(null);
        }
        refresh();
    }

    private void refresh() {
        for (var frame : FrameManager.getFrames()) frame.refreshRegionDisplayCoordinateMap();
        igv.repaint();
    }
}
