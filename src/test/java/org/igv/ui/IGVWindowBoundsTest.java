package org.igv.ui;

import org.junit.Test;

import java.awt.Dimension;
import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;

public class IGVWindowBoundsTest {

    @Test
    public void expandsAVisibleSavedWindowToThePreferredStartupHeight() {
        Rectangle saved = new Rectangle(271, 111, 1042, 664);
        Rectangle[] screens = {new Rectangle(0, 0, 1512, 982)};

        assertEquals(new Rectangle(271, 111, 1042, 750),
                IGV.normalizeApplicationBounds(saved, screens));
    }

    @Test
    public void keepsExpandedWindowInsideItsScreen() {
        Rectangle saved = new Rectangle(700, 400, 600, 500);
        Rectangle[] screens = {new Rectangle(0, 0, 1200, 800)};

        assertEquals(new Rectangle(200, 50, 1000, 750),
                IGV.normalizeApplicationBounds(saved, screens));
    }

    @Test
    public void ignoresSavedBoundsWhoseOriginIsOffscreen() {
        Rectangle saved = new Rectangle(3000, 100, 600, 500);
        Rectangle[] screens = {new Rectangle(0, 0, 1512, 982)};

        assertEquals(new Rectangle(0, 0, 1150, 800),
                IGV.normalizeApplicationBounds(saved, screens));
    }

    @Test
    public void honorsAnExplicitReproducibleWindowSize() {
        Rectangle saved = new Rectangle(271, 111, 1042, 664);
        Rectangle[] screens = {new Rectangle(0, 0, 1512, 982)};

        assertEquals(new Rectangle(271, 111, 900, 600),
                IGV.normalizeApplicationBounds(saved, screens, new Dimension(900, 600)));
    }

    @Test
    public void clampsAnExplicitWindowSizeToTheCurrentScreen() {
        Rectangle saved = new Rectangle(700, 400, 1000, 750);
        Rectangle[] screens = {new Rectangle(0, 0, 1200, 800)};

        assertEquals(new Rectangle(0, 0, 1200, 800),
                IGV.normalizeApplicationBounds(saved, screens, new Dimension(1600, 1000)));
    }
}
