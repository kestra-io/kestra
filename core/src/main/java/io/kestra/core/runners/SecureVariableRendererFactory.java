package io.kestra.core.runners;

import java.util.List;

import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.kestra.core.runners.pebble.functions.KvFunction;
import io.kestra.core.runners.pebble.functions.SecretFunction;

import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Creates a {@link VariableRenderer} masking {@code secret()} and {@code kv()} at the function level, i.e. the
 * expression itself only ever sees {@code ******}.
 * <p>
 * This is meant for expressions supplied by a caller (e.g. the execution eval endpoint), where evaluating on the
 * real value would let the caller extract it one character at a time. Flow-authored expressions, like input
 * defaults, must instead be evaluated on the real values and have the secrets masked in their output, see
 * {@link FlowInputOutput} (#18195).
 */
@Singleton
public class SecureVariableRendererFactory {

    private final PebbleEngineFactory pebbleEngineFactory;
    private final ApplicationContext applicationContext;

    private VariableRenderer secureVariableRenderer;

    @Inject
    public SecureVariableRendererFactory(ApplicationContext applicationContext, PebbleEngineFactory pebbleEngineFactory) {
        this.pebbleEngineFactory = pebbleEngineFactory;
        this.applicationContext = applicationContext;
    }

    /**
     * Creates or returns the existing secured {@link VariableRenderer} instance.
     *
     * @return the secured {@link VariableRenderer} instance
     */
    public synchronized VariableRenderer createOrGet() {
        if (this.secureVariableRenderer == null) {
            // Explicitly create a new instance through the application context to ensure
            // eventual custom VariableRenderer implementation is used
            secureVariableRenderer = applicationContext.createBean(VariableRenderer.class);
            secureVariableRenderer.setPebbleEngine(pebbleEngineFactory.createWithMaskedFunctions(secureVariableRenderer, List.of(SecretFunction.NAME, KvFunction.NAME)));
        }
        return secureVariableRenderer;
    }
}
