package io.kestra.core.runners;

import io.kestra.core.runners.pebble.PebbleEngineFactory;
import io.kestra.core.runners.pebble.functions.SecretFunction;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

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
            secureVariableRenderer.setPebbleEngine(pebbleEngineFactory.createWithMaskedFunctions(secureVariableRenderer, List.of(SecretFunction.NAME)));
        }
        return secureVariableRenderer;
    }
}

