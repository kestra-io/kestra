package org.kestra.core.utils;

import lombok.extern.slf4j.Slf4j;

import java.lang.Thread.UncaughtExceptionHandler;

public final class UncaughtExceptionHandlers {
  private UncaughtExceptionHandlers() {}

  /**
   * Returns an exception handler that exits the system. This is particularly useful for the main
   * thread, which may start up other, non-daemon threads, but fail to fully initialize the
   * application successfully.
   *
   * <p>Example usage:
   *
   * <pre>
   * public static void main(String[] args) {
   *   Thread.currentThread().setUncaughtExceptionHandler(UncaughtExceptionHandlers.systemExit());
   *   ...
   * </pre>
   *
   * <p>The returned handler logs any exception at severity {@code ERROR} and then shuts down the
   * process with an exit status of 1, indicating abnormal termination.
   */
  public static UncaughtExceptionHandler systemExit() {
    return new Exiter(Runtime.getRuntime());
  }

  @Slf4j
  static final class Exiter implements UncaughtExceptionHandler {

    private final Runtime runtime;

    Exiter(Runtime runtime) {
      this.runtime = runtime;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
      try {
        // cannot use FormattingLogger due to a dependency loop
        log.error("Caught an exception in {}. Shutting down.", t, e);
      } catch (Throwable errorInLogging) {
        // If logging fails, e.g. due to missing memory, at least try to log the
        // message and the cause for the failed logging.
        System.err.println(e.getMessage());
        System.err.println(errorInLogging.getMessage());
      } finally {
        runtime.exit(1);
      }
    }
  }
}
