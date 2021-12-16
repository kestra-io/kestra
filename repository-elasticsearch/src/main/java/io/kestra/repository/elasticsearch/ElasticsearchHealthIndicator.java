package io.kestra.repository.elasticsearch;

import com.google.common.base.Charsets;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.context.annotation.Requires;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.opensearch.action.ActionListener;
import org.opensearch.action.admin.cluster.health.ClusterHealthRequest;
import org.opensearch.action.admin.cluster.health.ClusterHealthResponse;
import org.opensearch.client.*;
import org.opensearch.cluster.health.ClusterHealthStatus;
import org.opensearch.common.Strings;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.Map;
import jakarta.inject.Singleton;

import static io.micronaut.health.HealthStatus.DOWN;
import static io.micronaut.health.HealthStatus.UP;
import static java.util.Collections.emptyMap;
import static org.opensearch.cluster.health.ClusterHealthStatus.GREEN;
import static org.opensearch.cluster.health.ClusterHealthStatus.YELLOW;

/**
 * A {@link HealthIndicator} for Elasticsearch High Level REST client.
 * Mostly a copy from the micronaut one, since it don't work with yellow cluster for example
 */
@Requires(beans = HealthEndpoint.class)
@ElasticSearchRepositoryEnabled
@Singleton
@Slf4j
public class ElasticsearchHealthIndicator implements HealthIndicator {
    public static final String NAME = "elasticsearch";
    private final RestHighLevelClient esClient;
    private final Request request;
    /**
     * Constructor.
     *
     * @param esClient The Elasticsearch high level REST client.
     */
    public ElasticsearchHealthIndicator(RestHighLevelClient esClient) {
        this.esClient = esClient;

        request = new Request(HttpGet.METHOD_NAME, "_cluster/health");
        request.addParameter("master_timeout", "30s");
        request.addParameter("level", "cluster");
        request.addParameter("timeout", "30s");
    }

    @Override
    public Publisher<HealthResult> getResult() {
        return (subscriber -> {
            try {
                esClient.getLowLevelClient()
                    .performRequestAsync(
                        request,
                        new ResponseListener() {
                            private final HealthResult.Builder resultBuilder = HealthResult.builder(NAME);

                            @Override
                            public void onSuccess(Response response) {
                                HealthResult result;

                                try {
                                    Map<String, Object> map = JacksonMapper.toMap(
                                        IOUtils.toString(response.getEntity().getContent(), Charsets.UTF_8)
                                    );

                                    result = resultBuilder
                                        .status(healthResultStatus((String) map.get("status")))
                                        .details(map)
                                        .build();
                                } catch (IOException e) {
                                    result = resultBuilder.status(DOWN).exception(e).build();
                                }

                                subscriber.onNext(result);
                                subscriber.onComplete();
                            }

                            @Override
                            public void onFailure(Exception e) {
                                subscriber.onNext(resultBuilder.status(DOWN).exception(e).build());
                                subscriber.onComplete();
                            }
                        }
                    );
            } catch (Exception e) {
                HealthResult.Builder resultBuilder = HealthResult.builder(NAME);
                subscriber.onNext(resultBuilder.status(DOWN).exception(e).build());
                subscriber.onComplete();
            }
        });
    }


    private HealthStatus healthResultStatus(String status) {
        ClusterHealthStatus clusterHealthStatus = ClusterHealthStatus.fromString(status);

        return clusterHealthStatus == GREEN || clusterHealthStatus == YELLOW ? UP : DOWN;
    }
}
