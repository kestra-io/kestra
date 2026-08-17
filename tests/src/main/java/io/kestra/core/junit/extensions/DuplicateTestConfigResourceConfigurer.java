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
 * Use {@code FIRST_MATCH} rather than {@code MERGE_ALL}: a module's own {@code application-test.yml}
 * is naturally first on its own test classpath (Gradle puts a project's own output ahead of its
 * dependencies' artifacts), so {@code FIRST_MATCH} preserves each module's own settings for keys its
 * file defines. {@code MERGE_ALL} instead combines every module's file into one view with an
 * unspecified per-key precedence, which silently let an upstream dependency's (e.g. core's) value for
 * a shared key like {@code kestra.url} override the consuming module's (e.g. webserver's) own value.
 */
@ContextConfigurer
public class DuplicateTestConfigResourceConfigurer implements ApplicationContextConfigurer {
    @Override
    public void configure(ApplicationContextBuilder builder) {
        builder.configurationLoadingStrategy(ResourceLoadStrategy.builder().type(ResourceLoadStrategyType.FIRST_MATCH));
    }
}
