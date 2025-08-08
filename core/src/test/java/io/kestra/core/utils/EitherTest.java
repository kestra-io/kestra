package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;

class EitherTest {
    
    @Test
    void shouldCreateLeftInstance() {
        // Given
        String leftValue = "error";
        
        // When
        Either<String, Integer> either = Either.left(leftValue);
        
        // Then
        assertThat(either).isInstanceOf(Either.Left.class);
        assertThat(either.isLeft()).isTrue();
        assertThat(either.isRight()).isFalse();
        assertThat(either.getLeft()).isEqualTo(leftValue);
    }
    
    @Test
    void shouldCreateRightInstance() {
        // Given
        Integer rightValue = 42;
        
        // When
        Either<String, Integer> either = Either.right(rightValue);
        
        // Then
        assertThat(either).isInstanceOf(Either.Right.class);
        assertThat(either.isRight()).isTrue();
        assertThat(either.isLeft()).isFalse();
        assertThat(either.getRight()).isEqualTo(rightValue);
    }
    
    @Test
    void shouldCreateLeftWithNullValue() {
        // When
        Either<String, Integer> either = Either.left(null);
        
        // Then
        assertThat(either.isLeft()).isTrue();
        assertThat(either.getLeft()).isNull();
    }
    
    @Test
    void shouldCreateRightWithNullValue() {
        // When
        Either<String, Integer> either = Either.right(null);
        
        // Then
        assertThat(either.isRight()).isTrue();
        assertThat(either.getRight()).isNull();
    }
    
    @Test
    void leftShouldReturnCorrectValues() {
        // Given
        String leftValue = "error message";
        Either<String, Integer> either = Either.left(leftValue);
        
        // Then
        assertThat(either.isLeft()).isTrue();
        assertThat(either.isRight()).isFalse();
        assertThat(either.getLeft()).isEqualTo(leftValue);
    }
    
    @Test
    void leftShouldThrowExceptionWhenGettingRightValue() {
        // Given
        Either<String, Integer> either = Either.left("error");
        
        // When/Then
        assertThatThrownBy(either::getRight)
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("This is Left");
    }
    
    @Test
    void rightShouldReturnCorrectValues() {
        // Given
        Integer rightValue = 100;
        Either<String, Integer> either = Either.right(rightValue);
        
        // Then
        assertThat(either.isRight()).isTrue();
        assertThat(either.isLeft()).isFalse();
        assertThat(either.getRight()).isEqualTo(rightValue);
    }
    
    @Test
    void rightShouldThrowExceptionWhenGettingLeftValue() {
        // Given
        Either<String, Integer> either = Either.right(42);
        
        // When/Then
        assertThatThrownBy(either::getLeft)
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("This is Right");
    }
    
    @Test
    void shouldApplyLeftFunctionForLeftInstanceInFold() {
        // Given
        Either<String, Integer> either = Either.left("error");
        Function<String, String> leftFn = s -> "Left: " + s;
        Function<Integer, String> rightFn = i -> "Right: " + i;
        
        // When
        String result = either.fold(leftFn, rightFn);
        
        // Then
        assertThat(result).isEqualTo("Left: error");
    }
    
    @Test
    void shouldApplyRightFunctionForRightInstanceInFold() {
        // Given
        Either<String, Integer> either = Either.right(42);
        Function<String, String> leftFn = s -> "Left: " + s;
        Function<Integer, String> rightFn = i -> "Right: " + i;
        
        // When
        String result = either.fold(leftFn, rightFn);
        
        // Then
        assertThat(result).isEqualTo("Right: 42");
    }
    
    @Test
    void shouldHandleNullReturnValuesInFold() {
        // Given
        Either<String, Integer> leftEither = Either.left("error");
        Either<String, Integer> rightEither = Either.right(42);
        
        // When
        String leftResult = leftEither.fold(s -> null, i -> "not null");
        String rightResult = rightEither.fold(s -> "not null", i -> null);
        
        // Then
        assertThat(leftResult).isNull();
        assertThat(rightResult).isNull();
    }
    
    @Test
    void leftProjectionShouldExistForLeftInstance() {
        // Given
        Either<String, Integer> either = Either.left("error");
        
        // When
        Either.LeftProjection<String, Integer> projection = either.left();
        
        // Then
        assertThat(projection.exists()).isTrue();
        assertThat(projection.get()).isEqualTo("error");
    }
    
    @Test
    void leftProjectionShouldNotExistForRightInstance() {
        // Given
        Either<String, Integer> either = Either.right(42);
        
        // When
        Either.LeftProjection<String, Integer> projection = either.left();
        
        // Then
        assertThat(projection.exists()).isFalse();
        assertThatThrownBy(projection::get)
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("This is Right");
    }
    
    @Test
    void leftProjectionMapShouldTransformLeftValue() {
        // Given
        Either<String, Integer> either = Either.left("error");
        
        // When
        Either<Integer, Integer> result = either.left().map(String::length);
        
        // Then
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo(5);
    }
    
    @Test
    void leftProjectionMapShouldPreserveRightValue() {
        // Given
        Either<String, Integer> either = Either.right(42);
        
        // When
        Either<Integer, Integer> result = either.left().map(String::length);
        
        // Then
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(42);
    }
    
    @Test
    void leftProjectionFlatMapShouldTransformLeftValue() {
        // Given
        Either<String, Integer> either = Either.left("error");
        
        // When
        Either<Integer, Integer> result = either.left().flatMap(s -> Either.left(s.length()));
        
        // Then
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo(5);
    }
    
    @Test
    void leftProjectionFlatMapShouldPreserveRightValue() {
        // Given
        Either<String, Integer> either = Either.right(42);
        
        // When
        Either<Integer, Integer> result = either.left().flatMap(s -> Either.left(s.length()));
        
        // Then
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(42);
    }
    
    @Test
    void leftProjectionFlatMapCanReturnRight() {
        // Given
        Either<String, Integer> either = Either.left("error");
        
        // When
        Either<String, Integer> result = either.left().flatMap(s -> Either.right(999));
        
        // Then
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(999);
    }
    
    @Test
    void leftProjectionToOptionalShouldReturnPresentForLeft() {
        // Given
        Either<String, Integer> either = Either.left("error");
        
        // When
        Optional<String> optional = either.left().toOptional();
        
        // Then
        assertThat(optional).isPresent();
        assertThat(optional.get()).isEqualTo("error");
    }
    
    @Test
    void leftProjectionToOptionalShouldReturnEmptyForRight() {
        // Given
        Either<String, Integer> either = Either.right(42);
        
        // When
        Optional<String> optional = either.left().toOptional();
        
        // Then
        assertThat(optional).isEmpty();
    }
    
    @Test
    void leftProjectionConstructorShouldThrowForNullEither() {
        // When/Then
        assertThatThrownBy(() -> new Either.LeftProjection<>(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("either can't be null");
    }
    
    @Test
    void rightProjectionShouldExistForRightInstance() {
        // Given
        Either<String, Integer> either = Either.right(42);
        
        // When
        Either.RightProjection<String, Integer> projection = either.right();
        
        // Then
        assertThat(projection.exists()).isTrue();
        assertThat(projection.get()).isEqualTo(42);
    }
    
    @Test
    void rightProjectionShouldNotExistForLeftInstance() {
        // Given
        Either<String, Integer> either = Either.left("error");
        
        // When
        Either.RightProjection<String, Integer> projection = either.right();
        
        // Then
        assertThat(projection.exists()).isFalse();
        assertThatThrownBy(projection::get)
            .isInstanceOf(NoSuchElementException.class)
            .hasMessage("This is Left");
    }
    
    @Test
    void rightProjectionMapShouldTransformRightValue() {
        // Given
        Either<String, Integer> either = Either.right(42);
        
        // When
        Either<String, String> result = either.right().map(Object::toString);
        
        // Then
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo("42");
    }
    
    @Test
    void rightProjectionMapShouldPreserveLeftValue() {
        // Given
        Either<String, Integer> either = Either.left("error");
        
        // When
        Either<String, String> result = either.right().map(Object::toString);
        
        // Then
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo("error");
    }
    
    @Test
    void rightProjectionFlatMapShouldTransformRightValue() {
        // Given
        Either<String, Integer> either = Either.right(42);
        
        // When
        Either<String, String> result = either.right().flatMap(i -> Either.right(i.toString()));
        
        // Then
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo("42");
    }
    
    @Test
    void rightProjectionFlatMapShouldPreserveLeftValue() {
        // Given
        Either<String, Integer> either = Either.left("error");
        
        // When
        Either<String, String> result = either.right().flatMap(i -> Either.right(i.toString()));
        
        // Then
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo("error");
    }
    
    @Test
    void rightProjectionFlatMapCanReturnLeft() {
        // Given
        Either<String, Integer> either = Either.right(42);
        
        // When
        Either<String, Integer> result = either.right().flatMap(i -> Either.left("converted"));
        
        // Then
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo("converted");
    }
    
    @Test
    void rightProjectionToOptionalShouldReturnPresentForRight() {
        // Given
        Either<String, Integer> either = Either.right(42);
        
        // When
        Optional<Integer> optional = either.right().toOptional();
        
        // Then
        assertThat(optional).isPresent();
        assertThat(optional.get()).isEqualTo(42);
    }
    
    @Test
    void rightProjectionToOptionalShouldReturnEmptyForLeft() {
        // Given
        Either<String, Integer> either = Either.left("error");
        
        // When
        Optional<Integer> optional = either.right().toOptional();
        
        // Then
        assertThat(optional).isEmpty();
    }
    
    @Test
    void rightProjectionConstructorShouldThrowForNullEither() {
        // When/Then
        assertThatThrownBy(() -> new Either.RightProjection<>(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("either can't be null");
    }
    
    @Test
    void shouldHandleNullValuesInTransformations() {
        // Given
        Either<String, Integer> leftEither = Either.left(null);
        Either<String, Integer> rightEither = Either.right(null);
        
        // When/Then
        assertThat(leftEither.left().map(s -> s == null ? "was null" : s).getLeft())
            .isEqualTo("was null");
        
        assertThat(rightEither.right().map(i -> i == null ? "was null" : i.toString()).getRight())
            .isEqualTo("was null");
    }
    
    @Test
    void shouldHandleComplexTypeTransformations() {
        // Given
        Either<Exception, String> either = Either.right("hello world");
        
        // When
        Either<String, Integer> result = either
            .left().map(Exception::getMessage)
            .right().map(String::length);
        
        // Then
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo(11);
    }
    
    @Test
    void shouldChainTransformationsCorrectly() {
        // Given
        Either<String, Integer> either = Either.right(10);
        
        // When
        Either<String, String> result = either
            .right().flatMap(i -> i > 5 ? Either.right(i * 2) : Either.left("too small"))
            .right().map(i -> "Result: " + i);
        
        // Then
        assertThat(result.isRight()).isTrue();
        assertThat(result.getRight()).isEqualTo("Result: 20");
    }
    
    @Test
    void shouldHandleProjectionChainingWithErrorCases() {
        // Given
        Either<String, Integer> either = Either.right(3);
        
        // When
        Either<String, String> result = either
            .right().flatMap(i -> i > 5 ? Either.right(i * 2) : Either.left("too small"))
            .right().map(i -> "Result: " + i);
        
        // Then
        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isEqualTo("too small");
    }
}