package io.kestra.webserver.converters;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Logical;
import io.kestra.webserver.configuration.QueryFilterConfiguration;
import io.kestra.webserver.utils.RequestUtils;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteAttributes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class QueryFilterFormatBinder implements AnnotatedRequestArgumentBinder<QueryFilterFormat, List<QueryFilter>> {

    private static final Pattern FILTER_PATTERN = Pattern.compile(
        "filters((?:\\[(?i:and|or)]\\[\\d+])*)\\[([^\\]]*)]\\[([^\\]]*)](?:\\[(.+)])?"
    );

    private static final Pattern PREFIX_SEG = Pattern.compile(
        "\\[(?i:(and|or))]\\[(\\d+)]"
    );

    /**
     * The pre-2.0 flat filter parameter names ({@code state}, {@code namespace}, {@code q}, ...), derived from the
     * filter fields themselves so the set cannot drift as fields are added or removed. A request carrying one of
     * these is rejected rather than silently widened - see {@link #rejectLegacyParams(HttpRequest)}.
     */
    private static final Set<String> LEGACY_FILTER_PARAMS = Arrays.stream(QueryFilter.Field.values())
        .map(QueryFilter.Field::value)
        .collect(Collectors.toUnmodifiableSet());

    private final QueryFilterConfiguration configuration;

    @Inject
    public QueryFilterFormatBinder(QueryFilterConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }

    /** Test-only convenience using permissive caps for parser-correctness tests. */
    @VisibleForTesting
    static List<QueryFilter> getQueryFilters(Map<String, List<String>> queryParams) {
        return getQueryFilters(queryParams, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @VisibleForTesting
    static List<QueryFilter> getQueryFilters(Map<String, List<String>> queryParams, int maxDepth, int maxWidth) {
        NodeBuilder root = new NodeBuilder();
        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("filters[")) {
                continue;
            }

            Matcher m = FILTER_PATTERN.matcher(key);
            if (!m.matches()) {
                continue;
            }

            String prefixChain = m.group(1);
            String fieldStr = m.group(2);
            String operationStr = m.group(3);
            String nestedKey = m.group(4);

            NodeBuilder target = descend(root, prefixChain, maxDepth);
            target.addLeaf(fieldStr, operationStr, nestedKey, entry.getValue());
        }
        List<QueryFilter> filters = root.build(maxWidth);
        if (filters.size() > maxWidth) {
            throw new IllegalArgumentException(
                "QueryFilter root width (" + filters.size() + ") exceeds maximum of " + maxWidth
            );
        }
        return filters;
    }

    private static NodeBuilder descend(NodeBuilder root, String prefixChain, int maxDepth) {
        if (prefixChain == null || prefixChain.isEmpty()) {
            return root;
        }
        NodeBuilder current = root;
        Matcher pm = PREFIX_SEG.matcher(prefixChain);
        int depth = 0;
        while (pm.find()) {
            if (++depth > maxDepth) {
                throw new IllegalArgumentException(
                    "QueryFilter nesting depth exceeds maximum of " + maxDepth
                );
            }
            Logical lg = Logical.valueOf(pm.group(1).toUpperCase(Locale.ROOT));
            int idx = Integer.parseInt(pm.group(2));
            current = current.descend(lg, idx);
        }
        return current;
    }

    @Override
    public Class<QueryFilterFormat> getAnnotationType() {
        return QueryFilterFormat.class;
    }

    @Override
    public BindingResult<List<QueryFilter>> bind(ArgumentConversionContext<List<QueryFilter>> context, HttpRequest<?> source) {
        QueryFilter.Resource resource = context.getAnnotationMetadata()
            .enumValue(QueryFilterFormat.class, QueryFilter.Resource.class)
            .orElseThrow(
                () -> new IllegalStateException(
                    "@QueryFilterFormat requires a QueryFilter.Resource value"
                )
            );

        if (configuration.rejectLegacyParams()) {
            rejectLegacyParams(source);
        }

        int maxDepth = configuration.maxDepthFor(resource);
        int maxWidth = configuration.maxWidthFor(resource);

        Map<String, List<String>> queryParams = source.getParameters().asMap();
        List<QueryFilter> filters = getQueryFilters(queryParams, maxDepth, maxWidth);

        return () -> Optional.of(filters);
    }

    /**
     * Rejects pre-2.0 flat filter parameters, which this binder does not read: only {@code filters[field][OP]=value}
     * is honoured, so a request scoped with {@code state=RUNNING} used to degrade to "match everything" - dangerous
     * on the by-query bulk endpoints, where it widens a delete or a kill to the whole tenant.
     * <p>
     * A parameter is only legacy when the matched route does not declare it as a real argument: names such as
     * {@code namespace} or {@code flowId} are legitimate query values on several endpoints, and those must keep
     * working.
     */
    private static void rejectLegacyParams(HttpRequest<?> source) {
        Optional<Set<String>> declared = declaredParamNames(source);
        if (declared.isEmpty()) {
            return;
        }

        List<String> legacy = legacyParams(source.getParameters().asMap().keySet(), declared.get());
        if (!legacy.isEmpty()) {
            throw new IllegalArgumentException(
                "Legacy filter parameter(s) " + legacy + " are no longer supported and would have been silently "
                    + "ignored. Use the bracket format instead, e.g. filters[" + legacy.getFirst() + "][EQUALS]=value."
            );
        }
    }

    @VisibleForTesting
    static List<String> legacyParams(Collection<String> paramNames, Set<String> declaredNames) {
        return paramNames.stream()
            .filter(name -> !name.startsWith("filters["))
            .filter(LEGACY_FILTER_PARAMS::contains)
            .filter(name -> !declaredNames.contains(name))
            .sorted()
            .toList();
    }

    /**
     * The query parameter names the matched route actually binds - argument names plus any {@code @QueryValue} alias.
     * Empty when the route arguments cannot be read, in which case the legacy check is skipped entirely: an unknown
     * route surface must not turn into a wave of false rejections.
     */
    private static Optional<Set<String>> declaredParamNames(HttpRequest<?> source) {
        return RouteAttributes.getRouteMatch(source)
            .filter(MethodBasedRouteMatch.class::isInstance)
            .map(routeMatch -> ((MethodBasedRouteMatch<?, ?>) routeMatch).getRequiredArguments().stream()
                .flatMap(argument -> Stream.concat(
                    Stream.of(argument.getName()),
                    argument.getAnnotationMetadata().stringValue(QueryValue.class).stream()
                ))
                .collect(Collectors.toSet()));
    }

    private static List<Object> parseValues(List<String> values, QueryFilter.Field field, QueryFilter.Op operation) {
        return values.stream().map(value -> switch (field) {
            case SCOPE -> RequestUtils.toFlowScopes(value);
            default -> (operation == QueryFilter.Op.IN || operation == QueryFilter.Op.NOT_IN)
                ? Arrays.asList(URLDecoder.decode(value, StandardCharsets.UTF_8).replaceAll("[\\[\\]]", "").split(","))
                : value;
        }).toList();
    }

    private static void checkWidth(int count, int maxWidth) {
        if (count > maxWidth) {
            throw new IllegalArgumentException(
                "QueryFilter node width (" + count + ") exceeds maximum of " + maxWidth
            );
        }
    }

    /**
     * Builds one logical position in the filter tree. Each URL param either lands a leaf here
     * (via {@link #addLeaf}) or descends into a sub-node identified by ({@link Logical}, index).
     * {@link #build(int)} flattens the position into the QueryFilter list that the parent should
     * receive — direct leaves first, then merged labels, then one wrapper per (logical, slots) group.
     */
    private static class NodeBuilder {
        private final List<QueryFilter> directLeaves = new ArrayList<>();
        private final Map<QueryFilter.Op, Map<String, String>> labelsByOp = new EnumMap<>(QueryFilter.Op.class);
        private final Map<Logical, Map<Integer, NodeBuilder>> subNodes = new EnumMap<>(Logical.class);

        NodeBuilder descend(Logical lg, int idx) {
            return subNodes
                .computeIfAbsent(lg, k -> new TreeMap<>())
                .computeIfAbsent(idx, k -> new NodeBuilder());
        }

        void addLeaf(String fieldStr, String operationStr, String nestedKey, List<String> values) {
            QueryFilter.Field field = QueryFilter.Field.fromString(fieldStr);
            QueryFilter.Op op = QueryFilter.Op.valueOf(operationStr);

            if (field == QueryFilter.Field.LABELS && nestedKey != null) {
                labelsByOp.computeIfAbsent(op, k -> new HashMap<>()).put(nestedKey, values.getFirst());
                return;
            }

            List<Object> parsedValues = nestedKey != null
                ? List.of(Map.of(nestedKey, values.getFirst()))
                : parseValues(values, field, op);

            for (Object v : parsedValues) {
                directLeaves.add(
                    QueryFilter.builder()
                        .field(field)
                        .operation(op)
                        .value(v)
                        .build()
                );
            }
        }

        List<QueryFilter> build(int maxWidth) {
            List<QueryFilter> items = new ArrayList<>(directLeaves);
            labelsByOp.forEach((op, kvMap) ->
            {
                if (!kvMap.isEmpty()) {
                    items.add(
                        QueryFilter.builder()
                            .field(QueryFilter.Field.LABELS)
                            .operation(op)
                            .value(kvMap)
                            .build()
                    );
                }
            });
            subNodes.forEach((lg, slots) ->
            {
                List<QueryFilter> branches = new ArrayList<>();
                for (NodeBuilder slot : slots.values()) {
                    List<QueryFilter> slotItems = slot.build(maxWidth);
                    if (slotItems.isEmpty()) {
                        continue;
                    }
                    if (slotItems.size() == 1) {
                        branches.add(slotItems.getFirst());
                    } else {
                        checkWidth(slotItems.size(), maxWidth);
                        branches.add(
                            QueryFilter.builder()
                                .logical(Logical.AND)
                                .children(slotItems)
                                .build()
                        );
                    }
                }
                if (branches.isEmpty()) {
                    return;
                }
                if (branches.size() == 1 && lg == Logical.AND) {
                    items.add(branches.getFirst());
                } else {
                    checkWidth(branches.size(), maxWidth);
                    items.add(
                        QueryFilter.builder()
                            .logical(lg)
                            .children(branches)
                            .build()
                    );
                }
            });
            return items;
        }
    }
}
