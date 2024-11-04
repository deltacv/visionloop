package io.github.deltacv.visionloop.receiver;

import android.graphics.Bitmap;
import io.github.deltacv.visionloop.processor.Processor;
import io.javalin.Javalin;
import io.javalin.http.Handler;
import org.firstinspires.ftc.robotcore.internal.collections.EvictingBlockingQueue;
import org.jetbrains.skia.impl.BufferUtil;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.openftc.easyopencv.MatRecycler;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;

/**
 * A receiver that streams MJPEG video over HTTP.
 * <p>
 *     This receiver will start a Javalin server on the specified port and stream the video frames to clients
 *     in MJPEG format. The server will respond to GET requests at / with the MJPEG stream.
 *     The server will stop when the receiver is closed.
 * <p>
 *     The MJPEG stream is generated by encoding the video frames as JPEG images and sending them to the client
 *     in a multipart/x-mixed-replace response. The server will send a new frame whenever it is available
 *     and the client will display the frames as they arrive.
 *
 * Inherits from {@link CanvasViewportReceiver}, which uses a RenderThread to render the frames into a
 * Skiko Canvas and then extract the raw pixel data from the Canvas to create a Mat object.
 */
public class MjpegHttpStreamerReceiver extends CanvasViewportReceiver {

    private static final String BOUNDARY = "frame";

    private static final byte[] boundaryBytes = ("--" + BOUNDARY + "\r\n").getBytes();
    private static final byte[] contentTypeBytes = "Content-Type: image/jpeg\r\n".getBytes();
    private static final byte[] contentLengthBytes = ("Content-Length: ").getBytes();
    private static final byte[] crlfBytes = "\r\n\r\n".getBytes();

    private static final int QUEUE_SIZE = 3;

    private final int port;
    private Javalin app;

    private volatile boolean getHandlerCalled = false;

    private final EvictingBlockingQueue<MatRecycler.RecyclableMat> frames = new EvictingBlockingQueue<>(new ArrayBlockingQueue<>(QUEUE_SIZE));
    private final MatRecycler matRecycler = new MatRecycler(QUEUE_SIZE + 2);

    /**
     * Creates a new MjpegHttpStreamerReceiver with the specified port and stream size.
     * @param port The port to start the Javalin server on. Specify 0 to use a random port.
     * @param streamSize The size of the video stream.
     */
    public MjpegHttpStreamerReceiver(int port, Size streamSize) {
        this(port, streamSize, null);
    }

    /**
     * Creates a new MjpegHttpStreamerReceiver with the specified port, stream size, and descriptor.
     * @param port The port to start the Javalin server on. Specify 0 to use a random port.
     * @param streamSize The size of the video stream.
     * @param descriptor The descriptor for the OpenCvViewRenderer. Specify null to disable the FPS meter.
     */
    public MjpegHttpStreamerReceiver(int port, Size streamSize, String descriptor) {
        super(streamSize, descriptor);
        this.port = port;

        // the frame queue will automatically recycle the Mat objects
        frames.setEvictAction(MatRecycler.RecyclableMat::returnMat);
    }

    /**
     * Returns the handler that is meant to be attached to a Javalin server. This method can only be called once.
     * Used by {@link MjpegHttpStreamerReceiver#init(Processor[])}} to create a handler for the server.
     * If this method is called by the user, the user is responsible for attaching the handler to a Javalin server,
     * and {@link MjpegHttpStreamerReceiver#init(Processor[])} will do nothing.
     *
     * If the method is called more than once, an {@link IllegalStateException} will be thrown.
     * That also means that if the method is called after {@link MjpegHttpStreamerReceiver#init(Processor[])} is called,
     * an {@link IllegalStateException} will be thrown.
     *
     * @return The handler for the Javalin server.
     */
    public Handler takeHandler() {
        if(getHandlerCalled) {
            throw new IllegalStateException("getHandler can only be called once");
        }

        getHandlerCalled = true;

        return ctx -> {
            // set the content type to multipart/x-mixed-replace
            ctx.contentType("multipart/x-mixed-replace; boundary=" + BOUNDARY);

            // get the output stream
            OutputStream outputStream = ctx.res().getOutputStream();

            // reusable instances
            MatOfByte buf = new MatOfByte();
            byte[] bufArray = null;

            while (!Thread.interrupted()) {
                // peek at the frame queue
                MatRecycler.RecyclableMat frame = frames.peek();

                if (frame != null) {
                    try {
                        // actual JPEG encoding magic
                        Imgcodecs.imencode(".jpg", frame, buf, new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 80));

                        var contentLength = (int) buf.total();

                        if(bufArray == null || bufArray.length < contentLength) {
                            // allocate a new buffer if the existing one is too small
                            bufArray = new byte[contentLength];
                        }

                        buf.get(0, 0, bufArray); // copy the data to the buffer

                        // write the JPEG data to the output stream
                        outputStream.write(boundaryBytes);
                        outputStream.write(contentTypeBytes);
                        outputStream.write(contentLengthBytes);
                        outputStream.write(String.valueOf(contentLength).getBytes());
                        outputStream.write(crlfBytes);
                        outputStream.write(bufArray);
                        outputStream.write(crlfBytes);

                        // there it goes !
                        outputStream.flush();

                        // no need to recycle the Mat, as the frame queue will do it
                    } catch (Exception e) {
                        break;
                    }
                }
            }
        };
    }

    /**
     * Initializes the receiver with the specified processors.
     * If {@link MjpegHttpStreamerReceiver#takeHandler()} has been called, this method will do nothing.
     * Otherwise, it will start a Javalin server on the specified port and attach the handler to it.
     * @param processors An array of processors that will provide processed frames.
     */
    @Override
    public void init(Processor[] processors) {
        // the CanvasViewportReceiver will take care of calling drawFrame on each processor
        super.init(processors);

        try {
            Handler handler = takeHandler();

            // start javalin async
            Executors.newSingleThreadExecutor().submit(() -> {
                app = Javalin.create();
                app.get("/", handler);
                app.start(port);
            });
        } catch(Exception ignored) { }
    }

    /**
     * Returns the port that the Javalin server is running on.
     * If the port was set to 0 in the constructor, this method will return the actual port that the server is running on.
     * If the server is not running, this method return value will be undefined.
     * @return The port that the Javalin server is running on.
     */
    public int getPort() {
        return app.port();
    }

    @Override
    void afterRender(Bitmap bitmap) {
        try {
            MatRecycler.RecyclableMat frame = matRecycler.takeMatOrInterrupt();

            if(frame.size().width != bitmap.getWidth() || frame.size().height != bitmap.getHeight() || frame.type() != CvType.CV_8UC3) {
                frame.release();
                // create a new Mat object if the size or type of the existing one doesn't match
                frame.create(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC3);
            }

            // extract the raw pixel data from the bitmap
            int size = bitmap.getWidth() * bitmap.getHeight() * 3;
            long addr = bitmap.theBitmap.peekPixels().getAddr();
            ByteBuffer buffer = BufferUtil.INSTANCE.getByteBufferFromPointer(addr, size);

            // convert the raw pixel data to a Mat object
            Mat tmp = new Mat(bitmap.getHeight(), bitmap.getWidth(), CvType.CV_8UC2, buffer);
            Imgproc.cvtColor(tmp, frame, Imgproc.COLOR_BGR5652BGR); // directly convert to BGR for MJPEG
            tmp.release();

            frames.add(frame);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean pollViewportTapped() {
        return false;
    }

    @Override
    public void close() {
        super.close();

        // flush flush flush
        if(app != null) {
            app.stop();
        }
        frames.clear();
        matRecycler.releaseAll();
    }
}