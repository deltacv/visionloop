package io.github.deltacv.visionloop;

import io.github.deltacv.common.pipeline.util.PipelineStatisticsCalculator;
import io.github.deltacv.vision.external.util.Timestamped;
import io.github.deltacv.visionloop.input.ImageSource;
import io.github.deltacv.visionloop.input.InputSource;
import io.github.deltacv.visionloop.input.VideoCaptureSource;
import io.github.deltacv.visionloop.input.WebcamSource;
import io.github.deltacv.visionloop.processor.OpenCvPipelineProcessor;
import io.github.deltacv.visionloop.processor.Processor;
import io.github.deltacv.visionloop.receiver.Receiver;
import io.github.deltacv.visionloop.receiver.SwingViewportReceiver;
import nu.pattern.OpenCV;
import org.firstinspires.ftc.vision.VisionProcessor;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.openftc.easyopencv.OpenCvPipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/**
 * A VisionLoop represents a continuous loop for processing video frames
 * from an {@link InputSource}, passing them through a chain of {@link Processor}
 * instances, and sending them to one or more {@link Receiver} instances.
 * <p>
 * This loop also supports frame hooks and viewport tapping detection.
 * Frames are processed, and statistics are updated and sent to receivers.
 */
@SuppressWarnings("unused")
public class VisionLoop implements Runnable, AutoCloseable {

    static {
        OpenCV.loadLocally();
    }

    private final InputSource source;
    private final Processor[] processors;
    private final Receiver[] receivers;
    private final Runnable[] frameHooks;

    private boolean hasBeenRunning = false;
    private boolean running = false;
    private boolean closed = false;

    private boolean isAsync = false;

    private final Object loopLock = new Object();

    private final PipelineStatisticsCalculator statisticsCalculator = new PipelineStatisticsCalculator();

    /**
     * Creates a VisionLoop with the specified input source, processors, receivers, and frame hooks.
     * Use the {@link Builder} class to create a VisionLoop instance.
     *
     * @param source the input source for video frames
     * @param processors an array of processors that will process each frame
     * @param receivers an array of receivers to display or use processed frames
     * @param frameHooks an array of hooks to run after each frame is processed
     */
    private VisionLoop(InputSource source, Processor[] processors, Receiver[] receivers, Runnable[] frameHooks) {
        this.source = source;
        this.processors = processors;
        this.receivers = receivers;
        this.frameHooks = frameHooks;
    }

    /**
     * Runs a single iteration of the vision loop, processing a frame from the input source.
     * Frames are sent through processors, and results are passed to each receiver.
     * Hooks are executed at the end of each frame.
     */
    @Override
    public void run() {
        synchronized (loopLock) {
            if (closed) {
                throw new IllegalStateException("Loop has been closed. Did you call close() instead of interrupting?");
            }

            if (!hasBeenRunning) {
                running = true;
                source.open();
                statisticsCalculator.init();
            }

            statisticsCalculator.newInputFrameStart();

            Timestamped<Mat> frame = source.update();

            statisticsCalculator.newPipelineFrameStart();

            statisticsCalculator.beforeProcessFrame();

            for (Processor processor : processors) {
                if (!hasBeenRunning) {
                    processor.init(frame);
                }

                processor.processFrame(frame);
                frame = new Timestamped<>(frame.getValue(), frame.getTimestamp());
            }

            statisticsCalculator.afterProcessFrame();

            boolean viewportTapped = false;

            for (Receiver receiver : receivers) {
                if (!hasBeenRunning) {
                    receiver.init(processors);
                }

                receiver.take(frame.getValue());
                receiver.notifyStatistics(statisticsCalculator.getAvgFps(), statisticsCalculator.getAvgPipelineTime(), statisticsCalculator.getAvgOverheadTime());

                viewportTapped |= receiver.pollViewportTapped();
            }

            if(viewportTapped) {
                for(Processor processor : processors) {
                    processor.onViewportTapped();
                }
            }

            statisticsCalculator.endFrame();

            for(Runnable hook : frameHooks) {
                hook.run();
            }

            hasBeenRunning = true;
        }
    }

    /**
     * Closes the VisionLoop, releasing any resources held by receivers and input sources.
     * After closing, the loop cannot be restarted again.
     * This method is inherited from the AutoCloseable interface.
     * You can also use try-with-resources to automatically close the loop.
     * @throws IllegalStateException if the loop is not running
     */
    @Override
    public void close() {
        if(!running) {
            throw new IllegalStateException("Loop is not running");
        }

        synchronized (loopLock) {
            for (Receiver receiver : receivers) {
                receiver.close();
            }
            source.close();

            running = false;
            closed = true;
        }
    }

    /**
     * Runs the loop continuously on the current thread until the specified condition becomes false.
     * @param condition a BooleanSupplier that determines when the loop should stop
     */
    public void runBlockingWhile(BooleanSupplier condition) {
        if(isAsync) {
            throw new IllegalStateException("Cannot run a blocking loop with a VisionLoop that has been converted to an AsyncVisionLoop");
        }
        if(hasBeenRunning) {
            throw new IllegalStateException("Cannot run a blocking loop while already running a loop");
        }

        try(this) {
            while(condition.getAsBoolean()) {
                run();
            }
        }
    }

    /**
     * Runs the loop continuously until interrupted.
     */
    public void runBlocking() {
        runBlockingWhile(() -> true);
    }


    /**
     * Converts this VisionLoop to an AsyncVisionLoop with the specified thread name.
     * This allows the VisionLoop to run asynchronously on a separate thread.
     * Call {@link AsyncVisionLoop#run()} to start the loop.
     * @param threadName the name of the thread
     * @return an AsyncVisionLoop instance for running the VisionLoop asynchronously
     */
    public AsyncVisionLoop toAsync(String threadName) {
        if(hasBeenRunning) {
            throw new IllegalStateException("Cannot convert a VisionLoop to an AsyncVisionLoop after it has been run");
        }

        isAsync = true;
        return new AsyncVisionLoop(this, threadName);
    }

    /**
     * Converts this VisionLoop to an AsyncVisionLoop with a unique thread name.
     * This allows the VisionLoop to run asynchronously on a separate thread.
     * Call {@link AsyncVisionLoop#run()} to start the loop.
     * @return an AsyncVisionLoop instance for running the VisionLoop asynchronously
     */
    public AsyncVisionLoop toAsync() {
        return toAsync(Integer.toHexString(hashCode()));
    }

    // BUILDER METHODS

    /**
     * Begins the building process for a new VisionLoop with the specified input source.
     * The path may refer to a video file or a video stream that can be opened by OpenCV.
     *
     * @param source the input source to use for frames
     * @return a Builder to configure and build the VisionLoop
     */
    public static Builder with(String path) {
        return new Builder(new VideoCaptureSource(path));
    }

    /**
     * Begins the building process for a new VisionLoop with the specified input source.
     *
     * @param source the input source to use for frames
     * @return a Builder to configure and build the VisionLoop
     */
    public static Builder with(InputSource source) {
        return new Builder(source);
    }

    /**
     * Starts the VisionLoop using a webcam source specified by its index.
     *
     * @param index the index of the webcam to use as the input source
     * @return a Builder instance for further configuration
     */
    public static Builder withWebcamIndex(int index) {
        return new Builder(WebcamSource.withIndex(index));
    }

    /**
     * Starts the VisionLoop using a webcam source specified by its name.
     *
     * @param name the name of the webcam to use as the input source
     * @return a Builder instance for further configuration
     */
    public static Builder withWebcamName(String name) {
        return new Builder(WebcamSource.withName(name));
    }

    /**
     * Starts the VisionLoop using an image file as the source, with a specified
     * scaling factor.
     *
     * @param path the file path to the image source
     * @param scale the scaling factor for the image
     * @return a Builder instance for further configuration
     */
    public static Builder withImage(String path, double scale) {
        return new Builder(new ImageSource(path, scale));
    }

    /**
     * Starts the VisionLoop using an image file as the source with a default
     * scaling factor of 1.0.
     *
     * @param path the file path to the image source
     * @return a Builder instance for further configuration
     */
    public static Builder withImage(String path) {
        return withImage(path, 1.0);
    }

    /**
     * A Builder class to configure and construct instances of {@link VisionLoop}.
     * This class allows adding various {@link Processor} instances, {@link Receiver}
     * instances, and frame hooks to the VisionLoop, enabling custom configurations
     * for the vision processing pipeline. This class supports method chaining for
     * convenient configuration setup.
     */
    public static class Builder {
        private final InputSource source;
        private final ArrayList<Processor> processors = new ArrayList<>();
        private final ArrayList<Receiver> receivers = new ArrayList<>();
        private final ArrayList<Runnable> frameHooks = new ArrayList<>();

        /**
         * Constructs a new Builder with the specified input source.
         *
         * @param source the {@link InputSource} that provides frames for the
         *               vision processing pipeline. This source will be used
         *               to supply images to the {@link VisionLoop} instance
         *               built by this Builder.
         */
        public Builder(InputSource source) {
            this.source = source;
        }

        /**
         * Adds one or more {@link Processor} instances to the processing pipeline.
         * This allows chaining of multiple processing steps in the vision loop.
         * The processors will be executed in the order they are added.
         *
         * @param processors The processors to add to the pipeline.
         * @return The Builder instance, allowing for method chaining.
         */
        public Builder then(Processor... processors) {
            this.processors.addAll(Arrays.asList(processors));
            return this;
        }

        /**
         * Adds a {@link Processor.Simple} to the processing pipeline.
         * Provides a shorthand for adding frame-only processors to the pipeline.
         *
         * @param processor A simplified processor to add to the pipeline.
         * @return The Builder instance, enabling method chaining.
         */
        public Builder then(Processor.Simple processor) {
            return then((Processor) processor);
        }

        /**
         * Adds an {@link OpenCvPipeline} as a processor in the pipeline.
         * This method wraps an OpenCvPipeline, allowing OpenCV processing within the VisionLoop.
         *
         * @param pipeline The OpenCvPipeline to add to the pipeline.
         * @return The Builder instance, allowing method chaining.
         */
        public Builder then(OpenCvPipeline pipeline) {
            this.processors.add(new OpenCvPipelineProcessor(pipeline));
            return this;
        }

        /**
         * Adds a {@link VisionProcessor} as a processor to the pipeline.
         * This enables integration with FTC's VisionProcessor interface.
         *
         * @param visionProcessor The VisionProcessor to add to the pipeline.
         * @return The Builder instance, enabling method chaining.
         */
        public Builder then(VisionProcessor visionProcessor) {
            return then(OpenCvPipelineProcessor.fromVisionProcessor(visionProcessor));
        }

        /**
         * Adds a {@link Runnable} to be executed on every frame processed by the VisionLoop.
         * This can be useful for logging, monitoring, or updating UI elements each frame.
         *
         * @param runnable The Runnable to execute on every frame.
         * @return The Builder instance, allowing for method chaining.
         */
        public Builder onEveryFrame(Runnable runnable) {
            frameHooks.add(runnable);
            return this;
        }

        /**
         * Adds a {@link Runnable} to be executed each time the viewport is tapped.
         * This is used to add custom behavior in response to viewport interaction.
         *
         * @param runnable The Runnable to execute on viewport tap.
         * @return The Builder instance, allowing method chaining.
         */
        public Builder onViewportTapped(Runnable runnable) {
            return then(new Processor.Simple() {
                @Override
                public Mat processFrame(Timestamped<Mat> input) {
                    return input.getValue();
                }

                @Override
                public void onViewportTapped() {
                    runnable.run();
                }
            });
        }

        /**
         * Specifies one or more {@link Receiver} instances to receive processed frames.
         * Each receiver will get frames from the pipeline, useful for displaying or logging.
         *
         * @param receivers The receivers to add to the VisionLoop.
         * @return A FinalBuilder instance for finalizing the configuration.
         */
        public FinalBuilder streamTo(Receiver... receivers) {
            this.receivers.addAll(Arrays.asList(receivers));
            return new FinalBuilder(this);
        }

        /**
         * Creates a {@link SwingViewportReceiver} as the receiver and opens a live view window.
         * This method sets up a window to display processed frames in real-time.
         *
         * @param title The title of the live view window.
         * @return A FinalBuilder instance for finalizing the configuration.
         */
        public FinalBuilder withLiveView(String title, boolean fpsDescriptorEnabled) {
            return streamTo(new SwingViewportReceiver(title, new Size(640, 480), fpsDescriptorEnabled));
        }

        /**
         * Creates a {@link SwingViewportReceiver} as the receiver and opens a live view window.
         * This method sets up a window to display processed frames in real-time.
         *
         * @param title The title of the live view window.
         * @param size The dimensions of the live view window.
         * @return A FinalBuilder instance for finalizing the configuration.
         */
        public FinalBuilder withLiveView(String title, Size size, boolean fpsDescriptorEnabled) {
            return streamTo(new SwingViewportReceiver(title, size, fpsDescriptorEnabled));
        }

        /**
         * Creates a {@link SwingViewportReceiver} with the specified size.
         * Opens a live view window with the specified dimensions for real-time display.
         *
         * @param size The dimensions of the live view window.
         * @return A FinalBuilder instance for finalizing the configuration.
         */
        public FinalBuilder withLiveView(Size size, boolean fpsDescriptorEnabled) {
            return streamTo(new SwingViewportReceiver(size, fpsDescriptorEnabled));
        }

        /**
         * Creates a {@link SwingViewportReceiver} with a default size (640x480).
         * Opens a default live view window for real-time display of processed frames.
         *
         * @return A FinalBuilder instance for finalizing the configuration.
         */
        public FinalBuilder withLiveView(boolean fpsDescriptorEnabled) {
            return withLiveView(new Size(640, 480), fpsDescriptorEnabled);
        }

        /**
         * Creates a {@link SwingViewportReceiver} with a default size (640x480).
         * @return A FinalBuilder instance for finalizing the configuration.
         */
        public FinalBuilder withLiveView() {
            return withLiveView(true);
        }

        /**
         * Builds and returns a new {@link VisionLoop} instance.
         *
         * @return a {@link VisionLoop} configured with the specified input
         *         source, processors, receivers, and frame hooks.
         *
         * <p>The VisionLoop will be initialized with:</p>
         * <ul>
         *   <li>The {@link InputSource} to provide frames for processing.</li>
         *   <li>An array of {@link Processor} instances that will perform
         *       various transformations or analyses on each frame.</li>
         *   <li>An array of {@link Receiver} instances to handle processed
         *       output data.</li>
         *   <li>Frame hooks as {@link Runnable} instances that will be
         *       executed in each loop iteration for additional operations.</li>
         * </ul>
         */
        public VisionLoop build() {
            return new VisionLoop(source, processors.toArray(new Processor[0]), receivers.toArray(new Receiver[0]), frameHooks.toArray(new Runnable[0]));
        }
    }

    /**
     * FinalBuilder is a class that facilitates the final configuration and
     * creation of a {@link VisionLoop} instance. It acts as a secondary builder
     * that ensures the user has completed all necessary configurations before
     * constructing the final object.
     *
     * <p>This class provides methods to finalize the configuration of receivers
     * and build the {@link VisionLoop} instance, promoting immutability and
     * clarity in the building process.</p>
     *
     * <p>Usage:</p>
     * <pre>
     * VisionLoop loop = new VisionLoop.Builder(inputSource)
     *     .addProcessor(processor1)
     *     .addProcessor(processor2)
     *     .finalizeBuild() // Transition to FinalBuilder
     *     .streamTo(receiver1, receiver2)
     *     .build();
     * </pre>
     *
     * <p>Note: The methods in this class are designed to ensure that once
     * the user transitions to this builder, they cannot modify previous
     * configurations, reinforcing the intent of finalizing the setup.</p>
     */
    public static class FinalBuilder {
        private final Builder builder;

        private FinalBuilder(Builder builder) {
            this.builder = builder;
        }

        /**
         * Configures the {@link VisionLoop} to stream output to the specified
         * receivers and returns the current instance of {@link FinalBuilder}.
         *
         * <p>This allows for method chaining and further configuration before
         * building the final {@link VisionLoop} instance.</p>
         *
         * @param receivers The receivers to which the VisionLoop will stream output.
         * @return The current instance of {@link FinalBuilder} for method chaining.
         */
        public FinalBuilder streamTo(Receiver... receivers) {
            builder.streamTo(receivers);
            return this;
        }

        /**
         * Builds and returns the configured {@link VisionLoop} instance.
         *
         * @return A newly constructed {@link VisionLoop} instance.
         */
        public VisionLoop build() {
            return builder.build();
        }
    }

}