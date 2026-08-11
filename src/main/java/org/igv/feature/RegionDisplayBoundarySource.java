package org.igv.feature;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Objects;

/** Finds active region-rule boundaries that intersect the current genomic viewport. */
public final class RegionDisplayBoundarySource {

    public record Interval(int start, int end) {
    }

    private RegionDisplayBoundarySource() {
    }

    public static List<Integer> getVisibleHardBreaks(Collection<RegionOfInterest> regions,
                                                     String chromosome,
                                                     int viewportStart,
                                                     int viewportEnd,
                                                     Set<String> visibleTrackIds) {
        if (regions == null || regions.isEmpty() || viewportEnd <= viewportStart) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        for (RegionOfInterest region : regions) {
            if (region == null || !Objects.equals(chromosome, region.getChr())) continue;
            // Half-open overlap. A rule wholly outside the viewport contributes no bins or breaks.
            if (region.getEnd() <= viewportStart || region.getStart() >= viewportEnd) continue;
            RegionDisplayRule rule = region.getDisplayRule();
            if (rule == null || !rule.hasEffectForVisibleTracks(visibleTrackIds)) continue;
            if (region.getStart() > viewportStart && region.getStart() < viewportEnd) {
                result.add(region.getStart());
            }
            if (region.getEnd() > viewportStart && region.getEnd() < viewportEnd) {
                result.add(region.getEnd());
            }
        }
        return result;
    }

    public static List<Interval> getVisibleCollapsedIntervals(
            Collection<RegionOfInterest> regions, String chromosome, int viewportStart, int viewportEnd) {
        if (regions == null || regions.isEmpty() || viewportEnd <= viewportStart) {
            return Collections.emptyList();
        }
        List<Interval> result = new ArrayList<>();
        for (RegionOfInterest region : regions) {
            if (region == null || !Objects.equals(chromosome, region.getChr())) continue;
            RegionDisplayRule rule = region.getDisplayRule();
            if (rule == null || !rule.isCollapsed()) continue;
            int start = Math.max(viewportStart, region.getStart());
            int end = Math.min(viewportEnd, region.getEnd());
            if (end > start) result.add(new Interval(start, end));
        }
        return result;
    }

    public static List<Interval> getCollapsedIntervals(
            Collection<RegionOfInterest> regions, String chromosome) {
        if (regions == null || regions.isEmpty()) return Collections.emptyList();
        List<Interval> result = new ArrayList<>();
        for (RegionOfInterest region : regions) {
            if (region == null || !Objects.equals(chromosome, region.getChr())) continue;
            RegionDisplayRule rule = region.getDisplayRule();
            if (rule != null && rule.isCollapsed() && region.getEnd() > region.getStart()) {
                result.add(new Interval(region.getStart(), region.getEnd()));
            }
        }
        return result;
    }
}
