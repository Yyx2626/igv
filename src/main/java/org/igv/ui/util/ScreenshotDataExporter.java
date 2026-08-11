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
import org.igv.track.RegionDisplayBinPlanner;
import org.igv.track.SequenceTrack;
import org.igv.track.Track;
import org.igv.track.TrackPairing;
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

    enum ValueKind {VALUE, N, AVERAGE, SD, SEM}

    private record ValueColumn(String header, ValueKind kind) {
    }

    private record ExportTrack(String sourceHeader, Track displayTrack, DataTrack dataTrack,
                               int memberIndex, List<ValueColumn> values, boolean includeSource) {
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
                            null, -1, List.of(), includeSequenceSource);
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
                                    mapping.start, mapping.end, value.kind);
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
        for (Track track : igv.getAllTracks()) {
            if (!track.isVisible() || !(track instanceof DataTrack dataTrack)) continue;
            boolean includeSource = hasSourceTransform(track, frames, igv);
            if (dataTrack instanceof MergedTracks merged) {
                List<DataTrack> members = merged.getMemberTracks();
                for (int i = 0; i < members.size(); i++) {
                    DataTrack member = members.get(i);
                    addExportTrack(result, usedNames, track, member, i,
                            dataTrack.getName() + "." + member.getName(), includeSource);
                }
            } else {
                addExportTrack(result, usedNames, track, dataTrack, -1,
                        dataTrack.getName(), includeSource);
            }
        }
        return result;
    }

    private static void addExportTrack(List<ExportTrack> result, Map<String, Integer> usedNames,
                                       Track displayTrack, DataTrack dataTrack, int memberIndex,
                                       String prefix, boolean includeSource) {
        String sourceHeader = uniqueName(usedNames, prefix + ".source");
        List<ValueColumn> values = new ArrayList<>();
        if (dataTrack instanceof AverageErrorBarTrack) {
            values.add(new ValueColumn(uniqueName(usedNames, prefix + ".N"), ValueKind.N));
            values.add(new ValueColumn(uniqueName(usedNames, prefix + ".average"), ValueKind.AVERAGE));
            values.add(new ValueColumn(uniqueName(usedNames, prefix + ".SD"), ValueKind.SD));
            values.add(new ValueColumn(uniqueName(usedNames, prefix + ".SEM"), ValueKind.SEM));
        } else {
            values.add(new ValueColumn(uniqueName(usedNames, prefix), ValueKind.VALUE));
        }
        result.add(new ExportTrack(sourceHeader, displayTrack, dataTrack,
                memberIndex, List.copyOf(values), includeSource));
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
                        .getSummaryScores(chr, interval.start, interval.end, zoom).getFeatures();
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

    static String valueFor(List<LocusScore> scores, int binStart, int binEnd, ValueKind kind) {
        if (scores == null || scores.isEmpty()) return null;
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
            if (kind == ValueKind.VALUE || kind == ValueKind.AVERAGE) {
                value = score.getScore();
            } else if (score instanceof AverageErrorLocusScore average) {
                value = kind == ValueKind.SD ? average.getSd() : average.getSem();
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
