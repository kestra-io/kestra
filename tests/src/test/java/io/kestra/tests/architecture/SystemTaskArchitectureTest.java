package io.kestra.tests.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import io.kestra.core.models.tasks.SystemTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.Services;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.kestra", importOptions = ImportOption.DoNotIncludeTests.class)
public class SystemTaskArchitectureTest {

    static final DescribedPredicate<JavaAccess<?>> ADDITIONAL_SERVICE_IS_CALLED = DescribedPredicate.describe(
        "Services.additionalService() is called",
        access -> access.getTarget().getOwner().isAssignableTo(Services.class)
            && "additionalService".equals(access.getTarget().getName())
    );

    @ArchTest
    public static final ArchRule tasks_reaching_for_internal_services_are_system_tasks = noClasses()
        .that().areAssignableTo(Task.class)
        .and().areNotAssignableTo(SystemTask.class)
        .should().callCodeUnitWhere(ADDITIONAL_SERVICE_IS_CALLED)
        .because(
            "Services.additionalService() throws on a dedicated Worker; only a " + SystemTask.class.getName()
                + " is routed to the SystemWorker, which is the only Worker with access to internal services"
        );
}
