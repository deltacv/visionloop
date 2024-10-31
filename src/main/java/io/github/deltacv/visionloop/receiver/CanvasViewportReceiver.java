package io.github.deltacv.visionloop.receiver;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import io.github.deltacv.visionloop.processor.Processor;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.openftc.easyopencv.OpenCvViewRenderer;
import org.openftc.easyopencv.OpenCvViewport;

public abstract class CanvasViewportReceiver implements Receiver {

    private final OpenCvViewRenderer renderer;
    private Processor[] processors = null;

    private final Canvas canvas;
    private final Bitmap bitmap;

    private final OpenCvViewport.RenderHook renderHook = (canvas, onscreenWidth, onscreenHeight, scaleBmpPxToCanvasPx, canvasDensityScale, userContext) -> {
        if (processors != null) {
            for (Processor processor : processors) {
                processor.drawFrame(canvas, onscreenWidth, onscreenHeight, scaleBmpPxToCanvasPx, canvasDensityScale);
            }
        }
    };

    public CanvasViewportReceiver(Size viewportSize, String descriptor) {
        renderer = new OpenCvViewRenderer(false, descriptor);

        if(descriptor == null) {
            renderer.setFpsMeterEnabled(false);
        }

        bitmap = Bitmap.createBitmap((int) viewportSize.width, (int) viewportSize.height, Bitmap.Config.RGB_565);
        canvas = new Canvas(bitmap);
    }

    @Override
    public void init(Processor[] processors) {
        this.processors = processors;
    }

    @Override
    public void take(Mat frame) {
        renderer.render(frame, canvas, renderHook, null);
        afterRender(bitmap);
    }

    @Override
    public void notifyStatistics(float fps, int pipelineMs, int overheadMs) {
        if (renderer != null) {
            renderer.notifyStatistics(fps, pipelineMs, overheadMs);
        }
    }

    abstract void afterRender(Bitmap bitmap);
}
