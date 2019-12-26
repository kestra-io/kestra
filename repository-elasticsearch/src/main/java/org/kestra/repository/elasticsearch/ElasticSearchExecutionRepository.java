package org.kestra.repository.elasticsearch;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchExecutionRepository extends AbstractElasticSearchRepository<Execution> implements ExecutionRepositoryInterface {
    @Inject
    public ElasticSearchExecutionRepository(RestHighLevelClient client, List<IndicesConfig> indicesConfigs) {
        super(client, indicesConfigs, Execution.class);
    }

    @Override
    public Optional<Execution> findById(String id) {
        return this.getRequest(id);
    }

    @Override
    public ArrayListTotal<Execution> find(String query, Pageable pageable) {
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
    public ArrayListTotal<Execution> findByFlowId(String namespace, String id, Pageable pageable) {
        BoolQueryBuilder bool = QueryBuilders.boolQuery()
            .must(QueryBuilders.termQuery("namespace", namespace))
            .must(QueryBuilders.termQuery("flowId", id));


        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .size(pageable.getSize())
            .from(Math.toIntExact(pageable.getOffset() - pageable.getSize()));

        for (Sort.Order order:  pageable.getSort().getOrderBy()) {
            sourceBuilder = sourceBuilder.sort(
                order.getProperty(),
                order.getDirection() == Sort.Order.Direction.ASC ? SortOrder.ASC : SortOrder.DESC
            );
        }

        return this.query(sourceBuilder);
    }

    @Override
    public Execution save(Execution execution) {
        this.putRequest(execution.getId(), execution);

        return execution;
    }
}
