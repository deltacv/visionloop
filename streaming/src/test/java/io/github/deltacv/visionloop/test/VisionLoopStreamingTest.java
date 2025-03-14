package io.github.deltacv.visionloop.test;

import io.github.deltacv.visionloop.VisionLoop;
import io.github.deltacv.visionloop.input.WebcamSource;
import io.github.deltacv.visionloop.sink.MjpegHttpStreamSink;
import org.junit.jupiter.api.Test;
import org.opencv.core.Size;

import java.util.Arrays;

public class VisionLoopStreamingTest {

    @Test
    public void mjpegSteamingTest() {
        WebcamSource source = Arrays.stream(WebcamSource.available()).findFirst().get();
        source.getWebcam().setResolution(new Size(320, 240));

        VisionLoop visionLoop = VisionLoop.with(source)
                // .then(AprilTagProcessor.easyCreateWithDefaults())
                .streamTo(new MjpegHttpStreamSink(8080, new Size(320, 240), 80))
                .build();

        visionLoop.runBlocking();
    }

}