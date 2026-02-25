package io.kestra.core.architecture;

import com.tngtech.archunit.lang.ArchRule;
import io.kestra.tests.architecture.BaseArchitectureTest;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests specific to the core module.
 * These tests enforce architectural constraints on core classes such as services, repositories, and tasks.
 */
public class ArchitectureTest extends BaseArchitectureTest {

    @Test
    void servicesShouldNotDependOnEndpoints() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..services..")
            .should().dependOnClassesThat()
            .resideInAPackage("..endpoints..")
            .because("Services should not depend on controller/endpoint layer");

        rule.check(importedClasses);
    }

    @Test
    void repositoriesShouldBeNamedCorrectly() {
        ArchRule rule = classes()
            .that().resideInAPackage("..repositories..")
            .and().areNotInterfaces()
            .and().areNotAnnotations()
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Repository")
            .orShould().haveSimpleNameEndingWith("RepositoryService")
            .because("Repository classes should follow naming conventions");

        rule.check(importedClasses);
    }

    @Test
    void servicesShouldBeNamedCorrectly() {
        ArchRule rule = classes()
            .that().resideInAPackage("..services..")
            .and().areNotInterfaces()
            .and().areNotAnnotations()
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Service")
            .orShould().haveSimpleNameEndingWith("ServiceInterface")
            .because("Service classes should follow naming conventions");

        rule.check(importedClasses);
    }

    @Test
    void tasksShouldNotDependOnRepositories() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..tasks..")
            .should().dependOnClassesThat().resideInAPackage("..repositories..")
            .because("Tasks should not directly access repositories");

        rule.check(importedClasses);
    }
}
