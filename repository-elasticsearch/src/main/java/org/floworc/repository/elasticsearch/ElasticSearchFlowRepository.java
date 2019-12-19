package org.floworc.repository.elasticsearch;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.ArrayListTotal;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.repository.elasticsearch.configs.IndicesConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchFlowRepository extends AbstractElasticSearchRepository<Flow> implements FlowRepositoryInterface {
    @Inject
    public ElasticSearchFlowRepository(RestHighLevelClient client, List<IndicesConfig> indicesConfigs) {
        super(client, indicesConfigs, Flow.class);
    }

    @Override
    public Optional<Flow> findById(String namespace, String id, Optional<Integer> revision) {
        BoolQueryBuilder bool = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("namespace", namespace))
            .must(QueryBuilders.termQuery("id", id));

        revision
            .ifPresent(v -> bool.must(QueryBuilders.termQuery("revision", v)));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .sort(new FieldSortBuilder("revision").order(SortOrder.DESC))
            .size(1);

        List<Flow> query = this.query(sourceBuilder);

        return query.size() > 0 ? Optional.of(query.get(0)) : Optional.empty();
    }

    @Override
    public List<Flow> findRevisions(String namespace, String id) {
        BoolQueryBuilder bool = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("namespace", namespace))
            .must(QueryBuilders.termQuery("id", id));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .sort(new FieldSortBuilder("revision").order(SortOrder.ASC));

        return this.scroll(sourceBuilder);
    }

    @Override
    public List<Flow> findAll() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery());

        return this.scroll(sourceBuilder);
    }


    @Override
    public ArrayListTotal<Flow> find(String query, Pageable pageable) {
        QueryStringQueryBuilder queryString = QueryBuilders.queryStringQuery(query);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(queryString)
                .size(pageable.getSize())
                .from(Math.toIntExact(pageable.getOffset() - pageable.getSize()));

        for (Sort.Order order : pageable.getSort().getOrderBy()) {
            sourceBuilder = sourceBuilder.sort(
                    order.getProperty(),
                    order.getDirection() == Sort.Order.Direction.ASC ? SortOrder.ASC : SortOrder.DESC
            );
        }

        return this.query(sourceBuilder);
    }

    @Override
    public ArrayListTotal<Flow> findByNamespace(String namespace, Pageable pageable) {
        TermQueryBuilder termQuery = QueryBuilders
                .termQuery("namespace", namespace);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(termQuery)
                .size(pageable.getSize())
                .from(Math.toIntExact(pageable.getOffset() - pageable.getSize()));

        for (Sort.Order order : pageable.getSort().getOrderBy()) {
            sourceBuilder = sourceBuilder.sort(
                    order.getProperty(),
                    order.getDirection() == Sort.Order.Direction.ASC ? SortOrder.ASC : SortOrder.DESC
            );
        }

        return this.query(sourceBuilder);
    }

    @Override
    public Flow save(Flow flow) {
        Optional<Flow> exists = this.exists(flow);
        if (exists.isPresent()) {
            return exists.get();
        }

        Optional<Flow> current = this.findById(flow.getNamespace(), flow.getId());

        if (current.isPresent()) {
            flow = flow.withRevision(current.get().getRevision() + 1);
        } else if (flow.getRevision() == null) {
            flow = flow.withRevision(1);
        }

        this.putRequest(flow.uid(), flow);

        return flow;
    }

    @Override
    public void delete(Flow flow) {
        this.deleteRequest(flow.uid());
    }

    @Override
    public ArrayListTotal<String> findNamespaces(Optional<String> prefix) {

        PrefixQueryBuilder prefixQuery = QueryBuilders.prefixQuery("namespace", prefix.orElse(""));

        // We want to keep only "distinct" values of field "namespace"
        TermsAggregationBuilder termsAggregationBuilder =
                AggregationBuilders
                        .terms("distinct_namespace")
                        .field("namespace")
                        .order(BucketOrder.key(true));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .query(prefixQuery)
                .aggregation(termsAggregationBuilder);

        SearchRequest searchRequest = searchRequest(sourceBuilder, false);

        try {
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            Terms namespaces = searchResponse.getAggregations().get("distinct_namespace");

            return new ArrayListTotal<String>(
                    namespaces.getBuckets()
                        .stream()
                        .map(o -> {
                            return o.getKey().toString();
                        })
                        .collect(Collectors.toList()),
                    namespaces.getBuckets().size());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
