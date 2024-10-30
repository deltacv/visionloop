package io.github.deltacv.visionloop.input;

import io.github.deltacv.vision.external.util.Timestamped;
import org.opencv.core.Mat;

/**
 * The {@code InputSource} interface defines a contract for input sources
 * that provide frames for processing in a vision loop. Implementing classes
 * should handle the opening, updating, and closing of the input source.
 */
public interface InputSource {

    /**
     * Opens the input source, preparing it for frame acquisition. This method
     * should initialize any necessary resources for capturing input.
     */
    void open();

    /**
     * Updates the input source and retrieves the latest frame.
     *
     * @return A {@link Timestamped} object containing the latest frame as a
     *         {@link Mat} along with its timestamp. If no frame is available,
     *         the implementation may return null or an empty {@link Timestamped}.
     */
    Timestamped<Mat> update();

    /**
     * Closes the input source, releasing any resources that were acquired
     * during opening. This method should be called to ensure proper cleanup
     * and prevent resource leaks.
     */
    void close();
}