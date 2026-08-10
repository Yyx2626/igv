package org.igv.track;

import org.igv.ui.panel.ReferenceFrame;

import java.awt.event.MouseEvent;

/**
 * @author jrobinso
 * @date Sep 17, 2010
 */
public class TrackClickEvent {

    private final MouseEvent mouseEvent;
    private final ReferenceFrame frame;
    private final double chromosomePosition;

    public TrackClickEvent(MouseEvent mouseEvent, ReferenceFrame frame) {
        this(mouseEvent, frame, frame == null ? 0 : frame.getChromosomePosition(mouseEvent));
    }

    public TrackClickEvent(MouseEvent mouseEvent, ReferenceFrame frame, double chromosomePosition) {
        this.mouseEvent = mouseEvent;
        this.frame = frame;
        this.chromosomePosition = chromosomePosition;
    }


    public MouseEvent getMouseEvent() {
        return mouseEvent;
    }

    public ReferenceFrame getFrame() {
        return frame;
    }

    public double getChromosomePosition() {
        return chromosomePosition;
    }
}
