package io.github.deltacv.visionloop.receiver;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import io.github.deltacv.steve.util.EvictingBlockingQueue;
import io.github.deltacv.visionloop.processor.Processor;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.openftc.easyopencv.MatRecycler;
import org.openftc.easyopencv.OpenCvViewRenderer;
import org.openftc.easyopencv.OpenCvViewport;

import java.util.concurrent.ArrayBlockingQueue;

public abstract class CanvasViewportReceiver implements Receiver {

    private static final int RENDER_QUEUE_SIZE = 2;

    private final OpenCvViewRenderer renderer;
    private Processor[] processors = null;

    private final Bitmap bitmap;
    private final Canvas canvas;

    private final RenderThread renderThread = new RenderThread();

    private final MatRecycler matRecycler = new MatRecycler(RENDER_QUEUE_SIZE + 2);
    private final EvictingBlockingQueue<MatRecycler.RecyclableMat> frames = new EvictingBlockingQueue<>(new ArrayBlockingQueue<>(RENDER_QUEUE_SIZE));

    private final OpenCvViewport.RenderHook renderHook = (canvas, onscreenWidth, onscreenHeight, scaleBmpPxToCanvasPx, canvasDensityScale, userContext) -> {
        if (processors != null) {
            for (Processor processor : processors) {
                processor.drawFrame(canvas, onscreenWidth, onscreenHeight, scaleBmpPxToCanvasPx, canvasDensityScale);
            }
        }
    };

    public CanvasViewportReceiver(Size viewportSize, String descriptor) {
        renderer = new OpenCvViewRenderer(false, descriptor);

        frames.setEvictAction(MatRecycler.RecyclableMat::returnMat);

        if(descriptor == null) {
            renderer.setFpsMeterEnabled(false);
        }

        bitmap = Bitmap.createBitmap((int) viewportSize.width, (int) viewportSize.height, Bitmap.Config.RGB_565);
        canvas = new Canvas(bitmap);
    }

    @Override
    public void init(Processor[] processors) {
        this.processors = processors;
        renderThread.start();
    }

    @Override
    public void take(Mat frame) {
        try {
            MatRecycler.RecyclableMat recyclableMat = matRecycler.takeMatOrInterrupt();

            frame.copyTo(recyclableMat);
            frames.add(recyclableMat);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void notifyStatistics(float fps, int pipelineMs, int overheadMs) {
        if (renderer != null) {
            renderer.notifyStatistics(fps, pipelineMs, overheadMs);
        }
    }

    abstract void afterRender(Bitmap bitmap);

    @Override
    public void close() {
        renderThread.interrupt();
        matRecycler.releaseAll();
    }

    private class RenderThread extends Thread {

        private RenderThread() {
            super("CanvasViewportReceiver-RenderThread-" + CanvasViewportReceiver.this.hashCode());
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                MatRecycler.RecyclableMat frame = frames.poll();
                if (frame == null) {
                    continue;
                }

                renderer.render(frame, canvas, renderHook, null);
                afterRender(bitmap);

                frame.returnMat();
            }
        }
    }
}
