package org.kestra.repository.elasticsearch;

import io.micronaut.data.model.Pageable;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.utils.ThreadMainFactoryBuilder;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchFlowRepository extends AbstractElasticSearchRepository<Flow> implements FlowRepositoryInterface {
    private static final String INDEX_NAME = "flows";
    private static final String REVISIONS_NAME = "flows-revisions";

    @Inject
    public ElasticSearchFlowRepository(
        RestHighLevelClient client,
        List<IndicesConfig> indicesConfigs,
        ModelValidator modelValidator,
        ThreadMainFactoryBuilder threadFactoryBuilder
    ) {
        super(client, indicesConfigs, modelValidator, threadFactoryBuilder, Flow.class);
    }

    private static String flowId(Flow flow) {
        return String.join("_", Arrays.asList(
            flow.getNamespace(),
            flow.getId()
        ));
    }

    private static String flowUid(Flow flow) {
        return String.join("_", Arrays.asList(
            flow.getNamespace(),
            flow.getId(),
            flow.getRevision() != null ? String.valueOf(flow.getRevision()) : "-1"
        ));
    }

    @Override
    public Optional<Flow> findById(String namespace, String id, Optional<Integer> revision) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.termQuery("namespace", namespace))
            .must(QueryBuilders.termQuery("id", id));

        revision
            .ifPresent(v -> bool.must(QueryBuilders.termQuery("revision", v)));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .sort(new FieldSortBuilder("revision").order(SortOrder.DESC))
            .size(1);

        List<Flow> query = this.query(revision.isPresent() ? REVISIONS_NAME : INDEX_NAME, sourceBuilder);

        return query.size() > 0 ? Optional.of(query.get(0)) : Optional.empty();
    }

    @Override
    public List<Flow> findRevisions(String namespace, String id) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.termQuery("namespace", namespace))
            .must(QueryBuilders.termQuery("id", id));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .sort(new FieldSortBuilder("revision").order(SortOrder.ASC));

        return this.scroll(REVISIONS_NAME, sourceBuilder);
    }

    @Override
    public List<Flow> findAll() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(this.defaultFilter());

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    @Override
    public ArrayListTotal<Flow> find(String query, Pageable pageable) {
        return super.findQueryString(INDEX_NAME, query, pageable);
    }

    public Flow create(Flow flow) throws ConstraintViolationException {
        return this.save(flow);
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

        return this.save(flow);
    }

    public Flow save(Flow flow) throws ConstraintViolationException {
        modelValidator
            .isValid(flow)
            .ifPresent(s -> {
                throw s;
            });

        Optional<Flow> exists = this.findById(flow.getNamespace(), flow.getId());
        if (exists.isPresent() && exists.get().equalsWithoutRevision(flow)) {
            return exists.get();
        }

        Optional<Flow> current = this.findById(flow.getNamespace(), flow.getId());

        if (current.isPresent()) {
            flow = flow.withRevision(current.get().getRevision() + 1);
        } else if (flow.getRevision() == null) {
            flow = flow.withRevision(1);
        }

        this.putRequest(INDEX_NAME, flowId(flow), flow);
        this.putRequest(REVISIONS_NAME, flowUid(flow), flow);

        return flow;
    }

    @Override
    public void delete(Flow flow) {
        this.deleteRequest(INDEX_NAME, flowId(flow));
    }

    @Override
    public List<String> findDistinctNamespace(Optional<String> prefix) {
        BoolQueryBuilder query = this.defaultFilter()
            .must(QueryBuilders.prefixQuery("namespace", prefix.orElse("")));

        // We want to keep only "distinct" values of field "namespace"
        // @TODO: use includeExclude(new IncludeExclude(0, 10)) to partition results
        TermsAggregationBuilder termsAggregationBuilder = AggregationBuilders
            .terms("distinct_namespace")
            .field("namespace")
            .size(10000)
            .order(BucketOrder.key(true));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(query)
            .aggregation(termsAggregationBuilder);

        SearchRequest searchRequest = searchRequest(INDEX_NAME, sourceBuilder, false);

        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            Terms namespaces = searchResponse.getAggregations().get("distinct_namespace");

            return new ArrayListTotal<>(
                namespaces.getBuckets()
                    .stream()
                    .map(o -> {
                        return o.getKey().toString();
                    })
                    .collect(Collectors.toList()),
                namespaces.getBuckets().size()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
