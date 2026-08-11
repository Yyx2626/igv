package org.igv.ui.undo;

import org.junit.Test;

import javax.swing.undo.AbstractUndoableEdit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IGVUndoManagerTest {

    @Test
    public void supportsUndoAndRedoPresentation() {
        IGVUndoManager manager = new IGVUndoManager();
        CountingEdit edit = new CountingEdit("Delete Track");
        manager.addEdit(edit);

        assertEquals("Undo Delete Track", manager.getUndoPresentationName());
        manager.undo();
        assertEquals(1, edit.undoCount);
        assertEquals("Redo Delete Track", manager.getRedoPresentationName());
        manager.redo();
        assertEquals(1, edit.redoCount);
    }

    @Test
    public void destroysEditsBeyondTwentySteps() {
        IGVUndoManager manager = new IGVUndoManager();
        CountingEdit first = new CountingEdit("First");
        manager.addEdit(first);
        for (int i = 1; i <= 20; i++) manager.addEdit(new CountingEdit("Edit " + i));

        assertTrue(first.died);
        for (int i = 0; i < 20; i++) manager.undo();
        assertFalse(manager.canUndo());
    }

    @Test
    public void newEditDestroysDiscardedRedoBranch() {
        IGVUndoManager manager = new IGVUndoManager();
        CountingEdit discarded = new CountingEdit("Discarded");
        manager.addEdit(discarded);
        manager.undo();
        manager.addEdit(new CountingEdit("Replacement"));

        assertTrue(discarded.died);
        assertFalse(manager.canRedo());
    }

    private static final class CountingEdit extends AbstractUndoableEdit {
        private final String name;
        private int undoCount;
        private int redoCount;
        private boolean died;

        private CountingEdit(String name) {
            this.name = name;
        }

        @Override
        public void undo() {
            super.undo();
            undoCount++;
        }

        @Override
        public void redo() {
            super.redo();
            redoCount++;
        }

        @Override
        public void die() {
            died = true;
            super.die();
        }

        @Override
        public String getPresentationName() {
            return name;
        }
    }
}
