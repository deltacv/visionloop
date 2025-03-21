package io.github.deltacv.visionloop.sink;

import android.graphics.Bitmap;
import io.github.deltacv.visionloop.processor.Processor;
import io.github.deltacv.visionloop.tj.TJLoader;
import io.javalin.Javalin;
import io.javalin.http.Handler;
import org.eclipse.jetty.io.EofException;
import org.firstinspires.ftc.robotcore.internal.collections.EvictingBlockingQueue;
import org.jetbrains.skia.impl.BufferUtil;
import org.libjpegturbo.turbojpeg.TJ;
import org.libjpegturbo.turbojpeg.TJCompressor;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.openftc.easyopencv.MatRecycler;

import java.io.EOFException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * Inherits from {@link CanvasViewportSink}, which uses a RenderThread to render the frames into a
 * Skiko Canvas and then extract the raw pixel data from the Canvas to create a Mat object.
 */
public class MjpegHttpStreamSink extends CanvasViewportSink {

    private static final String BOUNDARY = "frame";

    private static final byte[] boundaryBytes = ("--" + BOUNDARY + "\r\n").getBytes();
    private static final byte[] contentTypeBytes = "Content-Type: image/jpeg\r\n".getBytes();
    private static final byte[] contentLengthBytes = ("Content-Length: ").getBytes();
    private static final byte[] crlfBytes = "\r\n\r\n".getBytes();

    private static final int QUEUE_SIZE = 5;
    private static final int REUSABLE_BUFFER_QUEUE_SIZE = 10;
    private static final int COMPRESSION_THREAD_POOL_SIZE = 4; // Number of threads for JPEG compression

    private final int port;

    private final Object qualityLock = new Object();
    private int quality;

    private Javalin app;

    private volatile boolean getHandlerCalled = false;

    private final EvictingBlockingQueue<MatRecycler.RecyclableMat> frames = new EvictingBlockingQueue<>(new ArrayBlockingQueue<>(QUEUE_SIZE));
    private final MatRecycler matRecycler = new MatRecycler(QUEUE_SIZE + 4);

    private final Map<Integer, Queue<byte[]>> reusableBuffers = new ConcurrentHashMap<>();

    // Thread pool for JPEG compression
    private final ExecutorService compressionThreadPool;

    // Queue for compressed frames
    private final BlockingQueue<CompressedFrame> compressedFrames = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    static {
        TJLoader.load();
    }

    /**
     * Represents a compressed JPEG frame ready to be sent to clients
     */
    private static class CompressedFrame {
        final byte[] data;
        final int size;

        CompressedFrame(byte[] data, int size) {
            this.data = data;
            this.size = size;
        }
    }

    /**
     * Creates a new MjpegHttpStreamerReceiver with the specified port and stream size.
     * @param port The port to start the Javalin server on. Specify 0 to use a random port.
     * @param streamSize The size of the video stream.
     * @param quality The quality of the JPEG stream, from 0 to 100.
     */
    public MjpegHttpStreamSink(int port, Size streamSize, int quality) {
        this(port, streamSize, quality, null);
    }

    /**
     * Creates a new MjpegHttpStreamerReceiver with the specified port, stream size, and descriptor.
     * @param port The port to start the Javalin server on. Specify 0 to use a random port.
     * @param streamSize The size of the video stream.
     * @param descriptor The descriptor for the OpenCvViewRenderer. Specify null to disable the FPS meter.
     */
    public MjpegHttpStreamSink(int port, Size streamSize, int quality, String descriptor) {
        super(streamSize, descriptor);
        this.quality = quality;
        this.port = port;

        // Create fixed thread pool for compression
        this.compressionThreadPool = Executors.newFixedThreadPool(COMPRESSION_THREAD_POOL_SIZE,
                r -> {
                    Thread t = new Thread(r, "JPEG-Compression-Thread");
                    t.setDaemon(true);
                    return t;
                });

        // the frame queue will automatically recycle the Mat objects
        frames.setEvictAction(mat -> {
            // Submit compression task when a new frame is available
            submitCompressionTask(mat);
            // Return the mat to the recycler
            mat.returnMat();
        });
    }

    /**
     * Submits a compression task to the thread pool
     * @param frame The frame to compress
     */
    private void submitCompressionTask(MatRecycler.RecyclableMat frame) {
        if (!isRunning.get()) return;

        MatRecycler.RecyclableMat frameCopy = matRecycler.takeMatOrNull();

        if(frameCopy == null) {
            return;
        }

        frame.copyTo(frameCopy);

        compressionThreadPool.submit(() -> {
            try {
                // Create a copy of the frame data to work with
                byte[] frameData = getOrCreateReusableBuffer((int) frame.total() * frame.channels());
                frameCopy.get(0, 0, frameData);
                frameCopy.returnMat();

                TJCompressor compressor = new TJCompressor();
                try {
                    synchronized (qualityLock){
                        compressor.setJPEGQuality(quality);
                    }

                    compressor.setSubsamp(TJ.SAMP_440);
                    compressor.setSourceImage(frameData, frame.width(), 0, frame.height(), TJ.PF_BGR);

                    byte[] buffer = getOrCreateReusableBuffer(2_000_000); // Pre-allocate buffer
                    compressor.compress(buffer, TJ.FLAG_FASTDCT);

                    returnReusableBuffer(frameData);

                    int compressedSize = (int) compressor.getCompressedSize();

                    // Add compressed frame to the output queue if we're still running
                    if (isRunning.get()) {
                        CompressedFrame compressedFrame = new CompressedFrame(buffer, compressedSize);
                        compressedFrames.offer(compressedFrame, 100, TimeUnit.MILLISECONDS);
                    }
                } finally {
                    compressor.close();
                }
            } catch (Exception e) {
                if (isRunning.get()) {
                    System.err.println("Error compressing frame: " + e.getMessage());
                }
            }
        });
    }


    public void returnReusableBuffer(byte[] buffer) {
        synchronized (reusableBuffers) {
            Queue<byte[]> queue = reusableBuffers.get(buffer.length);
            if (queue != null) {
                queue.offer(buffer);
            } else {
                System.err.println("Buffer pool for size " + buffer.length + " is null");
            }
        }
    }

    public byte[] getOrCreateReusableBuffer(int size) {
        synchronized (reusableBuffers) {
            reusableBuffers.computeIfAbsent(size, k -> {
                Queue<byte[]> queue = new ArrayBlockingQueue<>(REUSABLE_BUFFER_QUEUE_SIZE);
                for (int i = 0; i < REUSABLE_BUFFER_QUEUE_SIZE; i++) {
                    queue.offer(new byte[size]);
                }
                return queue;
            });

            Queue<byte[]> queue = reusableBuffers.get(size);
            byte[] buffer = queue.poll();
            return (buffer != null) ? buffer : new byte[size];
        }
    }

    /**
     * Returns the handler that is meant to be attached to a Javalin server. This method can only be called once.
     * Used by {@link MjpegHttpStreamSink#init(Processor[])}} to create a handler for the server.
     * If this method is called by the user, the user is responsible for attaching the handler to a Javalin server,
     * and {@link MjpegHttpStreamSink#init(Processor[])} will do nothing.
     *
     * If the method is called more than once, an {@link IllegalStateException} will be thrown.
     * That also means that if the method is called after {@link MjpegHttpStreamSink#init(Processor[])} is called,
     * an {@link IllegalStateException} will be thrown.
     *
     * @return The handler for the Javalin server.
     */
    public Handler takeHandler() {
        if(getHandlerCalled) {
            throw new IllegalStateException("takeHandler can only be called once. Has init() already been called?");
        }

        getHandlerCalled = true;

        return ctx -> {
            // set the content type to multipart/x-mixed-replace
            ctx.contentType("multipart/x-mixed-replace; boundary=" + BOUNDARY);

            // get the output stream
            OutputStream outputStream = ctx.res().getOutputStream();

            byte[] contentLengthNumberBytes = new byte[16];
            int lastContentLengthNumber = 0;

            try {
                while (!Thread.interrupted() && isRunning.get()) {
                    // Get compressed frame from the queue
                    CompressedFrame frame = compressedFrames.poll(500, TimeUnit.MILLISECONDS);

                    if (frame != null) {
                        try {
                            int contentLength = frame.size;

                            // Avoid string conversions: preallocate and reuse byte buffer
                            if (lastContentLengthNumber != contentLength) {
                                contentLengthNumberBytes = String.valueOf(contentLength).getBytes();
                                lastContentLengthNumber = contentLength;
                            }

                            outputStream.write(crlfBytes);

                            // write the JPEG data to the output stream
                            outputStream.write(boundaryBytes);
                            outputStream.write(contentTypeBytes);
                            outputStream.write(contentLengthBytes);
                            outputStream.write(contentLengthNumberBytes);
                            outputStream.write(crlfBytes);
                            outputStream.write(frame.data, 0, contentLength);

                            outputStream.flush();
                        } catch (EOFException e) {
                            // ignore
                            break;
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            returnReusableBuffer(frame.data);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    /**
     * Initializes the receiver with the specified processors.
     * If {@link MjpegHttpStreamSink#takeHandler()} has been called, this method will do nothing.
     * Otherwise, it will start a Javalin server on the specified port and attach the handler to it.
     * @param processors An array of processors that will provide processed frames.
     */
    @Override
    public void init(Processor[] processors) {
        // the CanvasViewportReceiver will take care of calling drawFrame on each processor
        super.init(processors);

        if(!getHandlerCalled) {
            Handler handler = takeHandler();

            // start javalin async
            Executors.newSingleThreadExecutor().submit(() -> {
                app = Javalin.create();
                app.get("/", handler);
                app.start("127.0.0.1", port);
            });
        }
    }

    /**
     * Sets the JPEG compression quality of the stream.
     * @param quality The quality of the JPEG stream, from 0 to 100.
     */
    public void setQuality(int quality) {
        synchronized (qualityLock) {
            if (quality < 0 || quality > 100) {
                throw new IllegalArgumentException("Quality must be between 0 and 100");
            }
            this.quality = quality;
        }
    }


    /**
     * Returns the port that the Javalin server is running on.
     * If the port was set to 0 in the constructor, this method will return the actual port that the server is running on.
     * If the server is not running, this method's return value will be undefined.
     * @return The port that the Javalin server is running on.
     */
    public int getPort() {
        return app.port();
    }

    /**
     * Gets the JPEG compression quality of the stream.
     * @return The quality of the JPEG stream, from 0 to 100.
     */
    public int getQuality() {
        synchronized (qualityLock) {
            return quality;
        }
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
        isRunning.set(false);
        super.close();

        // Stop the compression thread pool
        compressionThreadPool.shutdown();
        try {
            if (!compressionThreadPool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                compressionThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            compressionThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // flush flush flush
        if(app != null) {
            app.stop();
        }
        frames.clear();
        compressedFrames.clear();
        matRecycler.releaseAll();
    }
}