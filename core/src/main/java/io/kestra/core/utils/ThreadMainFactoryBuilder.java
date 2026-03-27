package io.kestra.core.utils;

import java.util.concurrent.ThreadFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ThreadMainFactoryBuilder {
    @Inject
    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;

    public ThreadFactory build(String name) {
        return new ThreadFactoryBuilder()
            .setNameFormat(name)
            .setUncaughtExceptionHandler(this.uncaughtExceptionHandler)
            .build();
    }
}
