package io.smallrye.beanbag.maven;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.aether.RepositorySystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Bean loading must complete even if no common pool worker is available to run bean loading tasks,
 * e.g. when the factory is created during GraalVM native image build time class initialization.
 */
// the test blocks all the common pool workers, which would stall other tests using the common pool if run in parallel
@Isolated
public final class SaturatedCommonPoolTestCase {

    private final Object blockersLock = new Object();
    private boolean releaseBlockers;

    @BeforeEach
    public void saturateCommonPool() throws InterruptedException {
        final int parallelism = ForkJoinPool.getCommonPoolParallelism();
        // with a parallelism of 1, CompletableFuture.runAsync() does not use the common pool and runs each task
        // in a new thread, so a deadlock could not be detected (see the surefire argLine configuration)
        assumeTrue(parallelism > 1, "Common pool parallelism must be greater than 1 but is " + parallelism);
        final CountDownLatch started = new CountDownLatch(parallelism);
        for (int i = 0; i < parallelism; i++) {
            ForkJoinPool.commonPool().execute(() -> {
                started.countDown();
                // Object.wait() is not a managed blocker, so the pool will not compensate for blocked workers
                synchronized (blockersLock) {
                    while (!releaseBlockers) {
                        try {
                            blockersLock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            });
        }
        assertTrue(started.await(10, TimeUnit.SECONDS), "Failed to occupy all common pool workers");
    }

    @AfterEach
    public void releaseCommonPool() {
        synchronized (blockersLock) {
            releaseBlockers = true;
            blockersLock.notifyAll();
        }
    }

    @Test
    public void testCreateWithSaturatedCommonPool() throws Exception {
        final AtomicReference<Object> result = new AtomicReference<>();
        final Thread t = new Thread(() -> {
            try {
                result.set(MavenFactory.create(MavenFactory.class.getClassLoader()).getRepositorySystem());
            } catch (Throwable e) {
                result.set(e);
            }
        });
        t.setDaemon(true);
        t.start();
        t.join(TimeUnit.SECONDS.toMillis(30));
        assertFalse(t.isAlive(), "MavenFactory.create() did not complete, bean loading appears to be deadlocked");
        assertNotNull(result.get());
        if (result.get() instanceof Throwable e) {
            throw new AssertionError(e);
        }
        assertInstanceOf(RepositorySystem.class, result.get());
    }
}
