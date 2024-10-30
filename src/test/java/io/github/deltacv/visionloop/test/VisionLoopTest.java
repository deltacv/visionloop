package io.github.deltacv.visionloop.test;

import io.github.deltacv.visionloop.VisionLoop;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.junit.jupiter.api.Test;
import org.opencv.core.Mat;
import org.openftc.easyopencv.OpenCvPipeline;

public class VisionLoopTest {

    @Test
    public void test() {
        VisionLoop loop = VisionLoop.withWebcamIndex(0)
                .then(AprilTagProcessor.easyCreateWithDefaults())
                .show()
                .build();

        loop.runBlocking();
    }

    static class TestPipeline extends OpenCvPipeline {
        @Override
        public Mat processFrame(Mat input) {
            return input;
        }
    }

}