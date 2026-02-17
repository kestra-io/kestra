package io.kestra.core.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ThreadFactory;


public final class ThreadMainFactoryBuilder {

    private ThreadMainFactoryBuilder() {
        // utility class pattern
    }

    public static ThreadFactory build(String name) {
        return new ThreadFactoryBuilder()
            .setNameFormat(name)
            .setUncaughtExceptionHandler(ThreadUncaughtExceptionHandler.INSTANCE)
            .build();
    }
}
