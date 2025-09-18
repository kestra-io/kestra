package io.kestra.plugin.flink;

/**
 * Exception thrown when Flink operations fail.
 */
public class FlinkException extends Exception {
    
    public FlinkException(String message) {
        super(message);
    }
    
    public FlinkException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public FlinkException(Throwable cause) {
        super(cause);
    }
}