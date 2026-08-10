package org.igv.feature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;

/** Optional display-only overrides for one track inside one genomic region. */
public class TrackRegionOverride {

    public enum YAxisMode {
        DEFAULT("Default"),
        FLIP("Flip"),
        CUSTOM("Custom range");

        private final String label;

        YAxisMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private boolean reverseX;
    private YAxisMode yAxisMode = YAxisMode.DEFAULT;
    private Float rangeMinimum;
    private Float rangeBaseline;
    private Float rangeMaximum;
    private Boolean logScale;
    private Color positiveColor;
    private Color negativeColor;
    private Color backgroundColor;
    private Color foregroundMaskColor;

    public boolean isReverseX() {
        return reverseX;
    }

    public void setReverseX(boolean reverseX) {
        this.reverseX = reverseX;
    }

    public YAxisMode getYAxisMode() {
        return yAxisMode;
    }

    public void setYAxisMode(YAxisMode yAxisMode) {
        this.yAxisMode = yAxisMode == null ? YAxisMode.DEFAULT : yAxisMode;
    }

    public Float getRangeMinimum() {
        return rangeMinimum;
    }

    public Float getRangeBaseline() {
        return rangeBaseline;
    }

    public Float getRangeMaximum() {
        return rangeMaximum;
    }

    public Boolean getLogScale() {
        return logScale;
    }

    public void setCustomRange(float minimum, float baseline, float maximum, boolean logScale) {
        this.rangeMinimum = minimum;
        this.rangeBaseline = baseline;
        this.rangeMaximum = maximum;
        this.logScale = logScale;
        this.yAxisMode = YAxisMode.CUSTOM;
    }

    public void clearCustomRange() {
        this.rangeMinimum = null;
        this.rangeBaseline = null;
        this.rangeMaximum = null;
        this.logScale = null;
        if (yAxisMode == YAxisMode.CUSTOM) {
            yAxisMode = YAxisMode.DEFAULT;
        }
    }

    public Color getPositiveColor() {
        return positiveColor;
    }

    public void setPositiveColor(Color positiveColor) {
        this.positiveColor = positiveColor;
    }

    public Color getNegativeColor() {
        return negativeColor;
    }

    public void setNegativeColor(Color negativeColor) {
        this.negativeColor = negativeColor;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public Color getForegroundMaskColor() {
        return foregroundMaskColor;
    }

    public void setForegroundMaskColor(Color foregroundMaskColor) {
        this.foregroundMaskColor = foregroundMaskColor;
    }

    public boolean hasAnyEffect() {
        return reverseX || yAxisMode != YAxisMode.DEFAULT || positiveColor != null || negativeColor != null
                || backgroundColor != null || foregroundMaskColor != null;
    }

    public TrackRegionOverride copy() {
        return fromJson(toJson());
    }

    /**
     * Compose nested regional overrides in display order. Inversion and flip are reversible
     * transforms and therefore use XOR; explicit colors use the last non-null value.
     */
    public static TrackRegionOverride compose(Iterable<TrackRegionOverride> overrides) {
        TrackRegionOverride effective = new TrackRegionOverride();
        boolean reverseX = false;
        boolean flipY = false;
        TrackRegionOverride customRange = null;
        for (TrackRegionOverride next : overrides) {
            if (next == null) continue;
            reverseX ^= next.isReverseX();
            if (next.getYAxisMode() == YAxisMode.FLIP) flipY = !flipY;
            if (next.getYAxisMode() == YAxisMode.CUSTOM) customRange = next;
            if (next.getBackgroundColor() != null) effective.setBackgroundColor(next.getBackgroundColor());
            if (next.getForegroundMaskColor() != null) effective.setForegroundMaskColor(next.getForegroundMaskColor());
            if (next.getPositiveColor() != null) effective.setPositiveColor(next.getPositiveColor());
            if (next.getNegativeColor() != null) effective.setNegativeColor(next.getNegativeColor());
        }
        effective.setReverseX(reverseX);
        if (flipY) {
            effective.setYAxisMode(YAxisMode.FLIP);
        } else if (customRange != null && customRange.getRangeMinimum() != null
                && customRange.getRangeBaseline() != null && customRange.getRangeMaximum() != null) {
            effective.setCustomRange(customRange.getRangeMinimum(), customRange.getRangeBaseline(),
                    customRange.getRangeMaximum(), Boolean.TRUE.equals(customRange.getLogScale()));
        }
        return effective;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        if (reverseX) json.put("reverseX", true);
        if (yAxisMode != YAxisMode.DEFAULT) json.put("yAxisMode", yAxisMode.name());
        if (yAxisMode == YAxisMode.CUSTOM && rangeMinimum != null && rangeBaseline != null && rangeMaximum != null) {
            JSONObject range = new JSONObject();
            range.put("min", rangeMinimum);
            range.put("mid", rangeBaseline);
            range.put("max", rangeMaximum);
            if (logScale != null) range.put("logScale", logScale);
            json.put("range", range);
        }
        putColor(json, "positiveColor", positiveColor);
        putColor(json, "negativeColor", negativeColor);
        putColor(json, "backgroundColor", backgroundColor);
        putColor(json, "foregroundMaskColor", foregroundMaskColor);
        return json;
    }

    public static TrackRegionOverride fromJson(JSONObject json) {
        TrackRegionOverride override = new TrackRegionOverride();
        override.reverseX = json.optBoolean("reverseX", false);
        String yAxisMode = json.optString("yAxisMode", YAxisMode.DEFAULT.name());
        try {
            override.yAxisMode = YAxisMode.valueOf(yAxisMode);
        } catch (IllegalArgumentException ignored) {
            override.yAxisMode = YAxisMode.DEFAULT;
        }
        JSONObject range = json.optJSONObject("range");
        if (range != null && range.has("min") && range.has("mid") && range.has("max")) {
            override.rangeMinimum = (float) range.getDouble("min");
            override.rangeBaseline = (float) range.getDouble("mid");
            override.rangeMaximum = (float) range.getDouble("max");
            override.logScale = range.optBoolean("logScale", false);
        }
        override.positiveColor = getColor(json, "positiveColor");
        override.negativeColor = getColor(json, "negativeColor");
        override.backgroundColor = getColor(json, "backgroundColor");
        override.foregroundMaskColor = getColor(json, "foregroundMaskColor");
        return override;
    }

    static void putColor(JSONObject json, String key, Color color) {
        if (color != null) {
            json.put(key, new JSONArray(new int[]{color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()}));
        }
    }

    static Color getColor(JSONObject json, String key) {
        JSONArray rgba = json.optJSONArray(key);
        if (rgba == null || rgba.length() < 3) return null;
        int alpha = rgba.length() >= 4 ? rgba.getInt(3) : 255;
        return new Color(rgba.getInt(0), rgba.getInt(1), rgba.getInt(2), alpha);
    }
}
