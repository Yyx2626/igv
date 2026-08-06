package org.igv.ui.panel;

import org.igv.Globals;
import org.igv.prefs.Constants;
import org.igv.prefs.PreferencesManager;
import org.igv.track.TrackMenuUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A scrollable panel container that uses BoxLayout and doesn't stretch vertically
 * when placed in a JScrollPane viewport.
 */
public class ScrollableTrackContainer extends JPanel implements Scrollable {

    private MainPanel mainPanel;

    public ScrollableTrackContainer(MainPanel mainPanel) {
        this.mainPanel = mainPanel;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        if (Globals.isDarkMode() && !PreferencesManager.getPreferences().hasExplicitValue(Constants.BACKGROUND_COLOR)) {
            setBackground(UIManager.getColor("Panel.background"));
        } else {
            setBackground(PreferencesManager.getPreferences().getAsColor(Constants.BACKGROUND_COLOR));
        }

        // A click landing directly on this container (rather than on a child
        // TrackPanelScrollPane) is, by construction, in the empty space below the last
        // track - clear the selection checkboxes, mirroring clicking empty space in
        // Finder/Explorer to deselect everything.
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    TrackMenuUtils.clearAllTrackSelections();
                }
            }
        });
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        drawPanelDividers(g);
    }

    /**
     * Draw vertical divider lines at the boundaries between name, attribute, and data panels.
     * Uses computed positions from MainPanel so lines update immediately after layout changes
     * (e.g. hiding the attribute panel or changing name panel width).
     */
    private void drawPanelDividers(Graphics g) {
        int leftOffset = mainPanel.getLeftOffset();
        int nameRight = leftOffset + mainPanel.getNamePanelX() + mainPanel.getNamePanelWidth();
        int dataLeft = leftOffset + mainPanel.getDataPanelX();

        int h = getHeight();
        Color dividerColor = Globals.isDarkMode() ? Color.GRAY : Color.LIGHT_GRAY;
        g.setColor(dividerColor);

        g.drawLine(nameRight, 0, nameRight, h);
        if (dataLeft != nameRight) {
            g.drawLine(dataLeft, 0, dataLeft, h);
        }
    }

    // Scrollable implementation

    @Override
    public Dimension getPreferredSize() {
        // Calculate preferred size based on children
        int height = 0;
        int width = 0;
        for (Component c : getComponents()) {
            Dimension pref = c.getPreferredSize();
            height += pref.height;
            width = Math.max(width, pref.width);
        }
        return new Dimension(width, height);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        // Controls wheel and arrow-button speed for the outer track-stack scroll pane only.
        // Kept slightly below the inner-track increment so scrolling the whole stack feels
        // a touch slower than scrolling within a single track.
        return 12;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true; // Stretch horizontally to fit viewport
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false; // Do NOT stretch vertically - use preferred height
    }
}

