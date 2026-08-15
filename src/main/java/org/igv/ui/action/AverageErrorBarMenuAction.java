package org.igv.ui.action;

import org.igv.renderer.ScatterPointStyle;
import org.igv.prefs.Constants;
import org.igv.prefs.PreferencesManager;
import org.igv.track.DisplayBinPlan;
import org.igv.track.RegionDisplayBinPlanner;
import org.igv.track.AverageErrorBarTrack;
import org.igv.track.DataTrack;
import org.igv.track.ErrorBarType;
import org.igv.track.Track;
import org.igv.track.TrackPairing;
import org.igv.track.WindowFunction;
import org.igv.ui.IGV;
import org.igv.ui.panel.FrameManager;
import org.igv.ui.panel.ReferenceFrame;
import org.igv.ui.undo.TrackStructureEdit;

import javax.swing.JOptionPane;
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
        createAverageErrorBarTrack(selectedTracks, errorBarType, windowFunction, naValue,
                2, false, new ScatterPointStyle());
    }

    public static void createAverageErrorBarTrack(Collection<Track> selectedTracks, ErrorBarType errorBarType,
                                                   WindowFunction windowFunction, float naValue,
                                                   int minimumErrorBarN, boolean scatterPointsEnabled,
                                                   ScatterPointStyle scatterPointStyle) {

        List<DataTrack> allDataTracks = new ArrayList<>();
        for (Track t : selectedTracks) {
            if (t instanceof DataTrack) {
                allDataTracks.add((DataTrack) t);
            }
        }
        if (allDataTracks.size() < 2) {
            return;
        }

        TrackPairing.Partition partition = TrackPairing.partitionTopBottom(new ArrayList<>(allDataTracks));
        List<DataTrack> topGroup = filterDataTracks(partition.top);
        List<DataTrack> bottomGroup = filterDataTracks(partition.bottom);

        List<RegionalTrackSettingsTransfer.InputGroup> inputGroups = new ArrayList<>();
        inputGroups.add(new RegionalTrackSettingsTransfer.InputGroup(
                bottomGroup.isEmpty() ? "Average" : "Top", topGroup));
        if (!bottomGroup.isEmpty()) {
            inputGroups.add(new RegionalTrackSettingsTransfer.InputGroup("Bottom", bottomGroup));
        }
        IGV igv = IGV.getInstance();
        TrackStructureEdit.Snapshot before = igv.captureTrackStructure(allDataTracks);
        RegionalTrackSettingsTransfer.PreparationResult preparation =
                RegionalTrackSettingsTransfer.prepareCombination("average", inputGroups, true);
        if (!preparation.proceed()) return;

        List<Track> newTracks = new ArrayList<>();
        boolean regionalSettingsChanged = preparation.resetConflicts();
        AverageErrorBarTrack topAvg = null;
        AverageErrorBarTrack bottomAvg = null;
        if (!topGroup.isEmpty()) {
            topAvg = new AverageErrorBarTrack(UUID.randomUUID().toString(), "Average", topGroup, windowFunction, errorBarType, naValue);
            configureDisplay(topAvg, minimumErrorBarN, scatterPointsEnabled, scatterPointStyle);
            RegionalTrackSettingsTransfer.TransferResult transfer =
                    RegionalTrackSettingsTransfer.inheritMatchingSettings(topGroup, topAvg, true);
            regionalSettingsChanged |= transfer.changed();
            newTracks.add(topAvg);
        }
        if (!bottomGroup.isEmpty()) {
            String name = topAvg != null ? "Average (Bottom)" : "Average";
            bottomAvg = new AverageErrorBarTrack(UUID.randomUUID().toString(), name, bottomGroup, windowFunction, errorBarType, naValue);
            configureDisplay(bottomAvg, minimumErrorBarN, scatterPointsEnabled, scatterPointStyle);
            RegionalTrackSettingsTransfer.TransferResult transfer =
                    RegionalTrackSettingsTransfer.inheritMatchingSettings(bottomGroup, bottomAvg, true);
            regionalSettingsChanged |= transfer.changed();
            newTracks.add(bottomAvg);
        }
        if (newTracks.isEmpty()) {
            return;
        }

        // Replace only the DataTracks that actually contributed to the average. The panel
        // operation uses current visual positions rather than stale/tied Track.order values,
        // so TOP is inserted first, BOTTOM immediately below it, at the first input's slot.
        igv.replaceTracksPreserving(new ArrayList<>(allDataTracks), newTracks);
        if (topAvg != null && bottomAvg != null) {
            TrackPairing.pair(topAvg, bottomAvg);
        }
        if (regionalSettingsChanged) RegionalTrackSettingsTransfer.publishChanges();
        igv.repaint();
        igv.recordUndoableTrackStructureChange("Create Average Track", before, newTracks);
        if (preparation.resetConflicts()) {
            JOptionPane.showMessageDialog(IGV.getInstance().getMainFrame(),
                    "The conflicting member-track regional settings were reset and the average track was created.\n"
                            + "Please review the resulting Regional Settings before relying on the display.",
                    "Review Regional Settings", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void configureDisplay(AverageErrorBarTrack track, int minimumErrorBarN,
                                         boolean scatterPointsEnabled,
                                         ScatterPointStyle scatterPointStyle) {
        track.setMinimumErrorBarN(minimumErrorBarN);
        track.setScatterPointsEnabled(scatterPointsEnabled);
        if (scatterPointStyle != null) {
            track.getScatterPointStyle().copyFrom(scatterPointStyle);
        }
    }

    /** Repeat count used to preview the shared default before paired groups are created. */
    public static int computeDefaultRepeatCount(Collection<? extends Track> tracks) {
        List<DataTrack> dataTracks = filterDataTracks(new ArrayList<>(tracks));
        if (dataTracks.isEmpty()) return 1;
        TrackPairing.Partition partition = TrackPairing.partitionTopBottom(
                new ArrayList<>(dataTracks));
        return Math.max(1, Math.max(partition.top.size(), partition.bottom.size()));
    }

    /** Average on-screen bin width for the current first visible locus. */
    public static double estimateCurrentBinWidthPixels() {
        ReferenceFrame frame = FrameManager.getFirstFrame();
        int pixelWidth = frame == null ? 0 : frame.getWidthInPixels();
        if (pixelWidth <= 0 && IGV.hasInstance()) {
            pixelWidth = IGV.getInstance().getMainPanel().getDataPanelWidth();
        }
        if (frame == null || pixelWidth <= 0) return 1.0;
        int start = Math.max(0, (int) Math.floor(frame.getOrigin()));
        int end = Math.max(start + 1, (int) Math.ceil(frame.getEnd()));
        int requestedBins = Math.max(1, PreferencesManager.getPreferences()
                .getAsInt(Constants.SCREENSHOT_DATA_BINS));
        DisplayBinPlan plan = RegionDisplayBinPlanner.create(
                frame.getChrName(), start, end, requestedBins);
        int actualBins = Math.max(1, plan.getBins().size());
        return pixelWidth / (double) actualBins;
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
     * {@code none} is kept as {@code none}: {@code AverageErrorBarDataSource} handles it
     * directly (see its class javadoc), reading each member's actual max/min from the
     * bigwig zoom pyramid rather than substituting a different windowing function.
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
        return first == null ? WindowFunction.mean : first;
    }
}
