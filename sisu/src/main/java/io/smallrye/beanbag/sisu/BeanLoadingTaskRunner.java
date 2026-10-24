package io.smallrye.beanbag.sisu;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.logging.Logger;

/**
 * Task runner that runs {@link BeanLoadingTask}s asynchronously and in parallel.
 * <p>
 * Tasks are submitted to the common pool but the thread waiting for their completion also runs
 * the tasks that have not been picked up by the pool yet. This guarantees progress even if no pool worker
 * is available, e.g. when beans are loaded during GraalVM native image build time class initialization
 * which itself may be running on a (saturated) common pool worker.
 */
class BeanLoadingTaskRunner {

    private static final Logger log = Logger.getLogger(BeanLoadingTaskRunner.class);

    /**
     * Tasks that may not have been started yet
     */
    private final ConcurrentLinkedDeque<ClaimableTask> pending = new ConcurrentLinkedDeque<>();
    /**
     * Errors caught while running tasks
     */
    private final Collection<Exception> errors = new ConcurrentLinkedDeque<>();
    /**
     * Guards {@link #incomplete} and is used to notify the waiting thread about new or completed tasks
     */
    private final Object lock = new Object();
    /**
     * Number of tasks that have been scheduled but not completed yet
     */
    private int incomplete;

    /**
     * Runs a bean loading task asynchronously. This method may return before the task has completed.
     *
     * @param task task to run
     */
    void run(BeanLoadingTask task) {
        final ClaimableTask claimable = new ClaimableTask(task);
        synchronized (lock) {
            ++incomplete;
            pending.add(claimable);
            lock.notifyAll();
        }
        CompletableFuture.runAsync(claimable::tryRun);
    }

    /**
     * Blocks until all the tasks have completed, running the tasks that have not been started yet
     * on the calling thread.
     * <p>
     * In case some tasks failed with errors, this method will log each error and throw a {@link RuntimeException}
     * with a corresponding message.
     */
    void waitForCompletion() {
        boolean interrupted = false;
        try {
            for (;;) {
                ClaimableTask task;
                while ((task = pending.poll()) != null) {
                    task.tryRun();
                }
                synchronized (lock) {
                    if (incomplete == 0) {
                        break;
                    }
                    // the remaining tasks are being run by other threads and may schedule more tasks
                    if (pending.isEmpty()) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                    }
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (!errors.isEmpty()) {
            log.error("The following errors where caught while loading beans:");
            int i = 0;
            for (var e : errors) {
                log.error(++i + ") " + e.getMessage(), e);
            }
            throw new RuntimeException("Failed to load beans, please see the errors logged above");
        }
    }

    /**
     * A task that is run by whichever thread claims it first
     */
    private final class ClaimableTask {

        private final BeanLoadingTask task;
        private final AtomicBoolean claimed = new AtomicBoolean();

        private ClaimableTask(BeanLoadingTask task) {
            this.task = task;
        }

        private void tryRun() {
            if (!claimed.compareAndSet(false, true)) {
                return;
            }
            try {
                task.run();
            } catch (Exception e) {
                errors.add(e);
            } finally {
                synchronized (lock) {
                    --incomplete;
                    lock.notifyAll();
                }
            }
        }
    }
}
