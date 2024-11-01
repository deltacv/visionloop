# VisionLoop

VisionLoop is a simple, flexible, and intuitive framework for processing vision data in real-time applications for the Java Virtual Machine. Designed for robotics and computer vision tasks, this API simplifies the integration of image sources, processing pipelines, and user interactions.

## Features

- **Fluent Interface**: Build complex configurations easily with method chaining.
- **Flexible Configuration**: Configure image sources from various inputs and processing parameters dynamically.
- **Event Handling**: Easily respond to user interactions like viewport taps.
- **Asynchronous Execution**: Run vision loops without blocking the main thread.
- **Testable**: Designed with testability in mind, making unit testing straightforward.

## Getting Started

### Prerequisites

- Java 11 or higher
- A build tool (e.g. Gradle, Maven)

### Installation

Add the following dependency to your project:

#### Gradle 

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.deltacv:visionloop:1.1.0'
    implementation 'com.github.deltacv.visionloop:streamer:1.1.0' // optional for streaming support
}
```

#### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.deltacv</groupId>
    <artifactId>visionloop</artifactId>
    <version>1.0.0</version>
</dependency>

<dependency> <!-- optional for streaming support -->
    <groupId>com.github.deltacv.visionloop</groupId>
    <artifactId>streamer</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Usage


### VisionLoop implements the concepts of OpenFTC's OpenCvPipeline and VisionProcessor, allowing you to run them using the API. To learn how to use these interfaces, you may look into [EOCV-Sim's documentation here](https://deltacv.gitbook.io/eocv-sim/introduction/pipelines).

Here’s a quick example of how to set up a vision loop with an image source and an AprilTag processor:

```java
import io.github.deltacv.visionloop.AsyncVisionLoop;
import io.github.deltacv.visionloop.VisionLoop;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;

public class VisionLoopShowcase {
    public static void main(String[] args) {
        VisionLoop loop = VisionLoop.withWebcamIndex(0)
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
```

VisionLoop provides a few other methods to use other input sources...

```java
// Image can be both a path to an image file or resource from the classpath
VisionLoop loop = VisionLoop.withImage("path/to/image.jpg")
    //...
    .build();
```

### Result

![img.png](assets/apriltag_result.png)


## Using the Mjpeg Streamer

Adding the additional "streamer" module to your project allows you to stream the processed image to a web server.<br>
Make sure to add the dependency to your project as shown in the installation section.<br>
Here's an example of how to set up a vision loop with a webcam source and an AprilTag processor, and stream the processed image to a web server:

```java
package io.github.deltacv.visionloop.rpi;

import io.github.deltacv.visionloop.VisionLoop;
import io.github.deltacv.visionloop.receiver.MjpegHttpStreamerReceiver;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.opencv.core.Size;

public class VisionLoopMjpegStreamShowcase {
    public static void main(String[] args) {
        var visionLoop = VisionLoop.withWebcamIndex(0)
                .then(AprilTagProcessor.easyCreateWithDefaults())
                // The MjpegHttpStreamerReceiver takes in a port and a size for the stream
                .streamTo(new MjpegHttpStreamerReceiver(8080, new Size(640, 480)))
                .build();

        visionLoop.runBlocking();
    }
}
```

You can also add in an annotation name to the MjpegHttpStreamerReceiver constructor to display pipeline statistics

```java
new MjpegHttpStreamerReceiver(8080, new Size(640, 480), "AprilTag Processor") // Display pipeline statistics
```

### Result

Open the web server in any browser at `http://localhost:8080` to view the stream.

![img.png](assets/mjpeg_result.png)