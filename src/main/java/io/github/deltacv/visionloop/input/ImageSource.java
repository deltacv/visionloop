package io.github.deltacv.visionloop.input;

import io.github.deltacv.vision.external.util.Timestamped;
import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * The {@code ImageSource} class implements the {@link InputSource} interface
 * to provide image frames from a specified image file. It handles loading,
 * scaling, and releasing the image resource.
 */
public class ImageSource implements InputSource {

    static {
        OpenCV.loadLocally();
    }

    private final String path;
    private final double scale;

    private Mat image = null;
    private Mat imageCopy = new Mat();

    /**
     * Constructs an {@code ImageSource} with the specified image path and scale.
     *
     * @param path the path to the image file. This can be a local file path
     *             or a resource path.
     * @param scale the scaling factor to resize the image. A value of 1.0 means
     *              no scaling.
     */
    public ImageSource(String path, double scale) {
        this.path = path;
        this.scale = scale;
    }

    /**
     * Opens the image source by loading the image from the specified path.
     * If the image is not found, it attempts to load it from the resources.
     * The image is then resized based on the specified scale and converted
     * to RGB color format.
     *
     * @throws IllegalStateException if the image cannot be found or read.
     */
    @Override
    public void open() {
        File file = new File(path);

        if (!file.exists()) {
            // try to load from resources
            try (var stream = ImageSource.class.getResourceAsStream(path)) {
                // extract to temp file
                String extension = path.substring(path.lastIndexOf('.'));

                File tempFile = File.createTempFile(Integer.toHexString(path.hashCode()), extension);
                tempFile.deleteOnExit();

                try (var out = new FileOutputStream(tempFile)) {
                    out.write(stream.readAllBytes());
                }

                file = tempFile;
            } catch (Exception e) {
                throw new IllegalStateException("Image could not be found at path: " + path);
            }
        }

        image = Imgcodecs.imread(file.getAbsolutePath());
        if (image.empty()) {
            throw new IllegalStateException("Image could not be read from path: " + path);
        }

        if (scale != 1.0) {
            Imgproc.resize(image, image, new Size(image.width() * scale, image.height() * scale), 0, 0, Imgproc.INTER_AREA);
        }

        Imgproc.cvtColor(image, image, Imgproc.COLOR_BGR2RGB);
    }

    /**
     * Updates the image source by copying the current image frame and returning
     * it wrapped in a {@link Timestamped} object along with the current timestamp.
     *
     * @return A {@link Timestamped} object containing a copy of the current image frame
     *         and the timestamp of the update.
     */
    @Override
    public Timestamped<Mat> update() {
        image.copyTo(imageCopy);
        return new Timestamped<>(imageCopy, System.currentTimeMillis());
    }

    /**
     * Closes the image source, releasing any resources associated with the image.
     */
    @Override
    public void close() {
        if (image != null) image.release();
        imageCopy.release();
    }
}