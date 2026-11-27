package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.*;

class PairTest {
    
    @Test
    void shouldCreatesPairGivenTwoValues() {
        // Given
        String key = "foo";
        Integer value = 123;
        
        // When
        Pair<String, Integer> pair = Pair.of(key, value);
        
        // Then
        assertThat(pair._1()).isEqualTo("foo");
        assertThat(pair._2()).isEqualTo(123);
    }
    
    @Test
    void shouldCreatesPairGivenMapEntry() {
        // Given
        Map.Entry<String, Integer> entry = new AbstractMap.SimpleEntry<>("bar", 42);
        
        // When
        Pair<String, Integer> pair = Pair.of(entry);
        
        // Then
        assertThat(pair.key()).isEqualTo("bar");
        assertThat(pair.value()).isEqualTo(42);
    }
    
    @Test
    void shouldTransformsFirstElement() {
        // Given
        Pair<String, Integer> pair = Pair.of("test", 10);
        
        // When
        Pair<Integer, Integer> result = pair.mapLeft(String::length);
        
        // Then
        assertThat(result._1()).isEqualTo(4);
        assertThat(result._2()).isEqualTo(10);
    }
    
    @Test
    void shouldTransformsSecondElement() {
        // Given
        Pair<String, Integer> pair = Pair.of("hello", 5);
        
        // When
        Pair<String, String> result = pair.mapRight(Object::toString);
        
        // Then
        assertThat(result._1()).isEqualTo("hello");
        assertThat(result._2()).isEqualTo("5");
    }
    
    @Test
    void shouldSwapElements() {
        // Given
        Pair<String, Integer> pair = Pair.of("left", 99);
        
        // When
        Pair<Integer, String> swapped = pair.swap();
        
        // Then
        assertThat(swapped._1()).isEqualTo(99);
        assertThat(swapped._2()).isEqualTo("left");
    }
    
    @Test
    void shouldGetStreamOfOneElement() {
        // Given
        Pair<String, Integer> pair = Pair.of("stream", 1);
        
        // When
        long count = pair.stream().count();
        
        // Then
        assertThat(count).isEqualTo(1);
    }
    
    @Test
    void shouldGetAsMapEntry() {
        // Given
        Pair<String, Integer> pair = Pair.of("key", 888);
        
        // When
        Map.Entry<String, Integer> entry = pair.asMapEntry();
        
        // Then
        assertThat(entry.getKey()).isEqualTo("key");
        assertThat(entry.getValue()).isEqualTo(888);
    }
    
    @Test
    void shouldCheckIfBothElementsAreNull() {
        // Given
        Pair<String, Integer> pair = Pair.of(null, null);
        
        // When
        boolean isEmpty = pair.isEmpty();
        
        // Then
        assertThat(isEmpty).isTrue();
    }
    
    @Test
    void shouldTransformsBothElements() {
        // Given
        Pair<String, Integer> pair = Pair.of("abc", 7);
        
        // When
        Pair<Integer, String> result = pair.mapBoth(String::length, Object::toString);
        
        // Then
        assertThat(result).isEqualTo(Pair.of(3, "7"));
    }
    
    @Test
    void shouldReduceElements() {
        // Given
        Pair<String, Integer> pair = Pair.of("val", 2);
        
        // When
        String result = pair.reduce((s, i) -> s + "-" + i);
        
        // Then
        assertThat(result).isEqualTo("val-2");
    }
}