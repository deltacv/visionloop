package io.github.deltacv.visionloop.receiver;

import io.github.deltacv.visionloop.processor.Processor;
import org.opencv.core.Mat;

/**
 * The {@code Receiver} interface defines a contract for classes that
 * handle the output of processed frames in a vision loop. It provides methods
 * for initializing receivers with processors, taking in frames, and
 * handling user interactions and statistics related to the processing.
 *
 * <p>Implementing classes must define the behavior for managing frame
 * data, responding to viewport interactions, and tracking performance metrics.</p>
 */
public interface Receiver {

    /**
     * Initializes the receiver with the specified processors.
     *
     * <p>This method should be called before any frames are processed to ensure
     * the receiver is prepared to handle data from the given processors.</p>
     *
     * @param processors An array of processors that will provide processed frames.
     */
    void init(Processor[] processors);

    /**
     * Takes a processed frame for further handling.
     *
     * <p>This method is called when a new frame has been processed by the
     * different pipelines. Implementing classes should define how to handle
     * the resulting frame data.</p>
     *
     * @param frame The {@link Mat} object representing the frame that has been
     *              processed by the pipelines.
     */
    void take(Mat frame);


    /**
     * Checks if the viewport has been tapped by the user.
     *
     * <p>This method should return {@code true} if a tap event has occurred,
     * allowing the receiver to respond accordingly (e.g., for user interactions
     * such as selecting a region of interest).</p>
     *
     * @return {@code true} if the viewport has been tapped; {@code false} otherwise.
     */
    boolean pollViewportTapped();

    /**
     * Notifies the receiver of performance statistics.
     *
     * <p>This method can be used to send performance metrics such as frames
     * per second (fps) and processing overhead times. Implementing classes
     * can use this information for logging or adaptive processing strategies.</p>
     *
     * @param fps The frames per second processed.
     * @param pipelineMs The time taken for the processing pipeline in milliseconds.
     * @param overheadMs The overhead time in milliseconds incurred during processing.
     */
    void notifyStatistics(float fps, int pipelineMs, int overheadMs);

    /**
     * Closes the receiver and releases any resources it holds.
     *
     * <p>This method should be called when the receiver is no longer needed,
     * allowing it to perform cleanup tasks and free up resources.</p>
     */
    void close();
}
