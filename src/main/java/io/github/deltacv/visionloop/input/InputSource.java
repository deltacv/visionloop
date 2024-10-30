package io.github.deltacv.visionloop.input;

import io.github.deltacv.vision.external.util.Timestamped;
import org.opencv.core.Mat;

public interface InputSource {
    void open();

    Timestamped<Mat> update();

    void close();
}
