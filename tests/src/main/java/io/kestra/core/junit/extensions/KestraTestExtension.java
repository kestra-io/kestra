package io.kestra.core.junit.extensions;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.runners.StandAloneRunner;
import io.micronaut.test.annotation.MicronautTestValue;
import io.micronaut.test.extensions.junit5.MicronautJunit5Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

public class KestraTestExtension extends MicronautJunit5Extension {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(KestraTestExtension.class);

    @Override
    protected MicronautTestValue buildMicronautTestValue(Class<?> testClass) {
        return AnnotationSupport
            .findAnnotation(testClass, KestraTest.class)
            .map(kestraTestAnnotation -> new MicronautTestValue(
                kestraTestAnnotation.application(),
                kestraTestAnnotation.environments(),
                kestraTestAnnotation.packages(),
                kestraTestAnnotation.propertySources(),
                kestraTestAnnotation.rollback(),
                kestraTestAnnotation.transactional(),
                kestraTestAnnotation.rebuildContext(),
                kestraTestAnnotation.contextBuilder(),
                kestraTestAnnotation.transactionMode(),
                kestraTestAnnotation.startApplication(),
                kestraTestAnnotation.resolveParameters()
            ))
            .orElse(null);
    }

    @Override
    protected ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getRoot().getStore(NAMESPACE);
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
            StandAloneRunner runner = applicationContext.getBean(StandAloneRunner.class);
            if (!runner.isRunning()){
                runner.setSchedulerEnabled(kestraTest.startScheduler());
                runner.run();
            }
        }
    }
}
