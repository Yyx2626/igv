package org.igv.ui.action;

import org.igv.track.DataTrack;
import org.igv.track.MergedTracks;
import org.igv.track.Track;
import org.igv.ui.AttributeSelectionDialog;
import org.igv.ui.IGV;
import org.igv.ui.panel.TrackPanel;
import org.igv.ui.undo.TrackStructureEdit;
import org.igv.ui.util.UIUtilities;

import javax.swing.JOptionPane;
import java.awt.event.ActionEvent;
import java.util.*;

/**
 * @author jrobinso
 */
public class OverlayTracksMenuAction extends MenuAction {

    //static Logger log = LogManager.getLogger(GroupTracksMenuAction.class);
    IGV igv;

    public OverlayTracksMenuAction(String label, int mnemonic, IGV igv) {
        super(label, null, mnemonic);
        this.igv = igv;
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        UIUtilities.invokeOnEventThread(() -> {

            final AttributeSelectionDialog dlg = new AttributeSelectionDialog(igv.getMainFrame(), "Overlay");
            dlg.setVisible(true);

            if (!dlg.isCanceled()) {
                String selectedAttribute = dlg.getSelected();
                if (selectedAttribute == null) {
                    unmerge(IGV.getInstance().getAllTracks(), true);
                } else {
                    List<DataTrack> tracks = IGV.getInstance().getDataTracks();
                    Map<String, List<DataTrack>> groups = new HashMap<>();
                    for (DataTrack t : tracks) {
                        String v = t.getAttributeValue(selectedAttribute);
                        if (v != null) {
                            List<DataTrack> tlist = groups.get(v);
                            if (tlist == null) {
                                tlist = new ArrayList<>();
                                groups.put(v, tlist);
                            }
                            tlist.add(t);
                        }
                    }

                    for (Map.Entry<String, List<DataTrack>> entry : groups.entrySet()) {
                        String name = entry.getKey();
                        merge(entry.getValue(), name, true);
                    }
                    igv.repaint();
                }

            }
        });
    }

    public static void merge(List<DataTrack> dataTrackList, String name) {
        merge(dataTrackList, name, false);
    }

    public static MergedTracks merge(List<DataTrack> dataTrackList, String name,
                                     boolean interactive) {
        if(dataTrackList.size() < 2) return null;
        IGV igv = IGV.getInstance();
        TrackStructureEdit.Snapshot before = igv.captureTrackStructure(dataTrackList);
        RegionalTrackSettingsTransfer.PreparationResult preparation =
                RegionalTrackSettingsTransfer.prepareCombination("overlay",
                        List.of(new RegionalTrackSettingsTransfer.InputGroup(
                                name == null || name.isBlank() ? "Overlay" : name, dataTrackList)),
                        interactive);
        if (!preparation.proceed()) return null;
        MergedTracks mergedTracks = new MergedTracks(UUID.randomUUID().toString(), name, dataTrackList);
        mergedTracks.setOrder(dataTrackList.get(0).getOrder());
        RegionalTrackSettingsTransfer.TransferResult transfer =
                RegionalTrackSettingsTransfer.inheritMatchingSettings(
                        dataTrackList, mergedTracks, false);
        igv.replaceTracksPreserving(dataTrackList, List.of(mergedTracks));
        if (preparation.resetConflicts() || transfer.changed()) {
            RegionalTrackSettingsTransfer.publishChanges();
        }
        if (interactive) {
            RegionalTrackSettingsTransfer.showPairModeWarning(
                    "creating the overlay", transfer.pairModesRemoved());
            if (preparation.resetConflicts()) {
                JOptionPane.showMessageDialog(IGV.getInstance().getMainFrame(),
                        "The conflicting member-track regional settings were reset and the overlay was created.\n"
                                + "Please review the resulting Regional Settings before relying on the display.",
                        "Review Regional Settings", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        igv.recordUndoableTrackStructureChange("Create Overlay", before, List.of(mergedTracks));
        return mergedTracks;
    }

    public static void unmerge(Collection<Track> tracks) {
        unmerge(tracks, false);
    }

    public static void unmerge(Collection<Track> tracks, boolean interactive) {
        IGV igv = IGV.getInstance();
        TrackStructureEdit.Snapshot before = igv.captureTrackStructure(tracks);
        boolean regionalSettingsChanged = false;
        Set<org.igv.feature.RegionOfInterest> removedPairModes = new LinkedHashSet<>();
        for (Track t : tracks) {

            if (t instanceof MergedTracks mergedTracks) {
                long order = mergedTracks.getOrder();
                mergedTracks.setTrackAlphas(1.0);
                // Set the order of member tracks to match the merged track's order
                for (Track memberTrack : mergedTracks.getMemberTracks()) {
                    memberTrack.setOrder(order);
                }
                RegionalTrackSettingsTransfer.TransferResult transfer =
                        RegionalTrackSettingsTransfer.inheritCompositeSettings(
                                mergedTracks, mergedTracks.getMemberTracks());
                regionalSettingsChanged |= transfer.changed();
                removedPairModes.addAll(transfer.pairModesRemoved());
                igv.replaceTracksPreserving(List.of(mergedTracks),
                        new ArrayList<>(mergedTracks.getMemberTracks()));
            }
        }
        if (regionalSettingsChanged) RegionalTrackSettingsTransfer.publishChanges();
        igv.repaint();
        igv.recordUndoableTrackStructureChange("Separate Overlay", before, tracks);
        if (interactive) {
            RegionalTrackSettingsTransfer.showPairModeWarning(
                    "separating overlay tracks", removedPairModes);
        }
    }

}
