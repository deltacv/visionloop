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

public class WebcamSource implements InputSource {

    static {
        OpenCV.loadLocally();
    }

    public static WebcamSource[] available(WebcamBackend backend) {
        List<Webcam> webcams = backend.getAvailableWebcams();
        WebcamSource[] sources = new WebcamSource[webcams.size()];

        for (int i = 0; i < webcams.size(); i++) {
            sources[i] = new WebcamSource(webcams.get(i));
        }

        return sources;
    }

    public static WebcamSource[] available() {
        return available(OpenCvWebcamBackend.INSTANCE);
    }

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

    public static WebcamSource withIndex(int index) {
        return new WebcamSource(new OpenCvWebcam(index));
    }

    private Webcam webcam;
    private Mat frame = new Mat();

    public WebcamSource(Webcam webcam) {
        this.webcam = webcam;
    }

    @Override
    public void open() {
        webcam.open();
    }

    @Override
    public Timestamped<Mat> update() {
        webcam.read(frame);
        return new Timestamped<>(frame, System.currentTimeMillis());
    }

    @Override
    public void close() {
        webcam.close();
    }

    public Webcam getWebcam() {
        return webcam;
    }

}
