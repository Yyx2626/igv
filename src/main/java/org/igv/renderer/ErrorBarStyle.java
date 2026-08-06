package org.igv.renderer;

import org.igv.ui.color.ColorUtilities;
import org.json.JSONObject;

import java.awt.Color;

/**
 * Visual style for the error bar drawn by {@code AverageErrorBarRenderer} on top of an
 * {@code AverageErrorBarTrack}'s mean bar.
 */
public class ErrorBarStyle {

    public enum Shape {BAR, LINE}

    /** SINGLE = cap at one end only ("T"-shape); DOUBLE = cap at both ends ("I"-beam). Only used when shape == LINE. */
    public enum CapStyle {SINGLE, DOUBLE}

    /** Default error-bar color: a muted ochre/khaki ("土黄色"). */
    public static final Color DEFAULT_COLOR = new Color(0xC1, 0x9A, 0x3E);

    private Shape shape = Shape.BAR;
    private int barWidthPercent = 100;
    private CapStyle capStyle = CapStyle.SINGLE;
    private int lineWidthPx = 1;
    private Color colorOverride = DEFAULT_COLOR;

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = shape;
    }

    public int getBarWidthPercent() {
        return barWidthPercent;
    }

    public void setBarWidthPercent(int barWidthPercent) {
        this.barWidthPercent = barWidthPercent;
    }

    public CapStyle getCapStyle() {
        return capStyle;
    }

    public void setCapStyle(CapStyle capStyle) {
        this.capStyle = capStyle;
    }

    public int getLineWidthPx() {
        return lineWidthPx;
    }

    public void setLineWidthPx(int lineWidthPx) {
        this.lineWidthPx = lineWidthPx;
    }

    public Color getColorOverride() {
        return colorOverride;
    }

    public void setColorOverride(Color colorOverride) {
        this.colorOverride = colorOverride;
    }

    public void marshalJSON(JSONObject json) {
        json.put("errorBarShape", shape.toString());
        json.put("errorBarWidthPercent", barWidthPercent);
        json.put("errorBarCapStyle", capStyle.toString());
        json.put("errorBarLineWidthPx", lineWidthPx);
        if (colorOverride != null) {
            json.put("errorBarColor", ColorUtilities.colorToString(colorOverride));
        }
    }

    public static ErrorBarStyle fromJSON(JSONObject json) {
        ErrorBarStyle style = new ErrorBarStyle();
        if (json.has("errorBarShape")) {
            try {
                style.shape = Shape.valueOf(json.getString("errorBarShape"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (json.has("errorBarWidthPercent")) {
            style.barWidthPercent = json.getInt("errorBarWidthPercent");
        }
        if (json.has("errorBarCapStyle")) {
            try {
                style.capStyle = CapStyle.valueOf(json.getString("errorBarCapStyle"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (json.has("errorBarLineWidthPx")) {
            style.lineWidthPx = json.getInt("errorBarLineWidthPx");
        }
        if (json.has("errorBarColor")) {
            try {
                style.colorOverride = ColorUtilities.stringToColor(json.getString("errorBarColor"));
            } catch (Exception ignored) {
            }
        }
        return style;
    }
}
