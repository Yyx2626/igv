package org.igv.renderer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;

/** Visual settings for member-value points drawn on an Average track. */
public class ScatterPointStyle {

    public enum Shape {
        CIRCLE("Circle"),
        SQUARE("Square"),
        DIAMOND("Diamond"),
        TRIANGLE("Triangle");

        private final String displayName;

        Shape(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static final int DEFAULT_WIDTH_PERCENT = 75;
    public static final double DEFAULT_POINT_SIZE_PX = 1.0;
    public static final double DEFAULT_BORDER_LINE_WIDTH_PX = 0.2;
    public static final Color DEFAULT_INNER_COLOR = Color.WHITE;
    static final double DEFAULT_POINT_SIZE_FACTOR = 1.0;

    private int widthPercent = DEFAULT_WIDTH_PERCENT;
    private Shape shape = Shape.CIRCLE;
    private double pointSizePx = DEFAULT_POINT_SIZE_PX;
    private double borderLineWidthPx = DEFAULT_BORDER_LINE_WIDTH_PX;
    private boolean creationDefaultsInitialized;
    // null means follow the Average track's corresponding positive/negative color.
    private Color positiveColorOverride;
    private Color negativeColorOverride;
    // null means use DEFAULT_INNER_COLOR.
    private Color innerColorOverride;

    public int getWidthPercent() {
        return widthPercent;
    }

    public void setWidthPercent(int widthPercent) {
        this.widthPercent = clamp(widthPercent, 1, 100);
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = shape == null ? Shape.CIRCLE : shape;
    }

    public double getPointSizePx() {
        return pointSizePx;
    }

    public void setPointSizePx(double pointSizePx) {
        this.pointSizePx = clamp(pointSizePx, 0.1, 100.0);
    }

    public double getBorderLineWidthPx() {
        return borderLineWidthPx;
    }

    public void setBorderLineWidthPx(double borderLineWidthPx) {
        this.borderLineWidthPx = clamp(borderLineWidthPx, 0.0, 10.0);
    }

    public boolean areCreationDefaultsInitialized() {
        return creationDefaultsInitialized;
    }

    /** Calculates defaults the first time this Average's scatter settings are opened. */
    public boolean initializeDefaultsForFirstSettingsOpen(double binWidthPx, int repeatCount) {
        if (creationDefaultsInitialized) {
            return false;
        }
        int repeats = Math.max(1, repeatCount);
        // Base the outside diameter directly on the available width per repeat. The separate
        // bar-overlap inset supplies a small visual overlap when adjacent circles would
        // otherwise be exactly tangent.
        pointSizePx = clamp(binWidthPx * widthPercent / 100.0 / repeats
                        * DEFAULT_POINT_SIZE_FACTOR,
                0.1, 100.0);
        borderLineWidthPx = clamp(pointSizePx * 0.2, 0.0, 10.0);
        creationDefaultsInitialized = true;
        return true;
    }

    public Color getPositiveColorOverride() {
        return positiveColorOverride;
    }

    public void setPositiveColorOverride(Color positiveColorOverride) {
        this.positiveColorOverride = positiveColorOverride;
    }

    public Color getNegativeColorOverride() {
        return negativeColorOverride;
    }

    public void setNegativeColorOverride(Color negativeColorOverride) {
        this.negativeColorOverride = negativeColorOverride;
    }

    public Color getInnerColorOverride() {
        return innerColorOverride;
    }

    public void setInnerColorOverride(Color innerColorOverride) {
        this.innerColorOverride = innerColorOverride;
    }

    public Color getInnerColor() {
        return innerColorOverride == null ? DEFAULT_INNER_COLOR : innerColorOverride;
    }

    public void copyFrom(ScatterPointStyle other) {
        if (other == null) return;
        widthPercent = other.widthPercent;
        shape = other.shape;
        pointSizePx = other.pointSizePx;
        borderLineWidthPx = other.borderLineWidthPx;
        creationDefaultsInitialized = other.creationDefaultsInitialized;
        positiveColorOverride = other.positiveColorOverride;
        negativeColorOverride = other.negativeColorOverride;
        innerColorOverride = other.innerColorOverride;
    }

    public ScatterPointStyle copy() {
        ScatterPointStyle copy = new ScatterPointStyle();
        copy.copyFrom(this);
        return copy;
    }

    public void marshalJSON(JSONObject json) {
        json.put("scatterWidthPercent", widthPercent);
        json.put("scatterPointShape", shape.name());
        json.put("scatterPointSizePx", pointSizePx);
        json.put("scatterBorderLineWidthPx", borderLineWidthPx);
        json.put("scatterCreationDefaultsInitialized", creationDefaultsInitialized);
        putColor(json, "scatterPositiveColor", positiveColorOverride);
        putColor(json, "scatterNegativeColor", negativeColorOverride);
        putColor(json, "scatterInnerColor", innerColorOverride);
    }

    public static ScatterPointStyle fromJSON(JSONObject json) {
        ScatterPointStyle style = new ScatterPointStyle();
        style.setWidthPercent(json.optInt("scatterWidthPercent", DEFAULT_WIDTH_PERCENT));
        style.setPointSizePx(json.optDouble("scatterPointSizePx", DEFAULT_POINT_SIZE_PX));
        style.setBorderLineWidthPx(json.optDouble(
                "scatterBorderLineWidthPx", DEFAULT_BORDER_LINE_WIDTH_PX));
        if (json.has("scatterCreationDefaultsInitialized")) {
            style.creationDefaultsInitialized =
                    json.optBoolean("scatterCreationDefaultsInitialized", false);
        } else if (json.has("scatterCreationDefaultsPending")) {
            // Compatibility with development sessions written before this state was simplified.
            style.creationDefaultsInitialized =
                    !json.optBoolean("scatterCreationDefaultsPending", true);
        } else {
            // Older scatter-enabled sessions already stored absolute pixel values.
            style.creationDefaultsInitialized = json.has("scatterPointSizePx")
                    || json.has("scatterBorderLineWidthPx");
        }
        if (json.has("scatterPointShape")) {
            try {
                style.shape = Shape.valueOf(json.getString("scatterPointShape"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        style.positiveColorOverride = readColor(json, "scatterPositiveColor");
        style.negativeColorOverride = readColor(json, "scatterNegativeColor");
        style.innerColorOverride = readColor(json, "scatterInnerColor");
        return style;
    }

    private static void putColor(JSONObject json, String key, Color color) {
        if (color != null) {
            json.put(key, new JSONArray(new int[]{color.getRed(), color.getGreen(),
                    color.getBlue(), color.getAlpha()}));
        }
    }

    private static Color readColor(JSONObject json, String key) {
        JSONArray value = json.optJSONArray(key);
        if (value == null || value.length() < 3) return null;
        int alpha = value.length() >= 4 ? value.optInt(3, 255) : 255;
        return new Color(value.optInt(0), value.optInt(1), value.optInt(2), alpha);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
