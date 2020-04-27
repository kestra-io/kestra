package org.kestra.core.utils;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Rethrow {

    @FunctionalInterface
    public interface ConsumerChecked<T, E extends Exception> {
        void accept(T t) throws E;
    }

    @FunctionalInterface
    public interface FunctionChecked<T, R, E extends Exception> {
        R apply(T t) throws E;
    }

    @FunctionalInterface
    public interface PredicateChecked<T, E extends Exception> {
        boolean test(T t) throws E;
    }

    @FunctionalInterface
    public interface RunnableChecked<E extends Exception> {
        void run() throws E;
    }

    public static <T, E extends Exception> Consumer<T> throwConsumer(ConsumerChecked<T, E> consumer) throws E {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Exception exception) {
                throw throwException(exception);
            }
        };
    }

    public static <T, E extends Exception> Predicate<T> throwPredicate(PredicateChecked<T, E> consumer) throws E {
        return t -> {
            try {
                return consumer.test(t);
            } catch (Exception exception) {
                throw throwException(exception);
            }
        };
    }

    public static <T, R, E extends Exception> Function<T, R> throwFunction(FunctionChecked<T, R, E> function) throws E {
        return t -> {
            try {
                return function.apply(t);
            } catch (Exception exception) {
                throw throwException(exception);
            }
        };
    }

    public static <E extends Exception> Runnable throwRunnable(RunnableChecked<E> runnable) throws E {
        return () -> {
            try {
                runnable.run();
            } catch (Exception exception) {
                throw throwException(exception);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> E throwException(Exception exception) throws E {
        return (E) exception;
    }
}
