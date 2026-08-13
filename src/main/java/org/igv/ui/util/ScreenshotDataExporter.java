package org.igv.ui.util;

import org.igv.data.AverageErrorLocusScore;
import org.igv.feature.RegionDisplayBoundarySource;
import org.igv.feature.RegionDisplayRule;
import org.igv.feature.RegionOfInterest;
import org.igv.feature.Strand;
import org.igv.feature.LocusScore;
import org.igv.feature.TrackRegionOverride;
import org.igv.feature.genome.Genome;
import org.igv.feature.genome.GenomeManager;
import org.igv.feature.genome.SeqUtils;
import org.igv.prefs.Constants;
import org.igv.prefs.PreferencesManager;
import org.igv.track.AverageErrorBarTrack;
import org.igv.track.DataTrack;
import org.igv.track.DisplayBinPlan;
import org.igv.track.MergedTracks;
import org.igv.track.NumericTrackBinner;
import org.igv.track.RegionDisplayBinPlanner;
import org.igv.track.SequenceTrack;
import org.igv.track.Track;
import org.igv.track.TrackPairing;
import org.igv.track.WindowFunction;
import org.igv.ui.IGV;
import org.igv.ui.panel.FrameManager;
import org.igv.ui.panel.ReferenceFrame;
import org.igv.ui.panel.RegionDisplayCoordinateMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Exports visible numeric tracks and, at base resolution, reference sequence into genomic bins. */
public final class ScreenshotDataExporter {

    private ScreenshotDataExporter() {
    }

    enum ValueKind {VALUE, MEMBER, N, AVERAGE, SD, SEM, POS, NEG}

    /**
     * {@code signFilter} disambiguates which of a bin's (up to two) AverageErrorLocusScore
     * entries an N/AVERAGE/SD/SEM column reads, when an "Average With Error Bar" track's
     * Windowing Function is None and a bin has both a positive-group and a negative-group
     * entry: {@code TRUE} reads only the entry whose mean is above the baseline, {@code FALSE}
     * only the one below, {@code null} means "no ambiguity possible" (every other case - VALUE,
     * POS, NEG, and single-group N/AVERAGE/SD/SEM, where at most one matching entry ever exists).
     */
    private record ValueColumn(String header, ValueKind kind, Boolean signFilter, int memberIndex) {
        ValueColumn(String header, ValueKind kind) {
            this(header, kind, null, -1);
        }

        ValueColumn(String header, ValueKind kind, Boolean signFilter) {
            this(header, kind, signFilter, -1);
        }
    }

    private record ExportTrack(String sourceHeader, Track displayTrack, DataTrack dataTrack,
                               int memberIndex, List<ValueColumn> values, boolean includeSource,
                               float baseline, WindowFunction windowFunction) {
    }

    private record SourceMapping(Track sourceTrack, DataTrack sourceDataTrack,
                                 String chr, int start, int end,
                                 boolean inverted, boolean pairExchanged) {
        boolean transformed() {
            return inverted || pairExchanged;
        }
    }

    private record SequenceSlice(int rangeStart, byte[] bases) {
    }

    private record Interval(int start, int end) {
    }

    private record OutputRow(int start, int end, String note, int binIndex) {
        boolean isBin() {
            return binIndex >= 0;
        }
    }

    public static void export(IGV igv, File file, int requestedBins) throws IOException {
        List<ReferenceFrame> frames = FrameManager.getFrames().stream().filter(ReferenceFrame::isVisible).toList();
        List<ExportTrack> exportTracks = buildExportTracks(igv, frames);
        SequenceTrack sequenceTrack = igv.getSequenceTrack();
        boolean includeSequence = sequenceTrack != null && frames.stream().anyMatch(frame ->
                frame.getScale() < PreferencesManager.getPreferences().getAsInt(Constants.MAX_SEQUENCE_RESOLUTION)
                        && !frame.getChrName().equals(org.igv.Globals.CHR_ALL));
        boolean includeSequenceSource = includeSequence && hasSourceTransform(sequenceTrack, frames, igv);
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write("chr\tstart\tend\tbin_note");
            if (frames.size() > 1) writer.write("\tpanel");
            if (includeSequence) {
                if (includeSequenceSource) writer.write("\tSequence.source");
                writer.write("\t" + sequenceHeader(sequenceTrack.getStrand()));
            }
            for (ExportTrack exportTrack : exportTracks) {
                if (exportTrack.includeSource) writer.write("\t" + cleanHeader(exportTrack.sourceHeader));
                for (ValueColumn value : exportTrack.values) writer.write("\t" + cleanHeader(value.header));
            }
            writer.newLine();

            for (ReferenceFrame frame : frames) {
                RegionDisplayCoordinateMap coordinateMap = frame.getRegionDisplayCoordinateMap();
                int rangeStart = Math.max(0, (int) Math.floor(coordinateMap.getGenomicRenderStart()));
                int rangeEnd = Math.max(rangeStart + 1, (int) Math.ceil(coordinateMap.getGenomicRenderEnd()));
                boolean frameHasSequence = includeSequence
                        && frame.getScale() < PreferencesManager.getPreferences().getAsInt(Constants.MAX_SEQUENCE_RESOLUTION)
                        && !frame.getChrName().equals(org.igv.Globals.CHR_ALL);
                int visibleSpan = Math.max(1, (int) Math.round(coordinateMap.getVisibleSpan()));
                int targetBins = frameHasSequence
                        ? visibleSpan
                        : Math.max(1, Math.min(Math.max(1, requestedBins), visibleSpan));
                DisplayBinPlan binPlan = RegionDisplayBinPlanner.create(
                        frame.getChrName(), rangeStart, rangeEnd, targetBins);
                List<DisplayBinPlan.Bin> bins = binPlan.getBins();
                Collection<RegionOfInterest> regions = igv.getSession().getRegionsOfInterest(frame.getChrName());

                Map<ExportTrack, List<SourceMapping>> mappings = new IdentityHashMap<>();
                Map<DataTrack, List<Interval>> requestedIntervals = new IdentityHashMap<>();
                for (ExportTrack exportTrack : exportTracks) {
                    List<SourceMapping> trackMappings = new ArrayList<>(bins.size());
                    for (DisplayBinPlan.Bin bin : bins) {
                        SourceMapping mapping = resolveSource(exportTrack, bin, regions, igv.getAllTracks());
                        trackMappings.add(mapping);
                        if (mapping.sourceDataTrack != null) {
                            requestedIntervals.computeIfAbsent(mapping.sourceDataTrack,
                                    ignored -> new ArrayList<>()).add(new Interval(mapping.start, mapping.end));
                        }
                    }
                    mappings.put(exportTrack, trackMappings);
                }
                Map<DataTrack, List<LocusScore>> scoreCache = loadScores(
                        requestedIntervals, frame.getChrName(), frame.getZoom());

                List<SourceMapping> sequenceMappings = new ArrayList<>();
                List<SequenceSlice> sequenceSlices = new ArrayList<>();
                if (frameHasSequence) {
                    List<Interval> sequenceIntervals = new ArrayList<>();
                    ExportTrack sequenceExport = new ExportTrack("Sequence source", sequenceTrack,
                            null, -1, List.of(), includeSequenceSource, 0f, WindowFunction.none);
                    for (DisplayBinPlan.Bin bin : bins) {
                        SourceMapping mapping = resolveSource(sequenceExport, bin, regions, igv.getAllTracks());
                        sequenceMappings.add(mapping);
                        sequenceIntervals.add(new Interval(mapping.start, mapping.end));
                    }
                    Genome genome = GenomeManager.getInstance().getCurrentGenome();
                    if (genome != null) {
                        for (Interval interval : mergeIntervals(sequenceIntervals)) {
                            byte[] bases = genome.getSequence(frame.getChrName(), interval.start, interval.end);
                            if (bases != null) sequenceSlices.add(new SequenceSlice(interval.start, bases));
                        }
                    }
                }

                List<OutputRow> rows = createOutputRows(bins, regions, frame.getChrName(), rangeStart, rangeEnd);
                if (frame.isInverted()) java.util.Collections.reverse(rows);
                for (OutputRow row : rows) {
                    writer.write(clean(frame.getChrName()) + "\t" + row.start + "\t" + row.end
                            + "\t" + row.note);
                    if (frames.size() > 1) writer.write("\t" + clean(frame.getName()));
                    if (!row.isBin()) {
                        writeMissingColumns(writer, includeSequence, includeSequenceSource, exportTracks);
                        writer.newLine();
                        continue;
                    }
                    int binIndex = row.binIndex;
                    if (includeSequence) {
                        if (includeSequenceSource) {
                            SourceMapping mapping = frameHasSequence ? sequenceMappings.get(binIndex) : null;
                            writer.write("\t" + sourceValue(mapping));
                        }
                        String base = frameHasSequence ? sequenceValue(sequenceSlices,
                                sequenceMappings.get(binIndex), sequenceTrack.getStrand()) : null;
                        writer.write("\t" + (base == null ? "NA" : base));
                    }
                    for (ExportTrack exportTrack : exportTracks) {
                        SourceMapping mapping = mappings.get(exportTrack).get(binIndex);
                        if (exportTrack.includeSource) writer.write("\t" + sourceValue(mapping));
                        for (ValueColumn value : exportTrack.values) {
                            String result = valueFor(scoreCache.get(mapping.sourceDataTrack),
                                    mapping.start, mapping.end, value.kind, exportTrack.baseline,
                                    value.signFilter, value.memberIndex, exportTrack.windowFunction);
                            writer.write("\t" + (result == null ? "NA" : result));
                        }
                    }
                    writer.newLine();
                }
            }
        }
    }

    private static String sequenceValue(List<SequenceSlice> slices, SourceMapping mapping,
                                        Strand selectedStrand) {
        if (mapping == null || mapping.end - mapping.start != 1) return null;
        for (SequenceSlice slice : slices) {
            int index = mapping.start - slice.rangeStart;
            if (index < 0 || index >= slice.bases.length) continue;
            char base = (char) slice.bases[index];
            boolean complement = selectedStrand == Strand.NEGATIVE;
            if (mapping.inverted) complement = !complement;
            if (complement) base = SeqUtils.complementChar(base);
            return Character.toString(base);
        }
        return null;
    }

    static String sequenceValue(byte[] bases, int rangeStart, int binStart, int binEnd, Strand strand) {
        return sequenceValue(bases, rangeStart, binStart, binEnd, strand, false);
    }

    static String sequenceValue(byte[] bases, int rangeStart, int binStart, int binEnd,
                                Strand strand, boolean regionInverted) {
        SourceMapping mapping = new SourceMapping(
                null, null, "", binStart, binEnd, regionInverted, false);
        return sequenceValue(List.of(new SequenceSlice(rangeStart, bases)), mapping, strand);
    }

    static String sequenceHeader(Strand strand) {
        return strand == Strand.NEGATIVE ? "Sequence_genomic_minus" : "Sequence_genomic_plus";
    }

    private static List<ExportTrack> buildExportTracks(IGV igv, List<ReferenceFrame> frames) {
        List<ExportTrack> result = new ArrayList<>();
        Map<String, Integer> usedNames = new LinkedHashMap<>();
        int trackNumber = 0;
        for (Track track : igv.getAllTracks()) {
            if (!track.isVisible() || !(track instanceof DataTrack dataTrack)) continue;
            // "track_N." disambiguates which visible track a column belongs to at a glance,
            // numbered in display order among only the tracks that actually get exported - every
            // column contributed by this one visible track (including an Average track's own
            // stats and its members' source columns below) shares the same N.
            String trackPrefix = "track_" + (++trackNumber) + ".";
            boolean includeSource = hasSourceTransform(track, frames, igv);
            if (dataTrack instanceof MergedTracks merged) {
                List<DataTrack> members = merged.getMemberTracks();
                for (int i = 0; i < members.size(); i++) {
                    DataTrack member = members.get(i);
                    addExportTrack(result, usedNames, track, member, i,
                            trackPrefix + dataTrack.getName() + "." + member.getName(), includeSource, frames);
                }
            } else if (dataTrack instanceof AverageErrorBarTrack avgTrack) {
                addAverageExportTrack(result, usedNames, track, avgTrack,
                        trackPrefix + dataTrack.getName(), includeSource, frames);
            } else {
                addExportTrack(result, usedNames, track, dataTrack, -1,
                        trackPrefix + dataTrack.getName(), includeSource, frames);
            }
        }
        return result;
    }

    private static void addAverageExportTrack(List<ExportTrack> result, Map<String, Integer> usedNames,
                                              Track displayTrack, AverageErrorBarTrack averageTrack,
                                              String prefix, boolean includeSource,
                                              List<ReferenceFrame> frames) {
        String sourceHeader = uniqueName(usedNames, prefix + ".source");
        List<ValueColumn> values = new ArrayList<>();
        List<DataTrack> members = averageTrack.getMemberTracks();
        float baseline = averageTrack.getDataRange() == null
                ? 0f : averageTrack.getDataRange().getBaseline();

        if (averageTrack.getWindowFunction() == WindowFunction.none) {
            SignRange sign = classifyWholeRangeSign(averageTrack, frames, AVERAGE_ENVELOPE_BASELINE);
            if (sign.hasPositive() && sign.hasNegative()) {
                addAverageMemberColumns(values, usedNames, prefix + ".pos", members, true);
                addAverageStatColumns(values, usedNames, prefix + ".pos", true);
                addAverageMemberColumns(values, usedNames, prefix + ".neg", members, false);
                addAverageStatColumns(values, usedNames, prefix + ".neg", false);
            } else if (sign.hasNegative()) {
                addAverageMemberColumns(values, usedNames, prefix, members, false);
                addAverageStatColumns(values, usedNames, prefix, false);
            } else {
                addAverageMemberColumns(values, usedNames, prefix, members, true);
                addAverageStatColumns(values, usedNames, prefix, true);
            }
        } else {
            addAverageMemberColumns(values, usedNames, prefix, members, null);
            addAverageStatColumns(values, usedNames, prefix, null);
        }
        result.add(new ExportTrack(sourceHeader, displayTrack, averageTrack,
                -1, List.copyOf(values), includeSource, baseline, averageTrack.getWindowFunction()));
    }

    private static void addAverageMemberColumns(List<ValueColumn> values,
                                                Map<String, Integer> usedNames,
                                                String prefix, List<DataTrack> members,
                                                Boolean signFilter) {
        for (int i = 0; i < members.size(); i++) {
            values.add(new ValueColumn(uniqueName(usedNames,
                    prefix + ".source." + members.get(i).getName()),
                    ValueKind.MEMBER, signFilter, i));
        }
    }

    private static void addExportTrack(List<ExportTrack> result, Map<String, Integer> usedNames,
                                       Track displayTrack, DataTrack dataTrack, int memberIndex,
                                       String prefix, boolean includeSource, List<ReferenceFrame> frames) {
        String sourceHeader = uniqueName(usedNames, prefix + ".source");
        List<ValueColumn> values = new ArrayList<>();
        float baseline = dataTrack.getWindowFunction() == WindowFunction.none || dataTrack.getDataRange() == null
                ? 0f : dataTrack.getDataRange().getBaseline();
        if (dataTrack instanceof AverageErrorBarTrack && dataTrack.getWindowFunction() == WindowFunction.none) {
            // Mirrors the plain-track "None" envelope below, one level up: AverageErrorBarDataSource
            // (see its class javadoc) emits a positive-group and/or negative-group statistic per
            // bin from each member's own max/min, classified against a literal zero baseline. A
            // track whose groups are only ever on one side everywhere gets the usual unsuffixed
            // N/average/SD/SEM columns; one with any mixed-sign bin gets both a ".pos.*" and a
            // ".neg.*" set, each reading only its own group's entry via signFilter.
            SignRange sign = classifyWholeRangeSign(dataTrack, frames, AVERAGE_ENVELOPE_BASELINE);
            if (sign.hasPositive() && sign.hasNegative()) {
                addAverageStatColumns(values, usedNames, prefix + ".pos", true);
                addAverageStatColumns(values, usedNames, prefix + ".neg", false);
            } else if (sign.hasNegative()) {
                addAverageStatColumns(values, usedNames, prefix, false);
            } else {
                addAverageStatColumns(values, usedNames, prefix, true);
            }
        } else if (dataTrack instanceof AverageErrorBarTrack) {
            values.add(new ValueColumn(uniqueName(usedNames, prefix + ".N"), ValueKind.N));
            values.add(new ValueColumn(uniqueName(usedNames, prefix + ".average"), ValueKind.AVERAGE));
            values.add(new ValueColumn(uniqueName(usedNames, prefix + ".SD"), ValueKind.SD));
            values.add(new ValueColumn(uniqueName(usedNames, prefix + ".SEM"), ValueKind.SEM));
        } else if (dataTrack.getWindowFunction() == WindowFunction.none) {
            // "None" windowing shows the raw envelope rather than a single averaged value (see
            // DataTrack/NumericTrackBinner.binEnvelope): a bin whose raw values are all on one
            // side of the baseline reports that side's extreme, a bin straddling the baseline
            // reports both. The column layout is decided once, from the sign of every value
            // anywhere in the exported range, so every row of the file has the same columns -
            // a track that's positive-only (or negative-only) everywhere gets a single plain
            // column (unsuffixed, like any other single-value track); a track with any
            // mixed-sign bin gets both Pos and Neg columns, with NA in whichever a given bin
            // doesn't have.
            SignRange sign = classifyWholeRangeSign(dataTrack, frames, baseline);
            if (sign.hasPositive() && sign.hasNegative()) {
                values.add(new ValueColumn(uniqueName(usedNames, prefix + ".pos"), ValueKind.POS));
                values.add(new ValueColumn(uniqueName(usedNames, prefix + ".neg"), ValueKind.NEG));
            } else if (sign.hasNegative()) {
                values.add(new ValueColumn(uniqueName(usedNames, prefix), ValueKind.NEG));
            } else {
                values.add(new ValueColumn(uniqueName(usedNames, prefix), ValueKind.POS));
            }
        } else {
            values.add(new ValueColumn(uniqueName(usedNames, prefix), ValueKind.VALUE));
        }
        result.add(new ExportTrack(sourceHeader, displayTrack, dataTrack,
                memberIndex, List.copyOf(values), includeSource, baseline, dataTrack.getWindowFunction()));
    }

    /**
     * The literal zero {@code AverageErrorBarDataSource} classifies each member's max/min
     * against when Windowing Function is None (see its class javadoc) - not the display data
     * range's baseline, since whole-range column-layout classification must agree with how the
     * data source itself already decided which bins get a positive-group and/or negative-group
     * entry.
     */
    private static final float AVERAGE_ENVELOPE_BASELINE = 0f;

    private static void addAverageStatColumns(List<ValueColumn> values, Map<String, Integer> usedNames,
                                              String prefix, Boolean positiveGroup) {
        values.add(new ValueColumn(uniqueName(usedNames, prefix + ".N"), ValueKind.N, positiveGroup));
        values.add(new ValueColumn(uniqueName(usedNames, prefix + ".average"), ValueKind.AVERAGE, positiveGroup));
        values.add(new ValueColumn(uniqueName(usedNames, prefix + ".SD"), ValueKind.SD, positiveGroup));
        values.add(new ValueColumn(uniqueName(usedNames, prefix + ".SEM"), ValueKind.SEM, positiveGroup));
    }

    private record SignRange(boolean hasPositive, boolean hasNegative) {
    }

    private static SignRange classifyWholeRangeSign(DataTrack dataTrack, List<ReferenceFrame> frames, float baseline) {
        boolean hasPositive = false;
        boolean hasNegative = false;
        for (ReferenceFrame frame : frames) {
            RegionDisplayCoordinateMap coordinateMap = frame.getRegionDisplayCoordinateMap();
            int rangeStart = Math.max(0, (int) Math.floor(coordinateMap.getGenomicRenderStart()));
            int rangeEnd = Math.max(rangeStart + 1, (int) Math.ceil(coordinateMap.getGenomicRenderEnd()));
            List<LocusScore> scores = dataTrack.getRawSummaryScores(
                    frame.getChrName(), rangeStart, rangeEnd, frame.getZoom()).getFeatures();
            if (scores == null) continue;
            for (LocusScore score : scores) {
                if (score instanceof AverageErrorLocusScore average
                        && average.getMemberValues() != null) {
                    if (average.getGroup() == AverageErrorLocusScore.Group.POSITIVE) {
                        hasPositive = true;
                    } else if (average.getGroup() == AverageErrorLocusScore.Group.NEGATIVE) {
                        hasNegative = true;
                    } else {
                        for (float memberValue : average.getMemberValues()) {
                            if (Float.isNaN(memberValue)) continue;
                            if (memberValue > baseline) hasPositive = true;
                            else if (memberValue < baseline) hasNegative = true;
                        }
                    }
                    if (hasPositive && hasNegative) return new SignRange(true, true);
                    continue;
                }
                float value = score.getScore();
                if (Float.isNaN(value)) continue;
                if (value > baseline) hasPositive = true;
                else if (value < baseline) hasNegative = true;
                if (hasPositive && hasNegative) return new SignRange(true, true);
            }
        }
        return new SignRange(hasPositive, hasNegative);
    }

    private static String uniqueName(Map<String, Integer> usedNames, String requestedName) {
        int count = usedNames.merge(requestedName, 1, Integer::sum);
        return count == 1 ? requestedName : requestedName + "." + count;
    }

    private static boolean hasSourceTransform(Track track, List<ReferenceFrame> frames, IGV igv) {
        if (track == null || track.getId() == null) return false;
        for (ReferenceFrame frame : frames) {
            Collection<RegionOfInterest> regions = igv.getSession().getRegionsOfInterest(frame.getChrName());
            if (regions == null) continue;
            RegionDisplayCoordinateMap map = frame.getRegionDisplayCoordinateMap();
            double start = map.getGenomicRenderStart();
            double end = map.getGenomicRenderEnd();
            for (RegionOfInterest region : regions) {
                RegionDisplayRule rule = region.getDisplayRule();
                if (rule == null || rule.isCollapsed()
                        || region.getEnd() <= start || region.getStart() >= end) continue;
                TrackRegionOverride override = rule.getTrackOverride(track.getId());
                if (override != null && (override.isReverseX() || override.exchangesTrackPair())) return true;
            }
        }
        return false;
    }

    private static SourceMapping resolveSource(ExportTrack exportTrack, DisplayBinPlan.Bin bin,
                                               Collection<RegionOfInterest> regions,
                                               List<Track> allTracks) {
        String chr = regions == null || regions.isEmpty()
                ? "" : regions.iterator().next().getChr();
        List<RegionOfInterest> covering = regions == null ? List.of() : regions.stream()
                .filter(region -> region.getDisplayRule() != null && !region.getDisplayRule().isCollapsed())
                .filter(region -> region.getStart() <= bin.start() + (bin.end() - bin.start()) / 2.0
                        && region.getEnd() > bin.start() + (bin.end() - bin.start()) / 2.0)
                .sorted(Comparator.comparingInt(region -> region.getDisplayRule().getPriority()))
                .toList();
        List<TrackRegionOverride> overrides = new ArrayList<>();
        boolean reversed = false;
        double inversionSum = 0;
        for (RegionOfInterest region : covering) {
            TrackRegionOverride override = region.getDisplayRule().getTrackOverride(exportTrack.displayTrack.getId());
            overrides.add(override);
            if (override != null && override.isReverseX()) {
                reversed = !reversed;
                inversionSum = region.getStart() + (double) region.getEnd() - inversionSum;
            }
        }
        TrackRegionOverride effective = TrackRegionOverride.compose(overrides);
        int sourceStart = bin.start();
        int sourceEnd = bin.end();
        if (effective.isReverseX() && reversed) {
            sourceStart = (int) Math.floor(inversionSum - bin.end());
            sourceEnd = (int) Math.ceil(inversionSum - bin.start());
        }

        Track sourceTrack = exportTrack.displayTrack;
        if (effective.exchangesTrackPair() && TrackPairing.isPaired(exportTrack.displayTrack)) {
            Track partner = TrackPairing.findPartner(exportTrack.displayTrack, allTracks);
            if (partner != null) sourceTrack = partner;
        }
        boolean pairExchanged = sourceTrack != exportTrack.displayTrack;
        DataTrack sourceDataTrack = sourceDataTrack(exportTrack, sourceTrack);
        return new SourceMapping(sourceTrack, sourceDataTrack, chr, sourceStart, sourceEnd,
                effective.isReverseX(), pairExchanged);
    }

    private static DataTrack sourceDataTrack(ExportTrack exportTrack, Track sourceTrack) {
        if (sourceTrack == exportTrack.displayTrack) return exportTrack.dataTrack;
        if (sourceTrack instanceof MergedTracks merged) {
            List<DataTrack> members = merged.getMemberTracks();
            int index = exportTrack.memberIndex;
            return index >= 0 && index < members.size() ? members.get(index)
                    : (members.isEmpty() ? null : members.get(0));
        }
        return sourceTrack instanceof DataTrack dataTrack ? dataTrack : null;
    }

    private static Map<DataTrack, List<LocusScore>> loadScores(
            Map<DataTrack, List<Interval>> requests, String chr, int zoom) {
        Map<DataTrack, List<LocusScore>> result = new IdentityHashMap<>();
        for (Map.Entry<DataTrack, List<Interval>> entry : requests.entrySet()) {
            List<LocusScore> scores = new ArrayList<>();
            for (Interval interval : mergeIntervals(entry.getValue())) {
                List<LocusScore> loaded = entry.getKey()
                        .getRawSummaryScores(chr, interval.start, interval.end, zoom).getFeatures();
                if (loaded != null) scores.addAll(loaded);
            }
            result.put(entry.getKey(), scores);
        }
        return result;
    }

    static List<Interval> mergeIntervals(List<Interval> intervals) {
        if (intervals == null || intervals.isEmpty()) return List.of();
        List<Interval> sorted = intervals.stream()
                .filter(interval -> interval.end > interval.start)
                .sorted(Comparator.comparingInt(Interval::start)).toList();
        if (sorted.isEmpty()) return List.of();
        List<Interval> result = new ArrayList<>();
        int start = sorted.get(0).start;
        int end = sorted.get(0).end;
        for (int i = 1; i < sorted.size(); i++) {
            Interval next = sorted.get(i);
            if (next.start <= end) end = Math.max(end, next.end);
            else {
                result.add(new Interval(start, end));
                start = next.start;
                end = next.end;
            }
        }
        result.add(new Interval(start, end));
        return result;
    }

    private static List<OutputRow> createOutputRows(List<DisplayBinPlan.Bin> bins,
                                                    Collection<RegionOfInterest> regions,
                                                    String chr, int rangeStart, int rangeEnd) {
        List<OutputRow> rows = new ArrayList<>();
        for (int i = 0; i < bins.size(); i++) {
            DisplayBinPlan.Bin bin = bins.get(i);
            rows.add(new OutputRow(bin.start(), bin.end(), "", i));
        }
        List<Interval> collapsed = RegionDisplayBoundarySource.getVisibleCollapsedIntervals(
                        regions, chr, rangeStart, rangeEnd).stream()
                .map(interval -> new Interval(interval.start(), interval.end())).toList();
        for (Interval interval : mergeIntervals(collapsed)) {
            rows.add(new OutputRow(interval.start(), interval.end(),
                    "region_collapse_deleted", -1));
        }
        rows.sort(Comparator.comparingInt(OutputRow::start)
                .thenComparingInt(OutputRow::binIndex));
        return rows;
    }

    private static String sourceValue(SourceMapping mapping) {
        if (mapping == null || !mapping.transformed()) return "NA";
        String sourceTrackName = mapping.pairExchanged && mapping.sourceTrack != null
                ? mapping.sourceTrack.getName() : null;
        return formatSource(sourceTrackName, mapping.chr, mapping.start, mapping.end);
    }

    static String formatSource(String sourceTrackName, String chr, int start, int end) {
        String prefix = sourceTrackName == null ? "" : "[" + clean(sourceTrackName) + "]";
        return prefix + clean(chr) + ":" + start + "-" + end;
    }

    private static void writeMissingColumns(BufferedWriter writer, boolean includeSequence,
                                            boolean includeSequenceSource,
                                            List<ExportTrack> tracks) throws IOException {
        if (includeSequence) {
            if (includeSequenceSource) writer.write("\tNA");
            writer.write("\tNA");
        }
        for (ExportTrack track : tracks) {
            if (track.includeSource) writer.write("\tNA");
            for (int i = 0; i < track.values.size(); i++) writer.write("\tNA");
        }
    }

    static String valueFor(List<LocusScore> scores, int binStart, int binEnd, ValueKind kind, float baseline,
                           Boolean signFilter) {
        return valueFor(scores, binStart, binEnd, kind, baseline, signFilter, -1,
                signFilter == null ? WindowFunction.mean : WindowFunction.none);
    }

    static String valueFor(List<LocusScore> scores, int binStart, int binEnd, ValueKind kind, float baseline,
                           Boolean signFilter, int memberIndex) {
        return valueFor(scores, binStart, binEnd, kind, baseline, signFilter, memberIndex,
                signFilter == null ? WindowFunction.mean : WindowFunction.none);
    }

    static String valueFor(List<LocusScore> scores, int binStart, int binEnd, ValueKind kind, float baseline,
                           Boolean signFilter, int memberIndex, WindowFunction windowFunction) {
        if (scores == null || scores.isEmpty()) return null;

        if (kind == ValueKind.POS || kind == ValueKind.NEG) {
            // "None" windowing envelope: the extreme (not a weighted average) among raw values
            // strictly on this column's side of the baseline overlapping the bin - matches
            // NumericTrackBinner.binEnvelope's per-bin classification used for display, so
            // export and display never disagree about which bins have a pos/neg value at all.
            Float extreme = null;
            for (LocusScore score : scores) {
                if (overlap(score, binStart, binEnd) <= 0) continue;
                float value = score.getScore();
                if (Float.isNaN(value)) continue;
                if (kind == ValueKind.POS && value > baseline) {
                    if (extreme == null || value > extreme) extreme = value;
                } else if (kind == ValueKind.NEG && value < baseline) {
                    if (extreme == null || value < extreme) extreme = value;
                }
            }
            return extreme == null ? null : String.format(Locale.ROOT, "%.9g", extreme);
        }

        if (signFilter != null) {
            // Reuse the display binner so source values and N/average/SD/SEM all come from each
            // member's max/min within this exact TSV bin, in the same order as on screen.
            AverageErrorLocusScore peak = selectAveragePeak(
                    scores, binStart, binEnd, signFilter, baseline);
            if (peak == null) return null;
            return switch (kind) {
                case MEMBER -> formatNumber(peak.getMemberValue(memberIndex));
                case N -> Integer.toString(peak.getN());
                case AVERAGE -> String.format(Locale.ROOT, "%.9g", peak.getScore());
                case SD -> Float.isNaN(peak.getSd()) ? null : String.format(Locale.ROOT, "%.9g", peak.getSd());
                case SEM -> Float.isNaN(peak.getSem()) ? null : String.format(Locale.ROOT, "%.9g", peak.getSem());
                default -> null;
            };
        }

        if (kind == ValueKind.MEMBER || kind == ValueKind.N || kind == ValueKind.AVERAGE
                || kind == ValueKind.SD || kind == ValueKind.SEM) {
            AverageErrorLocusScore binnedAverage = binAverageScore(
                    scores, binStart, binEnd, windowFunction);
            if (binnedAverage != null && binnedAverage.getMemberValues() != null) {
                float[] memberValues = binnedAverage.getMemberValues();
                if (kind == ValueKind.MEMBER) {
                    return memberIndex < 0 || memberIndex >= memberValues.length
                            ? null : formatNumber(memberValues[memberIndex]);
                }
                return switch (kind) {
                    case N -> Integer.toString(binnedAverage.getN());
                    case AVERAGE -> formatNumber(binnedAverage.getScore());
                    case SD -> formatNumber(binnedAverage.getSd());
                    case SEM -> formatNumber(binnedAverage.getSem());
                    default -> null;
                };
            }
        }

        if (kind == ValueKind.VALUE) {
            List<LocusScore> binned = NumericTrackBinner.binRaw(scores,
                    DisplayBinPlan.create(binStart, binEnd, 1, List.of()), windowFunction);
            return binned.isEmpty() ? null : formatNumber(binned.get(0).getScore());
        }

        if (kind == ValueKind.N) {
            int n = 0;
            boolean found = false;
            for (LocusScore score : scores) {
                if (overlap(score, binStart, binEnd) > 0 && score instanceof AverageErrorLocusScore average) {
                    n = Math.max(n, average.getN());
                    found = true;
                }
            }
            return found ? Integer.toString(n) : null;
        }

        double weightedSum = 0;
        double totalWeight = 0;
        for (LocusScore score : scores) {
            int weight = overlap(score, binStart, binEnd);
            if (weight <= 0) continue;
            float value;
            if (kind == ValueKind.VALUE) {
                value = score.getScore();
            } else if (score instanceof AverageErrorLocusScore average) {
                value = switch (kind) {
                    case AVERAGE -> average.getScore();
                    case SD -> average.getSd();
                    case SEM -> average.getSem();
                    default -> Float.NaN;
                };
            } else {
                continue;
            }
            if (!Float.isNaN(value)) {
                weightedSum += value * weight;
                totalWeight += weight;
            }
        }
        return totalWeight == 0 ? null : String.format(Locale.ROOT, "%.9g", weightedSum / totalWeight);
    }

    private static AverageErrorLocusScore binAverageScore(List<LocusScore> scores,
                                                           int binStart, int binEnd,
                                                           WindowFunction windowFunction) {
        List<LocusScore> binned = NumericTrackBinner.binAverage(scores,
                DisplayBinPlan.create(binStart, binEnd, 1, List.of()), windowFunction);
        return binned.size() == 1 && binned.get(0) instanceof AverageErrorLocusScore average
                ? average : null;
    }

    private static AverageErrorLocusScore selectAveragePeak(List<LocusScore> scores,
                                                             int binStart, int binEnd,
                                                             boolean positiveGroup,
                                                             float baseline) {
        List<LocusScore> binned = NumericTrackBinner.binAverageEnvelope(scores,
                DisplayBinPlan.create(binStart, binEnd, 1, List.of()));
        AverageErrorLocusScore peak = null;
        for (LocusScore score : binned) {
            if (!(score instanceof AverageErrorLocusScore average)
                    || !matchesSign(average, positiveGroup, baseline)) {
                continue;
            }
            if (peak == null || Math.abs(average.getScore()) > Math.abs(peak.getScore())) {
                peak = average;
            }
        }
        return peak;
    }

    private static String formatNumber(float value) {
        return Float.isNaN(value) ? null : String.format(Locale.ROOT, "%.9g", value);
    }

    /**
     * {@code signFilter == null} means no ambiguity is possible (see {@link ValueColumn}) - every
     * entry matches. Otherwise only the entry on the requested side of the baseline matches,
     * disambiguating a bin with both a positive-group and a negative-group
     * {@code AverageErrorLocusScore} (Windowing Function None on an "Average With Error Bar"
     * track - see {@code AverageErrorBarDataSource}).
     */
    private static boolean matchesSign(AverageErrorLocusScore score, Boolean signFilter, float baseline) {
        if (signFilter == null) return true;
        if (score.getGroup() == AverageErrorLocusScore.Group.POSITIVE) return signFilter;
        if (score.getGroup() == AverageErrorLocusScore.Group.NEGATIVE) return !signFilter;
        return signFilter ? score.getScore() > baseline : score.getScore() < baseline;
    }

    private static int overlap(LocusScore score, int start, int end) {
        int scoreStart = score.getStart();
        int scoreEnd = score.getEnd();
        if (scoreEnd <= scoreStart) {
            return scoreStart >= start && scoreStart < end ? 1 : 0;
        }
        return Math.max(0, Math.min(end, scoreEnd) - Math.max(start, scoreStart));
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    static String cleanHeader(String value) {
        return clean(value).trim().replaceAll("\\s+", "_");
    }
}
