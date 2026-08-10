package org.igv.ui.panel;

import org.igv.Globals;
import org.igv.event.IGVEvent;
import org.igv.event.IGVEventBus;
import org.igv.event.IGVEventObserver;
import org.igv.track.TrackMenuUtils;
import org.igv.ui.IGV;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Name header panel.  Displayed in upper left corner
 *
 * @author jrobinso
 */
public class NameHeaderPanel extends JPanel implements Paintable, IGVEventObserver {


    private final boolean darkMode;
    private final JCheckBox invertCoordinatesCheckbox;

    public NameHeaderPanel() {
        this.darkMode = Globals.isDarkMode();
        setBorder(null);
        setLayout(new GridBagLayout());
        invertCoordinatesCheckbox = new JCheckBox("Invert coordinates");
        invertCoordinatesCheckbox.setOpaque(false);
        invertCoordinatesCheckbox.setToolTipText("Reverse the genomic left-to-right display direction");
        invertCoordinatesCheckbox.setSelected(FrameManager.getDefaultFrame().isInverted());
        invertCoordinatesCheckbox.addActionListener(e -> applyDirectionToFrames());
        add(invertCoordinatesCheckbox);
        IGVEventBus.getInstance().subscribe(FrameManager.ChangeEvent.class, this);
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

    private void applyDirectionToFrames() {
        boolean inverted = invertCoordinatesCheckbox.isSelected();
        for (ReferenceFrame frame : FrameManager.getFrames()) {
            frame.setInverted(inverted);
        }
        if (IGV.hasInstance()) IGV.getInstance().repaint();
    }

    public void setCoordinatesInverted(boolean inverted) {
        invertCoordinatesCheckbox.setSelected(inverted);
        applyDirectionToFrames();
    }

    @Override
    public void receiveEvent(IGVEvent event) {
        if (event instanceof FrameManager.ChangeEvent) {
            applyDirectionToFrames();
        }
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        IGVEventBus.getInstance().unsubscribe(this);
    }
}
