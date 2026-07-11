package com.smart.agent.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Named thread factory utility.
 *
 * @description Creates daemon threads with a descriptive name prefix, reducing boilerplate
 *              in ThreadPoolExecutor configurations across the codebase.
 * @author Jiangbo Li
 * @date 2026-06-18
 * @version 1.0
 */
public class NamedThreadFactory implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger seq = new AtomicInteger(1);

    public NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, prefix + "-" + seq.getAndIncrement());
        t.setDaemon(true);
        return t;
    }
}
