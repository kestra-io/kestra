package org.kestra.core.utils;

import java.util.concurrent.Callable;
import java.util.function.*;

public final class Rethrow {
    @FunctionalInterface
    public interface ConsumerChecked<T, E extends Exception> {
        void accept(T t) throws Exception;
    }

    @FunctionalInterface
    public interface SupplierChecked<T, E extends Exception> {
        T get() throws Exception;
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
                throw throwException(exception);
            }
        };
    }

    public static <T, E extends Exception> Supplier<T> throwSupplier(SupplierChecked<T, E> supplier) throws E {
        return () -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                throw throwException(exception);
            }
        };
    }


    public static <K, V, E extends Exception> BiConsumer<K, V> throwBiConsumer(BiConsumerChecked<K, V, E> consumer) throws E {
        return (k, v) -> {
            try {
                consumer.accept(k, v);
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

    public static <R, E extends Exception> Callable<R> throwCallable(CallableChecked<R, E> runnable) throws E {
        return () -> {
            try {
                return runnable.call();
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
