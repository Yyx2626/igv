package org.igv.renderer;

import org.igv.session.Persistable;
import org.igv.track.Track;
import org.igv.ui.color.ColorUtilities;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.Color;
import java.util.Collection;

/**
 * Encapsulates parameter for an x-y plot axis.
 *
 * @author jrobinso
 */

public class DataRange {

    /** Default Mid-line color when {@link #midlineColor} isn't explicitly set - a light, unobtrusive gray (lighter than {@code Color.lightGray}, which reads as noticeably darker against a near-white track background). */
    public static final Color DEFAULT_MIDLINE_COLOR = new Color(225, 225, 225);

    /**
     * The scale type,  linear by default
     */
    private Type type = Type.LINEAR;
    /**
     * Minimum data value displayed.  Zero by default.
     */
    private float minimum = 0;
    /**
     * Where to draw the plot baseline.  Zero by default
     */
    private float baseline = 0;
    /**
     * Maximum data value displayed. This value is required, no default
     */
    public float maximum;
    /**
     * If true the Y axis is "flipped" (most negative value at top)
     */
    private boolean flipAxis = false;
    private boolean drawBaseline = true;
    /** Explicit color for the Mid guide line, or null to use XYPlotRenderer's existing gray/track-color fallback. */
    private Color midlineColor = null;

    public DataRange() {
    }


    public DataRange(float minimum, float maximum) {
        this(minimum, minimum, maximum, true);
    }

    public DataRange(float minimum, float baseline, float maximum) {
        this(minimum, baseline, maximum, true);
    }


    public DataRange(float minimum, float baseline, float maximum, boolean drawBaseline) {
        this(minimum, baseline, maximum, drawBaseline, false);
    }

    public DataRange(float minimum, float baseline, float maximum, boolean drawBaseline, boolean isLog) {
        this.minimum = minimum;
        this.baseline = baseline;
        this.maximum = maximum;
        this.drawBaseline = drawBaseline;
        this.type = isLog ? Type.LOG : Type.LINEAR;
    }

    public DataRange(Element element, Integer version) {

        String tmp = element.getAttribute("type");
        if (tmp != null) {
            this.type = Type.valueOf(tmp);
        }

        tmp = element.getAttribute("minimum");
        if (tmp != null) {
            this.minimum = Float.parseFloat(tmp);
        }

        tmp = element.getAttribute("baseline");
        if (tmp != null) {
            this.baseline = Float.parseFloat(tmp);
        }

        tmp = element.getAttribute("maximum");
        if (tmp != null) {
            this.maximum = Float.parseFloat(tmp);
        }

        tmp = element.getAttribute("flipAxis");
        if (tmp != null) {
            this.flipAxis = Boolean.parseBoolean(tmp);
        }

        tmp = element.getAttribute("drawBaseline");
        if (tmp != null) {
            drawBaseline = Boolean.parseBoolean(tmp);
        }

    }

    public static DataRange fromJson(JSONObject jsonObject) {
        float minimum = (float) jsonObject.optDouble("min", 0);
        float baseline = (float) jsonObject.optDouble("mid", 0);
        float maximum = (float) jsonObject.optDouble("max", 0);
        boolean drawBaseline = jsonObject.optBoolean("drawBaseline", true);
        boolean isLog = jsonObject.optBoolean("logScale", false);
        boolean flipAxis = jsonObject.optBoolean("flipAxis", false);
        DataRange dr = new DataRange(minimum, baseline, maximum, drawBaseline, isLog);
        dr.flipAxis = flipAxis;
        if (jsonObject.has("midlineColor")) {
            dr.midlineColor = ColorUtilities.stringToColor(jsonObject.getString("midlineColor"));
        }
        return dr;
    }

    public static DataRange unmarshalJSON(JSONObject jsonObject) {
        return fromJson(jsonObject);
    }

    public static DataRange getFromTracks(Collection<? extends Track> tracks) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        float mid = 0;
        boolean drawBaseline = true;
        boolean isLog = true;
        for (Track t : tracks) {
            DataRange dr = t.getDataRange();
            min = Math.min(min, dr.getMinimum());
            max = Math.max(max, dr.getMaximum());
            mid += dr.getBaseline();
            drawBaseline &= dr.isDrawBaseline();
            isLog &= dr.isLog();
        }
        mid /= tracks.size();
        if (mid < min) {
            mid = min;
        } else if (mid > max) {
            min = max;
        }

        return new DataRange(min, mid, max, drawBaseline, isLog);
    }

    /** Independent copy - callers that hand a DataRange to more than one track must not let them share the same mutable instance. */
    public DataRange copy() {
        DataRange dr = new DataRange(minimum, baseline, maximum, drawBaseline, isLog());
        dr.flipAxis = this.flipAxis;
        dr.midlineColor = this.midlineColor;
        return dr;
    }

    /** Independent copy with the vertical axis direction reversed. */
    public DataRange flipped() {
        DataRange dr = new DataRange(maximum, baseline, minimum, drawBaseline, isLog());
        dr.flipAxis = !this.flipAxis;
        dr.midlineColor = this.midlineColor;
        return dr;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isLog() {
        return type == Type.LOG;
    }


    public float getMinimum() {
        return minimum;
    }


    public float getBaseline() {
        return baseline;
    }


    public float getMaximum() {
        return maximum;
    }


    public boolean isFlipAxis() {
        return flipAxis;
    }

    public boolean isDrawBaseline() {
        return drawBaseline;
    }

    public void setDrawBaseline(boolean drawBaseline) {
        this.drawBaseline = drawBaseline;
    }

    public Color getMidlineColor() {
        return midlineColor;
    }

    public void setMidlineColor(Color midlineColor) {
        this.midlineColor = midlineColor;
    }


    /**
     * Restore object state from an XML element
     */

    public void unmarshalXML(Element element, Integer version) {

        this.baseline = Float.parseFloat(element.getAttribute("baseline"));
        this.drawBaseline = Boolean.parseBoolean(element.getAttribute("drawBaseLine"));
        this.flipAxis = Boolean.parseBoolean(element.getAttribute("flipAxis"));
        this.maximum = Float.parseFloat(element.getAttribute("maximum"));
        this.minimum = Float.parseFloat(element.getAttribute("minimum"));
    }

    public void marshalJSON(JSONObject jsonObject) {
        jsonObject.put("logScale", this.type == Type.LOG ? true : false);
        jsonObject.put("min", this.minimum);
        jsonObject.put("mid", this.baseline);
        jsonObject.put("max", this.maximum);
        jsonObject.put("flipAxis", this.flipAxis);
        jsonObject.put("drawBaseline", this.drawBaseline);
        if (this.midlineColor != null) {
            jsonObject.put("midlineColor", ColorUtilities.colorToString(this.midlineColor));
        }
    }


    public enum Type {
        LOG, LINEAR
    }

    ;


}
