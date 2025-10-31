package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class CustomVariableRendererTest {
    
    @Inject
    private SecureVariableRendererFactory secureVariableRendererFactory;
    
    @Inject
    private VariableRenderer renderer;
    
    @Test
    void shouldUseCustomVariableRender() throws IllegalVariableEvaluationException {
        // When
        String result = renderer.render("{{ dummy }}", Map.of());
        
        // Then
        assertThat(result).isEqualTo("alternativeRender");
    }
    
    @Test
    void shouldUseCustomVariableRenderWhenUsingSecured() throws IllegalVariableEvaluationException {
        // Given
        VariableRenderer renderer = secureVariableRendererFactory.createOrGet();
        
        // When
        String result = renderer.render("{{ dummy }}", Map.of());
        
        // Then
        assertThat(result).isEqualTo("alternativeRender");
    }
    
    @MockBean(VariableRenderer.class)
    VariableRenderer testCustomRenderer(ApplicationContext applicationContext) {
        return new VariableRenderer(applicationContext, null) {
            
            @Override
            protected String alternativeRender(Exception e, String inline, Map<String, Object> variables) {
                return "alternativeRender";
            }
        };
    }
}