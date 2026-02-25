package io.kestra.model.architecture;

import com.tngtech.archunit.lang.ArchRule;
import io.kestra.tests.architecture.BaseArchitectureTest;
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
            .should().dependOnClassesThat()
            .resideInAPackage("io.micronaut..")
            .because("Domain models should be framework-agnostic and must not depend on Micronaut");

        rule.check(importedClasses);
    }
}
