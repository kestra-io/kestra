package io.kestra.core.runners.pebble;

/**
 * Thrown by display-engine function proxies to signal that a given expression segment
 * is non-deterministic or has side effects and must therefore be kept raw rather than resolved.
 * The caller ({@code DisplayExpressionRenderer}) catches this and falls back to the original template text.
 */
public class DisplayUnrenderableException extends RuntimeException {

    public DisplayUnrenderableException() {
        super(null, null, true, false); // suppress stack-trace capture — this is a control-flow signal
    }
}
