package io.kestra.model.architecture;

import com.tngtech.archunit.lang.ArchRule;
import io.kestra.tests.architecture.BaseArchitectureTest;
import io.micronaut.core.annotation.Generated;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests specific to the model module.
 * These tests enforce architectural constraints on model classes.
 */
public class ArchitectureTest extends BaseArchitectureTest {

    @Test
    void modelsShouldNotDependOnMicronaut() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..models..")
            .and().areNotAnnotatedWith(Generated.class)
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "io.micronaut.inject..",
                "io.micronaut.runtime..",
                "io.micronaut.aop.."
            )
            .because("Domain models should avoid Micronaut injection/runtime dependencies; annotations are allowed");

        rule.check(importedClasses);
    }
}
