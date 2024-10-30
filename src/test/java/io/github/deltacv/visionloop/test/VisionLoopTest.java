package io.github.deltacv.visionloop.test;

import io.github.deltacv.visionloop.AsyncVisionLoopRunner;
import io.github.deltacv.visionloop.VisionLoop;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.junit.jupiter.api.Test;

public class VisionLoopTest {

    @Test
    public void test() throws InterruptedException {
        VisionLoop loop = VisionLoop.withWebcamIndex(0)
                .then(AprilTagProcessor.easyCreateWithDefaults())
                .withLiveView()
                .build();

        AsyncVisionLoopRunner runner = loop.runAsync();

        Thread.sleep(25000);

        runner.stop();
    }
}