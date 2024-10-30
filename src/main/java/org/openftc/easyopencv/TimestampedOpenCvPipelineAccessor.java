package org.openftc.easyopencv;

/**
 * The {@code TimestampedOpenCvPipelineAccessor} class provides static methods
 * to access and modify the properties of a {@link TimestampedOpenCvPipeline}.
 * This class is designed to encapsulate access to timestamp-related functionality,
 * ensuring that the internal state of the pipeline can be modified safely.
 */
public class TimestampedOpenCvPipelineAccessor {
    private TimestampedOpenCvPipelineAccessor() { }

    /**
     * Sets the timestamp for the specified {@link TimestampedOpenCvPipeline}.
     *
     * @param pipeline the {@link TimestampedOpenCvPipeline} instance
     *                 for which the timestamp is to be set.
     * @param timestamp the timestamp to be set, typically representing
     *                  the time when the frame was captured.
     */
    public static void setTimestamp(TimestampedOpenCvPipeline pipeline, long timestamp) {
        pipeline.setTimestamp(timestamp);
    }
}