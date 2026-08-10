package org.igv.ui.genome;

import org.igv.ui.IGVDialog;
import org.igv.ui.util.UIUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Modal, cancellable status dialog for loading a genome and its default annotation tracks. */
public final class GenomeLoadingDialog {

    private final AtomicBoolean closed = new AtomicBoolean();
    private final CountDownLatch shown = new CountDownLatch(1);
    private final IGVDialog dialog;

    private GenomeLoadingDialog(Frame owner, String genomeLabel, Runnable cancelAction) {
        dialog = new IGVDialog(owner);
        dialog.setTitle("Loading Genome");
        dialog.setModal(true);
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JLabel label = new JLabel("Loading genome: " + genomeLabel);
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        JButton stop = new JButton("Stop");

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        content.add(label, BorderLayout.NORTH);
        content.add(progress, BorderLayout.CENTER);
        content.add(stop, BorderLayout.SOUTH);
        dialog.setContentPane(content);

        Runnable cancelAndClose = () -> {
            if (closed.compareAndSet(false, true)) {
                cancelAction.run();
                dialog.dispose();
            }
        };
        stop.addActionListener(e -> cancelAndClose.run());
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                shown.countDown();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                cancelAndClose.run();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                shown.countDown();
            }
        });

        dialog.pack();
        dialog.setLocationRelativeTo(owner);
    }

    public static GenomeLoadingDialog show(Frame owner, String genomeLabel, Runnable cancelAction) {
        GenomeLoadingDialog[] holder = new GenomeLoadingDialog[1];
        UIUtilities.invokeAndWaitOnEventThread(() ->
                holder[0] = new GenomeLoadingDialog(owner, genomeLabel, cancelAction));
        GenomeLoadingDialog loadingDialog = holder[0];
        UIUtilities.invokeOnEventThread(() -> {
            if (!loadingDialog.closed.get()) {
                loadingDialog.dialog.setVisible(true);
            }
        });
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                // Do not begin genome network access until the loading UI has actually opened.
                loadingDialog.shown.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return loadingDialog;
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            UIUtilities.invokeOnEventThread(dialog::dispose);
        }
    }
}
