package io.github.deltacv.visionloop.input;

import android.annotation.Nullable;
import io.github.deltacv.steve.Webcam;
import io.github.deltacv.steve.WebcamBackend;
import io.github.deltacv.steve.opencv.OpenCvWebcam;
import io.github.deltacv.steve.opencv.OpenCvWebcamBackend;
import io.github.deltacv.steve.openpnp.OpenPnpBackend;
import io.github.deltacv.vision.external.util.Timestamped;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;

import java.util.List;

/**
 * The {@code WebcamSource} class implements the {@link InputSource} interface
 * to provide image frames from a connected webcam. It allows opening the webcam,
 * reading frames, and closing the connection.
 */
public class WebcamSource implements InputSource {

    static {
        OpenCV.loadLocally();
    }

    /**
     * Retrieves an array of available {@code WebcamSource} instances
     * from the specified webcam backend.
     *
     * @param backend the webcam backend to query for available webcams.
     * @return an array of {@code WebcamSource} instances representing
     *         the available webcams.
     */
    public static WebcamSource[] available(WebcamBackend backend) {
        List<Webcam> webcams = backend.getAvailableWebcams();
        WebcamSource[] sources = new WebcamSource[webcams.size()];

        for (int i = 0; i < webcams.size(); i++) {
            sources[i] = new WebcamSource(webcams.get(i));
        }

        return sources;
    }

    /**
     * Retrieves an array of available {@code WebcamSource} instances
     * using the default OpenCV webcam backend.
     *
     * @return an array of {@code WebcamSource} instances representing
     *         the available webcams.
     */
    public static WebcamSource[] available() {
        return available(OpenCvWebcamBackend.INSTANCE);
    }

    /**
     * Retrieves a {@code WebcamSource} instance with the specified webcam name.
     *
     * @param name the name of the desired webcam.
     * @return a {@code WebcamSource} instance for the webcam with the specified
     *         name, or {@code null} if no such webcam exists.
     */
    @Nullable
    public static WebcamSource withName(String name) {
        List<Webcam> webcams = OpenPnpBackend.INSTANCE.getAvailableWebcams();

        for (Webcam webcam : webcams) {
            if (webcam.getName().equals(name)) {
                return new WebcamSource(webcam);
            }
        }

        return null;
    }

    /**
     * Retrieves a {@code WebcamSource} instance using the specified webcam index.
     *
     * @param index the index of the desired webcam.
     * @return a {@code WebcamSource} instance for the webcam at the specified index.
     */
    public static WebcamSource withIndex(int index) {
        return new WebcamSource(new OpenCvWebcam(index));
    }

    private Webcam webcam;
    private Mat frame = new Mat();

    /**
     * Constructs a {@code WebcamSource} for the specified {@link Webcam}.
     *
     * @param webcam the {@link Webcam} instance to be used for capturing frames.
     */
    public WebcamSource(Webcam webcam) {
        this.webcam = webcam;
    }

    /**
     * Opens the connection to the webcam, preparing it for capturing frames.
     */
    @Override
    public void open() {
        webcam.open();
    }

    /**
     * Updates the source by reading the current frame from the webcam.
     * Returns the frame wrapped in a {@link Timestamped} object along with
     * the current timestamp.
     *
     * @return A {@link Timestamped} object containing the current webcam frame
     *         and the timestamp of the update.
     */
    @Override
    public Timestamped<Mat> update() {
        webcam.read(frame);
        return new Timestamped<>(frame, System.currentTimeMillis());
    }

    /**
     * Closes the connection to the webcam, releasing any associated resources.
     */
    @Override
    public void close() {
        webcam.close();
    }

    /**
     * Retrieves the underlying {@link Webcam} instance used by this {@code WebcamSource}.
     *
     * @return the underlying {@link Webcam} instance.
     */
    public Webcam getWebcam() {
        return webcam;
    }
}