package org.igv.ui.panel;

import org.igv.Globals;
import org.igv.track.TrackMenuUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Name header panel.  Displayed in upper left corner
 *
 * @author jrobinso
 */
public class NameHeaderPanel extends JPanel implements Paintable {


    private final boolean darkMode;

    public NameHeaderPanel() {
        this.darkMode = Globals.isDarkMode();
        setBorder(null);
        //if(darkMode){
        //    setBackground(UIManager.getColor("Panel.background"));
        //}

        // Clicking this blank header gutter (above the tracks, left of the
        // chromosome/ruler) clears the selection checkboxes.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    TrackMenuUtils.clearAllTrackSelections();
                }
            }
        });
    }

    public void paintOffscreen(Graphics2D g, Rectangle rect, boolean batch) {
        paintComponent(g);
    }

    @Override
    public int getSnapshotHeight(boolean batch) {
        return getHeight();
    }
}
