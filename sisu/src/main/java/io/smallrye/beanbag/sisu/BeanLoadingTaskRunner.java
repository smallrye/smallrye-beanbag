package io.smallrye.beanbag.sisu;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Phaser;

import org.jboss.logging.Logger;

/**
 * Task runner that runs {@link BeanLoadingTask}s asynchronously and in parallel
 */
class BeanLoadingTaskRunner {

    private static final Logger log = Logger.getLogger(BeanLoadingTaskRunner.class);

    private final Phaser phaser = new Phaser(1);
    /**
     * Errors caught while running tasks
     */
    private final Collection<Exception> errors = new ConcurrentLinkedDeque<>();

    /**
     * Runs a bean loading task asynchronously. This method may return before the task has completed.
     *
     * @param task task to run
     */
    void run(BeanLoadingTask task) {
        phaser.register();
        CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                errors.add(e);
            } finally {
                phaser.arriveAndDeregister();
            }
        });
    }

    /**
     * Blocks until all the tasks have completed.
     * <p>
     * In case some tasks failed with errors, this method will log each error and throw a {@link RuntimeException}
     * with a corresponding message.
     */
    void waitForCompletion() {
        phaser.arriveAndAwaitAdvance();
        if (!errors.isEmpty()) {
            log.error("The following errors where caught while loading beans:");
            int i = 0;
            for (var e : errors) {
                log.error(++i + ") " + e.getMessage(), e);
            }
            throw new RuntimeException("Failed to load beans, please see the errors logged above");
        }
    }
}
