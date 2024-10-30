package io.github.deltacv.visionloop.processor;

import android.graphics.Canvas;
import io.github.deltacv.vision.external.util.Timestamped;
import org.opencv.core.Mat;

/**
 * The {@code Processor} interface defines a contract for processing frames
 * within a vision processing pipeline. Implementations of this interface
 * will handle the initialization, processing, drawing, and tap event handling
 * for visual processing of images.
 */
public interface Processor {

    /**
     * Initializes the processor with the input frame.
     *
     * @param input A {@link Timestamped} object containing the input frame as a {@link Mat}.
     */
    void init(Timestamped<Mat> input);

    /**
     * Processes a frame and returns the processed result.
     *
     * @param input A {@link Timestamped} object containing the input frame as a {@link Mat}.
     * @return The processed frame as a {@link Mat}.
     */
    Mat processFrame(Timestamped<Mat> input);

    /**
     * Invoked when the viewport is tapped, allowing the processor to respond to user interactions.
     */
    void onViewportTapped();

    /**
     * Draws the processed frame on the provided canvas.
     *
     * @param canvas The {@link Canvas} on which to draw the frame.
     * @param onscreenWidth The width of the viewport on the screen.
     * @param onscreenHeight The height of the viewport on the screen.
     * @param scaleBmpPxToCanvasPx The scale factor for converting bitmap pixels to canvas pixels.
     * @param scaleCanvasDensity The density scale of the canvas.
     */
    void drawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx, float scaleCanvasDensity);

    /**
     * A simple interface extending {@link Processor} that provides default no-operation
     * implementations for all methods. This can be used as a functional interface and
     * the base for simpler processors that do not require full functionality.
     */
    interface Simple extends Processor {

        /**
         * Initializes the processor with the input frame. This is a no-operation
         * implementation.
         *
         * @param input A {@link Timestamped} object containing the input frame as a {@link Mat}.
         */
        @Override
        default void init(Timestamped<Mat> input) {
            // no-op
        }

        /**
         * Processes a frame and returns the processed result. Implementations must provide
         * this method.
         *
         * @param input A {@link Timestamped} object containing the input frame as a {@link Mat}.
         * @return The processed frame as a {@link Mat}.
         */
        @Override
        Mat processFrame(Timestamped<Mat> input);

        /**
         * Invoked when the viewport is tapped. This is a no-operation implementation.
         */
        @Override
        default void onViewportTapped() {
            // no-op
        }

        /**
         * Draws the processed frame on the provided canvas. This is a no-operation
         * implementation.
         *
         * @param canvas The {@link Canvas} on which to draw the frame.
         * @param onscreenWidth The width of the viewport on the screen.
         * @param onscreenHeight The height of the viewport on the screen.
         * @param scaleBmpPxToCanvasPx The scale factor for converting bitmap pixels to canvas pixels.
         * @param scaleCanvasDensity The density scale of the canvas.
         */
        @Override
        default void drawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx, float scaleCanvasDensity) {
            // no-op
        }
    }
}