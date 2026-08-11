package org.igv.renderer;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Collects annotation labels for one track so region backgrounds cannot cut them in half. */
public final class FeatureLabelCollector {

    private static final int LABEL_GAP = 2;

    private record Label(String text, int x, int baselineY, int featureCenterX,
                         Font font, Color color, boolean regional) {
    }

    private final List<Label> labels = new ArrayList<>();
    private final List<Rectangle> regionalTargets = new ArrayList<>();

    public void addRegionalTarget(Rectangle target) {
        if (target != null) regionalTargets.add(new Rectangle(target));
    }

    public void addLabel(String text, int x, int baselineY, int featureCenterX,
                         Font font, Color color, boolean regional) {
        labels.add(new Label(text, x, baselineY, featureCenterX, font, color, regional));
    }

    public boolean shouldPaintBaseLabelAt(int featureCenterX) {
        return regionalTargets.stream().noneMatch(target ->
                featureCenterX >= target.x && featureCenterX < target.x + target.width);
    }

    public void paint(Graphics2D graphics) {
        List<Label> eligible = labels.stream()
                .filter(label -> label.regional() || shouldPaintBaseLabelAt(label.featureCenterX()))
                .toList();
        List<Rectangle> bounds = new ArrayList<>(eligible.size());
        for (Label label : eligible) {
            FontMetrics metrics = graphics.getFontMetrics(label.font());
            bounds.add(new Rectangle(label.x(), label.baselineY() - metrics.getAscent(),
                    Math.max(1, metrics.stringWidth(label.text())), metrics.getHeight()));
        }
        for (int index : selectNonOverlappingIndices(bounds)) {
            Label label = eligible.get(index);
            Graphics2D labelGraphics = (Graphics2D) graphics.create();
            try {
                labelGraphics.setFont(label.font());
                labelGraphics.setColor(label.color());
                labelGraphics.drawString(label.text(), label.x(), label.baselineY());
            } finally {
                labelGraphics.dispose();
            }
        }
    }

    static List<Integer> selectNonOverlappingIndices(List<Rectangle> bounds) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < bounds.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt((Integer index) -> bounds.get(index).x)
                .thenComparingInt(index -> bounds.get(index).y));

        List<Integer> selected = new ArrayList<>();
        List<Rectangle> occupied = new ArrayList<>();
        for (int index : order) {
            Rectangle candidate = bounds.get(index);
            Rectangle spaced = new Rectangle(candidate.x - LABEL_GAP, candidate.y,
                    candidate.width + 2 * LABEL_GAP, candidate.height);
            if (occupied.stream().noneMatch(spaced::intersects)) {
                selected.add(index);
                occupied.add(new Rectangle(candidate));
            }
        }
        return selected;
    }
}
