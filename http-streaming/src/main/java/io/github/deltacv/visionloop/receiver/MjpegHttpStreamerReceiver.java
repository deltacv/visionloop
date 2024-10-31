package io.github.deltacv.visionloop.receiver;

import android.graphics.Bitmap;
import io.github.deltacv.visionloop.VisionLoop;
import io.github.deltacv.visionloop.io.BitmapRecycler;
import io.github.deltacv.visionloop.processor.Processor;
import io.javalin.Javalin;
import org.firstinspires.ftc.robotcore.internal.collections.EvictingBlockingQueue;
import org.firstinspires.ftc.vision.apriltag.AprilTagProcessor;
import org.jetbrains.skia.impl.BufferUtil;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.openftc.easyopencv.MatRecycler;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;

public class MjpegHttpStreamerReceiver extends CanvasViewportReceiver {

    private static final String BOUNDARY = "frame";
    private static final int QUEUE_SIZE = 3;

    private final int port;
    private final Javalin app;

    private final EvictingBlockingQueue<MatRecycler.RecyclableMat> frames = new EvictingBlockingQueue<>(new ArrayBlockingQueue<>(QUEUE_SIZE));
    private final MatRecycler matRecycler = new MatRecycler(QUEUE_SIZE + 2);

    private final EvictingBlockingQueue<Bitmap> bitmaps = new EvictingBlockingQueue<>(new ArrayBlockingQueue<>(QUEUE_SIZE));
    private final BitmapRecycler bitmapRecycler;

    BitmapProcessingThread bitmapProcessingThread = new BitmapProcessingThread();

    public MjpegHttpStreamerReceiver(int port, Size streamSize) {
        this(port, streamSize, null);
    }

    public MjpegHttpStreamerReceiver(int port, Size streamSize, String descriptor) {
        super(streamSize, descriptor);
        this.port = port;

        bitmapRecycler = new BitmapRecycler((int) streamSize.width, (int) streamSize.height, QUEUE_SIZE + 2);

        // the frame queue will automatically recycle the Mat objects
        frames.setEvictAction(MatRecycler.RecyclableMat::returnMat);
        bitmaps.setEvictAction(bitmapRecycler::returnBitmap);

        app = Javalin.create()
                .get("/", ctx -> {
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

                                var bytes = buf.rows() * buf.cols() * buf.channels();

                                if(bufArray == null || bufArray.length < bytes) {
                                    // allocate a new buffer if the existing one is too small
                                    bufArray = new byte[bytes];
                                }

                                buf.get(0, 0, bufArray); // copy the data to the buffer

                                // write the JPEG data to the output stream
                                outputStream.write(("--" + BOUNDARY + "\r\n").getBytes());
                                outputStream.write("Content-Type: image/jpeg\r\n\r\n".getBytes());
                                outputStream.write(bufArray);
                                outputStream.write("\r\n\r\n".getBytes());

                                // there it goes !
                                outputStream.flush();

                                // no need to recycle the Mat, as the frame queue will do it
                            } catch (Exception e) {
                                break;
                            }
                        }
                    }
                });
    }

    @Override
    public void init(Processor[] processors) {
        // the CanvasViewportReceiver will take care of calling drawFrame on each processor
        super.init(processors);

        // start javalin async
        Executors.newSingleThreadExecutor().submit(() -> app.start(port));
        bitmapProcessingThread.start();
    }

    @Override
    void afterRender(Bitmap bitmap) {
        var targetBitmap = bitmapRecycler.takeBitmap();
        bitmap.theBitmap.peekPixels().readPixels(targetBitmap.theBitmap.peekPixels());
        bitmaps.add(targetBitmap);
    }

    @Override
    public boolean pollViewportTapped() {
        return false;
    }

    @Override
    public void close() {
        super.close();

        // flush flush flush
        app.stop();
        frames.clear();
        matRecycler.releaseAll();

        bitmapProcessingThread.interrupt();
    }

    private class BitmapProcessingThread extends Thread {
        private BitmapProcessingThread() {
            super("BitmapProcessingThread-" + port);
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    var bitmap = bitmaps.poll();
                    if(bitmap == null) {
                        continue;
                    }

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

                    bitmapRecycler.returnBitmap(bitmap);
                } catch (InterruptedException e) {
                }
            }
        }
    }

    public static void main(String[] args) {
        VisionLoop visionLoop = VisionLoop.withWebcamIndex(0)
                .then(AprilTagProcessor.easyCreateWithDefaults())
                .streamTo(new MjpegHttpStreamerReceiver(8080, new Size(160, 120)))
                .build();

        visionLoop.runBlocking();
    }
}