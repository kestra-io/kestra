package org.floworc.repository.elasticsearch;

import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.repository.elasticsearch.configs.IndicesConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

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
}
