package org.igv.ui.action;

import org.igv.feature.RegionDisplayRule;
import org.igv.feature.RegionOfInterest;
import org.igv.feature.TrackRegionOverride;
import org.igv.track.Track;
import org.igv.ui.IGV;
import org.igv.ui.panel.FrameManager;

import javax.swing.JOptionPane;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Transfers per-track regional display settings when tracks are combined or restored. */
public final class RegionalTrackSettingsTransfer {

    public record InputGroup(String name, List<Track> tracks) {
        public InputGroup(String name, Collection<? extends Track> tracks) {
            this(name, List.copyOf(tracks));
        }
    }

    public record TransferResult(boolean changed, Set<RegionOfInterest> pairModesRemoved) {
    }

    public record PreparationResult(boolean proceed, boolean resetConflicts) {
    }

    private record Conflict(String groupName, RegionOfInterest region, List<Track> tracks) {
    }

    private RegionalTrackSettingsTransfer() {
    }

    /**
     * Validate each prospective composite independently. If settings conflict, interactive
     * callers can reset only the conflicting member overrides and continue; non-interactive
     * callers leave member settings untouched and simply omit inheritance for that ROI.
     */
    public static PreparationResult prepareCombination(String operation, List<InputGroup> groups,
                                                       boolean interactive) {
        List<Conflict> conflicts = findConflicts(groups);
        if (conflicts.isEmpty() || !interactive) return new PreparationResult(true, false);
        int choice = JOptionPane.showConfirmDialog(IGV.getInstance().getMainFrame(),
                conflictMessage(operation, conflicts), "Regional Settings Differ",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return new PreparationResult(false, false);
        resetConflictingOverrides(conflicts);
        return new PreparationResult(true, true);
    }

    /** Copy settings shared by every member to a newly created composite track. */
    public static TransferResult inheritMatchingSettings(Collection<? extends Track> members,
                                                         Track composite,
                                                         boolean allowPairModes) {
        List<? extends Track> inputs = List.copyOf(members);
        if (inputs.isEmpty() || composite == null || !IGV.hasInstance()) {
            return new TransferResult(false, Set.of());
        }
        boolean changed = false;
        Set<RegionOfInterest> pairRemoved = new LinkedHashSet<>();
        for (RegionOfInterest region : IGV.getInstance().getSession().getAllRegionsOfInterest()) {
            RegionDisplayRule rule = region.getDisplayRule();
            if (rule == null) continue;
            TrackRegionOverride shared = normalized(rule.getTrackOverride(inputs.get(0).getId()));
            boolean same = true;
            for (int i = 1; i < inputs.size(); i++) {
                if (!sameOverride(shared, rule.getTrackOverride(inputs.get(i).getId()))) {
                    same = false;
                    break;
                }
            }
            if (!same || shared == null) continue;
            boolean removesPairMode = !allowPairModes
                    && shared.getPairMode() != TrackRegionOverride.PairMode.NONE;
            TrackRegionOverride inherited = copyForTransfer(shared, allowPairModes);
            if (removesPairMode) {
                pairRemoved.add(region);
            }
            rule.setTrackOverride(composite.getId(), inherited);
            changed |= inherited.hasAnyEffect();
        }
        return new TransferResult(changed, Set.copyOf(pairRemoved));
    }

    /**
     * Make restored members match the current composite setting in every ROI. Pair modes are
     * deliberately removed because the composite's partner cannot be mapped unambiguously to
     * individual member partners.
     */
    public static TransferResult inheritCompositeSettings(Track composite,
                                                          Collection<? extends Track> members) {
        if (composite == null || members == null || members.isEmpty() || !IGV.hasInstance()) {
            return new TransferResult(false, Set.of());
        }
        boolean changed = false;
        Set<RegionOfInterest> pairRemoved = new LinkedHashSet<>();
        for (RegionOfInterest region : IGV.getInstance().getSession().getAllRegionsOfInterest()) {
            RegionDisplayRule rule = region.getDisplayRule();
            if (rule == null) continue;
            TrackRegionOverride source = normalized(rule.getTrackOverride(composite.getId()));
            boolean removesPairMode = source != null
                    && source.getPairMode() != TrackRegionOverride.PairMode.NONE;
            TrackRegionOverride inherited = copyForTransfer(source, false);
            if (removesPairMode) {
                pairRemoved.add(region);
            }
            for (Track member : members) {
                TrackRegionOverride before = normalized(rule.getTrackOverride(member.getId()));
                if (!sameOverride(before, inherited)) changed = true;
                rule.setTrackOverride(member.getId(), inherited == null ? null : inherited.copy());
            }
            if (rule.getTrackOverride(composite.getId()) != null) {
                rule.removeTrackOverride(composite.getId());
                changed = true;
            }
            region.setDisplayRule(rule);
        }
        return new TransferResult(changed, Set.copyOf(pairRemoved));
    }

    public static void publishChanges() {
        if (!IGV.hasInstance()) return;
        IGV.getInstance().getSession().notifyRegionsOfInterestChanged();
        for (var frame : FrameManager.getFrames()) frame.refreshRegionDisplayCoordinateMap();
    }

    public static void showPairModeWarning(String operation,
                                           Collection<RegionOfInterest> regions) {
        if (regions == null || regions.isEmpty() || !IGV.hasInstance()) return;
        StringBuilder message = new StringBuilder(
                "Pair Swap/Pair Flip could not be inherited during " + operation
                        + " because the individual pair relationship is ambiguous.\n"
                        + "Those pair transformations were reset in:\n");
        appendRegions(message, regions, 12);
        message.append("\nReconfigure the individual track pairs and Regional Settings if needed.");
        JOptionPane.showMessageDialog(IGV.getInstance().getMainFrame(), message.toString(),
                "Regional Pair Settings Reset", JOptionPane.WARNING_MESSAGE);
    }

    static boolean sameOverride(TrackRegionOverride first, TrackRegionOverride second) {
        TrackRegionOverride a = normalized(first);
        TrackRegionOverride b = normalized(second);
        if (a == null || b == null) return a == b;
        return Objects.equals(a.toJson().toString(), b.toJson().toString());
    }

    static TrackRegionOverride copyForTransfer(TrackRegionOverride source,
                                               boolean allowPairModes) {
        if (source == null) return null;
        TrackRegionOverride copy = source.copy();
        if (!allowPairModes) copy.setPairMode(TrackRegionOverride.PairMode.NONE);
        return copy;
    }

    private static TrackRegionOverride normalized(TrackRegionOverride override) {
        return override != null && override.hasAnyEffect() ? override : null;
    }

    private static List<Conflict> findConflicts(List<InputGroup> groups) {
        List<Conflict> conflicts = new ArrayList<>();
        if (!IGV.hasInstance()) return conflicts;
        for (InputGroup group : groups) {
            if (group.tracks.size() < 2) continue;
            for (RegionOfInterest region : IGV.getInstance().getSession().getAllRegionsOfInterest()) {
                RegionDisplayRule rule = region.getDisplayRule();
                if (rule == null) continue;
                TrackRegionOverride first = normalized(
                        rule.getTrackOverride(group.tracks.get(0).getId()));
                boolean same = true;
                for (int i = 1; i < group.tracks.size(); i++) {
                    if (!sameOverride(first,
                            rule.getTrackOverride(group.tracks.get(i).getId()))) {
                        same = false;
                        break;
                    }
                }
                if (!same) conflicts.add(new Conflict(
                        group.name, region, group.tracks));
            }
        }
        return conflicts;
    }

    private static void resetConflictingOverrides(List<Conflict> conflicts) {
        Set<RegionOfInterest> touched = new LinkedHashSet<>();
        for (Conflict conflict : conflicts) {
            RegionDisplayRule rule = conflict.region.getDisplayRule();
            if (rule == null) continue;
            for (Track track : conflict.tracks) rule.removeTrackOverride(track.getId());
            touched.add(conflict.region);
        }
        for (RegionOfInterest region : touched) region.setDisplayRule(region.getDisplayRule());
    }

    private static String conflictMessage(String operation, List<Conflict> conflicts) {
        StringBuilder message = new StringBuilder(
                "Regional settings differ among tracks that would contribute to the same "
                        + operation + ":\n\n");
        int shown = Math.min(conflicts.size(), 12);
        for (int i = 0; i < shown; i++) {
            Conflict conflict = conflicts.get(i);
            message.append("• ").append(regionLabel(conflict.region))
                    .append(" (").append(conflict.groupName).append("): ")
                    .append(conflict.tracks.stream().map(Track::getName).toList()).append('\n');
        }
        if (conflicts.size() > shown) {
            message.append("• ... and ").append(conflicts.size() - shown).append(" more region(s)\n");
        }
        message.append("\nReset the track-specific settings in these conflicting regions and continue?\n")
                .append("The new composite track will inherit settings only where the inputs agree.");
        return message.toString();
    }

    private static void appendRegions(StringBuilder message,
                                      Collection<RegionOfInterest> regions, int limit) {
        int index = 0;
        for (RegionOfInterest region : regions) {
            if (index++ >= limit) break;
            message.append("• ").append(regionLabel(region)).append('\n');
        }
        if (regions.size() > limit) {
            message.append("• ... and ").append(regions.size() - limit).append(" more region(s)\n");
        }
    }

    private static String regionLabel(RegionOfInterest region) {
        String description = region.getDescription();
        return description == null || description.isBlank()
                ? region.getLocusString() : region.getLocusString() + " — " + description;
    }
}
