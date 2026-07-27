package io.kestra.core.utils;

import java.util.concurrent.ThreadFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public final class ThreadMainFactoryBuilder {

    private ThreadMainFactoryBuilder() {
        // utility class pattern
    }

    public static ThreadFactory build(String name) {
        return new ThreadFactoryBuilder()
            .setNameFormat(name)
            // a new handler per factory: it captures the KestraContext current at build time
            .setUncaughtExceptionHandler(new ThreadUncaughtExceptionHandler())
            .build();
    }
}
