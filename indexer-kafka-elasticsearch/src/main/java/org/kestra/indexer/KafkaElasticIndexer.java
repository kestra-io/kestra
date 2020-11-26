package org.kestra.indexer;

import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Replaces;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.models.triggers.Trigger;
import org.kestra.core.runners.Indexer;
import org.kestra.core.runners.IndexerInterface;
import org.kestra.core.utils.DurationOrSizeTrigger;
import org.kestra.repository.elasticsearch.ElasticSearchExecutionRepository;
import org.kestra.repository.elasticsearch.ElasticSearchLogRepository;
import org.kestra.repository.elasticsearch.ElasticSearchRepositoryEnabled;
import org.kestra.repository.elasticsearch.ElasticsearchTriggerRepository;
import org.kestra.repository.elasticsearch.configs.IndicesConfig;
import org.kestra.runner.kafka.KafkaQueueEnabled;
import org.kestra.runner.kafka.configs.TopicsConfig;
import org.kestra.runner.kafka.services.KafkaConsumerService;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.inject.Inject;

@Prototype
@Replaces(org.kestra.core.runners.Indexer.class)
@ElasticSearchRepositoryEnabled
@KafkaQueueEnabled
@Slf4j
public class KafkaElasticIndexer implements IndexerInterface, Cloneable {
    private final MetricRegistry metricRegistry;
    private final RestHighLevelClient elasticClient;
    private final KafkaConsumerService kafkaConsumerService;

    private final Map<String, String> mapping;
    private final Set<String> subscriptions;

    private final AtomicBoolean running = new AtomicBoolean(true);;
    private final DurationOrSizeTrigger<ConsumerRecord<String, String>> trigger;

    @Inject
    public KafkaElasticIndexer(
        MetricRegistry metricRegistry,
        RestHighLevelClient elasticClient,
        IndexerConfig indexerConfig,
        List<TopicsConfig> topicsConfig,
        List<IndicesConfig> indicesConfigs,
        KafkaConsumerService kafkaConsumerService,
        ElasticSearchExecutionRepository executionRepository,
        ElasticSearchLogRepository logRepository,
        ElasticsearchTriggerRepository triggerRepository
    ) {
        this.metricRegistry = metricRegistry;
        this.elasticClient = elasticClient;
        this.kafkaConsumerService = kafkaConsumerService;

        this.subscriptions = topicsConfig
            .stream()
            .filter(t -> t.getCls() == Execution.class || t.getCls() == LogEntry.class || t.getCls() == Trigger.class)
            .map(TopicsConfig::getName)
            .collect(Collectors.toSet());

        this.mapping = mapTopicToIndices(topicsConfig, indicesConfigs);

        trigger = new DurationOrSizeTrigger<>(
            indexerConfig.getBatchDuration(),
            indexerConfig.getBatchSize()
        );

        logRepository.initMapping();
        executionRepository.initMapping();
        triggerRepository.initMapping();
    }

    @Override
    public void run() {
        org.apache.kafka.clients.consumer.Consumer<String, String> kafkaConsumer = kafkaConsumerService.of(Indexer.class, Serdes.String());
        kafkaConsumer.subscribe(this.subscriptions);

        List<ConsumerRecord<String, String>> rows = new ArrayList<>();

        while (running.get()) {
            List<ConsumerRecord<String, String>> records = StreamSupport
                .stream(kafkaConsumer.poll(Duration.ofSeconds(1)).spliterator(), false)
                .collect(Collectors.toList());

            records
                .stream()
                .collect(Collectors.groupingBy(ConsumerRecord::topic))
                .forEach((topic, consumerRecords) -> {
                    this.metricRegistry
                        .counter(MetricRegistry.METRIC_INDEXER_MESSAGE_IN_COUNT, "topic", topic)
                        .increment(consumerRecords.size());
                });

            for (ConsumerRecord<String, String> record : records) {
                rows.add(record);
                this.send(rows, kafkaConsumer);
            }

            this.send(rows, kafkaConsumer);
        }
    }

    private void send(List<ConsumerRecord<String, String>> rows, org.apache.kafka.clients.consumer.Consumer<String, String> consumer) {
        if (trigger.test(rows)) {
            BulkRequest request = bulkRequest(rows);

            try {
                this.metricRegistry
                    .counter(MetricRegistry.METRIC_INDEXER_REQUEST_COUNT)
                    .increment();

                Timer timer = this.metricRegistry.timer(MetricRegistry.METRIC_INDEXER_REQUEST_DURATION);

                timer.record(() -> Failsafe
                    .with(retryPolicy())
                    .run(() -> {
                        this.insert(request);
                    }));

                consumer.commitSync(KafkaConsumerService.maxOffsets(rows));
                rows.clear();
            } catch (RuntimeException exception) {
                consumer.close();
                throw new RuntimeException(exception);
            }
        }
    }

    private BulkRequest bulkRequest(List<ConsumerRecord<String, String>> rows) {
        BulkRequest request = new BulkRequest();

        rows
            .forEach(record -> request
                .add(new IndexRequest(this.indexName(record))
                .id(record.key())
                .source(record.value(), XContentType.JSON)
            ));

        return request;
    }

    private void insert(BulkRequest bulkRequest) throws IOException {
        BulkResponse bulkResponse = this.elasticClient.bulk(bulkRequest, RequestOptions.DEFAULT);

        Map<String, List<BulkItemResponse>> grouped = StreamSupport.stream(bulkResponse.spliterator(), false)
            .collect(Collectors.groupingBy(BulkItemResponse::getIndex));

        grouped.
            forEach((index, bulkItemResponses) -> this.metricRegistry
                .counter(MetricRegistry.METRIC_INDEXER_MESSAGE_OUT_COUNT, "index", index)
                .increment(bulkItemResponses.size()));

        if (bulkResponse.hasFailures()) {
            grouped.
                forEach((index, bulkItemResponses) -> {
                    long count = bulkItemResponses.stream()
                        .filter(BulkItemResponse::isFailed)
                        .count();

                    if (count > 0) {
                        this.metricRegistry
                            .counter(MetricRegistry.METRIC_INDEXER_MESSAGE_FAILED_COUNT, "index", index)
                            .increment(bulkItemResponses.size());
                    }
                });

            throw new IOException("Indexer failed bulk '" + bulkResponse.buildFailureMessage() + "'");
        }

        this.metricRegistry.timer(MetricRegistry.METRIC_INDEXER_SERVER_DURATION)
            .record(bulkResponse.getTook().getNanos(), TimeUnit.NANOSECONDS);

        if (log.isDebugEnabled()) {
            log.debug("Indexer request with {} elements in {}", bulkRequest.numberOfActions(), bulkResponse.getTook().toString());
        }
    }

    private RetryPolicy<Object> retryPolicy() {
        return new RetryPolicy<>()
            .onFailedAttempt(event -> {
                this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_REQUEST_RETRY_COUNT)
                    .increment();

                log.warn(
                    "Indexer failed in " + event.getStartTime().toString() + " (" + event.getElapsedTime().toSeconds() + "s elapsed / " + event.getAttemptCount() + " retries)",
                    event.getLastFailure()
                );;
            })
            .withBackoff(2, 300, ChronoUnit.SECONDS);
    }

    private Map<String, String> mapTopicToIndices(List<TopicsConfig> topicsConfig, List<IndicesConfig> indicesConfigs) {
        return topicsConfig
            .stream()
            .filter(topic -> indicesConfigs
                .stream()
                .anyMatch(indicesConfig -> indicesConfig.getCls() == topic.getCls())
            )
            .map(topic -> new AbstractMap.SimpleEntry<>(
                topic.getName(),
                indicesConfigs
                    .stream()
                    .filter(indicesConfig -> indicesConfig.getCls() == topic.getCls())
                    .findFirst()
                    .orElseThrow()
                    .getIndex()
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private String indexName(ConsumerRecord<?, ?> record) {
        return this.mapping.get(record.topic());
    }
}
