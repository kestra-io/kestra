package io.kestra.core.models.flows.check;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class CheckTest {
    
    @Test
    void shouldReturnCreateExecutionGivenNullList() {
        // Given
        List<Check> checks = null;
        
        // When
        Check.Behavior result = Check.resolveBehavior(checks);
        
        // Then
        assertThat(result).isEqualTo(Check.Behavior.CREATE_EXECUTION);
    }
    
    @Test
    void shouldReturnCreateExecutionGivenEmptyList() {
        // Given
        List<Check> checks = List.of();
        
        // When
        Check.Behavior result = Check.resolveBehavior(checks);
        
        // Then
        assertThat(result).isEqualTo(Check.Behavior.CREATE_EXECUTION);
    }
    
    @Test
    void shouldReturnCreateExecutionGivenOnlyCreateExecutionChecks() {
        // Given
        List<Check> checks = List.of(
            Check.builder().behavior(Check.Behavior.CREATE_EXECUTION).build(),
            Check.builder().behavior(Check.Behavior.CREATE_EXECUTION).build()
        );
        
        // When
        Check.Behavior result = Check.resolveBehavior(checks);
        
        // Then
        assertThat(result).isEqualTo(Check.Behavior.CREATE_EXECUTION);
    }
    
    @Test
    void shouldReturnFailExecutionGivenFailAndCreateChecks() {
        // Given
        List<Check> checks = List.of(
            Check.builder().behavior(Check.Behavior.CREATE_EXECUTION).build(),
            Check.builder().behavior(Check.Behavior.FAIL_EXECUTION).build()
        );
        
        // When
        Check.Behavior result = Check.resolveBehavior(checks);
        
        // Then
        assertThat(result).isEqualTo(Check.Behavior.FAIL_EXECUTION);
    }
    
    @Test
    void shouldReturnBlockExecutionGivenMixedBehaviors() {
        // Given
        List<Check> checks = List.of(
            Check.builder().behavior(Check.Behavior.CREATE_EXECUTION).build(),
            Check.builder().behavior(Check.Behavior.FAIL_EXECUTION).build(),
            Check.builder().behavior(Check.Behavior.BLOCK_EXECUTION).build()
        );
        
        // When
        Check.Behavior result = Check.resolveBehavior(checks);
        
        // Then
        assertThat(result).isEqualTo(Check.Behavior.BLOCK_EXECUTION);
    }
    
    @Test
    void shouldIgnoreNullBehaviorsGivenMixedValues() {
        // Given
        List<Check> checks = List.of(
            Check.builder().behavior(null).build(),
            Check.builder().behavior(Check.Behavior.CREATE_EXECUTION).build()
        );
        
        // When
        Check.Behavior result = Check.resolveBehavior(checks);
        
        // Then
        assertThat(result).isEqualTo(Check.Behavior.CREATE_EXECUTION);
    }
}