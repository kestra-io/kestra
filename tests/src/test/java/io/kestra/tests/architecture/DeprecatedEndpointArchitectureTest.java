package io.kestra.tests.architecture;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import io.kestra.core.utils.Version;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An endpoint marked {@code @Deprecated(forRemoval = true)} lives for one minor release: deprecated in 2.1, it has
 * to be deleted before 2.2 development starts. The rule fails as soon as the build version reaches the next minor,
 * so the removal cannot be forgotten.
 */
@AnalyzeClasses(packages = "io.kestra", importOptions = ImportOption.DoNotIncludeTests.class)
public class DeprecatedEndpointArchitectureTest {
    private static final String HTTP_METHOD_MAPPING_ANNOTATION = "io.micronaut.http.annotation.HttpMethodMapping";

    @ArchTest
    static final ArchRule endpoints_deprecated_for_removal_are_removed_after_one_minor = endpointsDeprecatedForRemovalAreRemovedAfterOneMinor(currentVersion());

    @Test
    void shouldAcceptEndpointWhenStillInsideItsDeprecationWindow() {
        assertThatCode(() -> endpointsDeprecatedForRemovalAreRemovedAfterOneMinor(Version.of("2.1.0-SNAPSHOT")).check(fixtures(DeprecatedController.class)))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectEndpointWhenNextMinorDevelopmentHasStarted() {
        // a SNAPSHOT sorts before its release, so the window must close on major.minor alone to fire at the start of 2.2
        assertThatThrownBy(() -> endpointsDeprecatedForRemovalAreRemovedAfterOneMinor(Version.of("2.2.0-SNAPSHOT")).check(fixtures(DeprecatedController.class)))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("DeprecatedController.scheduled()")
            .hasMessageContaining("deprecated since 2.1.0, so it must be removed before 2.2.0-SNAPSHOT");
    }

    @Test
    void shouldRejectEndpointWhenSinceIsMissing() {
        assertThatThrownBy(() -> endpointsDeprecatedForRemovalAreRemovedAfterOneMinor(Version.of("2.1.0-SNAPSHOT")).check(fixtures(UnscheduledController.class)))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("UnscheduledController.unscheduled()")
            .hasMessageContaining("has no 'since'");
    }

    static ArchRule endpointsDeprecatedForRemovalAreRemovedAfterOneMinor(final Version current) {
        return methods()
            .that().areMetaAnnotatedWith(HTTP_METHOD_MAPPING_ANNOTATION)
            .and().areAnnotatedWith(Deprecated.class)
            .should(new ArchCondition<>("be removed one minor release after the version they were deprecated in") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    Deprecated deprecated = method.getAnnotationOfType(Deprecated.class);
                    if (!deprecated.forRemoval()) {
                        return;
                    }
                    if (deprecated.since().isBlank()) {
                        events.add(SimpleConditionEvent.violated(method, "%s is deprecated for removal but has no 'since'.".formatted(method.getFullName())));
                        return;
                    }
                    Version since = Version.of(deprecated.since());
                    if (isLaterMinor(current, since)) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                "%s is deprecated since %s, so it must be removed before %s.".formatted(method.getFullName(), deprecated.since(), current)
                            )
                        );
                    }
                }
            })
            .because("a deprecated endpoint is supported for one minor release, so the next minor must ship without it")
            .allowEmptyShould(true);
    }

    private static boolean isLaterMinor(final Version current, final Version since) {
        if (current.majorVersion() != since.majorVersion()) {
            return current.majorVersion() > since.majorVersion();
        }
        return current.minorVersion() > since.minorVersion();
    }

    private static Version currentVersion() {
        try (InputStream gradleProperties = DeprecatedEndpointArchitectureTest.class.getClassLoader().getResourceAsStream("gradle.properties")) {
            if (gradleProperties == null) {
                throw new IllegalStateException("Cannot find gradle.properties on the classpath to read the current Kestra version.");
            }
            Properties properties = new Properties();
            properties.load(gradleProperties);
            return Version.of(properties.getProperty("version"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JavaClasses fixtures(final Class<?> controller) {
        return new ClassFileImporter().importClasses(controller);
    }

    @Controller("/fixture")
    static class DeprecatedController {
        @Deprecated(since = "2.1.0", forRemoval = true)
        @Get("/scheduled")
        String scheduled() {
            return "";
        }

        @Deprecated(since = "2.0")
        @Get("/kept")
        String kept() {
            return "";
        }
    }

    @Controller("/fixture")
    static class UnscheduledController {
        @Deprecated(forRemoval = true)
        @Get("/unscheduled")
        String unscheduled() {
            return "";
        }
    }
}
