package io.kestra.tests.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.*;

/**
 * Base architecture test with common rules that apply across all modules.
 * This class can be extended by specific modules to add their own rules while inheriting common constraints.
 */
@AnalyzeClasses(packages = "io.kestra", importOptions = ImportOption.DoNotIncludeTests.class)
public class BaseArchitectureTest {

    @ArchTest
    static final ArchRule no_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule no_joda_time = NO_CLASSES_SHOULD_USE_JODATIME;

    @ArchTest
    static final ArchRule test_same_package = testClassesShouldResideInTheSamePackageAsImplementation();

    @ArchTest
    static final ArchRule no_standard_stream = noClasses()
        .that().doNotBelongToAnyOf(io.kestra.core.utils.ThreadUncaughtExceptionHandler.class)
        .should(ACCESS_STANDARD_STREAMS);

    @ArchTest
    public static final ArchRule no_production_use_of_awaitility = noClasses()
        .that().doNotBelongToAnyOf(io.kestra.core.utils.Await.class, io.kestra.core.junit.extensions.AbstractLoaderExtension.class)
        .should().dependOnClassesThat().resideInAPackage("org.awaitility")
        .because("you should not use it directly but use " + io.kestra.core.utils.Await.class.getName() + " wrapper instead");
}