package org.igv.ui.panel;

import org.igv.feature.RegionDisplayBoundarySource;
import org.igv.feature.RegionOfInterest;
import org.igv.ui.IGV;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Piecewise genomic-to-screen mapping after globally collapsed regions are removed. */
public final class RegionDisplayCoordinateMap {

    public record Segment(double genomicStart, double genomicEnd, int screenX, int screenWidth) {
    }

    private record GenomicSegment(double start, double end) {
        double length() {
            return end - start;
        }
    }

    private final String chromosome;
    private final double rangeStart;
    private final double rangeEnd;
    private final int width;
    private final boolean inverted;
    private final List<GenomicSegment> visibleSegments;
    private final double visibleSpan;
    private final double displayScale;
    private final boolean hasCollapsedIntervals;
    private final long regionsRevision;

    private RegionDisplayCoordinateMap(String chromosome, double rangeStart, double rangeEnd, int width,
                                       boolean inverted, List<GenomicSegment> visibleSegments,
                                       boolean hasCollapsedIntervals, long regionsRevision) {
        this.chromosome = chromosome;
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.width = Math.max(1, width);
        this.inverted = inverted;
        this.visibleSegments = List.copyOf(visibleSegments);
        this.visibleSpan = visibleSegments.stream().mapToDouble(GenomicSegment::length).sum();
        this.displayScale = visibleSpan > 0 ? visibleSpan / this.width : 1;
        this.hasCollapsedIntervals = hasCollapsedIntervals;
        this.regionsRevision = regionsRevision;
    }

    static RegionDisplayCoordinateMap forFrame(ReferenceFrame frame) {
        double start = frame.getOrigin();
        double end = frame.getEnd();
        int width = frame.getWidthInPixels();
        if (!IGV.hasInstance() || frame.getExpandedInsertion() != null || end <= start || width <= 0) {
            return ordinary(frame.getChrName(), start, end, width, frame.isInverted(), -1);
        }
        long revision = IGV.getInstance().getSession().getRegionsOfInterestRevision();
        Collection<RegionOfInterest> regions =
                IGV.getInstance().getSession().getRegionsOfInterest(frame.getChrName());
        List<RegionDisplayBoundarySource.Interval> collapsed =
                RegionDisplayBoundarySource.getVisibleCollapsedIntervals(regions, frame.getChrName(),
                        (int) Math.floor(start), (int) Math.ceil(end));
        return create(frame.getChrName(), start, end, width, frame.isInverted(), collapsed, revision);
    }

    static RegionDisplayCoordinateMap create(String chromosome, double rangeStart, double rangeEnd,
                                             int width, boolean inverted,
                                             Collection<RegionDisplayBoundarySource.Interval> collapsed) {
        return create(chromosome, rangeStart, rangeEnd, width, inverted, collapsed, -1);
    }

    private static RegionDisplayCoordinateMap create(String chromosome, double rangeStart, double rangeEnd,
                                                      int width, boolean inverted,
                                                      Collection<RegionDisplayBoundarySource.Interval> collapsed,
                                                      long regionsRevision) {
        List<GenomicSegment> merged = merge(collapsed, rangeStart, rangeEnd);
        if (merged.isEmpty()) {
            return ordinary(chromosome, rangeStart, rangeEnd, width, inverted, regionsRevision);
        }
        List<GenomicSegment> visible = new ArrayList<>();
        double cursor = rangeStart;
        for (GenomicSegment interval : merged) {
            if (interval.start() > cursor) visible.add(new GenomicSegment(cursor, interval.start()));
            cursor = Math.max(cursor, interval.end());
        }
        if (cursor < rangeEnd) visible.add(new GenomicSegment(cursor, rangeEnd));
        return new RegionDisplayCoordinateMap(chromosome, rangeStart, rangeEnd, width,
                inverted, visible, true, regionsRevision);
    }

    private static RegionDisplayCoordinateMap ordinary(String chromosome, double start, double end,
                                                       int width, boolean inverted, long regionsRevision) {
        List<GenomicSegment> segments = end > start
                ? List.of(new GenomicSegment(start, end)) : List.of();
        return new RegionDisplayCoordinateMap(
                chromosome, start, end, width, inverted, segments, false, regionsRevision);
    }

    private static List<GenomicSegment> merge(
            Collection<RegionDisplayBoundarySource.Interval> intervals, double rangeStart, double rangeEnd) {
        if (intervals == null || intervals.isEmpty()) return List.of();
        List<GenomicSegment> sorted = intervals.stream()
                .filter(interval -> interval.end() > rangeStart && interval.start() < rangeEnd)
                .map(interval -> new GenomicSegment(
                        Math.max(rangeStart, interval.start()),
                        Math.min(rangeEnd, interval.end())))
                .filter(interval -> interval.end() > interval.start())
                .sorted(Comparator.comparingDouble(GenomicSegment::start))
                .toList();
        if (sorted.isEmpty()) return List.of();
        List<GenomicSegment> merged = new ArrayList<>();
        double start = sorted.get(0).start();
        double end = sorted.get(0).end();
        for (int i = 1; i < sorted.size(); i++) {
            GenomicSegment next = sorted.get(i);
            if (next.start() <= end) {
                end = Math.max(end, next.end());
            } else {
                merged.add(new GenomicSegment(start, end));
                start = next.start();
                end = next.end();
            }
        }
        merged.add(new GenomicSegment(start, end));
        return merged;
    }

    public boolean matches(ReferenceFrame frame) {
        long currentRevision = IGV.hasInstance()
                ? IGV.getInstance().getSession().getRegionsOfInterestRevision() : -1;
        return regionsRevision == currentRevision && chromosome.equals(frame.getChrName())
                && rangeStart == frame.getOrigin()
                && rangeEnd == frame.getEnd() && width == Math.max(1, frame.getWidthInPixels())
                && inverted == frame.isInverted();
    }

    public boolean hasCollapsedIntervals() {
        return hasCollapsedIntervals;
    }

    public double getDisplayScale() {
        return displayScale;
    }

    public double getVisibleSpan() {
        return visibleSpan;
    }

    public int getScreenPosition(double genomicPosition) {
        if (visibleSegments.isEmpty()) return inverted ? width : 0;
        double offset = 0;
        for (GenomicSegment segment : visibleSegments) {
            if (genomicPosition < segment.start()) break;
            if (genomicPosition <= segment.end()) {
                offset += Math.max(0, genomicPosition - segment.start());
                return orient(offset / displayScale);
            }
            offset += segment.length();
        }
        return orient(offset / displayScale);
    }

    public double getGenomicPosition(int screenPosition) {
        if (visibleSegments.isEmpty()) return rangeStart;
        double forwardPixel = inverted ? width - screenPosition : screenPosition;
        double targetOffset = Math.max(0, Math.min(visibleSpan, forwardPixel * displayScale));
        double offset = 0;
        for (GenomicSegment segment : visibleSegments) {
            if (targetOffset <= offset + segment.length()) {
                return segment.start() + targetOffset - offset;
            }
            offset += segment.length();
        }
        return visibleSegments.get(visibleSegments.size() - 1).end();
    }

    public List<Segment> getSegments() {
        if (visibleSegments.isEmpty()) return List.of();
        List<Segment> result = new ArrayList<>(visibleSegments.size());
        double offset = 0;
        for (int i = 0; i < visibleSegments.size(); i++) {
            GenomicSegment segment = visibleSegments.get(i);
            int forwardStart = (int) Math.round(offset / displayScale);
            offset += segment.length();
            int forwardEnd = i == visibleSegments.size() - 1
                    ? width : (int) Math.round(offset / displayScale);
            int x = inverted ? width - forwardEnd : forwardStart;
            result.add(new Segment(segment.start(), segment.end(), x,
                    Math.max(1, forwardEnd - forwardStart)));
        }
        return result;
    }

    public boolean isCollapsed(double genomicPosition) {
        if (!hasCollapsedIntervals) return false;
        for (GenomicSegment segment : visibleSegments) {
            if (genomicPosition >= segment.start() && genomicPosition < segment.end()) return false;
        }
        return genomicPosition >= rangeStart && genomicPosition < rangeEnd;
    }

    private int orient(double forwardPixel) {
        int pixel = (int) Math.round(Math.max(0, Math.min(width, forwardPixel)));
        return inverted ? width - pixel : pixel;
    }
}
