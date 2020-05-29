package org.kestra.runner.kafka;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import io.reactivex.Flowable;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.kestra.runner.kafka.services.KafkaAdminService;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.inject.Singleton;

@Singleton

@KafkaQueueEnabled
public class KafkaHealthIndicator implements HealthIndicator {
    private static final String ID = "kafka";
    private static final String REPLICATION_PROPERTY = "offsets.topic.replication.factor";
    private final AdminClient adminClient;

    /**
     * Constructs a new Kafka health indicator for the given arguments.
     *
     * @param adminService The admin service
     */
    public KafkaHealthIndicator(KafkaAdminService adminService) {
        this.adminClient = adminService.of();
    }

    @Override
    public Flowable<HealthResult> getResult() {
        DescribeClusterResult result = adminClient.describeCluster();

        Flowable<String> clusterId = Flowable.fromFuture(result.clusterId());
        Flowable<Collection<Node>> nodes = Flowable.fromFuture(result.nodes());
        Flowable<Node> controller = Flowable.fromFuture(result.controller());

        return controller.switchMap(node -> {
            String brokerId = node.idString();
            ConfigResource configResource = new ConfigResource(ConfigResource.Type.BROKER, brokerId);
            DescribeConfigsResult configResult = adminClient.describeConfigs(Collections.singletonList(configResource));
            Flowable<Map<ConfigResource, Config>> configs = Flowable.fromFuture(configResult.all());
            return configs.switchMap(resources -> {
                Config config = resources.get(configResource);
                ConfigEntry ce = config.get(REPLICATION_PROPERTY);
                int replicationFactor = Integer.parseInt(ce.value());
                return nodes.switchMap(nodeList -> clusterId.map(clusterIdString -> {
                    int nodeCount = nodeList.size();
                    HealthResult.Builder builder;
                    if (nodeCount >= replicationFactor) {
                        builder = HealthResult.builder(ID, HealthStatus.UP);
                    } else {
                        builder = HealthResult.builder(ID, HealthStatus.DOWN);
                    }
                    return builder
                        .details(CollectionUtils.mapOf(
                            "brokerId", brokerId,
                            "clusterId", clusterIdString,
                            "nodes", nodeCount
                        )).build();
                }));
            });
        }).onErrorReturn(throwable ->
            HealthResult.builder(ID, HealthStatus.DOWN)
                .exception(throwable).build()
        );
    }
}
