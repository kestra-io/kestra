package io.kestra.tests.architecture;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Shared by the OSS and EE architecture tests so that both editions apply the same pagination rules.
 */
public final class PaginationRules {

    private static final String QUERY_VALUE = "io.micronaut.http.annotation.QueryValue";
    private static final String MAX = "jakarta.validation.constraints.Max";
    private static final String CONTROLLER = "io.micronaut.http.annotation.Controller";
    private static final Set<String> PAGINATION_BEANS = Set.of(
        "io.kestra.webserver.models.api.Pagination",
        "io.kestra.webserver.models.api.UnsortedPagination"
    );
    // Mirrors PageableUtils.MAX_PAGE_SIZE, which this module cannot reference.
    private static final long MAX_PAGE_SIZE = 1000L;

    private PaginationRules() {
    }

    /**
     * No controller method re-declares a page or size query parameter, and every allowlist entry still matches a method.
     *
     * @param allowed {@code SimpleClassName#method} (all its overloads) to the reason why its paging contract differs from the shared beans
     */
    public static ArchRule controllersDoNotRedeclarePaging(Map<String, String> allowed) {
        return methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(CONTROLLER)
            .should(notDeclarePageOrSizeParameter(allowed))
            .because("page, size and sort are declared once by Pagination or UnsortedPagination, use @Valid @RequestBean instead of re-declaring them");
    }

    /**
     * Every controller method returning a {@code *PagedResults} takes {@code Pagination} or {@code UnsortedPagination}, whatever its parameters are called.
     */
    public static ArchRule pagedControllersUsePaginationBeans(Map<String, String> allowed) {
        return methods()
            .that(DescribedPredicate.describe(
                "are declared in a controller and return a PagedResults",
                (JavaMethod method) -> method.getOwner().isAnnotatedWith(CONTROLLER)
                    && method.getReturnType().getAllInvolvedRawTypes().stream().anyMatch(type -> type.getSimpleName().endsWith("PagedResults"))
            ))
            .should(new ArchCondition<JavaMethod>("take a Pagination or UnsortedPagination parameter") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    String id = id(method);
                    boolean usesBean = method.getParameters().stream().anyMatch(parameter -> PAGINATION_BEANS.contains(parameter.getRawType().getName()));
                    if (!usesBean && !allowed.containsKey(id)) {
                        events.add(SimpleConditionEvent.violated(method, "%s returns a page without a Pagination parameter (%s)".formatted(id, method.getSourceCodeLocation())));
                    }
                }
            })
            .because("a paginated endpoint declares its paging through Pagination or UnsortedPagination");
    }

    private static ArchCondition<JavaMethod> notDeclarePageOrSizeParameter(Map<String, String> allowed) {
        return new ArchCondition<>("not declare a raw page or size query parameter") {
            private final Set<String> seen = new HashSet<>();

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String id = id(method);
                seen.add(id);
                if (allowed.containsKey(id)) {
                    return;
                }
                for (JavaParameter parameter : method.getParameters()) {
                    if (isRawPagingParameter(parameter)) {
                        events.add(SimpleConditionEvent.violated(method, "%s declares a raw page or size parameter (%s)".formatted(id, method.getSourceCodeLocation())));
                        return;
                    }
                }
            }

            @Override
            public void finish(ConditionEvents events) {
                allowed.keySet().stream()
                    .filter(id -> !seen.contains(id))
                    .forEach(id -> events.add(SimpleConditionEvent.violated(id, "%s is allowed but no such controller method exists, remove it from the allowlist".formatted(id))));
            }
        };
    }

    private static String id(JavaMethod method) {
        return method.getOwner().getSimpleName() + "#" + method.getName();
    }

    private static boolean isRawPagingParameter(JavaParameter parameter) {
        if (!parameter.isAnnotatedWith(QUERY_VALUE)) {
            return false;
        }
        JavaAnnotation<JavaParameter> queryValue = parameter.getAnnotationOfType(QUERY_VALUE);
        boolean namedPageOrSize = isPageOrSize(queryValue.get("value")) || isPageOrSize(queryValue.get("name"));
        boolean pageSizeBound = parameter.isAnnotatedWith(MAX)
            && parameter.getAnnotationOfType(MAX).get("value").map(max -> MAX_PAGE_SIZE == (Long) max).orElse(false);
        return namedPageOrSize || pageSizeBound;
    }

    private static boolean isPageOrSize(Optional<Object> name) {
        return name.filter(value -> "page".equals(value) || "size".equals(value)).isPresent();
    }
}
