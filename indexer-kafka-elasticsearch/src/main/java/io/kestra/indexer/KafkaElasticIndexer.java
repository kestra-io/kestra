package io.kestra.indexer;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.Indexer;
import io.kestra.core.runners.IndexerInterface;
import io.kestra.core.utils.DurationOrSizeTrigger;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.repository.elasticsearch.ElasticSearchIndicesService;
import io.kestra.repository.elasticsearch.ElasticSearchRepositoryEnabled;
import io.kestra.repository.elasticsearch.configs.IndicesConfig;
import io.kestra.runner.kafka.KafkaQueueEnabled;
import io.kestra.runner.kafka.configs.TopicsConfig;
import io.kestra.runner.kafka.services.KafkaConsumerService;
import io.micrometer.core.instrument.Timer;
import io.micronaut.context.annotation.Replaces;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.Serdes;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@Replaces(Indexer.class)
@ElasticSearchRepositoryEnabled
@KafkaQueueEnabled
@Slf4j
public class KafkaElasticIndexer implements IndexerInterface, Cloneable {
    private final MetricRegistry metricRegistry;
    private final RestHighLevelClient elasticClient;
    private final KafkaConsumerService kafkaConsumerService;
    private final ExecutorService poolExecutor;

    private final Map<String, String> mapping;
    private final Set<String> subscriptions;

    private final AtomicBoolean running = new AtomicBoolean(true);;
    private final DurationOrSizeTrigger<ConsumerRecord<String, String>> trigger;

    private org.apache.kafka.clients.consumer.Consumer<String, String> kafkaConsumer;

    @Inject
    public KafkaElasticIndexer(
        MetricRegistry metricRegistry,
        RestHighLevelClient elasticClient,
        IndexerConfig indexerConfig,
        List<TopicsConfig> topicsConfig,
        List<IndicesConfig> indicesConfigs,
        ElasticSearchIndicesService elasticSearchIndicesService,
        KafkaConsumerService kafkaConsumerService,
        ExecutorsUtils executorsUtils
    ) {
        this.metricRegistry = metricRegistry;
        this.elasticClient = elasticClient;
        this.kafkaConsumerService = kafkaConsumerService;
        this.poolExecutor = executorsUtils.cachedThreadPool("kakfa-elastic-indexer");

        this.subscriptions = subscriptions(topicsConfig, indexerConfig);
        this.mapping = mapTopicToIndices(topicsConfig, indicesConfigs);

        this.trigger = new DurationOrSizeTrigger<>(
            indexerConfig.getBatchDuration(),
            indexerConfig.getBatchSize()
        );

        elasticSearchIndicesService.createIndice(null);
        elasticSearchIndicesService.updateMapping(null);
    }

    public void run() {
        poolExecutor.execute(() -> {
            kafkaConsumer = kafkaConsumerService.of(Indexer.class, Serdes.String(), Indexer.class);
            kafkaConsumer.subscribe(this.subscriptions);

            List<ConsumerRecord<String, String>> rows = new ArrayList<>();

            while (running.get()) {
                try {
                    List<ConsumerRecord<String, String>> records = StreamSupport
                        .stream(kafkaConsumer.poll(Duration.ofMillis(500)).spliterator(), false)
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
                } catch (WakeupException e) {
                    log.debug("Received Wakeup on {}", this.getClass().getName());
                    running.set(false);
                }
            }

            this.kafkaConsumer.close();
        });
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
            .forEach(record -> {
                if (record.value() == null) {
                    request
                        .add(new DeleteRequest(this.indexName(record))
                            .id(record.key())
                        );
                } else {
                    request
                        .add(new IndexRequest(this.indexName(record))
                            .id(record.key())
                            .source(record.value(), XContentType.JSON)
                        );
                }
            });

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

    protected Map<String, String> mapTopicToIndices(List<TopicsConfig> topicsConfig, List<IndicesConfig> indicesConfigs) {
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

    protected Set<String> subscriptions(List<TopicsConfig> topicsConfig, IndexerConfig indexerConfig) {
        return topicsConfig
            .stream()
            .filter(t -> indexerConfig.getModels().contains(t.getCls()))
            .map(TopicsConfig::getName)
            .collect(Collectors.toSet());
    }

    protected String indexName(ConsumerRecord<?, ?> record) {
        return this.mapping.get(record.topic());
    }

    @Override
    public void close() throws IOException {
        if (kafkaConsumer != null) {
            this.kafkaConsumer.wakeup();
        }
    }
}
