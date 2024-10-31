package io.github.deltacv.visionloop.test;

import io.github.deltacv.visionloop.VisionLoop;
import io.github.deltacv.visionloop.receiver.MjpegHttpStreamerReceiver;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.junit.jupiter.api.Test;
import org.opencv.core.Size;

public class VisionLoopStreamingTest {

    @Test
    public void mjpegSteamingTest() {
        VisionLoop visionLoop = VisionLoop.withWebcamIndex(0)
                .then(AprilTagProcessor.easyCreateWithDefaults())
                .streamTo(new MjpegHttpStreamerReceiver(8080, new Size(640, 480), "pepe"))
                .build();

        visionLoop.runBlocking();
    }

}