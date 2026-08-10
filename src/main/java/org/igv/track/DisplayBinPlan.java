package org.igv.track;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/** A shared set of genomic bins whose edges respect every active display-rule boundary. */
public final class DisplayBinPlan {

    public record Bin(int start, int end) {
        public Bin {
            if (end <= start) throw new IllegalArgumentException("Bin end must be greater than start");
        }
    }

    public record Interval(int start, int end) {
        public Interval {
            if (end <= start) throw new IllegalArgumentException("Interval end must be greater than start");
        }
    }

    private record Segment(int start, int end, double remainder, int bins) {
        int length() {
            return end - start;
        }

        Segment withBins(int value) {
            return new Segment(start, end, remainder, value);
        }
    }

    private final int rangeStart;
    private final int rangeEnd;
    private final List<Bin> bins;

    private DisplayBinPlan(int rangeStart, int rangeEnd, List<Bin> bins) {
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.bins = List.copyOf(bins);
    }

    public static DisplayBinPlan create(int rangeStart, int rangeEnd, int requestedBins,
                                        Collection<Integer> hardBreaks) {
        return create(rangeStart, rangeEnd, requestedBins, hardBreaks, Collections.emptyList());
    }

    public static DisplayBinPlan create(int rangeStart, int rangeEnd, int requestedBins,
                                        Collection<Integer> hardBreaks,
                                        Collection<Interval> excludedIntervals) {
        if (rangeEnd <= rangeStart) {
            return new DisplayBinPlan(rangeStart, rangeEnd, Collections.emptyList());
        }

        TreeSet<Integer> boundaries = new TreeSet<>();
        boundaries.add(rangeStart);
        boundaries.add(rangeEnd);
        if (hardBreaks != null) {
            for (Integer boundary : hardBreaks) {
                if (boundary != null && boundary > rangeStart && boundary < rangeEnd) {
                    boundaries.add(boundary);
                }
            }
        }
        List<Interval> exclusions = mergeExclusions(rangeStart, rangeEnd, excludedIntervals);
        for (Interval interval : exclusions) {
            boundaries.add(interval.start());
            boundaries.add(interval.end());
        }

        List<Integer> points = new ArrayList<>(boundaries);
        List<Segment> segments = new ArrayList<>(points.size() - 1);

        for (int i = 0; i < points.size() - 1; i++) {
            int start = points.get(i);
            int end = points.get(i + 1);
            if (isExcluded(start, end, exclusions)) continue;
            segments.add(new Segment(start, end, 0, 1));
        }
        if (segments.isEmpty()) {
            return new DisplayBinPlan(rangeStart, rangeEnd, Collections.emptyList());
        }

        int visibleSpan = segments.stream().mapToInt(Segment::length).sum();
        int segmentCount = segments.size();
        int targetBins = Math.max(segmentCount, Math.min(Math.max(1, requestedBins), visibleSpan));
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            double ideal = ((double) targetBins * segment.length()) / visibleSpan;
            int allocated = Math.max(1, Math.min(segment.length(), (int) Math.floor(ideal)));
            segments.set(i, new Segment(segment.start(), segment.end(),
                    ideal - Math.floor(ideal), allocated));
        }

        int allocatedTotal = segments.stream().mapToInt(Segment::bins).sum();
        while (allocatedTotal < targetBins) {
            int index = bestSegmentToGrow(segments);
            if (index < 0) break;
            Segment segment = segments.get(index);
            segments.set(index, segment.withBins(segment.bins() + 1));
            allocatedTotal++;
        }
        while (allocatedTotal > targetBins) {
            int index = bestSegmentToShrink(segments);
            if (index < 0) break;
            Segment segment = segments.get(index);
            segments.set(index, segment.withBins(segment.bins() - 1));
            allocatedTotal--;
        }

        List<Bin> bins = new ArrayList<>(targetBins);
        for (Segment segment : segments) {
            for (int i = 0; i < segment.bins(); i++) {
                int binStart = segment.start() + (int) (((long) segment.length() * i) / segment.bins());
                int binEnd = segment.start() + (int) (((long) segment.length() * (i + 1)) / segment.bins());
                bins.add(new Bin(binStart, binEnd));
            }
        }
        return new DisplayBinPlan(rangeStart, rangeEnd, bins);
    }

    private static List<Interval> mergeExclusions(int rangeStart, int rangeEnd,
                                                  Collection<Interval> excludedIntervals) {
        if (excludedIntervals == null || excludedIntervals.isEmpty()) return Collections.emptyList();
        List<Interval> sorted = excludedIntervals.stream()
                .filter(interval -> interval.end() > rangeStart && interval.start() < rangeEnd)
                .map(interval -> new Interval(Math.max(rangeStart, interval.start()),
                        Math.min(rangeEnd, interval.end())))
                .sorted(Comparator.comparingInt(Interval::start))
                .toList();
        if (sorted.isEmpty()) return Collections.emptyList();
        List<Interval> merged = new ArrayList<>();
        int start = sorted.get(0).start();
        int end = sorted.get(0).end();
        for (int i = 1; i < sorted.size(); i++) {
            Interval next = sorted.get(i);
            if (next.start() <= end) {
                end = Math.max(end, next.end());
            } else {
                merged.add(new Interval(start, end));
                start = next.start();
                end = next.end();
            }
        }
        merged.add(new Interval(start, end));
        return merged;
    }

    private static boolean isExcluded(int start, int end, List<Interval> exclusions) {
        for (Interval interval : exclusions) {
            if (start >= interval.start() && end <= interval.end()) return true;
            if (interval.start() >= end) break;
        }
        return false;
    }

    private static int bestSegmentToGrow(List<Segment> segments) {
        return java.util.stream.IntStream.range(0, segments.size())
                .filter(i -> segments.get(i).bins() < segments.get(i).length())
                .boxed()
                .max(Comparator.<Integer>comparingDouble(i -> segments.get(i).remainder())
                        .thenComparingInt(i -> segments.get(i).length() - segments.get(i).bins()))
                .orElse(-1);
    }

    private static int bestSegmentToShrink(List<Segment> segments) {
        return java.util.stream.IntStream.range(0, segments.size())
                .filter(i -> segments.get(i).bins() > 1)
                .boxed()
                .min(Comparator.<Integer>comparingDouble(i -> segments.get(i).remainder())
                        .thenComparingInt(i -> segments.get(i).length()))
                .orElse(-1);
    }

    public int getRangeStart() {
        return rangeStart;
    }

    public int getRangeEnd() {
        return rangeEnd;
    }

    public List<Bin> getBins() {
        return bins;
    }

    public DisplayBinPlan subset(int start, int end) {
        List<Bin> subset = bins.stream()
                .filter(bin -> bin.end() > start && bin.start() < end)
                .toList();
        return new DisplayBinPlan(Math.max(rangeStart, start), Math.min(rangeEnd, end), subset);
    }
}
