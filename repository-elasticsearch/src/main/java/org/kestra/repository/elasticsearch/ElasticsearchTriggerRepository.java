package org.kestra.repository.elasticsearch;

import org.elasticsearch.client.RestHighLevelClient;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.models.triggers.TriggerContext;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.repositories.TriggerRepositoryInterface;
import org.kestra.core.utils.ThreadMainFactoryBuilder;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticsearchTriggerRepository extends AbstractElasticSearchRepository<Trigger> implements TriggerRepositoryInterface {
    private static final String INDEX_NAME = "triggers";

    @Inject
    public ElasticsearchTriggerRepository(
        RestHighLevelClient client,
        List<IndicesConfig> indicesConfigs,
        ModelValidator modelValidator,
        ThreadMainFactoryBuilder threadFactoryBuilder
    ) {
        super(client, indicesConfigs, modelValidator, threadFactoryBuilder, Trigger.class);
    }

    public Optional<Trigger> findLast(TriggerContext trigger) {
        return this.rawGetRequest(INDEX_NAME, trigger.uid());
    }

    @Override
    public Trigger save(Trigger trigger) {
        this.putRequest(INDEX_NAME, trigger.uid(), trigger);

        return trigger;
    }
}
