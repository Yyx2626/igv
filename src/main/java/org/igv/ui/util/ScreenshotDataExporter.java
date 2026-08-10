package org.igv.ui.util;

import org.igv.data.AverageErrorLocusScore;
import org.igv.feature.Strand;
import org.igv.feature.LocusScore;
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
import org.igv.ui.IGV;
import org.igv.ui.panel.FrameManager;
import org.igv.ui.panel.ReferenceFrame;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
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

    private record Column(String header, DataTrack track, ValueKind kind) {
    }

    private record SequenceSlice(int rangeStart, byte[] bases, Strand strand) {
    }

    public static void export(IGV igv, File file, int requestedBins) throws IOException {
        List<ReferenceFrame> frames = FrameManager.getFrames().stream().filter(ReferenceFrame::isVisible).toList();
        List<Column> columns = buildColumns(igv.getAllTracks());
        Map<ReferenceFrame, SequenceSlice> sequenceSlices = loadVisibleSequences(igv, frames);
        boolean includeSequence = !sequenceSlices.isEmpty();
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write("chr\tstart\tend");
            if (frames.size() > 1) writer.write("\tpanel");
            if (includeSequence) {
                Strand strand = sequenceSlices.values().iterator().next().strand;
                writer.write("\t" + sequenceHeader(strand));
            }
            for (Column column : columns) writer.write("\t" + clean(column.header));
            writer.newLine();

            for (ReferenceFrame frame : frames) {
                int rangeStart = Math.max(0, (int) Math.floor(frame.getOrigin()));
                int rangeEnd = Math.max(rangeStart + 1, (int) Math.ceil(frame.getEnd()));
                int span = rangeEnd - rangeStart;
                SequenceSlice sequenceSlice = sequenceSlices.get(frame);
                int targetBins = sequenceSlice == null
                        ? Math.max(1, Math.min(Math.max(1, requestedBins), span))
                        : span;
                DisplayBinPlan binPlan = RegionDisplayBinPlanner.create(
                        frame.getChrName(), rangeStart, rangeEnd, targetBins);
                List<DisplayBinPlan.Bin> bins = binPlan.getBins();
                Map<DataTrack, List<LocusScore>> scoreCache = new HashMap<>();
                for (Column column : columns) {
                    scoreCache.computeIfAbsent(column.track,
                            track -> track.getSummaryScores(frame.getChrName(), rangeStart, rangeEnd, frame.getZoom()).getFeatures());
                }

                for (int displayIndex = 0; displayIndex < bins.size(); displayIndex++) {
                    int binIndex = frame.isInverted() ? bins.size() - 1 - displayIndex : displayIndex;
                    DisplayBinPlan.Bin bin = bins.get(binIndex);
                    int binStart = bin.start();
                    int binEnd = bin.end();
                    writer.write(clean(frame.getChrName()) + "\t" + binStart + "\t" + binEnd);
                    if (frames.size() > 1) writer.write("\t" + clean(frame.getName()));
                    if (includeSequence) {
                        String base = sequenceValue(sequenceSlice, binStart, binEnd);
                        writer.write("\t" + (base == null ? "NA" : base));
                    }
                    for (Column column : columns) {
                        writer.write('\t');
                        String value = valueFor(scoreCache.get(column.track), binStart, binEnd, column.kind);
                        writer.write(value == null ? "NA" : value);
                    }
                    writer.newLine();
                }
            }
        }
    }

    private static Map<ReferenceFrame, SequenceSlice> loadVisibleSequences(
            IGV igv, List<ReferenceFrame> frames) {
        Map<ReferenceFrame, SequenceSlice> result = new IdentityHashMap<>();
        SequenceTrack sequenceTrack = igv.getSequenceTrack();
        Genome genome = GenomeManager.getInstance().getCurrentGenome();
        if (sequenceTrack == null || genome == null) return result;

        int resolutionThreshold = PreferencesManager.getPreferences().getAsInt(Constants.MAX_SEQUENCE_RESOLUTION);
        for (ReferenceFrame frame : frames) {
            if (frame.getScale() >= resolutionThreshold || frame.getChrName().equals(org.igv.Globals.CHR_ALL)) {
                continue;
            }
            int rangeStart = Math.max(0, (int) Math.floor(frame.getOrigin()));
            int rangeEnd = Math.max(rangeStart + 1, (int) Math.ceil(frame.getEnd()));
            byte[] bases = genome.getSequence(frame.getChrName(), rangeStart, rangeEnd);
            if (bases != null && bases.length > 0) {
                result.put(frame, new SequenceSlice(rangeStart, bases, sequenceTrack.getStrand()));
            }
        }
        return result;
    }

    private static String sequenceValue(SequenceSlice slice, int binStart, int binEnd) {
        if (slice == null || binEnd - binStart != 1) return null;
        int index = binStart - slice.rangeStart;
        if (index < 0 || index >= slice.bases.length) return null;
        char base = (char) slice.bases[index];
        if (slice.strand == Strand.NEGATIVE) base = SeqUtils.complementChar(base);
        return Character.toString(base);
    }

    static String sequenceValue(byte[] bases, int rangeStart, int binStart, int binEnd, Strand strand) {
        return sequenceValue(new SequenceSlice(rangeStart, bases, strand), binStart, binEnd);
    }

    static String sequenceHeader(Strand strand) {
        return strand == Strand.NEGATIVE ? "Sequence_genomic_minus" : "Sequence_genomic_plus";
    }

    private static List<Column> buildColumns(List<Track> tracks) {
        List<Column> result = new ArrayList<>();
        Map<String, Integer> usedNames = new LinkedHashMap<>();
        for (Track track : tracks) {
            if (!track.isVisible() || !(track instanceof DataTrack dataTrack)) continue;
            addTrackColumns(result, usedNames, dataTrack.getName(), dataTrack);
        }
        return result;
    }

    private static void addTrackColumns(List<Column> columns, Map<String, Integer> usedNames,
                                        String prefix, DataTrack track) {
        if (track instanceof MergedTracks merged) {
            for (DataTrack member : merged.getMemberTracks()) {
                addTrackColumns(columns, usedNames, prefix + " :: " + member.getName(), member);
            }
        } else if (track instanceof AverageErrorBarTrack) {
            add(columns, usedNames, prefix + " N", track, ValueKind.N);
            add(columns, usedNames, prefix + " average", track, ValueKind.AVERAGE);
            add(columns, usedNames, prefix + " SD", track, ValueKind.SD);
            add(columns, usedNames, prefix + " SEM", track, ValueKind.SEM);
        } else {
            add(columns, usedNames, prefix, track, ValueKind.VALUE);
        }
    }

    private static void add(List<Column> columns, Map<String, Integer> usedNames,
                            String requestedName, DataTrack track, ValueKind kind) {
        int count = usedNames.merge(requestedName, 1, Integer::sum);
        String uniqueName = count == 1 ? requestedName : requestedName + " (" + count + ")";
        columns.add(new Column(uniqueName, track, kind));
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
}
