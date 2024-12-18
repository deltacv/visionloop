package io.github.deltacv.visionloop;

import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;

/**
 * A class that runs a vision loop asynchronously on a separate thread
 */
public class AsyncVisionLoop {

    private final VisionLoop loop;
    private final String name;

    private final ArrayDeque<Runnable> submitQueue = new ArrayDeque<>();
    private final ArrayDeque<Throwable> exceptions = new ArrayDeque<>();

    private Thread visionThread;

    private final Object haltLock = new Object();

    /**
     * Creates a new AsyncVisionLoop with the given VisionLoop, name, and condition.
     * Consider using {@link VisionLoop#toAsync()} to create an instance of this class.
     * @param loop The VisionLoop to run
     * @param name The name of the thread
     */
    public AsyncVisionLoop(VisionLoop loop, String name) {
        this.loop = loop;
        this.name = name;
    }

    /**
     * Starts the vision loop thread and runs it while the condition is true.
     * @param condition The condition
     * @throws IllegalStateException If the vision thread is already running
     * @return This AsyncVisionLoop instance
     */
    public AsyncVisionLoop runWhile(BooleanSupplier condition) {
        if(visionThread != null && visionThread.isAlive()) {
            throw new IllegalStateException("Vision thread is already running");
        }

        visionThread = new Thread(() -> {
            try (loop) {
                while (!Thread.interrupted()) {
                    loop.run();

                    while (!submitQueue.isEmpty() && condition.getAsBoolean()) {
                        submitQueue.poll().run();
                    }
                }
            } catch (Exception e) {
                // this is a necessary evil no matter what intellij says
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
        }, "Thread-AsyncVisionLoop-"+name);

        visionThread.start();

        return this;
    }

    /**
     * Starts the vision loop thread.
     * @throws IllegalStateException If the vision thread is already running
     * @see #runWhile(BooleanSupplier)
     * @return This AsyncVisionLoop instance
     */
    public AsyncVisionLoop run() {
        runWhile(() -> true);
        return this;
    }

    /**
     * Submits a runnable to be executed on the vision loop thread.
     * @param runnable The runnable to submit
     * @return This AsyncVisionLoop instance
     */
    public AsyncVisionLoop submit(Runnable runnable) {
        synchronized (submitQueue) {
            submitQueue.add(runnable);
        }

        return this;
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
        if(visionThread != null && visionThread.isAlive())
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
        return visionThread != null && visionThread.isAlive();
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

}
