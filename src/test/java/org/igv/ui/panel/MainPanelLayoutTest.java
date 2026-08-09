package org.igv.ui.panel;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class MainPanelLayoutTest {

    @Test
    public void replacementsOccupyFirstSelectedPositionAndRemainAdjacent() {
        List<String> current = List.of("sequence", "refseq", "top-1", "unselected", "bottom-1", "tail");
        Set<String> selected = Set.of("top-1", "bottom-1");

        List<String> result = MainPanel.replaceAtFirstMatch(
                current, selected::contains, List.of("top-average", "bottom-average"));

        assertEquals(List.of("sequence", "refseq", "top-average", "bottom-average", "unselected", "tail"), result);
    }

    @Test
    public void unmatchedReplacementLeavesLayoutUnchanged() {
        List<String> current = List.of("sequence", "refseq", "signal");

        List<String> result = MainPanel.replaceAtFirstMatch(
                current, "missing"::equals, List.of("average"));

        assertEquals(current, result);
    }
}
