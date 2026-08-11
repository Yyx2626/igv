package org.igv.ui.undo;

import org.igv.feature.RegionDisplayRule;
import org.igv.feature.RegionOfInterest;
import org.igv.ui.IGV;
import org.igv.ui.panel.FrameManager;

import javax.swing.undo.AbstractUndoableEdit;
import java.awt.Color;
import java.util.Objects;

/** One accepted Regional Settings dialog, regardless of its live-preview changes. */
public final class RegionalSettingsEdit extends AbstractUndoableEdit {

    private final RegionOfInterest region;
    private final RegionDisplayRule beforeRule;
    private final RegionDisplayRule afterRule;
    private final Color beforeBarColor;
    private final Color afterBarColor;

    public RegionalSettingsEdit(RegionOfInterest region,
                                RegionDisplayRule beforeRule, Color beforeBarColor,
                                RegionDisplayRule afterRule, Color afterBarColor) {
        this.region = region;
        this.beforeRule = copy(beforeRule);
        this.afterRule = copy(afterRule);
        this.beforeBarColor = beforeBarColor;
        this.afterBarColor = afterBarColor;
    }

    public static boolean differs(RegionDisplayRule firstRule, Color firstColor,
                                  RegionDisplayRule secondRule, Color secondColor) {
        return !Objects.equals(json(firstRule), json(secondRule))
                || !Objects.equals(firstColor, secondColor);
    }

    @Override
    public void undo() {
        super.undo();
        apply(beforeRule, beforeBarColor);
    }

    @Override
    public void redo() {
        super.redo();
        apply(afterRule, afterBarColor);
    }

    @Override
    public String getPresentationName() {
        return "Regional Settings";
    }

    private void apply(RegionDisplayRule rule, Color barColor) {
        region.setBackgroundColor(barColor);
        region.setDisplayRule(copy(rule));
        IGV.getInstance().getSession().notifyRegionsOfInterestChanged();
        for (var frame : FrameManager.getFrames()) frame.refreshRegionDisplayCoordinateMap();
        IGV.getInstance().repaint();
    }

    private static RegionDisplayRule copy(RegionDisplayRule rule) {
        return rule == null ? null : rule.copy();
    }

    private static String json(RegionDisplayRule rule) {
        return rule == null || !rule.hasAnyEffect() ? null : rule.toJson().toString();
    }
}
