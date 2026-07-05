package software.amazon.awssdk.core.exception;

public class AbortedException extends RuntimeException {
    public AbortedException(String message) {
        super(message);
    }

    public AbortedException(String message, Throwable cause) {
        super(message, cause);
    }
}
