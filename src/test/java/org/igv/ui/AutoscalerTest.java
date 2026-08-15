package org.igv.ui;

import org.igv.renderer.DataRange;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;

public class AutoscalerTest {

    @Test
    public void autoscalingPreservesFlippedAxisAndGuideAppearance() {
        DataRange current = new DataRange(0, 0, 10, true).flipped();
        current.setMidlineColor(new Color(210, 211, 212));

        DataRange scaled = Autoscaler.autoscaledDataRange(current, 2, 20);

        assertEquals(20f, scaled.getMinimum(), 0f);
        assertEquals(0f, scaled.getBaseline(), 0f);
        assertEquals(0f, scaled.getMaximum(), 0f);
        assertEquals(current.getMidlineColor(), scaled.getMidlineColor());
    }
}
