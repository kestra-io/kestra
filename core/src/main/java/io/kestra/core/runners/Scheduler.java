package io.kestra.core.runners;

import io.kestra.core.server.Service;

public interface Scheduler extends Service, Runnable{
    
    // The default max threads 
    int MIN_MAX_THREAD = 4;
        
    static int defaultMaxNumThreads() {
        return Math.min(MIN_MAX_THREAD, Runtime.getRuntime().availableProcessors());
    }
    
    /**
     * Starts the scheduler.
     */
    @Override
    default void run() {
        start(defaultMaxNumThreads());
    }
    
    /**
     * Starts the scheduler.
     * 
     * @param maxThreads    The maximum number of threads that can be used
     *                      by the scheduler for handling trigger scheduling. 
     */
    void start(int maxThreads);
    
    /**
     * Checks whether this scheduler is processing triggers.
     * 
     * @return {@code true} if this scheduler is active, otherwise {@code false}.
     */
    boolean isActive();
}
