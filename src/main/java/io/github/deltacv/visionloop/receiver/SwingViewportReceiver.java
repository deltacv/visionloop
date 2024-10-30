package io.github.deltacv.visionloop.receiver;

import android.graphics.Canvas;
import io.github.deltacv.vision.external.gui.SwingOpenCvViewport;
import io.github.deltacv.visionloop.processor.Processor;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.openftc.easyopencv.OpenCvViewport;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayDeque;

/**
 * The {@code SwingViewportReceiver} class is a Swing-based implementation of the
 * {@link Receiver} interface that displays processed frames in a viewport using
 * Skiko. It allows user interactions, such as tapping on the viewport, and
 * visualizes statistics related to frame processing.
 */
public class SwingViewportReceiver extends JFrame implements Receiver {

    private SwingOpenCvViewport viewport;
    private final Size size;
    private final ArrayDeque<Boolean> viewportTapped = new ArrayDeque<>();

    /**
     * Constructs a {@code SwingViewportReceiver} with the specified title and size.
     *
     * @param title The title of the window.
     * @param size The size of the viewport.
     */
    public SwingViewportReceiver(String title, Size size) {
        super(title);
        this.size = size;
    }

    /**
     * Constructs a {@code SwingViewportReceiver} with the specified size and a
     * default title.
     *
     * @param size The size of the viewport.
     */
    public SwingViewportReceiver(Size size) {
        this("deltacv VisionLoop", size);
    }

    /**
     * Constructs a {@code SwingViewportReceiver} with a default size of 640x480
     * pixels and a default title.
     */
    public SwingViewportReceiver() {
        this(new Size(640, 480));
    }

    /**
     * Initializes the viewport and sets up the GUI components, including adding
     * a mouse listener for user interactions.
     *
     * @param processors An array of processors that will provide processed frames.
     */
    @Override
    public void init(Processor[] processors) {
        SwingUtilities.invokeLater(() -> {
            viewport = new SwingOpenCvViewport(size, getTitle());

            JLayeredPane skiaPanel = viewport.skiaPanel();
            skiaPanel.setLayout(new BorderLayout());

            add(skiaPanel);

            viewport.getComponent().addMouseListener(new MouseListener() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    synchronized (viewportTapped) {
                        viewportTapped.add(true);
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {}

                @Override
                public void mouseReleased(MouseEvent e) { }
                @Override
                public void mouseEntered(MouseEvent e) { }
                @Override
                public void mouseExited(MouseEvent e) { }
            });

            setBackground(Color.BLACK);
            setSize((int) size.width, (int) size.width);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLocationRelativeTo(null);
            setVisible(true);

            viewport.setRenderHook(new ReceiverRenderHook(processors));
            viewport.setDark(true);
            viewport.activate();
        });
    }

    /**
     * Displays a processed frame in the viewport.
     *
     * @param frame The {@link Mat} object representing the processed frame to be
     *              displayed.
     */
    @Override
    public void take(Mat frame) {
        if(viewport != null && frame != null)
            viewport.post(frame, new Object());
    }

    /**
     * Polls the viewport for user tap events.
     *
     * @return {@code true} if a tap event has occurred; {@code false} otherwise.
     */
    @Override
    public boolean pollViewportTapped() {
        synchronized (viewportTapped) {
            return Boolean.TRUE.equals(viewportTapped.poll());
        }
    }

    /**
     * Notifies the viewport of processing statistics such as frames per second
     * and processing overhead.
     *
     * @param fps The frames per second processed.
     * @param pipelineMs The time taken for the processing pipeline in milliseconds.
     * @param overheadMs The overhead time in milliseconds incurred during processing.
     */
    @Override
    public void notifyStatistics(float fps, int pipelineMs, int overheadMs) {
        if(viewport != null) {
            viewport.getRenderer().notifyStatistics(fps, pipelineMs, overheadMs);
        }
    }

    /**
     * Closes the viewport receiver and releases any associated resources.
     *
     * <p>This method should be called when the receiver is no longer needed to
     * allow for cleanup and resource deallocation.</p>
     */
    @Override
    public void close() {
        final Object closeLock = new Object();

        SwingUtilities.invokeLater(() -> {
            synchronized (closeLock) {
                if(viewport != null) {
                    viewport.clearViewport();
                    viewport.deactivate();
                    viewport.setRenderHook(new NoOpRenderHook());
                    viewport = null;
                }

                dispose();

                closeLock.notifyAll();
            }
        });

        synchronized (closeLock) {
            try {
                closeLock.wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * A no-operation implementation of the {@link OpenCvViewport.RenderHook}
     * interface, used to clear the render hook when closing the viewport.
     */
    private static class NoOpRenderHook implements OpenCvViewport.RenderHook {

        @Override
        public void onDrawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx, float canvasDensityScale, Object userContext) {
        }
    }

    /**
     * A render hook implementation that draws frames processed by the given
     * processors onto the viewport.
     */
    private static class ReceiverRenderHook implements OpenCvViewport.RenderHook {

        private final Processor[] processors;

        private ReceiverRenderHook(Processor[] processors) {
            this.processors = processors;
        }

        @Override
        public void onDrawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx, float canvasDensityScale, Object userContext) {
            for (Processor processor : processors) {
                processor.drawFrame(canvas, onscreenWidth, onscreenHeight, scaleBmpPxToCanvasPx, canvasDensityScale);
            }
        }
    }
}