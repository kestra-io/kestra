package io.kestra.core.junit.extensions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.TestRunner;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.annotation.MicronautTestValue;
import io.micronaut.test.extensions.junit5.MicronautJunit5Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import java.util.Set;

public class KestraTestExtension extends MicronautJunit5Extension {
    @Override
    protected MicronautTestValue buildMicronautTestValue(Class<?> testClass) {
        testProperties.put("kestra.jdbc.executor.thread-count", Runtime.getRuntime().availableProcessors() * 4);
        return AnnotationSupport
            .findAnnotation(testClass, KestraTest.class)
            .map(kestraTestAnnotation -> {
                var envsSet = new java.util.HashSet<>(Set.of(kestraTestAnnotation.environments()));
                envsSet.add("test");// add test env if not already present
                return new MicronautTestValue(
                    kestraTestAnnotation.application(),
                    envsSet.toArray(new String[0]),
                    kestraTestAnnotation.packages(),
                    kestraTestAnnotation.propertySources(),
                    kestraTestAnnotation.rollback(),
                    kestraTestAnnotation.transactional(),
                    kestraTestAnnotation.rebuildContext(),
                    kestraTestAnnotation.contextBuilder(),
                    kestraTestAnnotation.transactionMode(),
                    kestraTestAnnotation.startApplication(),
                    kestraTestAnnotation.resolveParameters()
                );
            })
            .orElse(null);
    }

    @Override
    protected ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getRoot().getStore(ExtensionContext.Namespace.create(KestraTestExtension.class, context.getTestClass().get()));
    }

    @Override
    protected boolean hasExpectedAnnotations(Class<?> testClass) {
        return AnnotationSupport.isAnnotated(testClass, KestraTest.class);
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        super.beforeAll(extensionContext);
        KestraTest kestraTest = extensionContext.getTestClass()
            .orElseThrow()
            .getAnnotation(KestraTest.class);
        if (kestraTest.startRunner()){
            TestRunner runner = applicationContext.getBean(TestRunner.class);
            if (!runner.isRunning()){
                runner.setSchedulerEnabled(kestraTest.startScheduler());
                runner.setWorkerEnabled(kestraTest.startWorker());
                runner.run();
            }
        }
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        super.afterTestExecution(context);

        TestsUtils.queueConsumersCleanup();
    }
}
