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

public class ImageSource implements InputSource {

    static {
        OpenCV.loadLocally();
    }

    private final String path;
    private final double scale;

    private Mat image = null;
    private Mat imageCopy = new Mat();

    public ImageSource(String path, double scale) {
        this.path = path;
        this.scale = scale;
    }

    @Override
    public void open() {
        File file = new File(path);

        if(!file.exists()) {
            // try to load from resources
            try(var stream = ImageSource.class.getResourceAsStream(path)) {
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
        if(image.empty()) {
            throw new IllegalStateException("Image could not be read from path: " + path);
        }

        if(scale != 1.0) {
            Imgproc.resize(image, image, new Size(image.width() * scale, image.height() * scale), 0, 0, Imgproc.INTER_AREA);
        }

        Imgproc.cvtColor(image, image, Imgproc.COLOR_BGR2RGB);
    }

    @Override
    public Timestamped<Mat> update() {
        image.copyTo(imageCopy);
        return new Timestamped<>(imageCopy, System.currentTimeMillis());
    }

    @Override
    public void close() {
        if(image != null) image.release();
        imageCopy.release();
    }
}
