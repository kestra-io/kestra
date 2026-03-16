package io.kestra.tests.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/**
 * Base architecture test with common rules that apply across all modules.
 * This class can be extended by specific modules to add their own rules while inheriting common constraints.
 */
public class BaseArchitectureTest {

    protected final JavaClasses importedClasses = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("io.kestra");

    static final com.tngtech.archunit.lang.ArchRule no_java_util_logging =
        NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
}
