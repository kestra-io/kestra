package io.kestra.repository.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.ListUtils;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.text.Text;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortOrder;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.validation.ConstraintViolationException;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchFlowRepository extends AbstractElasticSearchRepository<Flow> implements FlowRepositoryInterface {
    private static final String INDEX_NAME = "flows";
    protected static final String REVISIONS_NAME = "flows-revisions";
    protected static final ObjectMapper JSON_MAPPER = JacksonMapper.ofJson();

    private final QueueInterface<Flow> flowQueue;
    private final QueueInterface<Trigger> triggerQueue;
    private final ApplicationEventPublisher<CrudEvent<Flow>> eventPublisher;
    @Inject
    private ModelValidator modelValidator;
    @Inject
    public ElasticSearchFlowRepository(
        RestHighLevelClient client,
        ElasticSearchIndicesService elasticSearchIndicesService,
        ExecutorsUtils executorsUtils,
        @Named(QueueFactoryInterface.FLOW_NAMED) QueueInterface<Flow> flowQueue,
        @Named(QueueFactoryInterface.TRIGGER_NAMED) QueueInterface<Trigger> triggerQueue,
        ApplicationEventPublisher<CrudEvent<Flow>> eventPublisher
    ) {
        super(client, elasticSearchIndicesService, executorsUtils, Flow.class);

        this.flowQueue = flowQueue;
        this.triggerQueue = triggerQueue;
        this.eventPublisher = eventPublisher;
    }

    private static String flowId(Flow flow) {
        return String.join("_", Arrays.asList(
            flow.getNamespace(),
            flow.getId()
        ));
    }

    @Override
    protected Flow deserialize(String source) {
        try {
            return super.deserialize(source);
        } catch (DeserializationException e) {
            try {
                JsonNode jsonNode = MAPPER.readTree(source);
                return FlowWithException.builder()
                    .id(jsonNode.get("id").asText())
                    .namespace(jsonNode.get("namespace").asText())
                    .revision(jsonNode.get("revision").asInt())
                    .source(JacksonMapper.ofJson().writeValueAsString(JacksonMapper.toMap(source)))
                    .exception(e.getMessage())
                    .tasks(List.of())
                    .build();
            } catch (JsonProcessingException ex) {
                throw new DeserializationException(ex);
            }
        }
    }

    private SearchSourceBuilder searchById(String namespace, String id, Optional<Integer> revision) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.termQuery("namespace", namespace))
            .must(QueryBuilders.termQuery("id", id));

        revision
            .ifPresent(v -> {
                this.removeDeleted(bool);
                bool.must(QueryBuilders.termQuery("revision", v));
            });

        return new SearchSourceBuilder()
            .query(bool)
            .sort(new FieldSortBuilder("revision").order(SortOrder.DESC))
            .size(1);
    }

    public Optional<Flow> findById(String namespace, String id, Optional<Integer> revision) {
        SearchSourceBuilder sourceBuilder = this.searchById(namespace, id, revision)
            .fetchSource("*", "sourceCode");

        List<Flow> query = this.query(revision.isPresent() ? REVISIONS_NAME : INDEX_NAME, sourceBuilder);

        return query.size() > 0 ? Optional.of(query.get(0)) : Optional.empty();
    }

    @Override
    public Optional<FlowWithSource> findByIdWithSource(String namespace, String id, Optional<Integer> revision) {
        SearchSourceBuilder sourceBuilder = this.searchById(namespace, id, revision);
        SearchRequest searchRequest = searchRequest(revision.isPresent() ? REVISIONS_NAME : INDEX_NAME, sourceBuilder, false);

        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            if (searchResponse.getHits().getHits().length == 0) {
                return Optional.empty();
            }

            return Optional.of(FlowWithSource.of(
                this.deserialize(searchResponse.getHits().getHits()[0].getSourceAsString()),
                (String) searchResponse.getHits().getHits()[0].getSourceAsMap().get("sourceCode")
            ));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void removeDeleted(BoolQueryBuilder bool) {
        QueryBuilder deleted = bool
            .must()
            .stream()
            .filter(queryBuilder -> queryBuilder instanceof MatchQueryBuilder && ((MatchQueryBuilder) queryBuilder).fieldName().equals("deleted"))
            .findFirst()
            .orElseThrow();

        bool.must().remove(deleted);
    }

    @Override
    public List<FlowWithSource> findRevisions(String namespace, String id) {
        BoolQueryBuilder defaultFilter = this.defaultFilter();

        BoolQueryBuilder bool = defaultFilter
            .must(QueryBuilders.termQuery("namespace", namespace))
            .must(QueryBuilders.termQuery("id", id));

        this.removeDeleted(defaultFilter);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .sort(new FieldSortBuilder("revision").order(SortOrder.ASC));

        List<FlowWithSource> result = new ArrayList<>();

        this.internalScroll(
            REVISIONS_NAME,
            sourceBuilder,
            documentFields -> result.add(
                FlowWithSource.of(
                    this.deserialize(documentFields.getSourceAsString()),
                    (String) documentFields.getSourceAsMap().get("sourceCode")
                )
            )
        );

        return result;
    }

    @Override
    public List<Flow> findAll() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .fetchSource("*", "sourceCode")
            .query(this.defaultFilter());

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    @Override
    public List<Flow> findByNamespace(String namespace) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.termQuery("namespace", namespace));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .fetchSource("*", "sourceCode")
            .query(bool);

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    @Override
    public ArrayListTotal<Flow> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable Map<String, String> labels
    ) {
        BoolQueryBuilder bool = this.defaultFilter();

        if (query != null) {
            bool.must(queryString(query).field("*.fulltext"));
        }

        if (namespace != null) {
            bool.must(QueryBuilders.prefixQuery("namespace", namespace));
        }

        if (labels != null) {
            labels.forEach((key, value) -> {
                bool.must(QueryBuilders.termQuery("labelsMap.key", key));

                if (value != null) {
                    bool.must(QueryBuilders.termQuery("labelsMap.value", value));
                }
            });
        }

        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable);
        sourceBuilder.fetchSource("*", "sourceCode");

        return this.query(INDEX_NAME, sourceBuilder);
    }

    @Override
    public ArrayListTotal<SearchResult<Flow>> findSourceCode(Pageable pageable, @Nullable String query, @Nullable String namespace) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(queryString(query).field("sourceCode"));

        if (namespace != null) {
            bool.must(QueryBuilders.prefixQuery("namespace", namespace));
        }

        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable);
        sourceBuilder.fetchSource("*", "sourceCode");
        sourceBuilder.highlighter(new HighlightBuilder()
            .preTags("[mark]")
            .postTags("[/mark]")
            .field("sourceCode")
        );

        SearchRequest searchRequest = searchRequest(INDEX_NAME, sourceBuilder, false);

        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            return new ArrayListTotal<>(
                Arrays.stream(searchResponse.getHits().getHits())
                    .map(documentFields -> {
                        try {
                            return new SearchResult<>(
                                MAPPER.readValue(documentFields.getSourceAsString(), this.cls),
                                documentFields.getHighlightFields().get("sourceCode") != null ?
                                    Arrays.stream(documentFields.getHighlightFields().get("sourceCode").getFragments())
                                        .map(Text::string)
                                        .collect(Collectors.toList()) :
                                    Collections.emptyList()
                            );
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList()),
                searchResponse.getHits().getTotalHits().value
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public FlowWithSource create(Flow flow, String flowSource, Flow flowWithDefaults) throws ConstraintViolationException {
        if (this.findById(flow.getNamespace(), flow.getId()).isPresent()) {
            throw new ConstraintViolationException(Collections.singleton(ManualConstraintViolation.of(
                "Flow namespace:'" + flow.getNamespace() + "', id:'" + flow.getId() + "'  already exists",
                flow,
                Flow.class,
                "flow.id",
                flow.getId()
            )));
        }

        // Check flow with defaults injected
        modelValidator.validate(flowWithDefaults);

        return this.save(flow, CrudEventType.CREATE, flowSource);
    }

    public FlowWithSource update(Flow flow, Flow previous, String flowSource, Flow flowWithDefaults) throws ConstraintViolationException {
        // Check flow with defaults injected
        modelValidator.validate(flowWithDefaults);

        // control if update is valid
        Optional<ConstraintViolationException> checkUpdate = previous.validateUpdate(flowWithDefaults);
        if(checkUpdate.isPresent()){
            throw checkUpdate.get();
        }

        FlowService
            .findRemovedTrigger(flow, previous)
            .forEach(abstractTrigger -> triggerQueue.delete(Trigger.of(flow, abstractTrigger)));

        return this.save(flow, CrudEventType.UPDATE, flowSource);
    }

    public FlowWithSource save(Flow flow, CrudEventType crudEventType, String flowSource) throws ConstraintViolationException {
        Optional<FlowWithSource> exists = this.findByIdWithSource(flow.getNamespace(), flow.getId());
        if (exists.isPresent() && exists.get().isUpdatable(flow, flowSource)) {
            return exists.get();
        }

        List<FlowWithSource> revisions = this.findRevisions(flow.getNamespace(), flow.getId());

        if (revisions.size() > 0) {
            flow = flow.withRevision(revisions.get(revisions.size() - 1).getRevision() + 1);
        } else {
            flow = flow.withRevision(1);
        }

        String json;
        try {
            Map<String, Object> flowMap = JacksonMapper.toMap(flow);
            flowMap.put("sourceCode", flowSource);
            if (flow.getLabels() != null) {
                flowMap.put("labelsMap", flow.getLabels()
                    .entrySet()
                    .stream()
                    .map(e -> Map.of("key", e.getKey(), "value", e.getValue()))
                    .collect(Collectors.toList())
                );
            }
            json = JSON_MAPPER.writeValueAsString(flowMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        this.putRequest(INDEX_NAME, flowId(flow), json);
        this.putRequest(REVISIONS_NAME, flow.uid(), json);

        flowQueue.emit(flow);

        eventPublisher.publishEvent(new CrudEvent<>(flow, crudEventType));

        return FlowWithSource.of(flow, flowSource);
    }

    @Override
    public Flow delete(Flow flow) {
        Flow deleted = flow.toDeleted();

        flowQueue.emit(deleted);
        this.deleteRequest(INDEX_NAME, flowId(deleted));
        this.putRequest(REVISIONS_NAME, deleted.uid(), deleted);

        ListUtils.emptyOnNull(flow.getTriggers())
            .forEach(abstractTrigger -> triggerQueue.delete(Trigger.of(flow, abstractTrigger)));

        eventPublisher.publishEvent(new CrudEvent<>(deleted, CrudEventType.DELETE));

        return deleted;
    }

    public List<String> findDistinctNamespace() {
        return findDistinctNamespace(INDEX_NAME);
    }
}
