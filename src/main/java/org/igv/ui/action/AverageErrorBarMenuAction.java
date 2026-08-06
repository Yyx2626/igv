package org.igv.ui.action;

import org.igv.track.AverageErrorBarTrack;
import org.igv.track.DataTrack;
import org.igv.track.ErrorBarType;
import org.igv.track.Track;
import org.igv.track.TrackPairing;
import org.igv.track.WindowFunction;
import org.igv.ui.IGV;
import org.igv.ui.WindowFunctionChooserDialog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Handler for the "Average With Error Bar" context-menu action. Mirrors
 * {@link OverlayTracksMenuAction#merge}, but computes a real per-bin mean/error
 * (via {@link AverageErrorBarTrack}) instead of an alpha-blended overlay, and is
 * pairing-aware: if the selection includes {@link TrackPairing paired} tracks, produces
 * two averaged tracks (one from each pair's top-role members plus any unpaired tracks,
 * one from each pair's bottom-role members) and automatically pairs the two results.
 */
public class AverageErrorBarMenuAction {

    private AverageErrorBarMenuAction() {
    }

    public static void createAverageErrorBarTrack(Collection<Track> selectedTracks, ErrorBarType errorBarType) {

        List<DataTrack> allDataTracks = new ArrayList<>();
        for (Track t : selectedTracks) {
            if (t instanceof DataTrack) {
                allDataTracks.add((DataTrack) t);
            }
        }
        if (allDataTracks.size() < 2) {
            return;
        }

        WindowFunction resolvedFunction = resolveWindowFunction(allDataTracks);
        if (resolvedFunction == null) {
            return; // user canceled the aggregation-function chooser
        }

        TrackPairing.Partition partition = TrackPairing.partitionTopBottom(selectedTracks);
        List<DataTrack> topGroup = filterDataTracks(partition.top);
        List<DataTrack> bottomGroup = filterDataTracks(partition.bottom);

        List<Track> newTracks = new ArrayList<>();
        AverageErrorBarTrack topAvg = null;
        AverageErrorBarTrack bottomAvg = null;
        if (!topGroup.isEmpty()) {
            topAvg = new AverageErrorBarTrack(UUID.randomUUID().toString(), "Average", topGroup, resolvedFunction, errorBarType);
            newTracks.add(topAvg);
        }
        if (!bottomGroup.isEmpty()) {
            String name = topAvg != null ? "Average (Bottom)" : "Average";
            bottomAvg = new AverageErrorBarTrack(UUID.randomUUID().toString(), name, bottomGroup, resolvedFunction, errorBarType);
            newTracks.add(bottomAvg);
        }
        if (newTracks.isEmpty()) {
            return;
        }

        long baseOrder = selectedTracks.iterator().next().getOrder();
        for (int i = 0; i < newTracks.size(); i++) {
            newTracks.get(i).setOrder(baseOrder + i);
        }

        IGV.getInstance().removeTracks(selectedTracks);
        IGV.getInstance().addTracks(newTracks);
        if (topAvg != null && bottomAvg != null) {
            TrackPairing.pair(topAvg, bottomAvg);
        }
        IGV.getInstance().repaint();
    }

    private static List<DataTrack> filterDataTracks(List<Track> tracks) {
        List<DataTrack> result = new ArrayList<>();
        for (Track t : tracks) {
            if (t instanceof DataTrack) {
                result.add((DataTrack) t);
            }
        }
        return result;
    }

    /**
     * If every track already uses the same WindowFunction, use that (mapping
     * {@code none} -&gt; {@code max}). Otherwise ask the user to pick one. Returns null
     * if the user cancels.
     */
    private static WindowFunction resolveWindowFunction(List<DataTrack> tracks) {
        WindowFunction first = tracks.get(0).getWindowFunction();
        boolean allSame = true;
        for (DataTrack t : tracks) {
            if (t.getWindowFunction() != first) {
                allSame = false;
                break;
            }
        }

        if (allSame) {
            return first == null || first == WindowFunction.none ? WindowFunction.max : first;
        }

        WindowFunctionChooserDialog dlg = new WindowFunctionChooserDialog(IGV.getInstance().getMainFrame());
        dlg.setVisible(true);
        return dlg.isCanceled() ? null : dlg.getSelected();
    }
}
