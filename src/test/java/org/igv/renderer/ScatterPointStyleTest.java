package org.igv.renderer;

import org.json.JSONObject;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ScatterPointStyleTest {

    @Test
    public void defaultsMatchAverageDialogDefaults() {
        ScatterPointStyle style = new ScatterPointStyle();

        assertEquals(75, style.getWidthPercent());
        assertEquals(ScatterPointStyle.Shape.CIRCLE, style.getShape());
        assertEquals(1.0, style.getPointSizePx(), 0);
        assertEquals(0.2, style.getBorderLineWidthPx(), 0);
        assertNull(style.getPositiveColorOverride());
        assertNull(style.getNegativeColorOverride());
        assertNull(style.getInnerColorOverride());
        assertEquals(Color.WHITE, style.getInnerColor());
    }

    @Test
    public void jsonRoundTripPreservesSettings() {
        ScatterPointStyle original = new ScatterPointStyle();
        original.setWidthPercent(50);
        original.setShape(ScatterPointStyle.Shape.DIAMOND);
        original.setPointSizePx(0.5);
        original.setBorderLineWidthPx(0.75);
        original.setPositiveColorOverride(new Color(1, 2, 3, 4));
        original.setNegativeColorOverride(new Color(5, 6, 7, 8));
        original.setInnerColorOverride(new Color(9, 10, 11, 12));
        JSONObject json = new JSONObject();
        original.marshalJSON(json);

        ScatterPointStyle restored = ScatterPointStyle.fromJSON(json);

        assertEquals(50, restored.getWidthPercent());
        assertEquals(ScatterPointStyle.Shape.DIAMOND, restored.getShape());
        assertEquals(0.5, restored.getPointSizePx(), 0);
        assertEquals(0.75, restored.getBorderLineWidthPx(), 0);
        assertEquals(new Color(1, 2, 3, 4), restored.getPositiveColorOverride());
        assertEquals(new Color(5, 6, 7, 8), restored.getNegativeColorOverride());
        assertEquals(new Color(9, 10, 11, 12), restored.getInnerColorOverride());
        assertFalse(restored.areCreationDefaultsInitialized());
    }

    @Test
    public void creationDefaultsUseBinWidthScatterWidthAndRepeatCount() {
        ScatterPointStyle style = new ScatterPointStyle();
        style.setWidthPercent(50);

        assertTrue(style.initializeDefaultsForFirstSettingsOpen(12.0, 3));

        assertEquals(2.0, style.getPointSizePx(), 0.0001);
        assertEquals(0.4, style.getBorderLineWidthPx(), 0.0001);
        assertTrue(style.areCreationDefaultsInitialized());
        assertFalse(style.initializeDefaultsForFirstSettingsOpen(30.0, 3));
        assertEquals(2.0, style.getPointSizePx(), 0.0001);
    }

    @Test
    public void initializedAbsoluteValuesPersistInSession() {
        ScatterPointStyle original = new ScatterPointStyle();
        original.initializeDefaultsForFirstSettingsOpen(12.0, 3);
        JSONObject json = new JSONObject();
        original.marshalJSON(json);

        ScatterPointStyle restored = ScatterPointStyle.fromJSON(json);

        assertTrue(restored.areCreationDefaultsInitialized());
        assertEquals(3.0, restored.getPointSizePx(), 0.0001);
        assertEquals(0.6, restored.getBorderLineWidthPx(), 0.0001);
        assertFalse(restored.initializeDefaultsForFirstSettingsOpen(30.0, 5));
    }
}
