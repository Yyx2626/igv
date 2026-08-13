package org.igv.track;

import org.igv.data.AverageErrorLocusScore;
import org.igv.data.BasicScore;
import org.igv.feature.LocusScore;

import java.util.ArrayList;
import java.util.List;

/** Resamples visible numeric data into equal genomic bins for display. */
public final class NumericTrackBinner {

    private NumericTrackBinner() {
    }

    static List<LocusScore> bin(List<LocusScore> scores, int rangeStart, int rangeEnd, int requestedBins) {
        return bin(scores, DisplayBinPlan.create(rangeStart, rangeEnd, requestedBins, List.of()));
    }

    static List<LocusScore> bin(List<LocusScore> scores, DisplayBinPlan plan) {
        if (scores == null || scores.isEmpty() || plan == null) {
            return scores;
        }
        if (plan.getBins().isEmpty()) return List.of();
        List<LocusScore> result = new ArrayList<>(plan.getBins().size());
        int firstCandidate = 0;

        for (DisplayBinPlan.Bin bin : plan.getBins()) {
            int binStart = bin.start();
            int binEnd = bin.end();

            while (firstCandidate < scores.size() && scoreEnd(scores.get(firstCandidate)) <= binStart) {
                firstCandidate++;
            }

            double valueSum = 0;
            double valueWeight = 0;
            double sdSum = 0;
            double sdWeight = 0;
            double semSum = 0;
            double semWeight = 0;
            int n = 0;
            boolean averageScore = false;

            for (int i = firstCandidate; i < scores.size(); i++) {
                LocusScore score = scores.get(i);
                if (score.getStart() >= binEnd) break;
                int weight = overlap(score, binStart, binEnd);
                if (weight <= 0) continue;

                float value = score.getScore();
                if (!Float.isNaN(value)) {
                    valueSum += value * weight;
                    valueWeight += weight;
                }
                if (score instanceof AverageErrorLocusScore errorScore) {
                    averageScore = true;
                    n = Math.max(n, errorScore.getN());
                    if (!Float.isNaN(errorScore.getSd())) {
                        sdSum += errorScore.getSd() * weight;
                        sdWeight += weight;
                    }
                    if (!Float.isNaN(errorScore.getSem())) {
                        semSum += errorScore.getSem() * weight;
                        semWeight += weight;
                    }
                }
            }

            if (valueWeight == 0) continue;
            float value = (float) (valueSum / valueWeight);
            if (averageScore) {
                float sd = sdWeight == 0 ? Float.NaN : (float) (sdSum / sdWeight);
                float sem = semWeight == 0 ? Float.NaN : (float) (semSum / semWeight);
                result.add(new AverageErrorLocusScore(binStart, binEnd, value, sd, sem, n));
            } else {
                result.add(new BasicScore(binStart, binEnd, value));
            }
        }
        return result;
    }

    /**
     * Resamples raw (unaggregated) scores into equal genomic bins the same way {@link #bin} does,
     * but instead of a single weighted mean per bin, tracks the largest above-baseline value and
     * the smallest below-baseline value separately - the "None" windowing display/export: a bin
     * whose raw values are all on one side of the baseline gets a single entry (that side's
     * extreme); a bin straddling the baseline gets one entry per side, so both excursions stay
     * visible instead of one masking the other. A bin whose raw values never cross strictly to
     * either side of the baseline (all exactly at it) gets no entry at all - there's nothing to
     * show. Shared with {@code ScreenshotDataExporter} (hence public) so TSV export classifies
     * bins with the exact same rule display uses, rather than a second, possibly-diverging copy.
     */
    public static List<LocusScore> binEnvelope(List<LocusScore> scores, DisplayBinPlan plan, float baseline) {
        if (scores == null || scores.isEmpty() || plan == null) {
            return scores;
        }
        if (plan.getBins().isEmpty()) return List.of();
        List<LocusScore> result = new ArrayList<>(plan.getBins().size());
        int firstCandidate = 0;

        for (DisplayBinPlan.Bin bin : plan.getBins()) {
            int binStart = bin.start();
            int binEnd = bin.end();

            while (firstCandidate < scores.size() && scoreEnd(scores.get(firstCandidate)) <= binStart) {
                firstCandidate++;
            }

            float posMax = Float.NaN;
            float negMin = Float.NaN;

            for (int i = firstCandidate; i < scores.size(); i++) {
                LocusScore score = scores.get(i);
                if (score.getStart() >= binEnd) break;
                if (overlap(score, binStart, binEnd) <= 0) continue;

                float value = score.getScore();
                if (Float.isNaN(value)) continue;
                if (value > baseline) {
                    if (Float.isNaN(posMax) || value > posMax) posMax = value;
                } else if (value < baseline) {
                    if (Float.isNaN(negMin) || value < negMin) negMin = value;
                }
            }

            if (!Float.isNaN(posMax)) result.add(new BasicScore(binStart, binEnd, posMax));
            if (!Float.isNaN(negMin)) result.add(new BasicScore(binStart, binEnd, negMin));
        }
        return result;
    }

    /**
     * The {@link #binEnvelope} equivalent for an already-averaged track (an "Average With
     * Error Bar" track whose Windowing Function is None, so its scores are
     * {@code AverageErrorLocusScore}s carrying their own mean/SD/SEM/N - see
     * {@code AverageErrorBarDataSource}'s class javadoc). {@link #binEnvelope} itself can't be
     * reused here: it only ever reads a plain float per raw score and re-wraps the extreme it
     * finds in a bare {@code BasicScore}, which would silently discard the SD/SEM/N a
     * caller needs to draw the error bar at all.
     * <p>
     * Unlike {@link #bin}'s overlap-weighted averaging (correct for combining several native
     * mean-windowed entries into a wider bin), a bin here can span several native entries that
     * are themselves already each-member's-own peak within their own (narrower) span - weight-
     * averaging those together would dilute toward zero for every native entry's width the bin
     * also covers with no data at all (bigwig files only store covered positions - a real gap,
     * not a stored zero), understating the bin's true peak the wider it is relative to the
     * native resolution. Instead, each side's group keeps only the single native entry with the
     * largest-magnitude mean and reports that entry's own mean/SD/SEM/N as-is - the same "find
     * the extreme, don't average toward it" rule {@link #binEnvelope} already applies to plain
     * raw values, kept separate by which side of zero a native entry's own mean already falls on
     * so a bin spanning both never blends a positive-group entry with a negative-group one.
     */
    static List<LocusScore> binAverageEnvelope(List<LocusScore> scores, DisplayBinPlan plan) {
        if (scores == null || scores.isEmpty() || plan == null) {
            return scores;
        }
        if (plan.getBins().isEmpty()) return List.of();
        List<LocusScore> result = new ArrayList<>(plan.getBins().size());
        int firstCandidate = 0;

        for (DisplayBinPlan.Bin bin : plan.getBins()) {
            int binStart = bin.start();
            int binEnd = bin.end();

            while (firstCandidate < scores.size() && scoreEnd(scores.get(firstCandidate)) <= binStart) {
                firstCandidate++;
            }

            AverageErrorLocusScore posBest = null;
            AverageErrorLocusScore negBest = null;

            for (int i = firstCandidate; i < scores.size(); i++) {
                LocusScore score = scores.get(i);
                if (score.getStart() >= binEnd) break;
                if (overlap(score, binStart, binEnd) <= 0 || !(score instanceof AverageErrorLocusScore errorScore)) {
                    continue;
                }

                float value = errorScore.getScore();
                if (Float.isNaN(value)) continue;
                if (value > 0) {
                    if (posBest == null || value > posBest.getScore()) posBest = errorScore;
                } else if (value < 0) {
                    if (negBest == null || value < negBest.getScore()) negBest = errorScore;
                }
            }

            if (posBest != null) result.add(copyInto(posBest, binStart, binEnd));
            if (negBest != null) result.add(copyInto(negBest, binStart, binEnd));
        }
        return result;
    }

    private static AverageErrorLocusScore copyInto(AverageErrorLocusScore source, int start, int end) {
        return new AverageErrorLocusScore(start, end, source.getScore(), source.getSd(), source.getSem(), source.getN());
    }

    private static int overlap(LocusScore score, int start, int end) {
        int scoreEnd = scoreEnd(score);
        if (scoreEnd <= score.getStart()) {
            return score.getStart() >= start && score.getStart() < end ? 1 : 0;
        }
        return Math.max(0, Math.min(end, scoreEnd) - Math.max(start, score.getStart()));
    }

    private static int scoreEnd(LocusScore score) {
        return Math.max(score.getStart() + 1, score.getEnd());
    }
}
