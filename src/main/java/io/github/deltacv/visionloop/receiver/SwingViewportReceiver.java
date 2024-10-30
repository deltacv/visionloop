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

public class SwingViewportReceiver extends JFrame implements Receiver {

    private SwingOpenCvViewport viewport;
    private final Size size;
    private final ArrayDeque<Boolean> viewportTapped = new ArrayDeque<>();

    public SwingViewportReceiver(String title, Size size) {
        super(title);
        this.size = size;
    }

    public SwingViewportReceiver(Size size) {
        this("deltacv VisionLoop", size);
    }

    public SwingViewportReceiver() {
        this(new Size(640, 480));
    }

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
            setVisible(true);

            viewport.setRenderHook(new ReceiverRenderHook(processors));
            viewport.setDark(true);
            viewport.activate();
        });
    }

    @Override
    public void take(Mat frame) {
        if(viewport != null && frame != null)
            viewport.post(frame, new Object());
    }

    @Override
    public boolean pollViewportTapped() {
        synchronized (viewportTapped) {
            return Boolean.TRUE.equals(viewportTapped.poll());
        }
    }

    @Override
    public void notifyStatistics(float fps, int pipelineMs, int overheadMs) {
        if(viewport != null) {
            viewport.getRenderer().notifyStatistics(fps, pipelineMs, overheadMs);
        }
    }

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

    private static class NoOpRenderHook implements OpenCvViewport.RenderHook {

        @Override
        public void onDrawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx, float canvasDensityScale, Object userContext) {
        }
    }

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