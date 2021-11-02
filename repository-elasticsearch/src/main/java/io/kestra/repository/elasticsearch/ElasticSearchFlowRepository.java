package io.kestra.repository.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.SearchResult;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.ExecutorsUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchFlowRepository extends AbstractElasticSearchRepository<Flow> implements FlowRepositoryInterface {
    private static final String INDEX_NAME = "flows";
    protected static final String REVISIONS_NAME = "flows-revisions";
    protected static final ObjectMapper JSON_MAPPER = JacksonMapper.ofJson();
    protected static final ObjectMapper YAML_MAPPER = JacksonMapper.ofYaml();

    private final QueueInterface<Flow> flowQueue;
    private final QueueInterface<Trigger> triggerQueue;
    private final ApplicationEventPublisher eventPublisher;

    @Inject
    public ElasticSearchFlowRepository(
        RestHighLevelClient client,
        ElasticSearchIndicesService elasticSearchIndicesService,
        ModelValidator modelValidator,
        ExecutorsUtils executorsUtils,
        @Named(QueueFactoryInterface.FLOW_NAMED) QueueInterface<Flow> flowQueue,
        @Named(QueueFactoryInterface.TRIGGER_NAMED) QueueInterface<Trigger> triggerQueue,
        ApplicationEventPublisher eventPublisher
    ) {
        super(client, elasticSearchIndicesService, modelValidator, executorsUtils, Flow.class);

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
    public Optional<Flow> findById(String namespace, String id, Optional<Integer> revision) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.termQuery("namespace", namespace))
            .must(QueryBuilders.termQuery("id", id));

        revision
            .ifPresent(v -> {
                this.removeDeleted(bool);
                bool.must(QueryBuilders.termQuery("revision", v));
            });

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .fetchSource("*", "sourceCode")
            .sort(new FieldSortBuilder("revision").order(SortOrder.DESC))
            .size(1);

        List<Flow> query = this.query(revision.isPresent() ? REVISIONS_NAME : INDEX_NAME, sourceBuilder);

        return query.size() > 0 ? Optional.of(query.get(0)) : Optional.empty();
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
    public List<Flow> findRevisions(String namespace, String id) {
        BoolQueryBuilder defaultFilter = this.defaultFilter();

        BoolQueryBuilder bool = defaultFilter
            .must(QueryBuilders.termQuery("namespace", namespace))
            .must(QueryBuilders.termQuery("id", id));

        this.removeDeleted(defaultFilter);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .fetchSource("*", "sourceCode")
            .sort(new FieldSortBuilder("revision").order(SortOrder.ASC));

        return this.scroll(REVISIONS_NAME, sourceBuilder);
    }

    @Override
    public List<Flow> findAll() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .fetchSource("*", "sourceCode")
            .query(this.defaultFilter());

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    @Override
    public List<Flow> findAllWithRevisions() {
        BoolQueryBuilder defaultFilter = this.defaultFilter();
        this.removeDeleted(defaultFilter);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .fetchSource("*", "sourceCode")
            .query(defaultFilter)
            .sort(new FieldSortBuilder("id").order(SortOrder.ASC))
            .sort(new FieldSortBuilder("revision").order(SortOrder.ASC));

        return this.scroll(REVISIONS_NAME, sourceBuilder);
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
    public ArrayListTotal<Flow> find(String query, Pageable pageable) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.queryStringQuery(query)
                .field("namespace")
                .field("id", 2)
            );

        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable);
        sourceBuilder.fetchSource("*", "sourceCode");

        return this.query(INDEX_NAME, sourceBuilder);
    }

    @Override
    public ArrayListTotal<SearchResult<Flow>> findSourceCode(String query, Pageable pageable) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.queryStringQuery(query).field("sourceCode"));

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
                                mapper.readValue(documentFields.getSourceAsString(), this.cls),
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

    public Flow create(Flow flow) throws ConstraintViolationException {
        // control if create is valid
        flow.validate()
            .ifPresent(s -> {
                throw s;
            });

        return this.save(flow, CrudEventType.CREATE);
    }

    public Flow update(Flow flow, Flow previous) throws ConstraintViolationException {
        // control if update is valid
        this
            .findById(previous.getNamespace(), previous.getId())
            .map(current -> current.validateUpdate(flow))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .ifPresent(s -> {
                throw s;
            });

        Flow saved = this.save(flow, CrudEventType.UPDATE);

        FlowService
            .findRemovedTrigger(flow, previous)
            .forEach(abstractTrigger -> triggerQueue.delete(Trigger.of(flow, abstractTrigger)));

        return saved;
    }

    public Flow save(Flow flow, CrudEventType crudEventType) throws ConstraintViolationException {
        modelValidator
            .isValid(flow)
            .ifPresent(s -> {
                throw s;
            });

        Optional<Flow> exists = this.findById(flow.getNamespace(), flow.getId());
        if (exists.isPresent() && exists.get().equalsWithoutRevision(flow)) {
            return exists.get();
        }

        List<Flow> revisions = this.findRevisions(flow.getNamespace(), flow.getId());

        if (revisions.size() > 0) {
            flow = flow.withRevision(revisions.get(revisions.size() - 1).getRevision() + 1);
        } else {
            flow = flow.withRevision(1);
        }

        String json;
        try {
            Map<String, Object> flowMap = JacksonMapper.toMap(flow);
            flowMap.put("sourceCode", YAML_MAPPER.writeValueAsString(flow));
            json = JSON_MAPPER.writeValueAsString(flowMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        this.putRequest(INDEX_NAME, flowId(flow), json);
        this.putRequest(REVISIONS_NAME, flow.uid(), json);

        flowQueue.emit(flow);

        eventPublisher.publishEvent(new CrudEvent<>(flow, crudEventType));

        return flow;
    }

    @Override
    public Flow delete(Flow flow) {
        Flow deleted = flow.toDeleted();

        flowQueue.emit(deleted);
        this.deleteRequest(INDEX_NAME, flowId(deleted));
        this.putRequest(REVISIONS_NAME, deleted.uid(), deleted);

        ListUtils.emptyOnNull(flow.getTriggers())
            .forEach(abstractTrigger -> triggerQueue.delete(Trigger.of(flow, abstractTrigger)));

        eventPublisher.publishEvent(new CrudEvent<>(flow, CrudEventType.DELETE));

        return deleted;
    }

    public List<String> findDistinctNamespace() {
        return findDistinctNamespace(INDEX_NAME);
    }
}
