package org.igv.ui.undo;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Application edit history, separate from genomic navigation history. */
public final class IGVUndoManager extends UndoManager {

    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    public IGVUndoManager() {
        setLimit(20);
    }

    @Override
    public synchronized boolean addEdit(UndoableEdit edit) {
        boolean added = super.addEdit(edit);
        fireChanged();
        return added;
    }

    @Override
    public synchronized void undo() {
        if (!canUndo()) return;
        super.undo();
        fireChanged();
    }

    @Override
    public synchronized void redo() {
        if (!canRedo()) return;
        super.redo();
        fireChanged();
    }

    @Override
    public synchronized void discardAllEdits() {
        super.discardAllEdits();
        fireChanged();
    }

    public void addChangeListener(ChangeListener listener) {
        if (listener != null) listeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listeners.remove(listener);
    }

    private void fireChanged() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : listeners) listener.stateChanged(event);
    }
}
