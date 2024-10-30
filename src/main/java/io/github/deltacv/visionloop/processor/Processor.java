package io.github.deltacv.visionloop.processor;

import android.graphics.Canvas;
import io.github.deltacv.vision.external.util.Timestamped;
import org.opencv.core.Mat;

public interface Processor  {

    void init(Timestamped<Mat> input);

    Mat processFrame(Timestamped<Mat> input);

    void drawFrame(Canvas canvas, int onscreenWidth, int onscreenHeight, float scaleBmpPxToCanvasPx, float scaleCanvasDensity);

}