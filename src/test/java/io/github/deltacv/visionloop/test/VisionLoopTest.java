package io.github.deltacv.visionloop.test;

import io.github.deltacv.visionloop.VisionLoop;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.junit.jupiter.api.Test;
import org.opencv.imgproc.Imgproc;

public class VisionLoopTest {
    @Test
    public void test() {
        VisionLoop loop = VisionLoop.withImage("/apriltag.png")
                .then(AprilTagProcessor.easyCreateWithDefaults()) // Use an AprilTag processor to detect tags
                .then((image) -> {
                    // Inline processing, image is a Timestamped<Mat> object.
                    // do some other stuff...
                    return image.getValue(); // Return the image to pass it to the next processor
                })
                .onViewportTapped(() -> System.out.println("Tapped!")) // Print a message when the viewport is tapped
                .withLiveView() // Enable the live view to see the processed image in a window
                .build(); // Build the vision loop

        loop.runBlocking(); // Run the vision loop on this thread

        // or, alternatively, to run the vision loop asynchronously:
        // AsyncVisionLoop asyncLoop = loop.toAsync();
        // asyncLoop.run();
    }
}