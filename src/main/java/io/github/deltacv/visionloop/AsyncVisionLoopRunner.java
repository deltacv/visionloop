package io.github.deltacv.visionloop;

import org.firstinspires.ftc.robotcore.internal.collections.EvictingBlockingQueue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AsyncVisionLoopRunner {

    public final VisionLoop loop;

    private final ArrayDeque<Runnable> submitQueue = new ArrayDeque<>();
    private final ArrayList<Runnable> joinQueue = new ArrayList<>();

    private Thread visionThread;

    private final Object haltLock = new Object();

    public AsyncVisionLoopRunner(VisionLoop loop, String name) {
        this.loop = loop;

        visionThread = new Thread(() -> {
            try {
                while (!Thread.interrupted()) {
                    loop.run();

                    while (!submitQueue.isEmpty()) {
                        submitQueue.poll().run();
                    }

                    synchronized (joinQueue) {
                        for (Runnable runnable : joinQueue) {
                            runnable.run();
                        }
                    }
                }
            } catch (Exception e) {
                if(!(e instanceof InterruptedException)) {
                    throw e;
                }
            }

            loop.close();
        }, "VisionLoop-"+name);
    }

    public void start() {
        if(visionThread.isAlive()) {
            throw new IllegalStateException("Vision thread is already running");
        }

        visionThread.start();
    }

    public void submit(Runnable runnable) {
        synchronized (submitQueue) {
            submitQueue.add(runnable);
        }
    }

    public void joinWith(Runnable runnable) {
        synchronized (joinQueue) {
            joinQueue.add(runnable);
        }
    }

    public void join() {
        synchronized (haltLock) {
            try {
                haltLock.wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void stop() {
        visionThread.interrupt();
    }

}
