package io.kestra.core.junit.extensions;

import io.kestra.core.junit.annotations.KestraTest;
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
}
