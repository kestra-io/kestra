package io.kestra.core.utils;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Simple {@link Either} monad type.
 *
 * @param <L> the {@link Left} type.
 * @param <R> the {@link Right} type.
 */
public abstract sealed class Either<L, R> permits Either.Left, Either.Right {
    
    public static <L, R> Either<L, R> left(L value) {
        return new Left<>(value);
    }
    
    public static <L, R> Either<L, R> right(R value) {
        return new Right<>(value);
    }
    
    /**
     * Returns {@code true} if this is a {@link Left}, {@code false} otherwise.
     */
    public abstract boolean isLeft();
    
    /**
     * Returns {@code true} if this is a {@link Right}, {@code false} otherwise.
     */
    public abstract boolean isRight();
    
    /**
     * Returns the left value.
     *
     * @throws NoSuchElementException if is not left.
     */
    public abstract L getLeft();
    
    /**
     * Returns the right value.
     *
     * @throws NoSuchElementException if is not right.
     */
    public abstract R getRight();
    
    public LeftProjection<L, R> left() {
        return new LeftProjection<>(this);
    }
    
    public RightProjection<L, R> right() {
        return new RightProjection<>(this);
    }
    
    public <T> T fold(final Function<L, T> fl, final Function<R, T> fr) {
        return isLeft() ? fl.apply(getLeft()) : fr.apply(getRight());
    }
    
    public static final class Left<L, R> extends Either<L, R> {
        
        private final L value;
        
        private Left(L value) {
            this.value = value;
        }
        
        /**
         * @return {@code true}.
         */
        @Override
        public boolean isLeft() {
            return true;
        }
        
        /**
         * @return {@code false}.
         */
        @Override
        public boolean isRight() {
            return false;
        }
        
        @Override
        public L getLeft() {
            return value;
        }
        
        @Override
        public R getRight() {
            throw new NoSuchElementException("This is Left");
        }
    }
    
    public static final class Right<L, R> extends Either<L, R> {
        
        private final R value;
        
        private Right(R value) {
            this.value = value;
        }
        
        /**
         * @return {@code false}.
         */
        @Override
        public boolean isLeft() {
            return false;
        }
        
        /**
         * @return {@code true}.
         */
        @Override
        public boolean isRight() {
            return true;
        }
        
        @Override
        public L getLeft() {
            throw new NoSuchElementException("This is Right");
        }
        
        @Override
        public R getRight() {
            return value;
        }
    }
    
    public static class LeftProjection<L, R> {
        
        private final Either<L, R> either;
        
        LeftProjection(final Either<L, R> either) {
            Objects.requireNonNull(either, "either can't be null");
            this.either = either;
        }
        
        public boolean exists() {
            return either.isLeft();
        }
        
        public L get() {
            return either.getLeft();
        }
        
        public <LL> Either<LL, R> map(final Function<? super L, ? extends LL> fn) {
            if (either.isLeft()) return Either.left(fn.apply(either.getLeft()));
            else return Either.right(either.getRight());
        }
        
        public <LL> Either<LL, R> flatMap(final Function<? super L, Either<LL, R>> fn) {
            if (either.isLeft()) return fn.apply(either.getLeft());
            else return Either.right(either.getRight());
        }
        
        public Optional<L> toOptional() {
            return exists() ? Optional.of(either.getLeft()) : Optional.empty();
        }
    }
    
    public static class RightProjection<L, R> {
        
        private final Either<L, R> either;
        
        RightProjection(final Either<L, R> either) {
            Objects.requireNonNull(either, "either can't be null");
            this.either = either;
        }
        
        public boolean exists() {
            return either.isRight();
        }
        
        public R get() {
            return either.getRight();
        }
        
        public <RR> Either<L, RR> map(final Function<? super R, ? extends RR> fn) {
            if (either.isRight()) return Either.right(fn.apply(either.getRight()));
            else return Either.left(either.getLeft());
        }
        
        public <RR> Either<L, RR> flatMap(final Function<? super R, Either<L, RR>> fn) {
            if (either.isRight()) return fn.apply(either.getRight());
            else return Either.left(either.getLeft());
        }
        
        public Optional<R> toOptional() {
            return exists() ? Optional.of(either.getRight()) : Optional.empty();
        }
    }
}