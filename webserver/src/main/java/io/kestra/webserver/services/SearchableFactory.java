package io.kestra.webserver.services;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.apache.commons.lang3.Strings;
import org.slf4j.event.Level;

import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.docs.Plugin;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.FlowScope;
import io.kestra.core.models.namespaces.Namespace;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.core.services.FollowLogEventMatcher;
import io.kestra.webserver.utils.Searchable;

import java.time.Instant;
import java.time.ZonedDateTime;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
public class SearchableFactory {

    @Singleton
    @Named("NAMESPACE")
    public Searchable<Namespace> namespaceSearchable() {
        return Searchable.<Namespace>builder()
            .searchableExtractor("id", Namespace::getId)
            .sortableExtractor("id", Namespace::getId)
            .searchableQueryFilterExtractor(QueryFilter.Field.QUERY, QueryFilter.Op.EQUALS,
                (ns, v) -> Strings.CI.contains(ns.getId(), v.toString()))
            .searchableQueryFilterExtractor(QueryFilter.Field.QUERY, QueryFilter.Op.NOT_EQUALS,
                (ns, v) -> !Strings.CI.contains(ns.getId(), v.toString()))
            .searchableQueryFilterExtractor(QueryFilter.Field.NAMESPACE, Namespace::getId,
                QueryFilter.Op.EQUALS, QueryFilter.Op.NOT_EQUALS, QueryFilter.Op.CONTAINS,
                QueryFilter.Op.STARTS_WITH, QueryFilter.Op.ENDS_WITH, QueryFilter.Op.REGEX,
                QueryFilter.Op.IN, QueryFilter.Op.NOT_IN, QueryFilter.Op.PREFIX)
            .build();
    }

    @Singleton
    @Named("PLUGIN")
    public Searchable<Plugin> pluginSearchable() {
        return Searchable.<Plugin>builder()
            .searchableQueryFilterExtractor(QueryFilter.Field.QUERY, QueryFilter.Op.EQUALS,
                (plugin, v) -> pluginQueryMatches(plugin, v))
            .searchableQueryFilterExtractor(QueryFilter.Field.QUERY, QueryFilter.Op.NOT_EQUALS,
                (plugin, v) -> !pluginQueryMatches(plugin, v))
            // ARTIFACT_ID matches on Plugin.getName() — the X-Kestra-Name manifest entry (the Maven artifactId).
            .searchableQueryFilterExtractor(QueryFilter.Field.ARTIFACT_ID, Plugin::getName,
                QueryFilter.Op.IN, QueryFilter.Op.NOT_IN)
            .build();
    }

    /**
     * Substring match for the plugin QUERY filter across the plugin title, group and name (artifactId).
     */
    private static boolean pluginQueryMatches(Plugin plugin, Object queryValue) {
        String needle = queryValue == null ? "" : queryValue.toString();
        return Stream.of(plugin.getTitle(), plugin.getGroup(), plugin.getName())
            .filter(Objects::nonNull)
            .anyMatch(field -> field.contains(needle));
    }

    @Singleton
    @Named("LOG")
    public Searchable<FollowLogEvent> followLogEventSearchable(SystemFlowsConfiguration systemFlowsConfiguration) {
        String systemNamespace = systemFlowsConfiguration.namespace();
        return Searchable.<FollowLogEvent>builder()
            .searchableQueryFilterExtractor(QueryFilter.Field.QUERY, QueryFilter.Op.EQUALS,
                (event, v) -> event.message() != null && event.message().contains(v.toString()))
            .searchableQueryFilterExtractor(QueryFilter.Field.QUERY, QueryFilter.Op.NOT_EQUALS,
                (event, v) -> event.message() == null || !event.message().contains(v.toString()))

            .searchableQueryFilterExtractor(QueryFilter.Field.SCOPE, QueryFilter.Op.EQUALS,
                (event, v) -> scopeMatches(event, v, systemNamespace))
            .searchableQueryFilterExtractor(QueryFilter.Field.SCOPE, QueryFilter.Op.NOT_EQUALS,
                (event, v) -> !scopeMatches(event, v, systemNamespace))
            .searchableQueryFilterExtractor(QueryFilter.Field.SCOPE, QueryFilter.Op.IN,
                (event, v) -> v instanceof List<?> list
                    && list.stream().anyMatch(item -> scopeMatches(event, item, systemNamespace)))
            .searchableQueryFilterExtractor(QueryFilter.Field.SCOPE, QueryFilter.Op.NOT_IN,
                (event, v) -> !(v instanceof List<?> list)
                    || list.stream().noneMatch(item -> scopeMatches(event, item, systemNamespace)))

            .searchableQueryFilterExtractor(QueryFilter.Field.NAMESPACE, FollowLogEvent::namespace,
                QueryFilter.Op.EQUALS, QueryFilter.Op.NOT_EQUALS, QueryFilter.Op.CONTAINS,
                QueryFilter.Op.STARTS_WITH, QueryFilter.Op.ENDS_WITH, QueryFilter.Op.REGEX,
                QueryFilter.Op.IN, QueryFilter.Op.NOT_IN, QueryFilter.Op.PREFIX)

            .searchableQueryFilterExtractor(QueryFilter.Field.START_DATE, QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO,
                (event, v) -> compareTimestamps(event, v) >= 0)
            .searchableQueryFilterExtractor(QueryFilter.Field.START_DATE, QueryFilter.Op.GREATER_THAN,
                (event, v) -> compareTimestamps(event, v) > 0)
            .searchableQueryFilterExtractor(QueryFilter.Field.START_DATE, QueryFilter.Op.LESS_THAN_OR_EQUAL_TO,
                (event, v) -> compareTimestamps(event, v) <= 0)
            .searchableQueryFilterExtractor(QueryFilter.Field.START_DATE, QueryFilter.Op.LESS_THAN,
                (event, v) -> compareTimestamps(event, v) < 0)
            .searchableQueryFilterExtractor(QueryFilter.Field.START_DATE, QueryFilter.Op.EQUALS,
                (event, v) -> compareTimestamps(event, v) == 0)
            .searchableQueryFilterExtractor(QueryFilter.Field.START_DATE, QueryFilter.Op.NOT_EQUALS,
                (event, v) -> compareTimestamps(event, v) != 0)

            .searchableQueryFilterExtractor(QueryFilter.Field.END_DATE, QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO,
                (event, v) -> compareTimestamps(event, v) >= 0)
            .searchableQueryFilterExtractor(QueryFilter.Field.END_DATE, QueryFilter.Op.GREATER_THAN,
                (event, v) -> compareTimestamps(event, v) > 0)
            .searchableQueryFilterExtractor(QueryFilter.Field.END_DATE, QueryFilter.Op.LESS_THAN_OR_EQUAL_TO,
                (event, v) -> compareTimestamps(event, v) <= 0)
            .searchableQueryFilterExtractor(QueryFilter.Field.END_DATE, QueryFilter.Op.LESS_THAN,
                (event, v) -> compareTimestamps(event, v) < 0)
            .searchableQueryFilterExtractor(QueryFilter.Field.END_DATE, QueryFilter.Op.EQUALS,
                (event, v) -> compareTimestamps(event, v) == 0)
            .searchableQueryFilterExtractor(QueryFilter.Field.END_DATE, QueryFilter.Op.NOT_EQUALS,
                (event, v) -> compareTimestamps(event, v) != 0)

            .searchableQueryFilterExtractor(QueryFilter.Field.FLOW_ID, FollowLogEvent::flowId,
                QueryFilter.Op.EQUALS, QueryFilter.Op.NOT_EQUALS, QueryFilter.Op.CONTAINS,
                QueryFilter.Op.STARTS_WITH, QueryFilter.Op.ENDS_WITH, QueryFilter.Op.REGEX,
                QueryFilter.Op.IN, QueryFilter.Op.NOT_IN, QueryFilter.Op.PREFIX)

            .searchableQueryFilterExtractor(QueryFilter.Field.TRIGGER_ID, FollowLogEvent::triggerId,
                QueryFilter.Op.EQUALS, QueryFilter.Op.NOT_EQUALS, QueryFilter.Op.CONTAINS,
                QueryFilter.Op.STARTS_WITH, QueryFilter.Op.ENDS_WITH, QueryFilter.Op.IN, QueryFilter.Op.NOT_IN)

            .searchableQueryFilterExtractor(QueryFilter.Field.LEVEL, QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO,
                (event, v) -> event.level() != null && event.level().toInt() >= toLevel(v).toInt())
            .searchableQueryFilterExtractor(QueryFilter.Field.LEVEL, QueryFilter.Op.LESS_THAN_OR_EQUAL_TO,
                (event, v) -> event.level() != null && event.level().toInt() <= toLevel(v).toInt())

            .searchableQueryFilterExtractor(QueryFilter.Field.EXECUTION_ID, FollowLogEvent::executionId,
                QueryFilter.Op.EQUALS, QueryFilter.Op.NOT_EQUALS, QueryFilter.Op.CONTAINS,
                QueryFilter.Op.STARTS_WITH, QueryFilter.Op.ENDS_WITH, QueryFilter.Op.IN, QueryFilter.Op.NOT_IN)

            .searchableQueryFilterExtractor(QueryFilter.Field.TASK_ID, FollowLogEvent::taskId,
                QueryFilter.Op.EQUALS, QueryFilter.Op.NOT_EQUALS, QueryFilter.Op.CONTAINS,
                QueryFilter.Op.STARTS_WITH, QueryFilter.Op.ENDS_WITH, QueryFilter.Op.IN, QueryFilter.Op.NOT_IN)

            .searchableQueryFilterExtractor(QueryFilter.Field.TASK_RUN_ID, FollowLogEvent::taskRunId,
                QueryFilter.Op.EQUALS, QueryFilter.Op.NOT_EQUALS, QueryFilter.Op.CONTAINS,
                QueryFilter.Op.STARTS_WITH, QueryFilter.Op.ENDS_WITH, QueryFilter.Op.IN, QueryFilter.Op.NOT_IN)

            .searchableQueryFilterExtractor(QueryFilter.Field.ATTEMPT_NUMBER, FollowLogEvent::attemptNumber,
                QueryFilter.Op.EQUALS, QueryFilter.Op.NOT_EQUALS, QueryFilter.Op.IN, QueryFilter.Op.NOT_IN)
            .build();
    }

    private static boolean scopeMatches(FollowLogEvent event, Object value, String systemNamespace) {
        FlowScope desired = value instanceof FlowScope fs ? fs : FlowScope.valueOf(value.toString());
        boolean isSystem = systemNamespace.equals(event.namespace());
        return (desired == FlowScope.SYSTEM) == isSystem;
    }

    private static int compareTimestamps(FollowLogEvent event, Object queryValue) {
        if (event.timestamp() == null) {
            return -1;
        }
        Instant target = queryValue instanceof Instant i ? i
            : queryValue instanceof ZonedDateTime zdt ? zdt.toInstant()
            : ZonedDateTime.parse(queryValue.toString()).toInstant();
        return event.timestamp().compareTo(target);
    }

    @Singleton
    public FollowLogEventMatcher followLogEventMatcher(
        @Named("LOG") Searchable<FollowLogEvent> searchable
    ) {
        return searchable::matches;
    }

    private static Level toLevel(Object value) {
        return value instanceof Level lvl ? lvl : Level.valueOf(value.toString());
    }
}
