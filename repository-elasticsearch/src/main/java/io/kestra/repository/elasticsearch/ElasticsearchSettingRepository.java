package io.kestra.repository.elasticsearch;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.Setting;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.SettingRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.utils.ExecutorsUtils;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.List;
import java.util.Optional;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticsearchSettingRepository extends AbstractElasticSearchRepository<Setting> implements SettingRepositoryInterface {
    private static final String INDEX_NAME = "settings";

    private final ApplicationEventPublisher<CrudEvent<Setting>> eventPublisher;

    @Inject
    public ElasticsearchSettingRepository(
        RestHighLevelClient client,
        ElasticSearchIndicesService elasticSearchIndicesService,
        ModelValidator modelValidator,
        ExecutorsUtils executorsUtils,
        ApplicationEventPublisher<CrudEvent<Setting>> eventPublisher
    ) {
        super(client, elasticSearchIndicesService, modelValidator, executorsUtils, Setting.class);

        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<Setting> findByKey(String key) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.termQuery("key", key))
            .size(1);

        List<Setting> query = this.query(INDEX_NAME, sourceBuilder);

        return query.size() > 0 ? Optional.of(query.get(0)) : Optional.empty();
    }

    @Override
    public List<Setting> findAll() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(QueryBuilders.matchAllQuery());

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    @Override
    @VisibleForTesting
    public Setting save(Setting setting) {
        this.putRequest(INDEX_NAME, setting.getKey(), setting);

        eventPublisher.publishEvent(new CrudEvent<>(setting, CrudEventType.UPDATE));

        return setting;
    }


    @Override
    public Setting delete(Setting setting) {
        this.rawDeleteRequest(INDEX_NAME, setting.getKey());

        eventPublisher.publishEvent(new CrudEvent<>(setting, CrudEventType.DELETE));

        return setting;
    }

}
