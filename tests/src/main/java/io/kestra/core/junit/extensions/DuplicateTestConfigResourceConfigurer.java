package io.kestra.core.junit.extensions;

import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.ApplicationContextConfigurer;
import io.micronaut.context.annotation.ContextConfigurer;
import io.micronaut.core.io.ResourceLoadStrategy;
import io.micronaut.core.io.ResourceLoadStrategyType;

/**
 * Kestra's Gradle build exposes each module's own test resources (via the cross-project
 * {@code testArtifacts} configuration) on the test classpath of every module that depends on it,
 * so multiple modules' own {@code application-test.yml} end up on the same classpath under the
 * same name. Micronaut 5 defaults to {@link ResourceLoadStrategyType#FAIL_ON_DUPLICATE} for
 * same-named configuration resources.
 * <p>
 * Use {@code FIRST_MATCH} instead of the module's own {@code application-test.yml} which
 * is naturally first on its own test classpath.
 */
@ContextConfigurer
public class DuplicateTestConfigResourceConfigurer implements ApplicationContextConfigurer {
    @Override
    public void configure(ApplicationContextBuilder builder) {
        builder.configurationLoadingStrategy(ResourceLoadStrategy.builder().type(ResourceLoadStrategyType.FIRST_MATCH));
    }
}
