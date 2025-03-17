package io.kestra.core.utils;

import java.util.function.*;

public final class Rethrow {
    @FunctionalInterface
    public interface ConsumerChecked<T, E extends Exception> {
        void accept(T t) throws E;
    }

    @FunctionalInterface
    public interface SupplierChecked<T, E extends Exception> {
        T get() throws E;
    }

    @FunctionalInterface
    public interface BiConsumerChecked<K, V, E extends Exception> {
        void accept(K k, V v) throws E;
    }

    @FunctionalInterface
    public interface FunctionChecked<T, R, E extends Exception> {
        R apply(T t) throws E;
    }

    @FunctionalInterface
    public interface BiFunctionChecked<A, B, R, E extends Exception> {
        R apply(A a, B b) throws E;
    }

    @FunctionalInterface
    public interface PredicateChecked<T, E extends Exception> {
        boolean test(T t) throws E;
    }

    @FunctionalInterface
    public interface RunnableChecked<E extends Exception> {
        void run() throws E;
    }

    @FunctionalInterface
    public interface CallableChecked<R, E extends Exception> {
        R call() throws E;
    }

    public static <T, E extends Exception> Consumer<T> throwConsumer(ConsumerChecked<T, E> consumer) throws E {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Exception exception) {
                throwException(exception);
            }
        };
    }

    public static <K, V, E extends Exception> BiConsumer<K, V> throwBiConsumer(BiConsumerChecked<K, V, E> consumer) throws E {
        return (k, v) -> {
            try {
                consumer.accept(k, v);
            } catch (Exception exception) {
                throwException(exception);
            }
        };
    }

    public static <T, E extends Exception> Supplier<T> throwSupplier(SupplierChecked<T, E> supplier) throws E {
        return () -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                return throwException(exception);
            }
        };
    }

    public static <T, E extends Exception> Predicate<T> throwPredicate(PredicateChecked<T, E> consumer) throws E {
        return t -> {
            try {
                return consumer.test(t);
            } catch (Exception exception) {
                return throwException(exception);
            }
        };
    }

    public static <T, R, E extends Exception> Function<T, R> throwFunction(FunctionChecked<T, R, E> function) throws E {
        return t -> {
            try {
                return function.apply(t);
            } catch (Exception exception) {
                return throwException(exception);
            }
        };
    }

    public static <A, B, R, E extends Exception> BiFunction<A, B, R> throwBiFunction(BiFunctionChecked<A, B, R, E> function) throws E {
        return (a, b) -> {
            try {
                return function.apply(a, b);
            } catch (Exception exception) {
                return throwException(exception);
            }
        };
    }

    public static <E extends Exception> Runnable throwRunnable(RunnableChecked<E> runnable) throws E {
        return () -> {
            try {
                runnable.run();
            } catch (Exception exception) {
                throwException(exception);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception, R> R throwException(Exception exception) throws E {
        throw (E) exception;
    }
}
