package io.kestra.core.runners.pebble;

import java.io.Writer;

public abstract class OutputWriter extends Writer {
    public abstract Object output();
}
