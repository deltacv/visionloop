package io.github.deltacv.visionloop;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

public class AsyncVisionLoopRunner {

    private final VisionLoop loop;

    private final ArrayDeque<Runnable> submitQueue = new ArrayDeque<>();

    private final ArrayDeque<Throwable> exceptions = new ArrayDeque<>();

    private Thread visionThread;

    private final Object haltLock = new Object();

    public AsyncVisionLoopRunner(VisionLoop loop, String name, BooleanSupplier condition) {
        this.loop = loop;

        visionThread = new Thread(() -> {
            try (loop) {
                while (!Thread.interrupted() && condition.getAsBoolean()) {
                    loop.run();

                    while (!submitQueue.isEmpty()) {
                        submitQueue.poll().run();
                    }
                }
            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                    throw e;
                }
            } finally {
                synchronized (haltLock) {
                    haltLock.notifyAll();
                }
            }
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

    public void joinQuietly() {
        synchronized (haltLock) {
            try {
                haltLock.wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

    public void join() {
        synchronized (haltLock) {
            try {
                haltLock.wait();
            } catch (InterruptedException ignored) {
            }
        }

        if(hasExceptions()) {
            throw new RuntimeException("Vision loop threw an exception", pollException());
        }
    }

    public void interrupt() {
        visionThread.interrupt();
    }

    public Throwable pollException() {
        synchronized (exceptions) {
            return exceptions.poll();
        }
    }

    public boolean isAlive() {
        return visionThread.isAlive();
    }

    public boolean hasExceptions() {
        synchronized (exceptions) {
            return !exceptions.isEmpty();
        }
    }

    public VisionLoop getLoop() {
        return loop;
    }

}
