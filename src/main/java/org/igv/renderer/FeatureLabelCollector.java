package org.igv.renderer;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** Collects annotation labels for one track so region backgrounds cannot cut them in half. */
public final class FeatureLabelCollector {

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
        for (Label label : labels) {
            if (!label.regional() && !shouldPaintBaseLabelAt(label.featureCenterX())) continue;
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
}
