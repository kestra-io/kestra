package io.kestra.core.schedulers;

import jakarta.inject.Singleton;

@SuppressWarnings("try")
@Singleton
public interface Scheduler extends Runnable, AutoCloseable {

}
