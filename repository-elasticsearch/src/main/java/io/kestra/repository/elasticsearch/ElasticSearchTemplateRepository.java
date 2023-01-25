package io.kestra.repository.elasticsearch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.templates.TemplateSource;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ExecutorsUtils;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.ConstraintViolationException;

@Singleton
@ElasticSearchRepositoryEnabled
public class ElasticSearchTemplateRepository extends AbstractElasticSearchRepository<Template> implements TemplateRepositoryInterface {
    private static final String INDEX_NAME = "templates";
    private final QueueInterface<Template> templateQueue;
    private final ApplicationEventPublisher<CrudEvent<Template>> eventPublisher;

    @Inject
    public ElasticSearchTemplateRepository(
        RestHighLevelClient client,
        ElasticSearchIndicesService elasticSearchIndicesService,
        ModelValidator modelValidator,
        ExecutorsUtils executorsUtils,
        @Named(QueueFactoryInterface.TEMPLATE_NAMED) QueueInterface<Template> templateQueue,
        ApplicationEventPublisher<CrudEvent<Template>> eventPublisher
    ) {
        super(client, elasticSearchIndicesService, modelValidator, executorsUtils, Template.class);

        this.templateQueue = templateQueue;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected Template deserialize(String source) {
        try {
            return super.deserialize(source);
        } catch (DeserializationException e) {
            try {
                JsonNode jsonNode = MAPPER.readTree(source);
                return TemplateSource.builder()
                    .id(jsonNode.get("id").asText())
                    .namespace(jsonNode.get("namespace").asText())
                    .source(JacksonMapper.ofYaml().writeValueAsString(JacksonMapper.toMap(source)))
                    .exception(e.getMessage())
                    .tasks(List.of())
                    .build();
            } catch (JsonProcessingException ex) {
                throw new DeserializationException(ex);
            }
        }
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
    public ArrayListTotal<Template> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace
    ) {
        BoolQueryBuilder bool = this.defaultFilter();

        if (query != null) {
            bool.must(queryString(query).field("*.fulltext"));
        }

        if (namespace != null) {
            bool.must(QueryBuilders.prefixQuery("namespace", namespace));
        }


        SearchSourceBuilder sourceBuilder = this.searchSource(bool, Optional.empty(), pageable);

        return this.query(INDEX_NAME, sourceBuilder);
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
        return this.save(template, CrudEventType.CREATE);
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

        return this.save(template, CrudEventType.UPDATE);
    }

    public Template save(Template template, CrudEventType crudEventType) {
        this.putRequest(INDEX_NAME, template.uid(), template);

        templateQueue.emit(template);

        eventPublisher.publishEvent(new CrudEvent<>(template, crudEventType));

        return template;
    }

    @Override
    public void delete(Template template) {
        this.deleteRequest(INDEX_NAME, template.uid());

        eventPublisher.publishEvent(new CrudEvent<>(template, CrudEventType.DELETE));
    }

    public List<String> findDistinctNamespace() {
        return findDistinctNamespace(INDEX_NAME);
    }
}
