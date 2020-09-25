package org.kestra.repository.elasticsearch;

import io.micronaut.data.model.Pageable;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.kestra.core.models.templates.Template;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.TemplateRepositoryInterface;
import org.kestra.core.utils.ThreadMainFactoryBuilder;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchTemplateRepository extends AbstractElasticSearchRepository<Template> implements TemplateRepositoryInterface {
    private static final String INDEX_NAME = "templates";
    private final QueueInterface<Template> templateQueue;

    @Inject
    public ElasticSearchTemplateRepository(
        RestHighLevelClient client,
        List<IndicesConfig> indicesConfigs,
        ModelValidator modelValidator,
        ThreadMainFactoryBuilder threadFactoryBuilder,
        @Named(QueueFactoryInterface.TEMPLATE_NAMED) QueueInterface<Template> templateQueue
    ) {
        super(client, indicesConfigs, modelValidator, threadFactoryBuilder, Template.class);

        this.templateQueue = templateQueue;
    }

    private static String templateId(Template template) {
        return template.getId();
    }

    @Override
    public Optional<Template> findById(String namespace, String id) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.termQuery("namespace", namespace))
            .must(QueryBuilders.termQuery("id", id));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool)
            .size(1);

        List<Template> query = this.query(INDEX_NAME, sourceBuilder);

        return query.size() > 0 ? Optional.of(query.get(0)) : Optional.empty();
    }

    @Override
    public List<Template> findAll() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(this.defaultFilter());

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    @Override
    public ArrayListTotal<Template> find(String query, Pageable pageable) {
        return super.findQueryString(INDEX_NAME, query, pageable);
    }

    @Override
    public List<Template> findByNamespace(String namespace) {
        BoolQueryBuilder bool = this.defaultFilter()
            .must(QueryBuilders.termQuery("namespace", namespace));

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(bool);

        return this.scroll(INDEX_NAME, sourceBuilder);
    }

    public Template create(Template template) throws ConstraintViolationException {
        return this.save(template);
    }

    public Template update(Template template, Template previous) throws ConstraintViolationException {
        this
            .findById(previous.getNamespace(), previous.getId())
            .map(current -> current.validateUpdate(template))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .ifPresent(s -> {
                throw s;
            });

        return this.save(template);
    }

    public Template save(Template template) {
        this.putRequest(INDEX_NAME, template.getId(), template);

        templateQueue.emit(template);

        return template;
    }

    @Override
    public void delete(Template template) {
        this.deleteRequest(INDEX_NAME, templateId(template));
    }

    public List<String> findDistinctNamespace() {
        return findDistinctNamespace(INDEX_NAME);
    }
}
