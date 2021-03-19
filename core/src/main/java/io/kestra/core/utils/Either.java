package io.kestra.core.utils;

import java.util.Optional;

public class Either<L, R> {
    private final Optional<L> left;
    private final Optional<R> right;

    private Either(Optional<L> left, Optional<R> right) {
        this.left = left;
        this.right = right;
    }

    public static <L, R> Either<L, R> left(L value) {
        return new Either<>(Optional.ofNullable(value), Optional.empty());
    }

    public boolean isLeft() {
        return this.left.isPresent();
    }

    public L getLeft() {
        return this.left.get();
    }

    public static <L, R> Either<L, R> right(R value) {
        return new Either<>(Optional.empty(), Optional.ofNullable(value));
    }

    public boolean isRight() {
        return this.right.isPresent();
    }

    public R getRight() {
        return this.right.get();
    }
}
