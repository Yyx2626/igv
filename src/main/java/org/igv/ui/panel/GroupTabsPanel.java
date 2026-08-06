package org.igv.ui.panel;

import org.igv.event.IGVEvent;
import org.igv.event.IGVEventBus;
import org.igv.event.IGVEventObserver;
import org.igv.event.TrackSelectionEvent;
import org.igv.track.Track;
import org.igv.track.TrackGrouping;
import org.igv.ui.IGV;

import javax.swing.*;
import java.awt.*;

/**
 * RTS-game-style "control group" tabs: one button per group number 1-9, docked at the
 * bottom of the track area. Clicking a button selects that group's tracks (same as
 * pressing the corresponding number key). Kept as a strong-referenced field on
 * {@code MainPanel} so it isn't garbage-collected out of {@link IGVEventBus}'s
 * weak-referenced subscriber set.
 */
public class GroupTabsPanel extends JPanel implements IGVEventObserver {

    public static final int GROUP_COUNT = 9;

    private final JButton[] buttons = new JButton[GROUP_COUNT];

    public GroupTabsPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 4, 2));
        add(new JLabel("Track Groups:"));
        for (int i = 1; i <= GROUP_COUNT; i++) {
            final int n = i;
            JButton button = new JButton(String.valueOf(n));
            button.setMargin(new Insets(1, 6, 1, 6));
            button.addActionListener(e -> TrackGrouping.selectGroup(n));
            buttons[i - 1] = button;
            add(button);
        }
        // Defer the first refresh(): it calls IGV.getInstance().getAllTracks(), but this
        // panel is constructed from MainPanel's constructor, which runs before
        // IGV.contentPane is assigned (IGV.<init> -> IGVContentPane.<init> -> MainPanel.<init>).
        // invokeLater runs after that assignment completes.
        SwingUtilities.invokeLater(this::refresh);
        IGVEventBus.getInstance().subscribe(TrackSelectionEvent.class, this);
    }

    @Override
    public void receiveEvent(IGVEvent event) {
        if (event instanceof TrackSelectionEvent) {
            refresh();
        }
    }

    private void refresh() {
        for (int i = 1; i <= GROUP_COUNT; i++) {
            int count = 0;
            StringBuilder names = new StringBuilder();
            for (Track track : IGV.getInstance().getAllTracks()) {
                if (track.getTrackGroups().contains(i)) {
                    if (count > 0) names.append(", ");
                    names.append(track.getName());
                    count++;
                }
            }
            JButton button = buttons[i - 1];
            button.setToolTipText(count == 0
                    ? "Group " + i + " (empty). Ctrl+" + i + " to assign, Shift+" + i + " to add selected tracks."
                    : "Group " + i + " (" + count + "): " + names
                      + "  [Ctrl+" + i + " reassign, Shift+" + i + " add]");
            button.setEnabled(count > 0);
        }
    }
}
