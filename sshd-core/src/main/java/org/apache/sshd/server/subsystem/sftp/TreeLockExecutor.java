package org.apache.sshd.server.subsystem.sftp;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.function.Function;

public class TreeLockExecutor implements Closeable {

    private static final Runnable CLOSE = () -> { };

    private final ExecutorService executor;
    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final Future<?> future;
    private final Function<String, Path> resolver;

    public TreeLockExecutor(ExecutorService executor, Function<String, Path> resolver) {
        this.executor = executor;
        this.resolver = resolver;
        this.future = executor.submit(this::run);
    }

    public void submit(Runnable work, String... paths) {
        queue.add(work);
    }

    protected void run() {
        while (true) {
            try {
                Runnable work = queue.take();
                if (work == CLOSE) {
                    break;
                }
                work.run();
            } catch (Throwable t) {
                // ignore
            }
        }
    }

    @Override
    public void close() {
        queue.clear();
        queue.add(CLOSE);
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Ignore
        }
        future.cancel(true);
    }
}
