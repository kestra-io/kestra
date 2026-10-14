package io.kestra.core.exceptions;

import java.io.Serial;
import java.util.List;

public class CyclicGraphException extends KestraRuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public CyclicGraphException(String message) {
        super(message);
    }

    public static CyclicGraphException of(List<String> cycle) {
        return new CyclicGraphException(
            "Cannot walk the topology: it contains a cycle through %s. This is a bug in the graph construction, please report it with the failing flow.".formatted(String.join(" -> ", cycle))
        );
    }
}
