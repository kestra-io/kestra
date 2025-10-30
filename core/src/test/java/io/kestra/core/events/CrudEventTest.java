package io.kestra.core.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class CrudEventTest {
    
    @Test
    void shouldReturnCreateEventWhenModelIsProvided() {
        // Given
        String model = "testModel";
        
        // When
        CrudEvent<String> event = CrudEvent.create(model);
        
        // Then
        assertThat(event.getModel()).isEqualTo(model);
        assertThat(event.getPreviousModel()).isNull();
        assertThat(event.getType()).isEqualTo(CrudEventType.CREATE);
        assertThat(event.getRequest()).isNull();
    }
    
    @Test
    void shouldThrowExceptionWhenCreateEventWithNullModel() {
        // Given
        String model = null;
        
        // When / Then
        assertThatThrownBy(() -> CrudEvent.create(model))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Can't create CREATE event with a null model");
    }
    
    @Test
    void shouldReturnDeleteEventWhenModelIsProvided() {
        // Given
        String model = "testModel";
        
        // When
        CrudEvent<String> event = CrudEvent.delete(model);
        
        // Then
        assertThat(event.getModel()).isNull();
        assertThat(event.getPreviousModel()).isEqualTo(model);
        assertThat(event.getType()).isEqualTo(CrudEventType.DELETE);
        assertThat(event.getRequest()).isNull();
    }
    
    @Test
    void shouldThrowExceptionWhenDeleteEventWithNullModel() {
        // Given
        String model = null;
        
        // When / Then
        assertThatThrownBy(() -> CrudEvent.delete(model))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Can't create DELETE event with a null model");
    }
    
    @Test
    void shouldReturnUpdateEventWhenBeforeAndAfterAreProvided() {
        // Given
        String before = "oldModel";
        String after = "newModel";
        
        // When
        CrudEvent<String> event = CrudEvent.of(before, after);
        
        // Then
        assertThat(event.getModel()).isEqualTo(after);
        assertThat(event.getPreviousModel()).isEqualTo(before);
        assertThat(event.getType()).isEqualTo(CrudEventType.UPDATE);
        assertThat(event.getRequest()).isNull();
    }
    
    @Test
    void shouldReturnCreateEventWhenBeforeIsNullAndAfterIsProvided() {
        // Given
        String before = null;
        String after = "newModel";
        
        // When
        CrudEvent<String> event = CrudEvent.of(before, after);
        
        // Then
        assertThat(event.getModel()).isEqualTo(after);
        assertThat(event.getPreviousModel()).isNull();
        assertThat(event.getType()).isEqualTo(CrudEventType.CREATE);
        assertThat(event.getRequest()).isNull();
    }
    
    @Test
    void shouldReturnDeleteEventWhenAfterIsNullAndBeforeIsProvided() {
        // Given
        String before = "oldModel";
        String after = null;
        
        // When
        CrudEvent<String> event = CrudEvent.of(before, after);
        
        // Then
        assertThat(event.getModel()).isNull();
        assertThat(event.getPreviousModel()).isEqualTo(before);
        assertThat(event.getType()).isEqualTo(CrudEventType.DELETE);
        assertThat(event.getRequest()).isNull();
    }
    
    @Test
    void shouldThrowExceptionWhenBothBeforeAndAfterAreNull() {
        // Given
        String before = null;
        String after = null;
        
        // When / Then
        assertThatThrownBy(() -> CrudEvent.of(before, after))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Both before and after cannot be null");
    }
}