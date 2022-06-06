package io.kestra.core.schedulers;

import jakarta.inject.Singleton;

@Singleton
public interface Scheduler extends Runnable, AutoCloseable {

}
