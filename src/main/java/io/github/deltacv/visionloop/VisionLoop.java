package io.github.deltacv.visionloop;

import io.github.deltacv.common.pipeline.util.PipelineStatisticsCalculator;
import io.github.deltacv.vision.external.util.Timestamped;
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

    private boolean running = false;

    private final PipelineStatisticsCalculator statisticsCalculator = new PipelineStatisticsCalculator();

    private VisionLoop(InputSource source, Processor[] processors, Receiver[] receivers) {
        this.source = source;
        this.processors = processors;
        this.receivers = receivers;
    }

    @Override
    public void run() {
        if(!running) {
            source.open();
            statisticsCalculator.init();
        }

        statisticsCalculator.newInputFrameStart();

        Timestamped<Mat> frame = source.update();

        statisticsCalculator.newPipelineFrameStart();

        statisticsCalculator.beforeProcessFrame();

        for (Processor processor : processors) {
            if(!running) {
                processor.init(frame);
            }

            processor.processFrame(frame);
            frame = new Timestamped<>(frame.getValue(), frame.getTimestamp());
        }

        statisticsCalculator.afterProcessFrame();

        for (Receiver receiver : receivers) {
            if(!running) {
                receiver.init(processors);
            }

            receiver.take(frame.getValue());
            receiver.notifyStatistics(statisticsCalculator.getAvgFps(), statisticsCalculator.getAvgPipelineTime(), statisticsCalculator.getAvgOverheadTime());
        }

        statisticsCalculator.endFrame();

        running = true;
    }

    @Override
    public void close() {
        source.close();
        running = false;
    }

    public void runBlocking(BooleanSupplier until) {
        try(this) {
            while(until.getAsBoolean()) {
                run();
            }
        }
    }

    public void runBlocking() {
        runBlocking(() -> true);
    }

    public static Builder withInput(InputSource source) {
        return new Builder(source);
    }

    public static Builder withWebcamIndex(int index) {
        return new Builder(WebcamSource.withIndex(index));
    }

    public static Builder withWebcamName(String name) {
        return new Builder(WebcamSource.withName(name));
    }

    public static class Builder {
        private final InputSource source;
        private final ArrayList<Processor> processors = new ArrayList<>();
        private final ArrayList<Receiver> receivers = new ArrayList<>();

        public Builder(InputSource source) {
            this.source = source;
        }

        public Builder then(Processor... processors) {
            this.processors.addAll(Arrays.asList(processors));
            return this;
        }

        public Builder then(OpenCvPipeline pipeline){
            this.processors.add(new OpenCvPipelineProcessor(pipeline));
            return this;
        }

        public Builder then(VisionProcessor visionProcessor) {
            return then(OpenCvPipelineProcessor.fromVisionProcessor(visionProcessor));
        }

        public FinalBuilder streamTo(Receiver... receivers) {
            this.receivers.addAll(Arrays.asList(receivers));
            return new FinalBuilder(this);
        }

        public FinalBuilder show(String title, Size size) {
            return streamTo(new SwingViewportReceiver(title, size));
        }

        public FinalBuilder show(Size size) {
            return streamTo(new SwingViewportReceiver(size));
        }

        public FinalBuilder show() {
            return show(new Size(640, 480));
        }

        public VisionLoop build() {
            return new VisionLoop(source, processors.toArray(new Processor[0]), receivers.toArray(new Receiver[0]));
        }
    }

    public static class FinalBuilder {
        private final Builder builder;

        private FinalBuilder(Builder builder) {
            this.builder = builder;
        }

        public VisionLoop build() {
            return builder.build();
        }
    }

}
