package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.micronaut.context.ApplicationContext;
import java.util.List;
import java.util.Map;

public class DebugVariableRenderer extends VariableRenderer {
    private final VariableRenderer delegateRenderer;
    
    public DebugVariableRenderer(VariableRenderer delegate, 
                               ApplicationContext context,
                               VariableConfiguration config, 
                               List<String> maskedFunctions) {
        // Pass maskedFunctions to parent constructor - this creates the proxy mechanism
        // that automatically masks the specified functions by returning "******"
        super(context, config, maskedFunctions);
        this.delegateRenderer = delegate;
    }
    
    @Override
    protected String alternativeRender(Exception e, String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
        // Delegate to the base renderer's alternative render method
        // The masking is already handled by the parent class proxy mechanism
        return delegateRenderer.alternativeRender(e, inline, variables);
    }
}
