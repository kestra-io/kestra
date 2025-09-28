package io.kestra.core.runners;

import io.kestra.core.runners.pebble.functions.SecretFunction;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
public class SecureVariableRendererFactory {
    
    @Inject
    private ApplicationContext applicationContext;
    
    @Inject 
    private VariableRenderer.VariableConfiguration variableConfiguration;
    
    @Inject
    private VariableRenderer baseRenderer; // Injected renderer (may be custom)
    
    public VariableRenderer createDebugRenderer() {
        // Create debug renderer that wraps the injected renderer
        return new DebugVariableRenderer(
            baseRenderer, 
            applicationContext, 
            variableConfiguration, 
            List.of(SecretFunction.NAME)
        );
    }
}

