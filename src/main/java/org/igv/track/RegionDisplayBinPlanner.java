package org.igv.track;

import org.igv.feature.RegionDisplayBoundarySource;
import org.igv.feature.RegionOfInterest;
import org.igv.ui.IGV;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/** Creates the common bin grid used by on-screen numeric tracks and data export. */
public final class RegionDisplayBinPlanner {

    private RegionDisplayBinPlanner() {
    }

    public static DisplayBinPlan create(String chromosome, int rangeStart, int rangeEnd, int requestedBins) {
        if (!IGV.hasInstance()) {
            return DisplayBinPlan.create(rangeStart, rangeEnd, requestedBins, Collections.emptyList());
        }
        Collection<RegionOfInterest> regions = IGV.getInstance().getSession().getRegionsOfInterest(chromosome);
        Set<String> visibleTrackIds = IGV.getInstance().getAllTracks().stream()
                .filter(Track::isVisible)
                .map(Track::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        return create(regions, chromosome, rangeStart, rangeEnd, requestedBins, visibleTrackIds);
    }

    static DisplayBinPlan create(Collection<RegionOfInterest> regions,
                                 String chromosome,
                                 int rangeStart,
                                 int rangeEnd,
                                 int requestedBins,
                                 Set<String> visibleTrackIds) {
        var collapsed = RegionDisplayBoundarySource.getVisibleCollapsedIntervals(
                regions, chromosome, rangeStart, rangeEnd).stream()
                .map(interval -> new DisplayBinPlan.Interval(interval.start(), interval.end()))
                .toList();
        return DisplayBinPlan.create(rangeStart, rangeEnd, requestedBins,
                RegionDisplayBoundarySource.getVisibleHardBreaks(
                        regions, chromosome, rangeStart, rangeEnd, visibleTrackIds),
                collapsed);
    }
}
