package io.github.deltacv.visionloop.processor;

import android.graphics.Canvas;
import io.github.deltacv.vision.external.util.Timestamped;
import org.firstinspires.ftc.vision.VisionProcessor;
import org.opencv.core.Mat;
import org.openftc.easyopencv.OpenCvPipeline;
import org.openftc.easyopencv.TimestampedOpenCvPipeline;
import org.openftc.easyopencv.TimestampedOpenCvPipelineAccessor;

public class OpenCvPipelineProcessor implements Processor {
    private final OpenCvPipeline pipeline;

    public static OpenCvPipelineProcessor fromVisionProcessor(VisionProcessor processor) {
        return new OpenCvPipelineProcessor(new VisionProcessorPipeline(processor));
    }

    public OpenCvPipelineProcessor(OpenCvPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public void init(Timestamped<Mat> input) {
        pipeline.init(input.getValue());
    }

    @Override
    public Mat processFrame(Timestamped<Mat> input) {
        if(pipeline instanceof TimestampedOpenCvPipeline) {
            TimestampedOpenCvPipelineAccessor.setTimestamp((TimestampedOpenCvPipeline) pipeline, input.getTimestamp());
        }

        return pipeline.processFrame(input.getValue());
    }

    @Override
    public void drawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx, float scaleCanvasDensity) {
        Object userCtx = pipeline.getUserContextForDrawHook();

        if(userCtx != null) {
            pipeline.onDrawFrame(canvas, onscreenWidth, onscreenHeight, scaleBmpPxToCanvasPx, scaleCanvasDensity, userCtx);
        }
    }

    static class VisionProcessorPipeline extends TimestampedOpenCvPipeline {

        private final VisionProcessor processor;

        public VisionProcessorPipeline(VisionProcessor processor) {
            this.processor = processor;
        }

        @Override
        public void init(Mat input) {
            processor.init(input.width(), input.height(), null);
        }

        @Override
        public Mat processFrame(Mat input, long captureTimeNanos) {
            Object userCtx = processor.processFrame(input, captureTimeNanos);
            requestViewportDrawHook(userCtx);
            return input;
        }

        @Override
        public void onDrawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx, float scaleCanvasDensity, Object userContext) {
            processor.onDrawFrame(canvas, onscreenWidth, onscreenHeight, scaleBmpPxToCanvasPx, scaleCanvasDensity, userContext);
        }
    }
}