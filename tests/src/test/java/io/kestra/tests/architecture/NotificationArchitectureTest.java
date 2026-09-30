package io.kestra.tests.architecture;

import java.util.regex.Pattern;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import io.kestra.core.repositories.NotificationItemRepositoryInterface;
import io.kestra.core.repositories.NotificationRepositoryInterface;
import io.kestra.core.services.NotificationService;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.kestra", importOptions = ImportOption.DoNotIncludeTests.class)
public class NotificationArchitectureTest {

    private static final Pattern READ_METHODS = Pattern.compile("^(find|count).*");
    private static final Pattern WRITE_METHODS = Pattern.compile("^(create|save|update|delete|mark).*");

    @ArchTest
    public static final ArchRule notification_writes_go_through_the_service = noClasses()
        .that().areNotAssignableTo(NotificationRepositoryInterface.class)
        .and().areNotAssignableTo(NotificationItemRepositoryInterface.class)
        .and().doNotBelongToAnyOf(NotificationService.class)
        .should().callCodeUnitWhere(
            DescribedPredicate.describe(
                "a Notification or NotificationItem repository write method is called",
                (JavaAccess<?> access) -> (access.getTarget().getOwner().isAssignableTo(NotificationRepositoryInterface.class)
                    || access.getTarget().getOwner().isAssignableTo(NotificationItemRepositoryInterface.class))
                    && WRITE_METHODS.matcher(access.getTarget().getName()).matches()
            )
        )
        .because(
            "every Notification/NotificationItem write must go through " + NotificationService.class.getName()
                + " (correlation-key upsert and timestamp handling)"
        );

    @ArchTest
    public static final ArchRule notification_repository_methods_are_classified = methods()
        .that().areDeclaredIn(NotificationRepositoryInterface.class)
        .or().areDeclaredIn(NotificationItemRepositoryInterface.class)
        .should(new ArchCondition<>("match a read or write method name pattern") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (!READ_METHODS.matcher(method.getName()).matches() && !WRITE_METHODS.matcher(method.getName()).matches()) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method, method.getFullName()
                                + " matches neither pattern; classify it in " + NotificationArchitectureTest.class.getSimpleName()
                                + " so the write-path rule keeps covering it"
                        )
                    );
                }
            }
        })
        .because("an unclassified repository method would silently escape the write-path rule");
}
