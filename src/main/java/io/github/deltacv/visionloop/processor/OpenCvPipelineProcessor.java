package io.github.deltacv.visionloop.processor;

import android.graphics.Canvas;
import io.github.deltacv.vision.external.util.Timestamped;
import org.firstinspires.ftc.vision.VisionProcessor;
import org.opencv.core.Mat;
import org.openftc.easyopencv.OpenCvPipeline;
import org.openftc.easyopencv.TimestampedOpenCvPipeline;
import org.openftc.easyopencv.TimestampedOpenCvPipelineAccessor;

/**
 * The {@code OpenCvPipelineProcessor} class integrates an OpenCV pipeline
 * with the {@link Processor} interface, allowing for the initialization,
 * processing, and drawing of frames in a vision processing loop.
 */
public class OpenCvPipelineProcessor implements Processor {
    private final OpenCvPipeline pipeline;

    /**
     * Creates an instance of {@code OpenCvPipelineProcessor} from a
     * {@link VisionProcessor}.
     *
     * @param processor The {@link VisionProcessor} to integrate with.
     * @return A new instance of {@code OpenCvPipelineProcessor}.
     */
    public static OpenCvPipelineProcessor fromVisionProcessor(VisionProcessor processor) {
        return new OpenCvPipelineProcessor(new VisionProcessorPipeline(processor));
    }

    /**
     * Constructs an {@code OpenCvPipelineProcessor} with the specified
     * OpenCV pipeline.
     *
     * @param pipeline The {@link OpenCvPipeline} to be processed.
     */
    public OpenCvPipelineProcessor(OpenCvPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * Initializes the processor with the given input frame.
     *
     * @param input A {@link Timestamped} object containing the input frame as a {@link Mat}.
     */
    @Override
    public void init(Timestamped<Mat> input) {
        pipeline.init(input.getValue());
    }

    /**
     * Processes the input frame and returns the processed result.
     *
     * @param input A {@link Timestamped} object containing the input frame as a {@link Mat}.
     * @return The processed frame as a {@link Mat}.
     */
    @Override
    public Mat processFrame(Timestamped<Mat> input) {
        if (pipeline instanceof TimestampedOpenCvPipeline) {
            TimestampedOpenCvPipelineAccessor.setTimestamp((TimestampedOpenCvPipeline) pipeline, input.getTimestamp());
        }
        return pipeline.processFrame(input.getValue());
    }

    /**
     * Invoked when the viewport is tapped, allowing the processor to respond to user interactions.
     */
    @Override
    public void onViewportTapped() {
        pipeline.onViewportTapped();
    }

    /**
     * Draws the processed frame on the provided canvas.
     *
     * @param canvas The {@link Canvas} on which to draw the frame.
     * @param onscreenWidth The width of the viewport on the screen.
     * @param onscreenHeight The height of the viewport on the screen.
     * @param scaleBmpPxToCanvasPx The scale factor for converting bitmap pixels to canvas pixels.
     * @param scaleCanvasDensity The density scale of the canvas.
     */
    @Override
    public void drawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx, float scaleCanvasDensity) {
        Object userCtx = pipeline.getUserContextForDrawHook();

        if (userCtx != null) {
            pipeline.onDrawFrame(canvas, onscreenWidth, onscreenHeight, scaleBmpPxToCanvasPx, scaleCanvasDensity, userCtx);
        }
    }

    /**
     * The {@code VisionProcessorPipeline} class extends {@link TimestampedOpenCvPipeline}
     * to integrate with a {@link VisionProcessor}, handling initialization,
     * processing, and drawing frames.
     */
    static class VisionProcessorPipeline extends TimestampedOpenCvPipeline {

        private final VisionProcessor processor;

        /**
         * Constructs a {@code VisionProcessorPipeline} with the specified
         * {@link VisionProcessor}.
         *
         * @param processor The {@link VisionProcessor} to integrate with.
         */
        public VisionProcessorPipeline(VisionProcessor processor) {
            this.processor = processor;
        }

        /**
         * Initializes the pipeline with the input frame.
         *
         * @param input The input frame as a {@link Mat}.
         */
        @Override
        public void init(Mat input) {
            processor.init(input.width(), input.height(), null);
        }

        /**
         * Processes a frame and returns the result. This method also requests
         * the viewport to draw the processed frame.
         *
         * @param input The input frame as a {@link Mat}.
         * @param captureTimeNanos The capture time in nanoseconds.
         * @return The processed frame as a {@link Mat}.
         */
        @Override
        public Mat processFrame(Mat input, long captureTimeNanos) {
            Object userCtx = processor.processFrame(input, captureTimeNanos);
            requestViewportDrawHook(userCtx);
            return input;
        }

        /**
         * Draws the processed frame on the provided canvas.
         *
         * @param canvas The {@link Canvas} on which to draw the frame.
         * @param onscreenWidth The width of the viewport on the screen.
         * @param onscreenHeight The height of the viewport on the screen.
         * @param scaleBmpPxToCanvasPx The scale factor for converting bitmap pixels to canvas pixels.
         * @param scaleCanvasDensity The density scale of the canvas.
         * @param userContext The user context passed for drawing.
         */
        @Override
        public void onDrawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx, float scaleCanvasDensity, Object userContext) {
            processor.onDrawFrame(canvas, onscreenWidth, onscreenHeight, scaleBmpPxToCanvasPx, scaleCanvasDensity, userContext);
        }
    }
}