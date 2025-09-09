package io.kestra.core.models.property;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.VariableRenderer;

import java.util.Map;

/**
 * Contextual object for rendering properties.
 * 
 * @see Property
 */
public interface PropertyContext {
    
    String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException;
    
    Map<String, Object> render(Map<String, Object> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException;
    
    /**
     * Static helper method for creating a new {@link PropertyContext} from a given {@link VariableRenderer}.
     *
     * @param renderer the {@link VariableRenderer}.
     * @return a new {@link PropertyContext}.
     */
    static PropertyContext create(final VariableRenderer renderer) {
        return new PropertyContext() {
            @Override
            public String render(String inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
                return renderer.render(inline, variables);
            }
            
            @Override
            public Map<String, Object> render(Map<String, Object> inline, Map<String, Object> variables) throws IllegalVariableEvaluationException {
                return renderer.render(inline, variables);
            }
        };
    }
}
