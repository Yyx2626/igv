package org.igv.track;

import org.igv.data.AverageErrorLocusScore;
import org.igv.data.BasicScore;
import org.igv.feature.LocusScore;

import java.util.ArrayList;
import java.util.List;

/** Resamples visible numeric data into equal genomic bins for display. */
final class NumericTrackBinner {

    private NumericTrackBinner() {
    }

    static List<LocusScore> bin(List<LocusScore> scores, int rangeStart, int rangeEnd, int requestedBins) {
        if (scores == null || scores.isEmpty() || rangeEnd <= rangeStart || requestedBins <= 0) {
            return scores;
        }

        int span = rangeEnd - rangeStart;
        int binCount = Math.min(requestedBins, span);
        List<LocusScore> result = new ArrayList<>(binCount);
        int firstCandidate = 0;

        for (int binIndex = 0; binIndex < binCount; binIndex++) {
            int binStart = rangeStart + (int) (((long) span * binIndex) / binCount);
            int binEnd = rangeStart + (int) (((long) span * (binIndex + 1)) / binCount);

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
