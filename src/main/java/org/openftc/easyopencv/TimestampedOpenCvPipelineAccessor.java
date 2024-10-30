package org.openftc.easyopencv;

public class TimestampedOpenCvPipelineAccessor {
    private TimestampedOpenCvPipelineAccessor() { }

    public static void setTimestamp(TimestampedOpenCvPipeline pipeline, long timestamp) {
        pipeline.setTimestamp(timestamp);
    }
}
