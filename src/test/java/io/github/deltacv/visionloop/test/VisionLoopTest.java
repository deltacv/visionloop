package io.github.deltacv.visionloop.test;

import io.github.deltacv.visionloop.AsyncVisionLoopRunner;
import io.github.deltacv.visionloop.VisionLoop;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.junit.jupiter.api.Test;

public class VisionLoopTest {
    @Test
    public void test() {
        VisionLoop loop = VisionLoop.withImage("/ug_4.jpg", 0.1)
                .then(AprilTagProcessor.easyCreateWithDefaults())
                .onViewportTapped(() -> System.out.println("Tapped!"))
                .withLiveView()
                .build();

        AsyncVisionLoopRunner runner = loop.runAsync();
        runner.join();
    }
}