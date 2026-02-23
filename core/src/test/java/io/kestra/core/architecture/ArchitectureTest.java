package io.kestra.core.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/**
 * Architecture tests using ArchUnit to enforce coding rules and architectural constraints.
 * These tests help maintain code quality and architectural boundaries across the codebase.
 */
public class ArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setup() {
        importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.kestra");
    }

    @Test
    void servicesShouldNotDependOnControllers() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..services..")
            .should().dependOnClassesThat().resideInAPackage("..endpoints..")
            .because("Services should not depend on controller/endpoint layer");

        rule.check(importedClasses);
    }

    @Test
    void noClassesShouldUseJavaUtilLogging() {
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(importedClasses);
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
    void modelsShouldNotDependOnServices() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..models..")
            .should().dependOnClassesThat().resideInAPackage("..services..")
            .because("Models should be independent of service layer");

        rule.check(importedClasses);
    }

    @Test
    void modelsShouldNotDependOnRepositories() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..models..")
            .should().dependOnClassesThat().resideInAPackage("..repositories..")
            .because("Models should be independent of repository layer");

        rule.check(importedClasses);
    }

    @Test
    void modelsShouldNotDependOnMicronaut() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..models..")
            .should().dependOnClassesThat()
            .resideInAPackage("io.micronaut..")
            .because("Domain models should be framework-agnostic and must not depend on Micronaut");

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
