package io.kestra.core.utils;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * An immutable tuple of two elements.
 *
 * @param <T1> the type of the 1st element.
 * @param <T2> the type of the 2nd element.
 */
public record Pair<T1, T2>(T1 _1, T2 _2) {
    
    /**
     * Creates a {@code Pair} from a Map.Entry.
     *
     * @param entry the Map.Entry to convert
     * @param <T1>  the key type
     * @param <T2>  the value type
     * @return a new Pair with the key and value from the entry
     */
    public static <T1, T2> Pair<T1, T2> of(Map.Entry<T1, T2> entry) {
        return new Pair<>(entry.getKey(), entry.getValue());
    }
    
    /**
     * Creates a {@code Pair} from two values.
     *
     * @param o1   the first value
     * @param o2   the second value
     * @param <T1> the type of the first value
     * @param <T2> the type of the second value
     * @return a new Pair of the two values
     */
    public static <T1, T2> Pair<T1, T2> of(T1 o1, T2 o2) {
        return new Pair<>(o1, o2);
    }
    
    /**
     * Creates a tuple of two elements.
     *
     * @param _1 the 1st element
     * @param _2 the 2nd element
     */
    public Pair {
    }
    
    /**
     * Alias for {@link #_1()}.
     *
     * @return the first element
     */
    public T1 key() {
        return this._1;
    }
    
    /**
     * Alias for {@link #_2()}.
     *
     * @return the second element
     */
    public T2 value() {
        return this._2;
    }
    
    /**
     * Alias for {@link #_1()}.
     *
     * @return the first element
     */
    public T1 left() {
        return this._1;
    }
    
    /**
     * Alias for {@link #_2()}.
     *
     * @return the second element
     */
    public T2 right() {
        return this._2;
    }
    
    /**
     * Transforms the first element of the pair.
     *
     * @param mapper function to transform the first element
     * @param <R>    the result type of the transformation
     * @return a new Pair with the transformed first element
     */
    public <R> Pair<R, T2> mapLeft(Function<? super T1, ? extends R> mapper) {
        return new Pair<>(mapper.apply(_1), _2);
    }
    
    /**
     * Transforms the second element of the pair.
     *
     * @param mapper function to transform the second element
     * @param <R>    the result type of the transformation
     * @return a new Pair with the transformed second element
     */
    public <R> Pair<T1, R> mapRight(Function<? super T2, ? extends R> mapper) {
        return new Pair<>(_1, mapper.apply(_2));
    }
    
    /**
     * Transforms both elements of the pair.
     *
     * @param leftMapper  function to transform the first element
     * @param rightMapper function to transform the second element
     * @param <R1>        result type of first element
     * @param <R2>        result type of second element
     * @return a new Pair with both elements transformed
     */
    public <R1, R2> Pair<R1, R2> mapBoth(Function<? super T1, ? extends R1> leftMapper,
                                         Function<? super T2, ? extends R2> rightMapper) {
        return new Pair<>(leftMapper.apply(_1), rightMapper.apply(_2));
    }
    
    /**
     * Applies a bi-function to both elements.
     *
     * @param mapper function to apply to both elements
     * @param <R>    result type
     * @return the result of applying the function to the pair
     */
    public <R> R reduce(BiFunction<? super T1, ? super T2, ? extends R> mapper) {
        return mapper.apply(_1, _2);
    }
    
    /**
     * Returns a {@link Stream} containing only this pair.
     *
     * @return a singleton stream of this pair
     */
    public Stream<Pair<T1, T2>> stream() {
        return Stream.of(this);
    }
    
    /**
     * Converts this pair into a {@link Map.Entry}.
     *
     * @return a Map.Entry representing the pair
     */
    public Map.Entry<T1, T2> asMapEntry() {
        return Map.entry(this._1, this._2);
    }
    
    /**
     * Swaps the elements of the pair.
     *
     * @return a new Pair with the elements swapped
     */
    public Pair<T2, T1> swap() {
        return new Pair<>(_2, _1);
    }
    
    /**
     * Checks if both elements are null.
     *
     * @return true if both elements are null
     */
    public boolean isEmpty() {
        return _1 == null && _2 == null;
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public String toString() {
        return "(" + _1 + "," + _2 + ')';
    }
}