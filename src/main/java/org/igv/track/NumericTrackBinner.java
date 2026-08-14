package org.igv.track;

import org.igv.data.AverageErrorLocusScore;
import org.igv.data.AverageErrorStatistics;
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
            double[] memberSums = null;
            double[] memberWeights = null;
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
                    float[] memberValues = errorScore.getMemberValues();
                    if (memberValues != null) {
                        if (memberSums == null) {
                            memberSums = new double[memberValues.length];
                            memberWeights = new double[memberValues.length];
                        }
                        if (memberValues.length == memberSums.length) {
                            for (int member = 0; member < memberValues.length; member++) {
                                if (!Float.isNaN(memberValues[member])) {
                                    memberSums[member] += memberValues[member] * weight;
                                    memberWeights[member] += weight;
                                }
                            }
                        }
                    }
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
                float[] memberValues = aggregatedMemberValues(memberSums, memberWeights);
                if (memberValues != null) {
                    float[] stats = AverageErrorStatistics.calculate(memberValues);
                    value = stats[0];
                    sd = stats[1];
                    sem = stats[2];
                    n = memberValues.length;
                }
                result.add(new AverageErrorLocusScore(
                        binStart, binEnd, value, sd, sem, n, memberValues));
            } else {
                result.add(new BasicScore(binStart, binEnd, value));
            }
        }
        return result;
    }

    /** Aggregates raw numeric values directly into the final caller-defined bins. */
    public static List<LocusScore> binRaw(List<LocusScore> scores, DisplayBinPlan plan,
                                          WindowFunction function) {
        if (scores == null || scores.isEmpty() || plan == null) return scores;
        if (plan.getBins().isEmpty()) return List.of();
        List<LocusScore> result = new ArrayList<>(plan.getBins().size());
        int firstCandidate = 0;
        for (DisplayBinPlan.Bin bin : plan.getBins()) {
            int binStart = bin.start();
            int binEnd = bin.end();
            while (firstCandidate < scores.size() && scoreEnd(scores.get(firstCandidate)) <= binStart) {
                firstCandidate++;
            }
            double weightedSum = 0;
            double totalWeight = 0;
            float extreme = Float.NaN;
            for (int i = firstCandidate; i < scores.size(); i++) {
                LocusScore score = scores.get(i);
                if (score.getStart() >= binEnd) break;
                int weight = overlap(score, binStart, binEnd);
                float value = score.getScore();
                if (weight <= 0 || Float.isNaN(value)) continue;
                if (function == WindowFunction.max) {
                    extreme = Float.isNaN(extreme) ? value : Math.max(extreme, value);
                } else if (function == WindowFunction.min) {
                    extreme = Float.isNaN(extreme) ? value : Math.min(extreme, value);
                } else if (function == WindowFunction.absoluteMax) {
                    extreme = Float.isNaN(extreme) || Math.abs(value) > Math.abs(extreme)
                            ? value : extreme;
                } else {
                    weightedSum += value * weight;
                    totalWeight += weight;
                }
            }
            float value = function == WindowFunction.max || function == WindowFunction.min
                    || function == WindowFunction.absoluteMax
                    ? extreme : totalWeight == 0 ? Float.NaN : (float) (weightedSum / totalWeight);
            if (!Float.isNaN(value)) result.add(new BasicScore(binStart, binEnd, value));
        }
        return result;
    }

    /**
     * Computes an Average track from each member's statistic in the final bin. None produces
     * separate non-negative-max and non-positive-min groups; other supported functions produce
     * one group after applying that function independently to every member.
     */
    public static List<LocusScore> binAverage(List<LocusScore> scores, DisplayBinPlan plan,
                                              WindowFunction function) {
        if (function == WindowFunction.none) return binAverageEnvelope(scores, plan);
        if (scores == null || scores.isEmpty() || plan == null) return scores;
        if (plan.getBins().isEmpty()) return List.of();
        List<LocusScore> result = new ArrayList<>(plan.getBins().size());
        int firstCandidate = 0;
        for (DisplayBinPlan.Bin bin : plan.getBins()) {
            int binStart = bin.start();
            int binEnd = bin.end();
            while (firstCandidate < scores.size() && scoreEnd(scores.get(firstCandidate)) <= binStart) {
                firstCandidate++;
            }
            double[] sums = null;
            double[] weights = null;
            float[] extrema = null;
            float missingValue = 0f;
            for (int i = firstCandidate; i < scores.size(); i++) {
                LocusScore score = scores.get(i);
                if (score.getStart() >= binEnd) break;
                int weight = overlap(score, binStart, binEnd);
                if (weight <= 0 || !(score instanceof AverageErrorLocusScore average)) continue;
                float[] rawValues = average.getMemberValues();
                if (rawValues == null) continue;
                if (sums == null) {
                    sums = new double[rawValues.length];
                    weights = new double[rawValues.length];
                    extrema = new float[rawValues.length];
                    java.util.Arrays.fill(extrema, Float.NaN);
                    missingValue = average.getMissingValue();
                }
                if (rawValues.length != sums.length) continue;
                for (int member = 0; member < rawValues.length; member++) {
                    float value = rawValues[member];
                    if (Float.isNaN(value)) continue;
                    if (function == WindowFunction.max) {
                        extrema[member] = Float.isNaN(extrema[member]) ? value
                                : Math.max(extrema[member], value);
                    } else if (function == WindowFunction.min) {
                        extrema[member] = Float.isNaN(extrema[member]) ? value
                                : Math.min(extrema[member], value);
                    } else if (function == WindowFunction.absoluteMax) {
                        extrema[member] = Float.isNaN(extrema[member])
                                || Math.abs(value) > Math.abs(extrema[member]) ? value : extrema[member];
                    } else {
                        sums[member] += value * weight;
                        weights[member] += weight;
                    }
                }
            }
            if (sums == null) continue;
            float[] contributions = new float[sums.length];
            boolean anyData = false;
            for (int member = 0; member < contributions.length; member++) {
                boolean hasData = function == WindowFunction.max || function == WindowFunction.min
                        || function == WindowFunction.absoluteMax
                        ? !Float.isNaN(extrema[member]) : weights[member] > 0;
                if (hasData) {
                    anyData = true;
                    contributions[member] = function == WindowFunction.max || function == WindowFunction.min
                            || function == WindowFunction.absoluteMax
                            ? extrema[member] : (float) (sums[member] / weights[member]);
                } else {
                    contributions[member] = missingValue;
                }
            }
            if (!anyData) continue;
            float[] stats = AverageErrorStatistics.calculate(contributions);
            result.add(new AverageErrorLocusScore(binStart, binEnd, stats[0], stats[1], stats[2],
                    contributions.length, contributions, AverageErrorLocusScore.Group.NONE, missingValue));
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
     * For each final bin this first takes every member's own non-negative maximum and/or
     * non-positive minimum from the preserved raw values. Only then does it compute the two
     * possible cross-member mean/SD/SEM groups, applying the Average track's configured missing
     * value to a member that has no value on that side. This order is essential: averaging fine
     * intervals and then choosing the largest average is not equivalent when members peak at
     * different genomic positions.
     */
    public static List<LocusScore> binAverageEnvelope(List<LocusScore> scores, DisplayBinPlan plan) {
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
            float[] memberPosMax = null;
            float[] memberNegMin = null;
            float missingValue = 0f;
            boolean hasPositive = false;
            boolean hasNegative = false;
            boolean hasZero = false;

            for (int i = firstCandidate; i < scores.size(); i++) {
                LocusScore score = scores.get(i);
                if (score.getStart() >= binEnd) break;
                if (overlap(score, binStart, binEnd) <= 0 || !(score instanceof AverageErrorLocusScore errorScore)) {
                    continue;
                }

                float[] memberValues = errorScore.getMemberValues();
                if (memberValues != null) {
                    if (memberPosMax == null) {
                        memberPosMax = new float[memberValues.length];
                        memberNegMin = new float[memberValues.length];
                        java.util.Arrays.fill(memberPosMax, Float.NaN);
                        java.util.Arrays.fill(memberNegMin, Float.NaN);
                        missingValue = errorScore.getMissingValue();
                    }
                    if (memberValues.length != memberPosMax.length) continue;
                    for (int member = 0; member < memberValues.length; member++) {
                        float value = memberValues[member];
                        if (Float.isNaN(value)) continue;
                        if (value > 0) hasPositive = true;
                        else if (value < 0) hasNegative = true;
                        else hasZero = true;
                        if (value >= 0 && (Float.isNaN(memberPosMax[member])
                                || value > memberPosMax[member])) {
                            memberPosMax[member] = value;
                        }
                        if (value <= 0 && (Float.isNaN(memberNegMin[member])
                                || value < memberNegMin[member])) {
                            memberNegMin[member] = value;
                        }
                    }
                } else {
                    // Compatibility for synthetic/legacy scores without member contributions.
                    float value = errorScore.getScore();
                    if (Float.isNaN(value)) continue;
                    if (value > 0) {
                        if (posBest == null || value > posBest.getScore()) posBest = errorScore;
                    } else if (value < 0) {
                        if (negBest == null || value < negBest.getScore()) negBest = errorScore;
                    }
                }
            }

            if (memberPosMax != null) {
                if (hasPositive || (!hasNegative && hasZero)) {
                    result.add(buildEnvelopeGroup(binStart, binEnd, memberPosMax, missingValue,
                            AverageErrorLocusScore.Group.POSITIVE));
                }
                if (hasNegative) {
                    result.add(buildEnvelopeGroup(binStart, binEnd, memberNegMin, missingValue,
                            AverageErrorLocusScore.Group.NEGATIVE));
                }
            } else {
                if (posBest != null) result.add(copyInto(posBest, binStart, binEnd));
                if (negBest != null) result.add(copyInto(negBest, binStart, binEnd));
            }
        }
        return result;
    }

    private static AverageErrorLocusScore copyInto(AverageErrorLocusScore source, int start, int end) {
        return new AverageErrorLocusScore(start, end, source.getScore(), source.getSd(), source.getSem(),
                source.getN(), source.getMemberValues(), source.getGroup(), source.getMissingValue());
    }

    private static AverageErrorLocusScore buildEnvelopeGroup(int start, int end, float[] extrema,
                                                             float missingValue,
                                                             AverageErrorLocusScore.Group group) {
        float[] contributions = extrema.clone();
        for (int i = 0; i < contributions.length; i++) {
            if (Float.isNaN(contributions[i])) contributions[i] = missingValue;
        }
        float[] stats = AverageErrorStatistics.calculate(contributions);
        return new AverageErrorLocusScore(start, end, stats[0], stats[1], stats[2],
                contributions.length, contributions, group, missingValue);
    }

    private static float[] aggregatedMemberValues(double[] sums, double[] weights) {
        if (sums == null || weights == null || sums.length != weights.length) return null;
        float[] values = new float[sums.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = weights[i] == 0 ? Float.NaN : (float) (sums[i] / weights[i]);
        }
        return values;
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
