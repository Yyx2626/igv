package org.igv.ui.action;

import org.igv.track.AverageErrorBarTrack;
import org.igv.track.DataTrack;
import org.igv.track.ErrorBarType;
import org.igv.track.Track;
import org.igv.track.TrackPairing;
import org.igv.track.WindowFunction;
import org.igv.ui.IGV;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Handler for the "Average With Error Bar..." context-menu action. Mirrors
 * {@link OverlayTracksMenuAction#merge}, but computes a real per-bin mean/error
 * (via {@link AverageErrorBarTrack}) instead of an alpha-blended overlay, and is
 * pairing-aware: if the selection includes {@link TrackPairing paired} tracks, produces
 * two averaged tracks (one from each pair's top-role members plus any unpaired tracks,
 * one from each pair's bottom-role members) and automatically pairs the two results.
 */
public class AverageErrorBarMenuAction {

    private AverageErrorBarMenuAction() {
    }

    public static void createAverageErrorBarTrack(Collection<Track> selectedTracks, ErrorBarType errorBarType,
                                                    WindowFunction windowFunction, float naValue) {

        List<DataTrack> allDataTracks = new ArrayList<>();
        for (Track t : selectedTracks) {
            if (t instanceof DataTrack) {
                allDataTracks.add((DataTrack) t);
            }
        }
        if (allDataTracks.size() < 2) {
            return;
        }

        TrackPairing.Partition partition = TrackPairing.partitionTopBottom(selectedTracks);
        List<DataTrack> topGroup = filterDataTracks(partition.top);
        List<DataTrack> bottomGroup = filterDataTracks(partition.bottom);

        List<Track> newTracks = new ArrayList<>();
        AverageErrorBarTrack topAvg = null;
        AverageErrorBarTrack bottomAvg = null;
        if (!topGroup.isEmpty()) {
            topAvg = new AverageErrorBarTrack(UUID.randomUUID().toString(), "Average", topGroup, windowFunction, errorBarType, naValue);
            newTracks.add(topAvg);
        }
        if (!bottomGroup.isEmpty()) {
            String name = topAvg != null ? "Average (Bottom)" : "Average";
            bottomAvg = new AverageErrorBarTrack(UUID.randomUUID().toString(), name, bottomGroup, windowFunction, errorBarType, naValue);
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
     * Default Windowing Function to pre-select in {@code AverageErrorBarOptionsDialog}:
     * the members' own shared setting if they all agree, otherwise {@code mean} as a
     * neutral default - the user can always override it in the dialog. A shared
     * {@code none} maps to {@code absoluteMax} (Max where positive, Min where negative),
     * matching what a member's own "None" windowing already looks like once bigwig
     * zoom-pyramid summaries kick in at low zoom, rather than plain {@code max} (which
     * would silently flatten every negative-value bin toward its least-negative point).
     */
    public static WindowFunction computeDefaultWindowFunction(Collection<? extends Track> tracks) {
        List<DataTrack> dataTracks = filterDataTracks(new ArrayList<>(tracks));
        if (dataTracks.isEmpty()) {
            return WindowFunction.mean;
        }
        WindowFunction first = dataTracks.get(0).getWindowFunction();
        for (DataTrack t : dataTracks) {
            if (t.getWindowFunction() != first) {
                return WindowFunction.mean;
            }
        }
        return first == null || first == WindowFunction.none ? WindowFunction.absoluteMax : first;
    }
}
