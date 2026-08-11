package org.igv.ui.undo;

import org.igv.feature.RegionDisplayRule;
import org.igv.feature.RegionOfInterest;
import org.igv.track.AverageErrorBarTrack;
import org.igv.track.MergedTracks;
import org.igv.track.Track;
import org.igv.ui.IGV;
import org.igv.ui.panel.FrameManager;
import org.igv.ui.panel.TrackPanelScrollPane;

import javax.swing.undo.AbstractUndoableEdit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact before/after layout, track-state and regional-rule snapshots for composite edits. */
public final class TrackStructureEdit extends AbstractUndoableEdit {

    public record Snapshot(List<TrackPanelScrollPane> panes,
                           List<TrackStateEdit.State> trackStates,
                           Map<RegionOfInterest, RegionDisplayRule> regionRules) {
    }

    private final IGV igv;
    private final String name;
    private final Snapshot before;
    private final Snapshot after;
    private final List<Track> disposableTracks;

    public TrackStructureEdit(IGV igv, String name, Snapshot before, Snapshot after) {
        this.igv = igv;
        this.name = name;
        this.before = before;
        this.after = after;
        Set<Track> beforeTracks = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (TrackStateEdit.State state : before.trackStates()) beforeTracks.add(state.track());
        Set<Track> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        List<Track> result = new ArrayList<>();
        for (TrackStateEdit.State state : after.trackStates()) {
            Track track = state.track();
            if (!beforeTracks.contains(track) && seen.add(track)) result.add(track);
        }
        for (TrackStateEdit.State state : before.trackStates()) {
            Track track = state.track();
            if ((track instanceof AverageErrorBarTrack || track instanceof MergedTracks)
                    && seen.add(track)) result.add(track);
        }
        this.disposableTracks = List.copyOf(result);
    }

    public static Snapshot capture(IGV igv, Collection<? extends Track> extraTracks) {
        List<Track> tracks = new ArrayList<>(igv.getAllTracks());
        if (extraTracks != null) {
            for (Track track : extraTracks) if (track != null && !tracks.contains(track)) tracks.add(track);
        }
        // Composite tracks render from their dormant members. Snapshot those members too,
        // otherwise Undo could restore their colors/ranges while Redo restores only the
        // container and leaves its source tracks in the wrong state.
        for (int i = 0; i < tracks.size(); i++) {
            Track track = tracks.get(i);
            Collection<? extends Track> members = track instanceof AverageErrorBarTrack average
                    ? average.getMemberTracks()
                    : track instanceof MergedTracks merged ? merged.getMemberTracks() : List.of();
            for (Track member : members) if (member != null && !tracks.contains(member)) tracks.add(member);
        }
        Map<RegionOfInterest, RegionDisplayRule> rules = new LinkedHashMap<>();
        for (RegionOfInterest region : igv.getSession().getAllRegionsOfInterest()) {
            RegionDisplayRule rule = region.getDisplayRule();
            rules.put(region, rule == null ? null : rule.copy());
        }
        return new Snapshot(igv.getMainPanel().snapshotTrackPanes(),
                TrackStateEdit.capture(tracks), rules);
    }

    public static boolean differs(Snapshot first, Snapshot second) {
        if (!samePaneOrder(first.panes(), second.panes())) return true;
        if (TrackStateEdit.differs(first.trackStates(), second.trackStates())) return true;
        if (first.regionRules().size() != second.regionRules().size()) return true;
        for (Map.Entry<RegionOfInterest, RegionDisplayRule> entry : first.regionRules().entrySet()) {
            if (!second.regionRules().containsKey(entry.getKey())) return true;
            RegionDisplayRule other = second.regionRules().get(entry.getKey());
            if (!ruleJson(entry.getValue()).equals(ruleJson(other))) return true;
        }
        return false;
    }

    @Override
    public void undo() {
        super.undo();
        apply(before);
    }

    @Override
    public void redo() {
        super.redo();
        apply(after);
    }

    @Override
    public void die() {
        Set<Track> visible = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        visible.addAll(igv.getAllTracks());
        igv.disposeDetachedTracks(disposableTracks.stream()
                .filter(t -> !visible.contains(t)).toList());
        super.die();
    }

    @Override
    public String getPresentationName() {
        return name;
    }

    private void apply(Snapshot snapshot) {
        igv.getMainPanel().restoreTrackPanes(snapshot.panes());
        for (TrackStateEdit.State state : snapshot.trackStates()) state.apply();
        for (Map.Entry<RegionOfInterest, RegionDisplayRule> entry : snapshot.regionRules().entrySet()) {
            entry.getKey().setDisplayRule(entry.getValue() == null ? null : entry.getValue().copy());
        }
        igv.getSession().notifyRegionsOfInterestChanged();
        for (var frame : FrameManager.getFrames()) frame.refreshRegionDisplayCoordinateMap();
        igv.revalidateTrackPanels();
        igv.repaint();
    }

    private static boolean samePaneOrder(List<TrackPanelScrollPane> a, List<TrackPanelScrollPane> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i) != b.get(i)) return false;
        return true;
    }

    private static String ruleJson(RegionDisplayRule rule) {
        return rule == null || !rule.hasAnyEffect() ? "" : rule.toJson().toString();
    }

}
