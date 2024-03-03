package io.kestra.core.plugins;

import io.kestra.core.annotations.ClassLoaderIsolated;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
class ClassLoaderIsolatedInterceptorTest {

    @Inject
    ApplicationContext context;

    @Inject
    ClassLoaderIsolatedInterceptor interceptor;

    @Test
    void test() {
        // Given
        interceptor.setInterceptAnyClassLoader(true); // only for-testing

        ClassLoader parent = ClassLoaderIsolatedInterceptorTest.class.getClassLoader().getParent();
        Thread.currentThread().setContextClassLoader(parent);

        // When
        CaptureClassLoaderTest testBean = context.createBean(CaptureClassLoaderTest.class); // trigger interceptor on constructor
        Assertions.assertEquals(parent, getContextClassLoader());

        testBean.run(); // trigger interceptor on method

        // Then
        Assertions.assertEquals(parent, getContextClassLoader());

        Assertions.assertNotEquals(testBean.capturedConstructClassLoader, getContextClassLoader());
        Assertions.assertNotEquals(testBean.capturedMethodClassLoader, getContextClassLoader());
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    @Introspected
    @ClassLoaderIsolated
    public static class CaptureClassLoaderTest {

        public ClassLoader capturedConstructClassLoader;
        public ClassLoader capturedMethodClassLoader;

        public CaptureClassLoaderTest() {
            capturedConstructClassLoader = getContextClassLoader();
        }

        public void run() {
            capturedMethodClassLoader = getContextClassLoader();
        }
    }
}