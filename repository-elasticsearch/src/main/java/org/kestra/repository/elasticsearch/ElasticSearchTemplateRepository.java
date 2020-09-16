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
import org.kestra.core.models.templates.Template;
import org.kestra.core.models.validations.ModelValidator;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.TemplateRepositoryInterface;
import org.kestra.core.utils.ThreadMainFactoryBuilder;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchTemplateRepository extends AbstractElasticSearchRepository<Template> implements TemplateRepositoryInterface {
    private static final String INDEX_NAME = "templates";
    protected static final String REVISIONS_NAME = "templates-revisions";

    private final QueueInterface<Template> templateQueue;

    @Inject
    public ElasticSearchTemplateRepository(
        RestHighLevelClient client,
        List<IndicesConfig> indicesConfigs,
        ModelValidator modelValidator,
        ThreadMainFactoryBuilder threadFactoryBuilder,
        @Named(QueueFactoryInterface.FLOW_NAMED) QueueInterface<Template> templateQueue
    ) {
        super(client, indicesConfigs, modelValidator, threadFactoryBuilder, Template.class);

        this.templateQueue = templateQueue;
    }

    private static String templateId(Template template) {
        return template.getId();
    }

    @Override
    public Optional<Template> findById(String id) {
        BoolQueryBuilder bool = this.defaultFilter()
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
    public ArrayListTotal<Template> find(Optional<String> query, Pageable pageable) {
        return super.findQueryString(INDEX_NAME, query.get(), pageable);
    }

    public Template create(Template template) throws ConstraintViolationException {
        return this.save(template);
    }

    public Template update(Template template, Template previous) throws ConstraintViolationException {
        // How to do better than assert, I don't get the Template model to be throwable ?
        assert this.findById(previous.getId()).isPresent();
        return this.save(template);
    }

    public Template save(Template template) throws ConstraintViolationException {
        modelValidator
            .isValid(template)
            .ifPresent(s -> {
                throw s;
            });

        Optional<Template> exists = this.findById(template.getId());
        if (exists.isPresent()) {
            return exists.get();
        }

        Optional<Template> current = this.findById(template.getId());

        this.putRequest(INDEX_NAME, templateId(template), template);

        return template;
    }

    @Override
    public void delete(Template template) {
        this.deleteRequest(INDEX_NAME, templateId(template));
    }

}
