package io.github.deltacv.visionloop.receiver;

import io.github.deltacv.visionloop.processor.Processor;
import org.opencv.core.Mat;

public interface Receiver {
    void init(Processor[] processors);
    void take(Mat frame);
    void notifyStatistics(float fps, int pipelineMs, int overheadMs);
    void close();
}
