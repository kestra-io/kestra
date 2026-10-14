package io.kestra.core.runners.pebble;

import java.io.Writer;

public abstract class OutputWriter extends Writer {
    /**
     * Maximum number of characters the rendered output may reach before rendering is aborted.
     * A value {@code <= 0} disables the check (unbounded output).
     */
    private final long maxOutputSize;

    protected OutputWriter() {
        this(0L);
    }

    protected OutputWriter(final long maxOutputSize) {
        this.maxOutputSize = maxOutputSize;
    }

    /**
     * Guards against unbounded output growth during rendering. Concrete writers call this with the
     * current total output size after each write; once it exceeds {@link #maxOutputSize}, a bounded
     * {@link RenderLimitExceededException} is thrown so rendering fails gracefully instead of
     * exhausting the heap.
     *
     * @param currentSize the current total output size, in characters
     * @throws RenderLimitExceededException if {@code currentSize} exceeds the configured maximum
     */
    protected void checkOutputSize(final long currentSize) {
        if (maxOutputSize > 0 && currentSize > maxOutputSize) {
            throw new RenderLimitExceededException(
                "The rendered output exceeds the maximum allowed size of %d characters.".formatted(maxOutputSize)
            );
        }
    }

    public abstract Object output();
}
