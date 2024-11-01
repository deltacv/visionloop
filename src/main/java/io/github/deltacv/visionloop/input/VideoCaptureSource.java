package io.github.deltacv.visionloop.input;

import io.github.deltacv.vision.external.util.Timestamped;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

public class VideoCaptureSource implements InputSource {

    private final VideoCapture capture;
    private final String path;

    private final Mat captureMat = new Mat();
    private final Mat userMat = new Mat();

    public VideoCaptureSource(String path) {
        this.capture = new VideoCapture();
        this.path = path;
    }

    @Override
    public void open() {
        capture.open(path);

        if(!capture.isOpened()) {
            throw new IllegalStateException("VideoCapture " + path + " failed to open");
        }
    }

    @Override
    public Timestamped<Mat> update() {
        capture.read(captureMat);
        Imgproc.cvtColor(captureMat, userMat, Imgproc.COLOR_BGR2RGB);

        return new Timestamped<>(userMat, System.currentTimeMillis());
    }

    @Override
    public void close() {
        if(capture.isOpened()) {
            capture.release();
        }
    }

}
