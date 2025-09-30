package io.kestra.scheduler;

public interface JdbcQueue extends AutoCloseable {
    
    @Override
    void close();
}
