package io.github.deltacv.visionloop;

import io.github.deltacv.common.pipeline.util.PipelineStatisticsCalculator;
import io.github.deltacv.vision.external.util.Timestamped;
import io.github.deltacv.visionloop.input.ImageSource;
import io.github.deltacv.visionloop.input.InputSource;
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

    private final Object loopLock = new Object();

    private final PipelineStatisticsCalculator statisticsCalculator = new PipelineStatisticsCalculator();

    private VisionLoop(InputSource source, Processor[] processors, Receiver[] receivers, Runnable[] frameHooks) {
        this.source = source;
        this.processors = processors;
        this.receivers = receivers;
        this.frameHooks = frameHooks;
    }

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

    public void runBlocking(BooleanSupplier until) {
        if(running) {
            throw new IllegalStateException("Cannot run a blocking loop while already running a loop");
        }

        try(this) {
            while(until.getAsBoolean()) {
                run();
            }
        }
    }

    public void runBlocking() {
        runBlocking(() -> true);
    }

    public AsyncVisionLoopRunner runAsyncWhile(BooleanSupplier condition) {
        return runAsyncWhile(Integer.toHexString(hashCode()), condition);
    }

    public AsyncVisionLoopRunner runAsyncWhile(String threadName, BooleanSupplier condition) {
        AsyncVisionLoopRunner runner = new AsyncVisionLoopRunner(this, threadName, condition);
        runner.start();
        return runner;
    }

    public AsyncVisionLoopRunner runAsync() {
        return runAsyncWhile(() -> true);
    }

    public AsyncVisionLoopRunner runAsync(String threadName) {
        return runAsyncWhile(threadName, () -> true);
    }

    // BUILDER METHODS

    public static Builder with(InputSource source) {
        return new Builder(source);
    }

    public static Builder withWebcamIndex(int index) {
        return new Builder(WebcamSource.withIndex(index));
    }

    public static Builder withWebcamName(String name) {
        return new Builder(WebcamSource.withName(name));
    }

    public static Builder withImage(String path, double scale) {
        return new Builder(new ImageSource(path, scale));
    }

    public static Builder withImage(String path) {
        return withImage(path, 1.0);
    }

    public static class Builder {
        private final InputSource source;
        private final ArrayList<Processor> processors = new ArrayList<>();
        private final ArrayList<Receiver> receivers = new ArrayList<>();
        private final ArrayList<Runnable> frameHooks = new ArrayList<>();

        public Builder(InputSource source) {
            this.source = source;
        }

        public Builder then(Processor... processors) {
            this.processors.addAll(Arrays.asList(processors));
            return this;
        }

        public Builder then(Processor.Simple processor) {
            return then((Processor) processor);
        }

        public Builder then(OpenCvPipeline pipeline) {
            this.processors.add(new OpenCvPipelineProcessor(pipeline));
            return this;
        }

        public Builder then(VisionProcessor visionProcessor) {
            return then(OpenCvPipelineProcessor.fromVisionProcessor(visionProcessor));
        }

        public Builder onEveryFrame(Runnable runnable) {
            frameHooks.add(runnable);
            return this;
        }

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

        public FinalBuilder streamTo(Receiver... receivers) {
            this.receivers.addAll(Arrays.asList(receivers));
            return new FinalBuilder(this);
        }

        public FinalBuilder withLiveView(String title, Size size) {
            return streamTo(new SwingViewportReceiver(title, size));
        }

        public FinalBuilder withLiveView(Size size) {
            return streamTo(new SwingViewportReceiver(size));
        }

        public FinalBuilder withLiveView() {
            return withLiveView(new Size(640, 480));
        }

        public VisionLoop build() {
            return new VisionLoop(source, processors.toArray(new Processor[0]), receivers.toArray(new Receiver[0]), frameHooks.toArray(new Runnable[0]));
        }
    }

    public static class FinalBuilder {
        private final Builder builder;

        private FinalBuilder(Builder builder) {
            this.builder = builder;
        }

        public VisionLoop streamTo(Receiver... receivers) {
            return builder.streamTo(receivers).build();
        }

        public VisionLoop build() {
            return builder.build();
        }
    }

}