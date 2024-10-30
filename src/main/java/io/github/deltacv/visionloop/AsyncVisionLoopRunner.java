package io.github.deltacv.visionloop;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/**
 * A class that runs a vision loop asynchronously on a separate thread.
 * @author Sebastian Erives
 */
public class AsyncVisionLoopRunner {

    private final VisionLoop loop;

    private final ArrayDeque<Runnable> submitQueue = new ArrayDeque<>();

    private final ArrayDeque<Throwable> exceptions = new ArrayDeque<>();

    private Thread visionThread;

    private final Object haltLock = new Object();

    /**
     * Creates a new AsyncVisionLoopRunner with the given VisionLoop, name, and condition.
     * Consider using {@link VisionLoop#runAsync()} to create an instance of this class.
     * @param loop The VisionLoop to run
     * @param name The name of the thread
     * @param condition The condition to run the loop
     */
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

    /**
     * Starts the vision loop thread.
     * If created from {@link VisionLoop#runAsync()}, this method should not be called.
     * @throws IllegalStateException If the vision thread is already running
     */
    public void start() {
        if(visionThread.isAlive()) {
            throw new IllegalStateException("Vision thread is already running");
        }

        visionThread.start();
    }

    /**
     * Submits a runnable to be executed on the vision loop thread.
     * @param runnable The runnable to submit
     */
    public void submit(Runnable runnable) {
        synchronized (submitQueue) {
            submitQueue.add(runnable);
        }
    }

    /**
     * Joins the vision loop thread quietly.
     * This method will not inherit any exceptions thrown by the vision loop.
     */
    public void joinQuietly() {
        synchronized (haltLock) {
            try {
                haltLock.wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Joins the vision loop thread.
     * This method will throw any exceptions thrown by the vision loop.
     * @throws RuntimeException If the vision loop threw an exception
     */
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

    /**
     * Interrupts the vision loop thread
     * This is the preferred method to stop the vision loop thread.
     */
    public void interrupt() {
        visionThread.interrupt();
    }

    /**
     * Polls the next exception thrown by the vision loop.
     * @return The exception thrown by the vision loop, or null if there are no exceptions
     */
    public Throwable pollException() {
        synchronized (exceptions) {
            return exceptions.poll();
        }
    }

    /**
     * Checks if the vision loop thread is alive.
     * @return True if the vision loop thread is alive
     */
    public boolean isAlive() {
        return visionThread.isAlive();
    }

    /**
     * Checks if the vision loop thread has exceptions.
     * @return True if the vision loop thread has exceptions
     */
    public boolean hasExceptions() {
        synchronized (exceptions) {
            return !exceptions.isEmpty();
        }
    }

    /**
     * Gets the VisionLoop associated with this runner.
     * @return The VisionLoop associated with this runner
     */
    public VisionLoop getLoop() {
        return loop;
    }

}
